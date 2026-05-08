"""
Service layer for the Client Health Monitor agent.
Bridges between the LangGraph execution and the database persistence layer.
"""

import logging
from datetime import datetime

from sqlalchemy.orm import Session

from app.agents.client_health.graph import client_health_graph
from app.models.db_models import ClientHealthAlert, ClientHealthScore
from app.models.schemas import ClientHealthAlertDto, ClientHealthScoreDto

logger = logging.getLogger(__name__)


class ClientHealthService:
    """Orchestrates client health assessments using the LangGraph pipeline."""

    def __init__(self, db: Session):
        self.db = db

    async def assess_client(self, identification: str) -> ClientHealthScoreDto:
        """
        Run the full health assessment graph for a single client.
        Persists the score and any alerts to the database.
        """
        logger.info("Starting health assessment for client %s", identification)

        # Execute the LangGraph pipeline
        initial_state = {
            "identification": identification,
            "user_data": None,
            "accounts": [],
            "transactions": [],
            "scores": None,
            "ai_summary": "",
            "alerts": [],
            "result": None,
            "error": None,
        }
        final_state = await client_health_graph.ainvoke(initial_state)
        result = final_state.get("result", {})

        if result.get("error"):
            logger.error("Assessment failed for %s: %s", identification, result["error"])
            raise ValueError(result["error"])

        # Persist the health score to the database
        score_entity = ClientHealthScore(
            user_id=result.get("user_id", 0),
            identification=identification,
            health_score=result["health_score"],
            risk_level=result["risk_level"],
            balance_score=result.get("balance_score"),
            txn_velocity_score=result.get("txn_velocity_score"),
            payment_regularity_score=result.get("payment_regularity_score"),
            account_diversity_score=result.get("account_diversity_score"),
            ai_summary=result.get("ai_summary", ""),
            assessed_at=result.get("assessed_at", datetime.utcnow()),
        )
        self.db.add(score_entity)
        self.db.flush()

        # Persist any alerts linked to this score
        for alert_data in result.get("alerts", []):
            alert_entity = ClientHealthAlert(
                health_score_id=score_entity.id,
                alert_type=alert_data["alert_type"],
                severity=alert_data["severity"],
                message=alert_data["message"],
                acknowledged=False,
            )
            self.db.add(alert_entity)

        self.db.commit()
        self.db.refresh(score_entity)

        logger.info(
            "Assessment complete for %s: score=%s risk=%s alerts=%d",
            identification,
            score_entity.health_score,
            score_entity.risk_level,
            len(result.get("alerts", [])),
        )
        return ClientHealthScoreDto.model_validate(score_entity)

    async def assess_all_clients(self) -> list[ClientHealthScoreDto]:
        """
        Batch-assess all clients by fetching the user list from core-banking-service.
        Returns a list of assessment results.
        """
        from app.services.core_banking_client import core_banking_client

        users = await core_banking_client.get_users(page=0, size=100)
        results = []
        for user in users:
            if user.identification_number:
                try:
                    score = await self.assess_client(user.identification_number)
                    results.append(score)
                except Exception as e:
                    logger.error(
                        "Batch assessment failed for %s: %s",
                        user.identification_number,
                        str(e),
                    )
        return results

    def get_latest_score(self, identification: str) -> ClientHealthScoreDto:
        """Retrieve the most recent health score for a client."""
        score = (
            self.db.query(ClientHealthScore)
            .filter(ClientHealthScore.identification == identification)
            .order_by(ClientHealthScore.assessed_at.desc())
            .first()
        )
        if not score:
            raise ValueError(f"No health score found for client {identification}")
        return ClientHealthScoreDto.model_validate(score)

    def get_score_trend(self, identification: str) -> list[ClientHealthScoreDto]:
        """Retrieve the historical trend of health scores for a client."""
        scores = (
            self.db.query(ClientHealthScore)
            .filter(ClientHealthScore.identification == identification)
            .order_by(ClientHealthScore.assessed_at.desc())
            .limit(30)
            .all()
        )
        return [ClientHealthScoreDto.model_validate(s) for s in scores]

    def get_active_alerts(self) -> list[ClientHealthAlertDto]:
        """Retrieve all unacknowledged health alerts."""
        alerts = (
            self.db.query(ClientHealthAlert)
            .filter(ClientHealthAlert.acknowledged == False)
            .order_by(ClientHealthAlert.created_at.desc())
            .all()
        )
        return [ClientHealthAlertDto.model_validate(a) for a in alerts]

    def acknowledge_alert(self, alert_id: int) -> None:
        """Mark a health alert as acknowledged."""
        alert = self.db.query(ClientHealthAlert).filter(ClientHealthAlert.id == alert_id).first()
        if not alert:
            raise ValueError(f"Alert {alert_id} not found")
        alert.acknowledged = True
        self.db.commit()
