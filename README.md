# AI-Enhanced Provider Credentialing Automation Platform


A working prototype that automates healthcare provider credentialing: it reads a
submitted credential document, extracts the provider's details, verifies them
against the CMS NPPES National Provider Identifier registry and the HHS OIG List
of Excluded Individuals/Entities, identifies unusual records with an anomaly
detection model, and presents the evidence and a risk assessment to a
credentialing specialist for decision.

---

## 1. Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| Java Development Kit | 17 or later | `java -version` must report 17+ |
| Apache Maven | 3.8 or later | Only needed to build the backend |
| Node.js and npm | Node 18+ | Only needed to rebuild the frontend |
| Tesseract OCR | 4.0 or later | Optional. Needed only to read scanned images |

Tesseract is optional. Without it the platform still reads digital PDFs and text
files; image-only documents are routed to the manual review queue instead of
failing.

- Windows: install from <https://github.com/UB-Mannheim/tesseract/wiki> and add
  the install folder to `PATH`, or set `APP_OCR_TESSERACT_COMMAND` to the full
  path of `tesseract.exe`.
- macOS: `brew install tesseract`
- Debian/Ubuntu: `sudo apt install tesseract-ocr`

---

## 2. Quick start

```bash
# 1. Build the Angular client and copy it into the backend's static resources
cd frontend
npm install
npm run build
cp -r dist/* ../backend/src/main/resources/static/      # Windows: xcopy /E /I dist ..\backend\src\main\resources\static

# 2. Build and run the Spring Boot service
cd ../backend
mvn clean package
java -jar target/credentialing-platform.jar
```

Then open <http://localhost:8080> and sign in with one of the demonstration
accounts below.

To run the backend without packaging it first, use `mvn spring-boot:run`.

### Demonstration accounts

| Username | Password | Role | Capabilities |
|---|---|---|---|
| `admin` | `Admin#2026` | Administrator | Everything, including the control panel |
| `reviewer` | `Review#2026` | Credentialing specialist | Upload, verify, record decisions |
| `viewer` | `Viewer#2026` | Compliance officer | Read-only access |

These accounts are created only when the database is empty and only while
`app.security.seed-demo-users` is `true`. Set it to `false` in any deployment
holding real provider information.

---

## 3. Trying the platform

`sample-data/documents/` contains synthetic credential documents covering the
nine outcomes the platform is designed to distinguish. Upload them from the
**Upload document** screen.

| File | What it demonstrates | Expected outcome |
|---|---|---|
| `01-verified-individual.txt` | A clean individual provider | Verified, Approve |
| `02-excluded-provider.txt` | A provider on the OIG exclusion list | Not eligible, Reject |
| `03-address-discrepancy.txt` | Document disagrees with the registry | Needs review |
| `04-invalid-npi.txt` | Identifier fails the CMS check digit | Not eligible, Reject |
| `05-missing-npi.txt` | No identifier supplied | Needs review |
| `06-deactivated-npi.txt` | Identifier deactivated in the registry | Needs review |
| `07-organization-clean.txt` | A clean Type 2 organizational provider | Verified, Approve |
| `08-possible-exclusion-name-match.txt` | Name resembles an excluded party | Needs review |
| `10-verified-individual-digital.pdf` | Digital PDF read from its text layer | Verified, Approve |
| `12-scanned-individual.png` | Scanned image read by optical recognition | Verified, Approve |
| `13-unreadable-scan.png` | Nothing readable in the document | Manual review |

All provider names, addresses, license numbers and identifiers in these files and
in the bundled datasets are invented for this project. They describe no real
person or organization.

---

## 4. Project layout

```
credentialing-platform/
├── backend/                     Spring Boot 3 service (Java 17)
│   ├── pom.xml
│   └── src/main/java/edu/gcu/capstone/credentialing/
│       ├── config/              Configuration, scheduling, demo data seeding
│       ├── security/            PBKDF2 hashing, sessions, RBAC filter
│       ├── domain/              JPA entities and enumerations
│       ├── repository/          Spring Data repositories
│       ├── ocr/                 Pluggable text-extraction engines
│       ├── extraction/          Field parsing, cleansing, NPI check digit
│       ├── verification/        NPPES client, OIG screening, comparison
│       ├── ml/                  Isolation Forest, features, risk scoring
│       ├── workflow/            Pipeline orchestration, storage, retention
│       ├── report/              PDF and CSV report generation
│       ├── audit/               Append-only audit trail
│       ├── web/                 REST controllers and DTOs
│       └── exception/           Error handling
├── frontend/                    Angular 18 single-page application
│   └── src/app/
│       ├── core/                API service, authentication, guards, models
│       ├── shared/              Reusable presentation components
│       └── features/            One folder per screen
├── sample-data/documents/       Synthetic credential documents
├── tools/                       Development and documentation aids
└── docs/                        Screenshots and evaluation evidence
```

---

## 5. Configuration

Every setting lives in `backend/src/main/resources/application.yml` and can be
overridden by an environment variable or a command-line argument, which keeps
environment-specific values out of the source tree.

| Setting | Default | Purpose |
|---|---|---|
| `app.nppes.enabled` | `true` | Set to `false` to work entirely offline |
| `app.nppes.base-url` | CMS NPPES API | The registry endpoint |
| `app.oig.leie-file` | bundled sample | Replace with the full monthly LEIE extract |
| `app.oig.name-match-threshold` | `0.92` | Similarity needed for a possible name match |
| `app.ml.anomaly-threshold` | `0.60` | Anomaly score at which a record is flagged |
| `app.storage.retention-days` | `7` | How long uploaded files are kept |
| `app.security.seed-demo-users` | `true` | Set to `false` outside a demonstration |

Example:

```bash
java -jar target/credentialing-platform.jar \
     --app.nppes.enabled=false \
     --app.storage.retention-days=30 \
     --app.security.seed-demo-users=false
```

### Using Microsoft SQL Server instead of H2

The platform runs on the embedded H2 database out of the box so that nothing
has to be installed first. To run it against SQL Server instead, no Java code
changes — only configuration:

```bash
java -jar target/credentialing-platform.jar --spring.profiles.active=sqlserver --app.nppes.enabled=false
```

`docs/database/` holds the DDL script that creates the schema, the DML script
that seeds the accounts, a teardown script, and a step-by-step setup guide
covering TCP/IP configuration, SQL authentication, connection strings and
troubleshooting. Start with `docs/database/README.md`.

---

## 6. Running the tests

```bash
cd backend
mvn test
```

The suite covers the check-digit algorithm, the field parser, the cleansing
rules, name similarity, exclusion screening, the registry client's offline
behaviour, the Isolation Forest, the risk scoring rules, the full pipeline, and
the access rules at the HTTP boundary.

`AnomalyDetectionServiceTest` asserts the Milestone 2 targets of precision and
recall at or above 0.85, so a change that degraded detection quality fails the
build rather than passing unnoticed.

---

## 7. Third-party software and licensing

| Component | Version | License | Used for |
|---|---|---|---|
| Spring Boot | 3.3.5 | Apache-2.0 | Web framework, dependency injection |
| Spring Data JPA / Hibernate | via Spring Boot | Apache-2.0 / LGPL-2.1 | Persistence |
| H2 Database | via Spring Boot | MPL-2.0 / EPL-1.0 | Embedded SQL database |
| Apache PDFBox | 2.0.31 | Apache-2.0 | PDF text extraction, PDF reports |
| Jackson | via Spring Boot | Apache-2.0 | JSON binding |
| JUnit 5, AssertJ, Mockito | via Spring Boot | EPL-2.0 / Apache-2.0 / MIT | Testing |
| Angular | 18.2 | MIT | Single-page client |
| RxJS | 7.8 | Apache-2.0 | Reactive streams in the client |
| Tesseract OCR | 4.x or 5.x | Apache-2.0 | Optical character recognition (external) |

Data sources: the CMS NPPES NPI Registry public read API, which requires no key
and carries no usage fee, and the HHS OIG List of Excluded Individuals/Entities,
which is published for public download. Both are United States federal
government works.

The anomaly detection algorithm is the Isolation Forest of Liu, Ting and Zhou
(2008), implemented from the published description in
`ml/IsolationForest.java` and `ml/IsolationTree.java`. No third-party machine
learning library is used.

---

## 8. Development aids in `tools/`

`tools/ui-preview-server.js` serves the compiled Angular bundle with a fixed set
of records so the interface can be opened and reviewed without the Java service
running. It performs no verification and contacts no registry. It exists to
support front-end work and to capture the screenshots in the user guide; it is
not part of the product. Its fixed records were produced by running the
platform's own scoring logic over the bundled sample documents; see
`docs/scenario-results.txt`.

---

## 9. Known limitations

These are stated rather than implied, and are discussed in full in Section 9 of
the Milestone 3 document.

1. The anomaly model is trained on a synthetic population, because no corpus of
   real credentialing outcomes was available. Its reported precision and recall
   describe performance against that population, not against live data.
2. Exclusion screening uses a bundled twenty-row sample of the LEIE. A
   deployment must load the full monthly extract.
3. Name-based exclusion matching produces a possible match for human
   confirmation, never a confirmed exclusion.
4. License status is not verified against state licensing boards, which publish
   no common API; only the license number printed on the document is compared
   with the registry's taxonomy license field.
5. The field parser is tuned to labelled, English-language documents. A document
   with an unusual layout may yield missing fields, which the platform reports
   rather than guessing.
