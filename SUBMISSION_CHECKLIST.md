# Group 6 — MIS submission package

Lecturer: Pay Laten. Requirements source: **Assigment Flow.pdf**, pages 2–6.

## What to submit

| Requirement | Completed evidence |
|---|---|
| 1. Business case: TPS/MIS/DSS, problems and recommendations | `MIS_ASSIGNMENT_REPORT.pdf`, Part 1 |
| 2. System design: system flowchart and database tables | Part 2 and rendered `docs/submission/system-flow.svg`, `database-erd.svg` |
| 3. Decision support: data-backed managerial decisions | Part 3, slide 12, and the workbook decision formulas |
| 4. Excel/data analysis: charts, dashboard, reports and trends | `MIS_Financial_Analysis.xlsx`: 4 worksheets and 6 native Excel charts |
| 5. As-Is/To-Be process mapping | Part 5 and rendered `docs/submission/as-is.svg`, `to-be.svg` |

The finance/advisory company is a simulated case. Vehicle rental is an example
in the lecturer's document, not an explicit topic restriction.

### Ready-to-open files

- **Report:** `MIS_ASSIGNMENT_REPORT.pdf` (14 pages); editable Markdown and HTML sources remain available.
- **Excel:** `MIS_Financial_Analysis.xlsx`; start at `0_Executive_Dashboard`.
- **Presentation:** `MIS_Assignment_Presentation.pptx` (14 slides, Group 6).
- **Canva import:** `Group_6_MIS_Canva_Import.pptx` (same content, clear import filename).
- **Presentation preview:** `MIS_PRESENTATION_DECK.pdf` (14 pages, exported from the matching HTML deck).
- **Interactive/source deck:** `MIS_PRESENTATION_DECK.html`.
- **Source data:** `mis_transactions_6months.csv` and `mis_6month_summary.csv`.
- **Actual app screenshots and diagrams:** `docs/submission/`.

## Import into Canva

1. Sign in to Canva and choose its file-import/upload option.
2. Select **`Group_6_MIS_Canva_Import.pptx`**.
3. Open the imported presentation and check fonts, text wrapping, diagrams, and photos.
4. Use the PDF preview to compare content. The PowerPoint and HTML preview use
   their respective existing layouts, so they need not look pixel-identical.

This is an importable PowerPoint, not a native Canva design link. Native text and
shapes remain editable where supported by Canva; embedded images remain images.
Import into the user's Canva account has not been verified here.

## One consistent classroom dataset

The canonical source contains **66 synthetic May–October 2026 entries**.

| Currency | Income | Expense | Net | Rounded savings rate |
|---|---:|---:|---:|---:|
| USD | 11,150.00 | 6,478.00 | 4,672.00 | 42% |
| KHR | 9,600,000.00 | 6,600,000.00 | 3,000,000.00 | 31% |

No conversion or mixed-currency total is used. Original workbook source rows are
preserved. Monthly/category summaries use SUMIFS/COUNTIFS against the ledger,
and the six native charts reference those cells. Formula/chart caches allow
initial display before recalculation. Full recalculation is requested on opening.

**Scenario assumptions (not facts inferred from the CSV):**

- Opening USD funds: 5,050.00; closing funds: 9,722.00.
- October Food & Dining budget: 380.00 vs. actual 425.00 (111.8% utilization).
- A 15% food reduction saves 324.75 over six months.
- Reserve target: round the monthly USD expense average to 1,079.67, then multiply
  by 3 = 3,239.01, matching the app's rounding convention.
- A 50% surplus allocation is 2,336.00; a hypothetical 5.5% APY is illustrative,
  not an offered product, guaranteed return, or financial advice.
- Screenshot-only opening KHR funds: 12,000,000; closing funds: 15,000,000.

Application screenshots are real captures from an isolated synthetic-data database.
The app uses a current-date rolling six-month window; the fixed Excel case stays
May–October 2026. Captions explain the distinction. No private user database was seeded.

## Verification and rebuild

External Python tooling: `openpyxl`, `playwright` with Chromium, and `pypdf`.
No application runtime dependency was added.

```bash
python scripts/verify_assignment.py
```

For regeneration, use the same separate Python environment:

```bash
python scripts/build_assignment_workbook.py --rebuild-dashboard
python scripts/reconcile_assignment_slides.py
python scripts/capture_assignment_evidence.py --mermaid /path/to/mermaid-10.9.3.min.js
```

The workbook script creates an external backup before editing. Its rebuild flag
explicitly replaces only the generated dashboard. The capture script uses a
disposable PostgreSQL container and random loopback app port; it requires the built JAR.
If the PowerPoint source changes, refresh the Canva copy from it before validation.

Workbook ZIP/XML, cached formula totals, original source rows, charts, slide XML,
PDF page/text content, and rendered evidence are checked by the verification script.
Screenshots of the HTML slides were reviewed, including corrected metrics and
process diagrams. Native Excel/PowerPoint applications and Canva are not installed/
connected here, so final Office/Canva rendering should be checked on the submission machine.

Existing user work was preserved, with external backups of the original deliverables.
Group 6 follows the user's latest correction. No unmeasured speed or drift percentage
is presented as a benchmark result.
