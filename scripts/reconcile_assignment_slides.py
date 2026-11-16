#!/usr/bin/env python3
"""Reconcile existing slide text to the canonical CSV without rebuilding layouts or media."""
from pathlib import Path
import html as html_module
import re
from zipfile import ZipFile, ZIP_DEFLATED
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

REPLACEMENTS = {
    'Group 5': 'Group 6',  # User's latest correction: Group 6.
    'PostgreSQL 16': 'PostgreSQL 18',
    'POSTGRESQL 16': 'POSTGRESQL 18',
    'V1–V6': 'V1–V5',
    'Role-based access': 'Owner-scoped access',
    '15–20% balance drift': 'inconsistent balances',
    'causes 15–20% balance drift': 'can cause inconsistent balances',
    'Live MIS (<50ms)': 'Live MIS aggregation',
    'Live MIS (&lt;50ms)': 'Live MIS aggregation',
    "'AS-IS' MANUAL EXCEL (~4 hrs/month)": "'AS-IS' MANUAL EXCEL",
    "'TO-BE' AUTOMATED MIS (< 5 seconds)": "'TO-BE' AUTOMATED MIS",
    'LIVE MIS & DSS WORKSPACE SNAPSHOT (6-MONTH DATASET)': 'FIXED MAY–OCT 2026 CASE ILLUSTRATION (66 SYNTHETIC ENTRIES)',
    '$21,150.00': '$11,150.00',
    '$21,150': '$11,150',
    '$6,753.75': '$6,478.00',
    '$6,754': '$6,478',
    '$14,396.25': '$4,672.00',
    '$14,396': '$4,672',
    '68.1%': '42%',
    '53.3%': '42%',
    '12.1%': '33%',
    'Travel (8.1%)': 'Transport (10%)',
    '$3,525/mo': '$1,858.33/mo',
    '56 transactions': '30 USD expense entries',
    '+24.3M KHR net': '+3.0M KHR net',
    'Housing $3,600': 'Housing $2,700',
    'Housing 42% ($3,600)': 'Housing 42% ($2,700)',
    'Food 33% ($818.50)': 'Food 33% ($2,165)',
    'Shopping $285 / $300 limit': 'Food $425 / $380 assumed cap',
    'Shopping at 95.0% ($285/$300) Near Limit': 'Food at 111.8% ($425/$380 assumed cap) Over Budget',
    'USD Wallets ($22,350) & KHR Wallets (42.8M KHR)': 'Separate totals: USD $9,722; KHR net +3.0M (no FX conversion)',
    'Shopping hit 95% ($285/$300) and Food hit 82.2% of October budget.': 'Food reached 111.8% of its assumed October cap ($425/$380).',
    'Freeze Shopping (+$220/mo saved), sweep $1,500/mo into 5.5% APY deposit (+$990/yr), and keep 4.5M KHR liquid reserve.': 'Cut Food by 15% ($324.75/six months); consider $2,336 surplus allocation only after retaining $3,239.01 USD reserve. Classroom assumptions, not financial advice.',
    'Saved $14.4k': 'Saved $4.7k',
    'Spent $6.8k': 'Spent $6.5k',
}


def replace(text):
    for before, after in REPLACEMENTS.items():
        text = text.replace(before, after)
    return text


def main():
    html = ROOT / 'MIS_PRESENTATION_DECK.html'
    text = replace(html.read_text())
    # The HTML deck uses formatted fragments around these scenario figures.
    text = text.replace('95.0%', '111.8%').replace('>Shopping</text>', '>Food</text>')
    text = text.replace('stroke-dasharray="47 88"', 'stroke-dasharray="36.9 88"')
    text = text.replace('stroke-dasharray="83 88"', 'stroke-dasharray="88 88"')
    text = text.replace('stroke-dasharray="60 88"', 'stroke-dasharray="36.9 88"')
    text = text.replace('''<strong>Shopping hit 95% ($285/$300)</strong> and <strong>Food &amp; Dining hit 82.2%</strong> of October budget.''',
                        '''<strong>Food &amp; Dining reached 111.8%</strong> of its assumed October cap (<strong>$425/$380</strong>).''')
    text = text.replace('''Freeze non-essential Shopping (+<strong>$220/mo</strong> saved), sweep <strong>$1,500/mo</strong> into 5.5% APY Term Deposit (+<strong>$990/yr</strong>), and hold a <strong>4.5M KHR</strong> reserve.''',
                        '''Cut Food by 15% (<strong>$324.75/six months</strong>); consider <strong>$2,336</strong> allocation only after retaining a <strong>$3,239.01 USD</strong> reserve. Classroom assumptions, not financial advice.''')
    text = text.replace('Workspace / MIS &amp; DSS Analytics', 'Fixed May–Oct 2026 case illustration')
    text = text.replace('Period: 6 Months', '66 synthetic entries')
    text = text.replace('🇰🇭 ខ្មែរ / EN', 'ខ្មែរ / EN')
    text = text.replace('~4 hrs / month', 'Delayed review').replace('&lt; 5 seconds', 'After valid entry')
    html.write_text(text)
    pptx = ROOT / 'MIS_Assignment_Presentation.pptx'
    with ZipFile(pptx) as source:
        entries = {name: source.read(name) for name in source.namelist()}
    changed = 0
    for name, data in list(entries.items()):
        if not (name.startswith('ppt/slides/slide') or name.startswith('ppt/notesSlides/notesSlide')) or not name.endswith('.xml'):
            continue
        def revise_run(match):
            nonlocal changed
            original = html_module.unescape(match.group(2))
            revised = replace(original)
            if revised == original:
                return match.group(0)
            changed += 1
            return match.group(1) + html_module.escape(revised, quote=False) + match.group(3)
        # Preserve original namespace declarations, shape markup, relationships and run styling.
        revised_xml = re.sub(r'(<a:t(?:\s[^>]*)?>)(.*?)(</a:t>)', revise_run, data.decode('utf-8'), flags=re.S)
        ET.fromstring(revised_xml)
        entries[name] = revised_xml.encode('utf-8')
    with ZipFile(pptx, 'w', ZIP_DEFLATED) as target:
        for name, content in entries.items():
            target.writestr(name, content)
    print(f'Reconciled HTML deck and {changed} PowerPoint text runs. Slide layouts and media retained.')


if __name__ == '__main__':
    main()
