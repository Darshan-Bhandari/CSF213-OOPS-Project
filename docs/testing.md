# Verification record

Latest acceptance (17 November 2026): **198 regression + 2 packaged restart cases passed**, zero failures/errors/skips, plus **4 launcher tests**. Phase 5 automated/backend/packaged scope is accepted; Phase 6 core AI/reporting is verified with controlled fixtures. Live AI, repeated-report correlation and final stabilization/submission remain open. The extended browser attempt was blocked before navigation; earlier desktop/mobile acceptance applies to Phases 1–4. See the final section for exact current evidence. Earlier failures below are historical.

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

## Phase 4 Step 7 — complete history and transaction consistency, 16 November 2026

Final command: `MAVEN_USER_HOME="$PWD/.local/maven-home" ./mvnw -o test` with elevated process/socket access. **141 passed, zero failures/errors/skips**: previous 131 cases plus 10 in `AuditConsistencyIntegrationTest`. Unlike earlier rollback-scoped fixture tests, the new class has no enclosing test transaction: application service calls actually commit or roll back, and subsequent reads use independent transactions. It owns a separate embedded PostgreSQL database. Its fixture cleanup/failure constraints never target `.local/postgres-data`.

Verified failure cases: audit writes without a business transaction are rejected; classification audit failure rolls back the inserted incident and initial history; message audit failure removes the inserted message/revision; resolution insert failure restores lifecycle/history; failure during the final CLOSED flush rolls back both verification/closure audits and already-written notifications; reassignment audit failure after rejection restores the prior resolution and removes the new cycle; feedback audit failure removes the feedback row. Snapshot assertions cover versions, assignment, state, cycle, conversation revision, deadlines and related-row counts. Temporary test-only constraints are dropped in finally blocks. PostgreSQL sequence gaps after rollback are valid and not treated as corruption.

Concurrency: two actual threads submit messages against the same incident version and fixed clock. Exactly one succeeds, the other receives ConflictException; only one message, audit event, version/revision increase and set of notifications commits.

History reconstruction: ordered audit IDs chain from CREATED through classification/assignment, messages, reasoned reassignment/correction, system SLA breach events, resolution/rejection/reassignment, second resolution, verification, closure and feedback. Incident ID/subject, actor/System identity, timestamps and descriptions are present; resulting state matches the persisted incident. Staff history is complete; reporter history filters internal-message and generated-AI-suggestion events. An explicit stored AI fixture checks access only; no provider was called and AI acceptance is not implied.

History service reads now require an authorized current actor for audit, messages, resolutions, SLA events, feedback and staff suggestions. Global audit and simulated-email reads require an active administrator. New audit entries store lifecycle states in previous/new-state fields; priority changes are described separately. Historical audit rows are not rewritten, so older priority-correction rows may retain their previous format. No schema migration/reset/repair was needed. Application audit reads/writes are protected; this does not claim tamper resistance against someone with direct database write access.

Evidence: `/private/tmp/csf213-step7-targeted.log`, `/private/tmp/csf213-step7-final.log`, `/private/tmp/csf213-step7-live.log` and Surefire XML/text. Step 8 full lifecycle restart and visual acceptance remain pending.

## Step 8 — Phases 1–4 acceptance, 16 November 2026

See `phase-1-4-acceptance.md` for the complete review and `architecture.md` for implementation contracts. The 17-page course source was re-extracted and cross-checked against the requirements register. Final report/UML/ER/team/video/submission work remains deferred to later phases.

Final `./mvnw clean verify` at 02:04 IST: **143 Surefire cases + 1 Failsafe packaged integration case passed**. Breakdown: Foundation 16, Catalog 7, Workflow 36, Views 18, Rules 56, AuditConsistency 10; `WorkflowRestartIT` 1. Four launcher tests also passed. Real packaged JAR/HTTP demonstrated demo login for every role, complete lifecycle including two resolution cycles and feedback, privacy/CSRF/deactivation/logout checks, stop/restart of Java and PostgreSQL and equality of all relevant records. Demo seed idempotence and unchanged existing passwords were verified even with a new supplied seed password. Packaging output is `target/campus-itsm-0.1.0-SNAPSHOT.jar`; reports are `target/surefire-reports` and `target/failsafe-reports`.

Real HTTP caught a forbidden POST forwarding defect (405 instead of 403); fixed the GET-only denial handler and reran acceptance successfully. Additional review tightened null registration validation and active-admin checks for assignment configuration. Mobile sign-out was restored, active navigation now tracks the current route and prior-null audit state no longer leaks literal null into the UI. Regression cases cover the validation/authorization changes.

Browser runner `scripts/browser_acceptance.cjs` passed on a separate fresh localhost demo instance with Chromium at 1440×1000 and 390×844. It exercises all three role logins, admin pages, report/assignment/acknowledgment/investigation, private/public messages and linked reply, unchanged conversation deadlines, rejection/re-resolution, closure, feedback and all three mobile sign-outs. Active navigation and no page-level horizontal overflow are asserted. No page JS exceptions or server 5xx occurred. Full-page screenshots were reviewed for representative desktop dashboards/configuration/investigation and mobile dashboards/configuration/closed details. Screenshots/results are ignored local artifacts in `.local/acceptance-screenshots/`; an additional `admin-preview.png` is the concise visual handoff. This does not certify Firefox/Safari, every screen width, accessibility, load/performance or production security.

The browser/demo runtime was isolated on 8081/54330 with `.local/visual-final-postgres-data` and was stopped after verification; original app 8080 remains running. Generated credentials remain only in ignored local configuration. Original development records were preserved. V1–V4 were not edited, no checksum repair occurred, and the database helper's optional port/directory overrides were exercised. Later-phase AI/SLA/reporting acceptance remains open, with no live provider spending.

Evidence: `/private/tmp/csf213-step8-final.log`, `/private/tmp/csf213-step8-browser-final.log`, `/private/tmp/csf213-visual-final-app.log`, `.local/acceptance-screenshots/results.json`. The earlier first packaged attempt failed on the denial handler and is superseded by the passing final run. Browser harness selector corrections and the launcher's macOS symlink-aware test assertion did not change application behavior.


## Phases 5–6 and regression stabilization — 17 November 2026

Final clean command in this Linux workspace: `JAVA_HOME="$PWD/.local/jdk" ./mvnw -s .local/maven-settings.xml -B clean verify`. **BUILD SUCCESS**, 11:35:45 IST: **198 Surefire cases + 2 Failsafe packaged cases**, zero failures/errors/skips. `.local/jdk` is a full Temurin JDK 21.0.13; `.local/maven-settings.xml` contains this workspace's inherited proxy settings only and is not a project prerequisite. Its trusted CA configuration was retained; TLS verification was not disabled. Normal machines use `./mvnw clean verify` (Windows: `.\mvnw.cmd clean verify`).

Breakdown: Foundation 16, Catalog 7, Workflow 36, Views 18, Rules 56, AuditConsistency 10, Automation 23, IntelligenceReporting 20 and OpenRouterAdapter 12. The latter three add **55** regression cases; parameterized invocations are counted by Surefire. The packaged class now contains two cases: full credentialed lifecycle/record persistence and SLA startup catch-up/deduplication. After strengthening the latter with a second overdue sentinel that proves the next startup scan actually completed, `JAVA_HOME="$PWD/.local/jdk" ./mvnw -o -B test-compile failsafe:integration-test failsafe:verify` passed **2/2 again at 11:38:11 IST**. These repeated runs are not added to the distinct case total. Four Python launcher tests also passed; browser-runner JavaScript syntax and `git diff --check` passed.

`docs/phase-5-6-acceptance.md` maps concrete inputs and expected outcomes to test methods and provides repeatable manual demo steps. Controlled-clock and committed PostgreSQL cases verify eligibility/capacity under concurrent reports for both strategies, workload exclusions, stable ties, manual reassignment/fresh acknowledgment, policy overrides/snapshots, missing-policy rollback, warning/breach thresholds/deduplication and both channels' recipients/statuses. Forced notification constraint failures prove creation/monitor writes, audit entries and all deliveries roll back together and retry without loss. Restart acceptance ages only test-owned tickets while the app is stopped; startup listeners catch up with periodic scans delayed one hour. Repeated Java/PostgreSQL restarts retain original events/notifications/audits without duplication.

AI tests use a mocked transport plus actual loopback HTTP fake-provider requests. They exercise success, malformed/fractional/overflow/inactive categories, missing skills, explicit human application, editable drafts, final accepted historical fixes, bounded advice, permission/CSRF checks, escaped model output, stale responses/application, missing configuration, HTTP 401/429/500, invalid/empty/oversized content, stalled body timeout and interruption. No real OpenRouter credentials/model or provider requests were used. Reporting checks cover all eight criteria combined with AND semantics, role scope, inactive-account rejection, Indian-midnight boundaries, 20/1 pagination, SQL-like text and manually known totals/averages/sample counts/eligibility.

Defects corrected: same-priority corrections previously adopted edited catalog targets; non-technician skill writes surfaced a database error; fractional category IDs could be truncated; closed-ticket history included rejected prior fixes; provider timeouts did not cover a stalled response body; empty provider envelopes needed a safe error; reporting service reads lacked the incident service's current-account check. No published migration was modified and no user development database was reset/repaired.

Initial environment attempts failed before meaningful integration assertions: direct Maven DNS resolution required the inherited proxy, the installed Java runtime lacked full JDK release metadata, PostgreSQL attempted forbidden Unix sockets, and Mockito attempted blocked dynamic attachment. A full ignored local JDK/trust configuration, TCP-only embedded PostgreSQL and a Surefire startup Mockito agent resolved those issues. An early automation fixture deleted the P1 policy without restoring it for later tests; fixture restoration was corrected, then all cases passed. These failures are not passing acceptance evidence.

Browser attempt: installed Chromium failed during startup with Unix-socket creation `Operation not permitted`, before page navigation. Compatible Playwright headless-shell downloads returned an unavailable-site HTML page instead of a browser archive. A separate-process manual app attempt also could not reach the database across isolated command networking; this is not recorded as an application defect or a browser pass. The optional extended runner is syntax-checked only, and must be rerun in a browser-capable environment. Previous 16 November desktop/mobile browser evidence is retained separately. No accessibility/cross-browser/performance/production-security certification is claimed.

Local evidence: `/tmp/itsm-verify.log`, `/tmp/itsm-restart-final.log`, `/tmp/itsm-targeted.log`, `/tmp/itsm-browser.log`, `target/surefire-reports`, `target/failsafe-reports`. Logs, dependency caches, test data, credentials and build output are ignored. The source and documentation are tracked together; Phase 6 live-provider verification and correlation plus final Phase 7–8 gates remain open.


## Phase 7 follow-up — local macOS, 17 November 2026

Local workspace was clean on older main `0dde9ee`; 21 tracked source/doc files were synchronized from verified branch commit `2ab4421` without copying configuration or data. Current follow-up changes select `mvnw.cmd` on Windows, add `scripts/run-local.cmd`, and load literal dotenv text with explicit UTF-8/BOM support.

`python3 -m unittest discover -s src/test/python -v` passed **5 cases** on macOS/Python 3.14.2, including BOM, CRLF, Unicode, literal shell-like text, exported-value precedence, invalid configuration and Maven failure exit status. These are POSIX process tests; native Windows execution is not claimed. Windows setup/demo instructions are in `docs/windows-demo.md`.

`MAVEN_USER_HOME="$PWD/.local/maven-home" ./mvnw -o -Dtest=WorkflowRulesTest,WorkflowViewsTest test` passed **74 cases (56 rules + 18 views)**, zero failures/errors/skips, on JDK 25.0.1 at 22:49 IST. Compilation succeeded. The preceding full `./mvnw -o test` attempt failed during embedded PostgreSQL initialization (`shmget: Operation not permitted`) and the loopback fake-provider server's socket bind (`Operation not permitted`); database/provider assertions did not execute. Raw Surefire output was 204 cases, 74 passed, 130 errors including six class cleanup/initialization entries; none of those errors is counted as a pass. No sandbox escalation was attempted. Earlier Linux full-suite/package acceptance remains recorded separately.

Logs: `/tmp/itsm-phase7-regression.log`, `/tmp/itsm-phase7-portable.log`; results in ignored `target/surefire-reports`. The targeted rerun overwrites only its two report files; the other failed full-attempt reports remain, so summing the directory is not a fresh successful full-suite result. The sandbox blocks fresh isolated runtime setup; no browser rerun is claimed. `docs/phase-7-checklist.md` prepares the three full workflows but keeps actual execution unchecked. Full verify/browser/native Windows, live user-configured AI and repeated-report correlation remain open; Phase 7 is IN PROGRESS.
