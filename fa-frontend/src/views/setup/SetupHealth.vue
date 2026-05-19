<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuth } from '@/composables/useAuth.js'
import { useSetupChecks } from '@/composables/useSetupChecks.js'
import { fmt as fmtDate } from '@/utils/format.js'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import Banner from '@/components/data-display/Banner.vue'
import Badge from '@/components/primitives/Badge.vue'

const router = useRouter()
const { currentUser } = useAuth()
const { checks, loading, lastRun, criticalFails, warningFails, totalIssues, runChecks } = useSetupChecks()

const entityId = computed(() => currentUser.value?.entityId ?? null)

import { computed } from 'vue'

const failing   = computed(() => checks.value.filter(c => c.status === 'FAIL'))
const passing   = computed(() => checks.value.filter(c => c.status === 'PASS'))
const errored   = computed(() => checks.value.filter(c => c.status === 'ERROR'))

const criticals = computed(() => failing.value.filter(c => c.severity === 'critical'))
const warnings  = computed(() => failing.value.filter(c => c.severity === 'warning'))
const infos     = computed(() => failing.value.filter(c => c.severity === 'info'))

function severityStatus(s) {
  return s === 'critical' ? 'error' : s === 'warning' ? 'warn' : 'info'
}

function lastRunLabel() {
  if (!lastRun.value) return 'Not yet run'
  const diff = Math.round((Date.now() - lastRun.value) / 1000)
  if (diff < 60)  return `${diff}s ago`
  if (diff < 3600) return `${Math.round(diff / 60)}m ago`
  return lastRun.value.toLocaleTimeString()
}

onMounted(() => {
  if (entityId.value && !checks.value.length) runChecks(entityId.value)
})
</script>

<template>
  <div class="page">
    <PageHeader
      title="System Health"
      :meta="`Setup checklist · Last checked: ${lastRunLabel()}`"
    >
      <Button variant="ghost" icon="refresh" :loading="loading" @click="runChecks(entityId, { force: true })">
        Re-run checks
      </Button>
    </PageHeader>

    <div class="page-section stack">

      <!-- Summary banner -->
      <Banner v-if="!loading && totalIssues === 0 && checks.length > 0" kind="success" icon="check">
        All setup checks passed. Your entity is correctly configured.
      </Banner>
      <Banner v-else-if="!loading && criticalFails > 0" kind="error" icon="warn">
        <strong>{{ criticalFails }} critical issue{{ criticalFails !== 1 ? 's' : '' }} require immediate attention</strong>
        — core accounting functions are blocked until these are resolved.
      </Banner>
      <Banner v-else-if="!loading && warningFails > 0" kind="warn" icon="warn">
        {{ warningFails }} configuration gap{{ warningFails !== 1 ? 's' : '' }} detected — some features will not work correctly.
      </Banner>

      <!-- Loading -->
      <div v-if="loading" class="card" style="padding:48px;text-align:center;color:var(--text-muted)">
        Running setup checks…
      </div>

      <template v-else-if="checks.length">

        <!-- Critical failures -->
        <div v-if="criticals.length">
          <div class="section-label" style="color:var(--neg);font-weight:700;font-size:11px;letter-spacing:.06em;text-transform:uppercase;margin-bottom:8px">
            Critical — Blocks core functionality
          </div>
          <div class="card">
            <div
              v-for="(c, i) in criticals"
              :key="c.id"
              class="check-row"
              :class="{ 'border-t': i > 0 }"
            >
              <div class="check-icon fail-critical">✕</div>
              <div class="check-body">
                <div class="check-label">{{ c.label }}</div>
                <div class="check-desc">{{ c.description }}</div>
                <div class="check-fix">
                  <span style="font-size:11px;color:var(--text-muted);font-style:italic">Fix: {{ c.fix }}</span>
                </div>
              </div>
              <Button variant="primary" size="sm" @click="router.push(c.route)">Fix →</Button>
            </div>
          </div>
        </div>

        <!-- Warnings -->
        <div v-if="warnings.length" style="margin-top:16px">
          <div class="section-label" style="color:oklch(0.6 0.14 75);font-weight:700;font-size:11px;letter-spacing:.06em;text-transform:uppercase;margin-bottom:8px">
            Warnings — Features partially unavailable
          </div>
          <div class="card">
            <div
              v-for="(c, i) in warnings"
              :key="c.id"
              class="check-row"
              :class="{ 'border-t': i > 0 }"
            >
              <div class="check-icon fail-warn">!</div>
              <div class="check-body">
                <div class="check-label">{{ c.label }}</div>
                <div class="check-desc">{{ c.description }}</div>
                <div class="check-fix">
                  <span style="font-size:11px;color:var(--text-muted);font-style:italic">Fix: {{ c.fix }}</span>
                </div>
              </div>
              <Button variant="ghost" size="sm" @click="router.push(c.route)">Go →</Button>
            </div>
          </div>
        </div>

        <!-- Info -->
        <div v-if="infos.length" style="margin-top:16px">
          <div class="section-label" style="color:var(--accent);font-weight:700;font-size:11px;letter-spacing:.06em;text-transform:uppercase;margin-bottom:8px">
            Recommended — Best practice gaps
          </div>
          <div class="card">
            <div
              v-for="(c, i) in infos"
              :key="c.id"
              class="check-row"
              :class="{ 'border-t': i > 0 }"
            >
              <div class="check-icon fail-info">i</div>
              <div class="check-body">
                <div class="check-label">{{ c.label }}</div>
                <div class="check-desc">{{ c.description }}</div>
              </div>
              <Button variant="ghost" size="sm" @click="router.push(c.route)">Go →</Button>
            </div>
          </div>
        </div>

        <!-- Passed -->
        <div v-if="passing.length" style="margin-top:16px">
          <div class="section-label" style="color:var(--pos);font-weight:700;font-size:11px;letter-spacing:.06em;text-transform:uppercase;margin-bottom:8px">
            Passed
          </div>
          <div class="card">
            <div
              v-for="(c, i) in passing"
              :key="c.id"
              class="check-row"
              :class="{ 'border-t': i > 0 }"
            >
              <div class="check-icon pass">✓</div>
              <div class="check-body">
                <div class="check-label">{{ c.label }}</div>
              </div>
              <Badge status="approved" :dot="false">Pass</Badge>
            </div>
          </div>
        </div>

        <!-- Errored -->
        <div v-if="errored.length" style="margin-top:16px">
          <div class="section-label" style="color:var(--text-muted);font-weight:700;font-size:11px;letter-spacing:.06em;text-transform:uppercase;margin-bottom:8px">
            Could not check
          </div>
          <div class="card">
            <div v-for="(c, i) in errored" :key="c.id" class="check-row" :class="{ 'border-t': i > 0 }">
              <div class="check-icon error">?</div>
              <div class="check-body">
                <div class="check-label">{{ c.label }}</div>
                <div class="check-desc" style="color:var(--text-muted)">Check could not run — network or permission error.</div>
              </div>
            </div>
          </div>
        </div>

      </template>

      <!-- Not run yet -->
      <div v-else-if="!loading" class="card" style="padding:48px;text-align:center;color:var(--text-muted)">
        Click <strong>Re-run checks</strong> to analyse your setup.
      </div>

    </div>
  </div>
</template>

<style scoped>
.check-row {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 16px;
}
.border-t { border-top: 1px solid var(--border); }

.check-icon {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  margin-top: 1px;
}
.fail-critical { background: color-mix(in oklab, var(--neg) 15%, transparent); color: var(--neg); border: 1px solid color-mix(in oklab, var(--neg) 30%, transparent); }
.fail-warn     { background: color-mix(in oklab, oklch(0.7 0.15 75) 15%, transparent); color: oklch(0.55 0.12 75); border: 1px solid color-mix(in oklab, oklch(0.7 0.15 75) 30%, transparent); }
.fail-info     { background: color-mix(in oklab, var(--accent) 12%, transparent); color: var(--accent); border: 1px solid color-mix(in oklab, var(--accent) 25%, transparent); }
.pass          { background: color-mix(in oklab, var(--pos) 12%, transparent); color: var(--pos); border: 1px solid color-mix(in oklab, var(--pos) 25%, transparent); }
.error         { background: color-mix(in oklab, var(--text-muted) 15%, transparent); color: var(--text-muted); border: 1px solid color-mix(in oklab, var(--text-muted) 25%, transparent); }

.check-body    { flex: 1; min-width: 0; }
.check-label   { font-size: 13px; font-weight: 600; margin-bottom: 3px; }
.check-desc    { font-size: 12px; color: var(--text-muted); line-height: 1.5; }
.check-fix     { margin-top: 4px; }
.section-label { padding: 0 2px; }
</style>
