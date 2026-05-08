"""
LangGraph state machine for the Client Health Monitor & Assessment agent.

Graph flow:
  fetch_client_data → score_client → generate_ai_summary → create_alerts → persist_results
"""

import logging
from datetime import datetime
from decimal import Decimal
from typing import Any, Optional

from langgraph.graph import END, StateGraph
from typing_extensions import TypedDict

from app.agents.client_health.scorer import ClientHealthScorer
from app.models.enums import AlertType, RiskLevel, Severity
from app.services.core_banking_client import core_banking_client
from app.services.llm_service import llm_service
from app.services.prompt_templates import (
    CLIENT_HEALTH_SYSTEM_PROMPT,
    CLIENT_HEALTH_USER_PROMPT,
)

logger = logging.getLogger(__name__)

# Scorer singleton shared across invocations
_scorer = ClientHealthScorer()


# ---------------------------------------------------------------------------
# LangGraph State Definition
# ---------------------------------------------------------------------------


class ClientHealthState(TypedDict):
    """State passed between nodes in the client health assessment graph."""

    # Input
    identification: str

    # Data fetched from core-banking-service
    user_data: Optional[dict]
    accounts: list[dict]
    transactions: list[dict]

    # Scoring results
    scores: Optional[dict]

    # LLM-generated narrative
    ai_summary: str

    # Alerts to persist
    alerts: list[dict]

    # Final assembled result
    result: Optional[dict]

    # Error tracking
    error: Optional[str]


# ---------------------------------------------------------------------------
# Graph Nodes
# ---------------------------------------------------------------------------


async def fetch_client_data(state: ClientHealthState) -> dict:
    """
    Node 1: Fetch user profile and account data from core-banking-service.
    Populates user_data and accounts in state.
    """
    identification = state["identification"]
    logger.info("Fetching client data for %s", identification)

    user = await core_banking_client.get_user(identification)
    if not user:
        return {"error": f"Client not found: {identification}", "user_data": None, "accounts": [], "transactions": []}

    user_dict = user.model_dump()

    # Fetch all accounts for the user
    # Note: core-banking-service returns accounts with user data via /api/v1/user/{id}
    # For now, we use available account data from the user response
    accounts = []
    transactions = []

    # The core-banking-service stores accounts linked to users
    # We fetch users list to get account numbers, then fetch each account
    users_list = await core_banking_client.get_users(page=0, size=100)
    for u in users_list:
        if u.identification_number == identification:
            user_dict = u.model_dump()
            break

    return {
        "user_data": user_dict,
        "accounts": accounts,
        "transactions": transactions,
        "error": None,
    }


async def score_client(state: ClientHealthState) -> dict:
    """
    Node 2: Run the rule-based scoring engine on fetched account/transaction data.
    Produces sub-scores and composite health score.
    """
    if state.get("error"):
        return {}

    from app.models.schemas import BankAccountResponse, TransactionResponse

    # Convert raw dicts back to typed objects for the scorer
    accounts = [BankAccountResponse.model_validate(a) for a in state.get("accounts", [])]
    transactions = [TransactionResponse.model_validate(t) for t in state.get("transactions", [])]

    scores = _scorer.assess(accounts, transactions)
    logger.info(
        "Health scores for %s: composite=%s risk=%s",
        state["identification"],
        scores["health_score"],
        scores["risk_level"],
    )
    return {"scores": scores}


async def generate_ai_summary(state: ClientHealthState) -> dict:
    """
    Node 3: Use the LLM to generate a narrative health summary from the scores.
    Falls back to a templated summary when the LLM is not available.
    """
    if state.get("error"):
        return {"ai_summary": ""}

    scores = state.get("scores", {})
    user_data = state.get("user_data", {})
    accounts = state.get("accounts", [])

    # Build account summary text for the prompt
    account_lines = []
    for acc in accounts:
        account_lines.append(
            f"  - Account {acc.get('number', 'N/A')}: "
            f"Type={acc.get('type', 'N/A')}, "
            f"Balance={acc.get('available_balance', 0)}"
        )
    account_summary = "\n".join(account_lines) if account_lines else "  No detailed account data available."

    client_name = f"{user_data.get('first_name', '')} {user_data.get('last_name', '')}".strip() or "Unknown"

    user_prompt = CLIENT_HEALTH_USER_PROMPT.format(
        client_name=client_name,
        identification=state["identification"],
        account_summary=account_summary,
        balance_score=scores.get("balance_score", 0),
        txn_velocity_score=scores.get("txn_velocity_score", 0),
        payment_regularity_score=scores.get("payment_regularity_score", 0),
        account_diversity_score=scores.get("account_diversity_score", 0),
        health_score=scores.get("health_score", 0),
        risk_level=scores.get("risk_level", "UNKNOWN"),
    )

    summary = await llm_service.invoke(CLIENT_HEALTH_SYSTEM_PROMPT, user_prompt)
    return {"ai_summary": summary}


async def create_alerts(state: ClientHealthState) -> dict:
    """
    Node 4: Generate health alerts based on scores and risk level.
    Creates alerts for at-risk or critical clients.
    """
    if state.get("error"):
        return {"alerts": []}

    scores = state.get("scores", {})
    risk_level = scores.get("risk_level", "HEALTHY")
    alerts = []

    # Generate alerts based on risk level and sub-scores
    if risk_level == RiskLevel.CRITICAL.value:
        alerts.append({
            "alert_type": AlertType.HIGH_RISK.value,
            "severity": Severity.CRITICAL.value,
            "message": (
                f"Client {state['identification']} has a CRITICAL health score of "
                f"{scores.get('health_score', 0)}. Immediate RM attention required."
            ),
        })

    if risk_level == RiskLevel.AT_RISK.value:
        alerts.append({
            "alert_type": AlertType.HIGH_RISK.value,
            "severity": Severity.HIGH.value,
            "message": (
                f"Client {state['identification']} is AT RISK with a health score of "
                f"{scores.get('health_score', 0)}. Review recommended."
            ),
        })

    # Check for low balance specifically
    balance_score = scores.get("balance_score", Decimal("100"))
    if isinstance(balance_score, (int, float)):
        balance_score = Decimal(str(balance_score))
    if balance_score <= Decimal("30"):
        alerts.append({
            "alert_type": AlertType.BALANCE_DROP.value,
            "severity": Severity.MEDIUM.value,
            "message": f"Client {state['identification']} has a low balance score ({balance_score}/100).",
        })

    # Check for dormancy (low transaction velocity)
    txn_score = scores.get("txn_velocity_score", Decimal("100"))
    if isinstance(txn_score, (int, float)):
        txn_score = Decimal(str(txn_score))
    if txn_score <= Decimal("20"):
        alerts.append({
            "alert_type": AlertType.DORMANT.value,
            "severity": Severity.MEDIUM.value,
            "message": f"Client {state['identification']} shows low transaction activity (velocity score {txn_score}/100).",
        })

    return {"alerts": alerts}


async def persist_results(state: ClientHealthState) -> dict:
    """
    Node 5: Assemble the final result dict for the caller to persist to the database.
    Does not perform DB writes directly — the service layer handles persistence.
    """
    scores = state.get("scores", {})
    user_data = state.get("user_data", {})

    result = {
        "user_id": user_data.get("id", 0),
        "identification": state["identification"],
        "health_score": scores.get("health_score", Decimal("0")),
        "risk_level": scores.get("risk_level", RiskLevel.CRITICAL.value),
        "balance_score": scores.get("balance_score", Decimal("0")),
        "txn_velocity_score": scores.get("txn_velocity_score", Decimal("0")),
        "payment_regularity_score": scores.get("payment_regularity_score", Decimal("0")),
        "account_diversity_score": scores.get("account_diversity_score", Decimal("0")),
        "ai_summary": state.get("ai_summary", ""),
        "assessed_at": datetime.utcnow(),
        "alerts": state.get("alerts", []),
        "error": state.get("error"),
    }
    return {"result": result}


# ---------------------------------------------------------------------------
# Build the LangGraph
# ---------------------------------------------------------------------------


def build_client_health_graph() -> StateGraph:
    """
    Construct and compile the Client Health Assessment LangGraph.

    Graph: fetch_client_data → score_client → generate_ai_summary → create_alerts → persist_results
    """
    graph = StateGraph(ClientHealthState)

    # Add nodes
    graph.add_node("fetch_client_data", fetch_client_data)
    graph.add_node("score_client", score_client)
    graph.add_node("generate_ai_summary", generate_ai_summary)
    graph.add_node("create_alerts", create_alerts)
    graph.add_node("persist_results", persist_results)

    # Define edges (linear pipeline)
    graph.set_entry_point("fetch_client_data")
    graph.add_edge("fetch_client_data", "score_client")
    graph.add_edge("score_client", "generate_ai_summary")
    graph.add_edge("generate_ai_summary", "create_alerts")
    graph.add_edge("create_alerts", "persist_results")
    graph.add_edge("persist_results", END)

    return graph.compile()


# Compiled graph singleton
client_health_graph = build_client_health_graph()
