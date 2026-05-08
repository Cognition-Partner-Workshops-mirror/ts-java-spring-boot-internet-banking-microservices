"""
HTTP client for the core-banking-service REST API.
Replaces Java Feign clients with httpx-based async calls.
Fetches user, account, and transaction data needed by the AI agents.
"""

import logging
from typing import Optional

import httpx

from app.core.config import settings
from app.models.schemas import BankAccountResponse, TransactionResponse, UserResponse

logger = logging.getLogger(__name__)


class CoreBankingClient:
    """REST client that communicates with the core-banking-service."""

    def __init__(self, base_url: Optional[str] = None):
        # Base URL of the core-banking-service (e.g. http://core-banking-service:8092)
        self.base_url = (base_url or settings.core_banking_base_url).rstrip("/")

    async def get_user(self, identification: str) -> Optional[UserResponse]:
        """
        Fetch a user by their identification (NIC) from core-banking-service.
        Endpoint: GET /api/v1/user/{identification}
        """
        url = f"{self.base_url}/api/v1/user/{identification}"
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                response = await client.get(url)
                response.raise_for_status()
                return UserResponse.model_validate(response.json())
        except httpx.HTTPStatusError as e:
            logger.error("Failed to fetch user %s: HTTP %s", identification, e.response.status_code)
            return None
        except Exception as e:
            logger.error("Error fetching user %s: %s", identification, str(e))
            return None

    async def get_users(self, page: int = 0, size: int = 20) -> list[UserResponse]:
        """
        Fetch a paginated list of users from core-banking-service.
        Endpoint: GET /api/v1/user?page={page}&size={size}
        """
        url = f"{self.base_url}/api/v1/user"
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                response = await client.get(url, params={"page": page, "size": size})
                response.raise_for_status()
                data = response.json()
                # Response may be a list or a paginated wrapper
                if isinstance(data, list):
                    return [UserResponse.model_validate(u) for u in data]
                return [UserResponse.model_validate(u) for u in data.get("content", data)]
        except Exception as e:
            logger.error("Error fetching users: %s", str(e))
            return []

    async def get_bank_account(self, account_number: str) -> Optional[BankAccountResponse]:
        """
        Fetch a bank account by account number from core-banking-service.
        Endpoint: GET /api/v1/account/bank-account/{account_number}
        """
        url = f"{self.base_url}/api/v1/account/bank-account/{account_number}"
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                response = await client.get(url)
                response.raise_for_status()
                return BankAccountResponse.model_validate(response.json())
        except httpx.HTTPStatusError as e:
            logger.error(
                "Failed to fetch account %s: HTTP %s", account_number, e.response.status_code
            )
            return None
        except Exception as e:
            logger.error("Error fetching account %s: %s", account_number, str(e))
            return None

    async def get_transactions(self, account_number: str) -> list[TransactionResponse]:
        """
        Fetch transactions for an account from core-banking-service.
        Note: The current core-banking-service does not expose a transactions-by-account
        endpoint, so this returns an empty list as a placeholder for future integration.
        """
        logger.info(
            "Transaction fetch for account %s: endpoint not yet available in core-banking-service",
            account_number,
        )
        return []


# Singleton instance used across the application
core_banking_client = CoreBankingClient()
