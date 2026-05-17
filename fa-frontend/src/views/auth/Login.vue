<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuth } from '@/composables/useAuth.js'
import { useToast } from '@/composables/useToast.js'
import { isDemo } from '@/composables/useAppMode.js'
import Button from '@/components/primitives/Button.vue'
import Ico    from '@/components/primitives/Ico.vue'

const router = useRouter()
const route  = useRoute()
const { login } = useAuth()
const { toast }  = useToast()

const email      = ref(isDemo.value ? 'j.muriuki@apollo.co.ke' : '')
const password   = ref(isDemo.value ? 'demo-password' : '')
const submitting = ref(false)
const showPass   = ref(false)
const error      = ref(null)

async function onSubmit() {
  error.value = null
  if (!email.value.trim()) { error.value = 'Email is required.';    return }
  if (!password.value)     { error.value = 'Password is required.'; return }

  submitting.value = true
  try {
    if (isDemo.value) {
      await router.push(route.query.next || '/dashboard')
      return
    }
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
  <div class="auth-screen">

    <!-- Left poster -->
    <div class="auth-poster">
      <div class="auth-brand">
        <div class="brand-mark"><span>Q</span></div>
        <div class="brand-name" style="font-size:16px">QeSuite</div>
        <div class="brand-suffix">IFRS · ENTERPRISE</div>
      </div>
      <div>
        <h1 class="auth-headline">The accounting system <em>auditors</em> don't argue with.</h1>
        <div class="auth-sub">A full IFRS-compliant general ledger, revenue cycle, and period-end engine for mid-market enterprises.</div>
      </div>
      <div class="auth-stats">
        <div><div class="v">9-step</div><div class="l">Accounting cycle automated</div></div>
        <div><div class="v">IAS 1·7·8·21·15</div><div class="l">Standards in the engine</div></div>
        <div><div class="v">0.00</div><div class="l">Unbalanced periods, ever</div></div>
      </div>
    </div>

    <!-- Right form pane -->
    <div class="auth-form-pane">
      <form class="auth-form" @submit.prevent="onSubmit" novalidate>

        <div class="auth-form-header">
          <h3>Welcome back</h3>
          <p class="auth-sub">Sign in to <strong>Apollo Enterprises Ltd</strong></p>
        </div>

        <!-- Error banner -->
        <div v-if="error" class="auth-error" role="alert">
          <Ico name="warn" :size="14" style="flex-shrink:0" />
          <span>{{ error }}</span>
        </div>

        <!-- Email -->
        <div class="field">
          <label for="login-email">Email address</label>
          <input
            id="login-email"
            v-model="email"
            class="input"
            type="email"
            autocomplete="username"
            placeholder="you@company.com"
            :disabled="submitting"
            autofocus
          />
        </div>

        <!-- Password -->
        <div class="field">
          <div class="field-label-row">
            <label for="login-password">Password</label>
            <a class="auth-forgot" @click="router.push('/forgot-password')">Forgot password?</a>
          </div>
          <div class="input-wrap">
            <input
              id="login-password"
              v-model="password"
              class="input"
              :type="showPass ? 'text' : 'password'"
              autocomplete="current-password"
              placeholder="••••••••"
              :disabled="submitting"
            />
            <button
              type="button"
              class="eye-btn"
              tabindex="-1"
              @click="showPass = !showPass"
              :aria-label="showPass ? 'Hide password' : 'Show password'"
            >
              <Ico :name="showPass ? 'eye-off' : 'eye'" :size="15" />
            </button>
          </div>
        </div>

        <!-- Submit -->
        <Button
          variant="primary"
          size="lg"
          type="submit"
          :disabled="submitting"
          class="auth-submit"
        >
          <span v-if="submitting" class="auth-spinner-row">
            <span class="auth-spinner" />
            Signing in…
          </span>
          <span v-else>Sign in</span>
        </Button>

        <div class="divider" />

        <p class="auth-footer-note">
          New here?
          <a class="auth-link" @click="router.push('/signup')">Create an organization</a>
        </p>
        <p class="auth-security-note">Access token in memory · Session expires on close · SOC2-aligned</p>

      </form>
    </div>
  </div>
</template>

<style scoped>
/* ── Error banner ──────────────────────────── */
.auth-error {
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

/* ── Label + forgot row ────────────────────── */
.field-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.field-label-row label { margin-bottom: 0; }
.auth-forgot {
  font-size: 12px;
  color: var(--accent);
  cursor: pointer;
  font-weight: 500;
  text-decoration: none;
}
.auth-forgot:hover { text-decoration: underline; }

/* ── Password input wrapper ────────────────── */
.input-wrap {
  position: relative;
}
.input-wrap .input {
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
  line-height: 1;
}
.eye-btn:hover { color: var(--text, #1a1a1a); }

/* ── Submit button ─────────────────────────── */
.auth-submit { width: 100%; justify-content: center; }

.auth-spinner-row {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: center;
}
.auth-spinner {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid rgba(255,255,255,0.35);
  border-top-color: #fff;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* ── Footer ────────────────────────────────── */
.auth-form-header { margin-bottom: 4px; }
.auth-footer-note {
  font-size: 12px;
  text-align: center;
  color: var(--muted);
  margin: 0;
}
.auth-link {
  color: var(--accent);
  cursor: pointer;
  font-weight: 600;
}
.auth-link:hover { text-decoration: underline; }
.auth-security-note {
  font-size: 10.5px;
  text-align: center;
  color: var(--muted-2, #8E8F94);
  margin: 6px 0 0;
}
</style>
