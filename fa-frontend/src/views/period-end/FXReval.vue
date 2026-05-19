<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { FX_RATES, CURRENCIES } from '@/data/index.js'
import { fx, accounts as accountsApi, periods as periodsApi } from '@/api/index.js'
import { useAuth }    from '@/composables/useAuth.js'
import { useAppMode } from '@/composables/useAppMode.js'
import { useToast }   from '@/composables/useToast.js'
import { fmt }        from '@/utils/format.js'
import PageHeader     from '@/components/PageHeader.vue'
import Button         from '@/components/primitives/Button.vue'
import Banner         from '@/components/data-display/Banner.vue'
import Badge          from '@/components/primitives/Badge.vue'
import Modal          from '@/components/overlays/Modal.vue'
import SearchableSelect from '@/components/primitives/SearchableSelect.vue'

const { currentUser } = useAuth()
const { isDemo }      = useAppMode()
const { toast }       = useToast()

const entityId = computed(() => currentUser.value?.entityId ?? null)

// ── Period state ──────────────────────────────────────────────────────────────
const activePeriod    = ref(null)
const revDate         = ref(new Date().toISOString().slice(0, 10))

// ── Currency state ────────────────────────────────────────────────────────────
// rateRows: [{ code, toCurrency, label, rateInput, savedRate, saving, saved }]
const rateRows        = ref([])
const loadingRates    = ref(false)
const functionalCode  = ref('KES')

// ── Revaluation modal ─────────────────────────────────────────────────────────
const showRevModal    = ref(false)
const revPeriodId     = ref(null)
const revAccountId    = ref(null)
const revaluing       = ref(false)
const periodOptions   = ref([])
const accountOptions  = ref([])

// ── Revaluation preview ───────────────────────────────────────────────────────
const previewData    = ref(null)
const previewLoading = ref(false)

async function loadPreview() {
  if (isDemo.value || !entityId.value) return
  previewLoading.value = true
  try {
    const res = await fx.previewRevaluation(entityId.value, revDate.value)
    previewData.value = res
  } catch { previewData.value = null } finally { previewLoading.value = false }
}

const previewItems = computed(() => previewData.value?.items ?? [])
const previewTotal = computed(() => previewData.value ? Number(previewData.value.netDelta) : 0)

// ── Computed: all closing rates confirmed ─────────────────────────────────────
const allRatesSet  = computed(() => rateRows.value.length > 0 && rateRows.value.every(r => r.savedRate != null))
const missingCount = computed(() => rateRows.value.filter(r => r.savedRate == null).length)

// ── Load page data ────────────────────────────────────────────────────────────
async function loadPage() {
  if (isDemo.value) {
    // Demo: populate from static data
    rateRows.value = CURRENCIES
      .filter(c => !(c.functional || c.isFunctional))
      .map(c => {
        const demo = FX_RATES.find(r => r.from === c.code && r.to === 'KES')
        return {
          code:       c.code,
          toCurrency: 'KES',
          label:      `${c.code} / KES`,
          rateInput:  demo?.rate ? String(demo.rate) : '',
          savedRate:  demo?.rate ?? null,
          saving:     false,
          saved:      demo?.rate != null,
        }
      })
    return
  }
  if (!entityId.value) return

  loadingRates.value = true
  try {
    // 1. Get active/adjusting period for the revaluation date
    const periods = await periodsApi.list({ entityId: entityId.value, size: 50 })
    const perList  = periods?.content ?? (Array.isArray(periods) ? periods : [])
    const active   = perList.find(p => ['ADJUSTING', 'OPEN', 'CLOSING'].includes(p.status))
    if (active) {
      activePeriod.value = active
      revDate.value      = active.endDate ?? revDate.value
      revPeriodId.value  = active.id
    }

    // 2. Load registered currencies
    const ccyData = await fx.currencies(entityId.value)
    const ccyList  = Array.isArray(ccyData) ? ccyData : (ccyData?.content ?? [])
    const functional = ccyList.find(c => c.isFunctional)
    if (functional) functionalCode.value = functional.currencyCode

    const foreign = ccyList.filter(c => !c.isFunctional)
    if (!foreign.length) {
      toast.warn('No foreign currencies registered. Add currencies in Setup → Tax & Currency first.')
      return
    }

    // 3. Silently try to load existing CLOSING rates for each pair on the rev date
    const rateResults = await Promise.allSettled(
      foreign.map(c => fx.getRate({
        entityId:     entityId.value,
        fromCurrency: c.currencyCode,
        toCurrency:   functional?.currencyCode ?? 'KES',
        date:         revDate.value,
        rateType:     'CLOSING',
      }))
    )

    rateRows.value = foreign.map((c, i) => {
      const existing = rateResults[i].status === 'fulfilled' ? rateResults[i].value : null
      return {
        code:       c.currencyCode,
        toCurrency: functional?.currencyCode ?? 'KES',
        label:      `${c.currencyCode} / ${functional?.currencyCode ?? 'KES'}`,
        rateInput:  existing != null ? String(existing) : '',
        savedRate:  existing,
        saving:     false,
        saved:      existing != null,
      }
    })
    loadPreview()
  } catch (e) {
    toast.error('Failed to load FX data.')
  } finally {
    loadingRates.value = false
  }
}

// Re-load rates and preview when revaluation date changes
watch(revDate, async () => {
  if (isDemo.value || !entityId.value || !rateRows.value.length) return
  loadingRates.value = true
  const results = await Promise.allSettled(
    rateRows.value.map(r => fx.getRate({
      entityId:     entityId.value,
      fromCurrency: r.code,
      toCurrency:   r.toCurrency,
      date:         revDate.value,
      rateType:     'CLOSING',
    }))
  )
  rateRows.value = rateRows.value.map((r, i) => {
    const existing = results[i].status === 'fulfilled' ? results[i].value : null
    return { ...r, savedRate: existing, rateInput: existing != null ? String(existing) : r.rateInput, saved: existing != null }
  })
  loadingRates.value = false
  loadPreview()
})

// ── Save a single closing rate ────────────────────────────────────────────────
async function saveRate(row) {
  const value = parseFloat(row.rateInput)
  if (!value || value <= 0) { toast.warn(`Enter a valid positive rate for ${row.code}.`); return }

  row.saving = true
  try {
    await fx.createRate({
      entityId:     entityId.value,
      fromCurrency: row.code,
      toCurrency:   row.toCurrency,
      rateDate:     revDate.value,
      rateValue:    value,
      rateType:     'CLOSING',
    })
    row.savedRate = value
    row.saved     = true
    toast.success(`${row.code}/${row.toCurrency} closing rate saved — ${value}`)
    loadPreview()
  } catch { /* toast handled by apiFetch */ } finally {
    row.saving = false
  }
}

// ── Open revaluation modal ────────────────────────────────────────────────────
async function openRevModal() {
  showRevModal.value = true
  if (isDemo.value || !entityId.value) return
  try {
    const accsData = await accountsApi.list({ entityId: entityId.value })
    const accList  = Array.isArray(accsData) ? accsData : (accsData?.content ?? [])
    accountOptions.value = accList
      .filter(a => a.ifrsCategory === 'OTHER_INCOME_EXPENSE' || (a.accountCode ?? '').startsWith('5-9') || (a.accountCode ?? '').startsWith('6-9'))
      .map(a => ({ value: a.id, label: `${a.accountCode} · ${a.accountName}` }))
  } catch { }
}

async function runRevaluation() {
  if (!isDemo.value && !revPeriodId.value)  { toast.warn('Select a period'); return }
  if (!isDemo.value && !revAccountId.value) { toast.warn('Select a gain/loss account'); return }
  revaluing.value = true
  try {
    await fx.revalue({
      entityId:          entityId.value,
      periodId:          revPeriodId.value,
      date:              revDate.value,
      gainLossAccountId: revAccountId.value,
    })
    toast.success('FX revaluation journal entry posted successfully.')
    showRevModal.value = false
  } catch { } finally { revaluing.value = false }
}

onMounted(loadPage)
</script>

<template>
  <div class="page">
    <PageHeader
      title="FX Revaluation (IAS 21)"
      meta="Month-end revaluation · IAS 21 monetary item retranslation"
    >
      <Button variant="ghost" icon="refresh" :loading="loadingRates" @click="loadPage">Refresh</Button>
      <Button
        variant="primary"
        icon="play"
        :disabled="!isDemo && !allRatesSet"
        @click="openRevModal"
      >Post revaluation JE</Button>
    </PageHeader>

    <div class="page-section stack">

      <Banner kind="info" icon="info">
        IAS 21 requires monetary items denominated in foreign currency to be retranslated at the
        <strong>closing rate</strong> at the balance sheet date. Exchange differences are recognised
        in profit or loss (P&L) for the period.
      </Banner>

      <Banner v-if="!isDemo && !allRatesSet && rateRows.length > 0" kind="warn" icon="warn">
        {{ missingCount }} closing rate{{ missingCount !== 1 ? 's' : '' }} not yet set for
        <strong>{{ revDate }}</strong>. Enter and save all rates below before posting the revaluation JE.
      </Banner>

      <Banner v-if="!isDemo && allRatesSet" kind="success" icon="check">
        All closing rates confirmed for <strong>{{ revDate }}</strong>. Ready to post revaluation JE.
      </Banner>

      <!-- Step 1: Closing rates ─────────────────────────────────────────────── -->
      <div class="card" style="margin-top:var(--gap)">
        <div class="card-head" style="display:flex;align-items:center;gap:12px">
          <span style="font-weight:600">Step 1 — Set closing rates</span>
          <div style="margin-left:auto;display:flex;align-items:center;gap:8px">
            <label style="font-size:12px;color:var(--text-muted)">Revaluation date</label>
            <input
              type="date"
              v-model="revDate"
              class="date-input"
              style="font-size:13px;padding:4px 8px;border:1px solid var(--border);border-radius:6px;background:var(--surface);color:var(--text)"
            />
          </div>
        </div>

        <div v-if="loadingRates" style="padding:32px;text-align:center;color:var(--text-muted)">
          Loading currencies…
        </div>
        <div v-else-if="!rateRows.length" style="padding:32px;text-align:center;color:var(--text-muted)">
          No foreign currencies registered.
          <a href="#/setup/tax-currency" style="color:var(--accent)">Add currencies in Setup → Tax &amp; Currency</a>.
        </div>
        <table v-else class="tbl">
          <thead>
            <tr>
              <th>Currency pair</th>
              <th>Rate type</th>
              <th>Date</th>
              <th class="num" style="width:200px">Closing rate (to {{ functionalCode }})</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rateRows" :key="row.code">
              <td><strong>{{ row.label }}</strong></td>
              <td><Badge status="info" :dot="false">CLOSING</Badge></td>
              <td>{{ revDate }}</td>
              <td class="num">
                <input
                  v-model="row.rateInput"
                  type="number"
                  step="0.0001"
                  min="0.0001"
                  :placeholder="`${row.code}/${row.toCurrency} rate`"
                  class="input"
                  style="text-align:right;width:160px;font-variant-numeric:tabular-nums"
                  :style="row.savedRate == null ? 'border-color:var(--warn)' : ''"
                  @keyup.enter="saveRate(row)"
                />
              </td>
              <td>
                <Badge v-if="row.saved" status="approved" :dot="false">Confirmed</Badge>
                <Badge v-else status="warn" :dot="false">Not set</Badge>
              </td>
              <td>
                <Button
                  variant="primary"
                  size="sm"
                  :loading="row.saving"
                  :disabled="isDemo || !row.rateInput"
                  @click="saveRate(row)"
                >
                  {{ row.saved ? 'Update' : 'Save rate' }}
                </Button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Step 2: Revaluation preview ─────────────────────────────────────────── -->
      <div class="card" style="margin-top:var(--gap)">
        <div class="card-head" style="display:flex;align-items:center;gap:8px">
          <span style="font-weight:600">Step 2 — Revaluation preview</span>
          <Badge status="info" :dot="false" style="margin-left:8px">Indicative — based on current ledger balances</Badge>
        </div>
        <div v-if="previewLoading" style="padding:32px;text-align:center;color:var(--text-muted)">Computing preview…</div>
        <div v-else-if="!isDemo && !previewItems.length" style="padding:32px;text-align:center;color:var(--text-muted)">
          No monetary-item accounts with foreign-currency balances found, or no closing rates set yet.
        </div>
        <table v-else class="tbl">
          <thead>
            <tr>
              <th>Code</th>
              <th>Account</th>
              <th>CCY</th>
              <th class="num">Balance (FC)</th>
              <th class="num">Prior rate</th>
              <th class="num">Closing rate</th>
              <th class="num">Prior LC</th>
              <th class="num">New LC</th>
              <th class="num">Delta ({{ functionalCode }})</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, i) in previewItems" :key="i">
              <td><code>{{ item.accountCode }}</code></td>
              <td>{{ item.accountName }}</td>
              <td>{{ item.currencyCode }}</td>
              <td class="num mono">{{ fmt(Number(item.balanceFc)) }}</td>
              <td class="num mono">{{ Number(item.priorRate).toFixed(4) }}</td>
              <td class="num mono">{{ Number(item.closingRate).toFixed(4) }}</td>
              <td class="num mono">{{ fmt(Number(item.priorLc)) }}</td>
              <td class="num mono">{{ fmt(Number(item.newLc)) }}</td>
              <td class="num mono" :style="{ color: Number(item.delta) < 0 ? 'var(--neg)' : 'var(--pos)' }">
                {{ fmt(Number(item.delta), { signed: true }) }}
              </td>
            </tr>
          </tbody>
          <tfoot>
            <tr class="total-row">
              <td colspan="8" class="fw-600">
                Net FX gain / (loss)
                <span style="font-size:11px;font-weight:400;color:var(--text-muted);margin-left:8px">posted to P&L per IAS 21.28</span>
              </td>
              <td class="num mono fw-600" :style="{ color: previewTotal < 0 ? 'var(--neg)' : 'var(--pos)' }">
                {{ fmt(previewTotal, { signed: true }) }}
              </td>
            </tr>
          </tfoot>
        </table>
      </div>

    </div>

    <!-- Post revaluation JE modal ─────────────────────────────────────────────── -->
    <Modal
      :open="showRevModal"
      title="Post FX Revaluation Journal Entry"
      subtitle="IAS 21.28 — retranslate monetary items to closing rate, recognise FX difference in P&L"
      :width="540"
      @close="showRevModal = false"
    >
      <div class="form-grid cols-1" style="gap:16px">

        <div v-if="!isDemo && !allRatesSet" class="info-block warn">
          {{ missingCount }} closing rate{{ missingCount !== 1 ? 's' : '' }} still not saved.
          Save all rates before posting.
        </div>

        <div class="field">
          <label>Revaluation date</label>
          <input
            v-model="revDate"
            type="date"
            class="input"
            style="width:100%"
            readonly
          />
        </div>

        <!-- Rate summary -->
        <div class="field">
          <label>Confirmed closing rates</label>
          <div style="display:flex;flex-direction:column;gap:6px;margin-top:4px">
            <div
              v-for="r in rateRows"
              :key="r.code"
              style="display:flex;justify-content:space-between;font-size:13px;padding:6px 10px;background:var(--surface-2,var(--surface));border:1px solid var(--border);border-radius:6px"
            >
              <span style="font-weight:600">{{ r.label }}</span>
              <span v-if="r.savedRate" class="mono">{{ Number(r.savedRate).toFixed(4) }}</span>
              <Badge v-else status="warn" :dot="false">Not set</Badge>
            </div>
          </div>
        </div>

        <div class="field">
          <label>FX gain / loss account <span style="color:var(--neg)">*</span></label>
          <SearchableSelect
            v-model="revAccountId"
            :options="accountOptions"
            placeholder="Select gain/loss account (5-9xxx or 6-9xxx)…"
          />
          <div style="font-size:11px;color:var(--text-muted);margin-top:4px">
            The net FX difference is posted here per IAS 21.28. Typically an "FX Gain/Loss" P&L account.
          </div>
        </div>

        <Banner kind="info" icon="info" style="font-size:12px">
          This posts a single journal entry debiting/crediting each monetary item account and
          offsetting the gain/loss account. The revaluation is irreversible from this modal —
          use a manual journal entry reversal if correction is needed.
        </Banner>
      </div>

      <template #footer>
        <Button
          variant="primary"
          icon="play"
          :loading="revaluing"
          :disabled="!isDemo && (!allRatesSet || !revAccountId)"
          @click="runRevaluation"
        >
          Post revaluation JE
        </Button>
        <Button variant="ghost" @click="showRevModal = false">Cancel</Button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.info-block {
  padding: 10px 14px;
  font-size: 13px;
  border-radius: 6px;
  border: 1px solid;
}
.info-block.warn {
  background: color-mix(in oklab, oklch(0.7 0.15 75) 12%, var(--surface));
  border-color: color-mix(in oklab, oklch(0.7 0.15 75) 40%, transparent);
  color: oklch(0.45 0.12 75);
}
</style>
