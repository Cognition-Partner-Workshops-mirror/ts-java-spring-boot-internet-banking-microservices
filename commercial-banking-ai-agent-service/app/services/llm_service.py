"""
LLM integration layer using LangChain ChatOpenAI.
Provides structured prompt execution for all three agents.
Falls back to rule-based responses when OpenAI API key is not configured.
"""

import json
import logging
from typing import Optional

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI

from app.core.config import settings

logger = logging.getLogger(__name__)


class LlmService:
    """Abstraction over LLM calls; used by all three agent graphs."""

    def __init__(self):
        # Initialize ChatOpenAI only if an API key is configured
        self._llm: Optional[ChatOpenAI] = None
        if settings.openai_api_key:
            self._llm = ChatOpenAI(
                model=settings.openai_model,
                temperature=settings.openai_temperature,
                api_key=settings.openai_api_key,
            )
            logger.info("LLM service initialized with model=%s", settings.openai_model)
        else:
            logger.warning(
                "No OpenAI API key configured — LLM calls will return rule-based fallbacks"
            )

    @property
    def is_available(self) -> bool:
        """Check whether the LLM backend is configured and ready."""
        return self._llm is not None

    async def invoke(self, system_prompt: str, user_prompt: str) -> str:
        """
        Send a system + user prompt pair to the LLM and return the text response.
        Returns a fallback message when the LLM is not configured.
        """
        if not self._llm:
            return "[LLM not configured] — provide AI_AGENT_OPENAI_API_KEY to enable AI summaries."

        try:
            messages = [
                SystemMessage(content=system_prompt),
                HumanMessage(content=user_prompt),
            ]
            response = await self._llm.ainvoke(messages)
            return response.content
        except Exception as e:
            logger.error("LLM invocation failed: %s", str(e))
            return f"[LLM error] {str(e)}"

    async def invoke_json(self, system_prompt: str, user_prompt: str) -> dict:
        """
        Send a prompt expecting a JSON response. Parses the response into a dict.
        Returns an empty dict on parse failure.
        """
        raw = await self.invoke(system_prompt, user_prompt)
        # Strip markdown code fences if present
        cleaned = raw.strip()
        if cleaned.startswith("```"):
            lines = cleaned.split("\n")
            # Remove first and last lines (code fences)
            lines = [l for l in lines if not l.strip().startswith("```")]
            cleaned = "\n".join(lines)
        try:
            return json.loads(cleaned)
        except json.JSONDecodeError:
            logger.warning("Failed to parse LLM JSON response: %s", raw[:200])
            return {}


# Singleton instance used across the application
llm_service = LlmService()
