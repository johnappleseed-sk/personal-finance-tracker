# Personal Finance Tracker

A student-friendly, production-style personal finance application built incrementally.
User registration is implemented. Login, accounts, categories, transactions,
budgets, dashboards, and reports are planned; they are **not implemented yet**.

## Current foundation

- Spring Boot 4.1.1, Java 27, and Maven Wrapper
- Spring MVC, Thymeleaf, Spring Security, Jakarta Validation, and Spring Data JPA
- PostgreSQL 18 through Docker Compose
- Environment-based datasource configuration and isolated PostgreSQL tests
- Flyway versioned migrations with an initial users table
- Validated, CSRF-protected registration with BCrypt password hashing
- Responsive Thymeleaf registration pages and a JPA user model

The architecture is a feature-oriented modular monolith under `com.personalfinance`.
Registration flows from `RegistrationController` to `RegistrationService` to
`UserRepository`. A form DTO accepts browser input; the JPA `User` entity holds
persistent state. Flyway creates the schema before Hibernate validates it;
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

Visit `http://localhost:8080/register`. Registration creates a user and redirects
to a confirmation page without signing in. Login is the next milestone: a
deny-all identity lookup deliberately prevents both registered users and Spring's
generated development account from signing in. Other application routes remain
protected. Do not deploy this foundation as a finished application.

## Registration

- Name: required, trimmed, at most 100 characters.
- Email: required, valid format, trimmed/lowercase using `Locale.ROOT`, at most
  254 characters, and unique. The database constraint also handles concurrent inserts.
- Password: required, 12–72 characters and at most 72 UTF-8 bytes (BCrypt's limit).
  Passwords are not trimmed, truncated, logged, or rendered back into HTML.
- Password confirmation must match. BCrypt uses work factor 12 and a random salt.
- Validation runs in MVC and at the service boundary. Forms cannot assign IDs,
  password hashes, timestamps, or privileges.
- CSRF protection remains enabled; Thymeleaf generates the hidden CSRF token.

To try it, register a unique email, then submit it again using uppercase letters:
the second request should show a safe field error without creating another user.
Try an invalid email or mismatching passwords and verify the form shows errors
while both password fields stay empty. Refreshing the success page does not
resubmit registration. Use synthetic test details while developing.

Before a public deployment, finish authentication and add operational protections
such as HTTPS, secure production session cookies, and registration/login rate
limiting. Duplicate-email feedback can reveal whether an address is registered;
the current assignment flow uses a neutral message but does not eliminate that risk.

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

Registration tests cover PostgreSQL persistence, BCrypt verification, canonical
email conflicts, service validation, password character/UTF-8 limits, CSRF failures,
protected routes, mass-assignment protection, HTML escaping, and password redaction.
Service unit tests cover concurrent unique-constraint error translation and ensure
unrelated database failures are not mislabeled as email conflicts.

## Database migrations

Spring Boot automatically runs Flyway on startup using the configured datasource.
Versioned SQL lives in `src/main/resources/db/migration`:

- `V1__create_users.sql`: identity primary key, required name/email/password hash,
  unique normalized email, and timezone-aware creation/update timestamps.
- Flyway maintains `flyway_schema_history` to record versions and checksums.

Email must be trimmed and lowercase before persistence. The unique constraint
also supplies an index for future email lookups. `password_hash` is reserved for
encoded passwords; the database cannot determine whether a value is securely
encoded, so registration enforces BCrypt in its service. Timestamp defaults support
SQL inserts; JPA lifecycle callbacks initialize timestamps and update `updated_at`
when a managed user changes. Bulk SQL updates would need to maintain it explicitly.

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
src/main/java/com/personalfinance/auth/  Registration form, controller, service
src/main/java/com/personalfinance/user/  User entity and repository
src/main/java/com/personalfinance/security/ Security policy and password encoder
src/main/resources/application.properties
src/main/resources/db/migration/        Versioned Flyway SQL migrations
src/main/resources/templates/           Thymeleaf pages and reusable head fragment
src/main/resources/static/css/          Mobile-first styles
src/test/java/com/personalfinance/      PostgreSQL-backed foundation tests
compose.yaml                           Local PostgreSQL service and volume
.env.example                           Credential-free configuration template
```

## Next milestone

Implement database-backed login/logout and an authenticated landing page, then
add user-owned account management. No schema changes were needed for registration;
the existing V1 migration remains unchanged.
