"""
Application configuration loaded from environment variables.
Provides settings for database, core-banking API, OpenAI, and server.
"""

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """Central configuration for the AI agent service."""

    # --- Database (MySQL via SQLAlchemy) ---
    database_url: str = (
        "mysql+pymysql://javatodev_development:oPItyPticIAt"
        "@localhost:3306/banking_ai_agent_service"
    )

    # --- Core Banking Service base URL (REST client target) ---
    core_banking_base_url: str = "http://localhost:8092"

    # --- OpenAI / LLM settings ---
    openai_api_key: str = ""
    openai_model: str = "gpt-4o-mini"
    openai_temperature: float = 0.3

    # --- Server ---
    host: str = "0.0.0.0"
    port: int = 8093
    debug: bool = False

    # --- Eureka (optional, for service discovery) ---
    eureka_server_url: str = "http://localhost:8081/eureka"
    service_name: str = "commercial-banking-ai-agent-service"

    model_config = {"env_prefix": "AI_AGENT_", "env_file": ".env"}


# Singleton settings instance used across the application
settings = Settings()
