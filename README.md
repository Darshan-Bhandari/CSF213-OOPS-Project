# BITSOps Assistant — Group 57

BITSOps Assistant is a campus IT service management system for CS F213 / MAC F212 Object-Oriented Programming. Campus Community members report and track incidents. Technicians investigate and resolve assigned incidents. Administrators manage accounts, catalogues, assignments, SLA policies and audit history.

The V6 submission includes skill-based and load-based assignment, response and resolution SLA tracking, in-app notifications, simulated email, role-scoped reporting, and staff-reviewed duplicate incident correlation. Optional OpenRouter advice supports category and resolution suggestions.

## Run locally

Install a full JDK 21 or 25 and Python 3. The Maven Wrapper downloads Maven and dependencies on the first run. Use an existing PostgreSQL database or the included local development helper. The application does not require an AI key or a separate frontend build.

- [macOS and Linux setup](MAC_START_HERE.md)
- [Windows PowerShell setup](docs/windows-demo.md)

The setup guides cover database configuration, demo accounts and startup. The app runs at [http://127.0.0.1:8080](http://127.0.0.1:8080) by default.

## Evaluate the submission

- [Architecture and implementation contracts](docs/architecture.md)
- [Example application workflows](docs/phase-7-checklist.md)
- [Duplicate incident correlation](docs/incident-correlation.md)
- [Test commands, coverage and limits](docs/testing.md)
- [Verification summary](VERIFICATION_SUMMARY.md)

The final V6 Linux run passed 206 Java regression tests, two packaged restart tests and five Python launcher tests. A targeted macOS run passed 78 Java rules and view tests and five launcher tests. The project report and recorded demonstration are separate course submissions.

## Source layout

| Path | Contents |
| --- | --- |
| `src/main/java/edu/bits/itsm/` | Domain model, services, controllers and configuration |
| `src/main/resources/` | Thymeleaf views, static assets and Flyway migrations V1–V6 |
| `src/test/` | Java regression and packaged restart tests, plus Python launcher tests |
| `scripts/` | Local launchers for Unix and Windows |
| `third-party/` | Maven Wrapper license and attribution |
