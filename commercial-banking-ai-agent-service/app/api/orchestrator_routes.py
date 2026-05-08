"""
FastAPI routes for the Agent Orchestrator.
Provides endpoints for comprehensive multi-agent reviews and execution log access.
"""

from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session

from app.agents.orchestrator import OrchestratorService
from app.db.database import get_db
from app.models.schemas import AgentExecutionLogDto, ComprehensiveClientReviewDto

# Router mounted at /api/v1/orchestrator in the main app
router = APIRouter(prefix="/api/v1/orchestrator", tags=["Agent Orchestrator"])


@router.post(
    "/review/{identification}",
    response_model=ComprehensiveClientReviewDto,
    summary="Run a comprehensive multi-agent review for a client",
)
async def comprehensive_review(identification: str, db: Session = Depends(get_db)):
    """
    Execute all three agents in sequence for a complete client assessment:
    1. Client Health Assessment (scoring + LLM narrative)
    2. Wallet Share Analysis (metrics + LLM product recommendations)
    3. Meeting Brief Preview (cross-agent context-aware brief)

    Each step is logged to the agent_execution_log table for auditability.
    """
    try:
        service = OrchestratorService(db)
        return await service.comprehensive_review(identification)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Orchestrator error: {str(e)}")


@router.get(
    "/logs",
    response_model=list[AgentExecutionLogDto],
    summary="Get agent execution logs",
)
async def get_execution_logs(
    agent_type: Optional[str] = Query(None, description="Filter by agent type: CLIENT_HEALTH / RM_COPILOT / WALLET_SHARE"),
    limit: int = Query(50, ge=1, le=500, description="Maximum number of log entries to return"),
    db: Session = Depends(get_db),
):
    """
    Retrieve agent execution logs for auditing and monitoring.
    Optionally filter by agent type. Returns most recent entries first.
    """
    service = OrchestratorService(db)
    return service.get_execution_logs(agent_type=agent_type, limit=limit)
