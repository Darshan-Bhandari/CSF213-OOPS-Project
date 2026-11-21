# Independent review request

Review the entire project critically against `OOP_S1_2026-2027_Project.pdf` (original course requirements, 17 pages). Read AGENTS.md, plan.md and docs/testing.md, then inspect actual source rather than accepting report/test claims at face value.

Produce:
1. A requirement-by-requirement matrix for R01–R27: implemented, verified, partial/missing, and exact source/test evidence.
2. Concrete correctness/security issues ranked by severity, with file/method references and reproduction steps. Inspect password token entropy/hash/expiry/single-use, race locking, session invalidation, enumeration, CSRF, throttling, URL/referrer leakage, SMTP failures and local mailbox restrictions.
3. OOP/pattern assessment grounded in code, schema/diagram mismatches, priority/assignment/SLA/rejection/feedback/privacy and transactional consistency checks.
4. Run build/tests in an isolated test environment if available; distinguish static analysis, test execution and real browser/live-provider evidence. Never use/delete a real user database or publish keys.
5. Mac reproducibility and required final report/video/submission gaps. Do not inflate historical passing totals into a latest full acceptance claim.

Known open gates: repeated-report correlation is approved but absent; live AI and real SMTP require user configuration and are not verified; latest UI visual/native Windows and final submission gates remain as documented. Password recovery was added after the existing 45-page report: docs/password-recovery.md is its addendum. Ordinary incident email remains simulated. Team identities/contributions are not supplied.

Files included: complete source, Maven wrappers including hidden .mvn configuration, tests, migrations, living plan, architecture, report editable sources and original requirements PDF. The review verification summary records what actually ran. No .env, secret, token, user database, dependency cache, target directory or Git credentials are included.

Please do NOT modify the project until after delivering the review and separating mandatory defects from optional enhancements. Do not contact live AI/email providers or incur spending. A simulated/fake provider pass is not a real provider pass.
