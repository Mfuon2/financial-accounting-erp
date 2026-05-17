/* QeSuite — Main app shell, router, tweaks. */

const { useState: useStateMain, useEffect: useEffectMain } = React;

// Route → component mapping
const ROUTE_COMPONENTS = {
  "/dashboard":     (p) => <Dashboard {...p}/>,
  "/approvals":     (p) => <Approvals {...p}/>,
  "/coa":           (p) => <ChartOfAccounts {...p}/>,
  "/periods":       (p) => <Periods {...p}/>,
  "/journals":      (p) => <Journals {...p}/>,
  "/source-docs":   (p) => <SourceDocs {...p}/>,
  "/customers":     (p) => <Customers {...p}/>,
  "/suppliers":     (p) => <Suppliers {...p}/>,
  "/assets":        (p) => <Assets {...p}/>,
  "/depreciation":  (p) => <DepreciationRun {...p}/>,
  "/invoices":      (p) => <Invoices {...p}/>,
  "/credit-notes":  (p) => <CreditNotes {...p}/>,
  "/payments":      (p) => <Payments {...p}/>,
  "/receipts":      (p) => <Receipts {...p}/>,
  "/ar-ageing":     (p) => <ARAgeing {...p}/>,
  "/trial-balance": (p) => <TrialBalance {...p}/>,
  "/period-end":    (p) => <PeriodEnd {...p}/>,
  "/fx":            (p) => <FXReval {...p}/>,
  "/pnl":           (p) => <ProfitLoss {...p}/>,
  "/balance-sheet": (p) => <BalanceSheet {...p}/>,
  "/cash-flow":     (p) => <CashFlowStmt {...p}/>,
  "/close":         (p) => <ClosePeriod {...p}/>,
  "/t-account":     (p) => <TAccountView {...p}/>,
  "/sub-ledger":    (p) => <SubLedger {...p}/>,
  "/audit":         (p) => <AuditTrail {...p}/>,
  "/ias1":          (p) => <IAS1Check {...p}/>,
  "/comparative":   (p) => <ComparativeTB {...p}/>,
  "/organization":  (p) => <Organization {...p}/>,
  "/users":         (p) => <Users {...p}/>,
  "/api-keys":      (p) => <ApiKeys {...p}/>,
  "/tax":           (p) => <TaxCurrency {...p}/>,
  "/security":      (p) => <Security {...p}/>,
};

const CRUMBS = {
  "/dashboard":     ["Overview", "Dashboard"],
  "/approvals":     ["Overview", "Approvals"],
  "/coa":           ["Ledger", "Chart of Accounts"],
  "/periods":       ["Ledger", "Periods"],
  "/journals":      ["Ledger", "Journal Entries"],
  "/source-docs":   ["Ledger", "Source Documents"],
  "/customers":     ["Parties", "Customers"],
  "/suppliers":     ["Parties", "Suppliers"],
  "/assets":        ["Assets", "Fixed Asset Register"],
  "/depreciation":  ["Assets", "Depreciation Run"],
  "/invoices":      ["Revenue", "Invoices"],
  "/credit-notes":  ["Revenue", "Credit Notes"],
  "/payments":      ["Revenue", "Payments"],
  "/receipts":      ["Revenue", "Receipts"],
  "/ar-ageing":     ["Revenue", "AR Ageing"],
  "/trial-balance": ["Period-End", "Trial Balance"],
  "/period-end":    ["Period-End", "Workflow"],
  "/fx":            ["Period-End", "FX Revaluation"],
  "/pnl":           ["Statements", "Profit & Loss"],
  "/balance-sheet": ["Statements", "Balance Sheet"],
  "/cash-flow":     ["Statements", "Cash Flow"],
  "/close":         ["Statements", "Close Period"],
  "/t-account":     ["Reports", "T-Account"],
  "/sub-ledger":    ["Reports", "Sub-Ledgers"],
  "/audit":         ["Reports", "Audit Trail"],
  "/ias1":          ["Reports", "IAS 1 Compliance"],
  "/comparative":   ["Reports", "Comparative TB"],
  "/organization":  ["Setup", "Organization"],
  "/users":         ["Setup", "Users"],
  "/api-keys":      ["Setup", "API Keys"],
  "/tax":           ["Setup", "Tax & Currency"],
  "/security":      ["Setup", "Security"],
};

const PALETTE_ITEMS = [
  ...NAV_ROUTES.map(r => ({ icon: r.icon, label: r.label, route: r.route, group: "Navigate", tags: [r.group] })),
  { icon: "plus", label: "New journal entry", route: "/journals", group: "Quick actions", meta: "J" },
  { icon: "plus", label: "New invoice", route: "/invoices", group: "Quick actions", meta: "I" },
  { icon: "card", label: "Record a payment", route: "/payments", group: "Quick actions", meta: "P" },
  { icon: "play", label: "Run depreciation batch", route: "/depreciation", group: "Quick actions" },
  { icon: "fx",   label: "Run FX revaluation", route: "/fx", group: "Quick actions" },
  { icon: "lock", label: "Close period 2026-02", route: "/close", group: "Quick actions" },
  { icon: "doc",  label: "Generate Income Statement", route: "/pnl", group: "Quick actions" },
  { icon: "users",label: "Acme Corp", route: "/customers", group: "Customers", meta: "CUS-1001" },
  { icon: "users",label: "Karibu Hotels Group", route: "/customers", group: "Customers", meta: "CUS-1003" },
  { icon: "users",label: "Nimbus Logistics", route: "/customers", group: "Customers", meta: "CUS-1002" },
  { icon: "doc",  label: "INV-2026-0017", route: "/invoices", group: "Recent transactions", meta: "KES 290,000" },
  { icon: "journal", label: "JE-2026-0043 — Batch depreciation", route: "/journals", group: "Recent transactions", meta: "POSTED" },
  { icon: "journal", label: "JE-2026-0044 — FX revaluation", route: "/journals", group: "Recent transactions", meta: "POSTED" },
];

/* ============ TWEAKS PANEL ============ */
const TweaksUI = ({ tweaks, setTweak }) => {
  return (
    <TweaksPanel title="Look & feel">
      <TweakSection label="Theme">
        <TweakRadio
          label="Mode"
          value={tweaks.theme}
          onChange={(v) => setTweak("theme", v)}
          options={[{ value: "light", label: "Light" }, { value: "dark", label: "Dark" }]}
        />
        <TweakSelect
          label="Accent"
          value={tweaks.accent}
          onChange={(v) => setTweak("accent", v)}
          options={[
            { value: "emerald", label: "Emerald" },
            { value: "blue", label: "Blue" },
            { value: "violet", label: "Violet" },
            { value: "amber", label: "Amber" },
            { value: "graphite", label: "Graphite" },
          ]}
        />
      </TweakSection>
      <TweakSection label="Layout">
        <TweakRadio
          label="Density"
          value={tweaks.density}
          onChange={(v) => setTweak("density", v)}
          options={[{ value: "compact", label: "Compact" }, { value: "comfortable", label: "Comfortable" }]}
        />
        <TweakRadio
          label="Sidebar"
          value={tweaks.sidebar}
          onChange={(v) => setTweak("sidebar", v)}
          options={[{ value: "labeled", label: "Labeled" }, { value: "iconly", label: "Icons" }]}
        />
        <TweakRadio
          label="Tables"
          value={tweaks.tableStyle}
          onChange={(v) => setTweak("tableStyle", v)}
          options={[{ value: "lined", label: "Lined" }, { value: "zebra", label: "Zebra" }]}
        />
      </TweakSection>
      <TweakSection label="Typography">
        <TweakRadio
          label="Font"
          value={tweaks.font}
          onChange={(v) => setTweak("font", v)}
          options={[
            { value: "manrope", label: "Manrope" },
            { value: "geist", label: "Geist" },
            { value: "serif", label: "Serif" },
          ]}
        />
      </TweakSection>
    </TweaksPanel>
  );
};

/* ============ APP ============ */
const App = () => {
  const [authed, setAuthed] = useStateMain(true); // start signed in — easier to demo
  const [signup, setSignup] = useStateMain(false);
  const [route, setRoute] = useStateMain(() => location.hash.replace(/^#/, "") || "/dashboard");
  const [palette, setPalette] = useStateMain(false);

  const [tweaks, setTweak] = useTweaks(window.__TWEAKS_DEFAULTS);

  // Sync hash <-> route
  useEffectMain(() => {
    const onHash = () => setRoute(location.hash.replace(/^#/, "") || "/dashboard");
    window.addEventListener("hashchange", onHash);
    return () => window.removeEventListener("hashchange", onHash);
  }, []);
  const navigate = (r) => {
    location.hash = r;
    setRoute(r);
    document.querySelector(".main")?.scrollTo({ top: 0 });
  };

  // Apply tweaks to body
  useEffectMain(() => {
    document.body.dataset.theme = tweaks.theme;
    document.body.dataset.density = tweaks.density;
    document.body.dataset.accent = tweaks.accent;
    document.body.dataset.font = tweaks.font;
    document.body.dataset.tableStyle = tweaks.tableStyle;
  }, [tweaks]);

  // Cmd/Ctrl+K = command palette
  useEffectMain(() => {
    const h = (e) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault(); setPalette(true);
      }
      if (e.key === "/" && document.activeElement === document.body) {
        e.preventDefault(); setPalette(true);
      }
    };
    window.addEventListener("keydown", h);
    return () => window.removeEventListener("keydown", h);
  }, []);

  const currentPeriod = window.QSDATA.PERIODS.find(p => p.status === "ADJUSTING") || window.QSDATA.PERIODS.find(p => p.status === "OPEN");

  if (!authed) {
    return signup
      ? <Signup onLogin={() => { setAuthed(true); setSignup(false); navigate("/dashboard"); }} onCancel={() => setSignup(false)}/>
      : <Login onLogin={() => { setAuthed(true); navigate("/dashboard"); }} onSignup={() => setSignup(true)}/>;
  }

  const Page = ROUTE_COMPONENTS[route] || ROUTE_COMPONENTS["/dashboard"];
  const crumbs = CRUMBS[route] || ["Dashboard"];

  return (
    <div className="app" data-sidebar={tweaks.sidebar} data-table-style={tweaks.tableStyle}>
      <Sidebar activeRoute={route} onNavigate={navigate} sidebarMode={tweaks.sidebar}/>
      <Topbar
        crumbs={crumbs}
        onCommand={() => setPalette(true)}
        onTweak={() => window.postMessage({ type: '__activate_edit_mode' }, '*')}
        onSidebarToggle={() => setTweak("sidebar", tweaks.sidebar === "iconly" ? "labeled" : "iconly")}
        onLogout={() => setAuthed(false)}
        currentPeriod={currentPeriod}
      />
      <main className="main">
        {Page({ onNav: navigate })}
      </main>
      <CommandPalette open={palette} onClose={() => setPalette(false)} onNavigate={navigate} routes={PALETTE_ITEMS}/>
      <TweaksUI tweaks={tweaks} setTweak={setTweak}/>
    </div>
  );
};

ReactDOM.createRoot(document.getElementById("root")).render(<App/>);
