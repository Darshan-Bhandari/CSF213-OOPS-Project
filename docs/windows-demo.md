# Run BITSOps Assistant on Windows

Run the PowerShell commands from the project root containing `pom.xml` and `mvnw.cmd`. Install a full JDK 21 or 25 and Python 3 with the Windows Python launcher (`py`). The first Maven run needs internet access.

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

Leave the OpenRouter settings blank for normal evaluation. Exported environment values take precedence over `.env`. Keep your configuration local.

## 4. Start the app

```powershell
.\scripts\run-local.cmd "-Dspring-boot.run.profiles=demo"
```

The Windows launcher loads `.env` and selects `mvnw.cmd`. Open `http://127.0.0.1:8080`. Use separate browser profiles/private sessions for `employee@campus.test`, `technician@campus.test` and `admin@campus.test`, all with the chosen demo password.

## 5. Verify the extracted project

```powershell
.\mvnw.cmd clean verify
```

Java integration checks create isolated databases. Native Windows execution remains unverified. See the [test guide](testing.md) for test coverage and the [application walkthrough](phase-7-checklist.md) for example workflows.

Ctrl+C stops the application; Ctrl+C in the database terminal stops the helper. Retain `.local/postgres-data` if you want the same saved incidents next time.
