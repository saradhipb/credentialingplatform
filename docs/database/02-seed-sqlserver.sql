/* ===========================================================================
   AI-Enhanced Provider Credentialing Automation Platform
   Data Manipulation Language (DML) — Microsoft SQL Server

   Script  : 02 of 03 — seeds the accounts needed to sign in
   Run as  : sa, or any login with INSERT rights on the credentialing database
   Requires: 01-schema-sqlserver.sql to have been run first

   WHAT THIS SCRIPT DOES
     Inserts one account per role so that the platform can be signed in to and
     the access matrix demonstrated. It is idempotent: running it twice inserts
     nothing the second time.

   HOW THE PASSWORD VALUES WERE PRODUCED
     No password is stored. Each row holds a 16-byte random salt and the key
     derived from the password with PBKDF2-HMAC-SHA256 over 210,000 iterations
     producing 256 bits, both Base64 encoded. That is exactly what
     PasswordHasher.java computes, so these rows are indistinguishable from
     accounts the application creates itself. The iteration count is the figure
     OWASP publishes for PBKDF2-HMAC-SHA256.

     The salts below are genuinely random and unique per account, which is why
     the three hashes differ even though the passwords share a pattern.

   SECURITY NOTICE
     These credentials are published in the User Guide, so anyone who reads it
     can sign in with them. They exist to demonstrate the three roles. Before
     this platform processes any real provider information:
         UPDATE dbo.app_users SET active = 0 WHERE username IN ('admin','reviewer','viewer');
     and create individual accounts instead.

   ALTERNATIVE
     If you skip this script, DemoDataSeeder.java creates the same three
     accounts automatically the first time the platform starts against an empty
     app_users table. Use one route or the other, not both — though the seeder
     checks whether any account exists, so running it after this script is
     harmless.
   =========================================================================== */

SET NOCOUNT ON;
GO

USE credentialing;
GO

/* ---------------------------------------------------------------------------
   Demonstration accounts

   username   password       role      capabilities
   ---------  -------------  --------  ------------------------------------
   admin      Admin#2026     ADMIN     Everything, including administration
   reviewer   Review#2026    REVIEWER  Upload, verify, record decisions
   viewer     Viewer#2026    VIEWER    Read-only access
   --------------------------------------------------------------------------- */

IF NOT EXISTS (SELECT 1 FROM dbo.app_users)
BEGIN
    PRINT 'Seeding demonstration accounts...';

    INSERT INTO dbo.app_users
        (username, display_name, email, password_hash, salt, role, active, created_at)
    VALUES
    ('admin', 'System Administrator', 'admin@example.edu',
     'vjisTg/S/j8DtRXPUF8NoFl/O1bAzwP/JMnhpzS0omw=',
     'J8QWphT8UgNlsUVzj0FhJg==', 'ADMIN', 1, SYSUTCDATETIME()),
    ('reviewer', 'Credentialing Specialist', 'reviewer@example.edu',
     'bYP4z68FSrE/7rxZnD+1qlhaE636Ww/lW4ivDcRMQAA=',
     '8hHMZCeukZq3kFeoWzfEPg==', 'REVIEWER', 1, SYSUTCDATETIME()),
    ('viewer', 'Compliance Officer', 'viewer@example.edu',
     '6m0FmMwQpFPjrYQjjPXa9aKADNcqY+dq4beCrJYQIP0=',
     'qKakOMJhmXlsuTT3OLeidg==', 'VIEWER', 1, SYSUTCDATETIME());

    PRINT 'Three accounts created.';
END
ELSE
    PRINT 'app_users is not empty; no accounts were inserted.';
GO

/* ---------------------------------------------------------------------------
   Verification

   Provider, document, verification and risk rows are NOT seeded here. Those
   are produced by running documents through the platform, and inserting them
   by hand would create records that no evidence supports — which is the one
   thing a credentialing audit trail must never contain.

   To populate the other five tables, start the platform and upload the files
   in sample-data/documents. Eleven of the thirteen are readable and produce
   the outcomes listed in Section 8.3 of the Milestone 3 document.
   --------------------------------------------------------------------------- */

SELECT username, display_name, role, active, created_at
FROM dbo.app_users
ORDER BY id;
GO

SELECT 'providers' AS [table], COUNT(*) AS [rows] FROM dbo.providers
UNION ALL SELECT 'credential_documents', COUNT(*) FROM dbo.credential_documents
UNION ALL SELECT 'verification_records', COUNT(*) FROM dbo.verification_records
UNION ALL SELECT 'risk_assessments',     COUNT(*) FROM dbo.risk_assessments
UNION ALL SELECT 'audit_logs',           COUNT(*) FROM dbo.audit_logs
UNION ALL SELECT 'app_users',            COUNT(*) FROM dbo.app_users;
GO
