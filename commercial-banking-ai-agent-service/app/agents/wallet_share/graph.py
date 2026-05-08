"""
LangGraph state machine for the Wallet Share Agent.

Graph flow:
  fetch_client_data → compute_wallet_metrics → generate_ai_recommendations → assemble_results
"""

import logging
from decimal import Decimal
from typing import Optional

from langgraph.graph import END, StateGraph
from typing_extensions import TypedDict

from app.agents.wallet_share.calculator import WalletShareCalculator
from app.services.core_banking_client import core_banking_client
from app.services.llm_service import llm_service
from app.services.prompt_templates import (
    WALLET_ANALYSIS_SYSTEM_PROMPT,
    WALLET_ANALYSIS_USER_PROMPT,
)

logger = logging.getLogger(__name__)

# Calculator singleton shared across invocations
_calculator = WalletShareCalculator()


# ---------------------------------------------------------------------------
# LangGraph State Definition
# ---------------------------------------------------------------------------


class WalletShareState(TypedDict):
    """State passed between nodes in the wallet share analysis graph."""
    identification: str
    user_data: Optional[dict]
    accounts: list[dict]
    transactions: list[dict]
    wallet_metrics: Optional[dict]
    health_score: Optional[float]
    risk_level: str
    ai_analysis: Optional[dict]
    result: Optional[dict]
    error: Optional[str]


# ---------------------------------------------------------------------------
# Graph Nodes
# ---------------------------------------------------------------------------


async def fetch_client_data(state: WalletShareState) -> dict:
    """Node 1: Fetch client profile and account data from core-banking-service."""
    identification = state["identification"]
    logger.info("Fetching client data for wallet analysis: %s", identification)

    user = await core_banking_client.get_user(identification)
    if not user:
        return {"error": f"Client not found: {identification}", "user_data": None}

    return {
        "user_data": user.model_dump(),
        "accounts": state.get("accounts", []),
        "transactions": state.get("transactions", []),
        "error": None,
    }


async def compute_wallet_metrics(state: WalletShareState) -> dict:
    """Node 2: Run the rule-based wallet share calculator on the account data."""
    if state.get("error"):
        return {}

    from app.models.schemas import BankAccountResponse, TransactionResponse

    accounts = [BankAccountResponse.model_validate(a) for a in state.get("accounts", [])]
    transactions = [TransactionResponse.model_validate(t) for t in state.get("transactions", [])]

    metrics = _calculator.analyze(accounts, transactions)
    logger.info(
        "Wallet metrics for %s: share=%s total=%s pct=%s segment=%s",
        state["identification"],
        metrics["bank_wallet_share"],
        metrics["estimated_total_wallet"],
        metrics["wallet_share_pct"],
        metrics["segment"],
    )
    return {"wallet_metrics": metrics}


async def generate_ai_recommendations(state: WalletShareState) -> dict:
    """Node 3: Use the LLM to generate wallet insight and product recommendations."""
    if state.get("error"):
        return {"ai_analysis": {}}

    user_data = state.get("user_data", {})
    metrics = state.get("wallet_metrics", {})
    accounts = state.get("accounts", [])

    client_name = (
        f"{user_data.get('first_name', '')} {user_data.get('last_name', '')}".strip()
        or "Unknown"
    )

    # Build account types summary
    account_types = set()
    for a in accounts:
        if a.get("type"):
            account_types.add(a["type"])

    user_prompt = WALLET_ANALYSIS_USER_PROMPT.format(
        client_name=client_name,
        identification=state["identification"],
        segment=metrics.get("segment", "UNKNOWN"),
        total_deposits=metrics.get("bank_wallet_share", 0),
        num_accounts=len(accounts),
        account_types=", ".join(account_types) if account_types else "SAVINGS_ACCOUNT",
        bank_share=metrics.get("bank_wallet_share", 0),
        health_score=state.get("health_score") or 0,
        risk_level=state.get("risk_level", "UNKNOWN"),
    )

    ai_result = await llm_service.invoke_json(WALLET_ANALYSIS_SYSTEM_PROMPT, user_prompt)
    return {"ai_analysis": ai_result}


async def assemble_results(state: WalletShareState) -> dict:
    """Node 4: Combine rule-based metrics and AI recommendations into the final result."""
    metrics = state.get("wallet_metrics", {})
    ai_analysis = state.get("ai_analysis", {})
    user_data = state.get("user_data", {})

    # Use AI-provided values if available, else fall back to rule-based
    estimated_total = ai_analysis.get(
        "estimated_total_wallet", metrics.get("estimated_total_wallet", Decimal("0"))
    )
    segment = ai_analysis.get("segment", metrics.get("segment", "MASS"))

    result = {
        "user_id": user_data.get("id", 0),
        "identification": state["identification"],
        "estimated_total_wallet": Decimal(str(estimated_total)),
        "bank_wallet_share": metrics.get("bank_wallet_share", Decimal("0")),
        "wallet_share_pct": metrics.get("wallet_share_pct", Decimal("0")),
        "segment": segment,
        "analysis_summary": ai_analysis.get("analysis_summary", "Analysis complete."),
        "recommendations": ai_analysis.get("recommendations", []),
        "error": state.get("error"),
    }
    return {"result": result}


# ---------------------------------------------------------------------------
# Build the LangGraph
# ---------------------------------------------------------------------------


def build_wallet_share_graph() -> StateGraph:
    """
    Construct and compile the Wallet Share Analysis LangGraph.

    Graph: fetch_client_data → compute_wallet_metrics → generate_ai_recommendations → assemble_results
    """
    graph = StateGraph(WalletShareState)

    graph.add_node("fetch_client_data", fetch_client_data)
    graph.add_node("compute_wallet_metrics", compute_wallet_metrics)
    graph.add_node("generate_ai_recommendations", generate_ai_recommendations)
    graph.add_node("assemble_results", assemble_results)

    graph.set_entry_point("fetch_client_data")
    graph.add_edge("fetch_client_data", "compute_wallet_metrics")
    graph.add_edge("compute_wallet_metrics", "generate_ai_recommendations")
    graph.add_edge("generate_ai_recommendations", "assemble_results")
    graph.add_edge("assemble_results", END)

    return graph.compile()


# Compiled graph singleton
wallet_share_graph = build_wallet_share_graph()
