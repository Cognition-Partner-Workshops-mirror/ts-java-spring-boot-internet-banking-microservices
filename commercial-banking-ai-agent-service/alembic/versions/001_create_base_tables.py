"""Create base table structure for all three AI agents and the orchestrator execution log.

Revision ID: 001
Revises: None
Create Date: 2025-01-01 00:00:00.000000

Tables created:
- client_health_score: Stores point-in-time health assessments (Agent 1)
- client_health_alert: Alerts for at-risk clients (Agent 1)
- rm_meeting: RM-client meeting lifecycle (Agent 2)
- rm_meeting_action_item: Action items extracted from meeting notes (Agent 2)
- rm_meeting_note: Notes recorded during meetings (Agent 2)
- client_wallet_analysis: Wallet share analysis results (Agent 3)
- wallet_product_recommendation: Cross-sell/up-sell recommendations (Agent 3)
- agent_execution_log: Audit log for all agent executions (Orchestrator)
"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = "001"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # --- Agent 1: Client Health Monitor & Assessment ---
    op.create_table(
        "client_health_score",
        sa.Column("id", sa.BigInteger, primary_key=True, autoincrement=True),
        sa.Column("user_id", sa.BigInteger, nullable=False, comment="FK to banking_core_user.id"),
        sa.Column("identification", sa.String(255), nullable=False, comment="Client NIC"),
        sa.Column("health_score", sa.Numeric(5, 2), nullable=False, comment="Composite score 0-100"),
        sa.Column("risk_level", sa.String(20), nullable=False, comment="HEALTHY / AT_RISK / CRITICAL"),
        sa.Column("balance_score", sa.Numeric(5, 2), comment="Sub-score: balance adequacy"),
        sa.Column("txn_velocity_score", sa.Numeric(5, 2), comment="Sub-score: transaction frequency"),
        sa.Column("payment_regularity_score", sa.Numeric(5, 2), comment="Sub-score: on-time payments"),
        sa.Column("account_diversity_score", sa.Numeric(5, 2), comment="Sub-score: product mix"),
        sa.Column("ai_summary", sa.Text, comment="LLM-generated narrative summary"),
        sa.Column("assessed_at", sa.DateTime, nullable=False, server_default=sa.func.now()),
        sa.Column("created_at", sa.DateTime, server_default=sa.func.now()),
        sa.Column("updated_at", sa.DateTime, server_default=sa.func.now(), onupdate=sa.func.now()),
    )

    op.create_table(
        "client_health_alert",
        sa.Column("id", sa.BigInteger, primary_key=True, autoincrement=True),
        sa.Column("health_score_id", sa.BigInteger, sa.ForeignKey("client_health_score.id"), nullable=False),
        sa.Column("alert_type", sa.String(50), nullable=False, comment="BALANCE_DROP / DORMANT / HIGH_RISK"),
        sa.Column("severity", sa.String(20), nullable=False, comment="LOW / MEDIUM / HIGH / CRITICAL"),
        sa.Column("message", sa.Text, nullable=False),
        sa.Column("acknowledged", sa.Boolean, server_default=sa.text("false")),
        sa.Column("created_at", sa.DateTime, server_default=sa.func.now()),
    )

    # --- Agent 2: RM Copilot (Meeting Assistant) ---
    op.create_table(
        "rm_meeting",
        sa.Column("id", sa.BigInteger, primary_key=True, autoincrement=True),
        sa.Column("rm_user_id", sa.String(255), nullable=False, comment="Keycloak auth ID of RM"),
        sa.Column("client_user_id", sa.BigInteger, nullable=False, comment="banking_core_user.id"),
        sa.Column("client_identification", sa.String(255), nullable=False, comment="Client NIC"),
        sa.Column("meeting_date", sa.DateTime, nullable=False),
        sa.Column("status", sa.String(20), nullable=False, comment="SCHEDULED / IN_PROGRESS / COMPLETED"),
        sa.Column("pre_meeting_brief", sa.Text, comment="LLM-generated brief for the RM"),
        sa.Column("post_meeting_summary", sa.Text, comment="LLM-generated summary after meeting"),
        sa.Column("created_at", sa.DateTime, server_default=sa.func.now()),
        sa.Column("updated_at", sa.DateTime, server_default=sa.func.now(), onupdate=sa.func.now()),
    )

    op.create_table(
        "rm_meeting_action_item",
        sa.Column("id", sa.BigInteger, primary_key=True, autoincrement=True),
        sa.Column("meeting_id", sa.BigInteger, sa.ForeignKey("rm_meeting.id"), nullable=False),
        sa.Column("description", sa.Text, nullable=False),
        sa.Column("assignee", sa.String(255)),
        sa.Column("due_date", sa.Date),
        sa.Column("status", sa.String(20), nullable=False, comment="OPEN / IN_PROGRESS / COMPLETED"),
        sa.Column("priority", sa.String(10), nullable=False, comment="LOW / MEDIUM / HIGH"),
        sa.Column("created_at", sa.DateTime, server_default=sa.func.now()),
        sa.Column("updated_at", sa.DateTime, server_default=sa.func.now(), onupdate=sa.func.now()),
    )

    op.create_table(
        "rm_meeting_note",
        sa.Column("id", sa.BigInteger, primary_key=True, autoincrement=True),
        sa.Column("meeting_id", sa.BigInteger, sa.ForeignKey("rm_meeting.id"), nullable=False),
        sa.Column("content", sa.Text, nullable=False),
        sa.Column("note_type", sa.String(20), nullable=False, comment="MANUAL / AI_GENERATED"),
        sa.Column("created_at", sa.DateTime, server_default=sa.func.now()),
    )

    # --- Agent 3: Wallet Share Agent ---
    op.create_table(
        "client_wallet_analysis",
        sa.Column("id", sa.BigInteger, primary_key=True, autoincrement=True),
        sa.Column("user_id", sa.BigInteger, nullable=False),
        sa.Column("identification", sa.String(255), nullable=False),
        sa.Column("estimated_total_wallet", sa.Numeric(19, 2), comment="Estimated total financial wallet"),
        sa.Column("bank_wallet_share", sa.Numeric(19, 2), comment="Our bank's current share"),
        sa.Column("wallet_share_pct", sa.Numeric(5, 2), comment="Percentage share 0-100"),
        sa.Column("segment", sa.String(50), comment="MASS / AFFLUENT / HNW / UHNW"),
        sa.Column("analysis_summary", sa.Text, comment="LLM-generated insight"),
        sa.Column("analyzed_at", sa.DateTime, nullable=False, server_default=sa.func.now()),
        sa.Column("created_at", sa.DateTime, server_default=sa.func.now()),
        sa.Column("updated_at", sa.DateTime, server_default=sa.func.now(), onupdate=sa.func.now()),
    )

    op.create_table(
        "wallet_product_recommendation",
        sa.Column("id", sa.BigInteger, primary_key=True, autoincrement=True),
        sa.Column("wallet_analysis_id", sa.BigInteger, sa.ForeignKey("client_wallet_analysis.id"), nullable=False),
        sa.Column("product_type", sa.String(100), nullable=False, comment="TERM_DEPOSIT / MORTGAGE / etc."),
        sa.Column("product_name", sa.String(255), nullable=False),
        sa.Column("estimated_revenue", sa.Numeric(19, 2)),
        sa.Column("confidence_score", sa.Numeric(5, 2), comment="0-100"),
        sa.Column("rationale", sa.Text, comment="LLM-generated explanation"),
        sa.Column("status", sa.String(20), nullable=False, comment="SUGGESTED / PRESENTED / ACCEPTED / DECLINED"),
        sa.Column("created_at", sa.DateTime, server_default=sa.func.now()),
        sa.Column("updated_at", sa.DateTime, server_default=sa.func.now(), onupdate=sa.func.now()),
    )

    # --- Agent Orchestration — Execution Log ---
    op.create_table(
        "agent_execution_log",
        sa.Column("id", sa.BigInteger, primary_key=True, autoincrement=True),
        sa.Column("agent_type", sa.String(50), nullable=False, comment="CLIENT_HEALTH / RM_COPILOT / WALLET_SHARE"),
        sa.Column("request_id", sa.String(100), nullable=False),
        sa.Column("user_id", sa.BigInteger),
        sa.Column("status", sa.String(20), nullable=False, comment="STARTED / COMPLETED / FAILED"),
        sa.Column("input_summary", sa.Text),
        sa.Column("output_summary", sa.Text),
        sa.Column("tokens_used", sa.Integer),
        sa.Column("duration_ms", sa.BigInteger),
        sa.Column("error_message", sa.Text),
        sa.Column("created_at", sa.DateTime, server_default=sa.func.now()),
    )


def downgrade() -> None:
    # Drop tables in reverse order of creation (respecting foreign key constraints)
    op.drop_table("agent_execution_log")
    op.drop_table("wallet_product_recommendation")
    op.drop_table("client_wallet_analysis")
    op.drop_table("rm_meeting_note")
    op.drop_table("rm_meeting_action_item")
    op.drop_table("rm_meeting")
    op.drop_table("client_health_alert")
    op.drop_table("client_health_score")
