# UI modernization: audit and implementation plan

## Baseline and boundaries

The application is a server-rendered Spring MVC/Thymeleaf modular monolith, with
plain CSS and progressive-enhancement JavaScript. Data comes from JPA services and
PostgreSQL, not a frontend API or mock store. Spring Security controls sessions,
CSRF, and authentication; resource services enforce ownership. Flyway V1–V5 owns
schema changes. No framework, schema, API, or financial rule change is needed.

The working tree already contains presentation, dashboard, CSS, JS, template,
test, and startup-script edits. These must be preserved. Presentation deliverables
and startup configuration are outside this redesign.

## Audit findings

- Seven navigation destinations compete with theme/language/logout controls in a
  wrapping toolbar. Desktop content has no consistent readable maximum width.
- Dashboard analysis precedes balances; several headings are long, and every card
  competes through accents, elevation, gradients, and hover movement.
- Forms stretch across the full desktop, with shortcuts competing with labels.
- Card/table views duplicate sorting code. Sortable headers only respond to clicks;
  tables need keyboard-scroll cues and sorting announcements.
- Search hides rows without a no-results explanation. Empty filtered ledgers are
  incorrectly described as having no transactions at all.
- Theme, language, password and category icons use emoji/text glyphs. Dynamic labels
  can overwrite icons and lose translation state. Lucide integration is in progress.
- Browser storage contains display preferences only; legacy ownerless financial
  goals stay disabled and untouched. Storage restrictions should not break the UI.
- POST buttons lack reliable pending feedback/back-navigation recovery. Native and
  server validation exist and must remain authoritative.
- Dark mode, Khmer, CSS charts, CSV exports, budget alerts, transfer forms, and
  destructive confirmation pages already function and must remain available.

## Direction

Quiet neutral surfaces, one restrained indigo action color, readable system fonts,
compact status badges, consistent borders, and minimal elevation. Use locally
served Lucide SVGs, never emoji icons. Desktop gets a persistent navigation rail;
tablet/mobile retain a non-modal disclosure menu. Do not introduce fake settings,
notifications, loading skeletons, pagination, or profile pages without real behavior.

## Implementation sequence

1. Centralize surface/color/type/spacing/motion tokens and update shared components.
2. Modernize the shared navigation shell, dashboard hierarchy, and form widths.
3. Finish local Lucide integration, including dynamically updated controls.
4. Improve reusable empty states, filter feedback, tables, and form pending/errors.
5. Preserve light/dark/Khmer, reduced motion, no-JS behavior, all data routes and CSRF.
6. Add regression tests, run Maven tests/build and JS checks, then exercise real
   pages and workflows against an isolated database at 375/768/1024/1440/1920px.

## Verification scope

Automated checks must cover real registration/login, account/category creation,
income/expense entry, budget/transfer forms, filtered results, CSV, confirmations,
keyboard navigation/sorting, icons after theme/language changes, no console errors,
and no page-level horizontal overflow. Horizontal scrolling inside labeled data
tables is intentional. Do not create QA users in the user's normal database.

This is not a full WCAG certification or a public-deployment security review.

## Implemented design and workflow improvements

- Persistent desktop rail from 1024px; accessible non-modal disclosure below it.
  Shared branding, current-route state, local SVGs, theme/language/logout controls.
- Neutral theme tokens, indigo primary actions, restrained radii/elevation, readable
  typography, consistent 44px action targets, and a capped large-screen content area.
- Dashboard balances precede interpretation. Existing charts, savings benchmarks,
  budget watch, recent entries, and reports remain present and use real service data.
- Focused forms, server-error summaries with links/focus, pending POST feedback and
  back-navigation recovery. Type/account changes clear now-invalid dependent choices.
- Shared onboarding empty-state fragment; filtered empty ledgers and live no-match
  results are distinguished from a new user's empty ledger.
- A single sortable-table implementation uses native buttons and `aria-sort`.
  Wide tables have keyboard-focusable, labeled scrolling regions. View and filter
  buttons announce pressed state. Currency tabs retain arrow/Home/End navigation.
- Locally served Lucide 0.468.0 replaces emoji throughout application templates and
  dynamic category/theme/language/password controls. Icons inherit theme colors and
  are decorative; text labels and no-JS navigation remain usable.
- Chart bar values have accessible names; motion respects system preferences.
  Automated contrast findings in light/dark controls were corrected.
- Display-preference storage failures do not break the app. Portfolio display totals,
  quick amount additions, and numeric sorting use integer cents instead of floating
  point. Backend money calculations and business rules were not changed.

## Files created

- `src/main/resources/static/css/product.css`: shared product layout and component refinements.
- `src/main/resources/static/css/login.css`: login-only responsive split layout and focused sign-in form.
- `src/main/resources/templates/fragments/empty-state.html`: reusable onboarding state.
- `src/main/resources/static/vendor/lucide/{lucide.min.js,LICENSE,README.md}`: pinned, local icon library and attribution.
- `src/test/java/com/personalfinance/ui/UiRenderingTests.java`: view, asset-access, escaping, CSRF, and icon regressions.
- `scripts/ui_smoke.py`: repeatable isolated browser/workflow/accessibility review.
- `UI_REDESIGN.md`: audit, implementation plan, and this report.

## Files modified in this redesign

- `src/main/resources/static/css/app.css`: centralized theme/spacing tokens.
- `src/main/resources/static/js/app.js`: shared icons, sorting, filters, pending/error
  states, storage fallback, dependent fields, and accessibility enhancements.
- `src/main/resources/templates/fragments/{head,navigation}.html`: local assets and application shell.
- `src/main/resources/templates/auth/{home,login,register,registration-success}.html`.
- `src/main/resources/templates/accounts/list.html`.
- `src/main/resources/templates/categories/{form,list}.html`.
- `src/main/resources/templates/budgets/list.html`.
- `src/main/resources/templates/transactions/list.html`.
- `src/main/resources/templates/transfers/{form,list}.html`.
- `src/main/resources/templates/reports/index.html`.
- `src/main/java/com/personalfinance/security/SecurityConfiguration.java`: permit
  public icon assets only; protected routes, authentication, and CSRF are unchanged.
- `README.md`: interface/test instructions and corrections to stale feature descriptions.

Earlier local changes to other forms, presentation files, startup helpers, and tests
were not reverted or included in a commit. Existing legacy goal data was not read,
deleted, or migrated. No application schema or service persistence logic changed.

## Performance and maintainability

No framework migration, package manager, remote fonts, or runtime CDN was introduced.
Icons load deferred from the same origin, and already-rendered SVGs are not rebuilt
when a control label changes. Sorting duplication was removed. CSS charts remain
dependency-free; display feedback is updated without backend requests. The original
stylesheet is retained as a compatibility base rather than overwritten wholesale.

## Verification and limitations

- `./mvnw test` and `./mvnw clean verify`: 167 tests passed, none skipped.
- JavaScript syntax and browser smoke script syntax checks passed.
- Chromium workflow review: 17 populated routes at 375, 768, 1024, 1440, and 1920px,
  including card/table views; no page-level horizontal overflow.
- Real registration/login, account/category/transaction creation, transaction
  editing/deletion/cancellation, budgets, transfers, balances, CSV, search/filter
  feedback, validation focus, keyboard sorting/tabs/menu/skip link, and SVG rendering.
- Dark/Khmer controls, no-JS navigation, blocked storage, and no browser console errors.
- 36 axe-core 4.10.3 scans using WCAG 2 A/AA, 2.1 AA, and 2.2 AA tags: no violations
  in the scanned states. This does not prove full WCAG conformance.
- QA uses a disposable PostgreSQL container and a random loopback application port;
  normal user data is untouched. Screenshots/results: `target/ui-review/` (ignored).

Remaining recommendations: Safari/Firefox and real screen-reader/touch-device review;
complete Khmer localization via message bundles (the existing client dictionary is
partial); server-side pagination for large ledgers; and an icon-subset bundle if
asset size becomes a concern (the vendored UMD bundle is about 358KB uncompressed).
No Lighthouse performance score or full manual WCAG audit is claimed. No fake
profile/settings routes, modal behavior, or loading skeletons were added.

### Login refinement

The sign-in page now has an editorial feature introduction alongside a focused form
on desktop. On mobile, the introduction reduces to a compact brand header so the
form takes priority. Shared theme/language controls, Lucide icons, password reveal,
registration link, generic login errors, and CSRF behavior remain intact. The page
does not offer fake social login or an unimplemented password-reset action.
Login-only CSS is conditionally loaded by the shared head fragment; registration
and other pages retain their existing layouts. Smoke checks cover login at all
five widths, dark/Khmer states, rejected credentials, password clearing, and successful login.
