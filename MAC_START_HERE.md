# Run the updated website on your Mac

This complete source bundle also works as the Claude review ZIP. No passwords, API keys, saved database or build caches are included. Java backend target 21, Spring Boot, Thymeleaf, HTML/CSS/JavaScript and PostgreSQL remain unchanged.

## Updating an existing installation

1. Stop the app with Ctrl+C. Keep a backup of your existing project folder.
2. Extract this ZIP into a new folder. Copy your existing `.env` into the extracted `campus-itsm` folder. Do not publish or send that file to Claude.
3. Keep your existing PostgreSQL instance running and retain its datasource URL/credentials. Do not delete its database, run a reset, edit old migrations or repair checksums. Flyway automatically applies the new V5 migration on startup.
4. If you used our portable database, continue running the database helper from the OLD folder: this preserves its `.local/postgres-data` without copying it. Launch the new app against that existing instance. Do not run two helpers on port 54329.
5. Add recovery settings below to `.env`, then run the new app.

## A fresh Mac

Install a full JDK 21 (Apple Silicon ARM64 on M-series Macs) and Python 3. Check `java -version` and `python3 --version`. If Apple blocks Python with its Xcode licence prompt, finish that setup first.

In Finder, open the extracted folder containing `pom.xml`. Type `cd ` in Terminal, drag this folder in and press Enter. Then:

```bash
chmod +x mvnw scripts/run-local.sh
test -f .env || cp .env.example .env
open -e .env
```

For a new portable database set:

```dotenv
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:54329/postgres
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=
DEMO_PASSWORD=REPLACE_WITH_YOUR_OWN_PASSWORD
RECOVERY_MODE=local
APP_BASE_URL=http://127.0.0.1:8080
```

Replace the password placeholder (8–64 characters, at most 72 UTF-8 bytes). In Terminal 1:

```bash
./mvnw test-compile exec:java \
  -Dexec.mainClass=edu.bits.itsm.DevelopmentDatabase \
  -Dexec.classpathScope=test
```

Wait for “Local PostgreSQL ready” and keep it open. In Terminal 2, enter the same project folder:

```bash
./scripts/run-local.sh -Dspring-boot.run.profiles=demo
```

Open http://127.0.0.1:8080. Newly created demo logins are `admin@campus.test`, `technician@campus.test`, `employee@campus.test`, using DEMO_PASSWORD. Existing accounts retain their passwords.

## Try password recovery without any email account

Explicit `RECOVERY_MODE=local` writes private local demo messages and requires loopback binding. It does NOT send email. Default mode is `disabled`; the website otherwise starts normally.

1. Click Forgot password and enter an existing account email. Demo `.test` emails work in local mode.
2. The site always displays a generic message. Wait at least 60 seconds between requests for the same address.
3. On YOUR Mac, open `.local/recovery-mail` in the NEW app folder with `open .local/recovery-mail`. Open the newest `reset-*.txt` file and paste its reset URL into your browser. Do not share the URL/file or upload this directory to Claude.
4. Choose and confirm a new password. Merely opening the link does not change it. Log in with the new password. The old password stops working and old sessions expire on their next request.
5. Links expire at 15 minutes, are single use and are invalidated by a newer accepted request. Unknown/inactive emails generate no message. Existing account/ticket data is retained.

Local messages are sensitive development artifacts and remain on disk until you remove them. Keep the website private to your computer. Native Windows local file mode is unsupported; SMTP or disabled mode are available there.

## Real email when you are ready

Use any SMTP account/relay you select. No provider subscription or account has been created for you. App users do NOT need two-step verification. The sender's own provider may require it; if you do not want that, choose a provider allowing SMTP credentials without it. Free quotas and sender verification depend on that provider.

```dotenv
RECOVERY_MODE=smtp
APP_BASE_URL=http://127.0.0.1:8080
RECOVERY_FROM=your_verified_sender@example.com
SMTP_HOST=your_provider_smtp_host
SMTP_PORT=587
SMTP_USERNAME=your_smtp_username
SMTP_PASSWORD=your_smtp_credential
SMTP_AUTH=true
SMTP_STARTTLS=true
```

Restart the app after configuration. Use an account registered with a REAL reachable mailbox, not `@campus.test`. SMTP failure does not expose provider details to the requester and invalidates the generated token. It is intentionally not reported as successful inbox delivery. Real delivery has not been tested with your credentials.

For local demos open the received link on the same Mac running the app; localhost links will not reach it from your phone. Public deployment needs your real HTTPS APP_BASE_URL and additional operational review. Do not derive it from browser request headers.

## Checks and review

```bash
./mvnw clean verify
python3 -m unittest discover -s src/test/python
```

Read `docs/password-recovery.md`, `docs/testing.md`, `plan.md` and `CLAUDE_REVIEW.md`. Existing report PDFs are historical and do not yet describe this feature; a dated addendum is provided. This bundle changes real recovery only: incident notification emails remain simulations.
