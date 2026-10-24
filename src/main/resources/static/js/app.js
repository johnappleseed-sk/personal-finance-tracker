(function () {
    const STORAGE_KEY = 'pf-theme';
    const TX_VIEW_KEY = 'pf-tx-view';
    const root = document.documentElement;

    function getPreferredTheme() {
        const saved = localStorage.getItem(STORAGE_KEY);
        if (saved === 'dark' || saved === 'light') {
            return saved;
        }
        return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
            ? 'dark'
            : 'light';
    }

    function applyTheme(theme) {
        root.setAttribute('data-theme', theme);
        const btn = document.getElementById('theme-toggle');
        if (btn) {
            const isDark = theme === 'dark';
            btn.setAttribute('aria-label', isDark ? 'Switch to light theme' : 'Switch to dark theme');
            btn.textContent = isDark ? '☀ Light' : '☾ Dark';
        }
    }

    applyTheme(getPreferredTheme());

    function formatIsoDate(date) {
        const y = date.getFullYear();
        const m = String(date.getMonth() + 1).padStart(2, '0');
        const d = String(date.getDate()).padStart(2, '0');
        return y + '-' + m + '-' + d;
    }

    function initAutoSubmitSelects() {
        document.querySelectorAll('select[data-auto-submit="true"]').forEach(function (sel) {
            sel.addEventListener('change', function () {
                if (sel.form) {
                    sel.form.submit();
                }
            });
        });
    }

    function initDssSimulator() {
        const simPanel = document.getElementById('dss-simulator');
        const cutSlider = document.getElementById('sim-cut-slider');
        const allocSlider = document.getElementById('sim-alloc-slider');
        if (!simPanel || !cutSlider || !allocSlider) {
            return;
        }

        const currency = simPanel.getAttribute('data-currency') || 'USD';
        const baseIncome = parseFloat(simPanel.getAttribute('data-income') || '0');
        const baseExpense = parseFloat(simPanel.getAttribute('data-expense') || '0');
        const topExpenseAmt = parseFloat(simPanel.getAttribute('data-top-amt') || '0');

        const cutLabel = document.getElementById('sim-cut-label');
        const allocLabel = document.getElementById('sim-alloc-label');
        const savedVal = document.getElementById('sim-saved-val');
        const netVal = document.getElementById('sim-net-val');
        const rateVal = document.getElementById('sim-rate-val');
        const investVal = document.getElementById('sim-invest-val');

        function recalc() {
            const cutPct = parseInt(cutSlider.value, 10) || 0;
            const allocPct = parseInt(allocSlider.value, 10) || 0;

            if (cutLabel) cutLabel.textContent = cutPct + '% Cut';
            if (allocLabel) allocLabel.textContent = allocPct + '% Allocation';

            const saved = topExpenseAmt * (cutPct / 100.0);
            const newExpense = Math.max(0, baseExpense - saved);
            const newNet = baseIncome - newExpense;
            const newRate = baseIncome > 0
                ? Math.round((newNet / baseIncome) * 100)
                : (newExpense > 0 ? -100 : 0);
            const investable = newNet > 0 ? newNet * (allocPct / 100.0) : 0;

            if (savedVal) savedVal.textContent = '+' + currency + ' ' + saved.toFixed(2);
            if (netVal) {
                netVal.textContent = (newNet >= 0 ? '+' : '') + currency + ' ' + newNet.toFixed(2);
                netVal.className = 'money-lg ' + (newNet >= 0 ? 'money-income' : 'money-expense');
            }
            if (rateVal) rateVal.textContent = newRate + '%';
            if (investVal) investVal.textContent = currency + ' ' + investable.toFixed(2);
        }

        cutSlider.addEventListener('input', recalc);
        allocSlider.addEventListener('input', recalc);
        recalc();
    }

    function initTransactionsPage() {
        const gridBtn = document.getElementById('view-btn-grid');
        const tableBtn = document.getElementById('view-btn-table');
        const gridView = document.getElementById('tx-grid-view');
        const tableView = document.getElementById('tx-table-view');

        if (gridBtn && tableBtn && gridView && tableView) {
            function setViewMode(mode) {
                const isTable = mode === 'table';
                gridView.classList.toggle('hidden-view', isTable);
                tableView.classList.toggle('hidden-view', !isTable);
                gridBtn.classList.toggle('active', !isTable);
                tableBtn.classList.toggle('active', isTable);
                localStorage.setItem(TX_VIEW_KEY, mode);
            }

            const savedMode = localStorage.getItem(TX_VIEW_KEY);
            if (savedMode === 'table') {
                setViewMode('table');
            }

            gridBtn.addEventListener('click', function () { setViewMode('grid'); });
            tableBtn.addEventListener('click', function () { setViewMode('table'); });
        }

        // Instant client-side filter on #filter-q
        const searchInput = document.getElementById('filter-q');
        const countLabel = document.getElementById('tx-live-count');
        const cards = document.querySelectorAll('.tx-filterable-item');
        const rows = document.querySelectorAll('.tx-filterable-row');

        if (searchInput && (cards.length > 0 || rows.length > 0)) {
            searchInput.addEventListener('input', function () {
                const q = (searchInput.value || '').trim().toLowerCase();
                let visibleCount = 0;

                cards.forEach(function (card) {
                    const hay = card.getAttribute('data-search') || '';
                    const match = !q || hay.indexOf(q) !== -1;
                    card.style.display = match ? '' : 'none';
                    if (match) visibleCount++;
                });

                rows.forEach(function (row) {
                    const hay = row.getAttribute('data-search') || '';
                    const match = !q || hay.indexOf(q) !== -1;
                    row.style.display = match ? '' : 'none';
                });

                if (countLabel) {
                    countLabel.textContent = visibleCount + ' entries shown';
                }
            });
        }

        // Quick date range preset buttons
        const startInput = document.getElementById('filter-start');
        const endInput = document.getElementById('filter-end');
        const filterForm = document.getElementById('tx-filter-form');
        document.querySelectorAll('[data-date-preset]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                if (!startInput || !endInput) return;
                const preset = btn.getAttribute('data-date-preset');
                const today = new Date();
                const start = new Date(today.getTime());

                if (preset === 'month') {
                    start.setDate(1);
                } else if (preset === '30d') {
                    start.setDate(today.getDate() - 30);
                } else if (preset === '6m') {
                    start.setMonth(today.getMonth() - 5);
                    start.setDate(1);
                }

                startInput.value = formatIsoDate(start);
                endInput.value = formatIsoDate(today);
                if (filterForm) {
                    filterForm.submit();
                }
            });
        });
    }

    function initSmartForms() {
        // 1. Date shortcut buttons (Today / Yesterday)
        document.querySelectorAll('[data-date-target]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                const targetId = btn.getAttribute('data-date-target');
                const offset = parseInt(btn.getAttribute('data-date-offset') || '0', 10);
                const input = document.getElementById(targetId);
                if (!input) return;
                const d = new Date();
                d.setDate(d.getDate() + offset);
                input.value = formatIsoDate(d);
            });
        });

        // 2. Transaction Form: live currency badge + category type auto-filter
        const accountSelect = document.getElementById('accountId');
        const currencyBadge = document.getElementById('amount-currency-badge');
        if (accountSelect && currencyBadge) {
            function syncAccountCurrency() {
                const opt = accountSelect.options[accountSelect.selectedIndex];
                const curr = opt ? opt.getAttribute('data-currency') : null;
                currencyBadge.textContent = curr ? ('Currency: ' + curr) : 'Account Currency';
            }
            accountSelect.addEventListener('change', syncAccountCurrency);
            syncAccountCurrency();
        }

        const typeSelect = document.getElementById('transactionType');
        const categorySelect = document.getElementById('categoryId');
        if (typeSelect && categorySelect) {
            function syncCategoriesByType() {
                const selectedType = typeSelect.value;
                Array.from(categorySelect.options).forEach(function (opt) {
                    const catType = opt.getAttribute('data-category-type');
                    if (!catType) return; // Keep placeholder option
                    const matches = !selectedType || catType === selectedType;
                    opt.hidden = !matches;
                });
            }
            typeSelect.addEventListener('change', syncCategoriesByType);
            syncCategoriesByType();
        }

        // 3. Transfer Form: live currency badge + matching target accounts
        const sourceSelect = document.getElementById('sourceAccountId');
        const targetSelect = document.getElementById('targetAccountId');
        const transferBadge = document.getElementById('transfer-currency-badge');
        if (sourceSelect && targetSelect) {
            function syncTransferCurrency() {
                const opt = sourceSelect.options[sourceSelect.selectedIndex];
                const curr = opt ? opt.getAttribute('data-currency') : null;
                if (transferBadge) {
                    transferBadge.textContent = curr ? ('Transfer in ' + curr) : 'Same Currency';
                }
            }
            sourceSelect.addEventListener('change', syncTransferCurrency);
            syncTransferCurrency();
        }
    }

    document.addEventListener('DOMContentLoaded', function () {
        applyTheme(root.getAttribute('data-theme') || getPreferredTheme());

        const toggleBtn = document.getElementById('theme-toggle');
        if (toggleBtn) {
            toggleBtn.addEventListener('click', function () {
                const next = root.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
                localStorage.setItem(STORAGE_KEY, next);
                applyTheme(next);
            });
        }

        initAutoSubmitSelects();
        initDssSimulator();
        initTransactionsPage();
        initSmartForms();
        initGenericCardFilters();
        initDashboardChartTabs();
    });

    function initDashboardChartTabs() {
        const tabBtns = document.querySelectorAll('[data-dash-currency-btn]');
        const panes = document.querySelectorAll('[data-currency-pane]');
        if (tabBtns.length === 0 || panes.length === 0) return;

        tabBtns.forEach(function (btn) {
            btn.addEventListener('click', function () {
                const code = btn.getAttribute('data-currency-code');
                tabBtns.forEach(function (b) { b.classList.toggle('active', b === btn); });
                panes.forEach(function (pane) {
                    const match = pane.getAttribute('data-currency-pane') === code;
                    pane.classList.toggle('hidden-view', !match);
                });
            });
        });
    }

    function initGenericCardFilters() {
        const activeTagByGroup = {};
        const searchQueryByGroup = {};

        function applyGroupFilter(group) {
            const tag = activeTagByGroup[group] || 'all';
            const query = (searchQueryByGroup[group] || '').trim().toLowerCase();
            const items = document.querySelectorAll('[data-filter-item="' + group + '"]');

            items.forEach(function (item) {
                const itemTags = (item.getAttribute('data-filter-tag') || '').split(/\s+/);
                const itemText = item.getAttribute('data-filter-text') || '';
                const tagMatch = tag === 'all' || itemTags.indexOf(tag) !== -1;
                const textMatch = !query || itemText.indexOf(query) !== -1;
                item.style.display = (tagMatch && textMatch) ? '' : 'none';
            });
        }

        document.querySelectorAll('[data-card-filter-group]').forEach(function (btn) {
            btn.addEventListener('click', function () {
                const group = btn.getAttribute('data-card-filter-group');
                const val = btn.getAttribute('data-filter-val') || 'all';
                activeTagByGroup[group] = val;

                document.querySelectorAll('[data-card-filter-group="' + group + '"]').forEach(function (sibling) {
                    sibling.classList.toggle('active', sibling === btn);
                });

                applyGroupFilter(group);
            });
        });

        document.querySelectorAll('[data-card-search-target]').forEach(function (input) {
            input.addEventListener('input', function () {
                const group = input.getAttribute('data-card-search-target');
                searchQueryByGroup[group] = input.value || '';
                applyGroupFilter(group);
            });
        });
    }
})();
