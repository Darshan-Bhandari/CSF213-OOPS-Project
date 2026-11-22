# Run BITSOps Assistant on macOS or Linux

Use a full JDK 21 or 25 and Python 3. Use a native ARM64 JDK on Apple Silicon. Run commands from the project root containing `pom.xml`. The Maven Wrapper needs internet access on the first run.

## Configure the application

```bash
chmod +x mvnw scripts/run-local.sh
test -f .env || cp .env.example .env
```

Set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD` in `.env` to match your PostgreSQL server. For the included development database, use these values.

```dotenv
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:54329/postgres
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=
DEMO_PASSWORD=REPLACE_WITH_YOUR_CHOSEN_PASSWORD
```

Choose a demo password of 8–64 characters and at most 72 UTF-8 bytes. Leave the OpenRouter settings blank for normal evaluation. Keep `.env` local.

## Start PostgreSQL and the application

If using the development database, start it in terminal 1 and leave it running.

```bash
./mvnw test-compile exec:java -Dexec.mainClass=edu.bits.itsm.DevelopmentDatabase -Dexec.classpathScope=test
```

The helper binds to loopback and persists data in `.local/postgres-data`. If an existing PostgreSQL instance is already configured, use it and skip the helper. Flyway applies any pending migrations on startup.

From the project root in terminal 2:

```bash
./scripts/run-local.sh -Dspring-boot.run.profiles=demo
```

Open [http://127.0.0.1:8080](http://127.0.0.1:8080) and use the demo password with one of these newly seeded accounts.

| Account | Role |
| --- | --- |
| `employee@campus.test` | Campus Community |
| `technician@campus.test` | Technician |
| `admin@campus.test` | Administrator |

The seed profile preserves existing accounts and passwords. Stop each process with Ctrl+C and retain its database directory to reuse the saved incidents. For verification, see [the test guide](docs/testing.md).
