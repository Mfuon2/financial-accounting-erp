/**
 * Auth singleton — the single source of truth for authentication state.
 *
 * Token storage strategy (security rationale):
 *   accessToken  → JS memory (module-level ref) only.
 *                  Never persisted — cleared on page refresh.
 *                  Invisible to XSS that can only read storage APIs.
 *   refreshToken → sessionStorage('qs_rt') only.
 *                  Tab-scoped (not shared across tabs), cleared on browser close.
 *                  NOT in localStorage — per design requirement.
 *   user profile → sessionStorage('qs_u') — display data, not security-sensitive.
 *
 * Refresh token rotation: the backend issues a NEW refresh token on every /refresh call.
 * We always replace our stored RT with the latest one returned.
 */

import { ref, computed } from 'vue'
import { isDemo } from '@/composables/useAppMode.js'

// ── Storage keys ──────────────────────────────────────────────────────────────
const KEY_RT      = 'qs_rt'   // refresh token
const KEY_USER    = 'qs_u'    // user profile JSON
const KEY_SESSION = 'qs_st'   // session start timestamp (ms)

const BASE = import.meta.env.VITE_API_BASE_URL ?? ''

// ── Module-level state (singleton across all component imports) ───────────────
const _accessToken = ref(null)   // memory only — never written to any storage
const _user        = ref(null)
const _ready       = ref(false)  // has init() completed?

let _refreshPromise = null       // shared in-flight refresh (deduplicate concurrent callers)

// ── Public computed ───────────────────────────────────────────────────────────
export const isAuthenticated = computed(() => _accessToken.value !== null || isDemo.value)
export const currentUser     = computed(() => _user.value)
export const authReady       = computed(() => _ready.value)

// ── Internal helpers ──────────────────────────────────────────────────────────
function _setTokens(accessToken, refreshToken, user) {
  _accessToken.value = accessToken
  if (user) {
    _user.value = user
    try { sessionStorage.setItem(KEY_USER, JSON.stringify(user)) } catch {}
  }
  if (refreshToken) {
    try {
      sessionStorage.setItem(KEY_RT, refreshToken)
      sessionStorage.setItem(KEY_SESSION, Date.now().toString())
    } catch {}
  }
}

function _clear() {
  _accessToken.value = null
  _user.value = null
  try {
    sessionStorage.removeItem(KEY_RT)
    sessionStorage.removeItem(KEY_USER)
    sessionStorage.removeItem(KEY_SESSION)
  } catch {}
}

async function _doRefresh(rt) {
  const res = await fetch(`${BASE}/api/v1/auth/refresh`, {
    method:  'POST',
    headers: { 'Content-Type': 'application/json' },
    body:    JSON.stringify({ refreshToken: rt }),
  })
  if (!res.ok) {
    _clear()
    throw new Error('refresh_failed')
  }
  const json = await res.json()
  const data = json.data ?? json
  _setTokens(data.accessToken, data.refreshToken, _user.value)
  return data.accessToken
}

// ── Public API ────────────────────────────────────────────────────────────────

/**
 * Called once in main.js before the app mounts.
 * Attempts a silent refresh if a refresh token exists in sessionStorage.
 * Sets _ready=true when done (whether auth succeeded or not).
 */
async function init() {
  if (isDemo.value) { _ready.value = true; return }
  const rt = sessionStorage.getItem(KEY_RT)
  const userStr = sessionStorage.getItem(KEY_USER)
  if (userStr) {
    try { _user.value = JSON.parse(userStr) } catch {}
  }
  if (rt) {
    try { await _doRefresh(rt) }
    catch { _clear() }
  }
  _ready.value = true
}

/**
 * Sign in — calls /auth/login, stores tokens.
 * Returns the user profile on success.
 */
async function login(email, password) {
  const res = await fetch(`${BASE}/api/v1/auth/login`, {
    method:  'POST',
    headers: { 'Content-Type': 'application/json' },
    body:    JSON.stringify({ email, password }),   // entityId omitted — backend resolves from email
  })
  if (!res.ok) {
    let msg = `Login failed (${res.status})`
    try {
      const body = await res.json()
      msg = body?.errors?.[0]?.message ?? body?.message ?? msg
    } catch {}
    throw new Error(msg)
  }
  const json = await res.json()
  const data = json.data ?? json
  _setTokens(data.accessToken, data.refreshToken, data.user)
  return data.user
}

/**
 * Sign out — revokes tokens on the server, clears local state.
 * Never throws (graceful degradation if server is unreachable).
 */
async function logout() {
  if (!isDemo.value) {
    try {
      await fetch(`${BASE}/api/v1/auth/logout`, {
        method:  'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(getAccessToken() ? { Authorization: `Bearer ${getAccessToken()}` } : {}),
        },
      })
    } catch {}
  }
  _clear()
}

/**
 * Exchange the stored refresh token for a fresh access token.
 * Deduplicated — concurrent callers share the same in-flight promise.
 * Throws if refresh fails (caller should redirect to login).
 */
async function silentRefresh() {
  if (_refreshPromise) return _refreshPromise
  const rt = sessionStorage.getItem(KEY_RT)
  if (!rt) { _clear(); throw new Error('no_refresh_token') }
  _refreshPromise = _doRefresh(rt).finally(() => { _refreshPromise = null })
  return _refreshPromise
}

/** Returns the in-memory access token (null if not authenticated). */
function getAccessToken() {
  return _accessToken.value
}

/** Returns session start time in ms (from sessionStorage). */
function getSessionStart() {
  try { return parseInt(sessionStorage.getItem(KEY_SESSION) ?? '0', 10) } catch { return 0 }
}

function updateUser(patch) {
  if (!_user.value) return
  _user.value = { ..._user.value, ...patch }
  try { sessionStorage.setItem(KEY_USER, JSON.stringify(_user.value)) } catch {}
}

export function useAuth() {
  return {
    isAuthenticated,
    currentUser,
    authReady,
    init,
    login,
    logout,
    silentRefresh,
    getAccessToken,
    getSessionStart,
    updateUser,
    _clear,
  }
}
