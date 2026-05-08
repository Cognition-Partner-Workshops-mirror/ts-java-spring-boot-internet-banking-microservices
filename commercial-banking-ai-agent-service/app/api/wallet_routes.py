"""
FastAPI routes for the Wallet Share Agent (Agent 3).
Provides endpoints to trigger wallet analysis, view results, and manage recommendations.
"""

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.agents.wallet_share.service import WalletShareService
from app.db.database import get_db
from app.models.schemas import (
    ClientWalletAnalysisDto,
    UpdateRecommendationRequest,
)

# Router mounted at /api/v1/wallet in the main app
router = APIRouter(prefix="/api/v1/wallet", tags=["Wallet Share Agent"])


@router.post(
    "/analyze/{identification}",
    response_model=ClientWalletAnalysisDto,
    summary="Analyze wallet share for a client",
)
async def analyze_wallet(identification: str, db: Session = Depends(get_db)):
    """
    Trigger a full wallet share analysis for a client.
    Runs the LangGraph pipeline: fetch data → compute metrics → LLM recommendations → persist.
    """
    try:
        service = WalletShareService(db)
        return await service.analyze_wallet(identification)
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))


@router.get(
    "/analysis/{identification}",
    response_model=ClientWalletAnalysisDto,
    summary="Get the latest wallet analysis for a client",
)
async def get_latest_analysis(identification: str, db: Session = Depends(get_db)):
    """Retrieve the most recent wallet share analysis for a specific client."""
    try:
        service = WalletShareService(db)
        return service.get_latest_analysis(identification)
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))


@router.get(
    "/analysis/{identification}/trend",
    response_model=list[ClientWalletAnalysisDto],
    summary="Get wallet analysis trend for a client",
)
async def get_analysis_trend(identification: str, db: Session = Depends(get_db)):
    """Retrieve historical wallet analyses for a client (up to 30 records)."""
    service = WalletShareService(db)
    return service.get_analysis_trend(identification)


@router.put(
    "/recommendations/{recommendation_id}",
    summary="Update a product recommendation's status",
)
async def update_recommendation(
    recommendation_id: int,
    request: UpdateRecommendationRequest,
    db: Session = Depends(get_db),
):
    """
    Update the lifecycle status of a product recommendation.
    Transitions: SUGGESTED → PRESENTED → ACCEPTED / DECLINED.
    """
    try:
        service = WalletShareService(db)
        service.update_recommendation_status(recommendation_id, request.status)
        return {"message": f"Recommendation {recommendation_id} updated to {request.status}"}
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))
