# Assignment, SLA, notifications, AI and reporting acceptance

Work date: 17 November 2026. `plan.md` remains the phase/status authority. This record distinguishes local provider fixtures from a live AI demonstration.

## Changes

- A correction that retains the incident priority now retains its saved SLA targets, even if an administrator has edited catalog policies. A priority change still selects the applicable policy and recalculates pending deadlines from their original anchors. Existing violations are retained.
- Skill configuration rejects missing/non-technician IDs with a business error before the foreign-key write.
- The AI transport bounds the entire request, including a stalled response body, to 25 seconds. It cancels bodies above 64 KiB and does not surface provider diagnostics or credentials. The endpoint remains fixed to OpenRouter in production; the test-only constructor uses loopback HTTP.
- Category suggestions require an integer active category ID, a nonblank explanation and explicit human application. Resolution advice is limited to 6,000 characters. Retrieved history is limited to the final accepted resolution of three relevant closed incidents, 500 characters each. Server-authored labels distinguish those records from unverified generated advice. Applying advice creates a draft and does not advance the lifecycle.
- Reporting service reads recheck account activity and current role, as incident/history services already do.
- Embedded PostgreSQL uses TCP without Unix sockets; the optional local database helper continues to bind only loopback. Mockito loads its test agent at JVM startup rather than attempting dynamic attachment. These are verification portability changes, not extra application services.
- Published V1–V4 migrations are unchanged. No credential, AI model selection, public hosting or paid request is included.

## Repeatable automated evidence

`AutomationIntegrationTest` uses an isolated real PostgreSQL database with **committed** application transactions and a controlled clock. Its fixture resets only that test-owned database. `IntelligenceReportingIntegrationTest` uses real PostgreSQL and a mocked `AiTransport`. `OpenRouterAdapterTest` sends real HTTP to an in-process loopback fake provider.

| Scenario / input | Expected result | Evidence |
|---|---|---|
| Two concurrent reports, one free capacity slot, each strategy | One assignment; one CLASSIFIED ticket; capacity remains one | `concurrentReportsCannotExceedLastAvailableSlot` |
| High skill with work versus lower skill without work | SKILL and LOAD select different eligible technicians; stable ID settles ties | `bothStrategiesUsePersistedWorkloadProficiencyAndStableTies` |
| Unavailable, inactive, unskilled or full technician | No assignment; SLA retained; administrator notified in both channels | `ineligibleTechnicianLeavesTrackedClassifiedIncident` |
| RESOLVED, VERIFIED or CLOSED assigned work | Work no longer consumes active capacity | `completedWorkDoesNotConsumeCapacity` |
| Reassignment after first acknowledgment | New technician must acknowledge; first response and deadlines preserved | `reassignmentRequiresNewAcknowledgmentAndRetainsFirstResponseAndDeadlines` |
| Ineligible manual target / blank reason | No partial reassignment, audit or notification writes | `manualAssignmentRejectsInvalidEligibilityAndReasonWithoutChangingExistingWork` |
| Availability restored and administrator retries | Eligible assignment succeeds without restarting elapsed SLA | `pendingAssignmentCanBeRetriedAfterAvailabilityReturns` |
| Service override, subsequent policy edit, same-priority correction | Override wins; saved ticket targets stay fixed; new ticket uses new policy | `servicePolicyOverridesDefaultAndLaterPolicyEditsOnlyAffectNewTickets` |
| Required policy absent | Clear rejection; no incident, cycle or notification orphan | `absentPolicyRejectsCreationWithoutOrphanWrites` |
| Priority lowered after response violation | New pending targets retain original anchor; old violation remains | `reprioritizationKeepsElapsedAnchorAndExistingBreach` |
| 80% warning, overdue response/resolution, repeated scans | One event of each kind; relevant users each receive two correctly labelled channels | `warningAndBreachAreOncePerTargetWithRelevantRecipientsAndBothChannels` |
| First scan already overdue | Breaches only; historical warnings not fabricated | `firstOverdueScanRecordsBreachesWithoutBackfilledWarnings` |
| Two concurrent SLA scans | One durable warning and one delivery per recipient/channel | `concurrentMonitorsPersistOneEventAndOneDeliveryPerRecipientChannel` |
| Notification constraint fails during creation / scan | Entire transaction rolls back; later retry records exactly one complete event | `simulatedEmailFailureRollsBackCreationAndBothChannels`, `failedSlaDeliveryRollsBackEventAndRetriesWithoutLossOrDuplicateMessages` |
| Read someone else's notification | No read marker changes; simulated email remains separate | `readingNotificationCannotMarkSomeoneElsesInboxOrEmailAsRead` |
| Stop Java/PostgreSQL with an overdue pending ticket, restart twice | Startup catches up without periodic/browser polling; original rows survive and remain deduplicated | `WorkflowRestartIT.startupCatchesUpOverdueTargetsAndNextRestartDoesNotDuplicateDeliveries` |
| Valid category suggestion | Stored for review; explicit application only; priority/state unchanged | `categorySuggestionRequiresExplicitApplicationAndNeverMovesLifecycleOrPriority` |
| Malformed, fractional, overflow, nonexistent or blank category output | No suggestion or incident mutation | `invalidCategoryOutputDoesNotSaveOrMutateIncident` |
| Inactive category / assignee lacks suggested skill | Reject generation/application; retain prior category | `inactiveCategoryAndMissingRequiredSkillCannotBeApplied` |
| Relevant closed ticket with rejected then accepted fix | Only final accepted fix enters prompt; draft clearly separates history and advice | `resolutionUsesOnlyFinalClosedRelevantHistoryAndProducesAnEditableDraft` |
| Missing key / failed provider | No fake advice; ordinary manual workflow still closes successfully | `missingKeyKeepsAdvisorUnavailableWithoutCallingTransport`, `providerFailureLeavesCrudWorkingAndNoPartialSuggestion` |
| Incident changes during request / before application | Conflict; stale advice cannot overwrite newer work | `staleProviderResponseAndStaleApplicationAreRejected` |
| Provider HTTP 401/429/500, invalid content, oversized/stalled body, interruption | Safe unavailable error, bounded resources, preserved interruption | `OpenRouterAdapterTest` |
| Script text from model; employee apply attempt; missing CSRF | HTML escaped; employee cannot read/apply; CSRF rejected | `renderedSuggestionIsEscapedAndEmployeeCannotReadOrApplyIt` |
| Eight combined criteria and role scope | AND filtering; lists/counts/category totals/metrics agree | `eightCombinedFilterCriteriaAndRoleScopeMatchStoredRecords` |
| IST midnight boundaries, 21 matching tickets, SQL-like search | Inclusive local dates; stable 20/1 pagination; input remains data | `dateFiltersUseInclusiveIndianCalendarDays`, `paginationIsStableScopedAndCountIsIndependentOfPage` |
| Known timed fixtures and empty workspace | Averages 3.8/3.0 minutes with sample counts; eligibility 2, met 1; empty averages null | `metricsHaveKnownAveragesDenominatorsAndZeroDataBehavior` |
| Deactivated account / null principal | Reporting service access denied | `reportingServiceRejectsDeactivatedAccountsAndNullPrincipals` |

Execution totals and final outcomes are recorded in `docs/testing.md`; table entries identify executable assertions rather than inventing separate case counts.

## Manual Phase 5 demo

1. Start the development database and app using README instructions. For accelerated, clearly labelled timing only, use profiles `demo,sla-demo`; keep OpenRouter configuration empty. Existing demo passwords are never reset by seeding.
2. In separate browser sessions, sign in as employee, technician and administrator. In catalog configuration, switch SKILL/LOAD. To show different choices, provision two technicians with Network skills and deliberately different proficiency/workload. Show that old assignments stay unchanged.
3. Report a Network incident against **Demo SLA training — short targets**, impact 3, urgency 3. Its P1 targets are 10 seconds response and 30 seconds resolution. Normal services retain their normal policies.
4. Leave it unacknowledged. Refresh after roughly 8/11 seconds for response warning/breach and 24/31 seconds for resolution warning/breach; the training scheduler scans each second. Show the incident timeline, employee inbox and admin's explicitly simulated-email ledger. Reloading does not create additional alerts.
5. Filter the admin dashboard by this incident ID, status, P1, Network, training service, technician and breached SLA. Total, backlog, critical backlog and recorded violations each show one for that isolated pending ticket. Averages display N/A until a qualifying completion exists.
6. The AI buttons remain unavailable while credentials/model are absent. Test fixture advice is never shown as a live provider response.

The extended browser runner automates these checks plus the full existing lifecycle at desktop/mobile sizes. Start a disposable `demo,sla-demo` instance as in README and set `BROWSER_AUTOMATION=true`. `BROWSER_EXECUTABLE` optionally selects an installed Chromium binary; otherwise Playwright uses its own browser. Credentials, database files and screenshots remain ignored.

## Remaining gates

- Real OpenRouter call and live AI demonstration require the user's chosen model, local key and spending approval. None was selected or used here.
- Approved repeated-report correlation/escalation still requires its documented matching/threshold/SLA design and implementation; it is not silently claimed by the existing filters or priority formula.
- Final three complete presentation workflows, broader stabilization, member allocation, report/UML/ER, recording and submission remain tracked in Phases 7–8. Linux/Chromium acceptance does not certify Windows/macOS execution, other browsers, production load, accessibility or penetration testing.
