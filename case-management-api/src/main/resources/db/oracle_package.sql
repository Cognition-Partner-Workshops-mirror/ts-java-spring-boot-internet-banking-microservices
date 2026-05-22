-- =====================================================================
-- Oracle PL/SQL Package: CASE_DATA_PKG
-- =====================================================================
-- This package encapsulates the data insertion logic for case data.
-- The API calls these procedures to insert data into the respective
-- tables, ensuring consistent business rules and data integrity.
--
-- Procedures:
--   INSERT_CASE_DATA_SET1 - Inserts a record into CASE_DATA_SET1
--   INSERT_CASE_DATA_SET2 - Inserts a record into CASE_DATA_SET2
-- =====================================================================

-- =====================================================================
-- Package Specification
-- =====================================================================
CREATE OR REPLACE PACKAGE CASE_DATA_PKG AS

    -- Procedure to insert case data into Set 1 table
    -- Returns the generated record ID via OUT parameter
    PROCEDURE INSERT_CASE_DATA_SET1(
        p_case_number       IN  VARCHAR2,
        p_version_number    IN  VARCHAR2,
        p_agent_name        IN  VARCHAR2,
        p_party_identifier  IN  VARCHAR2,
        p_sm_user           IN  VARCHAR2,
        p_record_id         OUT NUMBER
    );

    -- Procedure to insert case data into Set 2 table
    -- Returns the generated record ID via OUT parameter
    PROCEDURE INSERT_CASE_DATA_SET2(
        p_case_number       IN  VARCHAR2,
        p_version_number    IN  VARCHAR2,
        p_agent_name        IN  VARCHAR2,
        p_party_identifier  IN  VARCHAR2,
        p_sm_user           IN  VARCHAR2,
        p_record_id         OUT NUMBER
    );

END CASE_DATA_PKG;
/

-- =====================================================================
-- Package Body - Implementation
-- =====================================================================
CREATE OR REPLACE PACKAGE BODY CASE_DATA_PKG AS

    -- ----------------------------------------------------------------
    -- INSERT_CASE_DATA_SET1
    -- Inserts a new record into CASE_DATA_SET1 table.
    -- Generates the primary key using CASE_SET1_SEQ sequence.
    -- Sets timestamps automatically.
    -- Note: No COMMIT/ROLLBACK here — transaction management is
    -- handled by Spring's @Transactional in the calling Java service.
    -- ----------------------------------------------------------------
    PROCEDURE INSERT_CASE_DATA_SET1(
        p_case_number       IN  VARCHAR2,
        p_version_number    IN  VARCHAR2,
        p_agent_name        IN  VARCHAR2,
        p_party_identifier  IN  VARCHAR2,
        p_sm_user           IN  VARCHAR2,
        p_record_id         OUT NUMBER
    ) IS
    BEGIN
        -- Generate the next sequence value for the record ID
        SELECT CASE_SET1_SEQ.NEXTVAL INTO p_record_id FROM DUAL;

        -- Insert the case data record
        INSERT INTO CASE_DATA_SET1 (
            ID,
            CASE_NUMBER,
            VERSION_NUMBER,
            AGENT_NAME,
            PARTY_IDENTIFIER,
            SM_USER,
            CREATED_AT,
            UPDATED_AT
        ) VALUES (
            p_record_id,
            p_case_number,
            p_version_number,
            p_agent_name,
            p_party_identifier,
            p_sm_user,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        );

    END INSERT_CASE_DATA_SET1;

    -- ----------------------------------------------------------------
    -- INSERT_CASE_DATA_SET2
    -- Inserts a new record into CASE_DATA_SET2 table.
    -- Generates the primary key using CASE_SET2_SEQ sequence.
    -- Sets timestamps automatically.
    -- Note: No COMMIT/ROLLBACK here — transaction management is
    -- handled by Spring's @Transactional in the calling Java service.
    -- ----------------------------------------------------------------
    PROCEDURE INSERT_CASE_DATA_SET2(
        p_case_number       IN  VARCHAR2,
        p_version_number    IN  VARCHAR2,
        p_agent_name        IN  VARCHAR2,
        p_party_identifier  IN  VARCHAR2,
        p_sm_user           IN  VARCHAR2,
        p_record_id         OUT NUMBER
    ) IS
    BEGIN
        -- Generate the next sequence value for the record ID
        SELECT CASE_SET2_SEQ.NEXTVAL INTO p_record_id FROM DUAL;

        -- Insert the case data record
        INSERT INTO CASE_DATA_SET2 (
            ID,
            CASE_NUMBER,
            VERSION_NUMBER,
            AGENT_NAME,
            PARTY_IDENTIFIER,
            SM_USER,
            CREATED_AT,
            UPDATED_AT
        ) VALUES (
            p_record_id,
            p_case_number,
            p_version_number,
            p_agent_name,
            p_party_identifier,
            p_sm_user,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        );

    END INSERT_CASE_DATA_SET2;

END CASE_DATA_PKG;
/
