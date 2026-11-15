# Campus IT Service Management

Java / Spring Boot / Thymeleaf / HTML / CSS / JavaScript / PostgreSQL. AI integration is planned through OpenRouter, with a user-supplied key and model.

The project is being built in phases. **Current implementation: Phase 3 foundation.** Incident reporting, assignment, incident lifecycle, SLA monitoring, notifications, AI suggestions and incident analytics are subsequent phases. The current dashboard displays actual catalog configuration counts and explicitly labels the foundation state.

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
- Missing OpenRouter configuration does not prevent startup. No AI provider calls are implemented in Phase 3.

## Tests and packaging

```sh
./mvnw test
./mvnw package
```

Tests start an isolated PostgreSQL process on a random port, run real migrations, and roll back each test's data. No demo passwords, real user data or AI keys are required. Test results appear in `target/surefire-reports`. See `docs/testing.md` for cases and actual execution status.

Package output: `target/campus-itsm-0.1.0-SNAPSHOT.jar`. Running the jar directly uses exported environment variables; `.env` is loaded only by the provided launcher.

## OpenRouter configuration — later

At the AI phase, set `OPENROUTER_API_KEY` and `OPENROUTER_MODEL` in `.env`. No model is chosen and no API spending is authorized implicitly. Backend integration, validation and live testing remain tracked in `plan.md`. Never commit a key. Enabling these values alone does not implement AI features.

## Version control

The project includes `.gitignore`; remote repository choice is deferred. This workspace currently blocks creating `.git`. In a normal terminal you can initialize it with `git init` when ready. Do not commit `.env`, `.local`, database files or build output.

## Current verification limitation

The managed workspace has blocked Java network connections, preventing Maven's normal dependency resolution, and blocks Git metadata creation. Application and test sources compiled successfully using an offline cache of officially downloaded dependencies. The integration attempt could not initialize PostgreSQL because shared-memory allocation was denied; no integration cases passed. Database/browser behavior remains unverified; see `docs/testing.md` and `plan.md` for the final recorded status.

## Third-party wrapper

`mvnw` and `mvnw.cmd` come from Apache Maven Wrapper 3.3.4 (Apache License 2.0). The template version marker is replaced with the pinned release. Source: https://github.com/apache/maven-wrapper/tree/maven-wrapper-3.3.4. PostgreSQL test tooling: https://github.com/zonkyio/embedded-postgres.
