# Deterministic recorded demonstration 15–20 minutes

No live AI or real SMTP is required or promised. Every member must introduce themselves and explain actual work; fill names/responsibilities before recording. Rehearse first; this document is not a claim that the recording happened.

## Preparation

Preserve existing configuration/database. Follow MAC_START_HERE.md and confirm normal login for Admin, Technician and Campus Community. If using demo accounts, existing passwords are unchanged. Use separate browser profiles/private sessions for role switching. Hide credentials and reset tokens in recordings.

For accelerated SLA evidence, the EXISTING training profile can be used:

```bash
./scripts/run-local.sh -Dspring-boot.run.profiles=demo,sla-demo
```

This seeds a clearly labelled service `Demo SLA training — short targets` once, with criticality 3 and P1 targets of 10 seconds response / 30 seconds resolution; scan interval is 1 second. It does not change normal service policies. Do not claim the short targets are production campus SLAs. Use an isolated demo database if you want training data separate; do not delete real records. Verify the actual training policy in Admin because existing seeded/policy data is preserved.

## 00:00–02:00 introductions

Each member states their name, ID, actual contribution and the module they will demonstrate. State the problem and three roles. The complaint role displays Campus Community but remains EMPLOYEE internally.

## 02:00–05:00 design walkthrough

Show the architecture, AssignmentStrategy implementations and explicit IncidentState transition table. Trace one incident's data and transaction boundary. Explain impact × urgency × snapshotted criticality; P1 >=18, P2 >=8, P3 >=3, otherwise P4. Explain that incident email is simulated and real AI advice remains optional/unverified live.

## 05:00–08:00 workflow 1 report to assignment

1. Admin shows active service/category, technician active/available status, appropriate category skill and workload below capacity. Select SKILL ranking.
2. Campus Community reports a Campus Wi-Fi incident with impact 3 and urgency 2. Criticality 3 gives score 18 / P1.
3. Show persisted priority, assignee and SLA deadlines; switch to Technician and show assigned incident.
4. Show in-app notifications and Admin simulated-email records. Explain these records are not delivered email.
5. Show LOAD configuration as the second interchangeable ranking. If demonstrating different choices, prepare two eligible technicians with visibly different workload/proficiency. Do not promise different outcomes when eligibility/ties select the same candidate.

End state: tracked assigned incident, or correctly explained classified/unassigned state if no candidate is eligible.

## 08:00–13:00 workflow 2 investigation and accepted closure with SLA

1. Create a separate P1 training-service ticket with impact 3, urgency 3. Score is 27. Note creation time and 10-second/30-second deadlines.
2. Leave acknowledgment pending through the 80% response point (8 seconds). Refresh around 8–10 seconds to inspect a warning. Then wait strictly beyond 10 seconds and refresh for a response breach. Scans are periodic; a delayed first scan may record a breach without a warning. Rehearse timing rather than claiming an unseen event.
3. Assigned technician acknowledges, starts investigation, adds an INTERNAL note and requests clarification. Reporter replies to that request. Demonstrate internal note privacy; do not enter sensitive administrative free-text because audit history still has the documented denylist limitation.
4. Record resolution details; reporter verifies then closes and gives optional feedback. Resolution may breach the 30-second training deadline during the walkthrough; explain the actual recorded events honestly.
5. Show persisted history/cycle, deadline records and both notification channels. Conversations do not pause SLA time.

End state: verified/closed incident with recorded warning/breach evidence that actually occurred.

## 13:00–17:00 workflow 3 rejected fix and new cycle

1. Use a separate ordinary-service incident; assign, acknowledge, investigate and provide a first resolution.
2. Reporter rejects the fix with a reason. Show CLASSIFIED/reassignment, preserved original resolution and incremented cycle.
3. Show new cycle deadline derived from stored targets. Technician acknowledges again, investigates and supplies a second resolution.
4. Reporter verifies/closes. Display both resolution records/cycles, then filter the incident list by service/category/status and show real scoped metrics.
5. Optional alternative: reasoned Admin correction/reassignment with proper eligibility and preserved elapsed time. Do not expose private reasons to reporters pending the separate privacy fix.

End state: retained failed fix plus accepted second fix; no invented correlation or live AI interaction.

## 17:00–19:00 testing and limitations

Show VERIFICATION_SUMMARY.md and actual available test logs. State Linux 21 November: 220 regression + 2 packaged restart and 5 Python checks passed. Claude reviewed statically only. A Mac result must come from a Mac log, not these Linux runs. Mention live AI/SMTP, correlation, latest visual/native-platform and submission gates honestly.

Explain a race, invalid transition or rollback test from actual source. Finish within 20 minutes. Confirm each member participated and inspect the video before upload.
