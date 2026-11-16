#!/usr/bin/env python3
"""Render assignment diagrams, capture real synthetic-data app screens, and export the report PDF.

Requires Python Playwright/Chromium, Docker, Java, the built JAR, and a local
mermaid@10.9.3 UMD bundle supplied through --mermaid. Normal user data is never used.
"""
import argparse
import csv
from datetime import date
import json
import os
from pathlib import Path
import re
import secrets
import socket
import subprocess
import time
import urllib.request

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'docs/submission'


def quote(value):
    return "'" + str(value).replace("'", "''") + "'"


def render_report(browser):
    page = browser.new_page(viewport={'width': 1440, 'height': 1000})
    page.goto((ROOT / 'MIS_ASSIGNMENT_REPORT.html').as_uri())
    page.wait_for_load_state('networkidle')
    assert page.locator('figure img').count() == 7
    assert page.locator('figure img').evaluate_all('(images) => images.every(i => i.complete && i.naturalWidth > 0)')
    page.pdf(path=str(ROOT / 'MIS_ASSIGNMENT_REPORT.pdf'), format='A4', print_background=True,
             margin={'top': '12mm', 'bottom': '12mm', 'left': '12mm', 'right': '12mm'})
    page.close()
    deck = browser.new_page(viewport={'width': 1600, 'height': 1000})
    deck.goto((ROOT / 'MIS_PRESENTATION_DECK.html').as_uri())
    deck.wait_for_load_state('networkidle')
    slides = deck.locator('.slide')
    assert slides.count() == 14
    for i in range(slides.count()):
        deck.evaluate("""(index) => document.querySelectorAll('.slide').forEach((slide, i) => {
            slide.classList.toggle('active', i === index);
        })""", i)
        slides.nth(i).screenshot(path=str(OUTPUT / f'slide-{i + 1:02d}.png'), animations='disabled')
    deck.pdf(path=str(ROOT / 'MIS_PRESENTATION_DECK.pdf'), width='13.333in', height='7.5in',
             print_background=True, margin={'top': '0', 'bottom': '0', 'left': '0', 'right': '0'})
    deck.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mermaid', type=Path, required=True)
    args = parser.parse_args()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    blocks = re.findall(r'```mermaid\s*\n(.*?)```', (ROOT / 'MIS_ASSIGNMENT_REPORT.md').read_text(), flags=re.S)
    assert len(blocks) == 5
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch()
        diagram_page = browser.new_page(viewport={'width': 1500, 'height': 1400})
        diagram_page.set_content('<!doctype html><html><head><meta charset="UTF-8"></head><body></body></html>')
        diagram_page.add_script_tag(path=str(args.mermaid.resolve()))
        diagram_page.evaluate("""() => mermaid.initialize({
            startOnLoad:false, securityLevel:'strict', theme:'neutral',
            fontFamily:'Arial, sans-serif', flowchart:{htmlLabels:false, useMaxWidth:false},
            er:{useMaxWidth:false}
        })""")
        for index, name in [(0, 'system-flow'), (1, 'database-erd'), (3, 'as-is'), (4, 'to-be')]:
            code = blocks[index].replace('flowchart LR', 'flowchart TD')
            svg = diagram_page.evaluate("async ([name, code]) => (await mermaid.render(name, code)).svg", [name, code])
            assert '<svg' in svg and 'viewBox=' in svg
            (OUTPUT / (name + '.svg')).write_text(svg)
            diagram_page.set_content(svg)
            diagram_page.locator('svg').screenshot(path=str(OUTPUT / (name + '.png')))
        diagram_page.close()

        password = secrets.token_urlsafe(24)
        container = subprocess.check_output(['docker', 'run', '-d', '--rm', '-p', '127.0.0.1::5432',
            '-e', 'POSTGRES_USER=submission', '-e', 'POSTGRES_DB=submission',
            '-e', 'POSTGRES_PASSWORD=' + password, 'postgres:18'], text=True).strip()
        process = None
        try:
            for _ in range(40):
                if subprocess.run(['docker', 'exec', container, 'pg_isready', '-U', 'submission'],
                                  stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
                    break
                time.sleep(1)
            database_port = subprocess.check_output(['docker', 'port', container, '5432/tcp'], text=True).strip().split(':')[-1]
            with socket.socket() as listener:
                listener.bind(('127.0.0.1', 0))
                app_port = listener.getsockname()[1]
            base = f'http://127.0.0.1:{app_port}'
            env = dict(os.environ, DB_URL=f'jdbc:postgresql://127.0.0.1:{database_port}/submission',
                       DB_USERNAME='submission', DB_PASSWORD=password)
            log_path = ROOT / 'target/submission-capture.log'
            with log_path.open('w') as log:
                process = subprocess.Popen(['java', '-jar', str(ROOT / 'target/personal-finance-0.0.1-SNAPSHOT.jar'),
                                            '--server.address=127.0.0.1', f'--server.port={app_port}'],
                                           env=env, stdout=log, stderr=subprocess.STDOUT)
                for _ in range(60):
                    if process.poll() is not None:
                        raise RuntimeError('Capture application stopped; inspect target/submission-capture.log')
                    try:
                        urllib.request.urlopen(base + '/login')
                        break
                    except OSError:
                        time.sleep(1)
                context = browser.new_context(viewport={'width': 1440, 'height': 1050}, reduced_motion='reduce')
                page = context.new_page()
                errors = []
                page.on('pageerror', lambda error: errors.append(str(error)))
                email = 'submission@example.test'
                passphrase = secrets.token_urlsafe(24)
                page.goto(base + '/register')
                for field, value in {'name': 'MIS Classroom Study', 'email': email,
                                     'password': passphrase, 'confirmPassword': passphrase}.items():
                    page.locator('#' + field).fill(value)
                page.get_by_role('button', name='Create account', exact=True).click()
                page.goto(base + '/login')
                page.locator('#email').fill(email)
                page.locator('#password').fill(passphrase)
                page.get_by_role('button', name='Sign in', exact=True).click()
                page.wait_for_url(base + '/home')

                # Seed only this disposable database, after real registration. No normal Compose DB is touched.
                with (ROOT / 'mis_transactions_6months.csv').open(newline='') as source:
                    rows = list(csv.DictReader(source))
                assert len(rows) == 66
                sql = ['BEGIN;']
                accounts = [
                    ('ABA Bank Checking (USD)', 'CHECKING', '1450.00', 'USD'),
                    ('Emergency Reserve Savings (USD)', 'SAVINGS', '3600.00', 'USD'),
                    ('Wing Digital Wallet (KHR)', 'CHECKING', '7000000.00', 'KHR'),
                    ('Daily Cash Wallet (KHR)', 'CASH', '5000000.00', 'KHR'),
                ]
                for name, kind, balance, currency in accounts:
                    sql.append(f"INSERT INTO accounts (user_id,name,account_type,initial_balance,currency) SELECT id,{quote(name)},{quote(kind)},{balance},{quote(currency)} FROM users WHERE email={quote(email)};")
                for name, kind in sorted({(row['Category'], row['Type'].upper()) for row in rows}):
                    sql.append(f"INSERT INTO categories (user_id,name,category_type) SELECT id,{quote(name)},{quote(kind)} FROM users WHERE email={quote(email)};")
                for row in rows:
                    sql.append(f"""INSERT INTO transactions (user_id,account_id,category_id,transaction_type,amount,currency,transaction_date,description)
                        SELECT u.id,a.id,c.id,{quote(row['Type'].upper())},{row['Amount']},{quote(row['Currency'])},{quote(row['Date'])},{quote(row['Description'])}
                        FROM users u JOIN accounts a ON a.user_id=u.id JOIN categories c ON c.user_id=u.id
                        WHERE u.email={quote(email)} AND a.name={quote(row['Account'])} AND c.name={quote(row['Category'])}
                        AND c.category_type={quote(row['Type'].upper())};""")
                sql.append(f"""INSERT INTO budgets (user_id,category_id,currency,budget_month,limit_amount)
                    SELECT u.id,c.id,'USD','2026-10',380 FROM users u JOIN categories c ON c.user_id=u.id
                    WHERE u.email={quote(email)} AND c.name='Food & Dining' AND c.category_type='EXPENSE';""")
                sql.append('COMMIT;')
                subprocess.run(['docker', 'exec', '-i', container, 'psql', '-v', 'ON_ERROR_STOP=1', '-U', 'submission', '-d', 'submission'],
                               input='\n'.join(sql), text=True, stdout=subprocess.DEVNULL, check=True)
                page.goto(base + '/accounts')
                page.wait_for_load_state('networkidle')
                assert '9722.00' in page.locator('#accounts-portfolio-strip').inner_text()
                page.locator('[data-view-mode="table"]').click()
                page.screenshot(path=str(OUTPUT / 'accounts.png'))
                page.goto(base + '/transactions?startDate=2026-05-01&endDate=2026-10-31')
                page.wait_for_load_state('networkidle')
                assert '66' in page.locator('#tx-live-count').inner_text()
                page.locator('[data-view-mode="table"]').click()
                page.screenshot(path=str(OUTPUT / 'ledger.png'))
                page.goto(base + '/home')
                page.wait_for_load_state('networkidle')
                page.screenshot(path=str(OUTPUT / 'dashboard.png'))
                assert not errors, errors
                (OUTPUT / 'capture-metadata.json').write_text(json.dumps({
                    'captured_on': str(date.today()), 'source': 'mis_transactions_6months.csv',
                    'synthetic_entries': 66, 'fixed_period': '2026-05 to 2026-10',
                    'opening_usd': '5050.00', 'closing_usd': '9722.00',
                    'opening_khr': '12000000.00', 'closing_khr': '15000000.00',
                    'note': 'Live dashboard analytics use the server date and rolling window; the workbook is a fixed-period study.',
                    'normal_database_modified': False,
                }, indent=2))
                context.close()
        finally:
            if process:
                process.terminate()
                process.wait(timeout=20)
            subprocess.run(['docker', 'stop', container], stdout=subprocess.DEVNULL, check=False)
        render_report(browser)
        browser.close()
    print('PASS: four rendered diagrams, three real application screenshots, report PDF, 14 slide previews and presentation PDF.')


if __name__ == '__main__':
    main()
