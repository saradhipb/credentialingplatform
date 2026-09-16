/* ===========================================================================
   04 — Converts the ten Instant columns from DATETIME2 to DATETIMEOFFSET.

   WHY
     Hibernate maps java.time.Instant to TIMESTAMP_UTC, which the SQL Server
     dialect renders as datetimeoffset. Script 01 originally declared these
     columns DATETIME2(6), which holds the same instant but is a different type,
     so ddl-auto=update attempted to convert every one of them at start-up. Nine
     conversions succeeded silently; audit_logs.[timestamp] failed outright,
     because SQL Server will not alter a column an index is built on.

   WHEN TO RUN IT
     Only on a database created by the earlier version of script 01. A database
     created by the corrected script already has the right types. Running this
     against a corrected database is harmless - altering a column to the type it
     already has succeeds and changes nothing.

   ABOUT THE DATA
     Existing values convert implicitly, taking a +00:00 offset. That is correct:
     every value was written from an Instant, which is UTC by definition.

   Run as a login with ALTER rights on dbo, with the application STOPPED.
   =========================================================================== */

USE credentialing;
GO

PRINT 'Dropping the index that blocks the conversion...';
IF EXISTS (SELECT 1 FROM sys.indexes
           WHERE name = 'idx_audit_timestamp' AND object_id = OBJECT_ID('dbo.audit_logs'))
    DROP INDEX idx_audit_timestamp ON dbo.audit_logs;
GO

PRINT 'Converting the timestamp columns...';
ALTER TABLE dbo.providers            ALTER COLUMN created_at    DATETIMEOFFSET(6) NOT NULL;
ALTER TABLE dbo.providers            ALTER COLUMN updated_at    DATETIMEOFFSET(6) NULL;
ALTER TABLE dbo.credential_documents ALTER COLUMN uploaded_at   DATETIMEOFFSET(6) NOT NULL;
ALTER TABLE dbo.credential_documents ALTER COLUMN purged_at     DATETIMEOFFSET(6) NULL;
ALTER TABLE dbo.verification_records ALTER COLUMN verified_at   DATETIMEOFFSET(6) NOT NULL;
ALTER TABLE dbo.risk_assessments     ALTER COLUMN reviewed_at   DATETIMEOFFSET(6) NULL;
ALTER TABLE dbo.risk_assessments     ALTER COLUMN assessed_at   DATETIMEOFFSET(6) NOT NULL;
ALTER TABLE dbo.audit_logs           ALTER COLUMN [timestamp]   DATETIMEOFFSET(6) NOT NULL;
ALTER TABLE dbo.app_users            ALTER COLUMN last_login_at DATETIMEOFFSET(6) NULL;
ALTER TABLE dbo.app_users            ALTER COLUMN created_at    DATETIMEOFFSET(6) NOT NULL;
GO

PRINT 'Recreating the index...';
CREATE NONCLUSTERED INDEX idx_audit_timestamp ON dbo.audit_logs ([timestamp]);
GO

/* Every row below should read datetimeoffset. Anything still datetime2 did not
   convert, and the application will try to alter it again on the next start. */
SELECT t.name AS table_name, c.name AS column_name, ty.name AS data_type, c.scale
FROM sys.columns c
JOIN sys.tables  t  ON t.object_id = c.object_id
JOIN sys.types   ty ON ty.user_type_id = c.user_type_id
WHERE ty.name IN ('datetime2', 'datetimeoffset')
ORDER BY t.name, c.name;
GO
