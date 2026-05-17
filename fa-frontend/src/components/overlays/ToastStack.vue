<script setup>
import { useToast } from '@/composables/useToast.js'
const { toasts, remove } = useToast()

const ICONS = {
  success: '✓',
  error:   '✕',
  warn:    '⚠',
  info:    'ℹ',
}
</script>

<template>
  <Teleport to="body">
    <div class="toast-stack">
      <TransitionGroup name="toast">
        <div
          v-for="t in toasts"
          :key="t.id"
          :class="['toast', `toast--${t.type}`]"
        >
          <span class="toast-icon">{{ ICONS[t.type] }}</span>
          <span class="toast-msg">{{ t.message }}</span>
          <button class="toast-close" @click="remove(t.id)">✕</button>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>

<style scoped>
.toast-stack {
  position: fixed;
  bottom: 24px;
  right: 24px;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: 8px;
  pointer-events: none;
}

.toast {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 13px;
  min-width: 260px;
  max-width: 420px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.12), 0 1px 4px rgba(0,0,0,0.08);
  pointer-events: all;
  backdrop-filter: blur(4px);
  border: 1px solid transparent;
}
.toast--success {
  background: color-mix(in oklab, oklch(0.55 0.18 145) 12%, var(--surface));
  border-color: color-mix(in oklab, oklch(0.55 0.18 145) 30%, transparent);
  color: oklch(0.38 0.14 145);
}
.toast--error {
  background: color-mix(in oklab, oklch(0.55 0.18 15) 12%, var(--surface));
  border-color: color-mix(in oklab, oklch(0.55 0.18 15) 30%, transparent);
  color: oklch(0.40 0.16 15);
}
.toast--warn {
  background: color-mix(in oklab, oklch(0.75 0.18 55) 14%, var(--surface));
  border-color: color-mix(in oklab, oklch(0.75 0.18 55) 30%, transparent);
  color: oklch(0.50 0.16 55);
}
.toast--info {
  background: color-mix(in oklab, var(--accent) 10%, var(--surface));
  border-color: color-mix(in oklab, var(--accent) 25%, transparent);
  color: var(--accent);
}
.toast-icon {
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
  width: 16px;
  text-align: center;
}
.toast-msg {
  flex: 1;
  line-height: 1.4;
}
.toast-close {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 11px;
  opacity: 0.5;
  padding: 0 2px;
  color: inherit;
  flex-shrink: 0;
  line-height: 1;
}
.toast-close:hover { opacity: 1 }

/* Transitions */
.toast-enter-active  { transition: all 0.25s cubic-bezier(0.34, 1.56, 0.64, 1) }
.toast-leave-active  { transition: all 0.2s ease }
.toast-enter-from    { opacity: 0; transform: translateX(40px) scale(0.92) }
.toast-leave-to      { opacity: 0; transform: translateX(40px) scale(0.92) }
.toast-move          { transition: transform 0.2s ease }
</style>
