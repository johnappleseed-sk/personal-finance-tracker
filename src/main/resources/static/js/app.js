(function () {
    const STORAGE_KEY = 'pf-theme';
    const LANG_STORAGE_KEY = 'pf-lang';
    const root = document.documentElement;

    const KM_DICT = {
        // Brand & Navigation
        'Personal Finance Tracker': 'ប្រព័ន្ធគ្រប់គ្រងហិរញ្ញវត្ថុផ្ទាល់ខ្លួន',
        'Dashboard': 'ផ្ទាំងគ្រប់គ្រង',
        'Accounts': 'គណនី',
        'Categories': 'ប្រភេទចំណូលចំណាយ',
        'Transactions': 'ប្រតិបត្តិការ',
        'Transfers': 'ការផ្ទេរប្រាក់',
        'Budgets': 'ផែនការថវិកា',
        'Reports & DSS': 'របាយការណ៍ និង DSS',
        'Sign out': 'ចាកចេញ',
        'Sign in': 'ចូលប្រើប្រាស់',
        'Create your account': 'បង្កើតគណនីរបស់អ្នក',
        'Create an account': 'បង្កើតគណនីថ្មី',
        'Create account': 'បង្កើតគណនី',
        'Use the email address and password you registered with.': 'សូមបញ្ចូលអ៊ីមែល និងពាក្យសម្ងាត់ដែលអ្នកបានចុះឈ្មោះ។',
        'Invalid email or password. Please try again.': 'អ៊ីមែល ឬពាក្យសម្ងាត់មិនត្រឹមត្រូវ។ សូមព្យាយាមម្តងទៀត។',
        'You have been signed out.': 'អ្នកបានចាកចេញពីប្រព័ន្ធដោយជោគជ័យ។',
        'Email address': 'អាសយដ្ឋានអ៊ីមែល',
        'Password': 'ពាក្យសម្ងាត់',
        'Confirm password': 'បញ្ជាក់ពាក្យសម្ងាត់',
        'Name': 'ឈ្មោះ',
        'New here?': 'មិនទាន់មានគណនីមែនទេ?',
        'Already registered?': 'មានគណនីរួចហើយមែនទេ?',
        'Start with your details. Your financial data will belong only to you.': 'ចាប់ផ្តើមជាមួយព័ត៌មានរបស់អ្នក។ ទិន្នន័យហិរញ្ញវត្ថុជារបស់អ្នកតែម្នាក់គត់។',

        // Common Actions & View Toggles
        '⊞ Cards': '⊞ ទម្រង់កាត',
        '☰ Compact Table': '☰ តារាងសង្ខេប',
        '📊 MIS Reports & DSS': '📊 របាយការណ៍ MIS និង DSS',
        '⇄ Transfer Funds': '⇄ ផ្ទេរប្រាក់',
        '◎ Set Budget': '◎ កំណត់ថវិកា',
        '+ New Transaction': '+ ប្រតិបត្តិការថ្មី',
        '+ New Account': '+ បង្កើតគណនីថ្មី',
        '+ New Category': '+ បង្កើតប្រភេទថ្មី',
        '+ New Transfer': '+ ការផ្ទេរប្រាក់ថ្មី',
        '+ New Budget': '+ ផែនការថវិកាថ្មី',
        'Edit': 'កែសម្រួល',
        'Archive': 'ទុកក្នុងបណ្ណសារ',
        'Delete': 'លុប',
        'Save': 'រក្សាទុក',
        'Save Changes': 'រក្សាទុកការផ្លាស់ប្តូរ',
        'Cancel': 'បោះបង់',
        'Filter': 'ចម្រាញ់',
        'Apply Filter': 'អនុវត្តការចម្រាញ់',
        'Reset': 'កំណត់ឡើងវិញ',
        'Clear': 'សម្អាត',
        'Today': 'ថ្ងៃនេះ',
        'Yesterday': 'ម្សិលមិញ',
        'This Month': 'ខែនេះ',
        'Last 30 Days': '៣០ ថ្ងៃចុងក្រោយ',
        '6 Months': '៦ ខែ',
        'All': 'ទាំងអស់',
        'Active': 'សកម្ម',
        'Archived': 'ទុកក្នុងបណ្ណសារ',
        'Income': 'ចំណូល',
        'Expense': 'ចំណាយ',
        'INCOME': 'ចំណូល',
        'EXPENSE': 'ចំណាយ',

        // Dashboard (Home)
        'Automated Data Analysis & Financial Health Signals': 'ការវិភាគទិន្នន័យស្វ័យប្រវត្តិ និងសញ្ញាសុខភាពហិរញ្ញវត្ថុ',
        'Real-time statistical indicators computed from your 6-month ledger history, emergency reserve benchmarks, and budget utilization.': 'សូចនាករស្ថិតិគណនាដោយស្វ័យប្រវត្តិពីប្រវត្តិប្រតិបត្តិការ ៦ ខែ ទុនបម្រុងបន្ទាន់ និងការប្រើប្រាស់ថវិកា។',
        'Net Balance & Monthly Cash Flow': 'សមតុល្យសុទ្ធ និងលំហូរសាច់ប្រាក់ប្រចាំខែ',
        'Open Full 6-Month MIS & DSS Report →': 'បើករបាយការណ៍ MIS និង DSS ៦ ខែពេញលេញ →',
        'Visual Analytics & Expense Distribution': 'ក្រាហ្វិកវិភាគទិន្នន័យ និងការបែងចែកចំណាយ',
        '6-Month Cash Flow Trend, Savings Rate Trajectory & Category Share': 'និន្នាការលំហូរសាច់ប្រាក់ ៦ ខែ អត្រាសន្សំ និងសមាមាត្រតាមប្រភេទចំណាយ',
        '6-Month Income vs. Expense Trend': 'និន្នាការចំណូលធៀបនឹងចំណាយរយៈពេល ៦ ខែ',
        'Savings Rate (%) Trajectory': 'និន្នាការអត្រាសន្សំ (%) រយៈពេល ៦ ខែ',
        'Current Month Expense Distribution': 'ការបែងចែកការចំណាយប្រចាំខែបច្ចុប្បន្ន',
        'Recent Transactions': 'ប្រតិបត្តិការថ្មីៗ',
        'Budget Watch': 'តាមដានផែនការថវិកា',
        'Active Accounts': 'គណនីសកម្ម',
        'View all →': 'មើលទាំងអស់ →',
        'Manage →': 'គ្រប់គ្រង →',
        'All accounts →': 'គណនីទាំងអស់ →',
        'Month Income': 'ចំណូលប្រចាំខែ',
        'Month Expense': 'ចំណាយប្រចាំខែ',
        'Net Cash Flow': 'លំហូរសាច់ប្រាក់សុទ្ធ',
        'Savings Rate': 'អត្រាសន្សំ',
        'Month Savings Rate': 'អត្រាសន្សំប្រចាំខែ',
        'Spending Momentum': 'សន្ទុះនៃការចំណាយ',
        'Emergency Runway': 'ទុនបម្រុងបន្ទាន់',
        'Daily Burn & Top Driver': 'ចំណាយប្រចាំថ្ងៃ និងប្រភេទចំណាយធំបំផុត',

        // Table Headers & Common Labels
        'Date': 'កាលបរិច្ឆេទ',
        'Description': 'ការពិពណ៌នា',
        'Category': 'ប្រភេទ',
        'Account': 'គណនី',
        'Type': 'ប្រភេទ',
        'Amount': 'ចំនួនទឹកប្រាក់',
        'Currency': 'រូបិយប័ណ្ណ',
        'Balance': 'សមតុល្យ',
        'Current Balance': 'សមតុល្យបច្ចុប្បន្ន',
        'Status': 'ស្ថានភាព',
        'Actions': 'សកម្មភាព',
        'Spent': 'បានចំណាយ',
        'Limit': 'កម្រិតថវិកា',
        'Budget Limit': 'កម្រិតថវិកា',
        'Remaining': 'នៅសល់',
        'Usage': 'ការប្រើប្រាស់',
        'Month': 'ខែ',
        'From Account': 'ពីគណនី',
        'To Account': 'ទៅគណនី',
        'Note': 'កំណត់ចំណាំ',
        'Share': 'សមាមាត្រ',
        'Transactions Count': 'ចំនួនប្រតិបត្តិការ',

        // Badges & Statuses
        'Healthy': 'ល្អប្រសើរ',
        'Strong': 'រឹងមាំ',
        'Moderate': 'មធ្យម',
        'Warning': 'ប្រុងប្រយ័ត្ន',
        'Alert': 'ប្រកាសអាសន្ន',
        'Needs Attention': 'ត្រូវការការយកចិត្តទុកដាក់',
        'On Track': 'តាមផែនការ',
        'Near Limit': 'ជិតដល់កម្រិត',
        'Over Budget': 'លើសថវិកា',
        'Exceeded': 'លើសកម្រិត',
        'Lowering': 'ថយចុះល្អ',
        'Stable': 'មានស្ថិរភាព',
        'Rising': 'កំពុងកើនឡើង',
        'Tracked': 'បានតាមដាន',

        // Pages & Sections
        'Your accounts': 'គណនីរបស់អ្នក',
        'Track checking, savings, cash, and credit card balances per currency.': 'តាមដានសមតុល្យគណនីចរន្ត គណនីសន្សំ សាច់ប្រាក់ និងកាតឥណទានតាមរូបិយប័ណ្ណ។',
        '+ Create account': '+ បង្កើតគណនីថ្មី',
        '⇄ Transfer funds': '⇄ ផ្ទេរប្រាក់',
        'Current balance': 'សមតុល្យបច្ចុប្បន្ន',
        'Checking': 'គណនីចរន្ត',
        'Savings': 'គណនីសន្សំ',
        'Cash': 'សាច់ប្រាក់',
        'Credit Card': 'កាតឥណទាន',
        'Your categories': 'ប្រភេទចំណូលចំណាយរបស់អ្នក',
        'Organize income and expense classifications for your transactions and budgets.': 'រៀបចំចំណាត់ថ្នាក់ចំណូល និងចំណាយសម្រាប់ប្រតិបត្តិការ និងផែនការថវិការបស់អ្នក។',
        '+ Create category': '+ បង្កើតប្រភេទថ្មី',
        '◎ Set budget': '◎ កំណត់ថវិកា',
        'Your transactions': 'ប្រតិបត្តិការរបស់អ្នក',
        'Record, filter, search, and export your income and expense ledger.': 'កត់ត្រា ចម្រាញ់ ស្វែងរក និងនាំចេញសៀវភៅកត់ត្រាចំណូល និងចំណាយរបស់អ្នក។',
        '+ Create transaction': '+ បង្កើតប្រតិបត្តិការថ្មី',
        '⇩ Export CSV': '⇩ នាំចេញ CSV',
        'Search': 'ស្វែងរក',
        'All accounts': 'គណនីទាំងអស់',
        'All categories': 'ប្រភេទទាំងអស់',
        'Account transfers': 'ការផ្ទេរប្រាក់រវាងគណនី',
        'Move funds between your accounts using the same currency.': 'ផ្ទេរប្រាក់រវាងគណនីរបស់អ្នកដែលប្រើរូបិយប័ណ្ណដូចគ្នា។',
        'View accounts': 'មើលគណនី',
        '+ Transfer funds': '+ ផ្ទេរប្រាក់',
        'Monthly budgets': 'ផែនការថវិកាប្រចាំខែ',
        'Set spending limits for your expense categories and monitor monthly utilization.': 'កំណត់កម្រិតចំណាយសម្រាប់ប្រភេទចំណាយ និងតាមដានការប្រើប្រាស់ប្រចាំខែ។',
        '📊 View DSS Variance': '📊 មើលរបាយការណ៍ DSS',
        '+ Create budget': '+ បង្កើតថវិកាថ្មី',
        'Filter by month (YYYY-MM)': 'ចម្រាញ់តាមខែ (YYYY-MM)',
        'Filter month': 'ចម្រាញ់តាមខែ',
        'Show all months': 'បង្ហាញគ្រប់ខែទាំងអស់',
        'Over budget': 'លើសថវិកា',
        'Quick filter:': 'ចម្រាញ់រហ័ស៖',
        'Currency:': 'រូបិយប័ណ្ណ៖',
        'Type:': 'ប្រភេទ៖',
        'MIS Reports & Decision Support (DSS)': 'របាយការណ៍ MIS និងប្រព័ន្ធគាំទ្រការសម្រេចចិត្ត (DSS)',
        '6-month trend analysis, category performance, interactive What-If simulation, and data-driven management decisions.': 'ការវិភាគនិន្នាការ ៦ ខែ ប្រសិទ្ធភាពតាមប្រភេទចំណាយ ការសាកល្បង What-If និងការសម្រេចចិត្តផ្អែកលើទិន្នន័យ។',
        '⇩ Export Excel Summary (.csv)': '⇩ នាំចេញសង្ខេប Excel (.csv)',
        '⇩ Export Raw TPS Ledger (.csv)': '⇩ នាំចេញទិន្នន័យ TPS (.csv)',
        '6-Month Total Income': 'ចំណូលសរុបរយៈពេល ៦ ខែ',
        '6-Month Total Expenses': 'ចំណាយសរុបរយៈពេល ៦ ខែ',
        '6-Month Net Savings & Rate': 'ប្រាក់សន្សំសុទ្ធ និងអត្រាសន្សំ ៦ ខែ',
        'Decision Support System (DSS) — Actionable Recommendations': 'ប្រព័ន្ធគាំទ្រការសម្រេចចិត្ត (DSS) — អនុសាសន៍សម្រាប់ការគ្រប់គ្រង',
        'Automated managerial insights generated from your cash-flow velocity, cost concentration, and budget variance.': 'ការវិភាគស្វ័យប្រវត្តិផ្អែកលើល្បឿនលំហូរសាច់ប្រាក់ ការប្រមូលផ្តុំចំណាយ និងគម្លាតថវិកា។',
        'MIS Data Finding:': 'លទ្ធផលទិន្នន័យ MIS៖',
        'Recommended Decision:': 'ការសម្រេចចិត្តដែលណែនាំ៖',
        'Decision Area': 'ផ្នែកសម្រេចចិត្ត',
        'Signal': 'សញ្ញាណ',
        'MIS Data Finding': 'លទ្ធផលទិន្នន័យ MIS',
        'Recommended Managerial Action': 'សកម្មភាពគ្រប់គ្រងដែលណែនាំ',
        'Live DSS Model': 'គំរូ DSS ផ្ទាល់',
        'Allocate Net Surplus to High-Yield Deposit (5.5% APY)': 'បែងចែកប្រាក់សន្សំសុទ្ធទៅគណនីបញ្ញើការប្រាក់ខ្ពស់ (5.5% APY)',
        'Financial Accounts': 'គណនីហិរញ្ញវត្ថុ',
        'Transaction Categories': 'ប្រភេទចំណូល និងចំណាយ',
        'Transaction Ledger': 'សៀវភៅកត់ត្រាប្រតិបត្តិការ',
        'Account Transfers': 'ការផ្ទេរប្រាក់រវាងគណនី',
        'Monthly Budgets': 'ផែនការថវិកាប្រចាំខែ',
        'MIS Reports & Decision Support System (DSS)': 'របាយការណ៍ MIS និងប្រព័ន្ធគាំទ្រការសម្រេចចិត្ត (DSS)',
        'Decision Support System (DSS) Recommendations': 'អនុសាសន៍ពីប្រព័ន្ធគាំទ្រការសម្រេចចិត្ត (DSS)',
        'Interactive What-If Decision Simulator': 'កម្មវិធីសាកល្បងការសម្រេចចិត្ត (What-If Simulator)',
        '6-Month Cash Flow Trend (MIS Summary)': 'និន្នាការលំហូរសាច់ប្រាក់ ៦ ខែ (សង្ខេប MIS)',
        'Expense Breakdown by Category (Current Month)': 'ការបែងចែកការចំណាយតាមប្រភេទ (ខែបច្ចុប្បន្ន)'
    };

    const KM_PATTERNS = [
        {
            re: /^Welcome,\s+(.+)$/,
            toKm: function (m) { return 'សូមស្វាគមន៍, ' + m[1]; }
        },
        {
            re: /^(\d+)\s+Ledger Entries Analyzed$/,
            toKm: function (m) { return m[1] + ' ប្រតិបត្តិការត្រូវបានវិភាគ'; }
        },
        {
            re: /^(\d+)\s+entries shown$/,
            toKm: function (m) { return 'បង្ហាញ ' + m[1] + ' ប្រតិបត្តិការ'; }
        },
        {
            re: /^Total Net Balance\s*\((.+)\)$/,
            toKm: function (m) { return 'សមតុល្យសុទ្ធសរុប (' + m[1] + ')'; }
        },
        {
            re: /^(\d+)\s+active account\(s\)$/,
            toKm: function (m) { return 'គណនីសកម្មចំនួន ' + m[1]; }
        }
    ];

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
            const isKm = (root.getAttribute('lang') || 'en') === 'km';
            btn.setAttribute('aria-label', isDark ? 'Switch to light theme' : 'Switch to dark theme');
            if (isKm) {
                btn.textContent = isDark ? '☀ ភ្លឺ' : '☾ ងងឹត';
            } else {
                btn.textContent = isDark ? '☀ Light' : '☾ Dark';
            }
        }
    }

    function getPreferredLang() {
        const params = new URLSearchParams(window.location.search);
        const qLang = params.get('lang');
        if (qLang === 'km' || qLang === 'en') {
            localStorage.setItem(LANG_STORAGE_KEY, qLang);
            return qLang;
        }
        const saved = localStorage.getItem(LANG_STORAGE_KEY);
        return saved === 'km' ? 'km' : 'en';
    }

    function translateNodeText(rawText, targetLang) {
        const trimmed = rawText.trim();
        if (!trimmed) return null;
        if (targetLang === 'km') {
            if (KM_DICT[trimmed]) {
                return rawText.replace(trimmed, KM_DICT[trimmed]);
            }
            for (let i = 0; i < KM_PATTERNS.length; i++) {
                const match = trimmed.match(KM_PATTERNS[i].re);
                if (match) {
                    return rawText.replace(trimmed, KM_PATTERNS[i].toKm(match));
                }
            }
        }
        return null;
    }

    const originalTextMap = new WeakMap();
    const originalPlaceholderMap = new WeakMap();

    function applyLanguage(lang) {
        root.setAttribute('lang', lang);
        const langBtn = document.getElementById('lang-toggle');
        if (langBtn) {
            langBtn.textContent = lang === 'km' ? '🇺🇸 EN' : '🇰🇭 ខ្មែរ';
            langBtn.setAttribute('aria-label', lang === 'km' ? 'Switch to English' : 'ប្តូរទៅភាសាខ្មែរ');
        }

        const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
        const textNodes = [];
        while (walker.nextNode()) {
            const node = walker.currentNode;
            const parentTag = node.parentElement ? node.parentElement.tagName : '';
            if (parentTag === 'SCRIPT' || parentTag === 'STYLE') continue;
            if (node.parentElement && (node.parentElement.id === 'lang-toggle' || node.parentElement.id === 'theme-toggle')) continue;
            textNodes.push(node);
        }

        textNodes.forEach(function (node) {
            if (!originalTextMap.has(node)) {
                originalTextMap.set(node, node.nodeValue);
            }
            const baseText = originalTextMap.get(node);
            if (lang === 'en') {
                node.nodeValue = baseText;
            } else {
                const translated = translateNodeText(baseText, 'km');
                if (translated !== null) {
                    node.nodeValue = translated;
                }
            }
        });

        document.querySelectorAll('input[placeholder], textarea[placeholder]').forEach(function (el) {
            if (!originalPlaceholderMap.has(el)) {
                originalPlaceholderMap.set(el, el.getAttribute('placeholder'));
            }
            const basePh = originalPlaceholderMap.get(el);
            if (lang === 'en') {
                el.setAttribute('placeholder', basePh);
            } else if (basePh) {
                const phMap = {
                    'Enter your password': 'បញ្ចូលពាក្យសម្ងាត់របស់អ្នក',
                    'Search description, category, account...': 'ស្វែងរកការពិពណ៌នា ប្រភេទ គណនី...',
                    'Search accounts...': 'ស្វែងរកគណនី...',
                    'Search categories...': 'ស្វែងរកប្រភេទ...',
                    'Search budgets...': 'ស្វែងរកថវិកា...',
                    'Search transfers...': 'ស្វែងរកការផ្ទេរប្រាក់...'
                };
                if (phMap[basePh]) {
                    el.setAttribute('placeholder', phMap[basePh]);
                }
            }
        });

        applyTheme(root.getAttribute('data-theme') || getPreferredTheme());
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

    function initViewToggles() {
        document.querySelectorAll('[data-view-toggle-group]').forEach(function (group) {
            const name = group.getAttribute('data-view-toggle-group');
            const storageKey = 'pf-view-' + name;
            const buttons = group.querySelectorAll('[data-view-mode]');
            const panes = document.querySelectorAll('[data-view-group="' + name + '"][data-view-pane]');
            if (buttons.length === 0 || panes.length === 0) return;

            function setViewMode(mode) {
                panes.forEach(function (pane) {
                    pane.classList.toggle('hidden-view', pane.getAttribute('data-view-pane') !== mode);
                });
                buttons.forEach(function (btn) {
                    const active = btn.getAttribute('data-view-mode') === mode;
                    btn.classList.toggle('active', active);
                    btn.setAttribute('aria-pressed', active ? 'true' : 'false');
                });
                localStorage.setItem(storageKey, mode);
            }

            buttons.forEach(function (btn) {
                btn.addEventListener('click', function () {
                    setViewMode(btn.getAttribute('data-view-mode'));
                });
            });

            setViewMode(localStorage.getItem(storageKey) === 'table' ? 'table' : 'grid');
        });
    }

    function initTransactionsPage() {
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
        applyLanguage(getPreferredLang());

        const toggleBtn = document.getElementById('theme-toggle');
        if (toggleBtn) {
            toggleBtn.addEventListener('click', function () {
                const next = root.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
                localStorage.setItem(STORAGE_KEY, next);
                applyTheme(next);
            });
        }

        const langBtn = document.getElementById('lang-toggle');
        if (langBtn) {
            langBtn.addEventListener('click', function () {
                const nextLang = (root.getAttribute('lang') || 'en') === 'km' ? 'en' : 'km';
                localStorage.setItem(LANG_STORAGE_KEY, nextLang);
                applyLanguage(nextLang);
            });
        }

        initAutoSubmitSelects();
        initViewToggles();
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
