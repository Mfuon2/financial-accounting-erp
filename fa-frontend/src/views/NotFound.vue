<script setup>
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <div style="display:flex;flex-direction:column;align-items:center;justify-content:center;height:60vh;gap:16px;text-align:center;color:var(--fg-2)">
    <div style="font-size:48px;font-weight:700;color:var(--border)">404</div>
    <div style="font-size:18px;font-weight:600">Page not found</div>
    <div style="font-size:13px;color:var(--text-muted)">The page you're looking for doesn't exist or has moved.</div>
    <button
      style="margin-top:8px;padding:8px 20px;border-radius:6px;background:var(--accent);color:white;border:none;cursor:pointer;font-size:13px;font-weight:500"
      @click="router.push('/dashboard')"
    >
      Go to Dashboard
    </button>
  </div>
</template>
