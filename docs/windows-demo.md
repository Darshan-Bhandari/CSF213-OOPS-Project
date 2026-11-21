# Windows setup and demonstration

Use the latest source ZIP (extract it first), or the review branch listed in `plan.md`. Run commands from the directory containing `pom.xml` and `mvnw.cmd`. Install a full JDK 21 or 25 and Python 3 with the Windows Python launcher (`py`). Internet is needed on the first Maven run. These instructions and wrapper selection have been prepared; native Windows execution still requires verification on the teammate's machine.

## 1. Check prerequisites in PowerShell

```powershell
java -version
py -3 --version
.\mvnw.cmd -version
```

## 2. Start a local database in terminal 1

For a fresh demonstration without a system PostgreSQL installation:

```powershell
.\mvnw.cmd test-compile exec:java "-Dexec.mainClass=edu.bits.itsm.DevelopmentDatabase" "-Dexec.classpathScope=test"
```

Keep terminal 1 open. It starts PostgreSQL on `127.0.0.1:54329` with database/user `postgres`, an empty password and persistent data under `.local/postgres-data`. This helper is for local development. If using your own PostgreSQL server, keep its actual connection details instead of the helper settings below.

## 3. Configure `.env` in terminal 2

Create the file only if missing, preserving any existing credentials:

```powershell
if (!(Test-Path .env)) { Copy-Item .env.example .env }
notepad .env
```

For the portable database, fill these entries:

```dotenv
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:54329/postgres
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=
DEMO_PASSWORD=YOUR_CHOSEN_DEMO_PASSWORD
OPENROUTER_API_KEY=
OPENROUTER_MODEL=
```

Replace the demo-password placeholder with your chosen 8–64 character password (at most 72 UTF-8 bytes). All three seeded demo accounts initially use it. Existing accounts/passwords are retained across restarts; changing this setting does not reset them.

Keep AI entries empty until you have chosen a model and budget. To enable AI, add your actual OpenRouter key and exact model ID locally, then restart the app. The key must never be put in source files, committed or displayed during recording. Exported environment variables take precedence over `.env`.

## 4. Start the app

```powershell
.\scripts\run-local.cmd "-Dspring-boot.run.profiles=demo"
```

The Windows launcher loads `.env` and selects `mvnw.cmd`. Open `http://127.0.0.1:8080`. Use separate browser profiles/private sessions for `employee@campus.test`, `technician@campus.test` and `admin@campus.test`, all with the chosen demo password.

For the clearly labelled accelerated SLA demonstration only:

```powershell
.\scripts\run-local.cmd "-Dspring-boot.run.profiles=demo,sla-demo"
```

Stop the previous app first with Ctrl+C; keep the database running. This profile adds the training service with 10/30-second P1 targets and a one-second monitor interval. Ordinary service policies are retained.

## 5. Verify the extracted project

```powershell
.\mvnw.cmd clean verify
py -3 -m unittest discover -s src/test/python -v
```

Record the actual result. Java integration checks create isolated databases; they do not reset your development database. The Python process tests currently use a POSIX fake executable and were verified on macOS; they are not a native Windows acceptance suite. The Windows application/demo and Java checks must still be exercised on Windows. See `docs/phase-7-checklist.md` for the three demonstrations and evidence to record.

Ctrl+C stops the application; Ctrl+C in the database terminal stops the helper. Retain `.local/postgres-data` if you want the same saved incidents next time.
