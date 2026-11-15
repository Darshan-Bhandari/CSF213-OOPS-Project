# Campus IT Service Management

Java / Spring Boot / Thymeleaf / HTML / CSS / JavaScript / PostgreSQL. AI integration is planned through OpenRouter, with a user-supplied key and model.

The project is being built in phases. **Current verified progress: foundation plus Phase 4 Steps 1–5 backend/form checks.** Catalog administration, incident reporting/detail access, calculated priority, guarded lifecycle and private/public investigation conversation are implemented. Supporting assignment/SLA/notification/reporting and AI source is included; remaining acceptance is tracked in `plan.md`. The latest automated suite passed 121 cases. Visual browser acceptance remains partial; no live AI provider call is verified.

## Requirements

- JDK 21 or 25. Code targets Java 21; Spring Boot 3.5.16 supports the installed JDK 25.
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
| `employee@campus.test` | Employee |
| `technician@campus.test` | Technician |
| `admin@campus.test` | Administrator |

Seeding is idempotent: existing accounts and passwords are retained on restart. Changing `DEMO_PASSWORD` does not reset an existing account. No privileged accounts are seeded unless the `demo` profile is explicitly enabled. Run `./scripts/run-local.sh` without the profile to disable demo provisioning.

## What works in the foundation

- Public employee registration, normalized unique email and hashed passwords.
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
./mvnw test
./mvnw package
```

Tests start an isolated PostgreSQL process on a random port, run real migrations, and roll back each test's data. No demo passwords, real user data or AI keys are required. Test results appear in `target/surefire-reports`. See `docs/testing.md` for cases and actual execution status.

Package output: `target/campus-itsm-0.1.0-SNAPSHOT.jar`. Running the jar directly uses exported environment variables; `.env` is loaded only by the provided launcher.

## OpenRouter configuration — later

At the AI phase, set `OPENROUTER_API_KEY` and `OPENROUTER_MODEL` in `.env`. No model is chosen and no API spending is authorized implicitly. Adapter source, validation and remaining provider verification are tracked in `plan.md`. Never commit a key. Configuring a key/model enables provider access; it does not establish that live AI behavior has been verified.

## Version control

Repository: https://github.com/Darshan-Bhandari/CSF213-OOPS-Project, branch `main`. Initial project upload is complete. Check `plan.md` for uncommitted work and current verification status. Do not commit `.env`, `.local`, database files or build output.

## Current verification status

Elevated execution permits local PostgreSQL and sockets in this managed workspace. The full suite passed **121 tests, zero failures/errors/skips**; fresh databases and persistent V1–V4 migration validation pass. A labeled local incident was reported through HTTP and retained its details, owner and priority after both app and database restarts. The employee desktop dashboard was visually inspected earlier; full-page/responsive and admin/technician visual checks remain pending. See `docs/testing.md` and `plan.md` for evidence and exact limits. Historical build/network failures do not describe the latest successful run.

## Third-party wrapper

`mvnw` and `mvnw.cmd` come from Apache Maven Wrapper 3.3.4 (Apache License 2.0). The template version marker is replaced with the pinned release. Source: https://github.com/apache/maven-wrapper/tree/maven-wrapper-3.3.4. PostgreSQL test tooling: https://github.com/zonkyio/embedded-postgres.
