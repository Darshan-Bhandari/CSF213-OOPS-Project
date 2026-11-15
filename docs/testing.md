# Foundation verification

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
