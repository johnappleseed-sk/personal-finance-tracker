# Personal Finance Tracker

A student-friendly, production-style personal finance application built incrementally.
User registration, login/logout, an authenticated welcome page, and user-owned
account/category management and income/expense transactions are implemented.
Budgets, financial dashboards, and reports are planned; they are **not implemented yet**.

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
- Private income/expense category CRUD with validated forms and confirmation before deletion
- Private transaction CRUD, exact derived account balances, and database-enforced history protection

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
- Current balance is calculated from the opening balance plus recorded income
  minus recorded expenses, per account/currency. Negative current balances are allowed.
- Editing the opening balance is a correction and also changes the current balance;
  it does not create a ledger entry. Currency cannot change while transactions exist.
- Deletion requires a confirmation page and CSRF-protected POST, and is permanent.
  Accounts with transactions cannot be deleted. GET never deletes an account.

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

## Categories

Open `/categories` after signing in, or follow **Categories** in the navigation.
Create a label such as Salary (Income) or Groceries (Expense), edit its name/type,
or delete it through a read-only confirmation page followed by a CSRF-protected
POST. Names are required, trimmed, and limited to 100 characters. Only Income and
Expense are supported; transfers are neither. Duplicate names are allowed, and
no shared or default categories are seeded.

Categories belong to the authenticated user, not to a particular account.
The allowlisted form accepts only name and type. `CategoryService` validates
inputs and uses owner-scoped repository queries for every lookup, update, and
delete. It returns detached `CategoryView` data. Submitted owner IDs cannot
reassign ownership, and missing/foreign resources share the same safe 404 page,
including invalid foreign edit submissions.

Categories can be renamed. A category with transactions cannot change type or be
deleted; the application gives safe HTTP 409 feedback rather than cascading history.
Unused categories can change type or be permanently deleted. Category totals are
not implemented yet. Renames are reflected in ledger views; names are not snapshots.

To check manually, create both types, edit one, cancel deletion, then confirm it.
Try an empty name and verify that nothing is saved. In a second browser profile,
sign in as another user: the list must be separate and the first user's edit/delete
URLs must return 404. Use synthetic category names during development.

## Transactions and balances

Open `/transactions` after creating an account and a matching income/expense
category. Create, list (newest date/ID first), edit, and delete ledger entries:

- Account and category: both must belong to the signed-in user. Browser-submitted
  reference IDs are untrusted and are checked at the service boundary.
- Type: Income or Expense; it must match the chosen category. Transfers are not supported.
- Amount: positive, at least 0.01, at most 17 whole-number digits and 2 decimal
  places; extra decimals are rejected, not rounded. Income adds and expenses subtract.
- Date: required, today or earlier according to the server's date. Future/scheduled
  transactions are not supported. Dates are calendar dates, not UTC timestamps.
- Description: optional, trimmed, at most 255 characters; escaped in HTML.
- Owner, currency, IDs, and timestamps are not bindable. Currency is derived from
  the authorized account. No conversion or combined cross-currency total exists.

Editing replaces an entry atomically. It can change the account, category, type,
amount, date, or description. Moving to another account removes the old balance
effect and applies the submitted amount in the new account's currency; it is a
correction, **not an exchange or transfer**. Enter the intended amount in that
currency yourself. Deletion requires a read-only confirmation page and CSRF-protected
POST and permanently removes the entry and its balance effect. There is no audit
trail, reconciliation, undo, pagination, or transaction filtering yet; lists are
intended for assignment-sized datasets.

Balances are not mutable cached counters. `TransactionRepository` calculates exact
income-minus-expense deltas in one grouped query, and `AccountService` adds each
account's opening balance using `BigDecimal`. Creating, editing, moving, and deleting
entries cannot leave a stale cached balance. Aggregate balances may exceed the
precision limit of a single amount; no database column truncates the total.

Ledger writes lock authorized account/category rows; parent edits/deletes use the
same locks before checking usage, and entry edits/deletes lock the entry. Composite
foreign keys independently enforce matching owner, account currency, and category
type, including direct SQL writes. They prevent deletion/reinterpretation of referenced
resources without cascading transactions. Missing/foreign transaction URLs share
the same generic 404. Unavailable selected references produce neutral form errors.

To check manually, start an account at 100.00, record income of 20.10 and an expense
of 5.20, and verify its current balance is 114.90. Edit/delete an entry and confirm
the balance changes. Try a mismatching category or another user's reference IDs:
nothing must be saved. Try deleting the used account/category: history must remain
and the response must explain the conflict. Use synthetic ledger data.

## Database operations

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

Category tests cover both types, duplicate names and stable ordering, ownership
isolation at MVC/service boundaries, forged IDs/owners, authentication/CSRF on all
mutations, validation, safe malformed-ID responses, HTML escaping, timestamps,
read-only confirmation, and PostgreSQL constraints. The V2-to-V3 migration test
preserves an existing user and exact account balance while adding categories.

Transaction tests cover ownership of entries and both references, forged owner/currency
inputs, CSRF/authentication, validation, exact arithmetic, negative and large balances,
create/edit/move/delete effects, atomic failure, ordering, escaping, concurrent inserts,
history-protected parent changes, composite foreign keys, and non-finite amount rejection.
The V3-to-V4 upgrade test preserves account/category data and inserts a valid ledger entry.

## Database migrations

Spring Boot automatically runs Flyway on startup using the configured datasource.
Versioned SQL lives in `src/main/resources/db/migration`:

- `V1__create_users.sql`: identity primary key, required name/email/password hash,
  unique normalized email, and timezone-aware creation/update timestamps.
- `V2__create_accounts.sql`: owner foreign key, account name/type, exact opening
  balance, supported currency checks, timestamps, and an owner/name/ID index.
- `V3__create_categories.sql`: owner foreign key, required name, income/expense
  check, timestamps, and an owner/name/ID index. Existing migrations are unchanged.
- `V4__create_transactions.sql`: exact positive amounts, dated income/expense entries,
  composite ownership/currency/type foreign keys, and ledger lookup indexes. V1–V3
  are unchanged; new unique parent keys support those foreign keys without rewriting data.
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
src/main/java/com/personalfinance/category/ Private income/expense category CRUD
src/main/java/com/personalfinance/transaction/ Private ledger CRUD and history protection
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

Add transaction filtering/pagination and user-owned budgets before financial dashboards
and reports. Transfers, currency conversion, and an audit trail remain future work.
Applied V1–V3 migrations remain unchanged.
