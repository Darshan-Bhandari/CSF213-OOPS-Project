# Password recovery implementation 21 November 2026

## Scope

Anonymous GET/POST `/forgot-password` and `/reset-password`, a sign-in link, configured sender and V5 schema. RecoveryDelivery abstracts SMTP delivery and explicit local demo capture. The normal website requires no sender. Real SMTP is configured independently of the existing simulated incident-email channel.

## Safety contracts

- 32 cryptographically random bytes encode a 43-character URL-safe token. Only SHA-256 hashes enter PostgreSQL; plaintext links occur only in the intended email/private explicit local file.
- Expiry is strict at 15 minutes; a successful reset deletes all account tokens. A newer accepted request replaces earlier tokens. Passwords must match and satisfy the project's length and BCrypt UTF-8 limits.
- User-row and token-row locking serialize reset attempts. Updating the hash, credential revision, token consumption and reset audit is atomic. Account deactivation prevents reset even for a previously issued link.
- A separate credential revision invalidates old authenticated sessions on their next request; it does not depend on retained plaintext credentials. Other account fields/roles/tickets remain intact.
- Generic responses for unknown, inactive, cooldown, rate-limited and delivery-failed requests. Response-time equality is not claimed. Persistent global gate caps 20 attempts/minute and per-address cooldown is 60 seconds; unknown addresses count too. The global cap trades availability for simple demo abuse control. A production public service needs per-source/edge abuse protection and operational monitoring as well.
- Only configured APP_BASE_URL builds links. HTTP allowed only for loopback; external URLs require HTTPS. Reset pages load no third-party assets, send no-store/no-referrer and a restrictive CSP. Keep query strings out of access logs.
- No external send on tests. SMTP uses configured STARTTLS and bounded connection/read/write timeouts. SMTP send occurs before transaction commit: a rare commit failure after send can produce an unusable email; there is no exactly-once external delivery claim or queue. Sender errors invalidate the token and retain the generic response. No reset link appears in audit or application logging.
- Local mode is explicit, Mac/Linux only and restricted to loopback app binding. Directories are 0700 and files 0600. It is a developer mailbox, never an admin browser inbox. It must not be exposed or included in review bundles. Files remain until locally deleted.

## Review targets

V5 adds credential_revision, token hashes/expiry, hashed-address cooldown and a singleton global limit row. Published V1–V4 are unchanged. No existing database reset or repair is needed. Review PasswordRecovery, RecoveryMail, RecoveryController, AccountStatusFilter and PasswordRecoveryIntegrationTest. App registration is not email-verified in the existing product; mailbox control is therefore the recovery identity. No unverified change-email feature was added.

See MAC_START_HERE.md for exact configuration and docs/testing.md for results and limits. The 45-page report predates this feature; use this file as its implementation addendum until final report reconciliation.


## Exact V5 schema

- `user_accounts.credential_revision BIGINT NOT NULL DEFAULT 0`: increments when a password is reset; AccountPrincipal snapshots it and AccountStatusFilter revokes older sessions on their next request.
- `password_reset_tokens`: `token_hash VARCHAR(64) PRIMARY KEY`, `user_id BIGINT NOT NULL REFERENCES user_accounts(id)`, `expires_at TIMESTAMPTZ NOT NULL`, `used BOOLEAN NOT NULL DEFAULT FALSE`. The used field is not set in the current code: successful reset deletes all account token rows. Single-use behavior relies on locking and deletion, not flipping this flag.
- `password_reset_limits`: `email_hash VARCHAR(64) PRIMARY KEY`, `requested_at TIMESTAMPTZ NOT NULL`. A hashed normalized address enforces a 60-second cooldown; this is not a per-IP limiter.
- `password_reset_gate`: singleton `id INTEGER PRIMARY KEY CHECK(id=1)`, `window_start TIMESTAMPTZ NOT NULL`, `requests INTEGER NOT NULL`. Its row lock and global 20-per-minute window serialize recovery request processing, including the synchronous SMTP send.

## Known limitations from independent static review

SMTP is synchronous under the global gate transaction/lock and its timeouts can delay all recovery requests. Known and unknown addresses may have different response timings despite identical visible messages. The global cap has no per-IP fairness and can be exhausted to deny recovery. Login/registration throttling, lockout and email verification are absent; registration explicitly reports duplicate emails. Reset URLs contain bearer tokens in query strings, mitigated by no-store/no-referrer but still sensitive to copying, browser history or access logging. Delivery exceptions invalidate tokens but produce no operator log. POSIX local mode is Mac/Linux only; its sensitive files are not automatically deleted. These are documented issues, not approved fixes. No application behavior was changed in this documentation task.
