<script setup>
import { ref, computed, onMounted } from 'vue'
import { useAuth }    from '@/composables/useAuth.js'
import { useAppMode } from '@/composables/useAppMode.js'
import { useActivePeriod } from '@/composables/useActivePeriod.js'
import { get } from '@/api/client.js'
import PageHeader from '@/components/PageHeader.vue'
import Badge from '@/components/primitives/Badge.vue'
import Ico from '@/components/primitives/Ico.vue'

const { currentUser } = useAuth()
const { isDemo }      = useAppMode()
const { activePeriod } = useActivePeriod()

const entityId = computed(() => currentUser.value?.entityId ?? null)
const periodLabel = computed(() => activePeriod.value?.code ?? '—')

const loading = ref(false)
const checks  = ref([])
const passed  = computed(() => checks.value.filter(c => c.status === 'PASS').length)

async function load() {
  if (!entityId.value || isDemo.value) return
  loading.value = true
  try {
    const res = await get(`/api/v1/compliance/ias1?entityId=${entityId.value}`)
    checks.value = res?.checks ?? []
  } catch {
    checks.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <PageHeader
      title="IAS 1 Compliance Check"
      :meta="checks.length ? `${passed} of ${checks.length} checks pass · for period ${periodLabel}` : `for period ${periodLabel}`"
    />

    <div class="page-section stack">
      <div class="card">
        <div class="card-head">
          <Ico name="shield" :size="13" />
          Disclosure &amp; presentation requirements
          <div v-if="checks.length" class="h-meta">{{ passed }}/{{ checks.length }} passed</div>
        </div>

        <div v-if="loading" style="padding:48px;text-align:center;color:var(--text-muted)">Running checks…</div>

        <div v-else-if="isDemo" style="padding:32px;text-align:center;color:var(--text-muted)">
          Compliance checks require a live entity. Switch to Live mode to run checks.
        </div>

        <div v-else-if="!checks.length" style="padding:32px;text-align:center;color:var(--text-muted)">
          No checks returned. Ensure the entity is configured and try again.
        </div>

        <table v-else class="tbl">
          <thead>
            <tr>
              <th style="width:44px"></th>
              <th>Check</th>
              <th>Detail</th>
              <th style="width:120px">Status</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="c in checks" :key="c.id">
              <td>
                <div
                  :style="{
                    width: '28px',
                    height: '28px',
                    borderRadius: '7px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    background: c.status === 'PASS'
                      ? 'color-mix(in oklab, var(--pos) 14%, transparent)'
                      : c.status === 'FAIL'
                        ? 'color-mix(in oklab, var(--neg) 14%, transparent)'
                        : 'color-mix(in oklab, var(--warn) 14%, transparent)',
                    color: c.status === 'PASS' ? 'var(--pos)' : c.status === 'FAIL' ? 'var(--neg)' : 'var(--warn)',
                  }"
                >
                  <Ico :name="c.status === 'PASS' ? 'check' : c.status === 'FAIL' ? 'error' : 'warn'" :size="13" />
                </div>
              </td>
              <td style="font-weight:500;white-space:nowrap">{{ c.name }}</td>
              <td class="muted" style="font-size:11.5px">{{ c.detail }}</td>
              <td>
                <Badge
                  :status="c.status === 'PASS' ? 'approved' : c.status === 'FAIL' ? 'error' : 'pending'"
                  :dot="false"
                >{{ c.status }}</Badge>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
