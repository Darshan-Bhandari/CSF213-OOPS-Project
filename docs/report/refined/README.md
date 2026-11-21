> Current product change 21 November 2026: Forgot password has been REMOVED at user request. The existing PDF/earlier recovery discussions are historical and need final reconciliation; preserved V5 schema is inactive compatibility data. See current plan.md and MAC_START_HERE.md.

> Updated 21 November 2026 after independent static review: the current PDF includes V5 support schema, credential_revision, current Linux result ledger and known limitations. No application behavior changed. Team identities and Mac verification remain pending.

# Detailed report source

This edition supersedes the initial 17-page draft. It contains 45 pages, nine vector diagrams and 24 documented test scenarios. Group identity and actual contribution fields remain deliberately unfilled.

The PDF is available as [BITSOps_Project_Report_Refined.pdf](https://chatgpt.com/library?file_id=libfile_1ec8ccca66e881918c2a72a70915e274&account_id=personal).

## Rebuild

Use Python 3 with ReportLab installed. From the repository root:

```bash
python3 docs/report/refined/author_content.py
python3 docs/report/refined/render_report.py
```

The first script writes structured content.json. The second writes the PDF under artifacts and an editable prose projection in report.md. Figures are vector drawing code in render_report.py; they do not require Graphviz or external images. The source JSON and Markdown use the same prose as the PDF.

## Verification and scope

The 45-page PDF was rendered and reviewed. Pagination, expected page titles, contents and text bounds were checked; history diagram routing was refined. These are document checks, not fresh application acceptance results. Historical tests and remaining live AI, correlation, browser and platform gates are explicitly distinguished. No application source, migration or user database is changed by report generation.

Changing a page's content can overflow its frame. Regenerate, check the resulting page count and contents, and visually review every affected page before replacing the deliverable. Footer/contents targets assume the current 45-page layout.
