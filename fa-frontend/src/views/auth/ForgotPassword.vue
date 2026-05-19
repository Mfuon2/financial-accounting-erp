<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import Button from '@/components/primitives/Button.vue'
import Ico from '@/components/primitives/Ico.vue'

const router  = useRouter()
const email   = ref('')
const sent    = ref(false)
const loading = ref(false)
const error   = ref(null)

async function onSubmit() {
  error.value = null
  if (!email.value.trim()) {
    error.value = 'Email address is required.'
    return
  }
  loading.value = true
  try {
    await fetch('/api/v1/auth/forgot-password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: email.value.trim() }),
    })
    sent.value = true
  } catch {
    error.value = 'Something went wrong. Please try again.'
  } finally {
    loading.value = false
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

      <!-- Isometric scene: secure vault with floating access cards -->
      <div class="dp-scene">
        <svg viewBox="0 0 480 400" fill="none" xmlns="http://www.w3.org/2000/svg" class="dp-svg">
          <defs>
            <filter id="glow-sm">
              <feGaussianBlur stdDeviation="2.5" result="b"/>
              <feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
            </filter>
            <filter id="glow-lg">
              <feGaussianBlur stdDeviation="6" result="b"/>
              <feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
            </filter>
            <radialGradient id="bg-halo" cx="35%" cy="55%" r="52%">
              <stop offset="0%"   stop-color="#1C2235" stop-opacity="1"/>
              <stop offset="100%" stop-color="#080C12" stop-opacity="0"/>
            </radialGradient>
          </defs>

          <rect width="480" height="400" fill="url(#bg-halo)"/>

          <!-- ── PLATFORM (8w × 4d × 1h, unit=20, right=(17,10), up=(0,-20)) ── -->
          <!-- FL=(60,280) FR=(196,360) BR=(128,400)→ clip, use 3d: BR=(128,400)clips -->
          <!-- FL=(70,275) FR=(206,355) BL=(2,315) BR=(138,395)clips -->
          <!-- Try FL=(80,270): FR=(80+8*17,270+8*10)=(216,350) BL=(80-3*17,270+3*10)=(29,300) BR=(216-51,350+30)=(165,380)-->
          <!-- All within viewbox. Good. -->
          <polygon points="80,270 216,350 165,380 29,300"   fill="#0D1620"/>
          <line x1="63"  y1="280" x2="199" y2="360" stroke="#152030" stroke-width="0.7"/>
          <line x1="46"  y1="290" x2="182" y2="370" stroke="#152030" stroke-width="0.7"/>
          <line x1="148" y1="290" x2="97"  y2="320" stroke="#152030" stroke-width="0.7"/>
          <line x1="182" y1="310" x2="131" y2="340" stroke="#152030" stroke-width="0.7"/>
          <polygon points="80,270 216,350 216,370 80,290"   fill="#09101A"/>
          <polygon points="29,300 80,270 80,290 29,320"     fill="#060C16"/>
          <circle cx="80"  cy="270" r="2"   fill="#A78BFA" opacity="0.5" filter="url(#glow-sm)"/>
          <circle cx="148" cy="310" r="1.5" fill="#A78BFA" opacity="0.3"/>
          <circle cx="216" cy="350" r="1.5" fill="#A78BFA" opacity="0.3"/>

          <!-- ── VAULT BODY — wide low structure (6w × 4d × 6h=120px) ── -->
          <!-- Positioned at i=1,j=0 from FL=(80,270) -->
          <!-- P(1,0)=(97,280) P(7,0)=(199,340) P(7,4)=(131,380)clips? -->
          <!-- Use 3d: P(1,0)=(97,280) P(7,0)=(199,340) P(7,3)=(148,370) P(1,3)=(46,310) -->
          <!-- h=120: top at y-120 -->
          <polygon points="46,310  97,280  97,160 46,190"   fill="#141E30"/>
          <polygon points="97,280  199,340 199,220 97,160"  fill="#1C2C44"/>
          <polygon points="97,160  199,220 148,250 46,190"  fill="#273D58"/>
          <!-- Vault roof accent -->
          <polygon points="97,160  199,220 148,250 46,190"  fill="#A78BFA" opacity="0.08"/>
          <polyline points="46,190 97,160 199,220"          stroke="#A78BFA" stroke-width="2" opacity="0.90" filter="url(#glow-lg)"/>

          <!-- Vault door face (centered on front-right face) -->
          <!-- Front-right face spans y=160 to y=280 (height 120) at roughly x=97..199 (right face) -->
          <!-- Vault door circle -->
          <circle cx="148" cy="250" r="38" fill="#0F1A2A" stroke="#A78BFA" stroke-width="1.5" opacity="0.95"/>
          <circle cx="148" cy="250" r="28" fill="none"    stroke="#1E2F48" stroke-width="1"/>
          <circle cx="148" cy="250" r="18" fill="none"    stroke="#A78BFA" stroke-width="1" opacity="0.60"/>
          <!-- Vault spokes -->
          <line x1="148" y1="212" x2="148" y2="288" stroke="#1E2F48" stroke-width="1.5"/>
          <line x1="110" y1="250" x2="186" y2="250" stroke="#1E2F48" stroke-width="1.5"/>
          <line x1="121" y1="223" x2="175" y2="277" stroke="#1E2F48" stroke-width="1"/>
          <line x1="175" y1="223" x2="121" y2="277" stroke="#1E2F48" stroke-width="1"/>
          <!-- Vault centre lock dot -->
          <circle cx="148" cy="250" r="6"  fill="#A78BFA" opacity="0.9" filter="url(#glow-sm)"/>
          <!-- Vault handle -->
          <rect x="160" y="246" width="16" height="8" rx="4" fill="#1E2F48" stroke="#A78BFA" stroke-width="1" opacity="0.8"/>

          <!-- Vault body windows (right face, decorative rows) -->
          <rect x="110" y="170" width="14" height="5" rx="1.5" fill="#A78BFA" opacity="0.25"/>
          <rect x="130" y="178" width="14" height="5" rx="1.5" fill="#A78BFA" opacity="0.25"/>
          <rect x="150" y="186" width="14" height="5" rx="1.5" fill="#3FAF82" opacity="0.20"/>
          <rect x="170" y="194" width="14" height="5" rx="1.5" fill="#3FAF82" opacity="0.20"/>
          <rect x="110" y="200" width="14" height="5" rx="1.5" fill="#A78BFA" opacity="0.20"/>
          <rect x="130" y="208" width="14" height="5" rx="1.5" fill="#A78BFA" opacity="0.20"/>

          <!-- Vault body windows (left face) -->
          <rect x="52"  y="198" width="11" height="5" rx="1.5" fill="#3FAF82" opacity="0.30"/>
          <rect x="68"  y="194" width="11" height="5" rx="1.5" fill="#3FAF82" opacity="0.30"/>
          <rect x="52"  y="216" width="11" height="5" rx="1.5" fill="#A78BFA" opacity="0.25"/>
          <rect x="68"  y="212" width="11" height="5" rx="1.5" fill="#A78BFA" opacity="0.25"/>
          <rect x="52"  y="234" width="11" height="5" rx="1.5" fill="#3FAF82" opacity="0.20"/>
          <rect x="68"  y="230" width="11" height="5" rx="1.5" fill="#3FAF82" opacity="0.20"/>

          <!-- Floating security particles -->
          <circle cx="220" cy="158" r="3"   fill="#A78BFA" opacity="0.35" filter="url(#glow-sm)"/>
          <circle cx="238" cy="144" r="2"   fill="#A78BFA" opacity="0.25"/>
          <circle cx="230" cy="172" r="1.5" fill="#3FAF82" opacity="0.30"/>

          <!-- ── Data flow lines ── -->
          <line x1="199" y1="160" x2="242" y2="88"  stroke="#A78BFA" stroke-width="0.8" stroke-dasharray="4,4" opacity="0.28"/>
          <line x1="199" y1="220" x2="242" y2="168" stroke="#3FAF82" stroke-width="0.8" stroke-dasharray="4,4" opacity="0.22"/>
          <line x1="199" y1="280" x2="242" y2="248" stroke="#A78BFA" stroke-width="0.8" stroke-dasharray="4,4" opacity="0.18"/>

          <!-- ── Floating dark-glass cards ── -->
          <!-- Card 1: Secure Access -->
          <rect x="242" y="60"  width="218" height="78" rx="10" fill="#0E1825" stroke="#1E3448" stroke-width="1"/>
          <rect x="256" y="76"  width="5"   height="5"  rx="1.5" fill="#A78BFA" filter="url(#glow-sm)"/>
          <text x="267" y="84"  font-size="9.5" fill="#6A5AAE" font-family="system-ui,sans-serif" font-weight="600" letter-spacing="0.04em">SECURE ACCESS</text>
          <text x="256" y="107" font-size="13"  fill="#E8F0F8" font-family="system-ui,sans-serif" font-weight="700">Account Recovery</text>
          <rect x="256" y="114" width="60" height="3.5" rx="2" fill="#A78BFA" opacity="0.70"/>
          <rect x="321" y="114" width="40" height="3.5" rx="2" fill="#1E3448" opacity="0.90"/>
          <rect x="366" y="114" width="54" height="3.5" rx="2" fill="#1E3448" opacity="0.90"/>
          <rect x="256" y="122" width="50" height="3.5" rx="2" fill="#1E3448" opacity="0.70"/>
          <rect x="311" y="122" width="74" height="3.5" rx="2" fill="#1E3448" opacity="0.70"/>

          <!-- Card 2: Role-Based Access -->
          <rect x="242" y="154" width="218" height="78" rx="10" fill="#0E1825" stroke="#1E3448" stroke-width="1"/>
          <rect x="256" y="170" width="5"   height="5"  rx="1.5" fill="#3FAF82" filter="url(#glow-sm)"/>
          <text x="267" y="178" font-size="9.5" fill="#4B9B78" font-family="system-ui,sans-serif" font-weight="600" letter-spacing="0.04em">ACCESS CONTROL</text>
          <text x="256" y="201" font-size="13"  fill="#E8F0F8" font-family="system-ui,sans-serif" font-weight="700">Role-Based Permissions</text>
          <rect x="256" y="208" width="72" height="3.5" rx="2" fill="#3FAF82" opacity="0.75"/>
          <rect x="333" y="208" width="46" height="3.5" rx="2" fill="#1E3448" opacity="0.90"/>
          <rect x="384" y="208" width="36" height="3.5" rx="2" fill="#1E3448" opacity="0.90"/>
          <rect x="256" y="216" width="56" height="3.5" rx="2" fill="#1E3448" opacity="0.70"/>
          <rect x="317" y="216" width="68" height="3.5" rx="2" fill="#1E3448" opacity="0.70"/>

          <!-- Card 3: Audit Trail -->
          <rect x="242" y="248" width="218" height="78" rx="10" fill="#0E1825" stroke="#1E3448" stroke-width="1"/>
          <rect x="256" y="264" width="5"   height="5"  rx="1.5" fill="#5BC4E8" filter="url(#glow-sm)"/>
          <text x="267" y="272" font-size="9.5" fill="#3E7A9C" font-family="system-ui,sans-serif" font-weight="600" letter-spacing="0.04em">AUDIT TRAIL</text>
          <text x="256" y="295" font-size="13"  fill="#E8F0F8" font-family="system-ui,sans-serif" font-weight="700">All Actions Logged</text>
          <rect x="256" y="302" width="54" height="3.5" rx="2" fill="#5BC4E8" opacity="0.65"/>
          <rect x="315" y="302" width="44" height="3.5" rx="2" fill="#1E3448" opacity="0.90"/>
          <rect x="364" y="302" width="56" height="3.5" rx="2" fill="#1E3448" opacity="0.90"/>
          <rect x="256" y="310" width="80" height="3.5" rx="2" fill="#1E3448" opacity="0.70"/>
          <rect x="341" y="310" width="50" height="3.5" rx="2" fill="#1E3448" opacity="0.70"/>
        </svg>
      </div>

      <!-- Bottom feature strip -->
      <div class="dp-features">
        <div class="dp-feat"><span class="dp-dot" style="background:#A78BFA"></span>Role-based access</div>
        <div class="dp-feat"><span class="dp-dot"></span>Complete audit trail</div>
        <div class="dp-feat"><span class="dp-dot" style="background:#5BC4E8"></span>Secure by design</div>
      </div>
    </div>

    <!-- ── Right form panel ──────────────────────────────────────── -->
    <div class="form-panel">

      <!-- Request form -->
      <form v-if="!sent" class="fp-form" @submit.prevent="onSubmit" novalidate>

        <div class="fp-header">
          <div class="fp-logo">
            <div class="brand-mark" style="width:32px;height:32px;font-size:14px"><span>Q</span></div>
          </div>
          <h2 class="fp-title">Reset password</h2>
          <p class="fp-sub">Enter your registered email and we'll send a reset link if an account is found.</p>
        </div>

        <div v-if="error" class="fp-error" role="alert">
          <Ico name="warn" :size="14" style="flex-shrink:0"/>
          <span>{{ error }}</span>
        </div>

        <div class="field">
          <label for="fp-email">Email address</label>
          <input
            id="fp-email"
            v-model="email"
            class="input"
            type="email"
            placeholder="you@company.com"
            autocomplete="username"
            :disabled="loading"
            autofocus
          />
        </div>

        <Button variant="primary" size="lg" type="submit" :disabled="loading" class="fp-submit">
          <span v-if="loading" class="spin-row"><span class="btn-spinner"/>Sending…</span>
          <span v-else>Send reset link</span>
        </Button>

        <p class="fp-foot">
          <a class="fp-link" @click="router.push('/login')">← Back to sign in</a>
        </p>

      </form>

      <!-- Sent confirmation -->
      <div v-else class="fp-form" style="text-align:center;align-items:center">
        <div class="sent-icon">
          <Ico name="envelope" :size="22"/>
        </div>
        <h2 class="fp-title" style="text-align:center">Check your inbox</h2>
        <p class="fp-sub" style="max-width:300px;text-align:center">
          If an account exists for <strong>{{ email }}</strong>, a reset link has been sent.
          Check your spam folder if you don't see it within a few minutes.
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
  padding: 40px 36px;
  background: var(--bg);
  border-left: 1px solid var(--border);
}
.fp-form {
  width: 100%;
  max-width: 340px;
  display: flex;
  flex-direction: column;
  gap: 18px;
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
.fp-link { color: var(--accent); cursor: pointer; font-weight: 500; font-size: 13px; }
.fp-link:hover { text-decoration: underline; }
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
