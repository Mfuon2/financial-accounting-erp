<script setup>
import { ref, computed, onMounted } from 'vue'
import { bankStatements as bankApi, accounts as accountsApi } from '@/api/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { useAuth } from '@/composables/useAuth.js'
import { useToast } from '@/composables/useToast.js'
import { fmt } from '@/utils/format.js'
import PageHeader   from '@/components/PageHeader.vue'
import Button       from '@/components/primitives/Button.vue'
import IconBtn      from '@/components/primitives/IconBtn.vue'
import Badge        from '@/components/primitives/Badge.vue'
import Kpi          from '@/components/data-display/Kpi.vue'
import Modal        from '@/components/overlays/Modal.vue'
import TableToolbar from '@/components/tables/TableToolbar.vue'
import TableFooter  from '@/components/tables/TableFooter.vue'
import AmountInput      from '@/components/primitives/AmountInput.vue'
import SearchableSelect from '@/components/primitives/SearchableSelect.vue'

const { toast } = useToast()
const { currentUser } = useAuth()
const entityId = computed(() => currentUser.value?.entityId ?? 'current')
const canManage = computed(() => ['ACCOUNTANT', 'SENIOR_ACCOUNTANT', 'CONTROLLER_CFO', 'SYSTEM_ADMIN'].includes(currentUser.value?.role))

// ── State ─────────────────────────────────────────────────────────────────────
const statementList = ref([])
const bankAccountOpts = ref([])
const loading = ref(false)
const search  = ref('')

const filtered = computed(() => statementList.value.filter(s => {
  if (!search.value) return true
  const q = search.value.toLowerCase()
  return s.accountCode.toLowerCase().includes(q) || s.accountName.toLowerCase().includes(q)
}))

const allLines = computed(() => statementList.value.flatMap(s => s.lines))
const matchedCount   = computed(() => allLines.value.filter(l => l.status === 'MATCHED').length)
const unmatchedCount = computed(() => allLines.value.filter(l => l.status === 'UNMATCHED').length)
const ignoredCount   = computed(() => allLines.value.filter(l => l.status === 'IGNORED').length)

// ── Load ──────────────────────────────────────────────────────────────────────
async function load() {
  loading.value = true
  try {
    const [res, accts] = await Promise.all([
      bankApi.list({ entityId: entityId.value }),
      accountsApi.list({ entityId: entityId.value }),
    ])
    statementList.value = Array.isArray(res) ? res : (res?.content ?? [])
    const acctArr = Array.isArray(accts) ? accts : (accts?.content ?? [])
    // Only non-header, Cash & Equivalents accounts are eligible for bank reconciliation —
    // matches BankStatementService.validateBankAccount's exact rule on the backend.
    bankAccountOpts.value = acctArr
      .filter(a => !a.isHeader && a.accountSubtype === 'CASH_AND_EQUIVALENTS')
      .map(a => ({ value: a.id, label: `${a.accountCode} · ${a.accountName}` }))
  } catch {
    toast.error('Failed to load bank statements.')
  } finally {
    loading.value = false
  }
}
onMounted(load)

// ── Import statement ─────────────────────────────────────────────────────────
const showImport = ref(false)
const importSaving = ref(false)
function emptyLine() { return { transactionDate: '', description: '', amount: '', reference: '' } }
const importForm = ref({ accountId: '', statementDate: '', openingBalance: '', closingBalance: '', notes: '', lines: [emptyLine()] })

function openImport() {
  importForm.value = { accountId: '', statementDate: '', openingBalance: '', closingBalance: '', notes: '', lines: [emptyLine()] }
  showImport.value = true
}
function addLine() { importForm.value.lines.push(emptyLine()) }
function removeLine(idx) { importForm.value.lines.splice(idx, 1) }

async function saveImport() {
  const f = importForm.value
  if (!f.accountId) return toast.warn('A bank account is required.')
  if (!f.statementDate) return toast.warn('Statement date is required.')
  if (f.openingBalance === '' || f.closingBalance === '') return toast.warn('Opening and closing balance are required.')
  if (!f.lines.length || f.lines.some(l => !l.transactionDate || !l.description || l.amount === '')) {
    return toast.warn('Every line needs a date, description, and amount.')
  }
  importSaving.value = true
  try {
    await bankApi.import({
      entityId: entityId.value,
      accountId: f.accountId,
      statementDate: f.statementDate,
      openingBalance: f.openingBalance,
      closingBalance: f.closingBalance,
      notes: f.notes.trim() || null,
      lines: f.lines.map(l => ({ ...l, amount: Number(l.amount) })),
    })
    toast.success('Bank statement imported.')
    showImport.value = false
    await load()
  } catch {
    /* handled by client */
  } finally {
    importSaving.value = false
  }
}

// ── Reconciliation ───────────────────────────────────────────────────────────
const reconTarget = ref(null)
const reconReport = ref(null)
const reconLoading = ref(false)

async function openReconciliation(statement) {
  reconTarget.value = statement
  reconLoading.value = true
  try {
    reconReport.value = await bankApi.reconciliation(statement.id)
  } catch {
    toast.error('Failed to load reconciliation report.')
    reconTarget.value = null
  } finally {
    reconLoading.value = false
  }
}

async function refreshRecon() {
  if (!reconTarget.value) return
  reconReport.value = await bankApi.reconciliation(reconTarget.value.id)
  await load()
}

async function autoMatch(line) {
  try {
    await bankApi.autoMatch(line.id)
    toast.success('Auto-matched.')
    await refreshRecon()
  } catch (e) {
    // Production: client.js's apiFetch already toasted the real backend error message
    // (NO_AUTO_MATCH_CANDIDATE / AMBIGUOUS_AUTO_MATCH) — only demo mode needs a manual toast here.
    if (isDemo.value) toast.warn(e?.message ?? 'Could not auto-match — try matching manually.')
  }
}

async function unmatch(line) {
  try {
    await bankApi.unmatch(line.id)
    toast.success('Unmatched.')
    await refreshRecon()
  } catch { /* handled */ }
}

const unignoring = ref(false)
async function unignore(line) {
  try {
    await bankApi.unignore(line.id)
    toast.success('Line restored to UNMATCHED.')
    await refreshRecon()
  } catch { /* handled */ }
}

// Ignore
const ignoreTarget = ref(null)
const ignoreReason = ref('')
const ignoring = ref(false)
function openIgnore(line) { ignoreTarget.value = line; ignoreReason.value = '' }
async function confirmIgnore() {
  if (!ignoreReason.value.trim()) return toast.warn('A reason is required to ignore a line.')
  ignoring.value = true
  try {
    await bankApi.ignore(ignoreTarget.value.id, ignoreReason.value.trim())
    toast.success('Line ignored.')
    ignoreTarget.value = null
    await refreshRecon()
  } catch { /* handled */ } finally { ignoring.value = false }
}

// Manual match
const matchTarget = ref(null)
const matchCandidates = ref([])
const matchSelected = ref([])
const matchLoading = ref(false)
const matching = ref(false)

async function openMatch(line) {
  matchTarget.value = line
  matchSelected.value = []
  matchLoading.value = true
  try {
    matchCandidates.value = await bankApi.suggestions(line.id)
  } catch {
    matchCandidates.value = []
  } finally {
    matchLoading.value = false
  }
}
function toggleCandidate(id) {
  const i = matchSelected.value.indexOf(id)
  if (i === -1) matchSelected.value.push(id)
  else matchSelected.value.splice(i, 1)
}
async function confirmMatch() {
  if (!matchSelected.value.length) return toast.warn('Select at least one ledger entry.')
  matching.value = true
  try {
    await bankApi.match(matchTarget.value.id, matchSelected.value)
    toast.success('Line matched.')
    matchTarget.value = null
    await refreshRecon()
  } catch {
    /* handled by client — amount-mismatch etc. surfaced via toast already */
  } finally {
    matching.value = false
  }
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="Cash & Bank Management"
      meta="Bank statement import, GL matching, and reconciliation tie-out — Project.md Domain 1 (Financial Operations)"
    >
      <Button v-if="canManage" variant="primary" icon="upload" @click="openImport">Import statement</Button>
    </PageHeader>

    <div class="page-section stack">
      <div class="kpi-grid">
        <Kpi label="Statements imported" icon="bank"  :value="statementList.length" />
        <Kpi label="Matched lines"       icon="check" :value="matchedCount" />
        <Kpi label="Unmatched lines"     icon="clock" :value="unmatchedCount" />
        <Kpi label="Ignored lines"       icon="x"     :value="ignoredCount" />
      </div>

      <TableToolbar v-model:search="search" />

      <div class="card">
        <div v-if="loading" class="empty-state">Loading…</div>
        <table v-else class="tbl">
          <thead>
            <tr>
              <th>Account</th>
              <th>Statement date</th>
              <th class="num">Opening</th>
              <th class="num">Closing</th>
              <th>Lines</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in filtered" :key="s.id">
              <td class="fw-500">{{ s.accountCode }} · {{ s.accountName }}</td>
              <td class="muted small">{{ s.statementDate }}</td>
              <td class="num mono">{{ fmt(s.openingBalance) }}</td>
              <td class="num mono">{{ fmt(s.closingBalance) }}</td>
              <td class="muted small">
                {{ s.lines.length }}
                <span class="muted small">({{ s.lines.filter(l => l.status === 'MATCHED').length }} matched)</span>
              </td>
              <td class="row-actions">
                <Button variant="ghost" size="sm" icon="scale" @click="openReconciliation(s)">Reconciliation</Button>
              </td>
            </tr>
            <tr v-if="!loading && !filtered.length">
              <td colspan="6" class="empty-state">No bank statements imported yet.</td>
            </tr>
          </tbody>
        </table>
        <TableFooter :total="filtered.length" label="statements" />
      </div>
    </div>

    <!-- Import statement -->
    <Modal :open="showImport" title="Import bank statement" :width="760" @close="showImport = false">
      <div class="form-grid cols-2">
        <div class="field">
          <label>Bank account <span class="req">*</span></label>
          <SearchableSelect v-model="importForm.accountId" :options="bankAccountOpts" placeholder="Select a Cash & Equivalents account…" />
        </div>
        <div class="field">
          <label>Statement date <span class="req">*</span></label>
          <input v-model="importForm.statementDate" type="date" class="input" />
        </div>
        <div class="field">
          <label>Opening balance <span class="req">*</span></label>
          <AmountInput class="input mono" v-model="importForm.openingBalance" placeholder="0.00" />
        </div>
        <div class="field">
          <label>Closing balance <span class="req">*</span></label>
          <AmountInput class="input mono" v-model="importForm.closingBalance" placeholder="0.00" />
        </div>
        <div class="field cols-span-2">
          <label>Notes</label>
          <input v-model="importForm.notes" class="input" placeholder="Optional" />
        </div>
      </div>

      <div class="stack" style="margin-top:16px">
        <p class="muted small">
          Amount sign convention: positive = deposit/credit to the bank, negative = withdrawal/debit.
          A real CSV/OFX import pipeline is future work — this is a simple line-by-line entry, same as a JSON import.
        </p>
        <div class="line-header">
          <span>Date</span>
          <span>Description</span>
          <span>Amount</span>
          <span>Reference</span>
          <span></span>
        </div>
        <div v-for="(line, idx) in importForm.lines" :key="idx" class="line-row">
          <input v-model="line.transactionDate" type="date" class="input" />
          <input v-model="line.description" class="input" placeholder="e.g. MPESA transfer" />
          <AmountInput class="input mono" v-model="line.amount" placeholder="0.00" />
          <input v-model="line.reference" class="input" placeholder="Optional" />
          <IconBtn icon="x" :disabled="importForm.lines.length <= 1" @click="removeLine(idx)" />
        </div>
        <Button variant="ghost" icon="plus" @click="addLine">Add line</Button>
      </div>

      <template #footer>
        <Button variant="primary" :loading="importSaving" @click="saveImport">Import</Button>
        <Button variant="ghost" @click="showImport = false">Cancel</Button>
      </template>
    </Modal>

    <!-- Reconciliation -->
    <Modal
      :open="!!reconTarget"
      :title="`Reconciliation — ${reconTarget?.accountCode ?? ''} · ${reconTarget?.statementDate ?? ''}`"
      :width="1040"
      @close="reconTarget = null; reconReport = null"
    >
      <div v-if="reconLoading" class="empty-state">Loading…</div>
      <template v-else-if="reconReport">
        <!-- 2-column, not the shared 4-across .kpi-grid: reconciliation amounts run into the
             millions and clip inside .kpi's overflow:hidden at 4-across width — found during
             live browser verification. -->
        <div class="recon-kpi-grid" style="margin-bottom:16px">
          <Kpi label="GL balance"           icon="ledger" :value="reconReport.glBalance" />
          <Kpi label="Statement balance"    icon="bank"   :value="reconReport.closingBalance" />
          <Kpi label="Adjusted book"        icon="scale"  :value="reconReport.adjustedBookBalance" />
          <Kpi label="Adjusted bank"        icon="scale"  :value="reconReport.adjustedBankBalance" />
        </div>

        <div class="info-box" :class="reconReport.tiesOut ? 'tie-ok' : 'tie-bad'">
          <strong>{{ reconReport.tiesOut ? 'Reconciled — the books and the bank tie out.' : 'Not reconciled yet.' }}</strong>
          <span v-if="!reconReport.tiesOut" class="mono"> Difference: {{ fmt(reconReport.difference) }}</span>
          <span v-if="!reconReport.statementLinesTieToClosingBalance" class="muted small" style="display:block;margin-top:4px">
            Note: the imported lines don't sum to the declared closing balance — check for a missing line.
          </span>
        </div>

        <table class="tbl" style="margin-top:16px">
          <thead>
            <tr>
              <th>Date</th>
              <th>Description</th>
              <th>Reference</th>
              <th class="num">Amount</th>
              <th>Status</th>
              <th>Matched to</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="l in reconReport.lines" :key="l.id">
              <td class="muted small">{{ l.transDate }}</td>
              <td>{{ l.description }}</td>
              <td class="muted small">{{ l.reference ?? '—' }}</td>
              <td class="num mono">{{ fmt(l.amount) }}</td>
              <td><Badge :status="l.status" /></td>
              <td class="muted small">
                <span v-if="l.matches.length">{{ l.matches.map(m => m.ledgerEntryId).join(', ') }}</span>
                <span v-else-if="l.status === 'IGNORED'">{{ l.ignoreReason }}</span>
                <span v-else>—</span>
              </td>
              <td class="row-actions">
                <template v-if="canManage && l.status === 'UNMATCHED'">
                  <Button variant="ghost" size="sm" icon="link" @click="openMatch(l)">Match</Button>
                  <Button variant="ghost" size="sm" icon="refresh" @click="autoMatch(l)">Auto-match</Button>
                  <Button variant="ghost" size="sm" icon="reject" @click="openIgnore(l)">Ignore</Button>
                </template>
                <template v-else-if="canManage && l.status === 'MATCHED'">
                  <Button variant="ghost" size="sm" icon="x" @click="unmatch(l)">Unmatch</Button>
                </template>
                <template v-else-if="canManage && l.status === 'IGNORED'">
                  <Button variant="ghost" size="sm" icon="rotate" @click="unignore(l)">Un-ignore</Button>
                </template>
              </td>
            </tr>
          </tbody>
        </table>
        <p class="muted small" style="margin-top:12px">
          adjustedBookBalance = glBalance + outstanding bank lines. adjustedBankBalance = statement
          closing balance + outstanding GL entries. The two must match for the account to be
          considered reconciled for this statement — no adjusting entry is posted automatically.
        </p>
      </template>
      <template #footer>
        <Button variant="ghost" @click="reconTarget = null; reconReport = null">Close</Button>
      </template>
    </Modal>

    <!-- Manual match picker -->
    <Modal :open="!!matchTarget" title="Match to ledger entries" :width="560" @close="matchTarget = null">
      <p class="muted small" style="margin-bottom:12px">
        Candidates are ledger entries on the same account with the exact same signed amount, dated
        within a few days of the bank line. Select one or more entries that together sum to the
        line amount.
      </p>
      <div v-if="matchLoading" class="empty-state">Loading…</div>
      <div v-else-if="!matchCandidates.length" class="empty-state">
        No candidates found for this line — it may need a manual journal entry first.
      </div>
      <div v-else class="stack">
        <label v-for="c in matchCandidates" :key="c.ledgerEntryId" class="candidate-row">
          <input type="checkbox" :checked="matchSelected.includes(c.ledgerEntryId)" @change="toggleCandidate(c.ledgerEntryId)" />
          <span class="mono small">{{ c.ledgerEntryId }}</span>
          <span class="muted small">{{ c.transDate }}</span>
          <span class="num mono">{{ fmt(c.signedAmount) }}</span>
        </label>
      </div>
      <template #footer>
        <Button variant="primary" :loading="matching" :disabled="!matchCandidates.length" @click="confirmMatch">Match</Button>
        <Button variant="ghost" @click="matchTarget = null">Cancel</Button>
      </template>
    </Modal>

    <!-- Ignore -->
    <Modal :open="!!ignoreTarget" title="Ignore bank line" :width="480" @close="ignoreTarget = null">
      <div class="field">
        <label>Reason <span class="req">*</span></label>
        <input v-model="ignoreReason" class="input" placeholder="e.g. Duplicate bank entry, corrected next statement" />
      </div>
      <template #footer>
        <Button variant="danger" :loading="ignoring" @click="confirmIgnore">Confirm ignore</Button>
        <Button variant="ghost" @click="ignoreTarget = null">Cancel</Button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.req { color: var(--danger, oklch(0.55 0.18 15)); }
.empty-state { text-align: center; padding: 40px; color: var(--muted); font-size: 13px; }
.muted { color: var(--muted); }
.fw-500 { font-weight: 500; }
.row-actions { display: flex; gap: 4px; justify-content: flex-end; flex-wrap: wrap; }
.line-header, .line-row {
  display: grid;
  grid-template-columns: 140px 1fr 120px 120px 32px;
  gap: 8px;
  align-items: center;
}
.line-header { font-size: 11px; text-transform: uppercase; color: var(--muted); letter-spacing: .04em; }
.cols-span-2 { grid-column: span 2; }
.recon-kpi-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
.info-box { padding: 12px 14px; border-radius: 8px; border: 1px solid var(--border); font-size: 13px; }
.info-box.tie-ok  { border-color: oklch(0.6 0.15 145 / 0.4); background: oklch(0.6 0.15 145 / 0.08); }
.info-box.tie-bad { border-color: oklch(0.55 0.18 15 / 0.4); background: oklch(0.55 0.18 15 / 0.08); }
.candidate-row { display: grid; grid-template-columns: 20px 1fr auto auto; gap: 12px; align-items: center; padding: 8px 4px; border-bottom: 1px solid var(--border); }
</style>
