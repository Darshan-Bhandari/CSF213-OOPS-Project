# Codex project instructions

## Start here

- Read `plan.md` before planning or implementing project work. It is the source of truth for approved scope, business rules, status, blockers, and acceptance criteria.
- Read `README.md` for setup and runtime details. Consult `docs/testing.md` before changing or describing verification.
- Inspect the current source before relying on an earlier status in the plan. Report discrepancies and update the plan when work changes the known state.

## Project decisions

- Implement approved decisions in `plan.md`, including P01–P10. Do not ask the user to approve them again.
- Do not silently choose a new product behavior, technology, external service, AI model, spending, public deployment, or team responsibility. Make material unresolved decisions explicit and continue independent work where possible.
- Keep provider credentials out of source control and browser code. AI must remain optional for local startup; do not make live provider calls without configured credentials and an approved model/budget.

## Work and status

- Prefer small, reviewable changes that advance the current phase and its recorded exit criteria.
- Keep `plan.md` synchronized with changed decisions, phase status, blockers, and verification. Check off acceptance criteria only when evidence supports them; record failed or blocked verification accurately.
- Do not claim tests, runtime behavior, database persistence, or browser behavior passed unless they were actually verified. Preserve the distinction between compilation and integration/runtime checks.
- Do not start optional enhancements while required functionality or its acceptance checks remain outstanding.
- Report what changed, why, what was verified, and any remaining blocker when finishing a work session.

## Existing stack

Use the approved Java, Spring Boot, Thymeleaf, HTML/CSS/JavaScript, PostgreSQL, Spring Security, JPA/Hibernate, Flyway, Maven Wrapper, and JUnit/Spring testing choices recorded in `plan.md`. The app is a local-first modular monolith. Follow the existing package and migration conventions.
