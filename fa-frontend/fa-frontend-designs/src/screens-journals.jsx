/* QeSuite — Journal Entries, Source Documents, Invoices, Credit Notes, Payments, Receipts, AR Ageing */

/* ===================== JOURNALS ===================== */
const Journals = ({ onNav }) => {
  const { JOURNALS } = window.QSDATA;
  const [tab, setTab] = useState("ALL");
  const [search, setSearch] = useState("");
  const [drawer, setDrawer] = useState(null);
  const [showNew, setShowNew] = useState(false);

  const filtered = JOURNALS.filter(j =>
    (tab === "ALL" || j.status === tab) &&
    (!search || j.ref.toLowerCase().includes(search.toLowerCase()) || j.description.toLowerCase().includes(search.toLowerCase()))
  );

  const counts = {
    ALL: JOURNALS.length,
    DRAFT: JOURNALS.filter(j => j.status === "DRAFT").length,
    PENDING_APPROVAL: JOURNALS.filter(j => j.status === "PENDING_APPROVAL").length,
    POSTED: JOURNALS.filter(j => j.status === "POSTED").length,
  };

  return (
    <div className="page">
      <PageHeader
        title="Journal Entries"
        meta={`${JOURNALS.length} entries in period 2026-02`}
        tabs={[
          { id: "ALL", label: "All", count: counts.ALL },
          { id: "DRAFT", label: "Drafts", count: counts.DRAFT },
          { id: "PENDING_APPROVAL", label: "Pending approval", count: counts.PENDING_APPROVAL },
          { id: "POSTED", label: "Posted", count: counts.POSTED },
        ]}
        activeTab={tab} onTab={setTab}
        actions={<><Button size="sm" icon="download" variant="ghost">Export</Button><Button size="sm" icon="plus" variant="primary" onClick={() => setShowNew(true)}>New journal</Button></>}
      />
      <div className="page-section">
        <div className="card">
          <TableToolbar search={search} onSearch={setSearch}>
            <ChipFilter>Period: 2026-02</ChipFilter>
            <ChipFilter>Source: any</ChipFilter>
            <ChipFilter>Account: any</ChipFilter>
            <div style={{ flex: 1 }}/>
            <span className="muted" style={{ fontSize: 11 }}>{filtered.length} entries</span>
          </TableToolbar>
          <table className="tbl">
            <thead>
              <tr>
                <th>Reference</th><th>Date</th><th>Description</th><th>Source</th>
                <th className="right-h">Debit</th><th className="right-h">Credit</th>
                <th>Status</th><th>Trail</th><th></th>
              </tr>
            </thead>
            <tbody>
              {filtered.map(j => {
                const totalDr = j.lines.reduce((s, l) => s + l.debit, 0);
                return (
                  <tr key={j.id} className="clickable" onClick={() => setDrawer(j)}>
                    <td className="code-cell" style={{ fontWeight: 600 }}>{j.ref}</td>
                    <td className="mono muted">{fmtDate(j.date)}</td>
                    <td className="ellipsis">{j.description}<div className="muted" style={{ fontSize: 10.5 }}>{j.lines.length} lines · {j.period}</div></td>
                    <td><Badge status={j.source === "MANUAL" ? "draft" : "info"} dot={false}>{j.source}</Badge></td>
                    <td className="num">{fmt(totalDr, { decimals: 2 })}</td>
                    <td className="num">{fmt(totalDr, { decimals: 2 })}</td>
                    <td><Badge status={j.status.toLowerCase().replace("_","-")}>{j.status.replace("_"," ")}</Badge></td>
                    <td className="muted" style={{ fontSize: 11 }}>
                      {j.postedBy ? <>by <span className="mono">{j.postedBy}</span></> : j.submittedBy ? <>by <span className="mono">{j.submittedBy}</span></> : "—"}
                    </td>
                    <td><span className="row-action"><IconBtn icon="dots"/></span></td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <TableFooter total={filtered.length} label="journal entries"/>
        </div>
      </div>
      <JournalDrawer journal={drawer} onClose={() => setDrawer(null)}/>
      <Drawer open={showNew} onClose={() => setShowNew(false)} title="New Journal Entry" subtitle="DRAFT — will require approval to post" size="lg"
        footer={<><Button variant="ghost">Save as draft</Button><Button variant="primary">Submit for approval</Button><div style={{flex:1}}/><Button variant="ghost" onClick={() => setShowNew(false)}>Cancel</Button></>}>
        <NewJournalForm/>
      </Drawer>
    </div>
  );
};

const JournalDrawer = ({ journal, onClose }) => {
  if (!journal) return null;
  const totalDr = journal.lines.reduce((s, l) => s + l.debit, 0);
  const totalCr = journal.lines.reduce((s, l) => s + l.credit, 0);
  const isPosted = journal.status === "POSTED";
  return (
    <Drawer open={!!journal} onClose={onClose}
      title={journal.ref}
      subtitle={`${journal.description} · ${journal.period}`}
      size="lg"
      footer={
        isPosted ? <><Button icon="rotate">Reverse</Button><Button icon="download">Download PDF</Button><div style={{flex:1}}/></> :
        journal.status === "PENDING_APPROVAL" ? <><Button variant="primary" icon="approve">Approve & post</Button><Button icon="reject">Reject</Button><div style={{flex:1}}/></> :
        <><Button variant="primary" icon="upload">Submit for approval</Button><Button icon="reject">Discard</Button><div style={{flex:1}}/></>
      }>
      <div className="stack">
        <div className="kpi-grid" style={{ gridTemplateColumns: "1fr 1fr 1fr 1fr" }}>
          <Kpi label="Date" value={fmtDate(journal.date)}/>
          <Kpi label="Source" value={journal.source.replace("_"," ")}/>
          <Kpi label="Status" value={<Badge status={journal.status.toLowerCase()}>{journal.status.replace("_"," ")}</Badge>}/>
          <Kpi label="Lines" value={journal.lines.length}/>
        </div>
        <div className="je-editor">
          <table className="tbl">
            <thead>
              <tr><th style={{ width: 110 }}>Account</th><th>Memo</th><th className="right-h">Debit</th><th className="right-h">Credit</th></tr>
            </thead>
            <tbody>
              {journal.lines.map((l, i) => (
                <tr key={i}>
                  <td className="code-cell"><span className="mono" style={{ fontWeight: 600 }}>{l.account}</span><div className="muted" style={{ fontSize: 10.5 }}>{l.name}</div></td>
                  <td>{l.memo}</td>
                  <td className="num">{l.debit > 0 ? fmt(l.debit, { decimals: 2 }) : "—"}</td>
                  <td className="num">{l.credit > 0 ? fmt(l.credit, { decimals: 2 }) : "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="je-totals">
            <div className="je-balanced"><Ico name="check" size={11}/> Balanced — IFRS-compliant double entry</div>
            <div style={{ textAlign: "right" }}>{fmt(totalDr, { decimals: 2 })}</div>
            <div style={{ textAlign: "right" }}>{fmt(totalCr, { decimals: 2 })}</div>
          </div>
        </div>
        <div className="card">
          <div className="card-head"><Ico name="shield" size={13}/> Audit trail</div>
          <div className="card-body">
            <div className="timeline">
              <TimelineRow time="2026-02-25 14:30" body={<><strong>CREATED</strong> as draft</>} actor="w.njeri"/>
              <TimelineRow time="2026-02-25 14:45" body={<><strong>UPDATED</strong> — line 3 memo</>} actor="w.njeri"/>
              <TimelineRow time="2026-02-25 15:20" body={<><strong>SUBMITTED</strong> for approval</>} actor="w.njeri"/>
              {isPosted && <TimelineRow time={journal.postedAt} body={<><strong>APPROVED & POSTED</strong></>} actor={journal.postedBy}/>}
            </div>
          </div>
        </div>
      </div>
    </Drawer>
  );
};

const NewJournalForm = () => {
  const [lines, setLines] = useState([
    { account: "", name: "", debit: 0, credit: 0, memo: "" },
    { account: "", name: "", debit: 0, credit: 0, memo: "" },
  ]);
  const totalDr = lines.reduce((s, l) => s + (+l.debit || 0), 0);
  const totalCr = lines.reduce((s, l) => s + (+l.credit || 0), 0);
  const balanced = totalDr === totalCr && totalDr > 0;
  const setLine = (i, key, val) => setLines(ls => ls.map((l, idx) => idx === i ? { ...l, [key]: val } : l));
  return (
    <div className="stack">
      <div className="form-grid cols-3">
        <div className="field"><label>Reference <span className="req">*</span></label><input className="input mono" defaultValue="JE-2026-0047"/></div>
        <div className="field"><label>Date <span className="req">*</span></label><input className="input mono" type="date" defaultValue="2026-02-28"/></div>
        <div className="field"><label>Period</label><input className="input mono" defaultValue="2026-02" disabled/></div>
        <div className="field" style={{ gridColumn: "1 / -1" }}><label>Description</label><input className="input" placeholder="e.g. Accrued utilities — February 2026"/></div>
      </div>
      <div className="je-editor">
        <table className="tbl">
          <thead>
            <tr><th style={{ width: 130 }}>Account</th><th>Memo</th><th className="right-h" style={{ width: 130 }}>Debit</th><th className="right-h" style={{ width: 130 }}>Credit</th><th style={{ width: 24 }}></th></tr>
          </thead>
          <tbody>
            {lines.map((l, i) => (
              <tr key={i}>
                <td><input className="je-input mono" placeholder="1-1100" value={l.account} onChange={(e) => setLine(i, "account", e.target.value)}/></td>
                <td><input className="je-input" placeholder="Memo" value={l.memo} onChange={(e) => setLine(i, "memo", e.target.value)}/></td>
                <td className="num"><input className="je-input mono" style={{ textAlign: "right" }} placeholder="0.00" value={l.debit || ""} onChange={(e) => setLine(i, "debit", e.target.value)}/></td>
                <td className="num"><input className="je-input mono" style={{ textAlign: "right" }} placeholder="0.00" value={l.credit || ""} onChange={(e) => setLine(i, "credit", e.target.value)}/></td>
                <td><IconBtn icon="x" title="Remove" onClick={() => setLines(ls => ls.filter((_, idx) => idx !== i))}/></td>
              </tr>
            ))}
            <tr><td colSpan="5"><Button size="sm" variant="ghost" icon="plus" onClick={() => setLines(ls => [...ls, { account: "", name: "", debit: 0, credit: 0, memo: "" }])}>Add line</Button></td></tr>
          </tbody>
        </table>
        <div className="je-totals">
          {balanced ? <div className="je-balanced"><Ico name="check" size={11}/> Balanced</div> : <div className="je-unbalanced"><Ico name="warn" size={11}/> Out of balance by {fmt(Math.abs(totalDr - totalCr), { decimals: 2 })}</div>}
          <div style={{ textAlign: "right" }}>{fmt(totalDr, { decimals: 2 })}</div>
          <div style={{ textAlign: "right" }}>{fmt(totalCr, { decimals: 2 })}</div>
        </div>
      </div>
    </div>
  );
};

/* ===================== SOURCE DOCUMENTS ===================== */
const SourceDocs = ({ onNav }) => {
  const { SOURCE_DOCS } = window.QSDATA;
  const [drawer, setDrawer] = useState(null);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const filtered = SOURCE_DOCS.filter(d => (statusFilter === "ALL" || d.status === statusFilter) && (!search || d.ref.toLowerCase().includes(search.toLowerCase()) || (d.supplier || "").toLowerCase().includes(search.toLowerCase())));

  return (
    <div className="page">
      <PageHeader
        title="Source Documents"
        meta={`${SOURCE_DOCS.length} documents · workflow: DRAFT → SUBMITTED → REVIEWED → APPROVED → ARCHIVED`}
        actions={<><Button size="sm" icon="upload" variant="ghost">Bulk upload</Button><Button size="sm" icon="plus" variant="primary">New document</Button></>}
      />
      <div className="page-section stack">
        <div className="card">
          <TableToolbar search={search} onSearch={setSearch}>
            {["ALL", "DRAFT", "SUBMITTED", "REVIEWED", "APPROVED", "ARCHIVED", "VOID"].map(s => (
              <ChipFilter key={s} active={statusFilter === s} onClick={() => setStatusFilter(s)}>{s}</ChipFilter>
            ))}
          </TableToolbar>
          <table className="tbl">
            <thead>
              <tr><th>Ref</th><th>Type</th><th>Counterparty</th><th>Date</th><th className="right-h">Amount</th><th>Attached</th><th>Classified as</th><th>Status</th><th></th></tr>
            </thead>
            <tbody>
              {filtered.map(d => (
                <tr key={d.id} className="clickable" onClick={() => setDrawer(d)}>
                  <td className="code-cell">{d.ref}</td>
                  <td>{d.type}</td>
                  <td>{d.supplier}</td>
                  <td className="mono muted">{fmtDate(d.date)}</td>
                  <td className="num">{d.amount != null ? `${d.currency} ${fmt(d.amount, { decimals: 0 })}` : "—"}</td>
                  <td><span className="badge outline"><Ico name="docs" size={9}/> {d.attachments}</span></td>
                  <td className="muted" style={{ fontSize: 11 }}>{d.classifiedAs || <em>Unclassified</em>}</td>
                  <td><Badge status={d.status.toLowerCase()}>{d.status}</Badge></td>
                  <td><span className="row-action"><IconBtn icon="dots"/></span></td>
                </tr>
              ))}
            </tbody>
          </table>
          <TableFooter total={filtered.length} label="documents"/>
        </div>
      </div>
      <Drawer open={!!drawer} onClose={() => setDrawer(null)} title={drawer?.ref} subtitle={drawer?.type}
        footer={drawer && (
          drawer.status === "DRAFT"     ? <><Button variant="primary">Submit →</Button><Button variant="ghost">Edit</Button><Button variant="danger">Void</Button></> :
          drawer.status === "SUBMITTED" ? <><Button variant="primary">Mark reviewed →</Button><Button variant="ghost">Send back</Button></> :
          drawer.status === "REVIEWED"  ? <><Button variant="primary">Approve →</Button><Button variant="ghost">Reject</Button></> :
          drawer.status === "APPROVED"  ? <><Button variant="primary">Classify →</Button><Button variant="ghost">Archive</Button></> :
          <><Button variant="ghost">Restore</Button></>
        )}>
        {drawer && <SourceDocDetails d={drawer}/>}
      </Drawer>
    </div>
  );
};

const SourceDocDetails = ({ d }) => (
  <div className="stack">
    <div className="card">
      <div className="card-head"><Ico name="branch" size={13}/> Workflow</div>
      <div className="card-body">
        <div className="stepper">
          {["DRAFT", "SUBMITTED", "REVIEWED", "APPROVED", "ARCHIVED"].map((s, i) => {
            const order = ["DRAFT", "SUBMITTED", "REVIEWED", "APPROVED", "ARCHIVED"];
            const curIdx = order.indexOf(d.status);
            return (
              <div key={s} className={`step ${i < curIdx ? "done" : i === curIdx ? "active" : ""}`}>
                <div className="step-num">{i < curIdx ? <Ico name="check" size={10}/> : i + 1}</div>
                <div>{s}</div>
                <div className="step-line"/>
              </div>
            );
          })}
        </div>
      </div>
    </div>
    <div className="form-grid cols-2">
      <div className="field"><label>Counterparty</label><div>{d.supplier}</div></div>
      <div className="field"><label>Date</label><div className="mono">{fmtDate(d.date)}</div></div>
      <div className="field"><label>Amount</label><div className="mono">{d.amount ? `${d.currency} ${fmt(d.amount, { decimals: 2 })}` : "—"}</div></div>
      <div className="field"><label>Attachments</label><div className="mono">{d.attachments} file{d.attachments !== 1 ? "s" : ""}</div></div>
      <div className="field" style={{ gridColumn: "1 / -1" }}><label>IFRS classification</label><div>{d.classifiedAs || <em className="muted">Not yet classified — run recognition test on approval</em>}</div></div>
    </div>
    <div className="card">
      <div className="card-head"><Ico name="check" size={13}/> IFRS recognition test</div>
      <div className="card-body" style={{ fontSize: 12 }}>
        <div className="h-row" style={{ justifyContent: "space-between", padding: "4px 0", borderBottom: "1px dashed var(--border)" }}><span>Probable economic benefit?</span><Badge status="approved" dot>YES</Badge></div>
        <div className="h-row" style={{ justifyContent: "space-between", padding: "4px 0", borderBottom: "1px dashed var(--border)" }}><span>Cost measurable reliably?</span><Badge status="approved" dot>YES</Badge></div>
        <div className="h-row" style={{ justifyContent: "space-between", padding: "4px 0", borderBottom: "1px dashed var(--border)" }}><span>Capitalize vs expense?</span><Badge status="info" dot>EXPENSE</Badge></div>
        <div className="h-row" style={{ justifyContent: "space-between", padding: "4px 0" }}><span>Recognition timing?</span><Badge status="info" dot>POINT_IN_TIME</Badge></div>
      </div>
    </div>
  </div>
);

/* ===================== INVOICES ===================== */
const Invoices = ({ onNav }) => {
  const { INVOICES } = window.QSDATA;
  const [tab, setTab] = useState("ALL");
  const [search, setSearch] = useState("");
  const [drawer, setDrawer] = useState(null);
  const [showNew, setShowNew] = useState(false);

  const filtered = INVOICES.filter(i => (tab === "ALL" || i.status === tab) && (!search || i.ref.toLowerCase().includes(search.toLowerCase()) || i.customerName.toLowerCase().includes(search.toLowerCase())));

  const counts = {
    ALL: INVOICES.length,
    DRAFT: INVOICES.filter(i => i.status === "DRAFT").length,
    POSTED: INVOICES.filter(i => i.status === "POSTED").length,
    PAID: INVOICES.filter(i => i.status === "PAID").length,
  };

  const totalUnpaid = INVOICES.filter(i => i.status === "POSTED").reduce((s, i) => s + i.balance, 0);

  return (
    <div className="page">
      <PageHeader
        title="Invoices"
        meta={`${INVOICES.length} invoices · ${fmt(totalUnpaid, { currency: "KES", decimals: 0, compact: true })} outstanding`}
        tabs={[
          { id: "ALL", label: "All", count: counts.ALL },
          { id: "DRAFT", label: "Drafts", count: counts.DRAFT },
          { id: "POSTED", label: "Posted / open", count: counts.POSTED },
          { id: "PAID", label: "Paid", count: counts.PAID },
        ]}
        activeTab={tab} onTab={setTab}
        actions={<><Button size="sm" icon="download" variant="ghost">Export</Button><Button size="sm" icon="plus" variant="primary" onClick={() => setShowNew(true)}>New invoice</Button></>}
      />
      <div className="page-section">
        <div className="card">
          <TableToolbar search={search} onSearch={setSearch}>
            <ChipFilter>Customer: any</ChipFilter>
            <ChipFilter>Currency: any</ChipFilter>
            <ChipFilter>Recognition: any</ChipFilter>
            <ChipFilter>Due: any</ChipFilter>
          </TableToolbar>
          <table className="tbl">
            <thead>
              <tr>
                <th>Invoice</th><th>Customer</th><th>Date</th><th>Due</th>
                <th>Recognition</th><th className="right-h">Subtotal</th><th className="right-h">Tax</th>
                <th className="right-h">Total</th><th className="right-h">Balance</th>
                <th>Status</th><th>Age</th><th></th>
              </tr>
            </thead>
            <tbody>
              {filtered.map(i => (
                <tr key={i.id} className="clickable" onClick={() => setDrawer(i)}>
                  <td className="code-cell" style={{ fontWeight: 600 }}>{i.ref}</td>
                  <td>{i.customerName}</td>
                  <td className="mono muted">{fmtDate(i.date)}</td>
                  <td className="mono muted">{fmtDate(i.due)}</td>
                  <td><Badge status={i.recognition === "OVER_TIME" ? "info" : "approved"} dot={false}>{i.recognition}</Badge>{i.discount && <span className="badge outline" style={{ marginLeft: 4 }}>−{i.discount}%</span>}</td>
                  <td className="num muted">{i.currency} {fmt(i.subtotal, { decimals: 0 })}</td>
                  <td className="num muted">{fmt(i.tax, { decimals: 0 })}</td>
                  <td className="num" style={{ fontWeight: 600 }}>{fmt(i.total, { decimals: 0 })}</td>
                  <td className="num">{fmt(i.balance, { decimals: 0 })}</td>
                  <td><Badge status={i.status === "PAID" ? "paid" : i.status === "POSTED" ? "posted" : "draft"}>{i.status}</Badge></td>
                  <td className="mono" style={{ fontSize: 11 }}>{i.aging != null ? <span style={{ color: i.aging > 0 ? "var(--warn)" : "var(--muted)" }}>{i.aging > 0 ? `+${i.aging}d` : `${i.aging}d`}</span> : "—"}</td>
                  <td><span className="row-action"><IconBtn icon="dots"/></span></td>
                </tr>
              ))}
            </tbody>
          </table>
          <TableFooter total={filtered.length} label="invoices"/>
        </div>
      </div>
      <InvoiceDrawer invoice={drawer} onClose={() => setDrawer(null)}/>
      <Drawer open={showNew} onClose={() => setShowNew(false)} title="New Invoice" subtitle="DRAFT — will not post until approved" size="lg"
        footer={<><Button variant="primary">Approve & post</Button><Button variant="ghost">Save as draft</Button><div style={{flex:1}}/><Button variant="ghost" onClick={() => setShowNew(false)}>Cancel</Button></>}>
        <NewInvoiceForm/>
      </Drawer>
    </div>
  );
};

const InvoiceDrawer = ({ invoice, onClose }) => {
  if (!invoice) return null;
  return (
    <Drawer open={!!invoice} onClose={onClose} title={invoice.ref} subtitle={`${invoice.customerName} · ${invoice.currency} ${fmt(invoice.total, { decimals: 2 })}`} size="lg"
      footer={
        invoice.status === "DRAFT" ? <><Button variant="primary" icon="check">Approve & post</Button><Button icon="reject">Void</Button><div style={{flex:1}}/></> :
        invoice.status === "POSTED" ? <><Button variant="primary" icon="card">Record payment</Button><Button icon="receipt">Issue credit note</Button><Button icon="download">PDF</Button><div style={{flex:1}}/></> :
        <><Button icon="download">PDF</Button><div style={{flex:1}}/></>
      }>
      <div className="stack">
        <div className="kpi-grid" style={{ gridTemplateColumns: "1fr 1fr 1fr 1fr" }}>
          <Kpi label="Total" value={fmt(invoice.total, { decimals: 2 })} unit={invoice.currency}/>
          <Kpi label="Paid" value={fmt(invoice.paid, { decimals: 2 })} unit={invoice.currency}/>
          <Kpi label="Balance" value={fmt(invoice.balance, { decimals: 2 })} unit={invoice.currency}/>
          <Kpi label="Status" value={<Badge status={invoice.status === "PAID" ? "paid" : invoice.status === "POSTED" ? "posted" : "draft"}>{invoice.status}</Badge>}/>
        </div>
        <div className="card">
          <div className="card-head"><Ico name="doc" size={13}/> Line items</div>
          <table className="tbl">
            <thead><tr><th>Description</th><th className="right-h">Qty</th><th className="right-h">Unit</th><th>Tax</th><th className="right-h">Amount</th></tr></thead>
            <tbody>
              {invoice.lines.map((l, i) => (
                <tr key={i}>
                  <td>{l.desc}</td>
                  <td className="num">{l.qty}</td>
                  <td className="num">{fmt(l.unit, { decimals: 2 })}</td>
                  <td className="mono muted">{l.tax}</td>
                  <td className="num">{fmt(l.qty * l.unit, { decimals: 2 })}</td>
                </tr>
              ))}
              <tr><td colSpan="4" style={{ textAlign: "right" }}>Subtotal</td><td className="num">{fmt(invoice.subtotal, { decimals: 2 })}</td></tr>
              <tr><td colSpan="4" style={{ textAlign: "right" }}>VAT (16%)</td><td className="num">{fmt(invoice.tax, { decimals: 2 })}</td></tr>
              <tr className="total-row"><td colSpan="4" style={{ textAlign: "right" }}>Total</td><td className="num">{invoice.currency} {fmt(invoice.total, { decimals: 2 })}</td></tr>
            </tbody>
          </table>
        </div>
        <div className="card">
          <div className="card-head"><Ico name="ledger" size={13}/> Posting preview</div>
          <table className="tbl">
            <thead><tr><th>Account</th><th>Memo</th><th className="right-h">DR</th><th className="right-h">CR</th></tr></thead>
            <tbody>
              <tr><td className="code-cell">1-1200 · Accounts Receivable</td><td>{invoice.customerName}</td><td className="num">{fmt(invoice.total, { decimals: 2 })}</td><td className="num">—</td></tr>
              <tr><td className="code-cell">{invoice.recognition === "OVER_TIME" ? "2-1200 · Deferred Revenue (IFRS 15)" : "4-1000 · Service Revenue"}</td><td>{invoice.recognition === "OVER_TIME" ? "Defer — recognize over time" : "Revenue recognized at point-in-time"}</td><td className="num">—</td><td className="num">{fmt(invoice.subtotal, { decimals: 2 })}</td></tr>
              <tr><td className="code-cell">2-2100 · VAT Payable</td><td>Output VAT 16%</td><td className="num">—</td><td className="num">{fmt(invoice.tax, { decimals: 2 })}</td></tr>
            </tbody>
          </table>
        </div>
      </div>
    </Drawer>
  );
};

const NewInvoiceForm = () => {
  const [recognition, setRecognition] = useState("POINT_IN_TIME");
  const [lines, setLines] = useState([{ desc: "Implementation consulting", qty: 1, unit: 250_000, tax: "VAT-16" }]);
  const subtotal = lines.reduce((s, l) => s + l.qty * l.unit, 0);
  const tax = Math.round(subtotal * 0.16 * 100) / 100;
  return (
    <div className="stack">
      <div className="form-grid cols-3">
        <div className="field"><label>Reference</label><input className="input mono" defaultValue="INV-2026-0024"/></div>
        <div className="field"><label>Customer <span className="req">*</span></label><select className="select"><option>Acme Corp</option><option>Karibu Hotels Group</option><option>Olympus Media</option></select></div>
        <div className="field"><label>Currency</label><select className="select"><option>KES</option><option>USD</option></select></div>
        <div className="field"><label>Invoice date</label><input type="date" className="input mono" defaultValue="2026-02-28"/></div>
        <div className="field"><label>Due date</label><input type="date" className="input mono" defaultValue="2026-03-30"/></div>
        <div className="field"><label>Recognition (IFRS 15)</label>
          <div className="segmented">
            <div className={`seg ${recognition === "POINT_IN_TIME" ? "active" : ""}`} onClick={() => setRecognition("POINT_IN_TIME")}>POINT_IN_TIME</div>
            <div className={`seg ${recognition === "OVER_TIME" ? "active" : ""}`} onClick={() => setRecognition("OVER_TIME")}>OVER_TIME</div>
          </div>
          <div className="field-help">{recognition === "OVER_TIME" ? "Credits Deferred Revenue (2-1200) — recognized via period-end batch" : "Credits Service Revenue immediately on approval"}</div>
        </div>
      </div>
      <div className="je-editor">
        <table className="tbl">
          <thead><tr><th>Description</th><th className="right-h" style={{ width: 70 }}>Qty</th><th className="right-h" style={{ width: 110 }}>Unit</th><th style={{ width: 90 }}>Tax</th><th className="right-h" style={{ width: 120 }}>Amount</th><th style={{ width: 24 }}></th></tr></thead>
          <tbody>
            {lines.map((l, i) => (
              <tr key={i}>
                <td><input className="je-input" value={l.desc} onChange={(e) => setLines(ls => ls.map((x, j) => j === i ? { ...x, desc: e.target.value } : x))}/></td>
                <td className="num"><input className="je-input mono" style={{ textAlign: "right" }} value={l.qty} onChange={(e) => setLines(ls => ls.map((x, j) => j === i ? { ...x, qty: +e.target.value } : x))}/></td>
                <td className="num"><input className="je-input mono" style={{ textAlign: "right" }} value={l.unit} onChange={(e) => setLines(ls => ls.map((x, j) => j === i ? { ...x, unit: +e.target.value } : x))}/></td>
                <td className="mono">{l.tax}</td>
                <td className="num">{fmt(l.qty * l.unit, { decimals: 2 })}</td>
                <td><IconBtn icon="x" onClick={() => setLines(ls => ls.filter((_, j) => j !== i))}/></td>
              </tr>
            ))}
            <tr><td colSpan="6"><Button size="sm" variant="ghost" icon="plus" onClick={() => setLines(ls => [...ls, { desc: "", qty: 1, unit: 0, tax: "VAT-16" }])}>Add line</Button></td></tr>
            <tr><td colSpan="4" style={{ textAlign: "right" }}>Subtotal</td><td className="num">{fmt(subtotal, { decimals: 2 })}</td><td></td></tr>
            <tr><td colSpan="4" style={{ textAlign: "right" }}>VAT 16%</td><td className="num">{fmt(tax, { decimals: 2 })}</td><td></td></tr>
            <tr className="total-row"><td colSpan="4" style={{ textAlign: "right" }}>Total</td><td className="num">KES {fmt(subtotal + tax, { decimals: 2 })}</td><td></td></tr>
          </tbody>
        </table>
      </div>
    </div>
  );
};

/* ===================== CREDIT NOTES ===================== */
const CreditNotes = () => {
  const { CREDIT_NOTES } = window.QSDATA;
  return (
    <div className="page">
      <PageHeader title="Credit Notes" meta={`${CREDIT_NOTES.length} notes · against posted invoices`}
        actions={<Button size="sm" icon="plus" variant="primary">New credit note</Button>}/>
      <div className="page-section">
        <div className="card">
          <table className="tbl">
            <thead><tr><th>Ref</th><th>Against</th><th>Customer</th><th>Date</th><th className="right-h">Amount</th><th>Reason</th><th>Status</th></tr></thead>
            <tbody>
              {CREDIT_NOTES.map(c => (
                <tr key={c.id}>
                  <td className="code-cell">{c.ref}</td>
                  <td className="code-cell">{c.invoice}</td>
                  <td>{c.customer}</td>
                  <td className="mono muted">{fmtDate(c.date)}</td>
                  <td className="num">{c.currency} {fmt(c.amount, { decimals: 2 })}</td>
                  <td className="muted">{c.reason}</td>
                  <td><Badge status={c.status.toLowerCase()}>{c.status}</Badge></td>
                </tr>
              ))}
            </tbody>
          </table>
          <TableFooter total={CREDIT_NOTES.length} label="credit notes"/>
        </div>
      </div>
    </div>
  );
};

/* ===================== PAYMENTS ===================== */
const Payments = ({ onNav }) => {
  const { PAYMENTS } = window.QSDATA;
  const [search, setSearch] = useState("");
  const [drawer, setDrawer] = useState(null);
  return (
    <div className="page">
      <PageHeader title="Payments"
        meta={`${PAYMENTS.length} payments · KES + USD + M-Pesa`}
        actions={<><Button size="sm" icon="globe" variant="ghost">M-Pesa callback log</Button><Button size="sm" icon="plus" variant="primary">Record payment</Button></>}/>
      <div className="page-section">
        <div className="card">
          <TableToolbar search={search} onSearch={setSearch}>
            <ChipFilter>Method: any</ChipFilter>
            <ChipFilter>Customer: any</ChipFilter>
            <ChipFilter>Status: any</ChipFilter>
          </TableToolbar>
          <table className="tbl">
            <thead>
              <tr><th>Ref</th><th>Customer</th><th>Date</th><th>Method</th>
              <th className="right-h">Amount</th><th className="right-h">Matched</th>
              <th>Against invoice</th><th>Status</th><th></th></tr>
            </thead>
            <tbody>
              {PAYMENTS.map(p => (
                <tr key={p.id} className="clickable" onClick={() => setDrawer(p)}>
                  <td className="code-cell">{p.ref}</td>
                  <td>{p.customer}</td>
                  <td className="mono muted">{fmtDate(p.date)}</td>
                  <td><Badge status={p.method === "M_PESA" ? "info" : "approved"} dot={false}>{p.method.replace("_"," ")}</Badge></td>
                  <td className="num">{p.currency} {fmt(p.amount, { decimals: 2 })}</td>
                  <td className="num">{fmt(p.matched, { decimals: 2 })}</td>
                  <td className="code-cell">{p.invoice || <em className="muted">unmatched</em>}</td>
                  <td><Badge status={p.status.toLowerCase().replace("_","-")}>{p.status.replace("_"," ")}</Badge></td>
                  <td><span className="row-action"><IconBtn icon="dots"/></span></td>
                </tr>
              ))}
            </tbody>
          </table>
          <TableFooter total={PAYMENTS.length} label="payments"/>
        </div>
      </div>
      <Drawer open={!!drawer} onClose={() => setDrawer(null)} title={drawer?.ref} subtitle={drawer ? `${drawer.customer} · ${drawer.currency} ${fmt(drawer.amount, { decimals: 2 })}` : ""}
        footer={drawer && (drawer.status === "PENDING_APPROVAL" ? <><Button variant="primary" icon="approve">Approve</Button><Button icon="link">Match to invoice</Button></> :
                            drawer.status === "APPROVED" ? <><Button variant="primary">Post payment</Button><Button icon="rotate">Reverse</Button></> :
                            drawer.status === "DRAFT" ? <><Button variant="primary">Submit for approval</Button><Button icon="link">Match invoice</Button></> :
                            <><Button icon="receipt" variant="primary">View receipt</Button><Button icon="rotate">Reverse</Button></>)}>
        {drawer && (
          <div className="stack">
            <div className="kpi-grid" style={{ gridTemplateColumns: "1fr 1fr 1fr 1fr" }}>
              <Kpi label="Amount" value={fmt(drawer.amount, { decimals: 2 })} unit={drawer.currency}/>
              <Kpi label="Matched" value={fmt(drawer.matched, { decimals: 2 })} unit={drawer.currency}/>
              <Kpi label="Method" value={drawer.method.replace("_"," ")}/>
              <Kpi label="Status" value={<Badge status={drawer.status.toLowerCase().replace("_","-")}>{drawer.status.replace("_"," ")}</Badge>}/>
            </div>
            <div className="card">
              <div className="card-head"><Ico name="link" size={13}/> Matched invoices</div>
              <table className="tbl">
                <thead><tr><th>Invoice</th><th>Customer</th><th className="right-h">Invoice total</th><th className="right-h">Applied</th><th className="right-h">Remaining</th></tr></thead>
                <tbody>
                  {drawer.invoice ? (
                    <tr><td className="code-cell">{drawer.invoice}</td><td>{drawer.customer}</td><td className="num">{fmt(drawer.amount, { decimals: 2 })}</td><td className="num">{fmt(drawer.matched, { decimals: 2 })}</td><td className="num">0.00</td></tr>
                  ) : (
                    <tr><td colSpan="5" className="empty"><div className="empty-title">Not yet matched</div>Click "Match to invoice" to apply this payment to one or more posted invoices.</td></tr>
                  )}
                </tbody>
              </table>
            </div>
            <div className="card">
              <div className="card-head"><Ico name="ledger" size={13}/> Posting on approval</div>
              <table className="tbl">
                <thead><tr><th>Account</th><th>Memo</th><th className="right-h">DR</th><th className="right-h">CR</th></tr></thead>
                <tbody>
                  <tr><td className="code-cell">{drawer.method === "M_PESA" ? "1-1100 · Cash (M-Pesa settlement)" : "1-1100 · Cash & Bank — KES"}</td><td>{drawer.customer}</td><td className="num">{fmt(drawer.matched || drawer.amount, { decimals: 2 })}</td><td className="num">—</td></tr>
                  <tr><td className="code-cell">1-1200 · Accounts Receivable</td><td>Clear AR</td><td className="num">—</td><td className="num">{fmt(drawer.matched || drawer.amount, { decimals: 2 })}</td></tr>
                </tbody>
              </table>
            </div>
          </div>
        )}
      </Drawer>
    </div>
  );
};

/* ===================== RECEIPTS ===================== */
const Receipts = () => {
  const { RECEIPTS } = window.QSDATA;
  return (
    <div className="page">
      <PageHeader title="Receipts" meta={`${RECEIPTS.length} receipts · auto-generated from posted payments`}/>
      <div className="page-section">
        <div className="card">
          <table className="tbl">
            <thead><tr><th>Ref</th><th>Linked payment</th><th>Customer</th><th>Date</th><th className="right-h">Amount</th><th>Issued?</th><th>Status</th><th></th></tr></thead>
            <tbody>
              {RECEIPTS.map(r => (
                <tr key={r.id}>
                  <td className="code-cell">{r.ref}</td>
                  <td className="code-cell">{r.payment}</td>
                  <td>{r.customer}</td>
                  <td className="mono muted">{fmtDate(r.date)}</td>
                  <td className="num">{r.currency} {fmt(r.amount, { decimals: 2 })}</td>
                  <td>{r.issued ? <Badge status="approved">Sent</Badge> : <Badge status="draft">Generated</Badge>}</td>
                  <td><Badge status={r.status.toLowerCase()}>{r.status}</Badge></td>
                  <td><Button size="sm" variant="ghost" icon="envelope">{r.issued ? "Resend" : "Issue"}</Button> <Button size="sm" variant="ghost" icon="download">PDF</Button></td>
                </tr>
              ))}
            </tbody>
          </table>
          <TableFooter total={RECEIPTS.length} label="receipts"/>
        </div>
      </div>
    </div>
  );
};

/* ===================== AR AGEING ===================== */
const ARAgeing = () => {
  const { AR_AGEING } = window.QSDATA;
  const totals = AR_AGEING.reduce((a, r) => ({
    current: a.current + r.current, b1_30: a.b1_30 + r.b1_30, b31_60: a.b31_60 + r.b31_60,
    b61_90: a.b61_90 + r.b61_90, b90: a.b90 + r.b90, total: a.total + r.total,
  }), { current: 0, b1_30: 0, b31_60: 0, b61_90: 0, b90: 0, total: 0 });

  return (
    <div className="page">
      <PageHeader title="AR Ageing Report" meta="As at 28 Feb 2026 · KES functional"
        actions={<><Button size="sm" icon="download" variant="ghost">Export</Button><Button size="sm" icon="envelope" variant="primary">Send statements</Button></>}/>
      <div className="page-section stack">
        <div className="kpi-grid" style={{ gridTemplateColumns: "repeat(5, 1fr)" }}>
          <Kpi label="Current" value={fmt(totals.current, { decimals: 0, compact: true })} unit="KES"/>
          <Kpi label="1–30 days" value={fmt(totals.b1_30, { decimals: 0, compact: true })} unit="KES"/>
          <Kpi label="31–60 days" value={fmt(totals.b31_60, { decimals: 0, compact: true })} unit="KES"/>
          <Kpi label="61–90 days" value={fmt(totals.b61_90, { decimals: 0, compact: true })} unit="KES"/>
          <Kpi label="90+ days" value={fmt(totals.b90, { decimals: 0, compact: true })} unit="KES"/>
        </div>
        <div className="card">
          <table className="tbl">
            <thead>
              <tr>
                <th>Customer</th>
                <th className="right-h">Current</th>
                <th className="right-h">1–30</th>
                <th className="right-h">31–60</th>
                <th className="right-h">61–90</th>
                <th className="right-h">90+</th>
                <th className="right-h">Total</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {AR_AGEING.map((r, i) => (
                <tr key={i}>
                  <td><strong>{r.customer}</strong></td>
                  <td className="num">{r.current ? fmt(r.current, { decimals: 0 }) : "—"}</td>
                  <td className="num">{r.b1_30 ? fmt(r.b1_30, { decimals: 0 }) : "—"}</td>
                  <td className="num">{r.b31_60 ? fmt(r.b31_60, { decimals: 0 }) : "—"}</td>
                  <td className="num" style={{ color: r.b61_90 ? "var(--warn)" : undefined }}>{r.b61_90 ? fmt(r.b61_90, { decimals: 0 }) : "—"}</td>
                  <td className="num" style={{ color: r.b90 ? "var(--neg)" : undefined }}>{r.b90 ? fmt(r.b90, { decimals: 0 }) : "—"}</td>
                  <td className="num" style={{ fontWeight: 600 }}>{fmt(r.total, { decimals: 0 })}</td>
                  <td><Button size="sm" variant="ghost" icon="envelope">Remind</Button></td>
                </tr>
              ))}
              <tr className="total-row">
                <td>Total</td>
                <td className="num">{fmt(totals.current, { decimals: 0 })}</td>
                <td className="num">{fmt(totals.b1_30, { decimals: 0 })}</td>
                <td className="num">{fmt(totals.b31_60, { decimals: 0 })}</td>
                <td className="num">{fmt(totals.b61_90, { decimals: 0 })}</td>
                <td className="num">{fmt(totals.b90, { decimals: 0 })}</td>
                <td className="num">{fmt(totals.total, { decimals: 0 })}</td>
                <td></td>
              </tr>
            </tbody>
          </table>
          <TableFooter total={AR_AGEING.length} label="customers"/>
        </div>
      </div>
    </div>
  );
};

Object.assign(window, { Journals, SourceDocs, Invoices, CreditNotes, Payments, Receipts, ARAgeing });
