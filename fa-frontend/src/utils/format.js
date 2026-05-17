export function fmt(n, opts = {}) {
  if (n == null || isNaN(n)) return '—'
  const { currency = null, decimals = 2, signed = false, parens = false, compact = false } = opts
  const abs = Math.abs(n)
  let body
  if (compact && abs >= 1_000_000) body = (n / 1_000_000).toFixed(2).replace(/\.?0+$/, '') + 'M'
  else if (compact && abs >= 1_000) body = (n / 1_000).toFixed(1).replace(/\.?0+$/, '') + 'K'
  else body = abs.toLocaleString('en-US', { minimumFractionDigits: decimals, maximumFractionDigits: decimals })
  if (parens && n < 0) body = '(' + body + ')'
  else if (n < 0) body = '−' + body
  else if (signed && n > 0) body = '+' + body
  if (currency) body = currency + ' ' + body
  return body
}

export function fmtDate(s) {
  if (!s) return '—'
  const d = new Date(s)
  return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })
}

export function fmtDateTime(s) {
  if (!s) return '—'
  const d = new Date(s.replace(' ', 'T'))
  if (isNaN(d)) return s
  return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }) + ' · ' + d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })
}
