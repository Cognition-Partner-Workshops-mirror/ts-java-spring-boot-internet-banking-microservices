# Commercial Banking AI Agent Service

Multi-agent AI system for commercial banking powered by **Python**, **FastAPI**, and **LangGraph**.

## Agents

| # | Agent | Description | Prefix |
|---|-------|-------------|--------|
| 1 | **Client Health Monitor** | Scores client financial health using rule-based engine + LLM narrative | `/api/v1/health` |
| 2 | **RM Copilot (Meeting Assistant)** | Generates pre-meeting briefs, post-meeting summaries, extracts action items | `/api/v1/meetings` |
| 3 | **Wallet Share Agent** | Estimates bank wallet share, recommends cross-sell products | `/api/v1/wallet` |
| - | **Orchestrator** | Coordinates all three agents for comprehensive client reviews | `/api/v1/orchestrator` |

## Architecture

Each agent is implemented as a **LangGraph state machine** with clearly defined nodes:

- **Client Health**: `fetch_data → score → llm_summary → alerts → persist`
- **RM Copilot (pre-meeting)**: `fetch_context → health_data → generate_brief`
- **RM Copilot (post-meeting)**: `generate_summary → extract_action_items`
- **Wallet Share**: `fetch_data → compute_metrics → llm_recommendations → assemble`

Cross-agent data sharing: health scores feed into meeting briefs and wallet analysis.

## Quick Start

```bash
# Install dependencies
pip install -r requirements.txt

# Set environment variables (copy and edit .env.example)
cp .env.example .env

# Run the service
uvicorn app.main:app --host 0.0.0.0 --port 8093 --reload

# Run tests
pytest tests/ -v
```

## Docker

```bash
# From the docker-compose directory
docker-compose up --build commercial-banking-ai-agent-service
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `AI_AGENT_DATABASE_URL` | MySQL connection string | `mysql+pymysql://...@localhost:3306/banking_ai_agent_service` |
| `AI_AGENT_CORE_BANKING_BASE_URL` | Core banking service URL | `http://localhost:8092` |
| `AI_AGENT_OPENAI_API_KEY` | OpenAI API key (optional — falls back to rule-based) | _(empty)_ |
| `AI_AGENT_OPENAI_MODEL` | LLM model name | `gpt-4o-mini` |

## Database

Uses MySQL schema `banking_ai_agent_service` with 8 tables managed by Alembic migrations.

```bash
# Run migrations
alembic upgrade head
```

## API Docs

Interactive Swagger UI available at `http://localhost:8093/docs` when running locally.
