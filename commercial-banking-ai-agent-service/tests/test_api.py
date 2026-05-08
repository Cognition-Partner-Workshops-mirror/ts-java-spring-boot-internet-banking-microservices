"""
Integration tests for FastAPI endpoints.
Uses TestClient with an in-memory SQLite database to validate API contracts.
"""

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.db.database import Base, get_db
from app.main import app

# Use in-memory SQLite for fast test isolation
SQLALCHEMY_DATABASE_URL = "sqlite://"

engine = create_engine(
    SQLALCHEMY_DATABASE_URL,
    connect_args={"check_same_thread": False},
    poolclass=StaticPool,
)
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def override_get_db():
    """Override the database dependency to use the test SQLite database."""
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()


# Override the database dependency for all tests
app.dependency_overrides[get_db] = override_get_db


@pytest.fixture(autouse=True)
def setup_database():
    """Create all tables before each test and drop them after."""
    Base.metadata.create_all(bind=engine)
    yield
    Base.metadata.drop_all(bind=engine)


@pytest.fixture
def client():
    """Provide a FastAPI test client."""
    return TestClient(app)


# ---------------------------------------------------------------------------
# Health Check / Actuator Tests
# ---------------------------------------------------------------------------


class TestActuator:
    """Tests for the actuator health and info endpoints."""

    def test_health_check(self, client):
        """Health check endpoint should return UP status."""
        response = client.get("/actuator/health")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "UP"

    def test_info_endpoint(self, client):
        """Info endpoint should return service metadata and agent list."""
        response = client.get("/actuator/info")
        assert response.status_code == 200
        data = response.json()
        assert data["app"]["name"] == "commercial-banking-ai-agent-service"
        assert len(data["agents"]) == 4


# ---------------------------------------------------------------------------
# Client Health Routes Tests
# ---------------------------------------------------------------------------


class TestHealthRoutes:
    """Tests for the Client Health Monitor API endpoints."""

    def test_get_latest_score_not_found(self, client):
        """Requesting a score for a non-existent client should return 404."""
        response = client.get("/api/v1/health/score/NON_EXISTENT")
        assert response.status_code == 404

    def test_get_active_alerts_empty(self, client):
        """Active alerts endpoint should return empty list when no alerts exist."""
        response = client.get("/api/v1/health/alerts")
        assert response.status_code == 200
        assert response.json() == []

    def test_acknowledge_alert_not_found(self, client):
        """Acknowledging a non-existent alert should return 404."""
        response = client.put("/api/v1/health/alerts/999/acknowledge")
        assert response.status_code == 404

    def test_get_score_trend_empty(self, client):
        """Score trend for a new client should return an empty list."""
        response = client.get("/api/v1/health/score/NEW_CLIENT/trend")
        assert response.status_code == 200
        assert response.json() == []


# ---------------------------------------------------------------------------
# Meeting Routes Tests
# ---------------------------------------------------------------------------


class TestMeetingRoutes:
    """Tests for the RM Copilot Meeting API endpoints."""

    def test_get_meeting_not_found(self, client):
        """Requesting a non-existent meeting should return 404."""
        response = client.get("/api/v1/meetings/999")
        assert response.status_code == 404

    def test_get_meetings_for_rm_empty(self, client):
        """Meetings list for a new RM should return an empty list."""
        response = client.get("/api/v1/meetings/rm/new_rm_user")
        assert response.status_code == 200
        assert response.json() == []

    def test_get_open_action_items_empty(self, client):
        """Action items for a new RM should return an empty list."""
        response = client.get("/api/v1/meetings/rm/new_rm_user/action-items")
        assert response.status_code == 200
        assert response.json() == []

    def test_update_action_item_not_found(self, client):
        """Updating a non-existent action item should return 404."""
        response = client.put(
            "/api/v1/meetings/action-items/999",
            json={"status": "COMPLETED"},
        )
        assert response.status_code == 404


# ---------------------------------------------------------------------------
# Wallet Routes Tests
# ---------------------------------------------------------------------------


class TestWalletRoutes:
    """Tests for the Wallet Share Agent API endpoints."""

    def test_get_latest_analysis_not_found(self, client):
        """Requesting analysis for a non-existent client should return 404."""
        response = client.get("/api/v1/wallet/analysis/NON_EXISTENT")
        assert response.status_code == 404

    def test_get_analysis_trend_empty(self, client):
        """Analysis trend for a new client should return an empty list."""
        response = client.get("/api/v1/wallet/analysis/NEW_CLIENT/trend")
        assert response.status_code == 200
        assert response.json() == []

    def test_update_recommendation_not_found(self, client):
        """Updating a non-existent recommendation should return 404."""
        response = client.put(
            "/api/v1/wallet/recommendations/999",
            json={"status": "PRESENTED"},
        )
        assert response.status_code == 404


# ---------------------------------------------------------------------------
# Orchestrator Routes Tests
# ---------------------------------------------------------------------------


class TestOrchestratorRoutes:
    """Tests for the Agent Orchestrator API endpoints."""

    def test_get_execution_logs_empty(self, client):
        """Execution logs should return an empty list when no agents have run."""
        response = client.get("/api/v1/orchestrator/logs")
        assert response.status_code == 200
        assert response.json() == []

    def test_get_execution_logs_with_filter(self, client):
        """Execution logs with agent_type filter should return empty list initially."""
        response = client.get("/api/v1/orchestrator/logs?agent_type=CLIENT_HEALTH&limit=10")
        assert response.status_code == 200
        assert response.json() == []
