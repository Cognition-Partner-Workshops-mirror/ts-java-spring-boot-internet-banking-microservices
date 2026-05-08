"""
Rule-based wallet share estimation engine.
Estimates a client's total financial wallet and the bank's current share,
then determines the client's wealth segment.
"""

from decimal import Decimal

from app.models.schemas import BankAccountResponse, TransactionResponse


class WalletShareCalculator:
    """Computes wallet share metrics from banking data."""

    # Segment thresholds based on total relationship value
    UHNW_THRESHOLD = Decimal("1000000")   # Ultra-High Net Worth >= 1M
    HNW_THRESHOLD = Decimal("500000")     # High Net Worth >= 500K
    AFFLUENT_THRESHOLD = Decimal("100000") # Affluent >= 100K
    # Below AFFLUENT_THRESHOLD = MASS

    # Wallet multiplier: estimated total wallet as a multiple of bank deposits
    # Based on industry heuristic that a bank typically holds 20-40% of a client's total wallet
    WALLET_MULTIPLIER = Decimal("3.0")

    def calculate_bank_share(self, accounts: list[BankAccountResponse]) -> Decimal:
        """
        Calculate the bank's current share from total deposits across all accounts.
        Sums available_balance for all active accounts.
        """
        if not accounts:
            return Decimal("0")

        total = sum(
            (a.available_balance or Decimal("0"))
            for a in accounts
            if a.status == "ACTIVE"
        )
        return total

    def estimate_total_wallet(
        self,
        accounts: list[BankAccountResponse],
        transactions: list[TransactionResponse],
    ) -> Decimal:
        """
        Estimate the client's total financial wallet across all institutions.
        Uses a multiplier on the bank's known deposits as a starting heuristic.
        Transaction volume can adjust the estimate upward.
        """
        bank_share = self.calculate_bank_share(accounts)

        # Base estimate: multiply known deposits by the wallet multiplier
        base_estimate = bank_share * self.WALLET_MULTIPLIER

        # Adjust upward if high transaction volume suggests larger external activity
        txn_volume = sum(abs(t.amount or Decimal("0")) for t in transactions)
        if txn_volume > bank_share * Decimal("2"):
            # High transaction volume relative to balance suggests external accounts
            adjustment = txn_volume * Decimal("0.5")
            base_estimate = max(base_estimate, bank_share + adjustment)

        return round(base_estimate, 2)

    def determine_segment(self, total_relationship_value: Decimal) -> str:
        """
        Classify the client into a wealth segment based on their total relationship value.
        Segments: UHNW, HNW, AFFLUENT, MASS.
        """
        if total_relationship_value >= self.UHNW_THRESHOLD:
            return "UHNW"
        elif total_relationship_value >= self.HNW_THRESHOLD:
            return "HNW"
        elif total_relationship_value >= self.AFFLUENT_THRESHOLD:
            return "AFFLUENT"
        else:
            return "MASS"

    def calculate_wallet_share_pct(
        self, bank_share: Decimal, total_wallet: Decimal
    ) -> Decimal:
        """Calculate the percentage of total wallet held by the bank."""
        if total_wallet <= 0:
            return Decimal("0")
        pct = (bank_share / total_wallet) * Decimal("100")
        return round(pct, 2)

    def analyze(
        self,
        accounts: list[BankAccountResponse],
        transactions: list[TransactionResponse],
    ) -> dict:
        """
        Run the full wallet share analysis pipeline.
        Returns a dict with bank_share, estimated_total_wallet, wallet_share_pct, and segment.
        """
        bank_share = self.calculate_bank_share(accounts)
        total_wallet = self.estimate_total_wallet(accounts, transactions)
        pct = self.calculate_wallet_share_pct(bank_share, total_wallet)
        segment = self.determine_segment(bank_share)

        return {
            "bank_wallet_share": bank_share,
            "estimated_total_wallet": total_wallet,
            "wallet_share_pct": pct,
            "segment": segment,
        }
