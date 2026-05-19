/**
 * API client — central fetch wrapper.
 *
 * Token handling:
 *   - Access token read from memory (useAuth) — never from storage.
 *   - On 401: attempts one silent refresh, retries the request.
 *     If refresh also fails, user is cleared and redirected to /login.
 *   - Concurrent 401s share one refresh promise (queue pattern).
 *
 * Response unwrapping:
 *   - All successful responses from ApiResponse<T> are unwrapped to .data.
 *   - Raw 204 returns null.
 */

import { useLoading }        from '@/composables/useLoading.js'
import { useToast }          from '@/composables/useToast.js'
import { useAuth }           from '@/composables/useAuth.js'

const BASE = import.meta.env.VITE_API_BASE_URL ?? ''

// ── 401 refresh queue ─────────────────────────────────────────────────────────
let _refreshing  = false
let _waitQueue   = []  // { resolve, reject }[]

function _flushQueue(err) {
  _waitQueue.forEach(cb => err ? cb.reject(err) : cb.resolve())
  _waitQueue = []
}

// ── Core fetch wrapper ────────────────────────────────────────────────────────
export async function apiFetch(path, options = {}, _retry = false, _silent = false) {
  const { start, stop } = useLoading()
  const { toast }       = useToast()
  const { getAccessToken, silentRefresh, _clear } = useAuth()

  const token = getAccessToken()
  start()

  try {
    const res = await fetch(`${BASE}${path}`, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...options.headers,
      },
    })

    // ── 401: attempt silent refresh, then retry once ──────────────────────
    if (res.status === 401 && !_retry) {
      if (_refreshing) {
        // Another request is already refreshing — wait for it
        await new Promise((resolve, reject) => _waitQueue.push({ resolve, reject }))
        return apiFetch(path, options, true)
      }

      _refreshing = true
      try {
        await silentRefresh()
        _refreshing = false
        _flushQueue(null)
        return apiFetch(path, options, true)   // retry with new token
      } catch (refreshErr) {
        _refreshing = false
        _flushQueue(refreshErr)
        _clear()
        // Redirect to login — supports hash history
        window.location.replace(window.location.origin + window.location.pathname + '#/login')
        throw new Error('session_expired')
      }
    }

    // ── 401 on retry (token freshly obtained but still rejected) ──────────
    if (res.status === 401 && _retry) {
      _clear()
      window.location.replace(window.location.origin + window.location.pathname + '#/login')
      throw new Error('session_expired')
    }

    if (!res.ok) {
      let detail = `${res.status}`
      try {
        const body = await res.json()
        detail = body?.errors?.[0]?.message ?? body?.message ?? detail
      } catch {}
      if (!_silent) toast.error(`Request failed: ${detail}`)
      throw new Error(`API ${res.status}: ${path}`)
    }

    if (res.status === 204) return null

    // ── Unwrap ApiResponse<T> envelope ───────────────────────────────────
    const json = await res.json()
    return json?.data !== undefined ? json.data : json

  } catch (err) {
    if (err.message === 'session_expired') throw err
    if (!err.message?.startsWith('API ')) {
      toast.error('Network error — check your connection')
    }
    throw err
  } finally {
    stop()
  }
}

export const get   = (path)        => apiFetch(path)
export const post  = (path, body)  => apiFetch(path, { method: 'POST',   body: JSON.stringify(body) })
export const put   = (path, body)  => apiFetch(path, { method: 'PUT',    body: JSON.stringify(body) })
export const patch = (path, body)  => apiFetch(path, { method: 'PATCH',  body: JSON.stringify(body) })
export const del   = (path)        => apiFetch(path, { method: 'DELETE' })

/** Like get() but returns null on error instead of toasting — use when 404 is expected. */
export async function silentGet(path) {
  try { return await apiFetch(path, {}, false, true) } catch { return null }
}

// ── Multipart file upload ─────────────────────────────────────────────────────
export async function uploadFile(path, formData) {
  const { start, stop } = useLoading()
  const { toast }       = useToast()
  const { getAccessToken } = useAuth()
  const token = getAccessToken()
  start()
  try {
    const res = await fetch(`${BASE}${path}`, {
      method:  'POST',
      body:    formData,
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!res.ok) {
      let detail = `${res.status}`
      try { const b = await res.json(); detail = b?.errors?.[0]?.message ?? b?.message ?? detail } catch {}
      toast.error(`Upload failed: ${detail}`)
      throw new Error(`Upload ${res.status}: ${path}`)
    }
    if (res.status === 204) return null
    const json = await res.json()
    return json?.data !== undefined ? json.data : json
  } catch (err) {
    if (!err.message?.startsWith('Upload ')) toast.error('Network error — upload failed')
    throw err
  } finally {
    stop()
  }
}

// ── Binary download ───────────────────────────────────────────────────────────
export async function downloadFile(path, filename) {
  const { start, stop } = useLoading()
  const { toast }       = useToast()
  const { getAccessToken } = useAuth()
  const token = getAccessToken()
  start()
  try {
    const res = await fetch(`${BASE}${path}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
    if (!res.ok) {
      toast.error(`Download failed: ${res.status}`)
      throw new Error('Download failed')
    }
    const blob = await res.blob()
    const url  = URL.createObjectURL(blob)
    const a    = document.createElement('a')
    a.href = url; a.download = filename
    document.body.appendChild(a); a.click(); document.body.removeChild(a)
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (err) {
    if (!err.message?.startsWith('Download')) toast.error('Network error — download failed')
    throw err
  } finally {
    stop()
  }
}
