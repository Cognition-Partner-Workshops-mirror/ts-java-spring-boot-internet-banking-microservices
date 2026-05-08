"""
Rule-based scoring engine for client financial health assessment.
Computes sub-scores (balance, transaction velocity, payment regularity, diversity)
and a weighted composite score. Deterministic and auditable.
"""

from decimal import Decimal
from typing import Optional

from app.models.enums import RiskLevel
from app.models.schemas import BankAccountResponse, TransactionResponse


class ClientHealthScorer:
    """Computes health sub-scores and composite score from banking data."""

    # Weights for the composite score calculation
    WEIGHT_BALANCE = Decimal("0.35")
    WEIGHT_TXN_VELOCITY = Decimal("0.25")
    WEIGHT_PAYMENT_REGULARITY = Decimal("0.20")
    WEIGHT_ACCOUNT_DIVERSITY = Decimal("0.20")

    # Thresholds for risk level determination
    HEALTHY_THRESHOLD = Decimal("65")
    AT_RISK_THRESHOLD = Decimal("40")

    def compute_balance_score(self, accounts: list[BankAccountResponse]) -> Decimal:
        """
        Score based on total available balance across all accounts.
        Higher balances yield higher scores (capped at 100).
        Tiers: <1K=20, <10K=40, <50K=60, <100K=80, >=100K=100.
        """
        if not accounts:
            return Decimal("0")

        total_balance = sum(
            (a.available_balance or Decimal("0")) for a in accounts
        )

        if total_balance >= 100_000:
            return Decimal("100")
        elif total_balance >= 50_000:
            return Decimal("80")
        elif total_balance >= 10_000:
            return Decimal("60")
        elif total_balance >= 1_000:
            return Decimal("40")
        else:
            return Decimal("20")

    def compute_txn_velocity_score(
        self, transactions: list[TransactionResponse]
    ) -> Decimal:
        """
        Score based on the number of recent transactions.
        More transactions indicate an active, engaged client.
        0 txns=10, 1-5=40, 6-15=60, 16-30=80, >30=100.
        """
        count = len(transactions)
        if count > 30:
            return Decimal("100")
        elif count > 15:
            return Decimal("80")
        elif count > 5:
            return Decimal("60")
        elif count >= 1:
            return Decimal("40")
        else:
            # No transactions — still give a minimal score (account exists)
            return Decimal("10")

    def compute_payment_regularity_score(
        self, transactions: list[TransactionResponse]
    ) -> Decimal:
        """
        Score based on payment consistency. Checks for outgoing (negative amount)
        transactions. Regular outflows indicate healthy financial activity.
        Since we may not have full transaction history, use a heuristic baseline.
        """
        if not transactions:
            # No transaction data available — assign neutral score
            return Decimal("50")

        outgoing = [t for t in transactions if t.amount and t.amount < 0]
        total = len(transactions)
        if total == 0:
            return Decimal("50")

        # Ratio of outgoing transactions to total as a regularity proxy
        ratio = len(outgoing) / total
        if ratio >= 0.3:
            return Decimal("85")
        elif ratio >= 0.15:
            return Decimal("65")
        else:
            return Decimal("45")

    def compute_account_diversity_score(
        self, accounts: list[BankAccountResponse]
    ) -> Decimal:
        """
        Score based on the number and type diversity of accounts.
        More account types indicate a deeper banking relationship.
        1 account=30, 2=50, 3=70, 4+=90.
        """
        if not accounts:
            return Decimal("0")

        unique_types = set(a.type for a in accounts if a.type)
        count = len(accounts)

        # Combine count and type diversity
        count_score = min(count * 15, 60)
        diversity_bonus = len(unique_types) * 10
        return Decimal(str(min(count_score + diversity_bonus, 100)))

    def compute_composite_score(
        self,
        balance: Decimal,
        velocity: Decimal,
        regularity: Decimal,
        diversity: Decimal,
    ) -> Decimal:
        """Weighted average of all four sub-scores, rounded to 2 decimal places."""
        composite = (
            balance * self.WEIGHT_BALANCE
            + velocity * self.WEIGHT_TXN_VELOCITY
            + regularity * self.WEIGHT_PAYMENT_REGULARITY
            + diversity * self.WEIGHT_ACCOUNT_DIVERSITY
        )
        return round(composite, 2)

    def determine_risk_level(self, composite_score: Decimal) -> RiskLevel:
        """Map composite score to a risk classification."""
        if composite_score >= self.HEALTHY_THRESHOLD:
            return RiskLevel.HEALTHY
        elif composite_score >= self.AT_RISK_THRESHOLD:
            return RiskLevel.AT_RISK
        else:
            return RiskLevel.CRITICAL

    def assess(
        self,
        accounts: list[BankAccountResponse],
        transactions: list[TransactionResponse],
    ) -> dict:
        """
        Run the full scoring pipeline and return all sub-scores, composite, and risk level.
        Returns a dict with keys matching ClientHealthScoreEntity fields.
        """
        balance = self.compute_balance_score(accounts)
        velocity = self.compute_txn_velocity_score(transactions)
        regularity = self.compute_payment_regularity_score(transactions)
        diversity = self.compute_account_diversity_score(accounts)
        composite = self.compute_composite_score(balance, velocity, regularity, diversity)
        risk_level = self.determine_risk_level(composite)

        return {
            "balance_score": balance,
            "txn_velocity_score": velocity,
            "payment_regularity_score": regularity,
            "account_diversity_score": diversity,
            "health_score": composite,
            "risk_level": risk_level.value,
        }
