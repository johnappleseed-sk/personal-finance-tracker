#!/usr/bin/env python3
"""Complete the existing assignment workbook without dropping its original sheets.

Requires openpyxl in a separate Python environment. The canonical dataset is
mis_transactions_6months.csv. Original deliverables are backed up outside the repo.
"""
import ast
import argparse
import csv
from datetime import datetime
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path
import re
import shutil
import tempfile
from xml.etree import ElementTree as ET
from zipfile import ZipFile, ZIP_DEFLATED

from openpyxl import load_workbook
from openpyxl.chart import BarChart, DoughnutChart, LineChart, Reference
from openpyxl.chart.label import DataLabelList
from openpyxl.styles import Alignment, Font, PatternFill
from openpyxl.workbook.properties import CalcProperties
from openpyxl.utils.cell import range_boundaries

ROOT = Path(__file__).resolve().parents[1]
FILE = ROOT / 'MIS_Financial_Analysis.xlsx'
SUMMARY = '1_6Month_Trend_DSS'
CATEGORY = '2_Category_Breakdown'
LEDGER = '3_TPS_Ledger_66_Rows'
DASHBOARD = '0_Executive_Dashboard'
S = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'
C = 'http://schemas.openxmlformats.org/drawingml/2006/chart'


def backup():
    folder = Path(tempfile.mkdtemp(prefix='mis-submission-backup-', dir=Path(tempfile.gettempdir()) / 'opencode'))
    for path in ROOT.glob('MIS*'):
        if path.is_file():
            shutil.copy2(path, folder / path.name)
    print('Original deliverables backed up to:', folder)
    return folder


def make_chart(kind, title, source, columns, start, end, category_column=1, percent=False):
    chart = kind()
    chart.title = title
    chart.style = 13
    chart.width, chart.height = 20, 10
    chart.add_data(Reference(source, min_col=columns[0], max_col=columns[-1], min_row=start - 1, max_row=end), titles_from_data=True)
    chart.set_categories(Reference(source, min_col=category_column, min_row=start, max_row=end))
    if hasattr(chart, 'y_axis'):
        chart.y_axis.title = 'Percent (rounded)' if percent else ('KHR' if 'KHR' in title else 'USD')
        chart.y_axis.scaling.min = 0
        if percent:
            chart.y_axis.scaling.max = 100
    if kind == BarChart:
        chart.type = 'col'
        chart.grouping = 'clustered'
    if kind == DoughnutChart:
        chart.holeSize = 60
        chart.dataLabels = DataLabelList()
        chart.dataLabels.showPercent = True
    return chart


class FormulaEvaluator:
    """Evaluate only the small, explicitly supported formula language used by this workbook."""
    reference = re.compile(r"(?:'([^']+)'!)?(\$?[A-Z]{1,3}\$?\d+)(?::(\$?[A-Z]{1,3}\$?\d+))?")

    def __init__(self, workbook):
        self.workbook = workbook
        self.cache = {}

    def cell(self, sheet, address):
        key = (sheet, address.replace('$', ''))
        if key in self.cache:
            return self.cache[key]
        value = self.workbook[sheet][key[1]].value
        if isinstance(value, str) and value.startswith('='):
            result = self.expression(sheet, value[1:])
        elif isinstance(value, (int, float, Decimal)):
            result = Decimal(str(value))
        else:
            result = value or ''
        self.cache[key] = result
        return result

    def expression(self, sheet, expression):
        def replace(match):
            target = match.group(1) or sheet
            first = match.group(2).replace('$', '')
            last = match.group(3)
            return repr((target, first, last.replace('$', '') if last else None))
        # Preserve quoted criteria, replacing references only outside double-quoted strings.
        parts = re.split(r'(".*?")', expression)
        expression = ''.join(part if i % 2 else self.reference.sub(replace, part) for i, part in enumerate(parts))
        return self.node(ast.parse(expression, mode='eval').body)

    def node(self, node):
        if isinstance(node, ast.Constant):
            return Decimal(str(node.value)) if isinstance(node.value, (int, float)) else node.value
        if isinstance(node, ast.Tuple):
            sheet, first, last = [self.node(n) for n in node.elts]
            if not last:
                return self.cell(sheet, first)
            x1, y1, x2, y2 = range_boundaries(first + ':' + last)
            from openpyxl.utils import get_column_letter
            return [self.cell(sheet, get_column_letter(x) + str(y)) for y in range(y1, y2 + 1) for x in range(x1, x2 + 1)]
        if isinstance(node, ast.BinOp):
            a, b = self.node(node.left), self.node(node.right)
            operations = {ast.Add: lambda: a + b, ast.Sub: lambda: a - b,
                          ast.Mult: lambda: a * b, ast.Div: lambda: a / b}
            return operations[type(node.op)]()
        if isinstance(node, ast.UnaryOp) and isinstance(node.op, ast.USub):
            return -self.node(node.operand)
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Name):
            args = [self.node(n) for n in node.args]
            name = node.func.id
            if name == 'ROUND':
                return args[0].quantize(Decimal(1).scaleb(-int(args[1])), rounding=ROUND_HALF_UP)
            if name in ('SUM', 'COUNT', 'AVERAGE'):
                values = [item for arg in args for item in (arg if isinstance(arg, list) else [arg]) if isinstance(item, Decimal)]
                if name == 'COUNT':
                    return Decimal(len(values))
                return sum(values, Decimal(0)) / (len(values) if name == 'AVERAGE' else 1)
            if name in ('SUMIFS', 'COUNTIFS'):
                values, conditions = (args[0], args[1:]) if name == 'SUMIFS' else (None, args)
                groups = list(zip(conditions[::2], conditions[1::2]))
                flags = [all(self.matches(column[i], criterion) for column, criterion in groups) for i in range(len(groups[0][0]))]
                return sum((values[i] if values is not None else Decimal(1) for i, match in enumerate(flags) if match), Decimal(0))
        raise ValueError('Unsupported workbook formula: ' + ast.dump(node))

    @staticmethod
    def matches(value, criterion):
        if isinstance(criterion, str) and criterion.endswith('*'):
            return str(value).startswith(criterion[:-1])
        if isinstance(criterion, str) and criterion.startswith('>='):
            return str(value) >= criterion[2:]
        if isinstance(criterion, str) and criterion.startswith('<'):
            return str(value) < criterion[1:]
        return value == criterion


def add_cached_values(workbook):
    """Keep formula/chart cached values visible before a recipient first recalculates in Excel."""
    evaluator = FormulaEvaluator(workbook)
    with ZipFile(FILE) as source:
        contents = {name: source.read(name) for name in source.namelist()}
    for index, sheet in enumerate(workbook, 1):
        path = f'xl/worksheets/sheet{index}.xml'
        root = ET.fromstring(contents[path])
        for cell in root.findall(f'.//{{{S}}}c'):
            if cell.find(f'{{{S}}}f') is None:
                continue
            result = evaluator.cell(sheet.title, cell.get('r'))
            value = cell.find(f'{{{S}}}v')
            if value is None:
                value = ET.SubElement(cell, f'{{{S}}}v')
            value.text = str(result)
        contents[path] = ET.tostring(root, encoding='utf-8', xml_declaration=True)
    for name in list(contents):
        if not name.startswith('xl/charts/chart') or not name.endswith('.xml'):
            continue
        root = ET.fromstring(contents[name])
        for reference in list(root.iter(f'{{{C}}}numRef')) + list(root.iter(f'{{{C}}}strRef')):
            formula = reference.find(f'{{{C}}}f').text
            sheet, addresses = formula.rsplit('!', 1)
            sheet = sheet.strip("'")
            x1, y1, x2, y2 = range_boundaries(addresses)
            from openpyxl.utils import get_column_letter
            values = [evaluator.cell(sheet, get_column_letter(x) + str(y)) for y in range(y1, y2 + 1) for x in range(x1, x2 + 1)]
            numeric = all(isinstance(value, Decimal) for value in values)
            if not numeric:
                reference.tag = f'{{{C}}}strRef'
            cache = ET.SubElement(reference, f'{{{C}}}numCache' if numeric else f'{{{C}}}strCache')
            ET.SubElement(cache, f'{{{C}}}ptCount', {'val': str(len(values))})
            for i, value in enumerate(values):
                point = ET.SubElement(cache, f'{{{C}}}pt', {'idx': str(i)})
                ET.SubElement(point, f'{{{C}}}v').text = str(value)
        contents[name] = ET.tostring(root, encoding='utf-8', xml_declaration=True)
    with ZipFile(FILE, 'w', ZIP_DEFLATED) as target:
        for name, content in contents.items():
            target.writestr(name, content)
    return evaluator


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rebuild-dashboard', action='store_true',
                        help='Explicitly replace only the dashboard generated by this script; back up first')
    args = parser.parse_args()
    backup()
    with (ROOT / 'mis_transactions_6months.csv').open(newline='') as source:
        rows = list(csv.DictReader(source))
    assert len(rows) == 66
    workbook = load_workbook(FILE)
    if DASHBOARD in workbook:
        if not args.rebuild_dashboard:
            raise SystemExit('Dashboard exists; use --rebuild-dashboard explicitly to regenerate it after a backup.')
        assert workbook[DASHBOARD]['A1'].value == 'PERSONAL FINANCE | EXECUTIVE EXCEL DASHBOARD'
        workbook.remove(workbook[DASHBOARD])
    ledger = workbook[LEDGER]
    assert ledger.max_row == 70
    for i, row in enumerate(rows, 4):
        assert str(ledger[f'A{i}'].value) == row['Date']
        assert Decimal(str(ledger[f'E{i}'].value)) == Decimal(row['Amount'])
        assert ledger[f'F{i}'].value == row['Currency']
    trend, categories = workbook[SUMMARY], workbook[CATEGORY]
    raw = "'" + LEDGER + "'!"
    amounts, types, currencies, dates, names = [raw + '$' + col + '$4:$' + col + '$69' for col in ['E', 'B', 'F', 'A', 'C']]
    for row in range(5, 11):
        month = str(trend[f'A{row}'].value)
        for col, currency, kind in [('B', 'USD', 'Income'), ('C', 'USD', 'Expense'), ('F', 'KHR', 'Income'), ('G', 'KHR', 'Expense')]:
            # The preserved source ledger stores ISO dates as text. Prefix matching works in
            # native Excel without depending on locale-specific coercion to numeric date serials.
            trend[f'{col}{row}'] = f'=SUMIFS({amounts},{currencies},"{currency}",{types},"{kind}",{dates},"{month}*")'
    for row in [4, 5, 6, 7, 8, 11, 12, 15, 16, 17]:
        category = categories[f'A{row}'].value.replace(' (KHR)', '')
        kind, currency = categories[f'B{row}'].value, categories[f'C{row}'].value
        criteria = f'{names},"{category}",{types},"{kind}",{currencies},"{currency}"'
        categories[f'D{row}'] = '=COUNTIFS(' + criteria + ')'
        categories[f'E{row}'] = f'=SUMIFS({amounts},' + criteria + ')'
    # Total average/entry must divide by transaction count, not average category subtotals.
    for row in [9, 13, 18]:
        categories[f'F{row}'] = f'=E{row}/D{row}'
    trend['B17'] = '=ROUND(C12,2)*3'
    trend['C17'] = 'Illustrative 3-month reserve; round monthly average to cents first, matching the application.'
    trend['C18'] = 'Scenario only: allocate 50% of net surplus after retaining the reserve. 5.5% APY is hypothetical.'
    trend['B19'] = f"='{CATEGORY}'!E5*0.15"
    dashboard = workbook.create_sheet(DASHBOARD, 0)
    dashboard.sheet_view.showGridLines = False
    dashboard.sheet_properties.pageSetUpPr.fitToPage = True
    dashboard.page_setup.orientation = 'landscape'
    dashboard.page_setup.paperSize = dashboard.PAPERSIZE_A3
    dashboard.page_setup.fitToWidth, dashboard.page_setup.fitToHeight = 1, 2
    dashboard.print_options.horizontalCentered = True
    dashboard.print_area = 'A1:T74'
    dashboard.freeze_panes = 'A9'
    dashboard.sheet_properties.tabColor = '4338CA'
    for col in range(1, 21):
        dashboard.column_dimensions[__import__('openpyxl').utils.get_column_letter(col)].width = 10
    dashboard.merge_cells('A1:T2')
    dashboard['A1'] = 'PERSONAL FINANCE | EXECUTIVE EXCEL DASHBOARD'
    dashboard['A1'].font = Font(name='Calibri', size=22, bold=True, color='FFFFFF')
    dashboard['A1'].fill = PatternFill('solid', fgColor='312E81')
    dashboard.merge_cells('A3:T3')
    dashboard['A3'] = 'MIS assignment • May–October 2026 • 66 synthetic ledger entries • USD and KHR kept separate'
    dashboard['A3'].font = Font(size=12, color='475569')
    for start, end, label, formula, number_format in [
        ('A5', 'E5', 'USD INCOME', f"='{SUMMARY}'!B11", '#,##0.00'),
        ('F5', 'J5', 'USD EXPENSE', f"='{SUMMARY}'!C11", '#,##0.00'),
        ('K5', 'O5', 'USD NET SAVINGS', f"='{SUMMARY}'!D11", '#,##0.00'),
        ('P5', 'T5', 'USD SAVINGS RATE', f"='{SUMMARY}'!E11", '0"%"')]:
        dashboard.merge_cells(start + ':' + end)
        dashboard[start] = label
        dashboard[start].font = Font(size=10, bold=True, color='475569')
        first, last = start.replace('5', '6'), end.replace('5', '7')
        dashboard.merge_cells(first + ':' + last)
        dashboard[first] = formula
        dashboard[first].font = Font(size=24, bold=True, color='312E81')
        dashboard[first].number_format = number_format
    for row, text in {
        68: 'DECISION 1 | USD 2,336.00 = 50% of net surplus; retain a USD 3,239.01 reserve before considering investment.',
        69: 'DECISION 2 | Food & Dining is the controllable cost: a 15% cut saves USD 324.75 over six months.',
        70: 'BUDGET SCENARIO | October Food & Dining: USD 425 actual / USD 380 assumed limit = 111.8% utilization.',
        71: 'DATA | All charts reference original sheets; monthly/category formulas link to the 66-row source ledger.',
        72: 'METHOD | Income/expense columns, net/savings-rate lines, category bars/donut. No currency conversion or mixed totals.',
        73: 'ASSUMPTIONS | Opening USD balance 5,050; no transfers in this dataset; closing USD balance 9,722.',
        74: 'CAUTION | Synthetic classroom scenario, not financial advice. Live application charts use a rolling six-month window.',
    }.items():
        dashboard.merge_cells(start_row=row, start_column=1, end_row=row, end_column=20)
        dashboard.cell(row, 1, text).font = Font(size=11, color='334155')
        dashboard.cell(row, 1).alignment = Alignment(wrap_text=True, vertical='center')
        dashboard.row_dimensions[row].height = 28
    charts = [
        (make_chart(BarChart, 'USD income vs. expenses', trend, [2, 3], 5, 10), 'A9'),
        (make_chart(LineChart, 'USD net cash flow', trend, [4], 5, 10), 'K9'),
        (make_chart(DoughnutChart, 'USD expense composition', categories, [5], 4, 8), 'A28'),
        (make_chart(LineChart, 'USD savings rate (%)', trend, [5], 5, 10, percent=True), 'K28'),
        (make_chart(BarChart, 'KHR income vs. expenses', trend, [6, 7], 5, 10), 'A47'),
    ]
    category_chart = make_chart(BarChart, 'USD category spending', categories, [5], 4, 8)
    category_chart.type = 'bar'
    category_chart.x_axis.title = 'USD'
    category_chart.y_axis.title = 'Category'
    charts.append((category_chart, 'K47'))
    for chart, location in charts:
        dashboard.add_chart(chart, location)
    workbook.calculation = CalcProperties(calcId=191029, fullCalcOnLoad=True, forceFullCalc=True)
    workbook.active = 0
    workbook.save(FILE)
    evaluator = add_cached_values(workbook)
    assert evaluator.cell(SUMMARY, 'B11') == Decimal('11150')
    assert evaluator.cell(SUMMARY, 'C11') == Decimal('6478')
    assert evaluator.cell(SUMMARY, 'D11') == Decimal('4672')
    assert evaluator.cell(SUMMARY, 'B17') == Decimal('3239.01')
    with ZipFile(FILE) as archive:
        assert len([name for name in archive.namelist() if re.fullmatch(r'xl/charts/chart\d+.xml', name)]) == 6
    # Open independently with cached-value mode so formulas, cached values and chart XML are all checked.
    cached = load_workbook(FILE, data_only=True)
    assert cached[DASHBOARD]['A6'].value == 11150
    assert cached[SUMMARY]['F11'].value == 9600000
    assert cached[SUMMARY]['G11'].value == 6600000
    assert len(load_workbook(FILE)[DASHBOARD]._charts) == 6
    print('PASS: four worksheets, six native Excel charts, live source-ledger formulas, cached values, and separate USD/KHR totals.')


if __name__ == '__main__':
    main()
