# Architecture and implementation contracts

Reviewed against Phases 1–5 and the controlled AI/reporting scope on 17 November 2026. Final submission UML/ER/report artifacts remain Phase 8 deliverables.

## Runtime and boundaries

One Spring Boot modular monolith renders Thymeleaf pages and offers read-only JSON endpoints. PostgreSQL stores accounts, catalog, incidents, conversations, resolutions, SLA cycles/events, notifications and audits. Flyway owns schema changes; Hibernate validates it. There is no separate frontend build, required Docker service, JWT infrastructure or broker.

```mermaid
flowchart LR
  Browser[Browser: HTML/CSS/JavaScript] --> Security[Spring Security: session + CSRF]
  Security --> MVC[Controllers and validated forms]
  MVC --> Services[Identity / catalog / incident services]
  Services --> Domain[Incident state and priority rules]
  Services --> Assignment[Assignment strategy contract]
  Services --> Store[JPA + JDBC on one transaction manager]
  Store --> PostgreSQL[(PostgreSQL)]
  Services --> Audit[Mandatory transactional audit]
  Audit --> Store
  Services --> Notifications[Persisted in-app / simulated email]
  Notifications --> Store
```

Pinned/verified setup: Java target 21, tested JDKs 25.0.1 (earlier macOS acceptance) and 21.0.13 (Linux acceptance); Spring Boot 3.5.16; Maven Wrapper 3.3.4 and Maven 3.9.11; PostgreSQL test tooling Zonky 2.1.0 with native Apple Silicon PostgreSQL 14.15.0; Surefire/Failsafe 3.5.6. Windows execution, other browsers and production deployment remain unverified. Embedded PostgreSQL uses TCP without Unix sockets; the development helper binds loopback. Surefire loads Mockito as a startup agent, including when dynamic JVM attachment is unavailable.

## Domain contracts and patterns

- **Table-driven state machine:** `IncidentState` owns explicit allowed edges; enum declaration order cannot silently change them. `Incident` guards lifecycle mutations. Service-level role, assignment and version checks precede commands.
- **Strategy:** `AssignmentStrategy` has skill-based and load-based implementations. Eligibility and capacity are checked before selecting a candidate. Committed PostgreSQL tests cover both strategies, eligibility, capacity, stable ties, concurrent reports and unchanged existing assignments.
- **Inheritance/polymorphism:** `BaseNotificationChannel` implements shared persistence behavior; in-app and simulated-email subclasses supply channel/status behavior through the notification contract. No actual external email is sent.
- **Composition:** incidents embed a `PriorityAssessment`; the incident service composes catalog, assignment, audit, SLA and notification services. Framework repositories support these contracts rather than replacing business rules.

```mermaid
classDiagram
  Incident *-- PriorityAssessment
  Incident --> IncidentState : validates transitions
  Incident --> UserAccount : reporter / assignee
  IncidentService --> Incident : changes
  IncidentService --> AuditService : same transaction
  AssignmentService --> AssignmentStrategy
  AssignmentStrategy <|.. SkillBasedAssignment
  AssignmentStrategy <|.. LoadBasedAssignment
  NotificationChannel <|.. BaseNotificationChannel
  BaseNotificationChannel <|-- InAppNotificationChannel
  BaseNotificationChannel <|-- SimulatedEmailChannel
```

Priority is impact × urgency × snapshotted service criticality. P4=1–2, P3=3–7, P2=8–17, P1=18–27. Affected-user count is context. Reporter inputs cannot directly set priority, reporter identity, assignment or status. Reasoned administrator corrections audit old/new factors. Repeat-report correlation/escalation is approved follow-up scope, not implemented.

Campus Community is the displayed complainant role (historically `EMPLOYEE` internally). The stored identifier, authorities, demo email and route paths remain compatible; Admin and Technician labels are unchanged.

## Interfaces and access

| Interface | Contract |
|---|---|
| `/register`, `/login`, POST `/logout` | Campus Community registration; role cannot be injected; hashed passwords; sessions; CSRF-protected mutations |
| `/employee/incidents/new`, POST `/employee/incidents` | Validated title/description/catalog/impact/urgency/affected count; retain invalid input |
| `/incidents`, `/incidents/{id}` | Lists/details scoped to reporter, assigned technician or active administrator |
| POST `/incidents/{id}/action` | Versioned acknowledgment/start/resolve/verify/close/reject; invalid edges and roles rejected |
| POST `/incidents/{id}/messages` | Versioned public/internal/request messages; only reporter replies to same-incident requests |
| POST `/incidents/{id}/feedback` | Optional reporter feedback after resolution; rating 1–5; once per incident |
| `/admin/users`, `/admin/catalog`, `/admin/audit`, `/admin/email` | Active administrator only; sensitive configuration writes check actor at service boundary too |
| `/api/incidents`, `/api/incidents/{id}`, `/api/metrics` | Authenticated read-only projections; ownership/role scope; no entity/password serialization |

Incident history services recheck current actor access. Staff-only suggestions and private investigation events are omitted from Campus Community history. Account deactivation/role changes invalidate session access on the next request. Forbidden POST forwards now return 403 through the error page. Input/output escaping is verified; this is not a penetration-test or production-hardening claim.

## Transactions, history and concurrency

Incident mutations combine JPA entity changes with JDBC child records under one Spring transaction. `AuditService` requires an existing transaction; an audit cannot silently commit separately. PostgreSQL constraints enforce foreign keys, bounds and uniqueness. A database lock serializes assignment/configuration-sensitive changes, and incident versions reject stale edits. V4 conversation revision ensures even identical-clock message writes advance the incident version.

Audit records carry incident reference, actor/System identity, timestamp, action, prior/new lifecycle state and context. Priority before/after values are descriptions rather than lifecycle states. Histories order by persisted IDs because several events may share a transaction timestamp. Earlier published audit records are not rewritten. There is no claim of tamper resistance against a direct database writer.

Rejection retains old resolution/cycle/breach records and first response/resolution timestamps; a new cycle starts with stored targets. Failed commands roll back state, child rows, audits and simulated notification rows together. Both notification channels are local persisted records, and delivery failure rolls back the command/monitor transaction. Real external email delivery is outside the approved scope.

## AI and reporting contracts

`AiAdvisor` composes incident authorization, a transport contract and transactional suggestion storage. `OpenRouterAdapter` fixes the production HTTPS endpoint, keeps credentials backend-only, caps the complete request at 25 seconds and cancels response bodies above 64 KiB. Requests happen outside the save transaction; saving and applying recheck the incident version. Category IDs must be integral and active, and application must retain eligible skills. Resolution advice creates an editable draft only. The server labels retrieved final accepted resolutions separately from generated advice; it selects at most three relevant closed-ticket examples, 500 characters each, and accepts at most 6,000 characters of generated resolution advice. Tests use a mock transport or a loopback fake provider, never a live paid request.

Reporting builds parameterized AND predicates shared by list/count/metrics/category totals. Current account activity/role and reporter/assignee/admin scope are checked in the service. Dates are inclusive Asia/Kolkata calendar days with an exclusive next-midnight upper bound. Pages contain 20 rows, ordered by creation time then stable ID. Averages include only qualifying timestamps and expose sample counts; missing averages remain N/A. Compliance uses eligible first-cycle incidents without recorded first-cycle breaches, as defined in P09; recorded alerts can lag a deadline by the scan interval.

## Remaining phase gates

See `docs/phase-5-6-acceptance.md` and `plan.md` for execution evidence/status. Phase 6 still needs the user's live AI model/key/budget and separate provider verification; approved repeated-report correlation/escalation remains unimplemented. Phase 7 broader stabilization and the final three demonstration workflows remain open. Phase 8 covers final diagrams/report/member responsibility/video/submission evidence.
