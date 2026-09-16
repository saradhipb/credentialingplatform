# Running the platform on Microsoft SQL Server

The platform ships with an embedded H2 database so it runs with nothing to
install. This folder switches it to SQL Server instead. Nothing in the Java
code changes — only configuration — which is the point: the same tested
artifact is repointed at a different database.

| Script | Purpose |
|---|---|
| `01-schema-sqlserver.sql` | Creates the database, the application login, the six tables, their indexes and foreign keys, and grants least-privilege permissions. |
| `02-seed-sqlserver.sql` | Inserts the three demonstration accounts. |
| `03-drop-sqlserver.sql` | Removes everything script 01 created. Destructive. |

---

## Step 1 — Confirm SQL Server accepts TCP connections

The JDBC driver connects over TCP, which is **disabled by default on SQL Server
Express**. If it is off, the driver reports
*"The TCP/IP connection to the host localhost, port 1433 has failed"*.

1. Open **SQL Server Configuration Manager**.
2. Expand **SQL Server Network Configuration** → **Protocols for MSSQLSERVER**
   (or **Protocols for SQLEXPRESS**).
3. If **TCP/IP** shows *Disabled*, right-click it and choose **Enable**.
4. Double-click **TCP/IP** → **IP Addresses** tab → scroll to **IPAll**. Set
   **TCP Port** to `1433` and clear **TCP Dynamic Ports**.
5. Under **SQL Server Services**, right-click your SQL Server instance and
   choose **Restart**.

## Step 2 — Confirm SQL Server authentication is enabled

The platform signs in with a SQL login, not a Windows account.

1. Open **SQL Server Management Studio** (SSMS) and connect to your instance.
2. Right-click the server name → **Properties** → **Security**.
3. Select **SQL Server and Windows Authentication mode**.
4. Select **OK**, then restart the SQL Server service.

## Step 3 — Create the database and schema

In SSMS, open `01-schema-sqlserver.sql` and execute it (F5). Alternatively,
from a command prompt:

```
sqlcmd -S localhost -E -i docs\database\01-schema-sqlserver.sql
```

`-E` uses your Windows account. If you use a SQL login instead, replace it with
`-U sa -P yourpassword`.

You should see the six tables listed at the end:

```
table                    columns
-----------------------  -------
app_users                     10
audit_logs                    11
credential_documents          14
providers                     16
risk_assessments              14
verification_records          22
```

## Step 4 — Seed the accounts

```
sqlcmd -S localhost -E -d credentialing -i docs\database\02-seed-sqlserver.sql
```

Or open the file in SSMS and execute it. It prints the three accounts it
created.

## Step 5 — Rebuild and start the platform

The SQL Server JDBC driver was added to `pom.xml`, so the project must be
rebuilt once:

```
build.bat
java -jar backend\target\credentialing-platform.jar --spring.profiles.active=sqlserver
```

Watch for these lines, which confirm it connected:

```
HikariPool-1 - Starting...
HikariPool-1 - Start completed.
Tomcat started on port(s): 8080 (http)
```

Then open <http://localhost:8080> and sign in as `reviewer` / `Review#2026`.

---

## Connection settings

Everything lives in `backend/src/main/resources/application-sqlserver.yml` and
every value can be overridden without rebuilding.

| Setting | Default | Override with |
|---|---|---|
| JDBC URL | `jdbc:sqlserver://localhost:1433;databaseName=credentialing;encrypt=true;trustServerCertificate=true` | `DB_URL` or `--spring.datasource.url=` |
| Username | `cred_app` | `DB_USERNAME` |
| Password | `Cred@pp2026!` | `DB_PASSWORD` |

**Named instance.** If your server is `localhost\SQLEXPRESS`, do not put a
backslash in YAML — use the `instanceName` parameter:

```
java -jar backend\target\credentialing-platform.jar ^
     --spring.profiles.active=sqlserver ^
     --spring.datasource.url="jdbc:sqlserver://localhost;instanceName=SQLEXPRESS;databaseName=credentialing;encrypt=true;trustServerCertificate=true"
```

**Why `trustServerCertificate=true`.** Since version 10 the Microsoft driver
encrypts by default and validates the server certificate. A local SQL Server
presents a self-signed certificate, which fails validation. This setting keeps
the connection encrypted but skips validation. In a hosted deployment, install
a certificate the client trusts and remove the parameter.

---

## Going back to H2

Start the platform with no profile:

```
java -jar backend\target\credentialing-platform.jar
```

The two databases are independent. Records created under one are not visible
in the other.

---

## Troubleshooting

| Message | Cause | Fix |
|---|---|---|
| `The TCP/IP connection to the host localhost, port 1433 has failed` | TCP/IP is disabled, or the instance listens on a dynamic port. | Step 1. For a named instance, use `instanceName` instead of a port. |
| `The driver could not establish a secure connection ... SSL encryption` | Certificate validation failed. | Ensure `trustServerCertificate=true` is in the URL. |
| `Login failed for user 'cred_app'` | The login was not created, the password differs, or SQL authentication is off. | Re-run script 01; check Step 2; confirm `DB_PASSWORD` matches the password in the script. |
| `Cannot open database "credentialing" requested by the login` | The database does not exist, or `cred_app` has no user mapped in it. | Re-run script 01, which creates both. |
| `Invalid object name 'dbo.providers'` | The schema was created in a different database. | Confirm `databaseName=credentialing` in the URL and that script 01 ran against that database. |
| `CREATE TABLE permission denied` at start-up | `ddl-auto` is trying to change the schema and `cred_app` lacks rights. | The schema already matches the entities, so this should not occur. If the entity model changed, run `GRANT ALTER ON SCHEMA::dbo TO cred_app;` or set `ddl-auto: validate`. |
| `Feature not supported: "AUTO_SERVER=TRUE && DB_CLOSE_ON_EXIT=FALSE"` | An older build's H2 URL. | Fixed in this version. Rebuild with `build.bat`. |

---

## Notes for the milestone documentation

- **DDL is script 01.** Every column name, length and nullability is taken from
  the JPA entity classes, so the script and the entity model are two views of
  one design.
- **DML is script 02.** It seeds only the accounts. Provider, document,
  verification and risk rows are deliberately not inserted by hand: they are
  produced by running documents through the pipeline, and hand-written rows
  would be records with no evidence behind them — the one thing a credentialing
  audit trail must not contain. Upload the files in `sample-data/documents` to
  populate the other five tables.
- **The audit table is append-only in the database as well as in the code.**
  Script 01 issues `DENY UPDATE, DELETE ON dbo.audit_logs TO cred_app`, so the
  guarantee does not depend on the application behaving correctly.
- **No password is stored.** `app_users` holds a per-account random salt and a
  PBKDF2-HMAC-SHA256 derived key at 210,000 iterations. The seeded hashes were
  generated with the same algorithm the application uses and verified against
  `PasswordHasher.java`.
