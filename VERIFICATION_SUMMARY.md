# Group 57 V6 verification

| Environment | Scope | Result |
| --- | --- | --- |
| Linux, JDK 21.0.13, PostgreSQL 14.15 | Java regression suite | 206 passed |
| Linux, JDK 21.0.13, PostgreSQL 14.15 | Packaged application and database restart | 2 passed |
| Linux | Python launcher suite | 5 passed |
| macOS, JDK 25.0.1 targeting Java 21 | Java workflow rules and view tests | 78 passed |
| macOS | Python launcher suite | 5 passed |

All listed runs completed with zero test failures, errors or skips. A separate HTTP check on the packaged Linux application submitted four reports and linked three to the same master, then checked audit records, incident statuses and reporter visibility.

Live OpenRouter calls, complete V6 browser acceptance and full native Windows verification were outside these recorded runs. The targeted Mac run covered rules, views and the launcher. The full database and restart results are from Linux.

See [test commands and coverage](docs/testing.md) for reproduction.
