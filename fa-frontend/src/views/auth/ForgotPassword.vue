<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import Button from '@/components/primitives/Button.vue'

const router  = useRouter()
const email   = ref('')
const entityId = ref('')
const sent    = ref(false)
const loading = ref(false)
const error   = ref(null)

async function onSubmit() {
  error.value = null
  if (!email.value.trim() || !entityId.value.trim()) {
    error.value = 'Both fields are required.'
    return
  }
  loading.value = true
  try {
    const res = await fetch('/api/v1/auth/forgot-password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: email.value.trim(), entityId: entityId.value.trim() }),
    })
    const json = await res.json()
    sent.value = true
  } catch {
    error.value = 'Something went wrong. Please try again.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-screen" style="justify-content:center">
    <div class="auth-form-pane" style="max-width:420px;width:100%">
      <form class="auth-form" @submit.prevent="onSubmit" v-if="!sent" novalidate>
        <h3>Reset password</h3>
        <div class="auth-sub">Enter your email and organization ID — we'll send a reset link.</div>
        <div v-if="error" class="auth-error" role="alert" style="background:color-mix(in oklab,oklch(0.55 0.18 15) 10%,var(--surface));border:1px solid color-mix(in oklab,oklch(0.55 0.18 15) 25%,transparent);color:oklch(0.40 0.16 15);padding:10px 14px;border-radius:8px;font-size:13px">{{ error }}</div>
        <div class="field">
          <label>Email address</label>
          <input v-model="email" class="input" type="email" placeholder="you@company.com" :disabled="loading" />
        </div>
        <div class="field">
          <label>Organization ID</label>
          <input v-model="entityId" class="input" type="text" placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" :disabled="loading" />
        </div>
        <Button variant="primary" size="lg" type="submit" :disabled="loading" style="width:100%">
          {{ loading ? 'Sending…' : 'Send reset link' }}
        </Button>
        <div style="text-align:center;margin-top:12px">
          <a style="font-size:13px;color:var(--accent);cursor:pointer" @click="router.push('/login')">← Back to sign in</a>
        </div>
      </form>
      <div class="auth-form" v-else style="text-align:center">
        <div style="font-size:32px;margin-bottom:12px">✉️</div>
        <h3>Check your inbox</h3>
        <div class="auth-sub">If that email exists, a reset link is on its way.</div>
        <Button variant="ghost" size="lg" @click="router.push('/login')" style="width:100%;margin-top:20px">Back to sign in</Button>
      </div>
    </div>
  </div>
</template>
