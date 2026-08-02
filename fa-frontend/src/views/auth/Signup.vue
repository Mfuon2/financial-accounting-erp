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
  <div class="auth-screen" data-theme="light">
    <div class="auth-card">

      <!-- ── Left panel: Corporate Auth Poster with Wallpaper ── -->
      <div class="auth-poster">
        <!-- Background SVG Wallpaper (3D Glassmorphic Setup blueprint) -->
        <svg class="poster-bg" viewBox="0 0 480 800" fill="none" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse">
              <path d="M 40 0 L 0 0 0 40" fill="none" stroke="var(--border-2)" stroke-width="0.5" />
            </pattern>
            <filter id="blur-glow" x="-20%" y="-20%" width="140%" height="140%">
              <feGaussianBlur stdDeviation="15" result="blur" />
              <feMerge>
                <feMergeNode in="blur" />
                <feMergeNode in="SourceGraphic" />
              </feMerge>
            </filter>
          </defs>
          
          <!-- Grid overlay -->
          <rect width="100%" height="100%" fill="url(#grid)" opacity="0.3" />
          
          <!-- Ambient Glowing Spheres (frosted by glass overlay) -->
          <circle cx="340" cy="280" r="180" fill="var(--accent-soft)" opacity="0.3" filter="url(#blur-glow)" />
          <circle cx="140" cy="560" r="160" fill="oklch(0.78 0.16 95)" opacity="0.25" filter="url(#blur-glow)" />
        </svg>

        <!-- Brand -->
        <div class="auth-brand">
          <div class="brand-mark"><span>Q</span></div>
          <span class="brand-name" style="font-size: 16px;">QeSuite</span>
          <span class="brand-suffix">IFRS</span>
        </div>

        <!-- Hero Headline -->
        <div>
          <h1 class="auth-headline">Set up your <em>book of record</em> in 3 minutes.</h1>
          <div class="auth-sub">We'll bootstrap your org, seed an IFRS-compliant chart of accounts, generate your first fiscal year, and hand you a clean ledger to start posting.</div>
        </div>

        <!-- Feature Stats -->
        <div class="auth-stats">
          <div><div class="v">IFRS COA</div><div class="l">Templates for services & trading</div></div>
          <div><div class="v">Multi-FX</div><div class="l">USD, EUR, GBP supported</div></div>
          <div><div class="v">RBAC + MFA</div><div class="l">Ready from day one</div></div>
        </div>
      </div>

      <!-- ── Right form panel ── -->
      <div class="auth-form-pane">
        <!-- Registration form -->
        <form v-if="!done" class="auth-form" @submit.prevent="onSubmit" novalidate>

          <h3>Create your organization</h3>
          <div class="auth-sub">Register your organization to set up your accounting ledger.</div>

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
            <template v-if="submitting">
              <span class="btn-spinner" />
              <span>Creating account…</span>
            </template>
            <template v-else>
              <span>Create account</span>
              <Ico name="plus" :size="12" />
            </template>
          </Button>

          <div class="divider"/>

          <p class="fp-foot" style="font-size: 11.5px; text-align: center; color: var(--muted); margin: 0;">
            Already registered? <a class="fp-link" @click="router.push('/login')" style="color: var(--accent); cursor: pointer; font-weight: 600;">Sign in</a>
          </p>

        </form>

        <!-- Confirmation state -->
        <div v-else class="auth-form" style="text-align:center;align-items:center">
          <div class="sent-icon">
            <Ico name="envelope" :size="22"/>
          </div>
          <h3>Check your inbox</h3>
          <p class="auth-sub" style="max-width:300px;text-align:center; margin-top: 8px;">
            A verification link has been sent to <strong>{{ email }}</strong>.
            Follow the link to activate your account.
          </p>
          <Button variant="ghost" size="lg" @click="router.push('/login')"
            style="width:100%;justify-content:center;margin-top:16px; border: 1px solid var(--border);">
            Back to sign in
          </Button>
        </div>

      </div>

    </div>
  </div>
</template>

<style scoped>
.poster-bg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
  pointer-events: none;
}

.fp-error {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 11px 14px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.45;
  background: color-mix(in oklab, oklch(0.55 0.18 15) 10%, var(--surface));
  border: 1px solid color-mix(in oklab, oklch(0.55 0.18 15) 28%, transparent);
  color: oklch(0.38 0.16 15);
}

.fp-link {
  color: var(--accent);
  cursor: pointer;
  font-weight: 600;
  font-size: 13px;
}

.fp-link:hover {
  text-decoration: underline;
}

.pw-wrap {
  position: relative;
}

.pw-wrap .input {
  width: 100%;
  padding-right: 42px;
  box-sizing: border-box;
}

.eye-btn {
  position: absolute;
  right: 11px;
  top: 50%;
  transform: translateY(-50%);
  background: none;
  border: none;
  cursor: pointer;
  color: var(--muted);
  padding: 0;
  display: flex;
  align-items: center;
}

.eye-btn:hover {
  color: var(--fg);
}

.fp-submit {
  width: 100%;
  justify-content: center;
  margin-top: 4px;
}

.spin-row {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: center;
}

.btn-spinner {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.sent-icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: color-mix(in oklab, var(--accent) 12%, var(--surface));
  color: var(--accent);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto;
  border: 1px solid color-mix(in oklab, var(--accent) 25%, transparent);
}

.fp-foot {
  font-size: 12.5px;
  text-align: center;
  color: var(--muted);
  margin: 0;
}
</style>
