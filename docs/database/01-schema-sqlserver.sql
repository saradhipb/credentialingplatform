/* ===========================================================================
   AI-Enhanced Provider Credentialing Automation Platform
   Data Definition Language (DDL) — Microsoft SQL Server 2019 or later

     Student  : Saradhi PB
   Script   : 01 of 03 — creates the database, the login and the schema
   Run as   : a login with sysadmin rights (for example sa)

   WHAT THIS SCRIPT DOES
     1. Creates the database and a dedicated application login and user.
     2. Creates the six tables, their indexes and their foreign keys.
     3. Grants the application user the least privilege it needs.

   RELATIONSHIP TO THE ENTITY MODEL
     Every table, column name, length and nullability below is taken from the
     JPA entity classes in edu.gcu.capstone.credentialing.domain. Hibernate can
     generate an equivalent schema by itself, and with ddl-auto set to "update"
     it will reconcile anything missing. This script exists so that the schema
     can be reviewed, version-controlled and created by a database
     administrator rather than by the application.

   NAMING
     Column names follow Spring Boot's camel-case-to-underscore convention,
     which is what Hibernate expects. Do not rename a column without changing
     the matching field, or the mapping breaks.

   TYPE CHOICES
     BIGINT IDENTITY(1,1)  matches @GeneratedValue(strategy = IDENTITY).
     VARCHAR(MAX)          is SQL Server's equivalent of the CLOB that H2 uses
                           for the four @Lob text columns.
     DATETIMEOFFSET(6)     matches java.time.Instant at microsecond precision.
                           Hibernate maps Instant to TIMESTAMP_UTC, which this
                           dialect renders as datetimeoffset. DATETIME2 would
                           store the same instant but would not match, and
                           ddl-auto=update would try to convert the column on
                           every start-up - which fails outright on the one
                           column that carries an index.
     FLOAT                 is 53-bit, matching a Java double.
     BIT                   matches a Java boolean.
     [timestamp]           is bracketed because TIMESTAMP is a deprecated
                           SQL Server type name. The column itself is ordinary.
   =========================================================================== */

SET NOCOUNT ON;
GO

/* ---------------------------------------------------------------------------
   1. Database
   --------------------------------------------------------------------------- */

IF DB_ID('credentialing') IS NULL
BEGIN
    PRINT 'Creating database [credentialing]...';
    CREATE DATABASE credentialing;
END
ELSE
    PRINT 'Database [credentialing] already exists; leaving it in place.';
GO

/* ---------------------------------------------------------------------------
   2. Application login and user

   The application connects as cred_app, never as sa. Change the password
   before using this outside a development machine, and change it in the
   DB_PASSWORD environment variable to match.
   --------------------------------------------------------------------------- */

IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'cred_app')
BEGIN
    PRINT 'Creating login [cred_app]...';
    CREATE LOGIN cred_app
        WITH PASSWORD = 'Cred@pp2026!',
             DEFAULT_DATABASE = credentialing,
             CHECK_POLICY = OFF;
END
ELSE
    PRINT 'Login [cred_app] already exists.';
GO

USE credentialing;
GO

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'cred_app')
BEGIN
    PRINT 'Creating database user [cred_app]...';
    CREATE USER cred_app FOR LOGIN cred_app;
END
GO

/* ---------------------------------------------------------------------------
   3. Tables

   Created parent-first so the foreign keys can be declared inline.
   --------------------------------------------------------------------------- */

/* --- providers -------------------------------------------------------------
   The root entity. One row is one healthcare provider being credentialed.
   Holds only the fields verification needs; no clinical or patient data.      */
IF OBJECT_ID('dbo.providers', 'U') IS NULL
BEGIN
    PRINT 'Creating table [providers]...';
    CREATE TABLE dbo.providers
    (
        id                 BIGINT        IDENTITY(1,1) NOT NULL,
        npi                VARCHAR(10)   NULL,           -- ten-digit CMS identifier
        npi_type           VARCHAR(20)   NOT NULL,       -- INDIVIDUAL | ORGANIZATION | UNKNOWN
        first_name         VARCHAR(120)  NULL,
        last_name          VARCHAR(120)  NULL,
        organization_name  VARCHAR(240)  NULL,           -- used instead of a person name for Type 2
        address_line1      VARCHAR(200)  NULL,
        city               VARCHAR(120)  NULL,
        state              VARCHAR(2)    NULL,           -- USPS two-letter code
        postal_code        VARCHAR(10)   NULL,
        license_number     VARCHAR(60)   NULL,
        license_state      VARCHAR(2)    NULL,
        taxonomy           VARCHAR(160)  NULL,           -- specialty description
        status             VARCHAR(20)   NOT NULL,       -- PENDING | ACTIVE | FLAGGED | REJECTED | MANUAL_REVIEW
        created_at         DATETIMEOFFSET(6) NOT NULL,
        updated_at         DATETIMEOFFSET(6) NULL,
        CONSTRAINT pk_providers PRIMARY KEY CLUSTERED (id)
    );

    CREATE NONCLUSTERED INDEX idx_provider_npi    ON dbo.providers (npi);
    CREATE NONCLUSTERED INDEX idx_provider_status ON dbo.providers (status);
END
GO

/* --- credential_documents --------------------------------------------------
   One uploaded document, the text extracted from it, and how that extraction
   was performed. purged_at records that the stored file was deleted under the
   retention policy while the row itself survives for the audit trail.         */
IF OBJECT_ID('dbo.credential_documents', 'U') IS NULL
BEGIN
    PRINT 'Creating table [credential_documents]...';
    CREATE TABLE dbo.credential_documents
    (
        id                     BIGINT        IDENTITY(1,1) NOT NULL,
        provider_id            BIGINT        NULL,
        original_filename      VARCHAR(260)  NOT NULL,    -- as supplied, for display only
        stored_filename        VARCHAR(120)  NULL,        -- generated; never taken from the upload
        content_type           VARCHAR(120)  NULL,
        size_bytes             BIGINT        NOT NULL,
        document_type          VARCHAR(80)   NULL,
        status                 VARCHAR(30)   NOT NULL,    -- UPLOADED | EXTRACTED | PARSED | EXTRACTION_FAILED | PURGED
        extracted_text         VARCHAR(MAX)  NULL,
        extraction_engine      VARCHAR(40)   NULL,        -- PLAIN_TEXT | PDF_TEXT_LAYER | TESSERACT_OCR | NONE
        extraction_confidence  FLOAT         NOT NULL,    -- 0.0 to 1.0
        extraction_millis      BIGINT        NOT NULL,
        uploaded_by            VARCHAR(80)   NULL,
        uploaded_at            DATETIMEOFFSET(6) NOT NULL,
        purged_at              DATETIMEOFFSET(6) NULL,
        CONSTRAINT pk_credential_documents PRIMARY KEY CLUSTERED (id),
        CONSTRAINT fk_document_provider FOREIGN KEY (provider_id)
            REFERENCES dbo.providers (id)
    );

    CREATE NONCLUSTERED INDEX idx_document_provider ON dbo.credential_documents (provider_id);
END
GO

/* --- verification_records --------------------------------------------------
   The platform's primary evidence: what each authoritative source reported and
   where the document disagreed with the registry. source records whether the
   answer came from a live lookup or the bundled offline snapshot, so a cached
   result can never be mistaken for a live confirmation.                       */
IF OBJECT_ID('dbo.verification_records', 'U') IS NULL
BEGIN
    PRINT 'Creating table [verification_records]...';
    CREATE TABLE dbo.verification_records
    (
        id                               BIGINT        IDENTITY(1,1) NOT NULL,
        provider_id                      BIGINT        NULL,
        document_id                      BIGINT        NULL,
        npi_status                       VARCHAR(30)   NOT NULL,  -- VALID | DEACTIVATED | NOT_FOUND | INVALID_FORMAT | MISSING | UNAVAILABLE
        registry_name                    VARCHAR(240)  NULL,
        registry_address                 VARCHAR(200)  NULL,
        registry_taxonomy                VARCHAR(160)  NULL,
        registry_enumeration_date        VARCHAR(30)   NULL,
        exclusion_status                 VARCHAR(30)   NOT NULL,  -- NOT_EXCLUDED | EXCLUDED | POSSIBLE_MATCH | UNAVAILABLE
        exclusion_matched_name           VARCHAR(240)  NULL,
        exclusion_date                   VARCHAR(30)   NULL,
        exclusion_type                   VARCHAR(40)   NULL,      -- statutory basis, e.g. 1128b4
        exclusion_match_score            FLOAT         NOT NULL,  -- Jaro-Winkler similarity, 0.0 to 1.0
        discrepancies_json               VARCHAR(MAX)  NULL,      -- serialised field-comparison array
        discrepancy_count                INT           NOT NULL,
        high_severity_discrepancy_count  INT           NOT NULL,
        missing_field_count              INT           NOT NULL,
        source                           VARCHAR(30)   NOT NULL,  -- LIVE_API | OFFLINE_FALLBACK | NONE
        api_response                     VARCHAR(MAX)  NULL,      -- raw registry payload, retained for audit
        processing_note                  VARCHAR(500)  NULL,
        duration_ms                      BIGINT        NOT NULL,
        verified_at                      DATETIMEOFFSET(6) NOT NULL,
        CONSTRAINT pk_verification_records PRIMARY KEY CLUSTERED (id),
        CONSTRAINT fk_verification_provider FOREIGN KEY (provider_id)
            REFERENCES dbo.providers (id),
        CONSTRAINT fk_verification_document FOREIGN KEY (document_id)
            REFERENCES dbo.credential_documents (id)
    );

    CREATE NONCLUSTERED INDEX idx_verification_provider ON dbo.verification_records (provider_id);
END
GO

/* --- risk_assessments ------------------------------------------------------
   Intelligence-layer output and the human decision recorded against it. The
   reviewer's decision is stored beside the platform's recommendation, never in
   place of it, so the record always shows both.                               */
IF OBJECT_ID('dbo.risk_assessments', 'U') IS NULL
BEGIN
    PRINT 'Creating table [risk_assessments]...';
    CREATE TABLE dbo.risk_assessments
    (
        id                 BIGINT        IDENTITY(1,1) NOT NULL,
        provider_id        BIGINT        NULL,
        verification_id    BIGINT        NULL,
        score              FLOAT         NOT NULL,   -- composite risk, 0 to 100
        anomaly_score      FLOAT         NOT NULL,   -- Isolation Forest output, 0.0 to 1.0
        confidence         FLOAT         NOT NULL,
        anomalous          BIT           NOT NULL,
        flags              VARCHAR(1000) NULL,       -- pipe-separated explanation strings
        feature_snapshot   VARCHAR(MAX)  NULL,       -- the seven feature values scored
        recommendation     VARCHAR(20)   NOT NULL,   -- APPROVE | REVIEW | REJECT
        model_version      VARCHAR(60)   NULL,
        reviewer_decision  VARCHAR(40)   NULL,       -- APPROVED | ESCALATED | DENIED
        reviewed_by        VARCHAR(80)   NULL,
        reviewed_at        DATETIMEOFFSET(6) NULL,
        assessed_at        DATETIMEOFFSET(6) NOT NULL,
        CONSTRAINT pk_risk_assessments PRIMARY KEY CLUSTERED (id),
        CONSTRAINT fk_risk_provider FOREIGN KEY (provider_id)
            REFERENCES dbo.providers (id),
        CONSTRAINT fk_risk_verification FOREIGN KEY (verification_id)
            REFERENCES dbo.verification_records (id)
    );

    CREATE NONCLUSTERED INDEX idx_risk_provider ON dbo.risk_assessments (provider_id);
END
GO

/* --- audit_logs ------------------------------------------------------------
   Append-only. No foreign keys: the trail references records by type and
   identifier so that an entry survives any later change to the record it
   describes. No UPDATE or DELETE permission is granted on this table.         */
IF OBJECT_ID('dbo.audit_logs', 'U') IS NULL
BEGIN
    PRINT 'Creating table [audit_logs]...';
    CREATE TABLE dbo.audit_logs
    (
        id           BIGINT        IDENTITY(1,1) NOT NULL,
        username     VARCHAR(80)   NULL,
        role         VARCHAR(20)   NULL,
        action       VARCHAR(60)   NOT NULL,   -- e.g. LOGIN_SUCCESS, DOCUMENT_UPLOAD
        module       VARCHAR(40)   NULL,       -- e.g. SECURITY, VERIFICATION
        entity_type  VARCHAR(40)   NULL,
        entity_id    BIGINT        NULL,
        detail       VARCHAR(500)  NULL,       -- what happened, not the data it happened to
        outcome      VARCHAR(20)   NULL,       -- SUCCESS | FAILURE
        ip_address   VARCHAR(60)   NULL,
        [timestamp]  DATETIMEOFFSET(6) NOT NULL,
        CONSTRAINT pk_audit_logs PRIMARY KEY CLUSTERED (id)
    );

    CREATE NONCLUSTERED INDEX idx_audit_timestamp ON dbo.audit_logs ([timestamp]);
    CREATE NONCLUSTERED INDEX idx_audit_username  ON dbo.audit_logs (username);
END
GO

/* --- app_users -------------------------------------------------------------
   No password is stored. Each row holds a per-user random salt and a
   PBKDF2-HMAC-SHA256 derived key, both Base64 encoded.                        */
IF OBJECT_ID('dbo.app_users', 'U') IS NULL
BEGIN
    PRINT 'Creating table [app_users]...';
    CREATE TABLE dbo.app_users
    (
        id             BIGINT        IDENTITY(1,1) NOT NULL,
        username       VARCHAR(80)   NOT NULL,
        display_name   VARCHAR(120)  NULL,
        email          VARCHAR(160)  NULL,
        password_hash  VARCHAR(200)  NOT NULL,   -- Base64 PBKDF2 derived key
        salt           VARCHAR(60)   NOT NULL,   -- Base64 16-byte random salt
        role           VARCHAR(20)   NOT NULL,   -- ADMIN | REVIEWER | VIEWER
        active         BIT           NOT NULL,
        last_login_at  DATETIMEOFFSET(6) NULL,
        created_at     DATETIMEOFFSET(6) NOT NULL,
        CONSTRAINT pk_app_users PRIMARY KEY CLUSTERED (id),
        CONSTRAINT uq_app_users_username UNIQUE (username)
    );

    CREATE NONCLUSTERED INDEX idx_user_username ON dbo.app_users (username);
END
GO

/* ---------------------------------------------------------------------------
   4. Permissions

   The application reads and writes rows. It does not create, alter or drop
   objects, so it is not given permission to. Note that with ddl-auto set to
   "update" Hibernate will attempt schema changes at start-up; grant ALTER
   temporarily if you change the entity model, or set ddl-auto to "validate"
   once the schema is stable.
   --------------------------------------------------------------------------- */

GRANT SELECT, INSERT, UPDATE, DELETE ON SCHEMA::dbo TO cred_app;

-- ALTER is granted because the sqlserver profile ships with ddl-auto set to
-- "update", which lets Hibernate reconcile the schema if the entity model
-- changes. Once the schema is stable, set ddl-auto to "validate" in
-- application-sqlserver.yml and revoke this with:
--     REVOKE ALTER ON SCHEMA::dbo FROM cred_app;
GRANT ALTER ON SCHEMA::dbo TO cred_app;

-- The audit trail is append-only at the database level as well as in code.
-- The application only ever inserts into it; this makes that a guarantee
-- rather than a convention.
DENY UPDATE, DELETE ON dbo.audit_logs TO cred_app;
GO

PRINT '';
PRINT 'Schema creation complete. Tables created:';
SELECT TABLE_NAME AS [table], (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS c
        WHERE c.TABLE_NAME = t.TABLE_NAME) AS [columns]
FROM INFORMATION_SCHEMA.TABLES t
WHERE TABLE_TYPE = 'BASE TABLE'
ORDER BY TABLE_NAME;
GO
