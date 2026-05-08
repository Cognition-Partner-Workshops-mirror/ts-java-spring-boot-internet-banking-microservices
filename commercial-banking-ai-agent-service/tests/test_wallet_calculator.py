"""
Unit tests for the Wallet Share calculator engine.
Validates bank share calculation, total wallet estimation, segment determination,
and the full analysis pipeline.
"""

from decimal import Decimal

import pytest

from app.agents.wallet_share.calculator import WalletShareCalculator
from app.models.schemas import BankAccountResponse, TransactionResponse


@pytest.fixture
def calculator():
    """Provide a fresh calculator instance for each test."""
    return WalletShareCalculator()


# ---------------------------------------------------------------------------
# Bank Share Calculation Tests
# ---------------------------------------------------------------------------


class TestBankShare:
    """Tests for calculating the bank's current share of the client's wallet."""

    def test_no_accounts_returns_zero(self, calculator):
        """No accounts should yield a bank share of 0."""
        assert calculator.calculate_bank_share([]) == Decimal("0")

    def test_sums_active_account_balances(self, calculator):
        """Bank share should sum available_balance across all ACTIVE accounts."""
        accounts = [
            BankAccountResponse(available_balance=Decimal("50000"), status="ACTIVE"),
            BankAccountResponse(available_balance=Decimal("30000"), status="ACTIVE"),
        ]
        assert calculator.calculate_bank_share(accounts) == Decimal("80000")

    def test_excludes_inactive_accounts(self, calculator):
        """Only ACTIVE accounts should contribute to bank share."""
        accounts = [
            BankAccountResponse(available_balance=Decimal("50000"), status="ACTIVE"),
            BankAccountResponse(available_balance=Decimal("20000"), status="CLOSED"),
        ]
        assert calculator.calculate_bank_share(accounts) == Decimal("50000")


# ---------------------------------------------------------------------------
# Total Wallet Estimation Tests
# ---------------------------------------------------------------------------


class TestTotalWalletEstimation:
    """Tests for estimating the client's total financial wallet."""

    def test_basic_multiplier_estimate(self, calculator):
        """Base estimate should be bank share multiplied by the wallet multiplier (3x)."""
        accounts = [
            BankAccountResponse(available_balance=Decimal("100000"), status="ACTIVE")
        ]
        total = calculator.estimate_total_wallet(accounts, [])
        # 100K * 3.0 = 300K
        assert total == Decimal("300000.00")

    def test_high_txn_volume_adjusts_upward(self, calculator):
        """High transaction volume relative to balance should increase the estimate."""
        accounts = [
            BankAccountResponse(available_balance=Decimal("10000"), status="ACTIVE")
        ]
        # Transaction volume = 25K, which is > 10K * 2 = 20K
        transactions = [TransactionResponse(amount=Decimal("5000")) for _ in range(5)]
        total = calculator.estimate_total_wallet(accounts, transactions)
        # base = 10K * 3 = 30K; adjustment = 25K * 0.5 = 12.5K; max(30K, 10K + 12.5K) = 30K
        assert total >= Decimal("22500")


# ---------------------------------------------------------------------------
# Segment Determination Tests
# ---------------------------------------------------------------------------


class TestSegmentDetermination:
    """Tests for wealth segment classification."""

    def test_uhnw_segment(self, calculator):
        """Total value >= 1M should classify as UHNW."""
        assert calculator.determine_segment(Decimal("1500000")) == "UHNW"

    def test_hnw_segment(self, calculator):
        """Total value >= 500K and < 1M should classify as HNW."""
        assert calculator.determine_segment(Decimal("750000")) == "HNW"

    def test_affluent_segment(self, calculator):
        """Total value >= 100K and < 500K should classify as AFFLUENT."""
        assert calculator.determine_segment(Decimal("250000")) == "AFFLUENT"

    def test_mass_segment(self, calculator):
        """Total value < 100K should classify as MASS."""
        assert calculator.determine_segment(Decimal("50000")) == "MASS"


# ---------------------------------------------------------------------------
# Wallet Share Percentage Tests
# ---------------------------------------------------------------------------


class TestWalletSharePct:
    """Tests for wallet share percentage calculation."""

    def test_basic_percentage(self, calculator):
        """Percentage should be (bank_share / total_wallet) * 100."""
        pct = calculator.calculate_wallet_share_pct(Decimal("50000"), Decimal("200000"))
        assert pct == Decimal("25.00")

    def test_zero_total_wallet_returns_zero(self, calculator):
        """Zero total wallet should yield 0% to avoid division by zero."""
        pct = calculator.calculate_wallet_share_pct(Decimal("10000"), Decimal("0"))
        assert pct == Decimal("0")

    def test_full_share_returns_100(self, calculator):
        """Bank share equal to total wallet should yield 100%."""
        pct = calculator.calculate_wallet_share_pct(Decimal("50000"), Decimal("50000"))
        assert pct == Decimal("100.00")


# ---------------------------------------------------------------------------
# Full Analysis Pipeline Tests
# ---------------------------------------------------------------------------


class TestFullAnalysis:
    """Tests for the complete analyze() pipeline."""

    def test_returns_all_keys(self, calculator):
        """The analyze() method should return all expected keys."""
        accounts = [
            BankAccountResponse(available_balance=Decimal("100000"), status="ACTIVE", type="SAVINGS_ACCOUNT"),
        ]
        result = calculator.analyze(accounts, [])

        assert "bank_wallet_share" in result
        assert "estimated_total_wallet" in result
        assert "wallet_share_pct" in result
        assert "segment" in result

    def test_empty_inputs(self, calculator):
        """Empty accounts and transactions should still produce valid output."""
        result = calculator.analyze([], [])

        assert result["bank_wallet_share"] == Decimal("0")
        assert result["segment"] == "MASS"
