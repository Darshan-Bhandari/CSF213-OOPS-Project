# Test commands and coverage

Run the Java suite from the project root. The test suite provisions isolated PostgreSQL instances and uses controlled provider fixtures. It does not require a configured application database, demo password or live OpenRouter key.

```bash
./mvnw clean verify
python3 -m unittest discover -s src/test/python -v
```

On Windows use `.\mvnw.cmd clean verify` in PowerShell. The Python launcher tests use a POSIX fixture. See [Windows setup](windows-demo.md) for platform-specific instructions.

## Where to find results

- Maven Surefire writes regression results to `target/surefire-reports/`.
- Maven Failsafe writes packaged restart results to `target/failsafe-reports/`.
- The Python suite prints launcher results in the terminal.

## Automated coverage

| Test class | Coverage |
| --- | --- |
| `FoundationIntegrationTest`, `CatalogIntegrationTest` | Authentication, roles, account status, catalogues and startup |
| `WorkflowRulesTest`, `WorkflowViewsTest` | Priority rules, lifecycle transitions and role-aware templates |
| `WorkflowIntegrationTest` | Report-to-close lifecycle, private messages, rejection and re-resolution, feedback and V6 linking |
| `AuditConsistencyIntegrationTest` | Transactional history and rollback |
| `AutomationIntegrationTest` | Assignment strategies, capacity, SLA policies and notifications |
| `IntelligenceReportingIntegrationTest`, `OpenRouterAdapterTest` | Controlled provider behavior, incident advice and role-scoped reporting |
| `WorkflowRestartIT` | Packaged application and database restart, preserved records and overdue SLA processing |

The [verification summary](../VERIFICATION_SUMMARY.md) gives the recorded V6 results and scope. The earlier [core workflow](phase-1-4-acceptance.md) and [automation/AI/reporting](phase-5-6-acceptance.md) records provide supporting evidence.
