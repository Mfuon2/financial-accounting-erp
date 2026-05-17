/* QeSuite — Users, API Keys, Security/Sessions, Auth (Login + Register/Org Wizard) */

/* ===================== USERS ===================== */
const Users = () => {
  const { USERS } = window.QSDATA;
  const [drawer, setDrawer] = useState(null);
  const [showNew, setShowNew] = useState(false);
  return (
    <div className="page">
      <PageHeader title="Users & Roles" meta={`${USERS.filter(u => u.active).length} active members · RBAC enforced`}
        actions={<><Button size="sm" icon="envelope" variant="ghost">Invite</Button><Button size="sm" icon="plus" variant="primary" onClick={() => setShowNew(true)}>New user</Button></>}/>
      <div className="page-section">
        <div className="card">
          <table className="tbl">
            <thead><tr><th>User</th><th>Email</th><th>Role</th><th>MFA</th><th>Last login</th><th>Status</th><th></th></tr></thead>
            <tbody>
              {USERS.map(u => (
                <tr key={u.id} className="clickable" onClick={() => setDrawer(u)}>
                  <td>
                    <div className="h-row" style={{ gap: 8 }}>
                      <div className="user-avatar">{u.fullName.split(" ").map(p => p[0]).join("").slice(0, 2)}</div>
                      <div>
                        <strong>{u.fullName}</strong>
                        <div className="muted mono" style={{ fontSize: 10.5 }}>@{u.username}</div>
                      </div>
                    </div>
                  </td>
                  <td className="mono" style={{ fontSize: 11.5 }}>{u.email}</td>
                  <td><Badge status={u.role === "ADMIN" ? "posted" : u.role === "SENIOR_ACCOUNTANT" ? "info" : u.role === "AUDITOR" ? "info" : "approved"}>{u.role.replace("_"," ")}</Badge></td>
                  <td>{u.mfa ? <Badge status="approved" dot>ON</Badge> : <Badge status="rejected" dot>OFF</Badge>}</td>
                  <td className="mono muted" style={{ fontSize: 11 }}>{u.lastLogin}</td>
                  <td>{u.active ? <Badge status="approved">Active</Badge> : <Badge status="archived">Suspended</Badge>}</td>
                  <td><span className="row-action"><IconBtn icon="dots"/></span></td>
                </tr>
              ))}
            </tbody>
          </table>
          <TableFooter total={USERS.length} label="users"/>
        </div>
      </div>
      <Drawer open={!!drawer} onClose={() => setDrawer(null)} title={drawer?.fullName} subtitle={drawer ? `${drawer.role} · ${drawer.email}` : ""}
        footer={<><Button variant="primary">Save</Button><Button icon="key">Reset password</Button><div style={{flex:1}}/><Button variant="danger" icon="lock">{drawer?.active ? "Deactivate" : "Reactivate"}</Button></>}>
        {drawer && (
          <div className="stack">
            <div className="form-grid cols-2">
              <div className="field"><label>Full name</label><input className="input" defaultValue={drawer.fullName}/></div>
              <div className="field"><label>Username</label><input className="input mono" defaultValue={drawer.username}/></div>
              <div className="field"><label>Email</label><input className="input" defaultValue={drawer.email}/></div>
              <div className="field"><label>Role</label>
                <select className="select" defaultValue={drawer.role}>
                  <option>ADMIN</option><option>SENIOR_ACCOUNTANT</option><option>ACCOUNTANT</option><option>AUDITOR</option><option>VIEWER</option>
                </select>
              </div>
            </div>
            <div className="divider"/>
            <div style={{ fontWeight: 600 }}>Permissions (computed from role)</div>
            <table className="tbl">
              <thead><tr><th>Capability</th><th>Permission</th></tr></thead>
              <tbody>
                <tr><td>Post journal entries</td><td><Badge status="approved" dot>ALLOW</Badge></td></tr>
                <tr><td>Approve journal entries</td><td>{drawer.role === "ACCOUNTANT" ? <Badge status="rejected" dot>DENY</Badge> : <Badge status="approved" dot>ALLOW</Badge>}</td></tr>
                <tr><td>Close periods</td><td>{drawer.role === "ADMIN" ? <Badge status="approved" dot>ALLOW</Badge> : <Badge status="rejected" dot>DENY</Badge>}</td></tr>
                <tr><td>Generate financial statements</td><td><Badge status="approved" dot>ALLOW</Badge></td></tr>
                <tr><td>Manage users & API keys</td><td>{drawer.role === "ADMIN" ? <Badge status="approved" dot>ALLOW</Badge> : <Badge status="rejected" dot>DENY</Badge>}</td></tr>
                <tr><td>Read-only audit access</td><td><Badge status="approved" dot>ALLOW</Badge></td></tr>
              </tbody>
            </table>
          </div>
        )}
      </Drawer>
      <Drawer open={showNew} onClose={() => setShowNew(false)} title="Invite new user"
        footer={<><Button variant="primary" icon="envelope">Send invite</Button><Button variant="ghost" onClick={() => setShowNew(false)}>Cancel</Button></>}>
        <div className="form-grid cols-2">
          <div className="field"><label>Full name <span className="req">*</span></label><input className="input"/></div>
          <div className="field"><label>Email <span className="req">*</span></label><input className="input"/></div>
          <div className="field"><label>Username</label><input className="input mono"/></div>
          <div className="field"><label>Role <span className="req">*</span></label><select className="select"><option>ACCOUNTANT</option><option>SENIOR_ACCOUNTANT</option><option>ADMIN</option><option>AUDITOR</option><option>VIEWER</option></select></div>
          <div className="field" style={{ gridColumn: "1 / -1" }}><label>Welcome message</label><textarea className="textarea" placeholder="Optional note included in the invite email"/></div>
        </div>
      </Drawer>
    </div>
  );
};

/* ===================== API KEYS ===================== */
const ApiKeys = () => {
  const { API_KEYS } = window.QSDATA;
  const [showNew, setShowNew] = useState(false);
  const [newKey, setNewKey] = useState(null);
  return (
    <div className="page">
      <PageHeader title="API Keys" meta={`${API_KEYS.filter(k => k.active).length} active · used by third-party integrations`}
        actions={<><Button size="sm" icon="external" variant="ghost">API docs</Button><Button size="sm" icon="plus" variant="primary" onClick={() => setShowNew(true)}>Generate key</Button></>}/>
      <div className="page-section">
        <div className="card">
          <table className="tbl">
            <thead><tr><th>Label</th><th>Key prefix</th><th>Scope</th><th>Created</th><th>Last used</th><th>Expires</th><th>Status</th><th></th></tr></thead>
            <tbody>
              {API_KEYS.map(k => (
                <tr key={k.id}>
                  <td><strong>{k.label}</strong></td>
                  <td className="code-cell">{k.prefix}<span className="muted">…</span></td>
                  <td className="muted mono" style={{ fontSize: 11 }}>{k.scope}</td>
                  <td className="mono muted" style={{ fontSize: 11 }}>{fmtDate(k.created)}</td>
                  <td className="mono muted" style={{ fontSize: 11 }}>{k.lastUsed}</td>
                  <td className="mono muted" style={{ fontSize: 11 }}>{k.expires ? fmtDate(k.expires) : "never"}</td>
                  <td>{k.active ? <Badge status="approved">Active</Badge> : <Badge status="archived">Revoked</Badge>}</td>
                  <td><Button size="sm" variant="ghost" icon="rotate">Rotate</Button> <Button size="sm" variant="ghost" icon="reject">Revoke</Button></td>
                </tr>
              ))}
            </tbody>
          </table>
          <TableFooter total={API_KEYS.length} label="keys"/>
        </div>
      </div>
      <Modal open={showNew && !newKey} onClose={() => setShowNew(false)} title="Generate API key" width={520}
        footer={<><Button variant="primary" onClick={() => setNewKey({ secret: "qek_live_" + Math.random().toString(36).slice(2, 14) + Math.random().toString(36).slice(2, 14) })}>Generate</Button><Button variant="ghost" onClick={() => setShowNew(false)}>Cancel</Button></>}>
        <div className="form-grid">
          <div className="field"><label>Label <span className="req">*</span></label><input className="input" placeholder="e.g. Mobile App — Production"/></div>
          <div className="field"><label>Scope</label><select className="select"><option>read:* (read-only)</option><option>read:invoices, write:payments</option><option>read:*, write:*</option></select></div>
          <div className="field"><label>Expires</label><select className="select"><option>Never</option><option>90 days</option><option>1 year</option></select></div>
        </div>
      </Modal>
      <Modal open={!!newKey} onClose={() => { setNewKey(null); setShowNew(false); }} title="Key generated — copy now"
        footer={<Button variant="primary" onClick={() => { setNewKey(null); setShowNew(false); }}>I've copied it</Button>}>
        <div className="banner warn"><Ico name="warn" size={14} className="b-ico"/><div><strong>Save this key immediately.</strong> For security, we won't show it again.</div></div>
        <div className="field" style={{ marginTop: 12 }}>
          <label>Secret key</label>
          <div className="input mono" style={{ background: "var(--code-bg)", userSelect: "all" }}>{newKey?.secret}</div>
        </div>
      </Modal>
    </div>
  );
};

/* ===================== SECURITY ===================== */
const Security = () => {
  const { SESSIONS } = window.QSDATA;
  const [showPwd, setShowPwd] = useState(false);
  return (
    <div className="page">
      <PageHeader title="Security" meta="Password, MFA & active sessions"/>
      <div className="page-section stack">
        <div className="row-2">
          <div className="card">
            <div className="card-head"><Ico name="lock" size={13}/> Password</div>
            <div className="card-body stack">
              <div className="muted" style={{ fontSize: 12 }}>Last changed 28 days ago. Changing your password will sign you out of all other sessions.</div>
              <Button onClick={() => setShowPwd(true)}>Change password</Button>
            </div>
          </div>
          <div className="card">
            <div className="card-head"><Ico name="shield" size={13}/> Two-factor authentication</div>
            <div className="card-body stack">
              <div className="h-row" style={{ justifyContent: "space-between" }}>
                <div>
                  <div style={{ fontWeight: 600 }}>Authenticator app</div>
                  <div className="muted" style={{ fontSize: 11 }}>TOTP · 6-digit codes</div>
                </div>
                <Badge status="approved" dot>ENABLED</Badge>
              </div>
              <div className="h-row" style={{ justifyContent: "space-between" }}>
                <div>
                  <div style={{ fontWeight: 600 }}>Recovery codes</div>
                  <div className="muted" style={{ fontSize: 11 }}>8 of 10 remaining</div>
                </div>
                <Button size="sm" variant="ghost" icon="download">View codes</Button>
              </div>
            </div>
          </div>
        </div>
        <div className="card">
          <div className="card-head"><Ico name="globe" size={13}/> Active sessions · {SESSIONS.length}</div>
          <table className="tbl">
            <thead><tr><th>Device</th><th>IP address</th><th>Location</th><th>Started</th><th></th></tr></thead>
            <tbody>
              {SESSIONS.map(s => (
                <tr key={s.id}>
                  <td>
                    <div className="h-row">
                      <span>{s.device}</span>
                      {s.current && <Badge status="approved" dot>This device</Badge>}
                    </div>
                  </td>
                  <td className="mono">{s.ip}</td>
                  <td className="muted">{s.location}</td>
                  <td className="mono muted" style={{ fontSize: 11 }}>{s.started}</td>
                  <td>{!s.current && <Button size="sm" variant="ghost" icon="reject">Revoke</Button>}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <TableFooter total={SESSIONS.length} label="sessions" defaultSize={10}/>
          <div className="drawer-foot"><Button variant="danger" icon="logout">Sign out all other sessions</Button></div>
        </div>
      </div>
      <Modal open={showPwd} onClose={() => setShowPwd(false)} title="Change password"
        footer={<><Button variant="primary">Update password</Button><Button variant="ghost" onClick={() => setShowPwd(false)}>Cancel</Button></>}>
        <div className="form-grid">
          <div className="field"><label>Current password</label><input className="input" type="password"/></div>
          <div className="field"><label>New password</label><input className="input" type="password"/></div>
          <div className="field"><label>Confirm new password</label><input className="input" type="password"/></div>
          <div className="banner info"><Ico name="info" size={14} className="b-ico"/><div>You'll be signed out of all other sessions and need to log in again.</div></div>
        </div>
      </Modal>
    </div>
  );
};

/* ===================== LOGIN ===================== */
const Login = ({ onLogin, onSignup }) => {
  return (
    <div className="auth-screen">
      <div className="auth-poster">
        <div className="auth-brand">
          <div className="brand-mark"><span>Q</span></div>
          <div className="brand-name" style={{ fontSize: 16 }}>QeSuite</div>
          <div className="brand-suffix">IFRS · ENTERPRISE</div>
        </div>
        <div>
          <h1 className="auth-headline">The accounting system <em>auditors</em> don't argue with.</h1>
          <div className="auth-sub">A full IFRS-compliant general ledger, revenue cycle, and period-end engine. Built for accountants, finance officers, auditors, and ops teams who close on time, every time.</div>
        </div>
        <div className="auth-stats">
          <div><div className="v">9-step</div><div className="l">Accounting cycle automated</div></div>
          <div><div className="v">IAS 1·7·8·21·15</div><div className="l">Standards in the engine</div></div>
          <div><div className="v">0.00</div><div className="l">Unbalanced periods, ever</div></div>
        </div>
      </div>
      <div className="auth-form-pane">
        <form className="auth-form" onSubmit={(e) => { e.preventDefault(); onLogin(); }}>
          <h3>Welcome back</h3>
          <div className="auth-sub">Sign in to <strong>Apollo Enterprises Ltd</strong>.</div>
          <div className="field"><label>Email or username</label><input className="input" defaultValue="j.muriuki@apollo.co.ke" autoFocus/></div>
          <div className="field"><label>Password</label><input className="input" type="password" defaultValue="••••••••••••"/></div>
          <div className="h-row" style={{ justifyContent: "space-between", fontSize: 12 }}>
            <label className="h-row"><input type="checkbox" defaultChecked/> Remember this device</label>
            <a className="muted" style={{ cursor: "pointer" }}>Forgot password?</a>
          </div>
          <Button variant="primary" size="lg" type="submit">Sign in <Ico name="chev-right" size={12}/></Button>
          <div className="divider"/>
          <div className="muted" style={{ fontSize: 11.5, textAlign: "center" }}>
            New here? <a onClick={onSignup} style={{ color: "var(--accent)", cursor: "pointer", fontWeight: 600 }}>Create an organization</a>
          </div>
          <div className="muted" style={{ fontSize: 10.5, textAlign: "center", marginTop: 8 }}>JWT · MFA enforced · SOC2-aligned</div>
        </form>
      </div>
    </div>
  );
};

/* ===================== SIGNUP / ORG WIZARD ===================== */
const Signup = ({ onLogin, onCancel }) => {
  const [step, setStep] = useState(0);
  const steps = ["Organization", "Admin user", "Currency & period", "Done"];
  return (
    <div className="auth-screen" style={{ gridTemplateColumns: "1fr 560px" }}>
      <div className="auth-poster">
        <div className="auth-brand">
          <div className="brand-mark"><span>Q</span></div>
          <div className="brand-name" style={{ fontSize: 16 }}>QeSuite</div>
          <div className="brand-suffix">IFRS</div>
        </div>
        <div>
          <h1 className="auth-headline">Set up your <em>book of record</em> in 3 minutes.</h1>
          <div className="auth-sub">We'll bootstrap your org, seed an IFRS-compliant chart of accounts, generate your first fiscal year, and hand you a clean ledger to start posting.</div>
        </div>
        <div className="auth-stats">
          <div><div className="v">IFRS COA</div><div className="l">Templates: Services · Trading · Manufacturing</div></div>
          <div><div className="v">Multi-FX</div><div className="l">KES · USD · EUR · GBP out of box</div></div>
          <div><div className="v">RBAC + MFA</div><div className="l">From day one</div></div>
        </div>
      </div>
      <div className="auth-form-pane" style={{ alignItems: "flex-start", paddingTop: 60 }}>
        <div className="auth-form" style={{ maxWidth: 420 }}>
          <h3>Create your organization</h3>
          <div className="stepper" style={{ marginTop: 10, marginBottom: 16 }}>
            {steps.map((s, i) => (
              <div key={i} className={`step ${i < step ? "done" : i === step ? "active" : ""}`}>
                <div className="step-num">{i < step ? <Ico name="check" size={10}/> : i + 1}</div>
                <div>{s}</div>
                <div className="step-line"/>
              </div>
            ))}
          </div>
          {step === 0 && (
            <div className="stack">
              <div className="field"><label>Trading name</label><input className="input" defaultValue="Apollo Enterprises Ltd"/></div>
              <div className="field"><label>Legal name</label><input className="input" defaultValue="Apollo Enterprises Limited"/></div>
              <div className="field"><label>Registration No.</label><input className="input mono" defaultValue="PVT-20240001"/></div>
              <div className="field"><label>Tax ID (KRA PIN)</label><input className="input mono" defaultValue="A001234567A"/></div>
              <div className="field"><label>Country</label><select className="select"><option>Kenya (KE)</option><option>Uganda (UG)</option><option>Tanzania (TZ)</option><option>Rwanda (RW)</option></select></div>
            </div>
          )}
          {step === 1 && (
            <div className="stack">
              <div className="field"><label>Your full name</label><input className="input" defaultValue="Jane Muriuki"/></div>
              <div className="field"><label>Email</label><input className="input" defaultValue="j.muriuki@apollo.co.ke"/></div>
              <div className="field"><label>Username</label><input className="input mono" defaultValue="j.muriuki"/></div>
              <div className="field"><label>Password</label><input className="input" type="password" defaultValue="••••••••"/></div>
              <Banner kind="info">You'll be created with <Badge status="posted">ADMIN</Badge> role. MFA setup follows after first login.</Banner>
            </div>
          )}
          {step === 2 && (
            <div className="stack">
              <div className="form-grid cols-2">
                <div className="field"><label>Functional currency</label><select className="select"><option>KES — Kenyan Shilling</option><option>USD</option><option>EUR</option></select></div>
                <div className="field"><label>Reporting currency</label><select className="select"><option>KES</option><option>USD</option></select></div>
                <div className="field"><label>Fiscal year start</label><select className="select"><option>January</option><option>April</option><option>July</option></select></div>
                <div className="field"><label>COA template</label><select className="select"><option>SERVICE (IFRS)</option><option>TRADING (IFRS)</option><option>MANUFACTURING (IFRS)</option><option>Empty</option></select></div>
              </div>
              <Banner kind="success" icon="check">We'll seed your COA, generate FY 2026 periods, register VAT 16% and KES exchange rates.</Banner>
            </div>
          )}
          {step === 3 && (
            <div className="stack" style={{ alignItems: "center", textAlign: "center", padding: "20px 0" }}>
              <div style={{ width: 56, height: 56, borderRadius: "50%", background: "color-mix(in oklab, var(--pos) 16%, transparent)", color: "var(--pos)", display: "flex", alignItems: "center", justifyContent: "center" }}>
                <Ico name="check" size={28}/>
              </div>
              <h3 style={{ margin: 0 }}>You're all set</h3>
              <div className="muted" style={{ fontSize: 12.5 }}>Organization, admin user, COA, periods, tax codes & currencies provisioned. Time to post your first journal.</div>
            </div>
          )}
          <div className="h-row" style={{ gap: 8, marginTop: 12 }}>
            {step > 0 && step < 3 && <Button variant="ghost" onClick={() => setStep(s => s - 1)}>Back</Button>}
            {step < 3 ? <Button variant="primary" onClick={() => setStep(s => s + 1)}>Continue <Ico name="chev-right" size={12}/></Button> : <Button variant="primary" onClick={onLogin}>Open dashboard</Button>}
            <div style={{ flex: 1 }}/>
            <Button variant="ghost" onClick={onCancel}>Cancel</Button>
          </div>
        </div>
      </div>
    </div>
  );
};

Object.assign(window, { Users, ApiKeys, Security, Login, Signup });
