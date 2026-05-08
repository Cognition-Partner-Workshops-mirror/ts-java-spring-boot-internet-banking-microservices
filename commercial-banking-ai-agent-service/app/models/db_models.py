"""
SQLAlchemy ORM models for the AI agent service database (banking_ai_agent_service).
Covers all three agents plus the orchestrator execution log.
"""

from datetime import date, datetime

from sqlalchemy import (
    BigInteger,
    Boolean,
    Column,
    Date,
    DateTime,
    Integer,
    Numeric,
    String,
    Text,
    ForeignKey,
)
from sqlalchemy.orm import relationship

from app.db.database import Base


# ---------------------------------------------------------------------------
# Agent 1: Client Health Monitor & Assessment
# ---------------------------------------------------------------------------


class ClientHealthScore(Base):
    """
    Stores a point-in-time health assessment for a banking client.
    Includes rule-based sub-scores and an LLM-generated narrative summary.
    """

    __tablename__ = "client_health_score"

    id: int = Column(BigInteger, primary_key=True, autoincrement=True)
    user_id: int = Column(BigInteger, nullable=False, comment="FK to banking_core_user.id")
    identification: str = Column(String(255), nullable=False, comment="Client NIC")
    health_score: float = Column(Numeric(5, 2), nullable=False, comment="Composite score 0-100")
    risk_level: str = Column(String(20), nullable=False, comment="HEALTHY / AT_RISK / CRITICAL")
    balance_score: float = Column(Numeric(5, 2), comment="Sub-score: balance adequacy")
    txn_velocity_score: float = Column(Numeric(5, 2), comment="Sub-score: transaction frequency")
    payment_regularity_score: float = Column(Numeric(5, 2), comment="Sub-score: on-time payments")
    account_diversity_score: float = Column(Numeric(5, 2), comment="Sub-score: product mix")
    ai_summary: str = Column(Text, comment="LLM-generated narrative summary")
    assessed_at: datetime = Column(DateTime, nullable=False, default=datetime.utcnow)
    created_at: datetime = Column(DateTime, default=datetime.utcnow)
    updated_at: datetime = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationship to alerts generated from this score
    alerts = relationship("ClientHealthAlert", back_populates="health_score_rel", cascade="all, delete-orphan")


class ClientHealthAlert(Base):
    """
    Alert raised when a client's health score drops below configured thresholds.
    Linked to the health score that triggered it.
    """

    __tablename__ = "client_health_alert"

    id: int = Column(BigInteger, primary_key=True, autoincrement=True)
    health_score_id: int = Column(BigInteger, ForeignKey("client_health_score.id"), nullable=False)
    alert_type: str = Column(String(50), nullable=False, comment="BALANCE_DROP / DORMANT / HIGH_RISK")
    severity: str = Column(String(20), nullable=False, comment="LOW / MEDIUM / HIGH / CRITICAL")
    message: str = Column(Text, nullable=False)
    acknowledged: bool = Column(Boolean, default=False)
    created_at: datetime = Column(DateTime, default=datetime.utcnow)

    # Back-reference to the parent health score
    health_score_rel = relationship("ClientHealthScore", back_populates="alerts")


# ---------------------------------------------------------------------------
# Agent 2: RM Copilot (Meeting Assistant)
# ---------------------------------------------------------------------------


class RmMeeting(Base):
    """
    Represents a scheduled or completed meeting between an RM and a client.
    Contains LLM-generated pre-meeting brief and post-meeting summary.
    """

    __tablename__ = "rm_meeting"

    id: int = Column(BigInteger, primary_key=True, autoincrement=True)
    rm_user_id: str = Column(String(255), nullable=False, comment="Keycloak auth ID of the RM")
    client_user_id: int = Column(BigInteger, nullable=False, comment="banking_core_user.id")
    client_identification: str = Column(String(255), nullable=False, comment="Client NIC")
    meeting_date: datetime = Column(DateTime, nullable=False)
    status: str = Column(String(20), nullable=False, comment="SCHEDULED / IN_PROGRESS / COMPLETED")
    pre_meeting_brief: str = Column(Text, comment="LLM-generated brief for the RM")
    post_meeting_summary: str = Column(Text, comment="LLM-generated summary after meeting")
    created_at: datetime = Column(DateTime, default=datetime.utcnow)
    updated_at: datetime = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationships
    action_items = relationship("RmMeetingActionItem", back_populates="meeting", cascade="all, delete-orphan")
    notes = relationship("RmMeetingNote", back_populates="meeting", cascade="all, delete-orphan")


class RmMeetingActionItem(Base):
    """
    Action item extracted by LLM from meeting notes.
    Tracks assignee, due date, priority, and completion status.
    """

    __tablename__ = "rm_meeting_action_item"

    id: int = Column(BigInteger, primary_key=True, autoincrement=True)
    meeting_id: int = Column(BigInteger, ForeignKey("rm_meeting.id"), nullable=False)
    description: str = Column(Text, nullable=False)
    assignee: str = Column(String(255))
    due_date: date = Column(Date)
    status: str = Column(String(20), nullable=False, comment="OPEN / IN_PROGRESS / COMPLETED")
    priority: str = Column(String(10), nullable=False, comment="LOW / MEDIUM / HIGH")
    created_at: datetime = Column(DateTime, default=datetime.utcnow)
    updated_at: datetime = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Back-reference to the parent meeting
    meeting = relationship("RmMeeting", back_populates="action_items")


class RmMeetingNote(Base):
    """
    Note recorded during a meeting, either manually by the RM or auto-generated by AI.
    """

    __tablename__ = "rm_meeting_note"

    id: int = Column(BigInteger, primary_key=True, autoincrement=True)
    meeting_id: int = Column(BigInteger, ForeignKey("rm_meeting.id"), nullable=False)
    content: str = Column(Text, nullable=False)
    note_type: str = Column(String(20), nullable=False, comment="MANUAL / AI_GENERATED")
    created_at: datetime = Column(DateTime, default=datetime.utcnow)

    # Back-reference to the parent meeting
    meeting = relationship("RmMeeting", back_populates="notes")


# ---------------------------------------------------------------------------
# Agent 3: Wallet Share Agent
# ---------------------------------------------------------------------------


class ClientWalletAnalysis(Base):
    """
    Stores the estimated wallet share analysis for a client.
    Includes segmentation, LLM-generated insight, and linked product recommendations.
    """

    __tablename__ = "client_wallet_analysis"

    id: int = Column(BigInteger, primary_key=True, autoincrement=True)
    user_id: int = Column(BigInteger, nullable=False)
    identification: str = Column(String(255), nullable=False)
    estimated_total_wallet: float = Column(Numeric(19, 2), comment="Estimated total financial wallet")
    bank_wallet_share: float = Column(Numeric(19, 2), comment="Our bank's current share")
    wallet_share_pct: float = Column(Numeric(5, 2), comment="Percentage share 0-100")
    segment: str = Column(String(50), comment="MASS / AFFLUENT / HNW / UHNW")
    analysis_summary: str = Column(Text, comment="LLM-generated insight")
    analyzed_at: datetime = Column(DateTime, nullable=False, default=datetime.utcnow)
    created_at: datetime = Column(DateTime, default=datetime.utcnow)
    updated_at: datetime = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Relationship to product recommendations
    recommendations = relationship(
        "WalletProductRecommendation", back_populates="wallet_analysis", cascade="all, delete-orphan"
    )


class WalletProductRecommendation(Base):
    """
    A cross-sell / up-sell product recommendation generated by the wallet share agent.
    Includes an LLM-generated rationale and a confidence score.
    """

    __tablename__ = "wallet_product_recommendation"

    id: int = Column(BigInteger, primary_key=True, autoincrement=True)
    wallet_analysis_id: int = Column(
        BigInteger, ForeignKey("client_wallet_analysis.id"), nullable=False
    )
    product_type: str = Column(String(100), nullable=False, comment="TERM_DEPOSIT / MORTGAGE / etc.")
    product_name: str = Column(String(255), nullable=False)
    estimated_revenue: float = Column(Numeric(19, 2))
    confidence_score: float = Column(Numeric(5, 2), comment="0-100")
    rationale: str = Column(Text, comment="LLM-generated explanation")
    status: str = Column(String(20), nullable=False, comment="SUGGESTED / PRESENTED / ACCEPTED / DECLINED")
    created_at: datetime = Column(DateTime, default=datetime.utcnow)
    updated_at: datetime = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # Back-reference to the parent wallet analysis
    wallet_analysis = relationship("ClientWalletAnalysis", back_populates="recommendations")


# ---------------------------------------------------------------------------
# Agent Orchestration — Execution Log
# ---------------------------------------------------------------------------


class AgentExecutionLog(Base):
    """
    Audit log entry for every agent execution.
    Tracks agent type, input/output summary, token usage, and duration.
    """

    __tablename__ = "agent_execution_log"

    id: int = Column(BigInteger, primary_key=True, autoincrement=True)
    agent_type: str = Column(String(50), nullable=False, comment="CLIENT_HEALTH / RM_COPILOT / WALLET_SHARE")
    request_id: str = Column(String(100), nullable=False)
    user_id: int = Column(BigInteger)
    status: str = Column(String(20), nullable=False, comment="STARTED / COMPLETED / FAILED")
    input_summary: str = Column(Text)
    output_summary: str = Column(Text)
    tokens_used: int = Column(Integer)
    duration_ms: int = Column(BigInteger)
    error_message: str = Column(Text)
    created_at: datetime = Column(DateTime, default=datetime.utcnow)
