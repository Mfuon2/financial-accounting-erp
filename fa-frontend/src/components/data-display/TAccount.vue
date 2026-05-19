<template>
  <div class="t-acct">
    <!-- Account title bar — top of the T -->
    <div class="t-acct-head">{{ accountCode }} — {{ accountName }}</div>

    <!-- Horizontal rule — the cross-bar of the T -->
    <div class="t-bar" />

    <!-- Two sides split by a vertical hairline — the stem of the T -->
    <div class="t-body">
      <!-- DR side -->
      <div class="t-side">
        <div class="t-side-label">DR</div>
        <div v-for="(line, i) in drLines" :key="i" class="t-line">
          <span class="t-date">{{ line.date }}</span>
          <span class="t-ref">{{ line.ref }}</span>
          <span class="t-amount">{{ fmt(line.amount) }}</span>
        </div>
        <div v-if="!drLines.length" class="t-empty">—</div>
        <div class="t-line t-total">
          <span></span>
          <span class="t-ref">Total</span>
          <span class="t-amount">{{ fmt(drTotal) }}</span>
        </div>
      </div>

      <!-- Vertical divider — the stem of the T -->
      <div class="t-divider" />

      <!-- CR side -->
      <div class="t-side">
        <div class="t-side-label">CR</div>
        <div v-for="(line, i) in crLines" :key="i" class="t-line">
          <span class="t-date">{{ line.date }}</span>
          <span class="t-ref">{{ line.ref }}</span>
          <span class="t-amount">{{ fmt(line.amount) }}</span>
        </div>
        <div v-if="!crLines.length" class="t-empty">—</div>
        <div class="t-line t-total">
          <span></span>
          <span class="t-ref">Total</span>
          <span class="t-amount">{{ fmt(crTotal) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { fmt } from '@/utils/format.js'

const props = defineProps({
  accountCode: { type: String },
  accountName: { type: String },
  drLines: { type: Array, default: () => [] },
  crLines: { type: Array, default: () => [] },
})

const drTotal = computed(() => props.drLines.reduce((s, l) => s + (l.amount || 0), 0))
const crTotal = computed(() => props.crLines.reduce((s, l) => s + (l.amount || 0), 0))
</script>

<style scoped>
.t-acct {
  font-family: var(--font-mono, 'Roboto Mono', monospace);
  font-size: 12.5px;
  border: 1px solid var(--border);
  border-radius: 6px;
  overflow: hidden;
}

.t-acct-head {
  text-align: center;
  padding: 10px 16px;
  font-weight: 700;
  font-size: 13px;
  letter-spacing: 0.02em;
  background: var(--surface-2, var(--surface));
  color: var(--fg, var(--text));
}

/* Horizontal hairline — the top bar of the T */
.t-bar {
  height: 1px;
  background: var(--border);
}

/* Two-column layout */
.t-body {
  display: flex;
  min-height: 120px;
}

.t-side {
  flex: 1;
  min-width: 0;
  padding: 10px 14px 14px;
}

/* Vertical hairline — the stem of the T */
.t-divider {
  width: 1px;
  background: var(--border);
  flex-shrink: 0;
}

.t-side-label {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--fg-3, var(--text-muted));
  text-transform: uppercase;
  margin-bottom: 8px;
  padding-bottom: 4px;
  border-bottom: 1px dashed var(--border);
}

.t-line {
  display: grid;
  grid-template-columns: 88px 1fr auto;
  gap: 6px;
  padding: 3px 0;
  font-variant-numeric: tabular-nums;
  color: var(--fg, var(--text));
}

.t-total {
  border-top: 1px solid var(--border);
  margin-top: 6px;
  padding-top: 6px;
  font-weight: 700;
}

.t-empty {
  color: var(--fg-3, var(--text-muted));
  font-style: italic;
  padding: 4px 0;
  font-size: 11px;
}

.t-date   { color: var(--fg-3, var(--text-muted)); white-space: nowrap; }
.t-ref    { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.t-amount { text-align: right; font-weight: 500; }
</style>
