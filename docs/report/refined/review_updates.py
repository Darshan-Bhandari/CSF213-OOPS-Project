"""Documentation reconciliation after independent static review; no runtime changes."""
def update(pages,H,P,S,T,D,C):
 for pg in pages:
  for b in pg['blocks']:
   if 'text' in b:
    b['text']=b['text'].replace('Version 2 detailed review draft','Version 3 post review draft').replace('Prepared 20 November 2026','Updated 21 November 2026').replace('V1-V4','V1-V5')
    b['text']=b['text'].replace('Forgot-password behavior was discussed, including single-use expiring links and a generic account-existence message, but no such flow is implemented.', 'Forgot password is implemented with expiring single-use links, hash storage, throttling and credential revision session revocation. SMTP is configurable; real inbox delivery remains unverified.')
    b['text']=b['text'].replace('No use case claims campus Google sign-in, real email delivery or password recovery because these are not implemented.', 'The diagram focuses on incident management. Forgot password now exists; SMTP is configurable but real delivery is unverified. Campus Google sign-in remains absent.')
    b['text']=b['text'].replace('remember-me, password recovery and campus switching were not added','remember-me and campus switching were not added')
    b['text']=b['text'].replace('There is no claimed latest-revision full-suite pass.', 'That earlier UI run did not establish a full-suite pass; the current 21 November Linux backend/full-suite result is stated above, while final browser acceptance remains open.')
    b['text']=b['text'].replace('This is a lightweight State implementation rather than a separate class for every state.', 'This is a table-driven state machine with one WorkflowState record type rather than separate per-state implementation classes.')
    b['text']=b['text'].replace('Source baseline: cec3bcfed486c9ffbd5b4ee4c2c70f96269bcb50.','Current reviewed implementation: 8292045fc1841e6d6dabbce847ba339d737b1aa0, including V5 recovery. Original report baseline: cec3bcfed486c9ffbd5b4ee4c2c70f96269bcb50.')
    b['text']=b['text'].replace('through V4__conversation_revision.sql','through V5__password_recovery.sql')
    b['text']=b['text'].replace('Refinement date: 20 November 2026.','Documentation reconciliation: 21 November 2026. Claude reviewed source statically only and did not rerun tests.')
 # Correct dictionary and current verification, preserving historical dated evidence.
 pages[28]['blocks'][0]['rows'][0][1]+='; credential_revision BIGINT'
 pages[28]['blocks'][0]['rows'][0][2]+=' Credential revision revokes sessions after reset.'
 pages[38]['blocks'].insert(2,P('<b>21 November current Linux run:</b> clean verify passed 220 regression and 2 packaged restart tests, plus 5 Python launcher cases. Count reconciliation: 198 + 4 extra template cases + 14 recovery integration + 4 delivery cases = 220. These are this project’s recorded results, not a Claude rerun or native Mac acceptance.'))
 # Include all previously omitted support tables and the credential column in the ER set.
 pages[31]={'title':'Migrations and support schema','blocks':[
  D('ersupport',275),C('Figure 9. Support schema completes the ER set. The reset-token user foreign key is enforced; hashed-address limits and the singleton gate have no account FK. Configuration and seed markers are independent.'),
  T(['Migration','Role'],[['V1','Identity, technician/catalog, SLA policies, audit and initial seeds.'],['V2','Incident workflow, configuration/skills, history, notifications and AI suggestions.'],['V3','demo_seed_markers initialization tracking.'],['V4','incidents.conversation_revision for fixed-clock versioning.'],['V5','user_accounts.credential_revision and all three password_reset_* tables.']],[65,422]),
  S('password_reset_tokens has a user index, expiry and unused used flag; consumption deletes rows. password_reset_limits records hashed-address cooldown timestamps. password_reset_gate stores id=1, window_start and requests. assignment_configuration stores id=1 and strategy; demo_seed_markers has name as primary key. Together Figures 7-9 cover all 20 tables.'),
  S('Migration seeds: Campus Wi-Fi 3; Campus login 3; Campus email 2; Lab computers 2; Printers 1. Categories: Network, Authentication, Hardware, Software, Email, Other. Default SLA rows are shown on page 19. Preserve published migrations and existing data; V5 adds recovery without reset/repair.')
 ]}
 for row in pages[2]['blocks'][0]['rows']:
  if row[2]=='32':row[3]='Migrations and support schema'
 # Concise report paragraph covers every requested finding, including separate privacy concern.
 pages[43]={'title':'Known limitations and release gates','blocks':[
 H('Known limitations'),P('Recovery holds the password_reset_gate row lock during synchronous SMTP delivery; slow mail delays other requests and real/unknown addresses have different timing. The global 20/min cap has no per-IP fairness and can deny service. Login/registration throttling or lockout and registration email verification are absent; duplicate registration explicitly reveals an existing address. Reset tokens are in URL query strings despite no-store/no-referrer mitigations. Delivery failures invalidate tokens but have no operator log; POSIX local mode fails on Windows and files are not auto-deleted. catalog.lock serializes writes globally; SlaMonitor loads all incidents every 30 seconds in one transaction. password_reset_tokens.used is never set because consumption deletes rows. Feedback uses a positional INSERT. AI sends incident text and past resolutions externally without redaction. Repeated-report correlation is absent; live AI and SMTP are unverified, and incident email remains simulated.'),
 H('Privacy issue needing separate code approval'),P('IncidentHistory.audit uses a denylist. Reporter-visible history can include free-text admin reasons and technician IDs. An explicit allowlist is a recommended follow-up, but was not implemented because this task authorizes documentation changes only. Review each reporter-visible event and field before approving that fix.'),
 T(['Release gate','Required evidence'],[['Team/report','Group number, member names/IDs and truthful contributions; team review.'],['Mac/browser','Run clean verify on the Mac and inspect actual role workflows; record failures honestly.'],['Recording','15-20 minute video, all members, three deterministic workflows.'],['Submission','Correct source link, instructor-confirmed filename year, upload destinations and receipts.']],[100,387]),
 S('Deadline: 22 November 2026; cutoff unknown. This report and the prepared demo script are not a recorded video or submission. The instructor filename question is drafted separately; no message was sent.')
 ]}
 for row in pages[2]['blocks'][0]['rows']:
  if row[2]=='44':row[3]='Known limitations and release gates'
