/* QeSuite — Finance screens: Dashboard, COA, Periods, Customers, Suppliers, Assets, Depreciation, Organization, Tax, Approvals */

/* ===================== DASHBOARD ===================== */
const Dashboard = ({ onNav }) => {
  const { APPROVALS, AUDIT, INVOICES, AR_AGEING, SPARK_CASH, SPARK_AR, SPARK_REV, SPARK_EXP } = window.QSDATA;
  const totalAR = AR_AGEING.reduce((s, r) => s + r.total, 0);
  const overdue = AR_AGEING.reduce((s, r) => s + r.b1_30 + r.b31_60 + r.b61_90 + r.b90, 0);
  const dueWk = INVOICES.filter(i => i.status === "POSTED").slice(0, 3);

  return (
    <div className="page">
      <PageHeader
        title="Dashboard"
        meta="Apollo Enterprises Ltd · Fiscal Year 2026 · Functional KES"
        actions={
          <>
            <Button icon="download" size="sm" variant="ghost">Export</Button>
            <Button icon="refresh" size="sm" variant="ghost">Refresh</Button>
            <Button icon="plus" size="sm" variant="primary">New journal</Button>
          </>
        }
      />
      <div className="page-section stack">

        <Banner kind="warn" icon="warn" action={<Button size="sm" onClick={() => onNav("/period-end")}>Open tasks →</Button>}>
          <strong>Period 2026-02 is in ADJUSTING.</strong> <span className="muted">3 adjusting entries pending; FX revaluation posted; depreciation batch done. Trial balance variance: 0.00 KES.</span>
        </Banner>

        <div className="kpi-grid">
          <Kpi label="Cash & Equivalents" icon="bank"  value={fmt(2.71, { decimals: 2 })} unit="M KES" delta={+12.4} deltaLabel="vs prior month" spark={SPARK_CASH}/>
          <Kpi label="Accounts Receivable" icon="users" value={fmt(1.84, { decimals: 2 })} unit="M KES" delta={+8.2} deltaLabel="DSO 41d"  spark={SPARK_AR}/>
          <Kpi label="MTD Revenue" icon="trend-up" value={fmt(2.17, { decimals: 2 })} unit="M KES" delta={+9.9} deltaLabel="vs Jan"      spark={SPARK_REV}/>
          <Kpi label="Operating Expenses" icon="card" value={fmt(946.8, { decimals: 1 })} unit="K KES" delta={+3.1} deltaLabel="vs Jan"   spark={SPARK_EXP} sparkColor="var(--neg)"/>
        </div>

        <div className="row-2" style={{ gridTemplateColumns: "1.6fr 1fr" }}>
          <div className="card">
            <div className="card-head">
              <Ico name="chart" size={13}/> Revenue vs Expenses — Last 12 months
              <div className="h-meta">KES · in millions</div>
            </div>
            <div className="card-body" style={{ display: "flex", justifyContent: "center", gap: 24, alignItems: "center" }}>
              <LineChart
                series={[
                  { name: "Revenue", color: "var(--accent)", data: SPARK_REV },
                  { name: "Expenses", color: "var(--neg)", data: SPARK_EXP },
                ]}
                labels={["M03","M04","M05","M06","M07","M08","M09","M10","M11","M12","JAN","FEB"]}
                w={520} h={200}
              />
              <div className="stack" style={{ gap: 10, fontSize: 11.5 }}>
                <div className="h-row"><span className="dot" style={{ background: "var(--accent)" }}/> Revenue <span className="mono" style={{ marginLeft: "auto", fontWeight: 600 }}>2.17M</span></div>
                <div className="h-row"><span className="dot" style={{ background: "var(--neg)" }}/> Expenses <span className="mono" style={{ marginLeft: "auto", fontWeight: 600 }}>0.95M</span></div>
                <div className="divider" style={{ margin: "4px 0" }}/>
                <div className="h-row"><span style={{ fontWeight: 600 }}>Net</span> <span className="mono" style={{ marginLeft: "auto", fontWeight: 600, color: "var(--pos)" }}>+1.22M</span></div>
                <div className="h-row muted" style={{ fontSize: 10.5 }}>Margin 56.1%</div>
              </div>
            </div>
          </div>

          <div className="card">
            <div className="card-head">
              <Ico name="approve" size={13}/> Approvals Queue
              <span className="badge pending dot" style={{ marginLeft: 6 }}>{APPROVALS.length} pending</span>
              <div className="h-meta"><a onClick={() => onNav("/approvals")} style={{ cursor: "pointer", color: "var(--accent)" }}>View all →</a></div>
            </div>
            <div className="card-body no-pad">
              {APPROVALS.slice(0, 4).map(a => (
                <div key={a.id} className="approval-row">
                  <div>
                    <div className="ar-title">{a.title}</div>
                    <div className="ar-meta">{a.type} · {a.ref} · {a.currency} {fmt(a.amount, { decimals: 0 })} · by {a.submittedBy}</div>
                  </div>
                  <div className="ar-actions">
                    <Button size="sm" icon="reject" variant="ghost"></Button>
                    <Button size="sm" icon="approve" variant="primary">Approve</Button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="row-3">
          <div className="card">
            <div className="card-head">
              <Ico name="clock" size={13}/> AR Ageing buckets
              <div className="h-meta">{fmt(totalAR, { currency: "KES", decimals: 0 })}</div>
            </div>
            <div className="card-body">
              {[
                { l: "Current", v: AR_AGEING.reduce((s, r) => s + r.current, 0), c: "var(--pos)" },
                { l: "1–30 d", v: AR_AGEING.reduce((s, r) => s + r.b1_30, 0), c: "var(--info)" },
                { l: "31–60 d", v: AR_AGEING.reduce((s, r) => s + r.b31_60, 0), c: "var(--warn)" },
                { l: "61–90 d", v: AR_AGEING.reduce((s, r) => s + r.b61_90, 0), c: "var(--warn)" },
                { l: "90+ d", v: AR_AGEING.reduce((s, r) => s + r.b90, 0), c: "var(--neg)" },
              ].map((row, i) => (
                <div key={i} className="h-bar-row">
                  <div>
                    <div className="h-row" style={{ justifyContent: "space-between", marginBottom: 3 }}>
                      <span>{row.l}</span>
                      <span className="mono">{fmt(row.v, { decimals: 0 })}</span>
                    </div>
                    <div className="h-bar"><span style={{ width: `${(row.v / Math.max(totalAR, 1)) * 100}%`, background: row.c }}/></div>
                  </div>
                </div>
              ))}
              <Button size="sm" variant="ghost" icon="external" onClick={() => onNav("/ar-ageing")} className="" style={{ marginTop: 6 }}>Full AR ageing report</Button>
            </div>
          </div>

          <div className="card">
            <div className="card-head">
              <Ico name="ledger" size={13}/> Trial Balance Health
              <div className="h-meta">Period 2026-02</div>
            </div>
            <div className="card-body stack" style={{ gap: 14 }}>
              <div className="h-row" style={{ gap: 16, alignItems: "center" }}>
                <Donut segments={[
                  { value: 8412500, color: "var(--accent)" },
                  { value: 1984140, color: "var(--neg)" },
                  { value: 6428360, color: "var(--info)" },
                ]} size={110} thickness={16}/>
                <div style={{ flex: 1, fontSize: 12 }}>
                  <div className="h-row" style={{ justifyContent: "space-between" }}><span><StatusDot color="var(--accent)"/> Assets</span><span className="mono">8.41M</span></div>
                  <div className="h-row" style={{ justifyContent: "space-between" }}><span><StatusDot color="var(--neg)"/> Liabilities</span><span className="mono">1.98M</span></div>
                  <div className="h-row" style={{ justifyContent: "space-between" }}><span><StatusDot color="var(--info)"/> Equity</span><span className="mono">6.43M</span></div>
                  <div className="divider" style={{ margin: "8px 0 4px" }}/>
                  <div className="h-row" style={{ justifyContent: "space-between", fontWeight: 600 }}>
                    <span><Ico name="check" size={11} style={{ color: "var(--pos)" }}/> Balanced</span>
                    <span className="mono pos">0.00 KES</span>
                  </div>
                </div>
              </div>
              <Button size="sm" variant="ghost" icon="external" onClick={() => onNav("/trial-balance")}>Open trial balance</Button>
            </div>
          </div>

          <div className="card">
            <div className="card-head">
              <Ico name="shield" size={13}/> Recent activity
              <div className="h-meta"><a onClick={() => onNav("/audit")} style={{ cursor: "pointer", color: "var(--accent)" }}>Audit trail →</a></div>
            </div>
            <div className="card-body">
              <div className="timeline">
                {AUDIT.slice(0, 6).map((a, i) => (
                  <TimelineRow key={i} time={a.ts.split(" ")[1]} body={<><strong>{a.action}</strong> · {a.target} <span className="muted">— {a.detail}</span></>} actor={a.actor}/>
                ))}
              </div>
            </div>
          </div>
        </div>

        <div className="card">
          <div className="card-head">
            <Ico name="branch" size={13}/> 9-Step accounting cycle
            <div className="h-meta">Period 2026-02 progress</div>
          </div>
          <div className="card-body">
            <div className="stepper">
              {["Source Docs","Journalize","Post to Ledger","Trial Balance","Adjusting","Adj. Trial Bal.","Statements","Closing","Post-Closing TB"].map((s, i) => (
                <div key={i} className={`step ${i < 5 ? "done" : i === 5 ? "active" : ""}`}>
                  <div className="step-num">{i < 5 ? <Ico name="check" size={10}/> : i + 1}</div>
                  <div>{s}</div>
                  <div className="step-line"/>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

/* ===================== APPROVALS ===================== */
const Approvals = ({ onNav }) => {
  const { APPROVALS } = window.QSDATA;
  return (
    <div className="page">
      <PageHeader title="Approvals" meta={`${APPROVALS.length} items pending your review`}/>
      <div className="page-section">
        <div className="card">
          <div className="card-body no-pad">
            <table className="tbl">
              <thead>
                <tr>
                  <th>Type</th>
                  <th>Reference</th>
                  <th>Title</th>
                  <th className="right-h">Amount</th>
                  <th>Submitted by</th>
                  <th>Waiting for</th>
                  <th>Submitted</th>
                  <th className="right-h">Actions</th>
                </tr>
              </thead>
              <tbody>
                {APPROVALS.map(a => (
                  <tr key={a.id}>
                    <td><Badge status={a.type} dot={false}/></td>
                    <td className="code-cell"><a onClick={() => onNav(a.type.includes("Journal") ? "/journals" : a.type.includes("Payment") ? "/payments" : a.type.includes("Invoice") ? "/invoices" : "/source-docs")} style={{ color: "var(--accent)", cursor: "pointer" }}>{a.ref}</a></td>
                    <td>{a.title}</td>
                    <td className="num">{a.currency} {fmt(a.amount, { decimals: 0 })}</td>
                    <td className="muted">{a.submittedBy}</td>
                    <td className="muted">{a.waitingFor}</td>
                    <td className="muted mono" style={{ fontSize: 11 }}>{a.submittedAt}</td>
                    <td className="right-h"><Button size="sm" variant="ghost" icon="reject"/><Button size="sm" variant="primary" icon="approve">Approve</Button></td>
                  </tr>
                ))}
              </tbody>
            </table>
            <TableFooter total={APPROVALS.length} label="approvals"/>
          </div>
        </div>
      </div>
    </div>
  );
};

/* ===================== CHART OF ACCOUNTS ===================== */
const ChartOfAccounts = ({ onNav }) => {
  const { COA } = window.QSDATA;
  const [search, setSearch] = useState("");
  const [classFilter, setClassFilter] = useState("ALL");
  const [collapsed, setCollapsed] = useState(new Set());
  const [drawerAcct, setDrawerAcct] = useState(null);

  const depths = useMemo(() => {
    const d = {};
    COA.forEach(a => { d[a.code] = a.parent ? (d[a.parent] || 0) + 1 : 0; });
    return d;
  }, []);

  const visible = useMemo(() => {
    return COA.filter(a => {
      if (classFilter !== "ALL" && a.class !== classFilter) return false;
      if (search) {
        const q = search.toLowerCase();
        if (!a.code.toLowerCase().includes(q) && !a.name.toLowerCase().includes(q)) return false;
      }
      let p = a.parent;
      while (p) {
        if (collapsed.has(p)) return false;
        p = COA.find(x => x.code === p)?.parent;
      }
      return true;
    });
  }, [search, classFilter, collapsed]);

  const toggle = (c) => setCollapsed(s => { const n = new Set(s); n.has(c) ? n.delete(c) : n.add(c); return n; });

  const drExp = COA.filter(a => a.type === "POST" && (a.class === "ASSET" || a.class === "EXPENSE")).reduce((s, a) => s + Math.max(0, a.balance), 0);
  const crExp = COA.filter(a => a.type === "POST" && (a.class === "LIABILITY" || a.class === "EQUITY" || a.class === "REVENUE")).reduce((s, a) => s + Math.max(0, a.balance), 0);

  return (
    <div className="page">
      <PageHeader
        title="Chart of Accounts"
        meta={`${COA.filter(a => a.type === "POST").length} postable · ${COA.filter(a => a.type === "HEADER").length} headers · KES functional`}
        actions={
          <>
            <Button size="sm" icon="download" variant="ghost">Export CSV</Button>
            <Button size="sm" icon="branch" variant="ghost">Apply template</Button>
            <Button size="sm" icon="plus" variant="primary">New account</Button>
          </>
        }
      />
      <div className="page-section">
        <div className="card">
          <TableToolbar search={search} onSearch={setSearch}>
            {["ALL", "ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE"].map(c => (
              <ChipFilter key={c} active={classFilter === c} onClick={() => setClassFilter(c)}>{c}</ChipFilter>
            ))}
            <div style={{ flex: 1 }}/>
            <Segmented value="tree" onChange={() => {}} options={[{ value: "tree", label: "Tree" }, { value: "list", label: "List" }]}/>
          </TableToolbar>
          <div style={{ overflow: "auto" }}>
            <table className="tbl">
              <thead>
                <tr>
                  <th style={{ minWidth: 360 }}>Account</th>
                  <th>Class</th>
                  <th>Normal</th>
                  <th>Currency</th>
                  <th>Type</th>
                  <th className="right-h">Balance (KES)</th>
                  <th className="right-h">Actions</th>
                </tr>
              </thead>
              <tbody>
                {visible.map(a => {
                  const depth = depths[a.code] || 0;
                  const hasChildren = COA.some(x => x.parent === a.code);
                  return (
                    <tr key={a.code} onClick={() => setDrawerAcct(a)} className="clickable">
                      <td>
                        <div className={`tree-row`} data-depth={depth}>
                          {hasChildren ? (
                            <span className="tree-toggle" onClick={(e) => { e.stopPropagation(); toggle(a.code); }}>
                              <Ico name={collapsed.has(a.code) ? "chev-right" : "chev-down"} size={10}/>
                            </span>
                          ) : <span style={{ width: 14 }}/>}
                          <span className="mono" style={{ color: "var(--muted)", fontSize: 11, fontWeight: 600 }}>{a.code}</span>
                          <span style={{ marginLeft: 4 }}>{a.name}</span>
                          {a.contra && <span className="badge outline" style={{ marginLeft: 6 }}>CONTRA</span>}
                          {a.sub && <span className="badge outline" style={{ marginLeft: 4 }}>SUB-LEDGER</span>}
                        </div>
                      </td>
                      <td><Badge status={a.class.toLowerCase()} dot={false}>{a.class}</Badge></td>
                      <td className="mono muted" style={{ fontSize: 11 }}>{a.normal}</td>
                      <td className="mono">{a.currency || "—"}</td>
                      <td className="muted" style={{ fontSize: 11 }}>{a.type}</td>
                      <td className="num" style={{ fontWeight: a.type === "HEADER" ? 700 : 500 }}>{fmt(a.balance, { decimals: 0, parens: true })}</td>
                      <td className="right-h"><span className="row-action"><IconBtn icon="external" title="Open ledger"/><IconBtn icon="dots" title="More"/></span></td>
                    </tr>
                  );
                })}
                <tr className="total-row">
                  <td colSpan="5" style={{ textAlign: "right" }}>Trial check</td>
                  <td className="num"><span className="pos">DR {fmt(drExp, { decimals: 0 })}</span> · <span className="pos">CR {fmt(crExp, { decimals: 0 })}</span></td>
                  <td></td>
                </tr>
              </tbody>
            </table>
          </div>
          <TableFooter total={COA.length} label="accounts" defaultSize={50}/>
        </div>
      </div>
      <Drawer open={!!drawerAcct} onClose={() => setDrawerAcct(null)}
        title={drawerAcct ? `${drawerAcct.code} · ${drawerAcct.name}` : ""}
        subtitle={drawerAcct ? `${drawerAcct.class} · ${drawerAcct.type} · ${drawerAcct.normal}-normal` : ""}
        footer={<><Button>View ledger</Button><Button variant="ghost">Edit</Button><div style={{flex:1}}/><Button variant="ghost" icon="lock">Deactivate</Button></>}>
        {drawerAcct && (
          <div className="stack">
            <div className="kpi-grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
              <Kpi label="Current balance" value={fmt(drawerAcct.balance, { decimals: 2 })} unit={drawerAcct.currency || "KES"}/>
              <Kpi label="Postable" icon="check" value={drawerAcct.type === "POST" ? "Yes" : "No"}/>
            </div>
            <div className="card">
              <div className="card-head">Recent activity</div>
              <table className="tbl">
                <thead><tr><th>Date</th><th>Reference</th><th>Memo</th><th className="right-h">Debit</th><th className="right-h">Credit</th></tr></thead>
                <tbody>
                  <tr><td className="mono">2026-02-28</td><td className="code-cell">JE-2026-0043</td><td>Batch depreciation</td><td className="num">{fmt(118134.92)}</td><td className="num">—</td></tr>
                  <tr><td className="mono">2026-02-25</td><td className="code-cell">JE-2026-0042</td><td>Salary payment</td><td className="num">—</td><td className="num">{fmt(553000)}</td></tr>
                  <tr><td className="mono">2026-02-15</td><td className="code-cell">JE-2026-0041</td><td>Reversal of dup posting</td><td className="num">{fmt(4500)}</td><td className="num">—</td></tr>
                </tbody>
              </table>
            </div>
          </div>
        )}
      </Drawer>
    </div>
  );
};

/* ===================== PERIODS ===================== */
const Periods = ({ onNav }) => {
  const { PERIODS } = window.QSDATA;
  return (
    <div className="page">
      <PageHeader
        title="Accounting Periods"
        meta="Fiscal Year 2026 · Jan–Dec · 12 periods"
        actions={
          <>
            <Button size="sm" icon="branch" variant="ghost">Validate cycle</Button>
            <Button size="sm" icon="play" variant="ghost">Run 9-step cycle</Button>
            <Button size="sm" icon="plus" variant="primary">Generate next FY</Button>
          </>
        }
      />
      <div className="page-section stack">
        <Banner kind="info">
          Period <strong>2026-02</strong> is in <Badge status="adjusting">ADJUSTING</Badge> phase. Posting reversals & adjusting entries is permitted; new transactional postings are gated.
        </Banner>
        <div className="card">
          <div className="card-body no-pad">
            <table className="tbl">
              <thead>
                <tr>
                  <th>Period</th>
                  <th>Range</th>
                  <th>Status</th>
                  <th>JEs</th>
                  <th>Closed by</th>
                  <th>Closed at</th>
                  <th className="right-h">Actions</th>
                </tr>
              </thead>
              <tbody>
                {PERIODS.map(p => (
                  <tr key={p.id}>
                    <td className="code-cell" style={{ fontWeight: 600 }}>{p.code}</td>
                    <td className="muted">{fmtDate(p.start)} – {fmtDate(p.end)}</td>
                    <td><Badge status={p.status.toLowerCase()}>{p.status}</Badge></td>
                    <td className="num">{Math.floor(Math.random() * 80) + 20}</td>
                    <td className="muted">{p.closedBy || "—"}</td>
                    <td className="muted mono" style={{ fontSize: 11 }}>{p.closedAt || "—"}</td>
                    <td className="right-h">
                      {p.status === "OPEN" && <Button size="sm" variant="ghost">Transition →</Button>}
                      {p.status === "ADJUSTING" && <><Button size="sm" variant="ghost">→ Closing</Button><Button size="sm" variant="primary" onClick={() => onNav("/close")}>Close period</Button></>}
                      {p.status === "CLOSED" && <Button size="sm" variant="ghost" icon="rotate">Reopen</Button>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <TableFooter total={PERIODS.length} label="periods" defaultSize={25}/>
        </div>
      </div>
    </div>
  );
};

/* ===================== CUSTOMERS ===================== */
const Customers = ({ onNav }) => {
  const { CUSTOMERS } = window.QSDATA;
  const [search, setSearch] = useState("");
  const [showInactive, setShowInactive] = useState(false);
  const [drawer, setDrawer] = useState(null);
  const filtered = CUSTOMERS.filter(c => (showInactive || c.active) && (!search || c.name.toLowerCase().includes(search.toLowerCase()) || c.code.toLowerCase().includes(search.toLowerCase())));
  const totalOutstanding = filtered.reduce((s, c) => s + c.balance, 0);

  return (
    <div className="page">
      <PageHeader
        title="Customers"
        meta={`${CUSTOMERS.filter(c => c.active).length} active · ${fmt(totalOutstanding, { currency: "KES", decimals: 0 })} outstanding`}
        actions={<><Button size="sm" icon="upload" variant="ghost">Import</Button><Button size="sm" icon="plus" variant="primary">New customer</Button></>}
      />
      <div className="page-section">
        <div className="kpi-grid" style={{ marginBottom: 12 }}>
          <Kpi label="Active customers" icon="users" value={CUSTOMERS.filter(c => c.active).length}/>
          <Kpi label="Total receivable" icon="coin" value={fmt(2_953_960, { decimals: 0, compact: true })} unit="KES" delta={+12.4}/>
          <Kpi label="Credit utilization" icon="card" value="38" unit="%" delta={-3.1}/>
          <Kpi label="Avg DSO" icon="clock" value="41" unit="days" delta={+2.4}/>
        </div>
        <div className="card">
          <TableToolbar search={search} onSearch={setSearch}>
            <ChipFilter active={showInactive} onClick={() => setShowInactive(v => !v)}>Show inactive</ChipFilter>
            <ChipFilter>Currency: any</ChipFilter>
            <ChipFilter>Terms: any</ChipFilter>
            <div style={{ flex: 1 }}/>
            <span className="muted" style={{ fontSize: 11 }}>{filtered.length} of {CUSTOMERS.length}</span>
          </TableToolbar>
          <div style={{ overflow: "auto" }}>
            <table className="tbl">
              <thead>
                <tr>
                  <th>Code</th>
                  <th>Customer</th>
                  <th>Contact</th>
                  <th>Currency</th>
                  <th>Terms</th>
                  <th className="right-h">Credit limit</th>
                  <th className="right-h">Balance</th>
                  <th>Utilization</th>
                  <th>Last invoice</th>
                  <th>Status</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {filtered.map(c => {
                  const util = (c.balance / c.creditLimit) * 100;
                  return (
                    <tr key={c.id} className="clickable" onClick={() => setDrawer(c)}>
                      <td className="code-cell">{c.code}</td>
                      <td><strong>{c.name}</strong></td>
                      <td className="muted">{c.contact}<div className="mono" style={{ fontSize: 10.5 }}>{c.email}</div></td>
                      <td className="mono">{c.currency}</td>
                      <td className="mono muted">{c.terms}</td>
                      <td className="num">{fmt(c.creditLimit, { decimals: 0 })}</td>
                      <td className="num">{fmt(c.balance, { decimals: 0 })}</td>
                      <td style={{ width: 110 }}>
                        <div className="bar" style={{ width: 80 }}><span style={{ width: `${Math.min(100, util)}%`, background: util > 80 ? "var(--neg)" : util > 50 ? "var(--warn)" : "var(--accent)" }}/></div>
                      </td>
                      <td className="mono muted" style={{ fontSize: 11 }}>{fmtDate(c.lastInvoice)}</td>
                      <td>{c.active ? <Badge status="approved" dot>Active</Badge> : <Badge status="archived">Inactive</Badge>}</td>
                      <td><span className="row-action"><IconBtn icon="dots"/></span></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <TableFooter total={filtered.length} label="customers"/>
        </div>
      </div>
      <Drawer open={!!drawer} onClose={() => setDrawer(null)} title={drawer?.name} subtitle={drawer ? `${drawer.code} · ${drawer.currency} · ${drawer.terms}` : ""}
        footer={<><Button variant="primary" icon="plus">New invoice</Button><Button icon="card">Record payment</Button><div style={{flex:1}}/><Button variant="ghost" icon="lock">Deactivate</Button></>}>
        {drawer && (
          <div className="stack">
            <div className="kpi-grid" style={{ gridTemplateColumns: "1fr 1fr 1fr" }}>
              <Kpi label="Outstanding" value={fmt(drawer.balance, { decimals: 0 })} unit={drawer.currency}/>
              <Kpi label="Credit limit" value={fmt(drawer.creditLimit, { decimals: 0 })} unit={drawer.currency}/>
              <Kpi label="Utilization" value={Math.round((drawer.balance / drawer.creditLimit) * 100)} unit="%"/>
            </div>
            <div className="form-grid cols-2">
              <div className="field"><label>Contact name</label><div>{drawer.contact}</div></div>
              <div className="field"><label>Email</label><div className="mono">{drawer.email}</div></div>
              <div className="field"><label>Phone</label><div className="mono">{drawer.phone}</div></div>
              <div className="field"><label>Payment terms</label><div className="mono">{drawer.terms}</div></div>
            </div>
            <div className="divider"/>
            <div style={{ fontWeight: 600, marginBottom: 8 }}>Recent invoices</div>
            <table className="tbl">
              <thead><tr><th>Invoice</th><th>Date</th><th>Due</th><th>Status</th><th className="right-h">Total</th><th className="right-h">Balance</th></tr></thead>
              <tbody>
                <tr><td className="code-cell">INV-2026-0017</td><td className="mono">12 Feb 2026</td><td className="mono">14 Mar 2026</td><td><Badge status="posted">POSTED</Badge></td><td className="num">290,000</td><td className="num">290,000</td></tr>
                <tr><td className="code-cell">INV-2026-0023</td><td className="mono">21 Feb 2026</td><td className="mono">23 Mar 2026</td><td><Badge status="draft">DRAFT</Badge></td><td className="num">192,000</td><td className="num">192,000</td></tr>
              </tbody>
            </table>
          </div>
        )}
      </Drawer>
    </div>
  );
};

/* ===================== SUPPLIERS ===================== */
const Suppliers = ({ onNav }) => {
  const { SUPPLIERS } = window.QSDATA;
  const [search, setSearch] = useState("");
  return (
    <div className="page">
      <PageHeader
        title="Suppliers"
        meta={`${SUPPLIERS.filter(s => s.active).length} active · ${fmt(SUPPLIERS.reduce((s, x) => s + x.balance, 0), { currency: "KES", decimals: 0 })} payable`}
        actions={<><Button size="sm" icon="upload" variant="ghost">Import</Button><Button size="sm" icon="plus" variant="primary">New supplier</Button></>}
      />
      <div className="page-section">
        <div className="card">
          <TableToolbar search={search} onSearch={setSearch}>
            <ChipFilter>Active only</ChipFilter>
            <ChipFilter>Currency: any</ChipFilter>
          </TableToolbar>
          <table className="tbl">
            <thead>
              <tr><th>Code</th><th>Supplier</th><th>Contact</th><th>Currency</th><th>Terms</th><th className="right-h">Balance owed</th><th>Last bill</th><th>Status</th><th></th></tr>
            </thead>
            <tbody>
              {SUPPLIERS.filter(s => !search || s.name.toLowerCase().includes(search.toLowerCase())).map(s => (
                <tr key={s.id}>
                  <td className="code-cell">{s.code}</td>
                  <td><strong>{s.name}</strong></td>
                  <td className="muted">{s.contact}<div className="mono" style={{ fontSize: 10.5 }}>{s.email}</div></td>
                  <td className="mono">{s.currency}</td>
                  <td className="mono muted">{s.terms}</td>
                  <td className="num">{fmt(s.balance, { decimals: 0 })}</td>
                  <td className="mono muted" style={{ fontSize: 11 }}>{fmtDate(s.lastBill)}</td>
                  <td>{s.active ? <Badge status="approved">Active</Badge> : <Badge status="archived">Inactive</Badge>}</td>
                  <td><span className="row-action"><IconBtn icon="dots"/></span></td>
                </tr>
              ))}
            </tbody>
          </table>
          <TableFooter total={SUPPLIERS.filter(s => !search || s.name.toLowerCase().includes(search.toLowerCase())).length} label="suppliers"/>
        </div>
      </div>
    </div>
  );
};

/* ===================== FIXED ASSETS ===================== */
const Assets = ({ onNav }) => {
  const { ASSETS } = window.QSDATA;
  const [search, setSearch] = useState("");
  const [drawer, setDrawer] = useState(null);
  const totalCost = ASSETS.reduce((s, a) => s + a.cost, 0);
  const totalNet = ASSETS.reduce((s, a) => s + a.netBook, 0);

  return (
    <div className="page">
      <PageHeader
        title="Fixed Assets Register"
        meta={`${ASSETS.length} assets · ${fmt(totalCost, { currency: "KES", decimals: 0, compact: true })} acquisition · ${fmt(totalNet, { currency: "KES", decimals: 0, compact: true })} net book value`}
        actions={
          <>
            <Button size="sm" icon="download" variant="ghost">Schedule</Button>
            <Button size="sm" icon="play" variant="ghost" onClick={() => onNav("/depreciation")}>Run depreciation</Button>
            <Button size="sm" icon="plus" variant="primary">New asset</Button>
          </>
        }
      />
      <div className="page-section">
        <div className="kpi-grid" style={{ marginBottom: 12 }}>
          <Kpi label="Acquisition cost" icon="package" value={fmt(totalCost / 1_000_000, { decimals: 2 })} unit="M KES"/>
          <Kpi label="Accumulated depr." icon="trend-down" value={fmt(ASSETS.reduce((s, a) => s + a.accum, 0) / 1_000_000, { decimals: 2 })} unit="M KES"/>
          <Kpi label="Net book value" icon="scale" value={fmt(totalNet / 1_000_000, { decimals: 2 })} unit="M KES"/>
          <Kpi label="Monthly depr." icon="clock" value={fmt(ASSETS.reduce((s, a) => s + a.monthlyDep, 0) / 1000, { decimals: 1 })} unit="K KES/mo"/>
        </div>
        <div className="card">
          <TableToolbar search={search} onSearch={setSearch}>
            <ChipFilter>Category: any</ChipFilter>
            <ChipFilter>Status: IN_USE</ChipFilter>
            <ChipFilter>Method: any</ChipFilter>
          </TableToolbar>
          <table className="tbl">
            <thead>
              <tr>
                <th>Tag</th><th>Asset</th><th>Category</th><th>Acquired</th>
                <th className="right-h">Cost</th><th className="right-h">Accum.</th><th className="right-h">Net Book</th>
                <th className="right-h">Mo. Dep.</th><th>Method</th><th>Status</th><th>Assignee</th><th></th>
              </tr>
            </thead>
            <tbody>
              {ASSETS.filter(a => !search || a.name.toLowerCase().includes(search.toLowerCase()) || a.tag.toLowerCase().includes(search.toLowerCase())).map(a => (
                <tr key={a.id} className="clickable" onClick={() => setDrawer(a)}>
                  <td className="code-cell">{a.tag}</td>
                  <td><strong>{a.name}</strong></td>
                  <td className="muted">{a.category}</td>
                  <td className="mono">{fmtDate(a.acquired)}</td>
                  <td className="num">{fmt(a.cost, { decimals: 0 })}</td>
                  <td className="num muted">{fmt(a.accum, { decimals: 0 })}</td>
                  <td className="num" style={{ fontWeight: 600 }}>{fmt(a.netBook, { decimals: 0 })}</td>
                  <td className="num">{fmt(a.monthlyDep, { decimals: 2 })}</td>
                  <td className="muted" style={{ fontSize: 11 }}>{a.method.replace("_", " ")}</td>
                  <td><Badge status={a.status === "IN_USE" ? "approved" : "archived"} dot={false}>{a.status}</Badge></td>
                  <td className="muted" style={{ fontSize: 11 }}>{a.assignedTo}</td>
                  <td><span className="row-action"><IconBtn icon="dots"/></span></td>
                </tr>
              ))}
              <tr className="total-row">
                <td colSpan="4">Totals</td>
                <td className="num">{fmt(totalCost, { decimals: 0 })}</td>
                <td className="num muted">{fmt(ASSETS.reduce((s, a) => s + a.accum, 0), { decimals: 0 })}</td>
                <td className="num">{fmt(totalNet, { decimals: 0 })}</td>
                <td className="num">{fmt(ASSETS.reduce((s, a) => s + a.monthlyDep, 0), { decimals: 2 })}</td>
                <td colSpan="4"></td>
              </tr>
            </tbody>
          </table>
          <TableFooter total={ASSETS.length} label="assets"/>
        </div>
      </div>
      <Drawer open={!!drawer} onClose={() => setDrawer(null)} title={drawer?.name} subtitle={drawer ? `${drawer.tag} · ${drawer.category} · ${drawer.method.replace('_',' ')}` : ""}
        footer={<><Button variant="primary" icon="play">Post depreciation</Button><Button icon="branch">Dispose</Button><div style={{flex:1}}/><Button variant="ghost">Edit</Button></>}>
        {drawer && <AssetDetails a={drawer}/>}
      </Drawer>
    </div>
  );
};

const AssetDetails = ({ a }) => {
  const months = Array.from({ length: 12 }, (_, i) => ({
    period: `2026-${String(i + 1).padStart(2, "0")}`,
    open: a.cost - a.monthlyDep * i,
    dep: a.monthlyDep,
    accum: a.monthlyDep * (i + 1),
    close: a.cost - a.monthlyDep * (i + 1),
  }));
  return (
    <div className="stack">
      <div className="kpi-grid" style={{ gridTemplateColumns: "1fr 1fr 1fr 1fr" }}>
        <Kpi label="Cost" value={fmt(a.cost, { decimals: 0 })} unit="KES"/>
        <Kpi label="Salvage" value={fmt(a.salvage, { decimals: 0 })} unit="KES"/>
        <Kpi label="Useful life" value={a.life} unit="months"/>
        <Kpi label="Net book" value={fmt(a.netBook, { decimals: 0 })} unit="KES"/>
      </div>
      <div className="card">
        <div className="card-head"><Ico name="calendar" size={13}/> Depreciation schedule (FY 2026)</div>
        <table className="tbl">
          <thead><tr><th>Period</th><th className="right-h">Opening NBV</th><th className="right-h">Depreciation</th><th className="right-h">Accumulated</th><th className="right-h">Closing NBV</th><th>Status</th></tr></thead>
          <tbody>
            {months.slice(0, 6).map((m, i) => (
              <tr key={i}>
                <td className="code-cell">{m.period}</td>
                <td className="num muted">{fmt(m.open, { decimals: 2 })}</td>
                <td className="num">{fmt(m.dep, { decimals: 2 })}</td>
                <td className="num muted">{fmt(m.accum, { decimals: 2 })}</td>
                <td className="num">{fmt(m.close, { decimals: 2 })}</td>
                <td><Badge status={i < 2 ? "posted" : "pending"}>{i < 2 ? "POSTED" : "PENDING"}</Badge></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
};

/* ===================== DEPRECIATION RUN ===================== */
const DepreciationRun = ({ onNav }) => {
  const { ASSETS } = window.QSDATA;
  const eligible = ASSETS.filter(a => a.status === "IN_USE");
  const total = eligible.reduce((s, a) => s + a.monthlyDep, 0);
  return (
    <div className="page">
      <PageHeader title="Batch Depreciation Run" meta="Period 2026-02 · Straight-line"/>
      <div className="page-section stack">
        <Banner kind="info">This will post a single journal entry: <strong>DR Depreciation Expense / CR Accumulated Depreciation</strong> per asset, dated <strong>2026-02-28</strong>.</Banner>
        <div className="card">
          <div className="card-head"><Ico name="play" size={13}/> Eligible assets · {eligible.length} · {fmt(total, { currency: "KES", decimals: 2 })} total</div>
          <table className="tbl">
            <thead><tr><th></th><th>Tag</th><th>Asset</th><th>Method</th><th className="right-h">Monthly Dep.</th><th className="right-h">Accum (after)</th></tr></thead>
            <tbody>
              {eligible.map((a, i) => (
                <tr key={a.id}>
                  <td className="checkbox-cell"><input type="checkbox" defaultChecked/></td>
                  <td className="code-cell">{a.tag}</td>
                  <td><strong>{a.name}</strong></td>
                  <td className="muted" style={{ fontSize: 11 }}>{a.method.replace("_", " ")}</td>
                  <td className="num">{fmt(a.monthlyDep, { decimals: 2 })}</td>
                  <td className="num muted">{fmt(a.accum + a.monthlyDep, { decimals: 2 })}</td>
                </tr>
              ))}
              <tr className="total-row">
                <td colSpan="4">Batch total — DR / CR</td>
                <td className="num">{fmt(total, { decimals: 2 })}</td>
                <td className="num">{fmt(total, { decimals: 2 })}</td>
              </tr>
            </tbody>
          </table>
          <div className="drawer-foot">
            <Button variant="primary" icon="play">Run batch depreciation</Button>
            <Button variant="ghost" icon="eye">Preview journal entry</Button>
            <div style={{flex:1}}/>
            <span className="muted" style={{ fontSize: 11 }}>Will create <strong>JE-2026-0047</strong></span>
          </div>
        </div>
      </div>
    </div>
  );
};

/* ===================== ORGANIZATION ===================== */
const Organization = () => {
  const { ORG, CURRENCIES, FX_RATES } = window.QSDATA;
  return (
    <div className="page">
      <PageHeader title="Organization" meta={`${ORG.id} · ${ORG.functionalCurrency} functional`}/>
      <div className="page-section stack">
        <div className="card">
          <div className="card-head"><Ico name="building" size={13}/> Entity details</div>
          <div className="card-body">
            <div className="form-grid cols-2">
              <div className="field"><label>Trading name</label><input className="input" defaultValue={ORG.name}/></div>
              <div className="field"><label>Legal name</label><input className="input" defaultValue={ORG.legalName}/></div>
              <div className="field"><label>Registration No.</label><input className="input mono" defaultValue={ORG.registrationNumber}/></div>
              <div className="field"><label>Tax ID (KRA PIN)</label><input className="input mono" defaultValue={ORG.taxIdentificationNumber}/></div>
              <div className="field"><label>Functional currency</label><input className="input mono" defaultValue={ORG.functionalCurrency} disabled/><div className="field-help">Cannot be changed without admin migration.</div></div>
              <div className="field"><label>Reporting currency</label><input className="input mono" defaultValue={ORG.reportingCurrency}/></div>
              <div className="field"><label>Country</label><input className="input" defaultValue="Kenya (KE)"/></div>
              <div className="field"><label>Timezone</label><input className="input" defaultValue={ORG.timezone}/></div>
              <div className="field"><label>Fiscal year start</label><input className="input" defaultValue="January"/></div>
              <div className="field"><label>Address</label><input className="input" defaultValue={`${ORG.addressLine1}, ${ORG.city} ${ORG.postalCode}`}/></div>
              <div className="field"><label>Email</label><input className="input" defaultValue={ORG.email}/></div>
              <div className="field"><label>Phone</label><input className="input mono" defaultValue={ORG.phone}/></div>
            </div>
          </div>
          <div className="drawer-foot"><Button variant="primary">Save changes</Button><Button variant="ghost">Discard</Button></div>
        </div>
      </div>
    </div>
  );
};

/* ===================== TAX & CURRENCY ===================== */
const TaxCurrency = () => {
  const { TAX_CODES, CURRENCIES, FX_RATES } = window.QSDATA;
  const [tab, setTab] = useState("tax");
  return (
    <div className="page">
      <PageHeader title="Tax & Currency"
        tabs={[
          { id: "tax", label: "Tax codes", count: TAX_CODES.length },
          { id: "ccy", label: "Currencies", count: CURRENCIES.length },
          { id: "fx", label: "FX rates", count: FX_RATES.length },
        ]} activeTab={tab} onTab={setTab}
        actions={<Button size="sm" icon="plus" variant="primary">New {tab === "tax" ? "tax code" : tab === "ccy" ? "currency" : "rate"}</Button>}
      />
      <div className="page-section">
        {tab === "tax" && (
          <div className="card">
            <table className="tbl">
              <thead><tr><th>Code</th><th>Name</th><th>Type</th><th className="right-h">Rate</th><th>Linked account</th><th>Status</th></tr></thead>
              <tbody>
                {TAX_CODES.map(t => (
                  <tr key={t.code}>
                    <td className="code-cell">{t.code}</td>
                    <td><strong>{t.name}</strong></td>
                    <td><Badge status="info" dot={false}>{t.type}</Badge></td>
                    <td className="num">{(t.rate * 100).toFixed(2)}%</td>
                    <td className="code-cell">{t.account || "—"}</td>
                    <td>{t.active ? <Badge status="approved">Active</Badge> : <Badge status="archived">Inactive</Badge>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <TableFooter total={TAX_CODES.length} label="tax codes"/>
          </div>
        )}
        {tab === "ccy" && (
          <div className="card">
            <table className="tbl">
              <thead><tr><th>Code</th><th>Name</th><th>Symbol</th><th>Decimals</th><th>Role</th></tr></thead>
              <tbody>
                {CURRENCIES.map(c => (
                  <tr key={c.code}>
                    <td className="code-cell">{c.code}</td>
                    <td><strong>{c.name}</strong></td>
                    <td className="mono">{c.symbol}</td>
                    <td className="num">{c.decimals}</td>
                    <td>{c.functional ? <Badge status="approved">Functional</Badge> : <Badge status="archived">Foreign</Badge>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <TableFooter total={CURRENCIES.length} label="currencies"/>
          </div>
        )}
        {tab === "fx" && (
          <div className="card">
            <table className="tbl">
              <thead><tr><th>From</th><th>To</th><th className="right-h">Rate</th><th>As of</th><th>Source</th></tr></thead>
              <tbody>
                {FX_RATES.map(r => (
                  <tr key={r.id}>
                    <td className="mono">{r.from}</td><td className="mono">{r.to}</td>
                    <td className="num">{fmt(r.rate, { decimals: 4 })}</td>
                    <td className="mono muted">{fmtDate(r.asOf)}</td>
                    <td className="muted">{r.source}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <TableFooter total={FX_RATES.length} label="FX rates"/>
          </div>
        )}
      </div>
    </div>
  );
};

Object.assign(window, { Dashboard, Approvals, ChartOfAccounts, Periods, Customers, Suppliers, Assets, DepreciationRun, Organization, TaxCurrency });
