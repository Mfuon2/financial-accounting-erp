<script setup>
import { isLoading } from '@/composables/useLoading.js'
</script>

<template>
  <Teleport to="body">
    <Transition name="loader-fade">
      <div v-if="isLoading" class="gl-root">

        <!-- Top progress bar -->
        <div class="gl-bar">
          <div class="gl-bar-fill" />
        </div>

        <!-- Backdrop -->
        <div class="gl-backdrop" />

        <!-- Card -->
        <div class="gl-stage">
          <div class="gl-card">

            <!-- Spinner ring + brand mark -->
            <div class="gl-ring-wrap">
              <div class="gl-ring" />
              <div class="gl-mark">QE</div>
            </div>

            <!-- Label -->
            <div class="gl-label">
              <p class="gl-brand">QeSuite</p>
              <p class="gl-sub">Please wait…</p>
            </div>

            <!-- Dot pulse -->
            <div class="gl-dots">
              <span class="gl-dot" />
              <span class="gl-dot" style="animation-delay:0.18s" />
              <span class="gl-dot" style="animation-delay:0.36s" />
            </div>

          </div>
        </div>

      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
/* ── Root overlay ──────────────────────────── */
.gl-root {
  position: fixed;
  inset: 0;
  z-index: 9999;
  pointer-events: all;
}

/* ── Top progress bar ──────────────────────── */
.gl-bar {
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 2px;
  overflow: hidden;
}
.gl-bar-fill {
  height: 100%;
  width: 100%;
  background: linear-gradient(90deg, var(--accent), color-mix(in oklab, var(--accent) 60%, white), var(--accent));
  background-size: 200% 100%;
  animation: gl-sweep 1.4s ease-in-out infinite;
}
@keyframes gl-sweep {
  0%   { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* ── Backdrop ──────────────────────────────── */
.gl-backdrop {
  position: absolute;
  inset: 0;
  background: oklch(0.15 0.01 260 / 0.08);
  backdrop-filter: blur(2px);
}

/* ── Card stage ────────────────────────────── */
.gl-stage {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.gl-card {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 20px;
  box-shadow: 0 24px 48px oklch(0.15 0.01 260 / 0.12), 0 4px 12px oklch(0.15 0.01 260 / 0.06);
  padding: 28px 32px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  min-width: 160px;
}

/* ── Spinner ring + mark ───────────────────── */
.gl-ring-wrap {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
}
.gl-ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 2.5px solid var(--border-strong);
  border-top-color: var(--accent);
  border-right-color: color-mix(in oklab, var(--accent) 60%, white);
  animation: gl-spin 0.8s cubic-bezier(0.4, 0, 0.2, 1) infinite;
}
@keyframes gl-spin {
  to { transform: rotate(360deg); }
}
.gl-mark {
  position: relative;
  z-index: 1;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: var(--accent);
  color: #fff;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.02em;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ── Label ─────────────────────────────────── */
.gl-label { text-align: center; }
.gl-brand {
  font-size: 12px;
  font-weight: 700;
  color: var(--text, #1a1a1a);
  letter-spacing: -0.01em;
  margin: 0;
}
.gl-sub {
  font-size: 9px;
  font-weight: 600;
  color: var(--muted);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  margin: 3px 0 0;
}

/* ── Dot pulse ─────────────────────────────── */
.gl-dots {
  display: flex;
  align-items: center;
  gap: 6px;
}
.gl-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--accent);
  animation: gl-pulse 1.1s ease-in-out infinite;
}
@keyframes gl-pulse {
  0%, 80%, 100% { transform: scale(0.55); opacity: 0.35; }
  40%           { transform: scale(1);    opacity: 1;    }
}

/* ── Transition ────────────────────────────── */
.loader-fade-enter-active { transition: opacity 0.15s ease; }
.loader-fade-leave-active { transition: opacity 0.25s ease; }
.loader-fade-enter-from,
.loader-fade-leave-to    { opacity: 0; }
</style>
