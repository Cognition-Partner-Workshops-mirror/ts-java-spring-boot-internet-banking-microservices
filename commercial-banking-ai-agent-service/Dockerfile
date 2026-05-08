# Multi-stage Dockerfile for the Commercial Banking AI Agent Service (Python/FastAPI)
FROM python:3.12-slim AS base

# Set working directory inside the container
WORKDIR /app

# Install system dependencies required by pymysql and cryptography
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# Copy and install Python dependencies first (for Docker layer caching)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy the application source code
COPY . .

# Expose the service port (matches config default and docker-compose mapping)
EXPOSE 8093

# Run the FastAPI application with uvicorn
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8093"]
