/* QeSuite — Trial Balance, Period-End tasks, FX revaluation, P&L, Balance Sheet, Cash Flow,
   Close period, T-Account, Sub-ledger, Audit trail, IAS 1 check, Comparative TB */

/* ===================== TRIAL BALANCE ===================== */
const TrialBalance = ({ onNav }) => {
  const { TRIAL_BALANCE } = window.QSDATA;
  const [mode, setMode] = useState("UNADJUSTED");
  const drKey = mode === "ADJUSTED" ? "adj_dr" : "dr";
  const crKey = mode === "ADJUSTED" ? "adj_cr" : "cr";
  const drTotal = TRIAL_BALANCE.reduce((s, r) => s + r[drKey], 0);
  const crTotal = TRIAL_BALANCE.reduce((s, r) => s + r[crKey], 0);
  const variance = drTotal - crTotal;

  return (
    <div className="page">
      <PageHeader title="Trial Balance" meta="Period 2026-02 · As at 28 Feb 2026"
        tabs={[
          { id: "UNADJUSTED", label: "Unadjusted" },
          { id: "ADJUSTED", label: "Adjusted (post period-end)" },
          { id: "POST_CLOSING", label: "Post-closing" },
        ]} activeTab={mode} onTab={setMode}
        actions={<><Button size="sm" icon="download" variant="ghost">Export</Button><Button size="sm" icon="scale" variant="ghost" onClick={() => onNav("/comparative")}>Compare periods</Button></>}/>
      <div className="page-section stack">
        <Banner kind={Math.abs(variance) < 0.01 ? "success" : "warn"} icon={Math.abs(variance) < 0.01 ? "check" : "warn"}>
          {Math.abs(variance) < 0.01 ? <><strong>Balanced.</strong> Total debits equal total credits — IAS 1 double-entry integrity intact.</> :
                                       <><strong>Out of balance by {fmt(variance, { decimals: 2 })} KES.</strong> Investigate posting before closing.</>}
        </Banner>
        <div className="card">
          <div className="card-head">
            <Ico name="scale" size={13}/> {mode === "ADJUSTED" ? "Adjusted" : "Unadjusted"} Trial Balance
            <div className="h-meta">{TRIAL_BALANCE.length} accounts · {mode === "POST_CLOSING" ? "permanent only" : "all postable"}</div>
          </div>
          <table className="tbl">
            <thead>
              <tr><th>Code</th><th>Account</th><th className="right-h">Debit (KES)</th><th className="right-h">Credit (KES)</th><th></th></tr>
            </thead>
            <tbody>
              {TRIAL_BALANCE.filter(r => mode !== "POST_CLOSING" || (!r.code.startsWith("4") && !r.code.startsWith("5"))).map(r => (
                <tr key={r.code}>
                  <td className="code-cell">{r.code}</td>
                  <td><strong>{r.name}</strong></td>
                  <td className="num">{r[drKey] > 0 ? fmt(r[drKey], { decimals: 2 }) : "—"}</td>
                  <td className="num">{r[crKey] > 0 ? fmt(r[crKey], { decimals: 2 }) : "—"}</td>
                  <td><Button size="sm" variant="ghost" icon="external" onClick={() => onNav("/t-account")}>T-account</Button></td>
                </tr>
              ))}
              <tr className="total-row">
                <td colSpan="2">Totals</td>
                <td className="num">{fmt(drTotal, { decimals: 2 })}</td>
                <td className="num">{fmt(crTotal, { decimals: 2 })}</td>
                <td><Badge status="approved" dot><Ico name="check" size={9}/> {fmt(variance, { decimals: 2 })}</Badge></td>
              </tr>
            </tbody>
          </table>
          <TableFooter total={TRIAL_BALANCE.length} label="accounts" defaultSize={50}/>
        </div>
      </div>
    </div>
  );
};

/* ===================== PERIOD-END TASKS ===================== */
const PeriodEnd = ({ onNav }) => {
  const tasks = [
    { id: 1, name: "Unadjusted Trial Balance generated",    status: "DONE",  who: "system",      ts: "2026-02-28 06:00", link: "/trial-balance" },
    { id: 2, name: "Transition period to ADJUSTING",        status: "DONE",  who: "w.njeri",     ts: "2026-02-28 09:00", link: "/periods" },
    { id: 3, name: "Accrue utilities (Feb)",                status: "PENDING", who: "m.karanja", ts: null, link: "/journals" },
    { id: 4, name: "Prepaid expense amortization (batch)",  status: "PENDING_APPROVAL", who: "w.njeri", ts: "2026-02-28 11:02", link: "/journals" },
    { id: 5, name: "Recognize unearned revenue (IFRS 15)",  status: "DONE",  who: "system",      ts: "2026-02-28 12:30", link: "/journals" },
    { id: 6, name: "Batch depreciation — 5 assets",         status: "DONE",  who: "system",      ts: "2026-02-28 23:55", link: "/depreciation" },
    { id: 7, name: "FX revaluation (IAS 21)",               status: "DONE",  who: "system",      ts: "2026-02-28 23:58", link: "/fx" },
    { id: 8, name: "Adjusted Trial Balance regenerated",    status: "DONE",  who: "system",      ts: "2026-02-28 23:59", link: "/trial-balance" },
    { id: 9, name: "Profit & Loss draft",                   status: "READY", who: null,          ts: null, link: "/pnl" },
    { id: 10, name: "Balance Sheet draft",                  status: "READY", who: null,          ts: null, link: "/balance-sheet" },
    { id: 11, name: "Cash Flow Statement (IAS 7)",          status: "READY", who: null,          ts: null, link: "/cash-flow" },
    { id: 12, name: "IAS 1 compliance check",               status: "READY", who: null,          ts: null, link: "/ias1" },
    { id: 13, name: "Transition period to CLOSING",         status: "BLOCKED", who: null,        ts: null, link: "/close" },
    { id: 14, name: "Post closing entries (zero temp accts)", status: "BLOCKED", who: null,      ts: null, link: "/close" },
    { id: 15, name: "Post-closing Trial Balance",           status: "BLOCKED", who: null,        ts: null, link: "/trial-balance" },
  ];
  const done = tasks.filter(t => t.status === "DONE").length;

  return (
    <div className="page">
      <PageHeader title="Period-End Workflow" meta={`Period 2026-02 · ${done}/${tasks.length} tasks complete`}
        actions={<><Button size="sm" icon="branch" variant="ghost">Validate cycle</Button><Button size="sm" icon="play" variant="primary">Run 9-step cycle</Button></>}/>
      <div className="page-section stack">
        <div className="card">
          <div className="card-head"><Ico name="branch" size={13}/> Checklist</div>
          <div className="card-body no-pad">
            <table className="tbl">
              <thead><tr><th></th><th>Task</th><th>Status</th><th>By</th><th>At</th><th></th></tr></thead>
              <tbody>
                {tasks.map(t => (
                  <tr key={t.id}>
                    <td className="checkbox-cell"><span className="mono muted" style={{ fontSize: 10 }}>{String(t.id).padStart(2,'0')}</span></td>
                    <td><strong>{t.name}</strong></td>
                    <td>
                      <Badge status={
                        t.status === "DONE" ? "approved" :
                        t.status === "PENDING" ? "draft" :
                        t.status === "PENDING_APPROVAL" ? "pending" :
                        t.status === "READY" ? "info" : "archived"
                      }>{t.status.replace("_"," ")}</Badge>
                    </td>
                    <td className="muted mono" style={{ fontSize: 11 }}>{t.who || "—"}</td>
                    <td className="muted mono" style={{ fontSize: 11 }}>{t.ts || "—"}</td>
                    <td><Button size="sm" variant="ghost" icon="external" onClick={() => onNav(t.link)}>Open</Button></td>
                  </tr>
                ))}
              </tbody>
            </table>
            <TableFooter total={tasks.length} label="tasks" defaultSize={25}/>
          </div>
        </div>
      </div>
    </div>
  );
};

/* ===================== FX REVALUATION ===================== */
const FXReval = () => {
  const { FX_RATES } = window.QSDATA;
  const items = [
    { code: "1-1105", name: "Cash & Bank — USD",      currency: "USD", balance_fc: 4_200,   prior_rate: 130.0200, new_rate: 129.4500, prior_lc: 546_084,  new_lc: 543_690,  delta: -2_394 },
    { code: "1-1200", name: "Accounts Receivable",    currency: "USD", balance_fc: 11_900,  prior_rate: 130.0200, new_rate: 129.4500, prior_lc: 1_547_238, new_lc: 1_540_455, delta: -6_783 },
    { code: "2-1100", name: "Accounts Payable",       currency: "USD", balance_fc: 2_140,   prior_rate: 130.0200, new_rate: 129.4500, prior_lc: 278_242,   new_lc: 277_023,  delta: +1_219 },
    { code: "1-1200", name: "Accounts Receivable",    currency: "EUR", balance_fc: 950,     prior_rate: 141.3800, new_rate: 140.2210, prior_lc: 134_311,   new_lc: 133_210,  delta: -1_101 },
  ];
  const total = items.reduce((s, i) => s + i.delta, 0);
  return (
    <div className="page">
      <PageHeader title="FX Revaluation (IAS 21)" meta="Month-end · 28 Feb 2026 · CBK rates"
        actions={<><Button size="sm" icon="refresh" variant="ghost">Refresh rates</Button><Button size="sm" icon="play" variant="primary">Post revaluation JE</Button></>}/>
      <div className="page-section stack">
        <Banner kind="info">Foreign-currency monetary assets/liabilities are re-translated at closing rate. Resulting <strong>{total > 0 ? "gain" : "loss"}</strong> posts to <code className="mono">5-9000 FX Loss/Gain</code> per IAS 21.</Banner>
        <div className="row-3">
          <div className="card"><div className="card-body"><div className="kpi-label">USD closing</div><div className="kpi-value">{FX_RATES[0].rate.toFixed(4)}<span className="unit">KES</span></div><div className="kpi-delta neg"><Ico name="trend-down" size={11}/> −0.44% vs prior month</div></div></div>
          <div className="card"><div className="card-body"><div className="kpi-label">EUR closing</div><div className="kpi-value">{FX_RATES[1].rate.toFixed(4)}<span className="unit">KES</span></div><div className="kpi-delta neg"><Ico name="trend-down" size={11}/> −0.82% vs prior month</div></div></div>
          <div className="card"><div className="card-body"><div className="kpi-label">GBP closing</div><div className="kpi-value">{FX_RATES[2].rate.toFixed(4)}<span className="unit">KES</span></div><div className="kpi-delta pos"><Ico name="trend-up" size={11}/> +0.12% vs prior month</div></div></div>
        </div>
        <div className="card">
          <div className="card-head"><Ico name="fx" size={13}/> Monetary items requiring revaluation</div>
          <table className="tbl">
            <thead><tr><th>Account</th><th>FC</th><th className="right-h">FC Balance</th><th className="right-h">Prior rate</th><th className="right-h">New rate</th><th className="right-h">Prior KES</th><th className="right-h">New KES</th><th className="right-h">Delta</th></tr></thead>
            <tbody>
              {items.map((i, idx) => (
                <tr key={idx}>
                  <td className="code-cell">{i.code} · {i.name}</td>
                  <td className="mono">{i.currency}</td>
                  <td className="num">{fmt(i.balance_fc, { decimals: 2 })}</td>
                  <td className="num muted">{i.prior_rate.toFixed(4)}</td>
                  <td className="num">{i.new_rate.toFixed(4)}</td>
                  <td className="num muted">{fmt(i.prior_lc, { decimals: 2 })}</td>
                  <td className="num">{fmt(i.new_lc, { decimals: 2 })}</td>
                  <td className={`num ${i.delta < 0 ? "neg" : "pos"}`}>{fmt(i.delta, { decimals: 2, signed: true })}</td>
                </tr>
              ))}
              <tr className="total-row">
                <td colSpan="7">Net FX impact (Loss to <code className="mono">5-9000</code>)</td>
                <td className={`num ${total < 0 ? "neg" : "pos"}`}>{fmt(total, { decimals: 2, signed: true })}</td>
              </tr>
            </tbody>
          </table>
          <TableFooter total={items.length} label="items"/>
        </div>
      </div>
    </div>
  );
};

/* ===================== STATEMENTS (P&L, BS, CASH FLOW) ===================== */
const Statement = ({ title, period, data, onNav, kind }) => {
  return (
    <div className="page">
      <PageHeader title={title} meta={period}
        actions={<><Button size="sm" icon="download" variant="ghost">PDF</Button><Button size="sm" icon="download" variant="ghost">Excel</Button><Button size="sm" icon="external" variant="ghost" onClick={() => onNav("/ias1")}>IAS 1 check</Button></>}/>
      <div className="page-section">
        <div className="statement">
          <div className="statement-head">
            <h2>{title}</h2>
            <div className="e-name">Apollo Enterprises Limited · ORG-1A3F</div>
            <div className="e-period">{period}</div>
          </div>
          <div className="st-row st-header" style={{ marginTop: 16, marginBottom: 6, fontSize: 10.5, color: "var(--muted)", fontWeight: 700, letterSpacing: "0.05em", textTransform: "uppercase" }}>
            <div></div>
            <div className="st-num">Current period</div>
            <div className="st-num dim">Prior period</div>
          </div>
          {data.sections.map((r, i) => <StRow key={i} {...r}/>)}
          <div style={{ marginTop: 24, fontSize: 10.5, color: "var(--muted)", borderTop: "1px solid var(--border)", paddingTop: 12, display: "flex", justifyContent: "space-between" }}>
            <span>All figures in Kenyan Shillings (KES) · Functional & reporting currency</span>
            <span className="mono">Generated 28 Feb 2026 · QeSuite IFRS</span>
          </div>
        </div>
      </div>
    </div>
  );
};

const ProfitLoss = ({ onNav }) => <Statement title="Statement of Profit & Loss" period={window.QSDATA.PnL.period} data={window.QSDATA.PnL} onNav={onNav} kind="pnl"/>;
const BalanceSheet = ({ onNav }) => <Statement title="Statement of Financial Position" period={`As at ${window.QSDATA.BS.asOf}`} data={window.QSDATA.BS} onNav={onNav} kind="bs"/>;
const CashFlowStmt = ({ onNav }) => <Statement title="Statement of Cash Flows (IAS 7 — Indirect)" period={window.QSDATA.CASHFLOW.period} data={window.QSDATA.CASHFLOW} onNav={onNav} kind="cf"/>;

/* ===================== CLOSE PERIOD ===================== */
const ClosePeriod = ({ onNav }) => {
  return (
    <div className="page">
      <PageHeader title="Close Period · 2026-02" meta="Phase: CLOSING (after adjusting)"
        actions={<Button size="sm" icon="lock" variant="primary">Post closing entries</Button>}/>
      <div className="page-section stack">
        <Banner kind="warn"><strong>Closing entries</strong> will zero out temporary accounts (revenue, expense) and roll the net result into <code className="mono">3-2000 Retained Earnings</code>. This is irreversible without a senior reopen.</Banner>
        <div className="card">
          <div className="card-head"><Ico name="branch" size={13}/> Closing journal entry preview</div>
          <table className="tbl">
            <thead><tr><th>Account</th><th>Memo</th><th className="right-h">DR</th><th className="right-h">CR</th></tr></thead>
            <tbody>
              <tr><td className="code-cell">4-1000 · Service Revenue</td><td>Close revenue</td><td className="num">1,944,700.00</td><td className="num">—</td></tr>
              <tr><td className="code-cell">4-2000 · Subscription Revenue</td><td>Close revenue</td><td className="num">240,000.00</td><td className="num">—</td></tr>
              <tr><td className="code-cell">5-1000 · Cost of Sales</td><td>Close expense</td><td className="num">—</td><td className="num">320,400.00</td></tr>
              <tr><td className="code-cell">5-2000 · Salaries & Wages</td><td>Close expense</td><td className="num">—</td><td className="num">624,000.00</td></tr>
              <tr><td className="code-cell">5-3000 · Operating Expenses</td><td>Close expense</td><td className="num">—</td><td className="num">198,400.00</td></tr>
              <tr><td className="code-cell">5-3100 · Depreciation Expense</td><td>Close expense</td><td className="num">—</td><td className="num">124,400.00</td></tr>
              <tr><td className="code-cell">5-9000 · Gain/Loss on Disposal & FX</td><td>Close expense</td><td className="num">—</td><td className="num">30,920.00</td></tr>
              <tr><td className="code-cell">3-2000 · Retained Earnings</td><td>Net profit to equity</td><td className="num">—</td><td className="num">886,580.00</td></tr>
              <tr className="total-row"><td colSpan="2">Totals</td><td className="num">2,184,700.00</td><td className="num">2,184,700.00</td></tr>
            </tbody>
          </table>
        </div>
        <div className="card">
          <div className="card-head"><Ico name="check" size={13}/> Pre-close gates</div>
          <div className="card-body">
            {[
              { name: "Adjusted Trial Balance balanced", pass: true },
              { name: "All journal entries POSTED (no PENDING_APPROVAL)", pass: false },
              { name: "Bank reconciliations complete", pass: true },
              { name: "AR Ageing reviewed", pass: true },
              { name: "FX revaluation posted", pass: true },
              { name: "Depreciation batch posted", pass: true },
              { name: "Statements drafted (P&L, BS, CF)", pass: true },
            ].map((g, i) => (
              <div key={i} className="h-row" style={{ padding: "6px 0", borderBottom: i < 6 ? "1px dashed var(--border)" : "0", justifyContent: "space-between" }}>
                <span>{g.name}</span>
                {g.pass ? <Badge status="approved" dot><Ico name="check" size={9}/> PASS</Badge> : <Badge status="pending" dot><Ico name="warn" size={9}/> ATTENTION</Badge>}
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

/* ===================== T-ACCOUNT VIEW ===================== */
const TAccountView = ({ onNav }) => {
  const [acct, setAcct] = useState("1-1100");
  const accounts = window.QSDATA.COA.filter(a => a.type === "POST");
  const sample = {
    "1-1100": {
      dr: [
        { date: "2026-02-01", ref: "OB", amount: 1_833_980 },
        { date: "2026-02-12", ref: "PAY-2026-0011", amount: 371_200 },
        { date: "2026-02-22", ref: "PAY-2026-0012", amount: 100_000 },
        { date: "2026-02-15", ref: "JE-2026-0041", amount: 4_500 },
      ],
      cr: [
        { date: "2026-02-25", ref: "JE-2026-0042", amount: 553_000 },
        { date: "2026-02-04", ref: "PAY-OUT-0001", amount: 18_400 },
      ],
    },
  };
  const data = sample[acct] || { dr: [], cr: [] };
  const drT = data.dr.reduce((s, l) => s + l.amount, 0);
  const crT = data.cr.reduce((s, l) => s + l.amount, 0);
  return (
    <div className="page">
      <PageHeader title="T-Account View" meta="Visual debit/credit ledger"/>
      <div className="page-section stack">
        <div className="card">
          <div className="card-head" style={{ gap: 12 }}>
            <span>Account</span>
            <select className="select" style={{ maxWidth: 360 }} value={acct} onChange={(e) => setAcct(e.target.value)}>
              {accounts.map(a => <option key={a.code} value={a.code}>{a.code} · {a.name}</option>)}
            </select>
            <div className="h-meta">Period 2026-02 · Balance {fmt(drT - crT, { currency: "KES", decimals: 2, signed: true })}</div>
          </div>
          <div className="card-body">
            <TAccount accountCode={acct} accountName={accounts.find(a => a.code === acct)?.name || ""} drLines={data.dr} crLines={data.cr}/>
          </div>
        </div>
      </div>
    </div>
  );
};

/* ===================== SUB-LEDGER ===================== */
const SubLedger = ({ onNav }) => {
  const { CUSTOMERS, SUPPLIERS } = window.QSDATA;
  const [tab, setTab] = useState("AR");
  return (
    <div className="page">
      <PageHeader title="Sub-Ledgers" meta="Customer AR & Supplier AP balances by counterparty"
        tabs={[{ id: "AR", label: "Customer AR" }, { id: "AP", label: "Supplier AP" }, { id: "FA", label: "Fixed Asset" }]}
        activeTab={tab} onTab={setTab}/>
      <div className="page-section">
        {tab === "AR" && (
          <div className="card">
            <table className="tbl">
              <thead><tr><th>Customer</th><th>Currency</th><th>Open invoices</th><th className="right-h">Balance</th><th>DSO</th><th>Credit utilization</th></tr></thead>
              <tbody>
                {CUSTOMERS.filter(c => c.balance > 0).map(c => (
                  <tr key={c.id} className="clickable">
                    <td><strong>{c.name}</strong><div className="muted mono" style={{ fontSize: 10.5 }}>{c.code}</div></td>
                    <td className="mono">{c.currency}</td>
                    <td className="num">{Math.ceil(Math.random() * 3) + 1}</td>
                    <td className="num">{fmt(c.balance, { decimals: 0 })}</td>
                    <td className="mono">{Math.round(Math.random() * 20) + 22}d</td>
                    <td><div className="bar" style={{ width: 90 }}><span style={{ width: `${Math.min(100,(c.balance / c.creditLimit) * 100)}%`, background: c.balance / c.creditLimit > 0.7 ? "var(--warn)" : "var(--accent)" }}/></div></td>
                  </tr>
                ))}
              </tbody>
            </table>
            <TableFooter total={CUSTOMERS.filter(c => c.balance > 0).length} label="customers"/>
          </div>
        )}
        {tab === "AP" && (
          <div className="card">
            <table className="tbl">
              <thead><tr><th>Supplier</th><th>Currency</th><th>Open bills</th><th className="right-h">Balance owed</th><th>Terms</th><th>Next due</th></tr></thead>
              <tbody>
                {SUPPLIERS.filter(s => s.balance > 0).map(s => (
                  <tr key={s.id}>
                    <td><strong>{s.name}</strong><div className="muted mono" style={{ fontSize: 10.5 }}>{s.code}</div></td>
                    <td className="mono">{s.currency}</td>
                    <td className="num">{Math.ceil(Math.random() * 3) + 1}</td>
                    <td className="num">{fmt(s.balance, { decimals: 0 })}</td>
                    <td className="mono muted">{s.terms}</td>
                    <td className="mono muted">{fmtDate(s.lastBill)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <TableFooter total={SUPPLIERS.filter(s => s.balance > 0).length} label="suppliers"/>
          </div>
        )}
        {tab === "FA" && (
          <div className="card">
            <table className="tbl">
              <thead><tr><th>Tag</th><th>Asset</th><th className="right-h">Cost</th><th className="right-h">Accum.</th><th className="right-h">Net Book</th></tr></thead>
              <tbody>
                {window.QSDATA.ASSETS.map(a => (
                  <tr key={a.id}>
                    <td className="code-cell">{a.tag}</td>
                    <td>{a.name}</td>
                    <td className="num">{fmt(a.cost, { decimals: 0 })}</td>
                    <td className="num muted">{fmt(a.accum, { decimals: 0 })}</td>
                    <td className="num" style={{ fontWeight: 600 }}>{fmt(a.netBook, { decimals: 0 })}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <TableFooter total={window.QSDATA.ASSETS.length} label="assets"/>
          </div>
        )}
      </div>
    </div>
  );
};

/* ===================== AUDIT TRAIL ===================== */
const AuditTrail = ({ onNav }) => {
  const { AUDIT } = window.QSDATA;
  return (
    <div className="page">
      <PageHeader title="Audit Trail" meta={`${AUDIT.length} events · immutable log`}
        actions={<><Button size="sm" icon="download" variant="ghost">Export CSV</Button><Button size="sm" icon="filter" variant="ghost">Filters</Button></>}/>
      <div className="page-section">
        <div className="card">
          <table className="tbl">
            <thead><tr><th>Timestamp</th><th>Actor</th><th>Action</th><th>Target</th><th>Detail</th></tr></thead>
            <tbody>
              {AUDIT.map((a, i) => (
                <tr key={i}>
                  <td className="mono muted" style={{ fontSize: 11 }}>{a.ts}</td>
                  <td><span className="mono">{a.actor}</span></td>
                  <td><Badge status={
                    a.action === "POST" ? "posted" :
                    a.action === "APPROVE" ? "approved" :
                    a.action === "REVERSE" ? "void" :
                    a.action === "LOGIN" ? "info" : "draft"
                  } dot={false}>{a.action}</Badge></td>
                  <td className="code-cell">{a.target}</td>
                  <td className="muted">{a.detail}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <TableFooter total={AUDIT.length} label="events" defaultSize={50}/>
        </div>
      </div>
    </div>
  );
};

/* ===================== IAS 1 COMPLIANCE ===================== */
const IAS1Check = () => {
  const { IAS1_CHECKS } = window.QSDATA;
  const pass = IAS1_CHECKS.filter(c => c.status === "PASS").length;
  return (
    <div className="page">
      <PageHeader title="IAS 1 Compliance Check" meta={`${pass} of ${IAS1_CHECKS.length} checks pass · for period 2026-02`}/>
      <div className="page-section">
        <div className="card">
          <div className="card-head"><Ico name="shield" size={13}/> Disclosure & presentation requirements</div>
          <div className="card-body no-pad">
            {IAS1_CHECKS.map(c => (
              <div key={c.id} style={{ display: "flex", alignItems: "center", gap: 12, padding: "10px 14px", borderBottom: "1px solid var(--border-2)" }}>
                <div style={{ width: 32, height: 32, borderRadius: 8, background: c.status === "PASS" ? "color-mix(in oklab, var(--pos) 14%, transparent)" : "color-mix(in oklab, var(--warn) 14%, transparent)", color: c.status === "PASS" ? "var(--pos)" : "var(--warn)", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                  <Ico name={c.status === "PASS" ? "check" : "warn"} size={14}/>
                </div>
                <div style={{ flex: 1 }}>
                  <div style={{ fontWeight: 600, fontSize: 12.5 }}>{c.name}</div>
                  <div className="muted" style={{ fontSize: 11.5, marginTop: 2 }}>{c.detail}</div>
                </div>
                <Badge status={c.status === "PASS" ? "approved" : "pending"}>{c.status}</Badge>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

/* ===================== COMPARATIVE TB ===================== */
const ComparativeTB = () => {
  const { TRIAL_BALANCE } = window.QSDATA;
  return (
    <div className="page">
      <PageHeader title="Comparative Trial Balance" meta="2026-01 vs 2026-02"/>
      <div className="page-section">
        <div className="card">
          <table className="tbl">
            <thead>
              <tr>
                <th rowSpan="2">Code</th><th rowSpan="2">Account</th>
                <th colSpan="2" style={{ textAlign: "center", borderBottom: "1px solid var(--border)" }}>Period 2026-01 (Closed)</th>
                <th colSpan="2" style={{ textAlign: "center", borderBottom: "1px solid var(--border)" }}>Period 2026-02 (Adjusting)</th>
                <th rowSpan="2" className="right-h">Δ Net</th>
              </tr>
              <tr><th className="right-h">DR</th><th className="right-h">CR</th><th className="right-h">DR</th><th className="right-h">CR</th></tr>
            </thead>
            <tbody>
              {TRIAL_BALANCE.slice(0, 15).map(r => {
                const priorNet = r.dr - r.cr;
                const currNet = r.adj_dr - r.adj_cr;
                const delta = currNet - priorNet;
                return (
                  <tr key={r.code}>
                    <td className="code-cell">{r.code}</td>
                    <td>{r.name}</td>
                    <td className="num muted">{r.dr ? fmt(r.dr * 0.95, { decimals: 0 }) : "—"}</td>
                    <td className="num muted">{r.cr ? fmt(r.cr * 0.95, { decimals: 0 }) : "—"}</td>
                    <td className="num">{r.adj_dr ? fmt(r.adj_dr, { decimals: 0 }) : "—"}</td>
                    <td className="num">{r.adj_cr ? fmt(r.adj_cr, { decimals: 0 }) : "—"}</td>
                    <td className={`num ${delta >= 0 ? "pos" : "neg"}`}>{fmt(delta, { decimals: 0, signed: true })}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <TableFooter total={TRIAL_BALANCE.slice(0, 15).length} label="accounts"/>
        </div>
      </div>
    </div>
  );
};

Object.assign(window, { TrialBalance, PeriodEnd, FXReval, ProfitLoss, BalanceSheet, CashFlowStmt, ClosePeriod, TAccountView, SubLedger, AuditTrail, IAS1Check, ComparativeTB });
