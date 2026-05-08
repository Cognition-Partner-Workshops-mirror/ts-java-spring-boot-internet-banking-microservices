"""
FastAPI application entry point for the Commercial Banking AI Agent Service.
Registers all agent routers, configures CORS, and manages database table creation on startup.
"""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.health_routes import router as health_router
from app.api.meeting_routes import router as meeting_router
from app.api.orchestrator_routes import router as orchestrator_router
from app.api.wallet_routes import router as wallet_router
from app.core.config import settings
from app.db.database import Base, engine

# Configure logging for the entire application
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    Application lifespan handler: creates all database tables on startup
    and performs cleanup on shutdown.
    """
    logger.info("Starting Commercial Banking AI Agent Service on port %s", settings.port)
    # Create all ORM tables if they don't exist (safe for development; use Alembic in production)
    Base.metadata.create_all(bind=engine)
    logger.info("Database tables verified/created")
    yield
    logger.info("Shutting down Commercial Banking AI Agent Service")


# Create the FastAPI application instance
app = FastAPI(
    title="Commercial Banking AI Agent Service",
    description=(
        "Multi-agent AI system for commercial banking powered by LangGraph. "
        "Provides Client Health Monitoring, RM Copilot Meeting Assistant, "
        "and Wallet Share Analysis agents with an orchestrator for comprehensive reviews."
    ),
    version="1.0.0",
    lifespan=lifespan,
)

# Configure CORS to allow the API Gateway and frontend clients
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Register all agent routers
app.include_router(health_router)
app.include_router(meeting_router)
app.include_router(wallet_router)
app.include_router(orchestrator_router)


@app.get("/actuator/health", tags=["Actuator"])
async def health_check():
    """Health check endpoint compatible with Spring Boot Actuator conventions."""
    return {
        "status": "UP",
        "service": settings.service_name,
        "llm_configured": bool(settings.openai_api_key),
    }


@app.get("/actuator/info", tags=["Actuator"])
async def info():
    """Service info endpoint compatible with Spring Boot Actuator conventions."""
    return {
        "app": {
            "name": "commercial-banking-ai-agent-service",
            "description": "Multi-Agent AI System for Commercial Banking",
            "version": "1.0.0",
        },
        "agents": [
            {"name": "Client Health Monitor", "prefix": "/api/v1/health"},
            {"name": "RM Copilot Meeting Assistant", "prefix": "/api/v1/meetings"},
            {"name": "Wallet Share Agent", "prefix": "/api/v1/wallet"},
            {"name": "Agent Orchestrator", "prefix": "/api/v1/orchestrator"},
        ],
    }
