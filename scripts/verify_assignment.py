#!/usr/bin/env python3
"""Verify assignment data, native workbook charts/formulas, PDFs, assets, and Group 6 slide text."""
import csv
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path
import re
from xml.etree import ElementTree as ET
from zipfile import ZipFile

from openpyxl import load_workbook
from pypdf import PdfReader

ROOT = Path(__file__).resolve().parents[1]


def main():
    with (ROOT / 'mis_transactions_6months.csv').open(newline='') as source:
        ledger = list(csv.DictReader(source))
    assert len(ledger) == 66
    monthly, categories = {}, {}
    for row in ledger:
        month, currency, kind = row['Date'][:7], row['Currency'], row['Type']
        amount = Decimal(row['Amount'])
        key = (month, currency, kind)
        monthly[key] = monthly.get(key, Decimal(0)) + amount
        key = (row['Category'], currency, kind)
        total, count = categories.get(key, (Decimal(0), 0))
        categories[key] = (total + amount, count + 1)
    with (ROOT / 'mis_6month_summary.csv').open(newline='') as source:
        for row in csv.DictReader(source):
            if row['Section'] == 'Monthly Trend':
                month, currency = row['Period_Or_Category'], row['Currency']
                income, expense = monthly[(month, currency, 'Income')], monthly[(month, currency, 'Expense')]
                assert Decimal(row['Income_Or_Total']) == income
                assert Decimal(row['Expense_Or_Average']) == expense
                assert Decimal(row['Net_Or_Count']) == income - expense
                assert row['Rate_Or_Share_Percent'] == str(((income - expense) / income * 100).quantize(Decimal(1), rounding=ROUND_HALF_UP)) + '%'
            elif row['Section'] == 'Category Analysis':
                total, count = categories[(row['Period_Or_Category'], row['Currency'], row['Type'])]
                assert Decimal(row['Income_Or_Total']) == total
                assert int(row['Net_Or_Count']) == count
                assert Decimal(row['Expense_Or_Average']) == (total / count).quantize(Decimal('.01'), rounding=ROUND_HALF_UP)
    workbook = load_workbook(ROOT / 'MIS_Financial_Analysis.xlsx')
    cached = load_workbook(ROOT / 'MIS_Financial_Analysis.xlsx', data_only=True)
    assert workbook.sheetnames == ['0_Executive_Dashboard', '1_6Month_Trend_DSS', '2_Category_Breakdown', '3_TPS_Ledger_66_Rows']
    assert len(workbook.worksheets[0]._charts) == 6
    raw = cached['3_TPS_Ledger_66_Rows']
    for index, row in enumerate(ledger, 4):
        for column, key in enumerate(['Date', 'Type', 'Category', 'Account', 'Amount', 'Currency', 'Description'], 1):
            value = raw.cell(index, column).value
            assert Decimal(str(value)) == Decimal(row[key]) if key == 'Amount' else value == row[key]
    for cell, expected in [('B11', '11150'), ('C11', '6478'), ('D11', '4672'), ('B17', '3239.01'), ('B18', '2336'), ('B19', '324.75'),
                           ('F11', '9600000'), ('G11', '6600000'), ('H11', '3000000')]:
        assert Decimal(str(cached['1_6Month_Trend_DSS'][cell].value)) == Decimal(expected), cell
    assert all('SUMIFS' in workbook['1_6Month_Trend_DSS'][f'B{row}'].value for row in range(5, 11))
    assert all('COUNTIFS' in workbook['2_Category_Breakdown'][f'D{row}'].value for row in range(4, 9))
    for file in ['MIS_Financial_Analysis.xlsx', 'MIS_Assignment_Presentation.pptx', 'Group_6_MIS_Canva_Import.pptx']:
        with ZipFile(ROOT / file) as archive:
            assert archive.testzip() is None
            for name in archive.namelist():
                if name.endswith('.xml'):
                    ET.fromstring(archive.read(name))
    assert (ROOT / 'Group_6_MIS_Canva_Import.pptx').read_bytes() == (ROOT / 'MIS_Assignment_Presentation.pptx').read_bytes()
    with ZipFile(ROOT / 'MIS_Assignment_Presentation.pptx') as archive:
        slides = [name for name in archive.namelist() if re.fullmatch(r'ppt/slides/slide\d+.xml', name)]
        assert len(slides) == 14
        slide_text = ' '.join(node.text or '' for name in slides for node in ET.fromstring(archive.read(name)).iter() if node.tag.endswith('}t'))
    html_deck = (ROOT / 'MIS_PRESENTATION_DECK.html').read_text()
    for text in [slide_text, html_deck]:
        assert 'Group 6' in text and 'Group 5' not in text
        for stale in ['$21,150', '$6,753', '$14,396', '68.1%', 'PostgreSQL 16', 'V1–V6', 'Shopping hit']:
            assert stale not in text, stale
    report = PdfReader(ROOT / 'MIS_ASSIGNMENT_REPORT.pdf')
    presentation = PdfReader(ROOT / 'MIS_PRESENTATION_DECK.pdf')
    assert len(presentation.pages) == 14
    report_text = ' '.join(page.extract_text() or '' for page in report.pages)
    for required in ['Business Case', 'System Architecture', 'Decision Support', 'Excel', 'Process', '9,722', '4,672']:
        assert required in report_text, required
    assert 'placeholder' not in report_text.lower()
    for name in ['system-flow', 'database-erd', 'as-is', 'to-be']:
        ET.parse(ROOT / 'docs/submission' / (name + '.svg'))
    for name in ['accounts.png', 'ledger.png', 'dashboard.png']:
        assert (ROOT / 'docs/submission' / name).stat().st_size > 1000
    print(f'PASS: 66 source rows; monthly/category reconciliation; 4 worksheets; 6 native charts; 14 slides; {len(report.pages)} report pages; Group 6; Canva import copy; SVGs/screenshots.')


if __name__ == '__main__':
    main()
