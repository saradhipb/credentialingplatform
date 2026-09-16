/* ===========================================================================
   AI-Enhanced Provider Credentialing Automation Platform
   Teardown — Microsoft SQL Server

   Script : 03 of 03 — removes everything script 01 created
   Run as : a login with sysadmin rights

   WARNING
     This destroys every provider record, every piece of verification evidence,
     every risk assessment and the entire audit trail. There is no undo. Use it
     to reset a development machine between demonstrations, never on anything
     holding real records.

   Tables are dropped child-first so the foreign keys do not block the drop.
   =========================================================================== */

SET NOCOUNT ON;
GO

USE credentialing;
GO

PRINT 'Dropping tables...';

DROP TABLE IF EXISTS dbo.risk_assessments;
DROP TABLE IF EXISTS dbo.verification_records;
DROP TABLE IF EXISTS dbo.credential_documents;
DROP TABLE IF EXISTS dbo.providers;
DROP TABLE IF EXISTS dbo.audit_logs;
DROP TABLE IF EXISTS dbo.app_users;
GO

/* Uncomment the block below to remove the database and the login as well.
   Everything above only empties the schema, which is usually what you want
   when resetting between runs.

USE master;
GO
ALTER DATABASE credentialing SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
DROP DATABASE IF EXISTS credentialing;
DROP LOGIN cred_app;
GO
*/

PRINT 'Teardown complete. Re-run 01-schema-sqlserver.sql to recreate the schema.';
GO
