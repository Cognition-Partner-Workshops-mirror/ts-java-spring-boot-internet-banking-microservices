"""
Service layer for the Wallet Share Agent.
Bridges between the LangGraph execution and the database persistence layer.
"""

import logging
from datetime import datetime
from decimal import Decimal

from sqlalchemy.orm import Session

from app.agents.wallet_share.graph import wallet_share_graph
from app.models.db_models import ClientWalletAnalysis, WalletProductRecommendation
from app.models.enums import RecommendationStatus
from app.models.schemas import ClientWalletAnalysisDto, ProductRecommendationDto

logger = logging.getLogger(__name__)


class WalletShareService:
    """Orchestrates wallet share analysis using the LangGraph pipeline."""

    def __init__(self, db: Session):
        self.db = db

    async def analyze_wallet(self, identification: str) -> ClientWalletAnalysisDto:
        """
        Run the full wallet share analysis graph for a client.
        Persists the analysis and product recommendations to the database.
        """
        logger.info("Starting wallet analysis for client %s", identification)

        # Optionally inject health score context for cross-agent data sharing
        health_score = None
        risk_level = "UNKNOWN"
        try:
            from app.models.db_models import ClientHealthScore

            latest_health = (
                self.db.query(ClientHealthScore)
                .filter(ClientHealthScore.identification == identification)
                .order_by(ClientHealthScore.assessed_at.desc())
                .first()
            )
            if latest_health:
                health_score = float(latest_health.health_score) if latest_health.health_score else None
                risk_level = latest_health.risk_level or "UNKNOWN"
        except Exception as e:
            logger.warning("Could not fetch health data for wallet context: %s", str(e))

        # Execute the LangGraph pipeline
        initial_state = {
            "identification": identification,
            "user_data": None,
            "accounts": [],
            "transactions": [],
            "wallet_metrics": None,
            "health_score": health_score,
            "risk_level": risk_level,
            "ai_analysis": None,
            "result": None,
            "error": None,
        }
        final_state = await wallet_share_graph.ainvoke(initial_state)
        result = final_state.get("result", {})

        if result.get("error"):
            logger.error("Wallet analysis failed for %s: %s", identification, result["error"])
            raise ValueError(result["error"])

        # Persist the wallet analysis
        analysis_entity = ClientWalletAnalysis(
            user_id=result.get("user_id", 0),
            identification=identification,
            estimated_total_wallet=result.get("estimated_total_wallet", Decimal("0")),
            bank_wallet_share=result.get("bank_wallet_share", Decimal("0")),
            wallet_share_pct=result.get("wallet_share_pct", Decimal("0")),
            segment=result.get("segment", "MASS"),
            analysis_summary=result.get("analysis_summary", ""),
            analyzed_at=datetime.utcnow(),
        )
        self.db.add(analysis_entity)
        self.db.flush()

        # Persist product recommendations
        for rec_data in result.get("recommendations", []):
            rec_entity = WalletProductRecommendation(
                wallet_analysis_id=analysis_entity.id,
                product_type=rec_data.get("product_type", "OTHER"),
                product_name=rec_data.get("product_name", "Unknown Product"),
                estimated_revenue=Decimal(str(rec_data.get("estimated_revenue", 0))),
                confidence_score=Decimal(str(rec_data.get("confidence_score", 0))),
                rationale=rec_data.get("rationale", ""),
                status=RecommendationStatus.SUGGESTED.value,
            )
            self.db.add(rec_entity)

        self.db.commit()
        self.db.refresh(analysis_entity)

        logger.info(
            "Wallet analysis complete for %s: share_pct=%s segment=%s recs=%d",
            identification,
            analysis_entity.wallet_share_pct,
            analysis_entity.segment,
            len(result.get("recommendations", [])),
        )
        return self._to_dto(analysis_entity)

    def get_latest_analysis(self, identification: str) -> ClientWalletAnalysisDto:
        """Retrieve the most recent wallet analysis for a client."""
        analysis = (
            self.db.query(ClientWalletAnalysis)
            .filter(ClientWalletAnalysis.identification == identification)
            .order_by(ClientWalletAnalysis.analyzed_at.desc())
            .first()
        )
        if not analysis:
            raise ValueError(f"No wallet analysis found for client {identification}")
        return self._to_dto(analysis)

    def get_analysis_trend(self, identification: str) -> list[ClientWalletAnalysisDto]:
        """Retrieve the historical trend of wallet analyses for a client."""
        analyses = (
            self.db.query(ClientWalletAnalysis)
            .filter(ClientWalletAnalysis.identification == identification)
            .order_by(ClientWalletAnalysis.analyzed_at.desc())
            .limit(30)
            .all()
        )
        return [self._to_dto(a) for a in analyses]

    def update_recommendation_status(self, recommendation_id: int, status: str) -> None:
        """Update the status of a product recommendation."""
        rec = (
            self.db.query(WalletProductRecommendation)
            .filter(WalletProductRecommendation.id == recommendation_id)
            .first()
        )
        if not rec:
            raise ValueError(f"Recommendation {recommendation_id} not found")
        rec.status = status
        self.db.commit()

    def _to_dto(self, analysis: ClientWalletAnalysis) -> ClientWalletAnalysisDto:
        """Convert a wallet analysis ORM entity to a DTO with nested recommendations."""
        return ClientWalletAnalysisDto(
            id=analysis.id,
            user_id=analysis.user_id,
            identification=analysis.identification,
            estimated_total_wallet=analysis.estimated_total_wallet,
            bank_wallet_share=analysis.bank_wallet_share,
            wallet_share_pct=analysis.wallet_share_pct,
            segment=analysis.segment,
            analysis_summary=analysis.analysis_summary,
            analyzed_at=analysis.analyzed_at,
            recommendations=[
                ProductRecommendationDto.model_validate(r)
                for r in (analysis.recommendations or [])
            ],
        )
