# Reference-inspired interface — 19 November 2026

User supplied a lavender BITS Pilani / BITSOps Assistant sign-in and dashboard reference and requested the application use that style. This supersedes the earlier teal visual direction. The interface retains Campus Community, Technician and Admin and their approved permissions.

Implemented shared styling: lavender gradient canvas, floating white/lavender surfaces, purple selected navigation, rounded metric cards, SVG line icons, responsive tables/forms, accessible focus styles and reduced-motion support. The shared top bar includes working GET ticket search, campus identity, notification link, profile initial and mobile sign-out. The navigation button collapses the desktop sidebar or opens the mobile drawer; Escape closes the mobile drawer. Resizing resets drawer/collapse state.

Sign-in now has campus branding, purple/blue sign-in button, email/password icons, a password visibility toggle and a desktop welcome panel. Existing form action, email/password fields, CSRF integration and error/registration/logout messages are retained. The Campus Community registration link remains functional. Google SSO, password recovery, remember-me, campus switching, tasks/assets/knowledge-base modules are not implemented and were not added as nonfunctional controls from the reference.

Dashboard retains real existing metric definitions and filters. Recent Highlights selects up to four recent tickets through the existing role-scoped filtered query; title, service/category, priority, state and IST creation time come from stored data. Empty workspaces show a real empty state. No sample percentages, active-user counts, trends, incidents or task-completion figures are presented as live data. Adding the read projection does not introduce an AI provider call.

## Assets and attribution

Local SVG line icons are project-authored in `src/main/resources/static/images/icons.svg`. The public `/images/**` static path permits anonymous access to decorative assets, matching existing CSS/JS handling; application routes retain their authentication rules.

The BITS crest is linked from `https://upload.wikimedia.org/wikipedia/en/d/d3/BITS_Pilani-Logo.svg`. It identifies the educational project setting; no institutional SSO/endorsement is claimed. The campus photograph is Muralikrishna m's **BITS, Pilani – Hyderabad Campus.jpg**, Wikimedia Commons, CC BY-SA 4.0: https://commons.wikimedia.org/wiki/File:BITS,_Pilani_%E2%80%93_Hyderabad_Campus.jpg . License: https://creativecommons.org/licenses/by-sa/4.0/ . The sign-in page includes visible author/license links. CSS uses a background-sized display and gradient overlay; no source-image file was modified.

The crest/photo are externally hosted and require network access. Text branding and a monogram fallback remain available if the crest fails; the photo panel has a lavender fallback. No remote fonts, icon packages or frontend build service were added.

## Verification and limits

Compilation plus 77 existing rules/template cases passed after the main implementation (56 rules, 21 templates). With populated-dashboard escaping added, all **22** template cases passed; there are now 78 distinct rules/template cases covered across those runs. Login, registration, all-role dashboards, administrator directory, incident forms/details and histories render under Thymeleaf. This does not certify pixel layout, JavaScript interactions, database runtime or browser network assets.

The optional `-Dapp.visual-export=true` test flag exports actual rendered administrator fixtures under ignored `.local/ui-preview/` for manual preview. Fixtures contain test data, not a signed-in live application. Browser preview was blocked by the browser's URL policy rejecting the local file protocol. No workaround was attempted; no screenshot or successful visual/interaction inspection is claimed. Existing database/shared-memory/socket restrictions remain documented in `docs/testing.md`.

Review actual desktop/mobile rendering in a browser-capable normal development environment before marking visual acceptance complete. Native Windows/Safari/Firefox/full accessibility/live AI/full final demonstration gates remain separate.


## Account dropdown 21 November 2026

User approved making the avatar interactive. Native details/summary opens a panel with escaped current account display name, email and displayed role, plus the existing POST logout/CSRF integration. Keyboard activation is native; Escape restores focus to the summary and outside click closes the panel. Mobile styles retain avatar access and bound panel width. The existing sidebar/mobile logout remains available. Forgot password was subsequently removed by explicit user instruction. Current verification is in docs/testing.md; no live browser pass is implied.
