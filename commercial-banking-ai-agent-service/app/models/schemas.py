"""
Pydantic schemas for API request/response serialization.
Organized by agent: Client Health, RM Copilot, Wallet Share, and Orchestrator.
"""

from datetime import date, datetime
from decimal import Decimal
from typing import Optional

from pydantic import BaseModel, Field


# ---------------------------------------------------------------------------
# Core Banking DTOs (responses from core-banking-service)
# ---------------------------------------------------------------------------


class UserResponse(BaseModel):
    """User data returned by core-banking-service."""
    id: Optional[int] = None
    first_name: Optional[str] = Field(None, alias="firstName")
    last_name: Optional[str] = Field(None, alias="lastName")
    email: Optional[str] = None
    identification_number: Optional[str] = Field(None, alias="identificationNumber")

    model_config = {"populate_by_name": True}


class BankAccountResponse(BaseModel):
    """Bank account data returned by core-banking-service."""
    id: Optional[int] = None
    number: Optional[str] = None
    type: Optional[str] = None
    status: Optional[str] = None
    actual_balance: Optional[Decimal] = Field(None, alias="actualBalance")
    available_balance: Optional[Decimal] = Field(None, alias="availableBalance")

    model_config = {"populate_by_name": True}


class TransactionResponse(BaseModel):
    """Transaction data returned by core-banking-service."""
    id: Optional[int] = None
    amount: Optional[Decimal] = None
    transaction_type: Optional[str] = Field(None, alias="transactionType")
    reference_number: Optional[str] = Field(None, alias="referenceNumber")
    transaction_id: Optional[str] = Field(None, alias="transactionId")

    model_config = {"populate_by_name": True}


# ---------------------------------------------------------------------------
# Agent 1: Client Health Monitor & Assessment
# ---------------------------------------------------------------------------


class ClientHealthScoreDto(BaseModel):
    """Response schema for a client health assessment."""
    id: Optional[int] = None
    user_id: int
    identification: str
    health_score: Decimal
    risk_level: str
    balance_score: Optional[Decimal] = None
    txn_velocity_score: Optional[Decimal] = None
    payment_regularity_score: Optional[Decimal] = None
    account_diversity_score: Optional[Decimal] = None
    ai_summary: Optional[str] = None
    assessed_at: Optional[datetime] = None

    model_config = {"from_attributes": True}


class ClientHealthAlertDto(BaseModel):
    """Response schema for a health alert."""
    id: Optional[int] = None
    health_score_id: int
    alert_type: str
    severity: str
    message: str
    acknowledged: bool = False
    created_at: Optional[datetime] = None

    model_config = {"from_attributes": True}


# ---------------------------------------------------------------------------
# Agent 2: RM Copilot (Meeting Assistant)
# ---------------------------------------------------------------------------


class ScheduleMeetingRequest(BaseModel):
    """Request to schedule a new RM-client meeting."""
    rm_user_id: str = Field(..., description="Keycloak auth ID of the RM")
    client_identification: str = Field(..., description="Client NIC / identification number")
    meeting_date: datetime = Field(..., description="Scheduled meeting date and time")


class AddNoteRequest(BaseModel):
    """Request to add a note during a meeting."""
    content: str = Field(..., description="Note content")
    note_type: str = Field("MANUAL", description="MANUAL or AI_GENERATED")


class UpdateActionItemRequest(BaseModel):
    """Request to update an action item's status."""
    status: str = Field(..., description="OPEN / IN_PROGRESS / COMPLETED")


class RmMeetingNoteDto(BaseModel):
    """Response schema for a meeting note."""
    id: Optional[int] = None
    meeting_id: int
    content: str
    note_type: str
    created_at: Optional[datetime] = None

    model_config = {"from_attributes": True}


class ActionItemDto(BaseModel):
    """Response schema for a meeting action item."""
    id: Optional[int] = None
    meeting_id: Optional[int] = None
    description: str
    assignee: Optional[str] = None
    due_date: Optional[date] = None
    status: str = "OPEN"
    priority: str = "MEDIUM"
    created_at: Optional[datetime] = None

    model_config = {"from_attributes": True}


class RmMeetingDto(BaseModel):
    """Response schema for a meeting including brief, summary, notes, and action items."""
    id: Optional[int] = None
    rm_user_id: str
    client_user_id: int
    client_identification: str
    meeting_date: datetime
    status: str
    pre_meeting_brief: Optional[str] = None
    post_meeting_summary: Optional[str] = None
    action_items: list[ActionItemDto] = []
    notes: list[RmMeetingNoteDto] = []
    created_at: Optional[datetime] = None

    model_config = {"from_attributes": True}


# ---------------------------------------------------------------------------
# Agent 3: Wallet Share Agent
# ---------------------------------------------------------------------------


class ProductRecommendationDto(BaseModel):
    """Response schema for a product recommendation."""
    id: Optional[int] = None
    wallet_analysis_id: Optional[int] = None
    product_type: str
    product_name: str
    estimated_revenue: Optional[Decimal] = None
    confidence_score: Optional[Decimal] = None
    rationale: Optional[str] = None
    status: str = "SUGGESTED"

    model_config = {"from_attributes": True}


class UpdateRecommendationRequest(BaseModel):
    """Request to update a recommendation's status."""
    status: str = Field(..., description="SUGGESTED / PRESENTED / ACCEPTED / DECLINED")


class ClientWalletAnalysisDto(BaseModel):
    """Response schema for a wallet share analysis."""
    id: Optional[int] = None
    user_id: int
    identification: str
    estimated_total_wallet: Optional[Decimal] = None
    bank_wallet_share: Optional[Decimal] = None
    wallet_share_pct: Optional[Decimal] = None
    segment: Optional[str] = None
    analysis_summary: Optional[str] = None
    analyzed_at: Optional[datetime] = None
    recommendations: list[ProductRecommendationDto] = []

    model_config = {"from_attributes": True}


# ---------------------------------------------------------------------------
# Agent Orchestrator
# ---------------------------------------------------------------------------


class AgentExecutionLogDto(BaseModel):
    """Response schema for an agent execution log entry."""
    id: Optional[int] = None
    agent_type: str
    request_id: str
    user_id: Optional[int] = None
    status: str
    input_summary: Optional[str] = None
    output_summary: Optional[str] = None
    tokens_used: Optional[int] = None
    duration_ms: Optional[int] = None
    error_message: Optional[str] = None
    created_at: Optional[datetime] = None

    model_config = {"from_attributes": True}


class ComprehensiveClientReviewDto(BaseModel):
    """Response from the orchestrator running all three agents on a single client."""
    identification: str
    health_score: Optional[ClientHealthScoreDto] = None
    wallet_analysis: Optional[ClientWalletAnalysisDto] = None
    meeting_brief_preview: Optional[str] = None
    execution_logs: list[AgentExecutionLogDto] = []
