# Verification record

Latest run (16 November 2026, 01:33 IST): **131 passed, zero failures/errors/skips**. Phase 4 Steps 1–6 backend/form checks pass; browser acceptance is partial. Earlier failures below are historical. See the last section for current scope.

## Foundation verification

Scope: Phase 3 accounts, security, migrations and role-specific pages. These are implemented test cases, not a claim of execution success. Full project acceptance still requires later incident/business-rule and browser tests.

| Test | Expected outcome |
|---|---|
| Real PostgreSQL migrations | Five services, six categories and four policies loaded |
| Anonymous dashboard access | Redirect to login |
| Public login/register pages | Render successfully, login contains CSRF token |
| Privilege injection during registration | Account remains EMPLOYEE; password is hashed |
| Missing CSRF during registration | 403; no account persisted |
| Duplicate email with different case | Rejected with field/form error |
| Invalid registration input | Errors shown; no account created |
| Correct/incorrect credentials | Success redirects home; invalid credentials rejected |
| Three role dashboards without AI key | Pages render; AI shown as not configured |
| Employee access to admin read/write | 403; no privileged account created |
| Admin creates technician | Account/profile/audit record persist together |
| Deactivated account | New login rejected; existing session revoked |
| Admin self-deactivation | Rejected |
| Non-admin provisioning service call | Rejected |
| Logout | CSRF-protected POST invalidates session |

## Execution evidence — 2026-11-15

- Java source formatting/parser check: passed for all 20 Java files.
- Shell launcher/wrapper syntax checks: passed.
- Python launcher syntax check and Maven XML parse: passed.
- Maven Wrapper 3.3.4 launching Maven 3.9.11 on installed JDK 25.0.1: passed.
- Maven `test-compile` against an offline cache of official dependencies: **BUILD SUCCESS**, 18 application classes and 2 test/helper classes, Java 21 target.
- Runtime PostgreSQL/security integration attempt: **blocked before test bodies ran**. PostgreSQL `initdb` failed with `FATAL: could not create shared memory segment: Operation not permitted` (`shmget`). Surefire reported 16 errors (15 cases plus class cleanup), zero passed; this is not a passing suite.
- The initial helper selected x86 binaries on Apple Silicon. A macOS/aarch64 Maven profile now adds the matching native ARM64 PostgreSQL 14.15.0 binary. Source compilation is rechecked after this portability adjustment; shared-memory restrictions remain an independent blocker.
- Database migrations, actual login/permissions, restart persistence and browser rendering remain unverified until PostgreSQL can start in a normal local environment.
- Git initialization: denied by managed workspace permissions.

Initial normal Maven downloads failed because Java network connections were denied by this environment. Dependencies were downloaded through the supported transfer route and compilation performed offline. These temporary cache tools are not application dependencies or required project files.

Native Apple Silicon follow-up: matching ARM64 PostgreSQL binaries were selected and source compilation remained successful. A one-case migration probe again failed during `initdb` with the same shared-memory denial (2 errors including class cleanup). This confirms the portability adjustment does not remove the environment restriction. No application assertions ran.

## Codex runtime follow-up — 15 November 2026

Elevated execution resolves the sandbox socket/shared-memory restriction. `./mvnw -o test -Dtest=FoundationIntegrationTest` passed **15 tests, zero failures/errors** against real PostgreSQL after correcting incident column mappings, dashboard model attributes and priority option values. Both V1 and V2 migrations executed. This result applies to that snapshot; shared files continued changing afterward.

The application started on 127.0.0.1:8080 against persistent `.local/postgres-data`. Actual HTTP registration and login reached the authenticated employee dashboard (200). A restart attempt then failed Flyway checksum validation for an applied migration changed during concurrent local work. No repair/reset was performed. Restart persistence and visual browser inspection remain unverified. Do not mark Phase 3 complete yet.

Evidence: `target/surefire-reports/edu.bits.itsm.FoundationIntegrationTest.txt`; temporary session logs `/private/tmp/csf213-runtime-elevated.log`, `/private/tmp/csf213-app.log`, `/private/tmp/csf213-app-restart.log`. These local logs/build outputs are not committed. The development database process is left running for follow-up; the application restart failed.

## Migration mismatch resolved — 15 November 2026

V2 was restored byte-for-byte to its committed contents. The added `demo_seed_markers` table now belongs to `V3__demo_seed_markers.sql`. FoundationIntegrationTest passed again: **15 tests, zero failures/errors** on a fresh isolated PostgreSQL database. The existing persistent database validated all three migrations and applied only V3. A subsequent application restart validated all three and reported no pending migration. The account registered before the mismatch authenticated successfully after both startups and reached the employee dashboard (HTTP 200). No checksum repair or database reset was performed. Visual browser inspection remains outstanding.

Evidence: `/private/tmp/csf213-migration-tests.log`, `/private/tmp/csf213-migration-startup.log`, `/private/tmp/csf213-migration-restart.log`. Application remains available on 127.0.0.1:8080 with the development database on 127.0.0.1:54329. Application-restart persistence has been checked; this does not claim a full database-process restart test.

## Existing account, database restart and visual check — 15 November 2026

Read-only JDBC inventory found only active employee ID 1 (the runtime verification account). Documented demo accounts are absent from this local database, and there is no local `.env`. Existing credentials were used without changing passwords or creating accounts.

Browser sign-in reached the employee dashboard. Desktop screenshot inspection showed readable sidebar navigation, filter controls and metric cards with no visible overlap in the inspected viewport. This is a limited desktop visual check, not full-page, responsive or all-role visual acceptance. No screenshot file was saved before interruption.

Stopped and restarted the app and PostgreSQL with the same `.local/postgres-data`; Flyway validated V1–V3. The browser returned to login after restart (expected for session loss). Existing account authentication was then confirmed over HTTP after both process restarts, reaching the employee dashboard with HTTP 200. Browser tools became unavailable after interruption, so further browser checks remain pending. Logs: `/private/tmp/csf213-db-restart.log`, `/private/tmp/csf213-full-restart.log`, `/private/tmp/csf213-db-resume.log`, `/private/tmp/csf213-app-resume.log`. No account passwords or hashes are included in these docs.

## Phase 4 Step 1 — catalog administration, 15 November 2026

Command: `MAVEN_USER_HOME="$PWD/.local/maven-home" ./mvnw -o test -Dtest=CatalogIntegrationTest,FoundationIntegrationTest` (elevated local process/socket access). Result: **21 tests passed**, zero failures/errors/skips: 6 catalog integration cases and 15 foundation regression cases. Real PostgreSQL and Flyway V1–V3 are used; test writes roll back.

Catalog coverage: admin page render and service form submission; trimmed names; add/edit/deactivate/reactivate with real IDs in audit records; category deactivation retains the record and rejects new selection; employee/technician HTTP denial; direct non-admin/inactive-admin service denial; CSRF rejection; case-insensitive duplicate category rejected with a readable error; invalid service names/criticality and missing service/category IDs do not write success audits. Admin desktop visual inspection and later lifecycle acceptance remain separate outstanding checks. Repeat-report correlation is planned scope, not implemented/tested here.

Local run log: `/private/tmp/csf213-catalog-tests.log`. Surefire reports: `target/surefire-reports`. Results describe the tested snapshot; other local workflow changes remain independently in progress.

## Phase 4 Steps 2–4 — final verification, 15 November 2026

Command: `MAVEN_USER_HOME="$PWD/.local/maven-home" ./mvnw -o test` with elevated local process/socket access. **114 passed, zero failures/errors/skips**: FoundationIntegrationTest 15, CatalogIntegrationTest 6, WorkflowIntegrationTest 19, WorkflowViewsTest 18, WorkflowRulesTest 56. Tests use real isolated PostgreSQL for database/HTTP cases; template fixtures verify rendering but do not establish visual layout quality. No live AI call was made.

Verified reporting and access: employee form submission redirects to persisted details; invalid/malformed factors retain title/description with readable validation errors; inactive services and missing CSRF reject creation; privilege/priority/state/owner injection is ignored; unrelated employee HTML/API reads are forbidden and lists are scoped. Direct deactivated-account access and reporting are rejected.

Verified priority: every valid impact/urgency/criticality combination has an explicit expected P1–P4 result, all factor bounds are rejected, service criticality changes preserve historical snapshots, and administrator corrections require a reason and audit old/new input values. Affected-user count remains context rather than a multiplier.

Verified lifecycle: all 64 state pairs checked against approved edges, credential-based logins for employee/technician/admin reach their role dashboards, assigned technician acknowledgment/start/resolution and reporter verification/closure complete through HTTP with ordered audit events. Wrong-role actions, unassigned technician writes, invalid shortcuts, stale edits and reopening CLOSED are rejected. Later conversation, rejection, feedback and concurrency acceptance still require their own fuller review.

Live check using existing local employee: created labeled incident INC-1 via HTTP (Wi-Fi/Network, 2 × 2 × 3 = 12/P2). No eligible technician was present so it remained CLASSIFIED. Stopped and restarted both app and PostgreSQL with the same data directory; authenticated employee reopened it with the same title, owner, score and classification. All three migrations validated, no repair/reset. The local account/ticket remain available for follow-up.

Evidence: `/private/tmp/csf213-phase4-final.log`, `/private/tmp/csf213-phase4-live.log`, `/private/tmp/csf213-phase4-db-restart.log`, `/private/tmp/csf213-phase4-restart.log`; Surefire XML/text in `target/surefire-reports`. Local secrets, database files and build/log outputs are excluded from Git. Existing later-phase helper sources are included with the runnable snapshot; these results do not mark Phases 5–6 fully accepted.

## Phase 4 Step 5 — conversation and investigation, 15 November 2026

Final command: `MAVEN_USER_HOME="$PWD/.local/maven-home" ./mvnw -o test` (elevated process/socket access). **121 passed, zero failures/errors/skips**: foundation 15, catalog 6, workflow PostgreSQL/HTTP 26, template rendering 18, rules 56. Seven added integration cases cover:

- Internal notes visible only to assigned technician/admin; reporter HTML and authorized history reads omit their bodies. Internal notes create no notification and their body is not copied into audit descriptions.
- Information request/public reply links are preserved and reply counts update. Reply target must be a REQUEST on the same incident; other incidents, internal notes, general messages and missing IDs are rejected.
- Information requests/replies retain status, cycle and both SLA deadlines. Public/request notifications are recorded; no SLA pause or new lifecycle state is introduced.
- Every message increments incident version even at the same fixed instant. A stale HTTP draft receives 409, remains visible, and creates no additional message.
- Wrong-role note/request actions, unrelated reporters, unassigned technicians and missing CSRF are rejected. Blank/oversized messages, unknown kinds and malformed reply IDs are rejected without success history; valid draft text is retained on errors.
- Resolved/closed incidents reject further investigation messages with a readable error.

V4 adds a nonnegative conversation revision used to make each message a real incident mutation for optimistic locking. Earlier migrations are untouched. Runtime upgrade validated V1–V4 and applied V4 once to the existing persistent database. A labeled public reply on INC-1 was submitted through actual employee HTTP login; its version increased while status/deadlines stayed unchanged. Both application and PostgreSQL were restarted; existing login, saved reply, version, status and deadlines were verified again. No data reset or checksum repair.

Evidence: `/private/tmp/csf213-step5-final.log`, `/private/tmp/csf213-step5-live.log`, `/private/tmp/csf213-step5-db-restart.log`, `/private/tmp/csf213-step5-restart.log`; Surefire results in `target/surefire-reports`. No browser visual inspection was performed for these new conversation controls; view rendering tests do not replace it. The local test reply remains for follow-up. Step 6 resolution/rejection/feedback and later concurrency/history acceptance remain separate.

## Phase 4 Step 6 — resolution, rejection, closure and feedback, 16 November 2026

Full command: `MAVEN_USER_HOME="$PWD/.local/maven-home" ./mvnw -o test` with elevated local process/socket access. **131 tests passed, zero failures/errors/skips**: foundation 15, catalog 6, workflow PostgreSQL/HTTP 36, view rendering 18, rules 56. Targeted workflow/rendering run also passed. No schema migration or live AI call was needed.

Ten additional workflow integration cases verify:

- Nonempty resolution details, technician identity and cycle completion persist; reporter can verify then close in separate steps. Feedback is optional; closing without it works. Repeated closure is rejected.
- Blank/oversized resolutions and blank/oversized rejection reasons fail without writing success records or moving the lifecycle. Stale resolution submissions retain the entered draft and return 409.
- A rejected late first fix retains its resolution record, completed cycle deadline/time, first acknowledgment/resolution timestamps and recorded SLA breach. A second cycle uses the incident’s stored target despite catalog policy changes; re-resolution creates a second record and leaves the earlier breach intact.
- Rejection without an eligible technician leaves CLASSIFIED/unassigned with both cycle records and previous resolution retained.
- Administrator verification/closure requires a reason and logs the administrator identity and explicit override text in both verification and closure audit entries. Technicians/unrelated employees cannot verify, close or reject on behalf of the reporter; administrators cannot reject as the reporter.
- Feedback is reporter-only and requires resolution. Out-of-range, empty/non-numeric ratings, excessive comments and missing CSRF are rejected. Both rating boundaries (1, 5) persist with reporter identity; comments are trimmed and escaped in HTML. Duplicate submission cannot overwrite earlier feedback or add another success audit.

Action and feedback forms now retain invalid input and expose readable validation errors on incident details. Resolution/rejection audit context includes the relevant cycle; administrator override reasons are explicit. Latest app startup uses the same persistent development database; no data reset or schema repair occurred. End-to-end persistence of resolved/rejected cycles across process restarts and visual browser inspection are still tracked under later acceptance tasks; these tests do not claim those checks were performed.

Evidence: `/private/tmp/csf213-step6-targeted.log`, `/private/tmp/csf213-step6-final.log`, `/private/tmp/csf213-step6-live.log`, plus Surefire XML/text in `target/surefire-reports` (local outputs excluded from Git).
