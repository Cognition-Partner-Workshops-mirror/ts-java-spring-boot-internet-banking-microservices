"""
Agent Orchestrator — coordinates all three agents for a comprehensive client review.
Implements a LangGraph that runs Client Health, Wallet Share, and RM brief generation
in sequence, logging each execution step.

Data flow:
  run_health_assessment → run_wallet_analysis → generate_meeting_brief → log_execution
"""

import logging
import time
import uuid
from datetime import datetime
from typing import Optional

from langgraph.graph import END, StateGraph
from sqlalchemy.orm import Session
from typing_extensions import TypedDict

from app.agents.client_health.service import ClientHealthService
from app.agents.rm_copilot.graph import pre_meeting_brief_graph
from app.agents.wallet_share.service import WalletShareService
from app.models.db_models import AgentExecutionLog, ClientHealthScore
from app.models.enums import AgentType, ExecutionStatus
from app.models.schemas import (
    AgentExecutionLogDto,
    ClientHealthScoreDto,
    ClientWalletAnalysisDto,
    ComprehensiveClientReviewDto,
)

logger = logging.getLogger(__name__)


class OrchestratorService:
    """
    Multi-agent orchestrator that runs all three agents for a comprehensive
    client review and logs each agent execution for auditability.
    """

    def __init__(self, db: Session):
        self.db = db

    async def comprehensive_review(self, identification: str) -> ComprehensiveClientReviewDto:
        """
        Execute all three agents sequentially for a complete client review:
        1. Client Health Assessment
        2. Wallet Share Analysis
        3. Meeting Brief Preview (standalone, not persisted as a meeting)

        Each step is logged to agent_execution_log for auditability.
        """
        request_id = str(uuid.uuid4())
        logger.info("Starting comprehensive review for %s (request_id=%s)", identification, request_id)

        health_result: Optional[ClientHealthScoreDto] = None
        wallet_result: Optional[ClientWalletAnalysisDto] = None
        meeting_brief: Optional[str] = None
        logs: list[AgentExecutionLogDto] = []

        # --- Step 1: Client Health Assessment ---
        health_log = self._start_log(AgentType.CLIENT_HEALTH, request_id, identification)
        try:
            health_svc = ClientHealthService(self.db)
            start_time = time.time()
            health_result = await health_svc.assess_client(identification)
            duration_ms = int((time.time() - start_time) * 1000)
            self._complete_log(health_log, duration_ms, f"Score={health_result.health_score} Risk={health_result.risk_level}")
        except Exception as e:
            self._fail_log(health_log, str(e))
            logger.error("Health assessment failed in orchestrator: %s", str(e))
        logs.append(AgentExecutionLogDto.model_validate(health_log))

        # --- Step 2: Wallet Share Analysis ---
        wallet_log = self._start_log(AgentType.WALLET_SHARE, request_id, identification)
        try:
            wallet_svc = WalletShareService(self.db)
            start_time = time.time()
            wallet_result = await wallet_svc.analyze_wallet(identification)
            duration_ms = int((time.time() - start_time) * 1000)
            self._complete_log(wallet_log, duration_ms, f"Share={wallet_result.wallet_share_pct}% Segment={wallet_result.segment}")
        except Exception as e:
            self._fail_log(wallet_log, str(e))
            logger.error("Wallet analysis failed in orchestrator: %s", str(e))
        logs.append(AgentExecutionLogDto.model_validate(wallet_log))

        # --- Step 3: Meeting Brief Preview (cross-agent data) ---
        copilot_log = self._start_log(AgentType.RM_COPILOT, request_id, identification)
        try:
            # Inject health data into the brief generation
            health_score_val = float(health_result.health_score) if health_result else 0
            risk_level_val = health_result.risk_level if health_result else "UNKNOWN"
            health_summary_val = health_result.ai_summary if health_result else "Not assessed"

            initial_state = {
                "identification": identification,
                "meeting_date": "Preview — no meeting scheduled",
                "user_data": None,
                "account_details": "",
                "health_score": health_score_val,
                "risk_level": risk_level_val,
                "health_summary": health_summary_val,
                "brief": "",
                "error": None,
            }
            start_time = time.time()
            final_state = await pre_meeting_brief_graph.ainvoke(initial_state)
            meeting_brief = final_state.get("brief", "")
            duration_ms = int((time.time() - start_time) * 1000)
            self._complete_log(copilot_log, duration_ms, "Brief generated successfully")
        except Exception as e:
            self._fail_log(copilot_log, str(e))
            logger.error("Meeting brief generation failed in orchestrator: %s", str(e))
        logs.append(AgentExecutionLogDto.model_validate(copilot_log))

        self.db.commit()

        return ComprehensiveClientReviewDto(
            identification=identification,
            health_score=health_result,
            wallet_analysis=wallet_result,
            meeting_brief_preview=meeting_brief,
            execution_logs=logs,
        )

    def get_execution_logs(
        self,
        agent_type: Optional[str] = None,
        limit: int = 50,
    ) -> list[AgentExecutionLogDto]:
        """Retrieve agent execution logs with optional filtering by agent type."""
        query = self.db.query(AgentExecutionLog).order_by(AgentExecutionLog.created_at.desc())
        if agent_type:
            query = query.filter(AgentExecutionLog.agent_type == agent_type)
        results = query.limit(limit).all()
        return [AgentExecutionLogDto.model_validate(r) for r in results]

    # --- Private helpers for execution logging ---

    def _start_log(self, agent_type: AgentType, request_id: str, identification: str) -> AgentExecutionLog:
        """Create and persist a STARTED execution log entry."""
        log_entry = AgentExecutionLog(
            agent_type=agent_type.value,
            request_id=request_id,
            user_id=None,
            status=ExecutionStatus.STARTED.value,
            input_summary=f"identification={identification}",
        )
        self.db.add(log_entry)
        self.db.flush()
        return log_entry

    def _complete_log(self, log_entry: AgentExecutionLog, duration_ms: int, output_summary: str) -> None:
        """Update an execution log entry to COMPLETED status."""
        log_entry.status = ExecutionStatus.COMPLETED.value
        log_entry.duration_ms = duration_ms
        log_entry.output_summary = output_summary

    def _fail_log(self, log_entry: AgentExecutionLog, error_message: str) -> None:
        """Update an execution log entry to FAILED status."""
        log_entry.status = ExecutionStatus.FAILED.value
        log_entry.error_message = error_message
