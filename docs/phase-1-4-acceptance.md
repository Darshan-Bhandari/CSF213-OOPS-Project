# Phase 1–4 acceptance review — 16 November 2026

This reviews the current implementation, not final course submission readiness. Historical failures remain in `docs/testing.md`; the latest evidence below supersedes their status.

## Requirements and design review

The 17-page `OOP_S1_2026-2027_Project.pdf` was re-extracted and checked against R01–R27 in `plan.md`. It requires three roles, the report-to-close lifecycle, priority inputs, at least two assignment strategies and notification channels, SLA tracking, full history, at least five filters, two intelligent/automated features, OOP/pattern evidence, persistence, documented tests, a report and a 15–20 minute team video. The deadline remains 22 November 2026; exact cutoff and the example filename year discrepancy still need course clarification.

Phases 1–2 decisions are approved. Runtime/architecture/contracts and actual design-pattern/inheritance evidence are documented in `architecture.md`. Group/member allocation, final UML/ER/report/video and submission destination details remain deferred deliverables. AI model/key/budget and public deployment are not silently selected. Source repository is now established; older plan lines treating it as pending were reconciled.

| Phase/gate | Latest evidence | Outcome |
|---|---|---|
| 1: requirements/scope | Source PDF cross-check; approved P01–P10 plus repeat-report scope register | Scope gate complete; team administration deferred |
| 2: architecture/design | `architecture.md`; explicit State and Strategy contracts; notification inheritance; pinned build/runtime | Design gate complete; final submission diagrams deferred |
| 3: foundation | Clean build; real role logins; demo provisioning/restart idempotence; CSRF/logout/deactivation; desktop/mobile dashboards | Accepted in tested environment |
| 4: core workflow | Rules/HTTP/database/transaction tests; real packaged lifecycle with both process restarts; browser lifecycle/privacy/feedback | Accepted in tested environment |

## Requirements coverage at this gate

R01–R07, R10, R12 and core R18/R19 have concrete Phase 1–4 implementation/check evidence. R03/R04 retain later assignment/SLA/report monitoring acceptance; R08/R09/R11 are Phase 5 gates despite supporting source/tests. R13/R14 await Phase 6 reporting/intelligence acceptance and live AI decisions. R15/R16 have source/test design evidence but final diagram/report justification remains due. R17/R18 broader outage/security/stabilization cases remain Phase 7 work. R20–R27 include final report, diagrams, course link submission, test documentation presentation and team recording obligations; establishing a repository and exceeding 15 automated cases does not mark those submission deliverables complete.

## Automated evidence

- `MAVEN_USER_HOME="$PWD/.local/maven-home" ./mvnw clean verify`: **143 regression tests plus 1 packaged restart acceptance test passed**, zero failures/errors/skips. Surefire and Failsafe reports are in ignored `target/`.
- `python3 -m unittest discover -s src/test/python -v`: **4 launcher tests passed**. Literal shell-like values do not execute; blank values, environment precedence, invalid config and exit propagation behave correctly.
- Shell syntax checks passed. `git diff --check` passed. Test artifacts, generated credentials, native browser cache and databases are ignored by Git.

`WorkflowRestartIT` launches the built JAR on random loopback ports with a separate persistent PostgreSQL directory. It logs in all three demo roles, verifies admin pages and cross-role denial, reports/assigns/investigates a ticket, exchanges a private note and linked request/reply, resolves/rejects/re-resolves, closes and rates it. It also checks public registration cannot inject ADMIN and deactivation revokes a real session. Java and PostgreSQL stop and restart; complete incident/conversation/resolution/cycle/audit/notification/user/seed-marker/migration snapshots remain equal. Changing the supplied demo seed password does not reset existing accounts or duplicate seed records. Logout and tokenless POST rejection pass over real HTTP. The test performs no live AI call.

## Browser and visual evidence

`scripts/browser_acceptance.cjs` runs against an isolated localhost demo instance, not the existing development database. It uses three independent Chromium sessions at **1440×1000** and **390×844**. It covers actual form submission, technician acknowledgment/investigation, private note visibility, request/reply linkage, unchanged state/deadlines during conversation, rejection/re-resolution, closure, feedback and mobile sign-out. Admin users/catalog/audit/simulated-email screens load. It asserts the active admin navigation link, no horizontal page overflow on captured pages, no JavaScript exceptions and no server 5xx responses.

Screenshots and `results.json` are written to ignored `.local/acceptance-screenshots/`. Representative desktop dashboards/catalog/investigation and mobile dashboard/detail/catalog screenshots are visually inspected. These tests are not Firefox/Safari coverage, a full accessibility audit, touch-device testing, performance certification or production security certification.

## Defects found and fixed during Step 8

1. Real forbidden POST forwards reached a GET-only denial page and returned 405. The denial page now handles the forwarded method and correctly returns 403.
2. Null registration service inputs could throw an internal exception; they now produce a business validation error without partial users.
3. Assignment strategy/availability/skill configuration lacked active-admin checks at the service boundary; they now match protected HTTP routes.
4. Mobile CSS hid sign-out. It now stays visible, and all three mobile-role sign-outs are exercised.
5. Overview was highlighted on every page. Active navigation is rendered from the current route, including pages without JavaScript.
6. Initial history entries displayed a literal null prior state. They now display a neutral dash.

No migration checksum was repaired and no existing development data was reset. V1–V4 remain immutable. The development database helper now permits `-Ddev.db.port=...` and `-Ddev.db.directory=...` for isolated acceptance runs while preserving its defaults and loopback binding.

## Reproduction and limits

See README for local startup, `mvnw verify`, launcher checks and the optional browser runner. Tested with JDK 25.0.1 targeting Java 21 on Apple Silicon macOS, native PostgreSQL 14.15.0 and bundled Playwright 1.62.1 Chromium. Other JDK/OS/browser combinations remain untested. Later-phase AI/reporting/SLA/notification source and some tests exist, but Phases 5–6 are not marked accepted by this review. Repeat-report escalation is recorded future scope. Final documentation, member responsibilities, recordings and course submission remain outstanding.
