FROM mysql:8.4.0
COPY ./privileges.sql /docker-entrypoint-initdb.d/01-databases.sql
COPY ./init-user.sh /docker-entrypoint-initdb.d/02-init-user.sh
