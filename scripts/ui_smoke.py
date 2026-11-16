#!/usr/bin/env python3
"""Exercise the built application with Playwright and a disposable PostgreSQL database.

Requires Docker, Java, a built target/*.jar, and Python Playwright with Chromium.
No QA data is written to the normal Compose database. Artifacts default to target/ui-review.
"""
import argparse
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import time
import urllib.request

from playwright.sync_api import sync_playwright


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifacts', type=Path, default=Path('target/ui-review'))
    parser.add_argument('--axe', type=Path, help='Optional local axe-core JavaScript bundle')
    parser.add_argument('--skip-layout', action='store_true', help='Run workflow/accessibility checks without the viewport matrix')
    args = parser.parse_args()
    args.artifacts.mkdir(parents=True, exist_ok=True)
    password = secrets.token_urlsafe(24)
    container = subprocess.check_output([
        'docker', 'run', '-d', '--rm', '-p', '127.0.0.1::5432',
        '-e', 'POSTGRES_USER=review', '-e', 'POSTGRES_DB=review',
        '-e', 'POSTGRES_PASSWORD=' + password, 'postgres:18'], text=True).strip()
    process = None
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        app_port = listener.getsockname()[1]
    base = f'http://127.0.0.1:{app_port}'
    results = {'viewports': [], 'accessibility': []}
    try:
        for _ in range(40):
            if subprocess.run(['docker', 'exec', container, 'pg_isready', '-U', 'review'],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
                break
            time.sleep(1)
        port = subprocess.check_output(['docker', 'port', container, '5432/tcp'], text=True).strip().split(':')[-1]
        env = dict(os.environ, DB_URL=f'jdbc:postgresql://localhost:{port}/review',
                   DB_USERNAME='review', DB_PASSWORD=password)
        with (args.artifacts / 'server.log').open('w') as log:
            process = subprocess.Popen(['java', '-jar', 'target/personal-finance-0.0.1-SNAPSHOT.jar',
                                        '--server.address=127.0.0.1', f'--server.port={app_port}'], env=env, stdout=log, stderr=subprocess.STDOUT)
            for _ in range(60):
                if process.poll() is not None:
                    raise RuntimeError('Review server stopped; inspect server.log')
                try:
                    urllib.request.urlopen(base + '/login')
                    break
                except OSError:
                    time.sleep(1)
            with sync_playwright() as p:
                browser = p.chromium.launch()
                context = browser.new_context(viewport={'width': 1440, 'height': 1000}, reduced_motion='reduce')
                page = context.new_page()
                errors = []
                page.on('pageerror', lambda error: errors.append(str(error)))
                page.on('console', lambda message: errors.append(message.text) if message.type == 'error' else None)

                def goto(path, expected=200):
                    response = page.goto(base + path)
                    assert response.status == expected, (path, response.status)
                    page.wait_for_load_state('networkidle')
                    assert not page.locator('i[data-lucide]').count(), ('Unrendered icon', path)
                    assert page.locator('svg.lucide').count(), ('Missing icons', path)

                def screenshot(name):
                    page.locator('#page-title').evaluate('(e) => e.blur()')
                    page.screenshot(path=str(args.artifacts / (name + '.png')), full_page=True)

                def check_password_controls():
                    for control in page.locator('.password-input').all():
                        field = control.locator('input').bounding_box()
                        button = control.locator('button').bounding_box()
                        assert field and button
                        assert button['x'] >= field['x'] and button['y'] >= field['y']
                        assert button['x'] + button['width'] <= field['x'] + field['width']
                        assert button['y'] + button['height'] <= field['y'] + field['height']
                        assert control.locator('button').inner_text().strip() == ''
                        assert control.locator('button svg.lucide').count() == 1
                        assert control.locator('button').get_attribute('aria-label')

                def accessibility(path):
                    if not args.axe:
                        return
                    # Let theme variable changes and reduced-motion transitions reach the rendered frame.
                    page.evaluate('() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))')
                    page.add_script_tag(path=str(args.axe.resolve()))
                    scan = page.evaluate("""async () => {
                        const result = await axe.run(document, {runOnly: {type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa']}});
                        return result.violations.map(v => ({id: v.id, impact: v.impact, nodes: v.nodes.map(n => ({target:n.target, summary:n.failureSummary}))}));
                    }""")
                    results['accessibility'].append({'path': path, 'violations': scan})
                    if scan:
                        results['computed'] = page.evaluate("""() => Array.from(document.querySelectorAll('.flow-label')).map(e => ({
                            html:e.outerHTML, color:getComputedStyle(e).color, variable:getComputedStyle(e).getPropertyValue('--text-secondary'),
                            children:Array.from(e.querySelectorAll('strong, span')).map(c => ({html:c.outerHTML, color:getComputedStyle(c).color, style:c.getAttribute('style')}))
                        }))""")
                        (args.artifacts / 'accessibility-failure.json').write_text(json.dumps(results, indent=2))
                        screenshot('accessibility-failure')
                    assert not scan, (path, scan)

                goto('/register')
                check_password_controls()
                for field, value in {'name': 'UI Review', 'email': 'ui@example.test',
                                     'password': 'UI-review-passphrase42', 'confirmPassword': 'UI-review-passphrase42'}.items():
                    page.locator('#' + field).fill(value)
                page.locator('[data-toggle-password="password"]').click()
                assert page.locator('#password').get_attribute('type') == 'text'
                page.locator('[data-toggle-password="password"]').click()
                page.get_by_role('button', name='Create account', exact=True).click()
                goto('/login')
                check_password_controls()
                accessibility('/login')
                screenshot('login')
                for width in [375, 768, 1024, 1440, 1920]:
                    page.set_viewport_size({'width': width, 'height': 1000})
                    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), ('login', width)
                    check_password_controls()
                    screenshot('login-' + str(width))
                page.set_viewport_size({'width': 375, 'height': 900})
                page.locator('#theme-toggle').click()
                accessibility('/login:dark')
                screenshot('login-dark')
                page.locator('#lang-toggle').click()
                assert page.locator('html').get_attribute('lang') == 'km'
                check_password_controls()
                assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
                screenshot('login-khmer')
                page.locator('#lang-toggle').click()
                page.locator('#theme-toggle').click()
                page.set_viewport_size({'width': 1440, 'height': 1000})
                page.locator('#email').fill('ui@example.test')
                page.locator('#password').fill('Wrong-test-password')
                page.get_by_role('button', name='Sign in', exact=True).click()
                page.wait_for_url(base + '/login?error')
                assert 'Invalid email or password.' in page.locator('.alert').inner_text()
                assert page.locator('#password').input_value() == ''
                page.locator('#email').fill('ui@example.test')
                page.locator('#password').fill('UI-review-passphrase42')
                page.get_by_role('button', name='Sign in', exact=True).click()
                page.wait_for_url(base + '/home')
                for path in ['/accounts', '/categories', '/transactions', '/budgets', '/transfers']:
                    goto(path)
                    assert page.locator('.empty-state .button-link').count() == 1, path

                account_ids = []
                for name, currency in [('Everyday', 'USD'), ('Reserve', 'USD'), ('Local cash', 'KHR')]:
                    goto('/accounts/new')
                    page.locator('#name').fill(name)
                    page.locator('#accountType').select_option('CHECKING')
                    page.locator('#currency').select_option(currency)
                    page.locator('#initialBalance').fill('100.00')
                    page.get_by_role('button', name='Save account', exact=True).click()
                    page.wait_for_url(base + '/accounts')
                    card = page.locator('.account-card').filter(has=page.get_by_role('heading', name=name, exact=True))
                    account_ids.append(card.locator('a[href$="/edit"]').get_attribute('href').split('/')[2])
                category_ids = []
                for name, kind in [('Salary', 'INCOME'), ('Groceries', 'EXPENSE')]:
                    goto('/categories/new')
                    page.locator('#name').fill(name)
                    page.locator('#categoryType').select_option(kind)
                    page.get_by_role('button', name='Save category', exact=True).click()
                    page.wait_for_url(base + '/categories')
                    card = page.locator('.category-card').filter(has=page.get_by_role('heading', name=name, exact=True))
                    category_ids.append(card.locator('a[href$="/edit"]').get_attribute('href').split('/')[2])
                for category, kind, amount in [(category_ids[0], 'INCOME', '20.10'), (category_ids[1], 'EXPENSE', '5.20')]:
                    goto('/transactions/new')
                    page.locator('#accountId').select_option(account_ids[0])
                    page.locator('#transactionType').select_option(kind)
                    page.locator('#categoryId').select_option(category)
                    page.locator('#amount').fill(amount)
                    page.locator('#description').fill('Synthetic UI review')
                    page.get_by_role('button', name='Save transaction', exact=True).click()
                    page.wait_for_url(base + '/transactions')
                transaction_edit = page.locator('.transaction-card').first.locator('a[href$="/edit"]').get_attribute('href')
                goto('/accounts')
                assert '114.90' in page.inner_text('main')
                goto('/budgets/new')
                page.locator('#categoryId').select_option(category_ids[1])
                page.locator('#currency').select_option('USD')
                page.locator('#limitAmount').fill('50.00')
                page.get_by_role('button', name='Save budget', exact=True).click()
                page.wait_for_url(base + '/budgets')
                budget_edit = page.locator('.budget-card a[href$="/edit"]').first.get_attribute('href')
                goto('/transfers/new')
                page.locator('#sourceAccountId').select_option(account_ids[0])
                page.locator('#targetAccountId').select_option(account_ids[1])
                page.locator('#swap-transfer-accounts-btn').click()
                assert page.locator('#sourceAccountId').input_value() == account_ids[1]
                page.locator('#swap-transfer-accounts-btn').click()
                page.locator('#amount').fill('10.00')
                page.get_by_role('button', name='Record transfer', exact=True).click()
                page.wait_for_url(base + '/transfers')
                paths = ['/home', '/accounts', '/categories', '/transactions', '/budgets', '/transfers', '/reports',
                         '/accounts/new', '/categories/new', '/transactions/new', '/budgets/new', '/transfers/new',
                         '/accounts/' + account_ids[0] + '/edit', '/categories/' + category_ids[1] + '/edit',
                         transaction_edit, budget_edit, transaction_edit.replace('/edit', '/delete')]
                for width in ([] if args.skip_layout else [375, 768, 1024, 1440, 1920]):
                    page.set_viewport_size({'width': width, 'height': 1000})
                    for path in paths:
                        goto(path)
                        assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), (width, path)
                        view = page.locator('[data-view-mode="table"]').first
                        if view.count():
                            view.click()
                            assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), (width, path, 'table')
                            page.locator('[data-view-mode="grid"]').first.click()
                    for path, name in [('/home', 'dashboard'), ('/accounts', 'accounts'), ('/transactions/new', 'entry')]:
                        goto(path)
                        screenshot(f'{name}-{width}')
                    results['viewports'].append(width)

                page.set_viewport_size({'width': 375, 'height': 900})
                goto('/home')
                toggle = page.locator('.nav-menu-toggle')
                toggle.click()
                assert page.locator('.nav-links').is_visible()
                page.keyboard.press('Escape')
                assert toggle.get_attribute('aria-expanded') == 'false'
                assert toggle.evaluate('(e) => e === document.activeElement')
                tabs = page.get_by_role('tab')
                tabs.first.focus()
                page.keyboard.press('ArrowRight')
                assert tabs.nth(1).get_attribute('aria-selected') == 'true'
                page.locator('.skip-link').focus()
                page.keyboard.press('Enter')
                assert page.locator('#page-title').evaluate('(e) => e === document.activeElement')
                goto('/transactions')
                page.locator('[data-view-mode="table"]').click()
                sort = page.locator('#tx-sortable-table th').first.get_by_role('button')
                sort.focus()
                page.keyboard.press('Enter')
                assert page.locator('#tx-sortable-table th').first.get_attribute('aria-sort') == 'ascending'
                page.locator('#filter-q').fill('no-match-ui-review')
                assert page.locator('#tx-no-results').is_visible()
                goto('/transactions?q=definitely-no-matches')
                assert 'No matching transactions' in page.inner_text('main')
                assert 'No transactions yet.' not in page.inner_text('main')
                goto('/accounts')
                page.locator('[data-card-search-target="accounts"]').fill('no-match-ui-review')
                assert 'No matches.' in page.locator('.filter-feedback').inner_text()
                goto('/reports')
                before = page.locator('#sim-cut-label').inner_text()
                page.locator('#sim-cut-slider').fill('30')
                assert page.locator('#sim-cut-label').inner_text() != before
                response = page.request.get(base + '/transactions/export.csv')
                assert response.status == 200 and 'Synthetic UI review' in response.text()

                # Server-side validation page: no silent writes and focusable error summary.
                goto('/accounts/new')
                page.locator('form[action="/accounts"]').evaluate('(f) => f.noValidate = true')
                page.locator('#name').fill('')
                page.get_by_role('button', name='Save account', exact=True).click()
                page.wait_for_load_state('networkidle')
                assert page.locator('form .alert').evaluate('(e) => e === document.activeElement')
                assert page.locator('form .alert a[href="#name"]').count() == 1
                for theme in ['light', 'dark']:
                    goto('/home')
                    if page.locator('html').get_attribute('data-theme') != theme:
                        page.locator('#theme-toggle').click()
                    accessibility('/home:' + theme)
                    screenshot('dashboard-' + theme)
                    for path in paths[1:]:
                        goto(path)
                        assert page.locator('html').get_attribute('data-theme') == theme
                        accessibility(path + ':' + theme)
                goto('/home')
                page.locator('#lang-toggle').click()
                assert page.locator('html').get_attribute('lang') == 'km'
                assert page.locator('#theme-toggle svg').count() == 1
                page.locator('#theme-toggle').click()
                assert page.locator('#lang-toggle svg').count() == 1
                assert page.evaluate('document.documentElement.scrollWidth <= innerWidth')
                screenshot('dashboard-khmer')
                page.locator('#lang-toggle').click()
                goto(transaction_edit)
                page.locator('#amount').fill('6.20')
                page.get_by_role('button', name='Save transaction', exact=True).click()
                page.wait_for_url(base + '/transactions')
                goto('/accounts')
                everyday = page.locator('.account-card').filter(has=page.get_by_role('heading', name='Everyday', exact=True))
                assert '103.90' in everyday.inner_text()
                goto(transaction_edit.replace('/edit', '/delete'))
                page.get_by_role('link', name='Cancel and keep transaction', exact=True).click()
                page.wait_for_url(base + '/transactions')
                assert page.locator('.transaction-card').count() == 2
                goto(transaction_edit.replace('/edit', '/delete'))
                page.get_by_role('button', name='Delete transaction permanently', exact=True).click()
                page.wait_for_url(base + '/transactions')
                goto('/accounts')
                everyday = page.locator('.account-card').filter(has=page.get_by_role('heading', name='Everyday', exact=True))
                assert '110.10' in everyday.inner_text()
                nojs = browser.new_context(java_script_enabled=False, storage_state=context.storage_state(), viewport={'width':375, 'height':900})
                fallback = nojs.new_page()
                fallback.goto(base + '/home')
                assert fallback.locator('.nav-links').is_visible()
                restricted = browser.new_context(storage_state=context.storage_state())
                restricted.add_init_script("Storage.prototype.getItem = () => { throw new Error('Blocked'); }; Storage.prototype.setItem = () => { throw new Error('Blocked'); };")
                restricted_page = restricted.new_page()
                restricted_page.goto(base + '/home')
                assert restricted_page.locator('svg.lucide').count() > 0
                assert not errors, errors
                browser.close()
                (args.artifacts / 'results.json').write_text(json.dumps(results, indent=2))
                print('PASS: real CRUD, transfer, budget, CSV, filters, keyboard controls, icons, dark/Khmer, no-JS and blocked-storage checks. Viewports: ' + (str(results['viewports']) if results['viewports'] else 'matrix skipped'))
    finally:
        if process:
            process.terminate()
            process.wait(timeout=20)
        subprocess.run(['docker', 'stop', container], stdout=subprocess.DEVNULL, check=False)


if __name__ == '__main__':
    main()
