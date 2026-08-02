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
  <div class="auth-screen" data-theme="light">
    <div class="auth-card">

      <!-- ── Left panel: Corporate Auth Poster with Wallpaper ── -->
      <div class="auth-poster">
        <!-- Background SVG Wallpaper (3D Glassmorphic Secure Shield Orbit) -->
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
          <circle cx="240" cy="400" r="220" fill="var(--accent-soft)" opacity="0.3" filter="url(#blur-glow)" />
          <circle cx="360" cy="600" r="160" fill="oklch(0.78 0.16 95)" opacity="0.25" filter="url(#blur-glow)" />
        </svg>

        <!-- Brand -->
        <div class="auth-brand">
          <div class="brand-mark"><span>Q</span></div>
          <span class="brand-name" style="font-size: 16px;">QeSuite</span>
          <span class="brand-suffix">IFRS · SECURITY</span>
        </div>

        <!-- Hero Headline -->
        <div>
          <h1 class="auth-headline">Secure and audit-ready recovery.</h1>
          <div class="auth-sub">Recover access to your organization's financial book of record. All security actions, password resets, and sessions are logged in our immutable audit trail.</div>
        </div>

        <!-- Feature Stats -->
        <div class="auth-stats">
          <div><div class="v">RBAC</div><div class="l">Role-based access control</div></div>
          <div><div class="v">Audit log</div><div class="l">Every action tracked</div></div>
          <div><div class="v">Secure</div><div class="l">SOC2-aligned platform</div></div>
        </div>
      </div>

      <!-- ── Right form panel ── -->
      <div class="auth-form-pane">
        <!-- Request form -->
        <form v-if="!sent" class="auth-form" @submit.prevent="onSubmit" novalidate>

          <h3>Reset password</h3>
          <div class="auth-sub">Enter your registered email and we'll send a reset link if an account is found.</div>

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
            <template v-if="loading">
              <span class="btn-spinner" />
              <span>Sending…</span>
            </template>
            <template v-else>
              <span>Send reset link</span>
              <Ico name="envelope" :size="12" />
            </template>
          </Button>

          <div class="divider"/>

          <p class="fp-foot" style="font-size: 11.5px; text-align: center; color: var(--muted); margin: 0;">
            Remembered your password? <a class="fp-link" @click="router.push('/login')" style="color: var(--accent); cursor: pointer; font-weight: 600;">Sign in</a>
          </p>

        </form>

        <!-- Confirmation state -->
        <div v-else class="auth-form" style="text-align:center;align-items:center">
          <div class="sent-icon">
            <Ico name="paper-plane" :size="22"/>
          </div>
          <h3>Reset link sent</h3>
          <p class="auth-sub" style="max-width:300px;text-align:center; margin-top: 8px;">
            If an account exists for <strong>{{ email }}</strong>, we have sent instructions to reset your password.
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
</style>
