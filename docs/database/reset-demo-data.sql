-- ---------------------------------------------------------------------------
-- reset-demo-data.sql
--
-- Empties every table of the credentialing platform EXCEPT app_users, so the
-- accounts and their password hashes survive and everything the pipeline
-- produced is removed.
--
-- Run it against the H2 file database with the service STOPPED. H2 allows one
-- writer at a time, so with the service running the connection is refused.
--
-- The deletes are in child-to-parent order. Reversing the order fails on the
-- foreign keys, which is a safety net rather than an obstacle: if a statement
-- errors, nothing has been half-removed.
-- ---------------------------------------------------------------------------

DELETE FROM risk_assessments;      -- references verification_records and providers
DELETE FROM verification_records;  -- references credential_documents and providers
DELETE FROM credential_documents;  -- references providers
DELETE FROM providers;
DELETE FROM audit_logs;            -- no foreign keys; remove this line to keep the trail

-- Identity columns keep counting from where they left off unless they are
-- restarted, so the first provider after a reset would be id 43 rather than 1.
-- Restarting them makes a demonstration read cleanly.
ALTER TABLE risk_assessments     ALTER COLUMN id RESTART WITH 1;
ALTER TABLE verification_records ALTER COLUMN id RESTART WITH 1;
ALTER TABLE credential_documents ALTER COLUMN id RESTART WITH 1;
ALTER TABLE providers            ALTER COLUMN id RESTART WITH 1;
ALTER TABLE audit_logs           ALTER COLUMN id RESTART WITH 1;

-- What is left, to confirm before closing the console.
SELECT 'app_users'            AS table_name, COUNT(*) AS rows FROM app_users
UNION ALL SELECT 'providers',            COUNT(*) FROM providers
UNION ALL SELECT 'credential_documents', COUNT(*) FROM credential_documents
UNION ALL SELECT 'verification_records', COUNT(*) FROM verification_records
UNION ALL SELECT 'risk_assessments',     COUNT(*) FROM risk_assessments
UNION ALL SELECT 'audit_logs',           COUNT(*) FROM audit_logs;
