#!/bin/bash
# Creates the application database user using the password from MYSQL_APP_USER_PASSWORD.
# This script runs as part of MySQL's docker-entrypoint-initdb.d sequence.

APP_USER_PASSWORD="${MYSQL_APP_USER_PASSWORD:-changeme}"

mysql -u root -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
    CREATE USER IF NOT EXISTS 'javatodev_development'@'%' IDENTIFIED BY '${APP_USER_PASSWORD}';
    GRANT CREATE, ALTER, DROP, INSERT, UPDATE, DELETE, SELECT, REFERENCES ON *.* TO 'javatodev_development'@'%';
    FLUSH PRIVILEGES;
EOSQL
