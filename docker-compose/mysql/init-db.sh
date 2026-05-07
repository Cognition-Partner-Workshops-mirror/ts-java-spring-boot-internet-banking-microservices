#!/bin/bash
set -e

# Uses environment variables passed from Docker Compose:
#   MYSQL_APP_USER     - application database user (default: javatodev_development)
#   MYSQL_APP_PASSWORD - application database password (required)

APP_USER="${MYSQL_APP_USER:-javatodev_development}"
APP_PASSWORD="${MYSQL_APP_PASSWORD:?MYSQL_APP_PASSWORD environment variable is required}"

mysql -u root -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
    CREATE USER IF NOT EXISTS '${APP_USER}'@'%' IDENTIFIED BY '${APP_PASSWORD}';
    GRANT CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES ON *.* TO '${APP_USER}'@'%';
    FLUSH PRIVILEGES;

    CREATE DATABASE IF NOT EXISTS banking_core_service;
    CREATE DATABASE IF NOT EXISTS banking_core_fund_transfer_service;
    CREATE DATABASE IF NOT EXISTS banking_core_user_service;
    CREATE DATABASE IF NOT EXISTS banking_core_utility_payment_service;
EOSQL
