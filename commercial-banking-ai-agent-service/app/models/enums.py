"""
Shared enumerations used across all three agents and the orchestrator.
"""

import enum


class RiskLevel(str, enum.Enum):
    """Client financial health risk classification."""
    HEALTHY = "HEALTHY"
    AT_RISK = "AT_RISK"
    CRITICAL = "CRITICAL"


class AlertType(str, enum.Enum):
    """Types of health alerts generated for at-risk clients."""
    BALANCE_DROP = "BALANCE_DROP"
    DORMANT = "DORMANT"
    HIGH_RISK = "HIGH_RISK"
    PAYMENT_IRREGULAR = "PAYMENT_IRREGULAR"


class Severity(str, enum.Enum):
    """Alert severity levels."""
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"


class MeetingStatus(str, enum.Enum):
    """Lifecycle status of an RM-client meeting."""
    SCHEDULED = "SCHEDULED"
    IN_PROGRESS = "IN_PROGRESS"
    COMPLETED = "COMPLETED"


class ActionItemStatus(str, enum.Enum):
    """Status of a meeting action item."""
    OPEN = "OPEN"
    IN_PROGRESS = "IN_PROGRESS"
    COMPLETED = "COMPLETED"


class Priority(str, enum.Enum):
    """Priority level for action items."""
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"


class NoteType(str, enum.Enum):
    """Origin type of a meeting note."""
    MANUAL = "MANUAL"
    AI_GENERATED = "AI_GENERATED"


class RecommendationStatus(str, enum.Enum):
    """Lifecycle status of a product recommendation."""
    SUGGESTED = "SUGGESTED"
    PRESENTED = "PRESENTED"
    ACCEPTED = "ACCEPTED"
    DECLINED = "DECLINED"


class AgentType(str, enum.Enum):
    """Identifies which agent performed an execution."""
    CLIENT_HEALTH = "CLIENT_HEALTH"
    RM_COPILOT = "RM_COPILOT"
    WALLET_SHARE = "WALLET_SHARE"


class ExecutionStatus(str, enum.Enum):
    """Status of an agent execution run."""
    STARTED = "STARTED"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"
