# Phase 7 — stabilization and demonstration checks

Status on 17 November: IN PROGRESS. Core application acceptance remains documented in `docs/phase-5-6-acceptance.md`. This checklist is prepared work; unchecked runtime steps are not passing evidence. Final report, team identities/responsibility allocation and recording/submission remain Phase 8.

## Automated checks and current environment

- [x] Synchronize the clean older local checkout with the verified review-branch source while retaining ignored local configuration/database files.
- [x] Correct the `.env` launcher to select `mvnw.cmd` on Windows; provide `scripts/run-local.cmd`.
- [x] Read `.env` explicitly as UTF-8 with optional BOM, supporting CRLF and non-ASCII values from Windows editors. Preserve literal parsing, exported-value precedence and Maven exit status.
- [x] Five Python launcher cases passed on macOS, including the added BOM/CRLF/Unicode case.
- [x] 74 Java rules/template cases passed on macOS/JDK 25.0.1 with the updated source.
- [ ] Rerun full `./mvnw clean verify` in an environment that permits local sockets and PostgreSQL shared-memory allocation. Current restricted macOS attempt was blocked during PostgreSQL initialization and loopback fake-provider binding. Earlier Linux 198 regression + 2 packaged passes remain historical evidence for that source snapshot.
- [ ] Complete desktop/mobile extended browser acceptance on a disposable `demo,sla-demo` database. Do not use the user's saved development incidents as test fixtures.
- [ ] Exercise native Windows startup and Java verification using `docs/windows-demo.md`. POSIX launcher tests do not certify Windows behavior.
- [ ] Verify live OpenRouter advice only after user-supplied key/model and approved spend. No live request was made in this session.
- [ ] Implement/verify approved repeated-report correlation; it is still absent. Do not demonstrate existing search as correlation or priority escalation.

## Demonstration 1 — reporting, assignment and access

Use three separate role sessions and a fresh labelled incident against Campus Wi-Fi / Network, impact 2, urgency 2, affected users 10. Expected score 12/P2. Show the reporter's incident, assigned technician's queue and the administrator's assignment/audit view. Show the initial response/resolution deadlines and both notification channels (email is explicitly simulated). The assigned technician acknowledges. Another employee must not read the incident; another unassigned technician must not change it. Capture actual incident ID, screenshot(s) and observed outcomes.

## Demonstration 2 — investigation through closure

On the assigned incident, start investigation; add an internal note and an information request. The employee sees the request, not the private note, and replies to that request. Confirm both deadlines retain their original values. If live AI has been configured and authorized, preview category advice, explicitly apply an eligible category, then preview/apply resolution advice as an editable draft. Otherwise show the unavailable controls and record live AI as pending; never pass off test-provider outputs as live advice.

Resolve with written details. Reporter rejects with a reason; inspect the new cycle and preserved history. Acknowledge again, investigate, record a second resolution, and have the reporter confirm/close and optionally rate 1–5. Inspect both resolutions and ordered audit entries. Restart the app/database using the same demo data directory, sign in again and verify the saved records. Do not reset a user database to simplify this demonstration.

## Demonstration 3 — administration, SLA timing and reports

Prepare two technicians with Network skill and deliberately different proficiency/workload. Show SKILL versus LOAD outcomes on new labelled incidents and stable existing assignments. Make both unavailable or at capacity, report another incident and show CLASSIFIED/unassigned plus administrator notification. Restore eligibility and retry assignment with a reason; deadlines do not restart.

With `demo,sla-demo`, report against **Demo SLA training — short targets**, impact 3/urgency 3. Leave it unacknowledged and show the response warning/breach and resolution warning/breach after their actual thresholds (roughly 8/11/24/31 seconds with one-second scans). Show each event once, the in-app inbox and explicitly simulated email ledger. Filter the dashboard to this incident: total/backlog/critical/recorded violations should each be one while it is pending. Inspect N/A averages and eligible SLA denominator, then clear filters to compare overall totals.

## Browser command

On a browser-capable environment, start an isolated disposable demo instance as described in README, with both `demo,sla-demo` profiles. With Playwright installed and `DEMO_PASSWORD` exported locally:

```sh
BROWSER_AUTOMATION=true BROWSER_BASE_URL=http://127.0.0.1:8081 node scripts/browser_acceptance.cjs
```

Record the actual outcome and inspect screenshots at 1440×1000 and 390×844. No page overflow, JavaScript errors or HTTP 5xx should occur. If the runner or environment fails, preserve the failure rather than checking off the browser criterion. User names, group allocation, a final 15–20 minute script and all-member recording are still separate deliverables.
