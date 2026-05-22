-- =====================================================================
-- Oracle Database Schema for Case Management API
-- =====================================================================
-- This script creates the tables, sequences, and indexes required
-- for the Case Management API application.
-- Run this script against the Oracle database before deploying the
-- application with the 'oracle' profile.
-- =====================================================================

-- =====================================================================
-- SEQUENCES - Auto-increment ID generators
-- =====================================================================

-- Sequence for CASE_DATA_SET1 table primary key
CREATE SEQUENCE CASE_SET1_SEQ
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- Sequence for CASE_DATA_SET2 table primary key
CREATE SEQUENCE CASE_SET2_SEQ
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- Sequence for API_REQUEST_LOG table primary key
CREATE SEQUENCE API_LOG_SEQ
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- =====================================================================
-- TABLE: CASE_DATA_SET1
-- Stores case data received from the Set 1 API endpoints.
-- =====================================================================
CREATE TABLE CASE_DATA_SET1 (
    ID                NUMBER(19)     PRIMARY KEY,
    CASE_NUMBER       VARCHAR2(50)   NOT NULL,
    VERSION_NUMBER    VARCHAR2(20)   NOT NULL,
    AGENT_NAME        VARCHAR2(100)  NOT NULL,
    PARTY_IDENTIFIER  VARCHAR2(100)  NOT NULL,
    SM_USER           VARCHAR2(100)  NOT NULL,
    CREATED_AT        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UPDATED_AT        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for common query patterns on CASE_DATA_SET1
CREATE INDEX IDX_SET1_CASE_NUMBER ON CASE_DATA_SET1(CASE_NUMBER);
CREATE INDEX IDX_SET1_AGENT_NAME ON CASE_DATA_SET1(AGENT_NAME);
CREATE INDEX IDX_SET1_SM_USER ON CASE_DATA_SET1(SM_USER);
CREATE INDEX IDX_SET1_CREATED_AT ON CASE_DATA_SET1(CREATED_AT);

-- =====================================================================
-- TABLE: CASE_DATA_SET2
-- Stores case data received from the Set 2 API endpoints.
-- =====================================================================
CREATE TABLE CASE_DATA_SET2 (
    ID                NUMBER(19)     PRIMARY KEY,
    CASE_NUMBER       VARCHAR2(50)   NOT NULL,
    VERSION_NUMBER    VARCHAR2(20)   NOT NULL,
    AGENT_NAME        VARCHAR2(100)  NOT NULL,
    PARTY_IDENTIFIER  VARCHAR2(100)  NOT NULL,
    SM_USER           VARCHAR2(100)  NOT NULL,
    CREATED_AT        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UPDATED_AT        TIMESTAMP      DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for common query patterns on CASE_DATA_SET2
CREATE INDEX IDX_SET2_CASE_NUMBER ON CASE_DATA_SET2(CASE_NUMBER);
CREATE INDEX IDX_SET2_AGENT_NAME ON CASE_DATA_SET2(AGENT_NAME);
CREATE INDEX IDX_SET2_SM_USER ON CASE_DATA_SET2(SM_USER);
CREATE INDEX IDX_SET2_CREATED_AT ON CASE_DATA_SET2(CREATED_AT);

-- =====================================================================
-- TABLE: API_REQUEST_LOG
-- Logs every API request for reporting and analytics.
-- This table is critical for measuring API usage metrics.
-- =====================================================================
CREATE TABLE API_REQUEST_LOG (
    ID                 NUMBER(19)      PRIMARY KEY,
    ENDPOINT           VARCHAR2(500)   NOT NULL,
    HTTP_METHOD        VARCHAR2(10)    NOT NULL,
    SM_USER            VARCHAR2(100),
    CLIENT_IP          VARCHAR2(50),
    REQUEST_BODY       VARCHAR2(4000),
    RESPONSE_STATUS    NUMBER(5),
    RESPONSE_TIME_MS   NUMBER(19),
    ERROR_MESSAGE      VARCHAR2(2000),
    REQUEST_TIMESTAMP  TIMESTAMP       NOT NULL,
    USER_AGENT         VARCHAR2(500),
    CORRELATION_ID     VARCHAR2(100)
);

-- Indexes for reporting queries on API_REQUEST_LOG
CREATE INDEX IDX_REQ_LOG_TIMESTAMP ON API_REQUEST_LOG(REQUEST_TIMESTAMP);
CREATE INDEX IDX_REQ_LOG_ENDPOINT ON API_REQUEST_LOG(ENDPOINT);
CREATE INDEX IDX_REQ_LOG_SM_USER ON API_REQUEST_LOG(SM_USER);
CREATE INDEX IDX_REQ_LOG_HTTP_METHOD ON API_REQUEST_LOG(HTTP_METHOD);
CREATE INDEX IDX_REQ_LOG_RESPONSE_STATUS ON API_REQUEST_LOG(RESPONSE_STATUS);

-- Composite index for common reporting query: daily counts by endpoint
CREATE INDEX IDX_REQ_LOG_TS_ENDPOINT ON API_REQUEST_LOG(REQUEST_TIMESTAMP, ENDPOINT);

COMMENT ON TABLE CASE_DATA_SET1 IS 'Stores case data from Set 1 API endpoints';
COMMENT ON TABLE CASE_DATA_SET2 IS 'Stores case data from Set 2 API endpoints';
COMMENT ON TABLE API_REQUEST_LOG IS 'Logs all API requests for usage reporting and analytics';
