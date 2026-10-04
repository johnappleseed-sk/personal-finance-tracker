# Personal Finance Tracker

A student-friendly, production-style personal finance application built incrementally.
Registration, accounts, categories, transactions, budgets, dashboards, and reports
are planned; they are **not implemented yet**.

## Current foundation

- Spring Boot 4.1.1, Java 27, and Maven Wrapper
- Spring MVC, Thymeleaf, Spring Security, Jakarta Validation, and Spring Data JPA
- PostgreSQL 18 through Docker Compose
- Environment-based datasource configuration and isolated PostgreSQL tests
- Flyway versioned migrations with an initial users table

The architecture is a feature-oriented modular monolith under `com.personalfinance`.
Future requests will flow from controller to service to repository. There are no
Java domain entities yet. Flyway creates the schema before Hibernate validates it;
Hibernate does not generate or update tables.

## Requirements

- JDK 27 (the current Maven compiler target)
- Docker with Docker Compose and a running Docker daemon
- Internet access for initial Maven dependencies and container image downloads

## Local setup

```bash
cp .env.example .env
```

Edit `.env` and set a nonempty, local-only `DB_PASSWORD`. Do not commit this file.
Use a simple properties-compatible value: no surrounding quotes, backslashes,
whitespace, or `$` interpolation. Compose and Spring parse the same file differently;
this restriction keeps the development setup predictable. Production credentials
should be supplied through environment variables rather than this local file.

| Variable | Purpose |
| --- | --- |
| `DB_NAME` | Database initialized by Compose; defaults to `personal_finance` |
| `DB_URL` | JDBC URL used by Spring |
| `DB_USERNAME` | Database username for Compose and Spring |
| `DB_PASSWORD` | Required database password; no committed default |

If you change `DB_NAME`, update the database name in `DB_URL` too. Environment
variables override `.env` values. The database is published only on
`127.0.0.1:5432`; port 5432 must be available.

```bash
docker compose up -d --wait
docker compose ps
./mvnw spring-boot:run
```

Spring Boot reads `.env` when run from the repository root. Its datasource
auto-configuration creates the JDBC connection pool; JPA uses it to connect to
PostgreSQL. Open Session in View is disabled so future services must load the data
needed by the UI within their transactional boundaries.

Visit `http://localhost:8080`. Spring Security currently provides its default
login page and temporary development user, not the planned registration/login
feature. Do not deploy this foundation as a finished application.

Useful database commands:

```bash
docker compose logs postgres
docker compose down
```

The named volume preserves data across container restarts. PostgreSQL initialization
variables only apply to an empty volume; changing credentials in `.env` does not
change an existing database's credentials. Do not delete the volume to fix this
without intentionally choosing to lose its data. Never run `docker compose down -v`
unless data deletion is intended.

## Tests and build

With Docker running:

```bash
./mvnw test
./mvnw clean verify
```

Testcontainers starts an isolated PostgreSQL 18 container on a random port and
supplies its credentials to Spring. Tests do not need `.env` or the Compose
database and do not touch local financial data. They verify application startup,
an actual database query, rejection of incorrect credentials, and safe JPA configuration.
The application starts an embedded web server on a random port. Docker unavailability fails
the tests rather than silently skipping them.

Migration tests also verify first application, schema history, repeat runs,
checksum mismatch rejection, refusal to baseline unmanaged schemas, disabled
clean operations, and users-table constraints. All test data lives in disposable
containers, not your Compose database.

## Database migrations

Spring Boot automatically runs Flyway on startup using the configured datasource.
Versioned SQL lives in `src/main/resources/db/migration`:

- `V1__create_users.sql`: identity primary key, required name/email/password hash,
  unique normalized email, and timezone-aware creation/update timestamps.
- Flyway maintains `flyway_schema_history` to record versions and checksums.

Email must be trimmed and lowercase before persistence. The unique constraint
also supplies an index for future email lookups. `password_hash` is reserved for
encoded passwords; the database cannot determine whether a value is securely
encoded, so registration must enforce BCrypt. Timestamp defaults initialize both
fields; future user services must maintain `updated_at` on changes.

Add a new migration for each schema change, for example `V2__create_accounts.sql`.
Never edit, rename, or delete an already-applied migration. Flyway validates
checksums and stops startup if migration history no longer matches the files.

Automatic baselining is disabled: an existing nonempty schema without Flyway
history requires investigation and an explicit adoption plan, not a bypass.
Flyway `clean` is disabled to protect data. If migration startup fails, inspect
the error and database state; do not delete volumes, enable automatic baselining,
or run repair merely to suppress the error. Back up existing data before applying
schema changes to any non-disposable database.

## Structure

```text
src/main/java/com/personalfinance/       Application entry point; future features
src/main/resources/application.properties
src/main/resources/db/migration/        Versioned Flyway SQL migrations
src/test/java/com/personalfinance/      PostgreSQL-backed foundation tests
compose.yaml                           Local PostgreSQL service and volume
.env.example                           Credential-free configuration template
```

## Next milestone

Create the Java user model and implement registration with BCrypt and validation,
then add authentication, CSRF-protected forms, and user-owned financial features.
