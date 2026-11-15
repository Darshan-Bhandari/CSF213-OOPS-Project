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
