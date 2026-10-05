# Personal Finance Tracker

A student-friendly, production-style personal finance application built incrementally.
User registration, login/logout, an authenticated welcome page, and user-owned
account management are implemented. Categories, transactions, budgets, financial dashboards, and reports are
planned; they are **not implemented yet**.

## Current foundation

- Spring Boot 4.1.1, Java 27, and Maven Wrapper
- Spring MVC, Thymeleaf, Spring Security, Jakarta Validation, and Spring Data JPA
- PostgreSQL 18 through Docker Compose
- Environment-based datasource configuration and isolated PostgreSQL tests
- Flyway versioned migrations with an initial users table
- Validated, CSRF-protected registration with BCrypt password hashing
- Responsive Thymeleaf registration pages and a JPA user model
- Database-backed authentication with email login and a CSRF-protected sign-out form
- Ownership-scoped account CRUD with exact opening balances and responsive pages

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
| `SESSION_COOKIE_SECURE` | Set `true` when serving the application through production HTTPS; local HTTP defaults to `false` |

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

Visit `http://localhost:8080`. Anonymous visitors are redirected to `/login`.
Register a user at `/register`, then sign in with its email and password.
Registration still does not sign you in automatically. There is no generated
development account. The authenticated `/home` page is a welcome screen, not a
financial dashboard. Do not deploy this foundation as a finished application.

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

Before a public deployment, add operational protections
such as HTTPS, `SESSION_COOKIE_SECURE=true`, and registration/login rate
limiting. Duplicate-email feedback can reveal whether an address is registered;
the current assignment flow uses a neutral message but does not eliminate that risk.

## Login, logout, and sessions

Spring Security processes `POST /login`; the MVC controller only renders the
Thymeleaf form. `DatabaseUserDetailsService` normalizes the email and loads the
stored BCrypt hash. A strict `PasswordEncoder` wrapper rejects inputs beyond
72 UTF-8 bytes on both encoding and verification, preventing BCrypt prefix
truncation. Passwords are never trimmed. Failed logins show the same generic
message for unknown emails and incorrect passwords.

Successful authentication always redirects to `/home`, rotates an existing
session ID, and erases credentials from the security context. A detached
`FinanceUserDetails` principal retains the trusted database user ID and display
name; ownership checks use that ID, never a submitted `userId`.
The welcome page renders only that user's escaped display name, not their entity,
email, or hash. Display names are session snapshots and refresh on a new login.

Login and logout require CSRF tokens. Sign out uses `POST /logout`, invalidates
the session, clears authentication, and deletes the session cookie. `GET /logout`
does not sign a user out. Redirect destinations are fixed; saved requests and
browser-supplied return URLs are not used. Spring Security sends no-store headers
for protected pages.

Sessions expire after 20 minutes of inactivity. Cookies are HttpOnly and
SameSite=Lax, and session IDs are not written into URLs. Secure cookies are
configurable because local development uses HTTP; production requires HTTPS
and `SESSION_COOKIE_SECURE=true`. Remember-me, password resets, and account
verification are not implemented.

To test manually, sign in, open `/home`, and sign out. `/home` must then redirect
back to login. An uppercase version of a registered email should work; a wrong
password should show a generic error and leave the password field empty. Try
two separate browser profiles with different users: each should see only its own
name. Existing users created through registration in Phase 4 can sign in without
schema changes.

## Financial accounts

After signing in, open `/accounts` or follow **Accounts** in the navigation.
Create, list, edit, and delete accounts belonging to your user:

- Name: required, trimmed, at most 100 characters; duplicate names are allowed.
- Type: Checking, Savings, Cash, or Credit card.
- Opening balance: required, at most 17 whole-number digits and 2 decimal places.
  Java `BigDecimal` and PostgreSQL `NUMERIC(19,2)` preserve exact decimal amounts;
  extra decimal places are rejected, not silently rounded. Negative values represent
  debt or overdrafts. This is not a transaction-derived current balance.
- Currency: EUR, USD, or GBP; all use two decimal places. No conversion or
  cross-currency total is calculated.
- Editing can change all four fields. There is no transaction history yet;
  future transaction features must protect history and define balance adjustments.
- Deletion requires a confirmation page and CSRF-protected POST, and is permanent.
  Merely visiting a GET URL never deletes an account.

`AccountController` accepts an allowlisted form DTO, extracts the trusted
`FinanceUserDetails.getUserId()`, and calls `AccountService`. Every resource lookup
uses both account ID and owner ID, including edit, delete, and invalid edit
submissions. Foreign and nonexistent accounts return the same generic 404 page.
Services validate input and return detached `AccountView` records without exposing
the owner entity. Browser-supplied IDs, owners, and timestamps cannot be assigned.

To check manually, create an account, edit its opening balance, and cancel deletion
before confirming it. In another browser profile, register a second user and try
the first user's edit/delete URLs: they must return 404, and each list must show
only its owner's accounts. Try an amount with three decimal places or an empty
name; no data should change. Use synthetic financial data during development.

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

Authentication tests cover real database login, generic failures, canonical emails,
CSRF for login/logout, session ID rotation, credential erasure, fixed redirects,
session isolation, escaped display names, logout invalidation, and rejection of
oversized passwords without BCrypt prefix matching. All preexisting migration and
registration tests remain part of the suite.

Account tests cover ownership isolation at MVC and service boundaries, forged
owner/ID input, CSRF and authentication on every mutation, escaped account names,
exact monetary boundaries, negative amounts, validation, timestamps, safe 404s,
delete confirmation, and PostgreSQL checks/foreign keys. Migration tests also
verify upgrading an existing V1 users schema without losing its users.

## Database migrations

Spring Boot automatically runs Flyway on startup using the configured datasource.
Versioned SQL lives in `src/main/resources/db/migration`:

- `V1__create_users.sql`: identity primary key, required name/email/password hash,
  unique normalized email, and timezone-aware creation/update timestamps.
- `V2__create_accounts.sql`: owner foreign key, account name/type, exact opening
  balance, supported currency checks, timestamps, and an owner/name/ID index.
- Flyway maintains `flyway_schema_history` to record versions and checksums.

Email must be trimmed and lowercase before persistence. The unique constraint
also supplies an index for future email lookups. `password_hash` is reserved for
encoded passwords; the database cannot determine whether a value is securely
encoded, so registration enforces BCrypt in its service. Timestamp defaults support
SQL inserts; JPA lifecycle callbacks initialize timestamps and update `updated_at`
when a managed user changes. Bulk SQL updates would need to maintain it explicitly.

Add a new migration for each schema change, using the next version number.
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
src/main/java/com/personalfinance/auth/  Authentication pages and registration
src/main/java/com/personalfinance/account/ Owner-scoped account CRUD and view/form DTOs
src/main/java/com/personalfinance/user/  User entity and repository
src/main/java/com/personalfinance/security/ Security policy, principal, identity lookup, password encoder
src/main/resources/application.properties
src/main/resources/db/migration/        Versioned Flyway SQL migrations
src/main/resources/templates/           Thymeleaf pages and reusable head fragment
src/main/resources/static/css/          Mobile-first styles
src/test/java/com/personalfinance/      PostgreSQL-backed foundation tests
compose.yaml                           Local PostgreSQL service and volume
.env.example                           Credential-free configuration template
```

## Next milestone

Add user-owned categories, then transactions with explicit balance semantics and
history-safe account deletion. Budgets, financial dashboards, and reports remain
future work. The applied V1 users migration remains unchanged.
