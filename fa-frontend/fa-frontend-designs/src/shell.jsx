/* QeSuite — App shell: Sidebar nav, Topbar, Command palette wire-up */

const NAV_ROUTES = [
  { id: "dashboard",   label: "Dashboard",          icon: "chart",   route: "/dashboard",   group: "Overview" },
  { id: "approvals",   label: "Approvals",          icon: "approve", route: "/approvals",   group: "Overview", count: () => window.QSDATA.APPROVALS.length },

  { id: "coa",         label: "Chart of Accounts",  icon: "ledger",  route: "/coa",         group: "Ledger" },
  { id: "periods",     label: "Periods",            icon: "calendar",route: "/periods",     group: "Ledger" },
  { id: "journals",    label: "Journal Entries",    icon: "journal", route: "/journals",    group: "Ledger" },
  { id: "source-docs", label: "Source Documents",   icon: "docs",    route: "/source-docs", group: "Ledger" },

  { id: "customers",   label: "Customers",          icon: "users",   route: "/customers",   group: "Parties" },
  { id: "suppliers",   label: "Suppliers",          icon: "truck",   route: "/suppliers",   group: "Parties" },

  { id: "assets",      label: "Fixed Assets",       icon: "asset",   route: "/assets",      group: "Assets" },
  { id: "depreciation",label: "Depreciation Run",   icon: "trend-down", route: "/depreciation", group: "Assets" },

  { id: "invoices",    label: "Invoices",           icon: "doc",     route: "/invoices",    group: "Revenue" },
  { id: "credit-notes",label: "Credit Notes",       icon: "receipt", route: "/credit-notes",group: "Revenue" },
  { id: "payments",    label: "Payments",           icon: "card",    route: "/payments",    group: "Revenue" },
  { id: "receipts",    label: "Receipts",           icon: "receipt", route: "/receipts",    group: "Revenue" },
  { id: "ar-ageing",   label: "AR Ageing",          icon: "clock",   route: "/ar-ageing",   group: "Revenue" },

  { id: "trial-balance",label:"Trial Balance",      icon: "scale",   route: "/trial-balance", group: "Period-End" },
  { id: "period-end",  label: "Period-End Tasks",   icon: "branch",  route: "/period-end",  group: "Period-End" },
  { id: "fx",          label: "FX Revaluation",     icon: "fx",      route: "/fx",          group: "Period-End" },

  { id: "pnl",         label: "Profit & Loss",      icon: "trend-up",route: "/pnl",         group: "Statements" },
  { id: "balance-sheet",label:"Balance Sheet",      icon: "scale",   route: "/balance-sheet", group: "Statements" },
  { id: "cash-flow",   label: "Cash Flow",          icon: "coin",    route: "/cash-flow",   group: "Statements" },
  { id: "close",       label: "Close Period",       icon: "lock",    route: "/close",       group: "Statements" },

  { id: "t-account",   label: "T-Account View",     icon: "ledger",  route: "/t-account",   group: "Reports" },
  { id: "sub-ledger",  label: "Sub-Ledgers",        icon: "branch",  route: "/sub-ledger",  group: "Reports" },
  { id: "audit",       label: "Audit Trail",        icon: "shield",  route: "/audit",       group: "Reports" },
  { id: "ias1",        label: "IAS 1 Compliance",   icon: "check",   route: "/ias1",        group: "Reports" },
  { id: "comparative", label: "Comparative TB",     icon: "scale",   route: "/comparative", group: "Reports" },

  { id: "organization",label: "Organization",       icon: "building",route: "/organization",group: "Setup" },
  { id: "users",       label: "Users",              icon: "users",   route: "/users",       group: "Setup" },
  { id: "api-keys",    label: "API Keys",           icon: "key",     route: "/api-keys",    group: "Setup" },
  { id: "tax",         label: "Tax & Currency",     icon: "tag",     route: "/tax",         group: "Setup" },
  { id: "security",    label: "Security",           icon: "shield",  route: "/security",    group: "Setup" },
];

const NAV_GROUPS_ORDER = ["Overview", "Ledger", "Parties", "Assets", "Revenue", "Period-End", "Statements", "Reports", "Setup"];

/* ============ SIDEBAR ============ */
const Sidebar = ({ activeRoute, onNavigate, sidebarMode }) => {
  const grouped = useMemo(() => {
    const g = {};
    NAV_ROUTES.forEach(r => { (g[r.group] = g[r.group] || []).push(r); });
    return g;
  }, []);

  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <div className="brand-mark"><span>Q</span></div>
        <div className="brand-name">QeSuite</div>
        <div className="brand-suffix">IFRS</div>
      </div>
      <div className="entity-switcher" title="Switch entity">
        <span className="entity-dot"/>
        <div className="ent-text">
          <div className="ent-name">Apollo Enterprises Ltd</div>
          <div className="ent-meta">ORG-1A3F · FY 2026 · KES</div>
        </div>
        <Ico name="chev-down" size={11} style={{ color: "var(--muted)" }}/>
      </div>
      <nav className="nav">
        {NAV_GROUPS_ORDER.map(g => (
          <div key={g} className="nav-group">
            <div className="nav-group-label">{g}</div>
            {(grouped[g] || []).map(item => {
              const isActive = activeRoute === item.route;
              const count = typeof item.count === "function" ? item.count() : null;
              return (
                <div key={item.id} className={`nav-item ${isActive ? "active" : ""}`} onClick={() => onNavigate(item.route)} title={item.label}>
                  <span className="nav-ico"><Ico name={item.icon} size={13}/></span>
                  <span>{item.label}</span>
                  {count != null && <span className="nav-count">{count}</span>}
                </div>
              );
            })}
          </div>
        ))}
      </nav>
      <div className="sidebar-foot">
        <div className="user-chip">
          <div className="user-avatar">JM</div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div className="u-name">Jane Muriuki</div>
            <div className="u-role">ADMIN · MFA on</div>
          </div>
        </div>
        <IconBtn icon="settings" title="Settings" onClick={() => onNavigate("/organization")}/>
      </div>
    </aside>
  );
};

/* ============ TOPBAR ============ */
const Topbar = ({ crumbs, onCommand, onTweak, onSidebarToggle, onLogout, currentPeriod }) => (
  <header className="topbar">
    <IconBtn icon="menu" title="Toggle sidebar" onClick={onSidebarToggle}/>
    <div className="crumbs">
      {crumbs.map((c, i) => (
        <React.Fragment key={i}>
          {i > 0 && <span className="crumb-sep">/</span>}
          <span className={i === crumbs.length - 1 ? "crumb-current" : ""}>{c}</span>
        </React.Fragment>
      ))}
    </div>
    <div className="spacer"/>
    <div className={`period-pill ${currentPeriod.status.toLowerCase()}`} title="Current period">
      <span className="p-status"/>
      <code>{currentPeriod.code}</code>
      <span className="muted">{currentPeriod.status}</span>
    </div>
    <button className="cmd-trigger" onClick={onCommand}>
      <Ico name="search" size={12}/>
      <span>Search or run a command…</span>
      <span className="kbd">⌘K</span>
    </button>
    <div className="topbar-icons">
      <IconBtn icon="bell" title="Notifications" hasDot/>
      <IconBtn icon="sliders" title="Tweaks panel" onClick={onTweak}/>
      <IconBtn icon="logout" title="Log out" onClick={onLogout}/>
    </div>
  </header>
);

Object.assign(window, { Sidebar, Topbar, NAV_ROUTES });
