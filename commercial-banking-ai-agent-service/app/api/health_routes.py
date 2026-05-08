"""
FastAPI routes for the Client Health Monitor & Assessment agent (Agent 1).
Provides endpoints to trigger assessments, retrieve scores, view trends, and manage alerts.
"""

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.agents.client_health.service import ClientHealthService
from app.db.database import get_db
from app.models.schemas import ClientHealthAlertDto, ClientHealthScoreDto

# Router mounted at /api/v1/health in the main app
router = APIRouter(prefix="/api/v1/health", tags=["Client Health Monitor"])


@router.post(
    "/assess/{identification}",
    response_model=ClientHealthScoreDto,
    summary="Assess a single client's financial health",
)
async def assess_client(identification: str, db: Session = Depends(get_db)):
    """
    Trigger a full health assessment for a client identified by their NIC.
    Runs the LangGraph pipeline: fetch data → score → LLM summary → alerts → persist.
    """
    try:
        service = ClientHealthService(db)
        return await service.assess_client(identification)
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))


@router.post(
    "/assess-all",
    response_model=list[ClientHealthScoreDto],
    summary="Batch-assess all clients",
)
async def assess_all_clients(db: Session = Depends(get_db)):
    """
    Trigger health assessments for all clients registered in core-banking-service.
    Useful for scheduled batch runs.
    """
    service = ClientHealthService(db)
    return await service.assess_all_clients()


@router.get(
    "/score/{identification}",
    response_model=ClientHealthScoreDto,
    summary="Get the latest health score for a client",
)
async def get_latest_score(identification: str, db: Session = Depends(get_db)):
    """Retrieve the most recent health assessment score for a specific client."""
    try:
        service = ClientHealthService(db)
        return service.get_latest_score(identification)
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))


@router.get(
    "/score/{identification}/trend",
    response_model=list[ClientHealthScoreDto],
    summary="Get health score trend for a client",
)
async def get_score_trend(identification: str, db: Session = Depends(get_db)):
    """Retrieve the historical trend of health scores for a client (up to 30 records)."""
    service = ClientHealthService(db)
    return service.get_score_trend(identification)


@router.get(
    "/alerts",
    response_model=list[ClientHealthAlertDto],
    summary="Get all active (unacknowledged) health alerts",
)
async def get_active_alerts(db: Session = Depends(get_db)):
    """List all unacknowledged health alerts across all clients."""
    service = ClientHealthService(db)
    return service.get_active_alerts()


@router.put(
    "/alerts/{alert_id}/acknowledge",
    summary="Acknowledge a health alert",
)
async def acknowledge_alert(alert_id: int, db: Session = Depends(get_db)):
    """Mark a specific health alert as acknowledged by the RM."""
    try:
        service = ClientHealthService(db)
        service.acknowledge_alert(alert_id)
        return {"message": f"Alert {alert_id} acknowledged"}
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))
