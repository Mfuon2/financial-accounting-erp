<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import Button from '@/components/primitives/Button.vue'
import Ico from '@/components/primitives/Ico.vue'

const router     = useRouter()
const fullName   = ref('')
const email      = ref('')
const password   = ref('')
const orgName    = ref('')
const showPass   = ref(false)
const submitting = ref(false)
const error      = ref(null)
const done       = ref(false)

async function onSubmit() {
  error.value = null
  if (!fullName.value.trim())    { error.value = 'Full name is required.';           return }
  if (!email.value.trim())       { error.value = 'Email address is required.';       return }
  if (!orgName.value.trim())     { error.value = 'Organisation name is required.';   return }
  if (password.value.length < 8) { error.value = 'Password must be at least 8 characters.'; return }

  submitting.value = true
  try {
    const res = await fetch('/api/v1/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        fullName: fullName.value.trim(),
        email:    email.value.trim(),
        password: password.value,
        orgName:  orgName.value.trim(),
      }),
    })
    if (!res.ok) {
      const body = await res.json().catch(() => ({}))
      throw new Error(body.message || `Registration failed (${res.status})`)
    }
    done.value = true
  } catch (e) {
    error.value = e?.message ?? 'Registration failed — please try again.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="auth-root">

    <!-- ── Left dark panel ──────────────────────────────────────── -->
    <div class="dark-panel">

      <!-- Brand -->
      <div class="dp-brand">
        <div class="dp-mark">Q</div>
        <span class="dp-name">QeSuite</span>
        <span class="dp-badge">IFRS</span>
      </div>

      <!-- Isometric scene: three ascending data towers -->
      <div class="dp-scene">
        <svg viewBox="0 0 480 400" fill="none" xmlns="http://www.w3.org/2000/svg" class="dp-svg">
          <defs>
            <filter id="glow-sm">
              <feGaussianBlur stdDeviation="2.5" result="b"/>
              <feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
            </filter>
            <filter id="glow-lg">
              <feGaussianBlur stdDeviation="5" result="b"/>
              <feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
            </filter>
            <radialGradient id="bg-halo" cx="35%" cy="62%" r="55%">
              <stop offset="0%"   stop-color="#1A2B40" stop-opacity="1"/>
              <stop offset="100%" stop-color="#080C12" stop-opacity="0"/>
            </radialGradient>
          </defs>

          <rect width="480" height="400" fill="url(#bg-halo)"/>

          <!-- ── PLATFORM (6w × 3d × 1h, unit=20, right=(17,10), up=(0,-20)) ── -->
          <!-- FL=(100,280) FR=(202,340) BR=(151,370) BL=(49,310) -->
          <polygon points="100,280 202,340 151,370 49,310"  fill="#0D1620"/>
          <!-- Grid lines -->
          <line x1="83"  y1="290" x2="185" y2="350" stroke="#152030" stroke-width="0.7"/>
          <line x1="66"  y1="300" x2="168" y2="360" stroke="#152030" stroke-width="0.7"/>
          <line x1="134" y1="300" x2="83"  y2="330" stroke="#152030" stroke-width="0.7"/>
          <line x1="168" y1="320" x2="117" y2="350" stroke="#152030" stroke-width="0.7"/>
          <polygon points="100,280 202,340 202,360 100,300"  fill="#09101A"/>
          <polygon points="49,310  100,280 100,300 49,330"   fill="#060C16"/>
          <circle cx="100" cy="280" r="2"   fill="#5BC4E8" opacity="0.6" filter="url(#glow-sm)"/>
          <circle cx="151" cy="310" r="1.5" fill="#5BC4E8" opacity="0.3"/>
          <circle cx="202" cy="340" r="1.5" fill="#5BC4E8" opacity="0.3"/>

          <!-- ── TOWER C — Fiscal Period (right, 2w×2d×3h=60px) ── -->
          <!-- P(4,0)=(168,320) P(6,0)=(202,340) P(6,2)=(168,360) P(4,2)=(134,340) -->
          <polygon points="134,340 168,320 168,260 134,280"  fill="#0F1E30"/>
          <polygon points="168,320 202,340 202,280 168,260"  fill="#172B42"/>
          <polygon points="168,260 202,280 168,300 134,280"  fill="#213B56"/>
          <polyline points="134,280 168,260 202,280"         stroke="#5BC4E8" stroke-width="1.5" opacity="0.60" filter="url(#glow-sm)"/>
          <rect x="172" y="270" width="10" height="6" rx="1.5" fill="#5BC4E8" opacity="0.45"/>
          <rect x="186" y="278" width="10" height="6" rx="1.5" fill="#5BC4E8" opacity="0.45"/>
          <rect x="172" y="290" width="10" height="6" rx="1.5" fill="#3FAF82" opacity="0.35"/>
          <rect x="186" y="298" width="10" height="6" rx="1.5" fill="#3FAF82" opacity="0.35"/>
          <rect x="172" y="310" width="10" height="6" rx="1.5" fill="#5BC4E8" opacity="0.30"/>
          <rect x="186" y="318" width="10" height="6" rx="1.5" fill="#5BC4E8" opacity="0.30"/>

          <!-- ── TOWER A — Organisation (front-left, 2w×2d×5h=100px) ── -->
          <!-- P(0,0)=(100,280) P(2,0)=(134,300) P(2,2)=(100,320) P(0,2)=(66,300) -->
          <polygon points="66,300  100,280 100,180 66,200"   fill="#132535"/>
          <polygon points="100,280 134,300 134,200 100,180"  fill="#1C3348"/>
          <polygon points="100,180 134,200 100,220 66,200"   fill="#264458"/>
          <polyline points="66,200 100,180 134,200"          stroke="#3FAF82" stroke-width="1.5" opacity="0.75" filter="url(#glow-sm)"/>
          <rect x="70"  y="210" width="9"  height="6" rx="1.5" fill="#3FAF82" opacity="0.40"/>
          <rect x="84"  y="206" width="9"  height="6" rx="1.5" fill="#3FAF82" opacity="0.40"/>
          <rect x="70"  y="228" width="9"  height="6" rx="1.5" fill="#5BC4E8" opacity="0.35"/>
          <rect x="84"  y="224" width="9"  height="6" rx="1.5" fill="#5BC4E8" opacity="0.35"/>
          <rect x="70"  y="246" width="9"  height="6" rx="1.5" fill="#3FAF82" opacity="0.30"/>
          <rect x="84"  y="242" width="9"  height="6" rx="1.5" fill="#3FAF82" opacity="0.30"/>
          <rect x="104" y="192" width="11" height="7" rx="1.5" fill="#3FAF82" opacity="0.55"/>
          <rect x="119" y="200" width="11" height="7" rx="1.5" fill="#3FAF82" opacity="0.55"/>
          <rect x="104" y="214" width="11" height="7" rx="1.5" fill="#5BC4E8" opacity="0.45"/>
          <rect x="119" y="222" width="11" height="7" rx="1.5" fill="#5BC4E8" opacity="0.45"/>
          <rect x="104" y="236" width="11" height="7" rx="1.5" fill="#3FAF82" opacity="0.40"/>
          <rect x="119" y="244" width="11" height="7" rx="1.5" fill="#3FAF82" opacity="0.40"/>
          <rect x="104" y="258" width="11" height="7" rx="1.5" fill="#3FAF82" opacity="0.50"/>
          <rect x="119" y="266" width="11" height="7" rx="1.5" fill="#3FAF82" opacity="0.50"/>

          <!-- ── TOWER B — Chart of Accounts (center-back, tallest, 2w×2d×7h=140px) ── -->
          <!-- P(2,1)=(117,310) P(4,1)=(151,330) P(4,3)=(117,350) P(2,3)=(83,330) -->
          <polygon points="83,330  117,310 117,170 83,190"   fill="#152A42"/>
          <polygon points="117,310 151,330 151,190 117,170"  fill="#1E3A58"/>
          <polygon points="117,170 151,190 117,210 83,190"   fill="#2A4E72"/>
          <polygon points="117,170 151,190 117,210 83,190"   fill="#5BC4E8" opacity="0.10"/>
          <polyline points="83,190 117,170 151,190"          stroke="#5BC4E8" stroke-width="2" opacity="0.95" filter="url(#glow-lg)"/>
          <line x1="117" y1="170" x2="117" y2="147"         stroke="#5BC4E8" stroke-width="1.5" opacity="0.80"/>
          <circle cx="117" cy="145" r="3.5" fill="#5BC4E8" opacity="1" filter="url(#glow-lg)"/>
          <rect x="87"  y="200" width="9"  height="6" rx="1.5" fill="#5BC4E8" opacity="0.45"/>
          <rect x="101" y="196" width="9"  height="6" rx="1.5" fill="#5BC4E8" opacity="0.45"/>
          <rect x="87"  y="218" width="9"  height="6" rx="1.5" fill="#3FAF82" opacity="0.35"/>
          <rect x="101" y="214" width="9"  height="6" rx="1.5" fill="#3FAF82" opacity="0.35"/>
          <rect x="87"  y="236" width="9"  height="6" rx="1.5" fill="#5BC4E8" opacity="0.30"/>
          <rect x="101" y="232" width="9"  height="6" rx="1.5" fill="#5BC4E8" opacity="0.30"/>
          <rect x="87"  y="254" width="9"  height="6" rx="1.5" fill="#3FAF82" opacity="0.40"/>
          <rect x="101" y="250" width="9"  height="6" rx="1.5" fill="#3FAF82" opacity="0.40"/>
          <rect x="121" y="182" width="11" height="7" rx="1.5" fill="#5BC4E8" opacity="0.60"/>
          <rect x="136" y="190" width="11" height="7" rx="1.5" fill="#5BC4E8" opacity="0.60"/>
          <rect x="121" y="204" width="11" height="7" rx="1.5" fill="#3FAF82" opacity="0.50"/>
          <rect x="136" y="212" width="11" height="7" rx="1.5" fill="#3FAF82" opacity="0.50"/>
          <rect x="121" y="226" width="11" height="7" rx="1.5" fill="#5BC4E8" opacity="0.40"/>
          <rect x="136" y="234" width="11" height="7" rx="1.5" fill="#5BC4E8" opacity="0.40"/>
          <rect x="121" y="248" width="11" height="7" rx="1.5" fill="#3FAF82" opacity="0.55"/>
          <rect x="136" y="256" width="11" height="7" rx="1.5" fill="#3FAF82" opacity="0.55"/>
          <rect x="121" y="270" width="11" height="7" rx="1.5" fill="#5BC4E8" opacity="0.35"/>
          <rect x="136" y="278" width="11" height="7" rx="1.5" fill="#5BC4E8" opacity="0.35"/>
          <rect x="121" y="292" width="11" height="7" rx="1.5" fill="#3FAF82" opacity="0.45"/>
          <rect x="136" y="300" width="11" height="7" rx="1.5" fill="#3FAF82" opacity="0.45"/>

          <!-- ── Data flow lines ── -->
          <line x1="117" y1="145" x2="242" y2="88"  stroke="#5BC4E8" stroke-width="0.8" stroke-dasharray="4,4" opacity="0.30"/>
          <line x1="100" y1="180" x2="242" y2="168" stroke="#3FAF82" stroke-width="0.8" stroke-dasharray="4,4" opacity="0.24"/>
          <line x1="168" y1="260" x2="242" y2="248" stroke="#5BC4E8" stroke-width="0.8" stroke-dasharray="4,4" opacity="0.20"/>

          <!-- ── Floating dark-glass cards ── -->
          <rect x="242" y="60"  width="218" height="78" rx="10" fill="#0E1825" stroke="#1E3448" stroke-width="1"/>
          <rect x="256" y="76"  width="5"   height="5"  rx="1.5" fill="#5BC4E8" filter="url(#glow-sm)"/>
          <text x="267" y="84"  font-size="9.5" fill="#3E7A9C" font-family="system-ui,sans-serif" font-weight="600" letter-spacing="0.04em">ORGANISATION SETUP</text>
          <text x="256" y="107" font-size="13"  fill="#E8F0F8" font-family="system-ui,sans-serif" font-weight="700">Entity Registration</text>
          <rect x="256" y="114" width="58" height="3.5" rx="2" fill="#5BC4E8" opacity="0.70"/>
          <rect x="319" y="114" width="44" height="3.5" rx="2" fill="#1E3448" opacity="0.90"/>
          <rect x="368" y="114" width="42" height="3.5" rx="2" fill="#1E3448" opacity="0.90"/>
          <rect x="256" y="122" width="76" height="3.5" rx="2" fill="#1E3448" opacity="0.70"/>
          <rect x="337" y="122" width="50" height="3.5" rx="2" fill="#1E3448" opacity="0.70"/>

          <rect x="242" y="154" width="218" height="78" rx="10" fill="#0E1825" stroke="#1E3448" stroke-width="1"/>
          <rect x="256" y="170" width="5"   height="5"  rx="1.5" fill="#3FAF82" filter="url(#glow-sm)"/>
          <text x="267" y="178" font-size="9.5" fill="#4B9B78" font-family="system-ui,sans-serif" font-weight="600" letter-spacing="0.04em">CHART OF ACCOUNTS</text>
          <text x="256" y="201" font-size="13"  fill="#E8F0F8" font-family="system-ui,sans-serif" font-weight="700">General Ledger</text>
          <rect x="256" y="208" width="46" height="3.5" rx="2" fill="#3FAF82" opacity="0.80"/>
          <rect x="307" y="208" width="62" height="3.5" rx="2" fill="#1E3448" opacity="0.90"/>
          <rect x="374" y="208" width="36" height="3.5" rx="2" fill="#1E3448" opacity="0.90"/>
          <rect x="256" y="216" width="92" height="3.5" rx="2" fill="#1E3448" opacity="0.70"/>
          <rect x="353" y="216" width="48" height="3.5" rx="2" fill="#1E3448" opacity="0.70"/>

          <rect x="242" y="248" width="218" height="78" rx="10" fill="#0E1825" stroke="#1E3448" stroke-width="1"/>
          <rect x="256" y="264" width="5"   height="5"  rx="1.5" fill="#A78BFA" filter="url(#glow-sm)"/>
          <text x="267" y="272" font-size="9.5" fill="#6A5AAE" font-family="system-ui,sans-serif" font-weight="600" letter-spacing="0.04em">FISCAL YEAR</text>
          <text x="256" y="295" font-size="13"  fill="#E8F0F8" font-family="system-ui,sans-serif" font-weight="700">Period Management</text>
          <rect x="256" y="302" width="68" height="3.5" rx="2" fill="#A78BFA" opacity="0.65"/>
          <rect x="329" y="302" width="40" height="3.5" rx="2" fill="#1E3448" opacity="0.90"/>
          <rect x="374" y="302" width="46" height="3.5" rx="2" fill="#1E3448" opacity="0.90"/>
          <rect x="256" y="310" width="48" height="3.5" rx="2" fill="#1E3448" opacity="0.70"/>
          <rect x="309" y="310" width="72" height="3.5" rx="2" fill="#1E3448" opacity="0.70"/>
        </svg>
      </div>

      <!-- Bottom feature strip -->
      <div class="dp-features">
        <div class="dp-feat"><span class="dp-dot"></span>IFRS-compliant GL</div>
        <div class="dp-feat"><span class="dp-dot" style="background:#5BC4E8"></span>Multi-entity support</div>
        <div class="dp-feat"><span class="dp-dot" style="background:#A78BFA"></span>Audit-ready reporting</div>
      </div>
    </div>

    <!-- ── Right form panel ──────────────────────────────────────── -->
    <div class="form-panel">

      <!-- Registration form -->
      <form v-if="!done" class="fp-form" @submit.prevent="onSubmit" novalidate>

        <div class="fp-header">
          <div class="fp-logo">
            <div class="brand-mark" style="width:32px;height:32px;font-size:14px"><span>Q</span></div>
          </div>
          <h2 class="fp-title">Create account</h2>
          <p class="fp-sub">Register your organisation and get started.</p>
        </div>

        <div v-if="error" class="fp-error" role="alert">
          <Ico name="warn" :size="14" style="flex-shrink:0"/>
          <span>{{ error }}</span>
        </div>

        <div class="field">
          <label for="s-name">Full name</label>
          <input id="s-name" v-model="fullName" class="input" type="text"
            autocomplete="name" placeholder="Jane Doe"
            :disabled="submitting" autofocus/>
        </div>

        <div class="field">
          <label for="s-org">Organisation name</label>
          <input id="s-org" v-model="orgName" class="input" type="text"
            autocomplete="organization" placeholder="Acme Holdings Ltd."
            :disabled="submitting"/>
        </div>

        <div class="field">
          <label for="s-email">Work email</label>
          <input id="s-email" v-model="email" class="input" type="email"
            autocomplete="username" placeholder="you@company.com"
            :disabled="submitting"/>
        </div>

        <div class="field">
          <label for="s-pass">Password</label>
          <div class="pw-wrap">
            <input id="s-pass" v-model="password" class="input"
              :type="showPass ? 'text' : 'password'"
              autocomplete="new-password" placeholder="Min. 8 characters"
              :disabled="submitting"/>
            <button type="button" class="eye-btn" tabindex="-1" @click="showPass = !showPass">
              <Ico name="eye" :size="15"/>
            </button>
          </div>
        </div>

        <Button variant="primary" size="lg" type="submit" :disabled="submitting" class="fp-submit">
          <span v-if="submitting" class="spin-row"><span class="btn-spinner"/>Creating account…</span>
          <span v-else>Create account</span>
        </Button>

        <p class="fp-foot">
          Already registered? <a class="fp-link" @click="router.push('/login')">Sign in</a>
        </p>

      </form>

      <!-- Confirmation state -->
      <div v-else class="fp-form" style="text-align:center;align-items:center">
        <div class="sent-icon">
          <Ico name="envelope" :size="22"/>
        </div>
        <h2 class="fp-title" style="text-align:center">Check your inbox</h2>
        <p class="fp-sub" style="max-width:300px;text-align:center">
          A verification link has been sent to <strong>{{ email }}</strong>.
          Follow the link to activate your account.
        </p>
        <Button variant="ghost" size="lg" @click="router.push('/login')"
          style="width:100%;justify-content:center;margin-top:8px">
          Back to sign in
        </Button>
      </div>

    </div>
  </div>
</template>

<style scoped>
/* ── Layout ─────────────────────────────────────────────────── */
.auth-root {
  height: 100vh;
  display: grid;
  grid-template-columns: 1fr 420px;
  background: var(--bg);
}

/* ── Dark left panel ─────────────────────────────────────────── */
.dark-panel {
  background: #080C14;
  display: flex;
  flex-direction: column;
  padding: 28px 32px;
  overflow: hidden;
  position: relative;
}
.dp-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  position: relative;
  z-index: 1;
}
.dp-mark {
  width: 30px; height: 30px; border-radius: 8px;
  background: #3FAF82; color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-weight: 800; font-size: 15px;
}
.dp-name  { color: #E8F0F8; font-size: 16px; font-weight: 700; letter-spacing: -0.02em; }
.dp-badge {
  font-size: 9px; font-weight: 700; letter-spacing: 0.1em;
  background: rgba(63,175,130,0.15); color: #3FAF82;
  padding: 2px 7px; border-radius: 100px;
  border: 1px solid rgba(63,175,130,0.25);
}
.dp-scene { flex: 1; display: flex; align-items: center; justify-content: center; min-height: 0; }
.dp-svg   { width: 100%; height: 100%; max-height: 420px; }
.dp-features {
  display: flex; gap: 24px; flex-shrink: 0; position: relative; z-index: 1;
  padding-top: 16px; border-top: 1px solid rgba(255,255,255,0.06);
}
.dp-feat { display: flex; align-items: center; gap: 7px; font-size: 11.5px; color: #4A6070; font-weight: 500; }
.dp-dot  { width: 6px; height: 6px; border-radius: 50%; background: #3FAF82; opacity: 0.7; flex-shrink: 0; }

/* ── Right form panel ────────────────────────────────────────── */
.form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 36px;
  background: var(--bg);
  border-left: 1px solid var(--border);
  overflow-y: auto;
}
.fp-form {
  width: 100%;
  max-width: 340px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.fp-header { display: flex; flex-direction: column; gap: 10px; }
.fp-logo   { margin-bottom: 4px; }
.fp-title  { margin: 0; font-size: 24px; font-weight: 700; letter-spacing: -0.03em; color: var(--fg); }
.fp-sub    { margin: 0; font-size: 13px; color: var(--muted); line-height: 1.5; }
.fp-error  {
  display: flex; align-items: flex-start; gap: 8px;
  padding: 11px 14px; border-radius: 8px; font-size: 13px; line-height: 1.45;
  background: color-mix(in oklab, oklch(0.55 0.18 15) 10%, var(--surface));
  border: 1px solid color-mix(in oklab, oklch(0.55 0.18 15) 28%, transparent);
  color: oklch(0.38 0.16 15);
}
.fp-link { color: var(--accent); cursor: pointer; font-weight: 600; font-size: 13px; }
.fp-link:hover { text-decoration: underline; }
.pw-wrap { position: relative; }
.pw-wrap .input { width: 100%; padding-right: 42px; box-sizing: border-box; }
.eye-btn {
  position: absolute; right: 11px; top: 50%; transform: translateY(-50%);
  background: none; border: none; cursor: pointer; color: var(--muted); padding: 0;
  display: flex; align-items: center;
}
.eye-btn:hover { color: var(--fg); }
.fp-submit { width: 100%; justify-content: center; }
.spin-row  { display: flex; align-items: center; gap: 8px; justify-content: center; }
.btn-spinner {
  width: 14px; height: 14px; border-radius: 50%;
  border: 2px solid rgba(255,255,255,0.3); border-top-color: #fff;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }
.fp-foot { font-size: 12.5px; text-align: center; color: var(--muted); margin: 0; }
.sent-icon {
  width: 56px; height: 56px; border-radius: 50%;
  background: rgba(63,175,130,0.12); color: #3FAF82;
  display: flex; align-items: center; justify-content: center; margin: 0 auto;
  border: 1px solid rgba(63,175,130,0.25);
}
</style>
