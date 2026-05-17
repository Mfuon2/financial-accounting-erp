/* QeSuite — UI primitives. Icons (inline SVG), badges, helpers, formatting. */

const { useState, useEffect, useRef, useMemo, useCallback, createContext, useContext } = React;

/* ============ FORMATTING ============ */
const fmt = (n, opts = {}) => {
  if (n == null || isNaN(n)) return "—";
  const { currency = null, decimals = 2, signed = false, parens = false, compact = false } = opts;
  const abs = Math.abs(n);
  let body;
  if (compact && abs >= 1_000_000) body = (n / 1_000_000).toFixed(2).replace(/\.?0+$/, "") + "M";
  else if (compact && abs >= 1_000) body = (n / 1_000).toFixed(1).replace(/\.?0+$/, "") + "K";
  else body = abs.toLocaleString("en-US", { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
  if (parens && n < 0) body = "(" + body + ")";
  else if (n < 0) body = "−" + body;
  else if (signed && n > 0) body = "+" + body;
  if (currency) body = currency + " " + body;
  return body;
};
const fmtDate = (s) => {
  if (!s) return "—";
  const d = new Date(s);
  return d.toLocaleDateString("en-GB", { day: "2-digit", month: "short", year: "numeric" });
};
const fmtDateTime = (s) => {
  if (!s) return "—";
  const d = new Date(s.replace(" ", "T"));
  if (isNaN(d)) return s;
  return d.toLocaleDateString("en-GB", { day: "2-digit", month: "short", year: "numeric" }) + " · " + d.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
};

/* ============ ICONS (inline, geometric, monoline) ============ */
const Ico = ({ name, size = 14, ...rest }) => {
  const s = size;
  const props = { width: s, height: s, viewBox: "0 0 16 16", fill: "none", stroke: "currentColor", strokeWidth: 1.4, strokeLinecap: "round", strokeLinejoin: "round", ...rest };
  switch (name) {
    case "search":    return <svg {...props}><circle cx="7" cy="7" r="4.5"/><path d="M13.5 13.5l-3-3"/></svg>;
    case "filter":    return <svg {...props}><path d="M2 4h12M4 8h8M6 12h4"/></svg>;
    case "sort":      return <svg {...props}><path d="M5 3v10M3 11l2 2 2-2M11 13V3M9 5l2-2 2 2"/></svg>;
    case "plus":      return <svg {...props}><path d="M8 3v10M3 8h10"/></svg>;
    case "chevron":   return <svg {...props}><path d="M5 3l4 5-4 5"/></svg>;
    case "chev-down": return <svg {...props}><path d="M3 5l5 4 5-4"/></svg>;
    case "chev-right":return <svg {...props}><path d="M5 3l4 5-4 5"/></svg>;
    case "menu":      return <svg {...props}><path d="M2 4h12M2 8h12M2 12h12"/></svg>;
    case "dots":      return <svg {...props}><circle cx="3" cy="8" r="1" fill="currentColor"/><circle cx="8" cy="8" r="1" fill="currentColor"/><circle cx="13" cy="8" r="1" fill="currentColor"/></svg>;
    case "x":         return <svg {...props}><path d="M3 3l10 10M13 3L3 13"/></svg>;
    case "check":     return <svg {...props}><path d="M2.5 8.5l3 3 8-8"/></svg>;
    case "circle":    return <svg {...props}><circle cx="8" cy="8" r="6"/></svg>;
    case "user":      return <svg {...props}><circle cx="8" cy="5.5" r="2.5"/><path d="M3 14c1-2.5 3-3.5 5-3.5s4 1 5 3.5"/></svg>;
    case "users":     return <svg {...props}><circle cx="6" cy="5.5" r="2"/><path d="M2 13.5c.5-2 2-3 4-3s3.5 1 4 3"/><circle cx="11.5" cy="6" r="1.6"/><path d="M14.5 13c-.3-1.5-1.4-2.4-3-2.4"/></svg>;
    case "key":       return <svg {...props}><circle cx="5" cy="11" r="2.5"/><path d="M6.8 9.2l5.7-5.7M10 6.5l1.5 1.5M11.5 5l1.5 1.5"/></svg>;
    case "shield":    return <svg {...props}><path d="M8 2l5 1.5v4.2c0 3.4-2.3 5.7-5 6.3-2.7-.6-5-2.9-5-6.3V3.5L8 2z"/></svg>;
    case "doc":       return <svg {...props}><path d="M4 1.5h5l3 3V14a.5.5 0 0 1-.5.5h-7A.5.5 0 0 1 4 14V2a.5.5 0 0 1 .5-.5z"/><path d="M9 1.5V4a.5.5 0 0 0 .5.5H12"/></svg>;
    case "docs":      return <svg {...props}><path d="M3 4.5h6l2.5 2.5V13.5a.5.5 0 0 1-.5.5h-8A.5.5 0 0 1 3 13.5V5z"/><path d="M5 2.5h6l2.5 2.5"/></svg>;
    case "ledger":    return <svg {...props}><rect x="2.5" y="2.5" width="11" height="11" rx="1"/><path d="M2.5 6h11M2.5 10h11M6 2.5v11"/></svg>;
    case "journal":   return <svg {...props}><rect x="3" y="2" width="10" height="12" rx="1"/><path d="M5 5h6M5 8h6M5 11h4"/></svg>;
    case "scale":     return <svg {...props}><path d="M8 2v12M3 5l3-1 3 1 3-1 3 1M5.5 4l-2 5a2 2 0 0 0 4 0l-2-5M10.5 4l-2 5a2 2 0 0 0 4 0l-2-5"/></svg>;
    case "coin":      return <svg {...props}><circle cx="8" cy="8" r="6"/><path d="M8 4.5v7M5.5 6.5c.5-.7 1.4-1 2.5-1s2 .5 2 1.5-1 1.3-2 1.3-2 .3-2 1.3 1 1.5 2 1.5 2-.3 2.5-1"/></svg>;
    case "bank":      return <svg {...props}><path d="M2 7h12M3 7l5-4 5 4M4 7v6M7 7v6M10 7v6M13 7v6M2 14h12"/></svg>;
    case "card":      return <svg {...props}><rect x="2" y="4" width="12" height="9" rx="1.2"/><path d="M2 7h12"/></svg>;
    case "trend-up":  return <svg {...props}><path d="M2 12l4-4 3 3 5-5M9 6h4v4"/></svg>;
    case "trend-down":return <svg {...props}><path d="M2 4l4 4 3-3 5 5M9 10h4V6"/></svg>;
    case "bell":      return <svg {...props}><path d="M4 12V8a4 4 0 0 1 8 0v4M3 12h10M7 14h2"/></svg>;
    case "clock":     return <svg {...props}><circle cx="8" cy="8" r="6"/><path d="M8 4.5V8l2 1.5"/></svg>;
    case "calendar":  return <svg {...props}><rect x="2.5" y="3.5" width="11" height="10" rx="1"/><path d="M2.5 6.5h11M5 2v3M11 2v3"/></svg>;
    case "tag":       return <svg {...props}><path d="M2 8.5V3.5A1 1 0 0 1 3 2.5h5l5.5 5.5a1 1 0 0 1 0 1.4l-4 4a1 1 0 0 1-1.4 0L2 8.5z"/><circle cx="5.5" cy="5.5" r=".7" fill="currentColor"/></svg>;
    case "settings":  return <svg {...props}><circle cx="8" cy="8" r="2"/><path d="M8 1.5v2M8 12.5v2M1.5 8h2M12.5 8h2M3 3l1.5 1.5M11.5 11.5L13 13M3 13l1.5-1.5M11.5 4.5L13 3"/></svg>;
    case "sliders":   return <svg {...props}><path d="M2 4h12M2 8h12M2 12h12"/><circle cx="5" cy="4" r="1.5" fill="var(--surface)"/><circle cx="10" cy="8" r="1.5" fill="var(--surface)"/><circle cx="7" cy="12" r="1.5" fill="var(--surface)"/></svg>;
    case "globe":     return <svg {...props}><circle cx="8" cy="8" r="6"/><path d="M2 8h12M8 2c2 2 2 10 0 12M8 2c-2 2-2 10 0 12"/></svg>;
    case "building":  return <svg {...props}><path d="M3 14V3h7v11M10 7h3v7M5 6h1M5 8.5h1M5 11h1M7.5 6h1M7.5 8.5h1M7.5 11h1"/></svg>;
    case "truck":     return <svg {...props}><path d="M1.5 4.5h8v6h-8z M9.5 7h3l1.5 2v1.5h-4.5"/><circle cx="4" cy="12" r="1.3"/><circle cx="11.5" cy="12" r="1.3"/></svg>;
    case "package":   return <svg {...props}><path d="M2.5 4.5L8 2l5.5 2.5L8 7 2.5 4.5z M2.5 4.5V11L8 13.5l5.5-2.5V4.5M8 7v6.5"/></svg>;
    case "receipt":   return <svg {...props}><path d="M3.5 1.5v13L5 13l1.5 1.5L8 13l1.5 1.5L11 13l1.5 1.5v-13M5 4h6M5 6.5h6M5 9h4"/></svg>;
    case "envelope":  return <svg {...props}><rect x="2" y="3.5" width="12" height="9" rx="1"/><path d="M2 4.5l6 4 6-4"/></svg>;
    case "lock":      return <svg {...props}><rect x="3" y="7" width="10" height="6.5" rx="1"/><path d="M5 7V5a3 3 0 0 1 6 0v2"/></svg>;
    case "external":  return <svg {...props}><path d="M6 3H3v10h10v-3M9 3h4v4M13 3L7.5 8.5"/></svg>;
    case "download":  return <svg {...props}><path d="M8 2v9M4 7l4 4 4-4M2 14h12"/></svg>;
    case "upload":    return <svg {...props}><path d="M8 11V2M4 6l4-4 4 4M2 14h12"/></svg>;
    case "refresh":   return <svg {...props}><path d="M13 4.5A6 6 0 0 0 2.5 7M13 2v3h-3M3 11.5A6 6 0 0 0 13.5 9M3 14v-3h3"/></svg>;
    case "play":      return <svg {...props}><path d="M4 3l8 5-8 5z" fill="currentColor"/></svg>;
    case "pause":     return <svg {...props}><path d="M5 3h2v10H5zM9 3h2v10H9z" fill="currentColor"/></svg>;
    case "approve":   return <svg {...props}><circle cx="8" cy="8" r="6"/><path d="M5 8l2 2 4-4"/></svg>;
    case "reject":    return <svg {...props}><circle cx="8" cy="8" r="6"/><path d="M5.5 5.5l5 5M10.5 5.5l-5 5"/></svg>;
    case "warn":      return <svg {...props}><path d="M8 2l6 11H2L8 2z"/><path d="M8 6.5v3M8 11v.5"/></svg>;
    case "info":      return <svg {...props}><circle cx="8" cy="8" r="6"/><path d="M8 5.5h.01M8 7.5v3.5"/></svg>;
    case "link":      return <svg {...props}><path d="M6.5 8.5l3-3M5.5 10.5L4 12a2.1 2.1 0 0 1-3-3l1.5-1.5M10.5 5.5L12 4a2.1 2.1 0 0 1 3 3l-1.5 1.5"/></svg>;
    case "branch":    return <svg {...props}><circle cx="4" cy="3.5" r="1.5"/><circle cx="4" cy="12.5" r="1.5"/><circle cx="12" cy="6" r="1.5"/><path d="M4 5v6M4 8.5h4.5a3 3 0 0 0 3-3"/></svg>;
    case "fa":        return <svg {...props}><path d="M2 13.5h12M3.5 13.5V9l4.5-3 4.5 3v4.5M6 13.5v-3h4v3"/></svg>;
    case "asset":     return <svg {...props}><rect x="2" y="3" width="12" height="10" rx="1"/><path d="M5 6.5h6M5 9h6M5 11.5h3"/></svg>;
    case "spark":     return <svg {...props}><path d="M1 12l3-4 3 2 3-5 3 3 2-2"/></svg>;
    case "fx":        return <svg {...props}><path d="M2 6h6M5 3v6M9 13h5M9 10h5M11.5 8L9 10.5l2.5 2.5"/></svg>;
    case "chart":     return <svg {...props}><path d="M2 14V2M2 14h12M5 11V8M8 11V5M11 11v-4"/></svg>;
    case "command":   return <svg {...props}><path d="M5 5.5A1.5 1.5 0 1 1 6.5 7H5zM11 5.5A1.5 1.5 0 1 0 9.5 7H11zM5 10.5A1.5 1.5 0 1 0 6.5 9H5zM11 10.5A1.5 1.5 0 1 1 9.5 9H11zM6.5 7v2M9.5 7v2M6.5 7h3M6.5 9h3"/></svg>;
    case "logout":    return <svg {...props}><path d="M6 2H2v12h4M10 4l4 4-4 4M14 8H6"/></svg>;
    case "eye":       return <svg {...props}><path d="M1.5 8C3 4.5 5.5 3 8 3s5 1.5 6.5 5C13 11.5 10.5 13 8 13S3 11.5 1.5 8z"/><circle cx="8" cy="8" r="2"/></svg>;
    case "rotate":    return <svg {...props}><path d="M2 8a6 6 0 1 0 1.5-4M2 3v4h4"/></svg>;
    default:          return <svg {...props}><circle cx="8" cy="8" r="3"/></svg>;
  }
};

/* ============ BADGES ============ */
const Badge = ({ status, dot = true, children }) => {
  const cls = (status || "").toLowerCase().replace(/_/g, "-");
  return <span className={`badge ${dot ? "dot" : ""} ${cls}`}>{children || status}</span>;
};

/* ============ BUTTONS ============ */
const Button = ({ variant = "default", size = "md", icon, children, onClick, disabled, type = "button", className = "" }) => {
  const v = variant === "primary" ? "primary" : variant === "ghost" ? "ghost" : variant === "danger" ? "danger" : "";
  const s = size === "sm" ? "sm" : size === "lg" ? "lg" : "";
  return (
    <button type={type} className={`btn ${v} ${s} ${className}`} onClick={onClick} disabled={disabled}>
      {icon && <Ico name={icon} size={size === "sm" ? 11 : 13} />}
      {children}
    </button>
  );
};

const IconBtn = ({ icon, title, onClick, hasDot = false }) => (
  <button className={`icon-btn ${hasDot ? "has-dot" : ""}`} title={title} onClick={onClick}>
    <Ico name={icon} size={14} />
  </button>
);

/* ============ SPARKLINE ============ */
const Sparkline = ({ data, w = 80, h = 22, stroke = "var(--accent)", fill = "var(--accent-soft)", strokeWidth = 1.4 }) => {
  if (!data || !data.length) return null;
  const min = Math.min(...data), max = Math.max(...data);
  const span = max - min || 1;
  const pts = data.map((v, i) => {
    const x = (i / (data.length - 1)) * w;
    const y = h - ((v - min) / span) * (h - 3) - 1.5;
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  });
  const d = "M" + pts.join(" L");
  const area = d + ` L${w},${h} L0,${h} Z`;
  return (
    <svg width={w} height={h} className="spark">
      <path d={area} fill={fill} />
      <path d={d} fill="none" stroke={stroke} strokeWidth={strokeWidth} strokeLinejoin="round" strokeLinecap="round"/>
    </svg>
  );
};

/* ============ BAR/COL CHART (mini) ============ */
const BarChart = ({ data, w = 280, h = 90, color = "var(--accent)", labels = [] }) => {
  const max = Math.max(...data);
  const bw = (w - 8) / data.length;
  return (
    <svg width={w} height={h + 14}>
      {data.map((v, i) => {
        const bh = (v / max) * h;
        return <g key={i}>
          <rect x={i * bw + 2} y={h - bh} width={bw - 4} height={bh} fill={color} rx="2"/>
          {labels[i] && <text x={i * bw + bw / 2} y={h + 11} fontSize="9" fill="var(--muted)" textAnchor="middle" fontFamily="var(--font-mono)">{labels[i]}</text>}
        </g>;
      })}
    </svg>
  );
};

/* ============ LINE CHART (medium) ============ */
const LineChart = ({ series, w = 600, h = 180, labels = [] }) => {
  // series: [{ name, color, data: [...] }]
  const allVals = series.flatMap(s => s.data);
  const max = Math.max(...allVals), min = Math.min(0, ...allVals);
  const span = max - min || 1;
  const padL = 36, padR = 8, padT = 8, padB = 22;
  const innerW = w - padL - padR, innerH = h - padT - padB;
  const xStep = innerW / (series[0].data.length - 1);
  const toPath = (data) => data.map((v, i) => {
    const x = padL + i * xStep;
    const y = padT + innerH - ((v - min) / span) * innerH;
    return `${i === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`;
  }).join(" ");
  const gridLines = 4;
  return (
    <svg width={w} height={h}>
      {Array.from({ length: gridLines + 1 }).map((_, i) => {
        const y = padT + (innerH / gridLines) * i;
        const v = max - ((max - min) / gridLines) * i;
        return <g key={i}>
          <line x1={padL} y1={y} x2={w - padR} y2={y} stroke="var(--border)" strokeDasharray="2 3"/>
          <text x={padL - 5} y={y + 3} textAnchor="end" fontSize="9" fill="var(--muted)" fontFamily="var(--font-mono)">{v.toFixed(1)}M</text>
        </g>;
      })}
      {labels.map((l, i) => (
        <text key={i} x={padL + i * xStep} y={h - 4} textAnchor="middle" fontSize="9" fill="var(--muted)" fontFamily="var(--font-mono)">{l}</text>
      ))}
      {series.map((s, idx) => (
        <g key={idx}>
          <path d={toPath(s.data)} fill="none" stroke={s.color} strokeWidth="1.6" strokeLinejoin="round"/>
          {s.data.map((v, i) => {
            const x = padL + i * xStep;
            const y = padT + innerH - ((v - min) / span) * innerH;
            return <circle key={i} cx={x} cy={y} r="1.6" fill={s.color}/>;
          })}
        </g>
      ))}
    </svg>
  );
};

/* ============ DONUT ============ */
const Donut = ({ segments, size = 110, thickness = 14 }) => {
  // segments: [{ label, value, color }]
  const total = segments.reduce((a, b) => a + b.value, 0);
  const r = (size - thickness) / 2;
  const c = size / 2;
  const circ = 2 * Math.PI * r;
  let acc = 0;
  return (
    <svg width={size} height={size}>
      <circle cx={c} cy={c} r={r} stroke="var(--border)" strokeWidth={thickness} fill="none"/>
      {segments.map((s, i) => {
        const frac = s.value / total;
        const dash = frac * circ;
        const off = (acc / total) * circ;
        acc += s.value;
        return <circle key={i}
          cx={c} cy={c} r={r}
          stroke={s.color} strokeWidth={thickness} fill="none"
          strokeDasharray={`${dash} ${circ - dash}`}
          strokeDashoffset={-off}
          strokeLinecap="butt"
          transform={`rotate(-90 ${c} ${c})`}
        />;
      })}
    </svg>
  );
};

/* ============ DRAWER (right-side) ============ */
const Drawer = ({ open, onClose, title, subtitle, children, footer, size = "md" }) => {
  useEffect(() => {
    if (!open) return;
    const h = (e) => e.key === "Escape" && onClose && onClose();
    window.addEventListener("keydown", h);
    return () => window.removeEventListener("keydown", h);
  }, [open, onClose]);
  if (!open) return null;
  return (
    <div className="scrim" onClick={(e) => { if (e.target.classList.contains("scrim")) onClose && onClose(); }}>
      <div className={`drawer ${size}`}>
        <div className="drawer-head">
          <div style={{ flex: 1 }}>
            <div className="drawer-title">{title}</div>
            {subtitle && <div className="muted mono" style={{ fontSize: 11, marginTop: 2 }}>{subtitle}</div>}
          </div>
          <IconBtn icon="x" title="Close" onClick={onClose}/>
        </div>
        <div className="drawer-body">{children}</div>
        {footer && <div className="drawer-foot">{footer}</div>}
      </div>
    </div>
  );
};

/* ============ MODAL (centered) ============ */
const Modal = ({ open, onClose, title, children, footer, width = 540 }) => {
  useEffect(() => {
    if (!open) return;
    const h = (e) => e.key === "Escape" && onClose && onClose();
    window.addEventListener("keydown", h);
    return () => window.removeEventListener("keydown", h);
  }, [open, onClose]);
  if (!open) return null;
  return (
    <div className="scrim center" onClick={(e) => { if (e.target.classList.contains("scrim")) onClose && onClose(); }}>
      <div className="modal" style={{ width }}>
        <div className="drawer-head">
          <div className="drawer-title" style={{ flex: 1 }}>{title}</div>
          <IconBtn icon="x" title="Close" onClick={onClose}/>
        </div>
        <div className="drawer-body">{children}</div>
        {footer && <div className="drawer-foot">{footer}</div>}
      </div>
    </div>
  );
};

/* ============ SEARCH FIELD ============ */
const SearchField = ({ value, onChange, placeholder = "Search…", width }) => (
  <div className="tbl-search" style={{ width }}>
    <Ico name="search" size={12} style={{ color: "var(--muted)" }}/>
    <input value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} />
    <span className="kbd" style={{ fontSize: 9 }}>/</span>
  </div>
);

/* ============ CHIP FILTER ============ */
const ChipFilter = ({ active, onClick, children, removable = false }) => (
  <span className={`chip-filter ${active ? "active" : ""}`} onClick={onClick}>
    {children}
    {removable && <Ico name="x" size={10} className="x"/>}
  </span>
);

/* ============ SEGMENTED ============ */
const Segmented = ({ value, onChange, options }) => (
  <div className="segmented">
    {options.map(o => (
      <div key={o.value} className={`seg ${value === o.value ? "active" : ""}`} onClick={() => onChange(o.value)}>
        {o.label}
      </div>
    ))}
  </div>
);

/* ============ TOGGLE ============ */
const Toggle = ({ value, onChange, label }) => (
  <label className="toggle" data-on={!!value} onClick={() => onChange(!value)}>
    <span className="toggle-track"/>
    {label && <span style={{ fontSize: 12 }}>{label}</span>}
  </label>
);

/* ============ KPI ============ */
const Kpi = ({ label, icon, value, unit, delta, deltaLabel, spark, sparkColor }) => (
  <div className="kpi">
    <div className="kpi-label">
      {icon && <Ico name={icon} size={12} className="kpi-ico"/>}
      <span>{label}</span>
    </div>
    <div className="kpi-value">
      {value}{unit && <span className="unit">{unit}</span>}
    </div>
    {delta != null && (
      <div className="kpi-delta" style={{ color: delta >= 0 ? "var(--pos)" : "var(--neg)" }}>
        <Ico name={delta >= 0 ? "trend-up" : "trend-down"} size={11}/>
        {fmt(Math.abs(delta), { decimals: 1 })}%{deltaLabel && <span className="muted" style={{ fontWeight: 400, fontFamily: "var(--font-ui)" }}> · {deltaLabel}</span>}
      </div>
    )}
    {spark && <div className="kpi-spark"><Sparkline data={spark} w={70} h={20} stroke={sparkColor || "var(--accent)"} fill={`${sparkColor || "var(--accent)"}1f`}/></div>}
  </div>
);

/* ============ TIMELINE ROW ============ */
const TimelineRow = ({ time, body, actor }) => (
  <div className="tl-row">
    <div className="tl-time">{time}</div>
    <div className="tl-dot"/>
    <div className="tl-body">
      <div>{body}</div>
      {actor && <div className="tl-actor">{actor}</div>}
    </div>
  </div>
);

/* ============ BANNER ============ */
const Banner = ({ kind = "info", icon = "info", children, action }) => (
  <div className={`banner ${kind}`}>
    <Ico name={icon} size={13} className="b-ico"/>
    <div style={{ flex: 1 }}>{children}</div>
    {action}
  </div>
);

/* ============ STATEMENT ROW ============ */
const StRow = ({ type, label, indent = 0, current, prior }) => {
  const c = type ? `st-row ${type}` : `st-row indent-${indent}`;
  return (
    <div className={c}>
      <div>{label}</div>
      {type === "section" ? null : (
        <>
          <div className="st-num">{current != null ? fmt(current, { decimals: 0, parens: true }) : ""}</div>
          <div className="st-num dim">{prior != null ? fmt(prior, { decimals: 0, parens: true }) : ""}</div>
        </>
      )}
    </div>
  );
};

/* ============ COMMAND PALETTE ============ */
const CommandPalette = ({ open, onClose, onNavigate, routes }) => {
  const [q, setQ] = useState("");
  const [sel, setSel] = useState(0);
  const inputRef = useRef();
  useEffect(() => {
    if (open) { setQ(""); setSel(0); setTimeout(() => inputRef.current?.focus(), 30); }
  }, [open]);
  const filtered = useMemo(() => {
    if (!q) return routes;
    const ql = q.toLowerCase();
    return routes.filter(r => r.label.toLowerCase().includes(ql) || (r.tags || []).some(t => t.toLowerCase().includes(ql)));
  }, [q, routes]);
  useEffect(() => {
    if (!open) return;
    const h = (e) => {
      if (e.key === "ArrowDown") { e.preventDefault(); setSel((s) => Math.min(filtered.length - 1, s + 1)); }
      else if (e.key === "ArrowUp") { e.preventDefault(); setSel((s) => Math.max(0, s - 1)); }
      else if (e.key === "Enter") { e.preventDefault(); const r = filtered[sel]; if (r) { onNavigate(r.route); onClose(); } }
      else if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", h);
    return () => window.removeEventListener("keydown", h);
  }, [open, filtered, sel, onClose, onNavigate]);
  if (!open) return null;
  // group by group field
  const groups = {};
  filtered.forEach((r, i) => {
    const g = r.group || "Navigate";
    (groups[g] = groups[g] || []).push({ ...r, _idx: i });
  });
  return (
    <div className="scrim" onClick={(e) => e.target.classList.contains("scrim") && onClose()}>
      <div className="cmdk" onClick={(e) => e.stopPropagation()}>
        <input ref={inputRef} className="cmdk-input" value={q} onChange={(e) => { setQ(e.target.value); setSel(0); }} placeholder="Search screens, transactions, customers…"/>
        <div className="cmdk-list">
          {Object.entries(groups).map(([g, items]) => (
            <div key={g}>
              <div className="cmdk-group-label">{g}</div>
              {items.map(r => (
                <div key={r.route + r.label} className={`cmdk-item ${r._idx === sel ? "active" : ""}`}
                     onMouseEnter={() => setSel(r._idx)}
                     onClick={() => { onNavigate(r.route); onClose(); }}>
                  <Ico name={r.icon || "doc"} size={13} className="cmdk-ico"/>
                  <span>{r.label}</span>
                  {r.meta && <span className="cmdk-meta">{r.meta}</span>}
                </div>
              ))}
            </div>
          ))}
          {!filtered.length && <div className="empty">No matches</div>}
        </div>
        <div className="cmdk-foot">
          <span className="kbd">↑↓</span> navigate
          <span className="kbd">↵</span> open
          <span className="kbd">esc</span> close
          <span style={{ marginLeft: "auto" }}>QeSuite Command Center</span>
        </div>
      </div>
    </div>
  );
};

/* ============ T-ACCOUNT ============ */
const TAccount = ({ accountCode, accountName, drLines, crLines }) => {
  const drTotal = drLines.reduce((s, l) => s + l.amount, 0);
  const crTotal = crLines.reduce((s, l) => s + l.amount, 0);
  return (
    <div className="t-acct">
      <div className="t-acct-head">{accountCode} · {accountName}</div>
      <div className="t-side dr">
        <div className="t-side-label">Debit</div>
        {drLines.map((l, i) => (
          <div key={i} className="t-line">
            <div className="t-date">{l.date}</div>
            <div className="ellipsis">{l.ref}</div>
            <div className="t-amt">{fmt(l.amount, { decimals: 2 })}</div>
          </div>
        ))}
        <div className="t-line" style={{ borderTop: "2px solid var(--fg)", marginTop: 6, paddingTop: 6, fontWeight: 700 }}>
          <div></div>
          <div>Total Dr</div>
          <div className="t-amt">{fmt(drTotal, { decimals: 2 })}</div>
        </div>
      </div>
      <div className="t-side">
        <div className="t-side-label">Credit</div>
        {crLines.map((l, i) => (
          <div key={i} className="t-line">
            <div className="t-date">{l.date}</div>
            <div className="ellipsis">{l.ref}</div>
            <div className="t-amt">{fmt(l.amount, { decimals: 2 })}</div>
          </div>
        ))}
        <div className="t-line" style={{ borderTop: "2px solid var(--fg)", marginTop: 6, paddingTop: 6, fontWeight: 700 }}>
          <div></div>
          <div>Total Cr</div>
          <div className="t-amt">{fmt(crTotal, { decimals: 2 })}</div>
        </div>
      </div>
    </div>
  );
};

/* ============ PAGE HEADER ============ */
const PageHeader = ({ title, meta, actions, tabs, activeTab, onTab }) => (
  <div className="subtopbar">
    <div>
      <div className="page-title">{title}</div>
      {meta && <div className="page-meta">{meta}</div>}
    </div>
    {tabs && (
      <div className="tabs" style={{ marginLeft: 16 }}>
        {tabs.map(t => (
          <div key={t.id} className={`tab ${activeTab === t.id ? "active" : ""}`} onClick={() => onTab(t.id)}>
            {t.label}
            {t.count != null && <span className="badge-mini">{t.count}</span>}
          </div>
        ))}
      </div>
    )}
    <div className="spacer"/>
    {actions}
  </div>
);

/* ============ STATUS DOT ============ */
const StatusDot = ({ color = "var(--accent)", glow = true }) => (
  <span className="dot" style={{ background: color, boxShadow: glow ? `0 0 0 3px color-mix(in oklab, ${color} 18%, transparent)` : "none" }}/>
);

/* ============ Pagination ============ */
const Pagination = ({ page = 1, pages = 1, total, onPage }) => (
  <div className="pagination">
    <span>Showing <span className="mono">{total}</span> results</span>
    <div className="pg-spacer"/>
    <Button size="sm" icon="chevron" variant="ghost" onClick={() => onPage && onPage(Math.max(1, page - 1))}><span className="sr-only">Prev</span></Button>
    <span className="mono">{page} / {pages}</span>
    <Button size="sm" variant="ghost" onClick={() => onPage && onPage(Math.min(pages, page + 1))}>Next</Button>
  </div>
);

/* ============ TableFooter — bottom-of-table pagination row.
   Stateful but visual-only: changing page/size doesn't slice the dataset,
   it just exposes the controls (the tables aren't long enough to need real slicing).
   Drop in after a list <table>. ============ */
const TableFooter = ({ total = 0, label = "rows", defaultSize = 25, sizes = [10, 25, 50, 100] }) => {
  const [pageSize, setPageSize] = useState(defaultSize);
  const [page, setPage] = useState(1);
  const pages = Math.max(1, Math.ceil(total / pageSize));
  const start = total === 0 ? 0 : (page - 1) * pageSize + 1;
  const end = Math.min(total, page * pageSize);
  const safePages = Math.max(1, pages);
  const atFirst = page <= 1;
  const atLast = page >= safePages;

  return (
    <div className="tbl-footer">
      <span className="tbl-footer-info">
        Showing <span className="mono">{start}–{end}</span> of <span className="mono">{total.toLocaleString()}</span> {label}
      </span>
      <div className="pg-spacer"/>
      <div className="tbl-footer-size">
        <span>Rows</span>
        <select className="tbl-footer-select" value={pageSize} onChange={e => { setPageSize(+e.target.value); setPage(1); }}>
          {sizes.map(s => <option key={s} value={s}>{s}</option>)}
        </select>
      </div>
      <div className="tbl-footer-pages">
        <button className="tbl-pg-btn" disabled={atFirst} onClick={() => setPage(1)} title="First page" aria-label="First page">
          <Ico name="chev-right" size={10} style={{ transform: "rotate(180deg)" }}/>
          <Ico name="chev-right" size={10} style={{ transform: "rotate(180deg)", marginLeft: -7 }}/>
        </button>
        <button className="tbl-pg-btn" disabled={atFirst} onClick={() => setPage(p => Math.max(1, p - 1))} title="Previous" aria-label="Previous">
          <Ico name="chev-right" size={11} style={{ transform: "rotate(180deg)" }}/>
        </button>
        <span className="tbl-pg-meta"><span className="mono">{page}</span> <span className="muted">/ {safePages}</span></span>
        <button className="tbl-pg-btn" disabled={atLast} onClick={() => setPage(p => Math.min(safePages, p + 1))} title="Next" aria-label="Next">
          <Ico name="chev-right" size={11}/>
        </button>
        <button className="tbl-pg-btn" disabled={atLast} onClick={() => setPage(safePages)} title="Last page" aria-label="Last page">
          <Ico name="chev-right" size={10}/>
          <Ico name="chev-right" size={10} style={{ marginLeft: -7 }}/>
        </button>
      </div>
    </div>
  );
};

/* ============ TableToolbar ============ */
const TableToolbar = ({ search, onSearch, children }) => (
  <div className="table-toolbar">
    {onSearch !== undefined && <SearchField value={search} onChange={onSearch}/>}
    {children}
  </div>
);

/* ============ EXPORT ============ */
Object.assign(window, {
  fmt, fmtDate, fmtDateTime,
  Ico, Badge, Button, IconBtn,
  Sparkline, BarChart, LineChart, Donut,
  Drawer, Modal, SearchField, ChipFilter, Segmented, Toggle, Kpi, TimelineRow, Banner,
  StRow, CommandPalette, TAccount, PageHeader, StatusDot, Pagination, TableToolbar, TableFooter,
});
