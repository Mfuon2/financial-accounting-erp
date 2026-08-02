<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuth } from '@/composables/useAuth.js'
import { useToast } from '@/composables/useToast.js'
import { isDemo } from '@/composables/useAppMode.js'
import Button from '@/components/primitives/Button.vue'
import Ico from '@/components/primitives/Ico.vue'

const router = useRouter()
const route  = useRoute()
const { login } = useAuth()
const { toast } = useToast()

const email      = ref('')
const password   = ref('')
const submitting = ref(false)
const showPass   = ref(false)
const error      = ref(null)

async function onSubmit() {
  error.value = null
  if (!email.value.trim()) { error.value = 'Email is required.';    return }
  if (!password.value)     { error.value = 'Password is required.'; return }
  submitting.value = true
  try {
    if (isDemo.value) { await router.push(route.query.next || '/dashboard'); return }
    await login(email.value.trim(), password.value)
    toast.success('Welcome back!')
    await router.push(route.query.next || '/dashboard')
  } catch (e) {
    error.value = e?.message ?? 'Sign in failed — check your credentials and try again.'
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
        <!-- Background SVG Wallpaper (3D Glassmorphic Balanced Ledger) -->
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
          <circle cx="210" cy="360" r="160" fill="var(--accent)" opacity="0.35" filter="url(#blur-glow)" />
          <circle cx="290" cy="450" r="180" fill="oklch(0.78 0.16 95)" opacity="0.25" filter="url(#blur-glow)" />
          <circle cx="100" cy="620" r="120" fill="var(--accent-soft)" opacity="0.2" filter="url(#blur-glow)" />
        </svg>

        <!-- Brand -->
        <div class="auth-brand">
          <div class="brand-mark"><span>Q</span></div>
          <span class="brand-name" style="font-size: 16px;">QeSuite</span>
          <span class="brand-suffix">IFRS · ENTERPRISE</span>
        </div>

        <!-- Hero Headline -->
        <div>
          <h1 class="auth-headline">The accounting system <em>auditors</em> don't argue with.</h1>
          <div class="auth-sub">A full IFRS-compliant general ledger, revenue cycle, and period-end engine. Built for accountants, finance officers, auditors, and ops teams who close on time, every time.</div>
        </div>

        <!-- Feature Stats -->
        <div class="auth-stats">
          <div><div class="v">9-step</div><div class="l">Accounting cycle automated</div></div>
          <div><div class="v">IAS 1·7·8·21·15</div><div class="l">Standards in the engine</div></div>
          <div><div class="v">0.00</div><div class="l">Unbalanced periods, ever</div></div>
        </div>
      </div>

      <!-- ── Right form panel ── -->
      <div class="auth-form-pane">
        <form class="auth-form" @submit.prevent="onSubmit" novalidate>

          <h3>Welcome back</h3>
          <div class="auth-sub">Sign in to access your organization's accounting workspace.</div>

          <div v-if="error" class="fp-error" role="alert">
            <Ico name="warn" :size="14" style="flex-shrink:0"/>
            <span>{{ error }}</span>
          </div>

          <div class="field">
            <label for="l-email">Email or username</label>
            <input id="l-email" v-model="email" class="input" type="email"
              autocomplete="username" placeholder="you@company.com"
              :disabled="submitting" autofocus/>
          </div>

          <div class="field">
            <div class="fl-row">
              <label for="l-pass" style="margin-bottom: 0;">Password</label>
              <a class="fp-link sm" @click="router.push('/forgot-password')">Forgot password?</a>
            </div>
            <div class="pw-wrap">
              <input id="l-pass" v-model="password" class="input"
                :type="showPass ? 'text' : 'password'"
                autocomplete="current-password" placeholder="••••••••"
                :disabled="submitting"/>
              <button type="button" class="eye-btn" tabindex="-1" @click="showPass = !showPass">
                <Ico name="eye" :size="15"/>
              </button>
            </div>
          </div>

          <Button variant="primary" size="lg" type="submit" :disabled="submitting" class="fp-submit">
            <template v-if="submitting">
              <span class="btn-spinner" />
              <span>Signing in…</span>
            </template>
            <template v-else>
              <span>Sign in</span>
              <Ico name="chev-right" :size="12" />
            </template>
          </Button>

          <div class="divider"/>

          <div class="muted" style="font-size: 11.5px; text-align: center;">
            New here? <a @click="router.push('/signup')" style="color: var(--accent); cursor: pointer; font-weight: 600;">Create an organization</a>
          </div>
          <div class="muted" style="font-size: 10.5px; text-align: center; margin-top: 8px;">JWT · MFA enforced · SOC2-aligned</div>

        </form>
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

.fl-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 2px;
}

.fp-link {
  color: var(--accent);
  cursor: pointer;
  font-weight: 600;
  font-size: 13px;
}

.fp-link.sm {
  font-size: 12px;
  font-weight: 500;
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

.fp-foot {
  font-size: 12.5px;
  text-align: center;
  color: var(--muted);
  margin: 0;
}
</style>
