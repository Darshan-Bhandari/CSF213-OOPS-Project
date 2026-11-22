# Duplicate incident correlation

## Matching and access

On submission, BITSOps checks up to 200 recent active reports for the same service and category from different reporters. The window is the preceding four hours. It normalizes title and description terms, including Wi-Fi spelling and phrases such as “cannot connect” and “outage”. A candidate must share at least two terms and score at least 30% overlap relative to the shorter set of distinct terms. The newest candidate wins a score tie. The best five matches are saved with the submitted report. This rule is intentionally conservative across services, categories and times. Similar wording can still produce a false positive. Suggestions are reviewed by a person.

Administrators can see and link both records across assignments. A technician must currently be assigned to both records. Campus Community retains access to its own reports; related reports and correlation audit entries are staff-only. Technicians see generic correlation audit text so a reassignment cannot expose another incident ID. Administrators can see the full link audit on both incidents.

A confirmed link marks one report as a duplicate of a master and keeps every incident record independent. Staff can choose an eligible suggestion or enter a master number manually. A report can have one master, and a master can have multiple reports. Chains, cycles, replacement links and new links to or from inactive incidents are rejected. Inactive here includes resolved, verified and closed. Existing links remain visible when an incident closes. Linking does not change reporter, state, priority, SLA deadlines or resolution history. OpenRouter is unnecessary.

Flyway V6 creates the candidate and duplicate-link tables, plus indexes for recent lookups and master membership. The matching audit is written when candidates are recorded. Staff confirmation and its two audit entries commit together. The existing write lock serializes confirmation with incident changes. Reports created before V6 can be linked manually; submission suggestions are generated for new reports.

## Demonstrate four Wi-Fi reports

1. Use the latest build and four different Campus Community accounts. Submit all four reports within four hours. Use **Campus Wi-Fi** as the service and **Network** as the category. Keep them active. Use a demo database or fictional accounts and descriptions.
2. Submit these title and description pairs in order.

   | Student | Title | Description |
   |---|---|---|
   | 1 | Wi-Fi is down | Campus Wi-Fi outage in the library. |
   | 2 | Cannot connect to wifi | Laptops cannot join wifi at the library. |
   | 3 | Wireless connection unavailable | Wifi is offline on every floor. |
   | 4 | Wi Fi not working | Phones lost access during the wifi outage. |

3. Note the first incident ID. Sign in as Admin and open reports 2, 3 and 4. In **Incident correlation**, find the first report under **Potential duplicates at submission**. Show the displayed ID, status, description and text overlap. Click **Use INC-… as master** on that candidate for each report.
4. Open the first incident and show all three reports under **Linked incidents**. Open a linked report and show the master reference. Show the staff audit entry on the master and at least one linked report. Switch back to a student account and show that the student's own report still exists with its current status and history.
