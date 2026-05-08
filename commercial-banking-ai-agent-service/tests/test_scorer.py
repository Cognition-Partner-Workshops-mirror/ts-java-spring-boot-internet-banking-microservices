"""
Unit tests for the Client Health scoring engine.
Validates sub-score calculations, composite scoring, and risk level determination.
"""

from decimal import Decimal

import pytest

from app.agents.client_health.scorer import ClientHealthScorer
from app.models.schemas import BankAccountResponse, TransactionResponse


@pytest.fixture
def scorer():
    """Provide a fresh scorer instance for each test."""
    return ClientHealthScorer()


# ---------------------------------------------------------------------------
# Balance Score Tests
# ---------------------------------------------------------------------------


class TestBalanceScore:
    """Tests for balance adequacy sub-score calculation."""

    def test_no_accounts_returns_zero(self, scorer):
        """No accounts should yield a balance score of 0."""
        assert scorer.compute_balance_score([]) == Decimal("0")

    def test_high_balance_returns_100(self, scorer):
        """Total balance >= 100K should yield the maximum score of 100."""
        accounts = [
            BankAccountResponse(available_balance=Decimal("150000"), status="ACTIVE")
        ]
        assert scorer.compute_balance_score(accounts) == Decimal("100")

    def test_medium_balance_returns_60(self, scorer):
        """Total balance between 10K and 50K should yield a score of 60."""
        accounts = [
            BankAccountResponse(available_balance=Decimal("25000"), status="ACTIVE")
        ]
        assert scorer.compute_balance_score(accounts) == Decimal("60")

    def test_low_balance_returns_40(self, scorer):
        """Total balance between 1K and 10K should yield a score of 40."""
        accounts = [
            BankAccountResponse(available_balance=Decimal("5000"), status="ACTIVE")
        ]
        assert scorer.compute_balance_score(accounts) == Decimal("40")

    def test_very_low_balance_returns_20(self, scorer):
        """Total balance below 1K should yield a score of 20."""
        accounts = [
            BankAccountResponse(available_balance=Decimal("500"), status="ACTIVE")
        ]
        assert scorer.compute_balance_score(accounts) == Decimal("20")

    def test_multiple_accounts_summed(self, scorer):
        """Balances from multiple accounts should be summed for scoring."""
        accounts = [
            BankAccountResponse(available_balance=Decimal("30000"), status="ACTIVE"),
            BankAccountResponse(available_balance=Decimal("25000"), status="ACTIVE"),
        ]
        # Total = 55K → should return 80
        assert scorer.compute_balance_score(accounts) == Decimal("80")


# ---------------------------------------------------------------------------
# Transaction Velocity Score Tests
# ---------------------------------------------------------------------------


class TestTxnVelocityScore:
    """Tests for transaction velocity sub-score calculation."""

    def test_no_transactions_returns_10(self, scorer):
        """No transactions should yield a minimal score of 10."""
        assert scorer.compute_txn_velocity_score([]) == Decimal("10")

    def test_few_transactions_returns_40(self, scorer):
        """1-5 transactions should yield a score of 40."""
        txns = [TransactionResponse(amount=Decimal("100")) for _ in range(3)]
        assert scorer.compute_txn_velocity_score(txns) == Decimal("40")

    def test_moderate_transactions_returns_60(self, scorer):
        """6-15 transactions should yield a score of 60."""
        txns = [TransactionResponse(amount=Decimal("100")) for _ in range(10)]
        assert scorer.compute_txn_velocity_score(txns) == Decimal("60")

    def test_many_transactions_returns_80(self, scorer):
        """16-30 transactions should yield a score of 80."""
        txns = [TransactionResponse(amount=Decimal("100")) for _ in range(20)]
        assert scorer.compute_txn_velocity_score(txns) == Decimal("80")

    def test_high_transactions_returns_100(self, scorer):
        """More than 30 transactions should yield the maximum score of 100."""
        txns = [TransactionResponse(amount=Decimal("100")) for _ in range(35)]
        assert scorer.compute_txn_velocity_score(txns) == Decimal("100")


# ---------------------------------------------------------------------------
# Account Diversity Score Tests
# ---------------------------------------------------------------------------


class TestAccountDiversityScore:
    """Tests for account diversity sub-score calculation."""

    def test_no_accounts_returns_zero(self, scorer):
        """No accounts should yield a diversity score of 0."""
        assert scorer.compute_account_diversity_score([]) == Decimal("0")

    def test_single_account_single_type(self, scorer):
        """One account with one type should give a low diversity score."""
        accounts = [BankAccountResponse(type="SAVINGS_ACCOUNT")]
        score = scorer.compute_account_diversity_score(accounts)
        # 1 account * 15 = 15 (count) + 1 type * 10 = 10 → 25
        assert score == Decimal("25")

    def test_multiple_types_increases_score(self, scorer):
        """Multiple account types should increase the diversity score."""
        accounts = [
            BankAccountResponse(type="SAVINGS_ACCOUNT"),
            BankAccountResponse(type="CURRENT_ACCOUNT"),
            BankAccountResponse(type="FIXED_DEPOSIT"),
        ]
        score = scorer.compute_account_diversity_score(accounts)
        # 3 * 15 = 45 (count) + 3 * 10 = 30 (diversity) → 75
        assert score == Decimal("75")


# ---------------------------------------------------------------------------
# Composite Score & Risk Level Tests
# ---------------------------------------------------------------------------


class TestCompositeScore:
    """Tests for the weighted composite score and risk level determination."""

    def test_composite_weighted_correctly(self, scorer):
        """Composite score should be the weighted average of all sub-scores."""
        # Weights: balance=0.35, velocity=0.25, regularity=0.20, diversity=0.20
        composite = scorer.compute_composite_score(
            balance=Decimal("80"),
            velocity=Decimal("60"),
            regularity=Decimal("70"),
            diversity=Decimal("50"),
        )
        # 80*0.35 + 60*0.25 + 70*0.20 + 50*0.20 = 28 + 15 + 14 + 10 = 67.0
        assert composite == Decimal("67.00")

    def test_healthy_risk_level(self, scorer):
        """Score >= 65 should classify as HEALTHY."""
        assert scorer.determine_risk_level(Decimal("75")).value == "HEALTHY"

    def test_at_risk_level(self, scorer):
        """Score between 40 and 65 should classify as AT_RISK."""
        assert scorer.determine_risk_level(Decimal("50")) .value == "AT_RISK"

    def test_critical_risk_level(self, scorer):
        """Score below 40 should classify as CRITICAL."""
        assert scorer.determine_risk_level(Decimal("30")).value == "CRITICAL"

    def test_full_assessment_pipeline(self, scorer):
        """The full assess() method should return all expected keys."""
        accounts = [
            BankAccountResponse(
                available_balance=Decimal("50000"),
                status="ACTIVE",
                type="SAVINGS_ACCOUNT",
            )
        ]
        transactions = [TransactionResponse(amount=Decimal("100")) for _ in range(8)]

        result = scorer.assess(accounts, transactions)

        # Verify all expected keys are present
        assert "balance_score" in result
        assert "txn_velocity_score" in result
        assert "payment_regularity_score" in result
        assert "account_diversity_score" in result
        assert "health_score" in result
        assert "risk_level" in result

        # Verify types
        assert isinstance(result["health_score"], Decimal)
        assert result["risk_level"] in ("HEALTHY", "AT_RISK", "CRITICAL")
