# Campus IT Service Management

Java / Spring Boot / Thymeleaf / HTML / CSS / JavaScript / PostgreSQL. OpenRouter integration is implemented with user-supplied local key/model configuration and remains optional for startup.

**Phases 1–5 are accepted for the documented automated/backend and packaged HTTP scope.** Core AI category/resolution suggestions and reporting pass controlled-provider acceptance. The latest clean verification passed **198 regression tests + 2 packaged restart cases**, and **4 launcher tests** passed. See [Phase 5–6 evidence and demo steps](docs/phase-5-6-acceptance.md), [architecture](docs/architecture.md), [test history](docs/testing.md), and [current plan](plan.md).

Live AI verification awaits the user's model/key/budget; approved repeated-report correlation and final stabilization/submission work remain open. Earlier Chromium desktop/mobile acceptance covers Phases 1–4; the extended browser run is blocked in the current workspace before navigation. No live provider request or public deployment is claimed. The old `artifacts/campus-itsm-phase3.zip` is historical; build current source for the latest app.

## Requirements

- Full JDK 21 or 25 (including compiler and release metadata). Code targets Java 21; both JDK versions have been exercised in the recorded environments.
- Maven 3.9.11 is downloaded by the included Apache Maven Wrapper on first use.
- Python 3 for the optional `.env` launcher.
- PostgreSQL. Use an existing local database or the optional portable development database below.
- Internet for initial Maven dependencies; OpenRouter is not required to start the application.

## Start locally

Run commands from this project directory. On macOS/Linux:

```sh
export MAVEN_USER_HOME="$PWD/.local/maven-home"
cp .env.example .env
```

Edit `.env` with database details. The app binds to `127.0.0.1:8080` by default. The `.env` file is excluded from source control. The launcher parses it as literal configuration; it does not execute shell commands or expand variables inside values. Existing exported environment variables take precedence.

### Option A: existing PostgreSQL

Create a database and application user using your local PostgreSQL administration tool, then set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD` in `.env`. Flyway creates the schema and seed service/category/SLA records automatically.

### Option B: portable PostgreSQL for development

This uses a real PostgreSQL process supplied through a **test-scoped** dependency. No global PostgreSQL installation or Docker is needed. The first run downloads platform binaries; the macOS/aarch64 profile includes native Apple Silicon binaries. Native execution must be permitted by your terminal/environment.

In terminal 1:

```sh
./mvnw test-compile exec:java -Dexec.mainClass=edu.bits.itsm.DevelopmentDatabase -Dexec.classpathScope=test
```

Keep that terminal open. Persistent data lives in `.local/postgres-data`. It listens only on `127.0.0.1:54329` with loopback trust authentication; it is for local development, not public deployment. Configure `.env`:

```dotenv
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:54329/postgres
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=
```

### Start the application and seed demo accounts

Choose `DEMO_PASSWORD` in `.env` (8–64 characters, at most 72 UTF-8 bytes). In terminal 2:

```sh
./scripts/run-local.sh -Dspring-boot.run.profiles=demo
```

Open **http://localhost:8080**. Demo accounts all use the password you supplied:

| Account | Role |
|---|---|
| `employee@campus.test` | Campus Community |
| `technician@campus.test` | Technician |
| `admin@campus.test` | Administrator |

Campus Community is the complaint-reporting role, including students and other campus members. The demo email `employee@campus.test` remains valid; its historical name and internal role/URLs are retained for compatibility. Admin and Technician responsibilities are unchanged.

Seeding is idempotent: existing accounts and passwords are retained on restart. Changing `DEMO_PASSWORD` does not reset an existing account. No privileged accounts are seeded unless the `demo` profile is explicitly enabled. Run `./scripts/run-local.sh` without the profile to disable demo provisioning.

## What works in the foundation

- Public Campus Community registration, normalized unique email and hashed passwords.
- Login/logout with server-side sessions and CSRF protection.
- Backend role restrictions and role-specific dashboard routing.
- Administrator directory, account creation and activation/deactivation; technician account creation includes a technician profile.
- Deactivated accounts lose existing session access on their next request.
- Account changes produce audit records in the same transaction.
- PostgreSQL schema managed by Flyway; Hibernate validates rather than silently changing it.
- Persistent catalog: five campus services, six categories and four default SLA policies.
- Missing OpenRouter configuration does not prevent startup. AI adapter source is present for later acceptance; no live provider request was made or verified.

## Tests and packaging

```sh
./mvnw test       # regression suite
./mvnw clean verify  # clean JAR build + real packaged HTTP/app/database restart acceptance
python3 -m unittest discover -s src/test/python -v
```

Tests start isolated PostgreSQL processes on random ports and run real migrations. Most fixtures roll back; concurrency/atomicity cases deliberately commit and reset only their test-owned database. Embedded PostgreSQL uses TCP without Unix sockets. No demo passwords, real user data or AI keys are required. Regression results appear in `target/surefire-reports`; packaged restart results appear in `target/failsafe-reports`. See `docs/testing.md` for cases and actual execution status.

Package output: `target/campus-itsm-0.1.0-SNAPSHOT.jar`. Running the jar directly uses exported environment variables; `.env` is loaded only by the provided launcher.

## OpenRouter configuration — later

At the AI phase, set `OPENROUTER_API_KEY` and `OPENROUTER_MODEL` in `.env`. No model is chosen and no API spending is authorized implicitly. Adapter source, validation and remaining provider verification are tracked in `plan.md`. Never commit a key. Configuring a key/model enables provider access; it does not establish that live AI behavior has been verified.

## Version control

Repository: https://github.com/Darshan-Bhandari/CSF213-OOPS-Project, branch `main`. Initial project upload is complete. Check `plan.md` for uncommitted work and current verification status. Do not commit `.env`, `.local`, database files or build output.

## Current verification status

On 17 November 2026, Linux/JDK 21.0.13/PostgreSQL 14.15 passed 198 regression tests and two real packaged HTTP/app/database restart cases, including startup SLA catch-up and durable deduplication. The strengthened restart suite was recompiled and rerun successfully. Four launcher tests passed. Surefire loads Mockito as a startup agent, avoiding dynamic attach requirements. V1–V4 migrations are unchanged.

Desktop/mobile Chromium acceptance from 16 November covers Phases 1–4. The extended Phase 5 browser runner below is syntax-checked but blocked before navigation in the current environment by Chromium's Unix-socket startup restriction; compatible headless-shell downloads were unavailable. It must still run on a browser-capable environment. Windows, other browsers, production load/security and final submission remain separate gates. See `docs/testing.md` for historical failures and actual results.

## Optional browser acceptance

Requires Node.js 20+ and Playwright (tested 1.62.1), only for browser checks. The application itself has no Node/frontend build dependency. Start an **isolated disposable** database/demo instance on loopback ports different from your normal app:

```sh
./mvnw test-compile exec:java -Dexec.mainClass=edu.bits.itsm.DevelopmentDatabase -Dexec.classpathScope=test -Ddev.db.port=54330 -Ddev.db.directory=.local/browser-test-data
```

Run the app in another terminal using that database, `SERVER_PORT=8081`, demo profile and a password you choose. Provide the same password in `DEMO_PASSWORD` to the browser runner; it creates/updates labeled verification tickets in that disposable instance. The database helper defaults remain 54329 and `.local/postgres-data` if overrides are absent.

With Playwright installed locally, the runner is:

```sh
BROWSER_BASE_URL=http://127.0.0.1:8081 node scripts/browser_acceptance.cjs
```

If Playwright is not installed, an optional project-local installation is:

```sh
npm install --prefix .local/browser-tools playwright@1.62.1
PLAYWRIGHT_BROWSERS_PATH="$PWD/.local/playwright-browsers" node .local/browser-tools/node_modules/playwright/cli.js install chromium --only-shell
NODE_PATH="$PWD/.local/browser-tools/node_modules" PLAYWRIGHT_BROWSERS_PATH="$PWD/.local/playwright-browsers" BROWSER_BASE_URL=http://127.0.0.1:8081 node scripts/browser_acceptance.cjs
```

`DEMO_PASSWORD` must already be exported for these runner commands. Alternatively set `DEMO_CREDENTIALS_FILE` to an ignored local JSON file with a `password` field. Passwords are not printed. Screenshots and results default to ignored `.local/acceptance-screenshots/`. The runner accepts only localhost targets. For the extended Phase 5 scenario, start with profiles `demo,sla-demo` and also set `BROWSER_AUTOMATION=true`. It exercises the labelled 10/30-second SLA service, strategy selection, disabled AI buttons, notifications, simulated email and combined dashboard filters. `BROWSER_EXECUTABLE` optionally selects an installed Chromium executable. This extension has not passed browser execution in the current restricted workspace; see the acceptance records for tested scope and deferred checks.

## Windows and final demonstration checks

See [Windows setup](docs/windows-demo.md) for PowerShell commands and the `.env`-loading `scripts/run-local.cmd` launcher. [Phase 7 checklist](docs/phase-7-checklist.md) contains the three repeatable demonstrations and remaining verification gates. Windows wrapper selection and UTF-8 BOM handling are implemented; native Windows execution remains unverified.

## Third-party wrapper

`mvnw` and `mvnw.cmd` come from Apache Maven Wrapper 3.3.4 (Apache License 2.0). The template version marker is replaced with the pinned release. Source: https://github.com/apache/maven-wrapper/tree/maven-wrapper-3.3.4. PostgreSQL test tooling: https://github.com/zonkyio/embedded-postgres.
