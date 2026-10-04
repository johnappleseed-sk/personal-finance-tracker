# Personal Finance Tracker

A student-friendly, production-style personal finance application built incrementally.
Registration, accounts, categories, transactions, budgets, dashboards, and reports
are planned; they are **not implemented yet**.

## Current foundation

- Spring Boot 4.1.1, Java 27, and Maven Wrapper
- Spring MVC, Thymeleaf, Spring Security, Jakarta Validation, and Spring Data JPA
- PostgreSQL 18 through Docker Compose
- Environment-based datasource configuration and isolated PostgreSQL tests

The architecture is a feature-oriented modular monolith under `com.personalfinance`.
Future requests will flow from controller to service to repository. There are no
domain entities or database migrations yet. Hibernate validates the schema and
does not create it; Flyway migrations are the next phase.

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

## Structure

```text
src/main/java/com/personalfinance/       Application entry point; future features
src/main/resources/application.properties
src/test/java/com/personalfinance/      PostgreSQL-backed foundation tests
compose.yaml                           Local PostgreSQL service and volume
.env.example                           Credential-free configuration template
```

## Next milestone

Introduce Flyway before creating the user model, then implement registration
and authentication with BCrypt, validation, CSRF protection, and ownership checks.
