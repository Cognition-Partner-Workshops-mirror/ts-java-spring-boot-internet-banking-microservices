"""
LangGraph state machine for the RM Copilot (Meeting Assistant) agent.

Two graph variants:
  1. Pre-meeting brief:  fetch_client_context → fetch_health_data → generate_brief
  2. Post-meeting:       collect_notes → generate_summary → extract_action_items
"""

import logging
from typing import Any, Optional

from langgraph.graph import END, StateGraph
from typing_extensions import TypedDict

from app.services.core_banking_client import core_banking_client
from app.services.llm_service import llm_service
from app.services.prompt_templates import (
    ACTION_ITEM_EXTRACTION_SYSTEM_PROMPT,
    ACTION_ITEM_EXTRACTION_USER_PROMPT,
    POST_MEETING_SUMMARY_SYSTEM_PROMPT,
    POST_MEETING_SUMMARY_USER_PROMPT,
    PRE_MEETING_BRIEF_SYSTEM_PROMPT,
    PRE_MEETING_BRIEF_USER_PROMPT,
)

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Pre-Meeting Brief Graph
# ---------------------------------------------------------------------------


class PreMeetingBriefState(TypedDict):
    """State for the pre-meeting brief generation graph."""
    identification: str
    meeting_date: str
    user_data: Optional[dict]
    account_details: str
    health_score: Optional[float]
    risk_level: str
    health_summary: str
    brief: str
    error: Optional[str]


async def fetch_client_context(state: PreMeetingBriefState) -> dict:
    """Node 1: Fetch client profile and account data from core-banking-service."""
    identification = state["identification"]
    logger.info("Fetching client context for pre-meeting brief: %s", identification)

    user = await core_banking_client.get_user(identification)
    if not user:
        return {
            "error": f"Client not found: {identification}",
            "user_data": None,
            "account_details": "No data available",
        }

    user_dict = user.model_dump()

    # Build account details text (accounts are linked via user in core-banking)
    account_details = (
        f"Client {user.first_name} {user.last_name} ({user.email}) — "
        f"detailed account data available via core-banking-service."
    )

    return {
        "user_data": user_dict,
        "account_details": account_details,
        "error": None,
    }


async def fetch_health_data(state: PreMeetingBriefState) -> dict:
    """Node 2: Pull the latest health score for the client (cross-agent data)."""
    if state.get("error"):
        return {"health_score": 0, "risk_level": "UNKNOWN", "health_summary": "Not assessed"}

    # Health data is injected by the service layer before graph execution
    # This node provides defaults if not already set
    return {
        "health_score": state.get("health_score") or 0,
        "risk_level": state.get("risk_level") or "UNKNOWN",
        "health_summary": state.get("health_summary") or "No health assessment available yet.",
    }


async def generate_brief(state: PreMeetingBriefState) -> dict:
    """Node 3: Use the LLM to generate a comprehensive pre-meeting brief."""
    if state.get("error"):
        return {"brief": f"[Error] {state['error']}"}

    user_data = state.get("user_data", {})
    client_name = (
        f"{user_data.get('first_name', '')} {user_data.get('last_name', '')}".strip()
        or "Unknown"
    )

    user_prompt = PRE_MEETING_BRIEF_USER_PROMPT.format(
        client_name=client_name,
        identification=state["identification"],
        meeting_date=state.get("meeting_date", "TBD"),
        account_details=state.get("account_details", "No data"),
        health_score=state.get("health_score", 0),
        risk_level=state.get("risk_level", "UNKNOWN"),
        health_summary=state.get("health_summary", "Not assessed"),
    )

    brief = await llm_service.invoke(PRE_MEETING_BRIEF_SYSTEM_PROMPT, user_prompt)
    return {"brief": brief}


def build_pre_meeting_brief_graph() -> StateGraph:
    """Construct the pre-meeting brief generation LangGraph."""
    graph = StateGraph(PreMeetingBriefState)

    graph.add_node("fetch_client_context", fetch_client_context)
    graph.add_node("fetch_health_data", fetch_health_data)
    graph.add_node("generate_brief", generate_brief)

    graph.set_entry_point("fetch_client_context")
    graph.add_edge("fetch_client_context", "fetch_health_data")
    graph.add_edge("fetch_health_data", "generate_brief")
    graph.add_edge("generate_brief", END)

    return graph.compile()


# ---------------------------------------------------------------------------
# Post-Meeting Summary & Action Item Graph
# ---------------------------------------------------------------------------


class PostMeetingState(TypedDict):
    """State for the post-meeting summary and action item extraction graph."""
    identification: str
    client_name: str
    meeting_date: str
    meeting_notes: str
    summary: str
    action_items: list[dict]
    error: Optional[str]


async def generate_summary(state: PostMeetingState) -> dict:
    """Node 1: Generate a structured post-meeting summary from notes."""
    user_prompt = POST_MEETING_SUMMARY_USER_PROMPT.format(
        client_name=state.get("client_name", "Unknown"),
        identification=state["identification"],
        meeting_date=state.get("meeting_date", "Unknown"),
        meeting_notes=state.get("meeting_notes", "No notes provided."),
    )

    summary = await llm_service.invoke(POST_MEETING_SUMMARY_SYSTEM_PROMPT, user_prompt)
    return {"summary": summary}


async def extract_action_items(state: PostMeetingState) -> dict:
    """Node 2: Extract structured action items from meeting notes using the LLM."""
    user_prompt = ACTION_ITEM_EXTRACTION_USER_PROMPT.format(
        meeting_notes=state.get("meeting_notes", "No notes provided."),
    )

    result = await llm_service.invoke_json(
        ACTION_ITEM_EXTRACTION_SYSTEM_PROMPT, user_prompt
    )

    # Normalize the LLM response into action item dicts
    action_items = []
    if isinstance(result, list):
        action_items = result
    elif isinstance(result, dict) and "action_items" in result:
        action_items = result["action_items"]

    # Ensure each item has required fields with defaults
    normalized = []
    for item in action_items:
        normalized.append({
            "description": item.get("description", "Follow up required"),
            "assignee": item.get("assignee", "RM"),
            "priority": item.get("priority", "MEDIUM"),
            "due_date": item.get("due_date"),
        })

    return {"action_items": normalized}


def build_post_meeting_graph() -> StateGraph:
    """Construct the post-meeting summary + action item extraction LangGraph."""
    graph = StateGraph(PostMeetingState)

    graph.add_node("generate_summary", generate_summary)
    graph.add_node("extract_action_items", extract_action_items)

    graph.set_entry_point("generate_summary")
    graph.add_edge("generate_summary", "extract_action_items")
    graph.add_edge("extract_action_items", END)

    return graph.compile()


# Compiled graph singletons
pre_meeting_brief_graph = build_pre_meeting_brief_graph()
post_meeting_graph = build_post_meeting_graph()
