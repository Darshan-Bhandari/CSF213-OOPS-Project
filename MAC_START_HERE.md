# Start the simplified BITSOps website on a Mac

Update 21 November 2026: Forgot password and recovery email configuration have been removed at the user's request. The account avatar now opens name, email, role and a CSRF-protected Sign out action. No SMTP account or recovery setup is required. Existing passwords and incident data remain unchanged.

## Update an existing installation safely

1. Stop the OLD APP terminal with Control+C. Keep the database terminal running.
2. Extract this complete ZIP into a NEW folder. Do not merge into the old source folder: recovery Java/classes/templates were removed and should not survive as stale files.
3. Copy the old `.env` into the new `campus-itsm` folder. Keep its working datasource and DEMO_PASSWORD. Old RECOVERY_*, SMTP_* and APP_BASE_URL settings may be deleted; the new app ignores them.
4. Keep the existing database. If the portable database helper runs from the old project folder, leave it there: it owns the existing `.local/postgres-data`. Do not start a second helper on the same port, delete the database, or rewrite/repair migrations.
5. Open Terminal, type `cd ` and drag the NEW folder containing pom.xml into Terminal; press Enter. Then:

```bash
ls -l src/main/resources/static/css/app.css
chmod +x mvnw scripts/run-local.sh
./scripts/run-local.sh -Dspring-boot.run.profiles=demo
```

Wait for `Started CampusItsmApplication`, then open http://127.0.0.1:8080/login in Safari/Chrome on that same Mac. Refresh or use a private window if an old stylesheet is cached. The avatar opens account details and Sign out; Escape/clicking outside closes it.

## A fresh installation

Use a full JDK 21 (ARM64 for Apple Silicon), Python 3 and internet for initial downloads. Extract the ZIP to a durable folder such as Downloads. Avoid running from a temporary preview directory. Enter the project folder as above.

```bash
java -version
python3 --version
chmod +x mvnw scripts/run-local.sh
test -f .env || cp .env.example .env
open -e .env
```

For a NEW portable database configure:

```dotenv
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:54329/postgres
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=
DEMO_PASSWORD=REPLACE_WITH_YOUR_CHOSEN_PASSWORD
OPENROUTER_API_KEY=
OPENROUTER_MODEL=
```

Choose a password of 8–64 characters, at most 72 UTF-8 bytes. Do not overwrite an already working database configuration. Terminal 1, from the project root:

```bash
./mvnw test-compile exec:java \
  -Dexec.mainClass=edu.bits.itsm.DevelopmentDatabase \
  -Dexec.classpathScope=test
```

Leave it running after `Local PostgreSQL ready`. In Terminal 2, enter the same project folder and run:

```bash
./scripts/run-local.sh -Dspring-boot.run.profiles=demo
```

Demo identities: admin@campus.test, technician@campus.test, employee@campus.test. Newly created demo accounts use DEMO_PASSWORD; existing accounts keep their existing password. Changing the seed value does not reset an account. The website has no password reset feature now. Keep your chosen credentials privately.

## Preserved migration and optional AI

V5 is retained unchanged because earlier installations already applied it. Its inactive recovery tables and credential_revision column remain for compatibility; no runtime reset flow or SMTP sender exists. Do not manually delete V5 or run Flyway repair.

AI remains optional and needs your own OpenRouter key and chosen model. Neither is required for normal startup. Ordinary incident email remains simulated, exactly as allowed in the project requirements.

See VERIFICATION_SUMMARY.md for actual test evidence, docs/demo-script.md for deterministic presentation and CLAUDE_REVIEW.md for review scope. The older report/recovery design are historical and must be reconciled before final submission. No native Mac or browser acceptance is implied by Linux tests.
