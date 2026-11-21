# Mac verification pending

The assistant's selected environment is Linux; the Mac environment is unavailable. No Mac clean verify was executed for this task. Earlier Linux evidence is not a replacement for a Mac result.

On the Mac, enter the latest extracted folder containing pom.xml and run these commands in Terminal. They do not start the demo or reset the existing user database; Maven tests use their isolated fixtures.

```bash
set -o pipefail
./mvnw clean verify 2>&1 | tee mac-clean-verify.log
```

After completion, run:

```bash
printf 'Verification exit status: %s\n' "$?"
tail -n 60 mac-clean-verify.log
```

The status must be captured immediately after the pipeline, before other commands; a zero status is necessary but inspect BUILD SUCCESS and the actual Surefire/Failsafe totals too. If it fails, retain the complete log and send the first error/root cause. Do not relabel a failure as an environment pass. Never paste .env/API keys/SMTP credentials into the log or chat.

Separate the startup command `./scripts/run-local.sh -Dspring-boot.run.profiles=demo` from `./mvnw clean verify`: a spring-boot:run exit-code-1 footer is not a test-suite result.

Current status: NOT RUN ON MAC. Record JDK version, date, command, result and counts only after the actual run. Do not run twice merely to produce a better-looking report unless a failure/change requires it.
