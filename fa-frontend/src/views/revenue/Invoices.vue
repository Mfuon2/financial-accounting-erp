<script setup>
import { ref, computed } from 'vue'
import { INVOICES } from '@/data/index.js'
import { fmt, fmtDate } from '@/utils/format.js'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import IconBtn      from '@/components/primitives/IconBtn.vue'
import AmountInput  from '@/components/primitives/AmountInput.vue'
import Badge from '@/components/primitives/Badge.vue'
import Kpi from '@/components/data-display/Kpi.vue'
import Segmented from '@/components/primitives/Segmented.vue'
import Modal from '@/components/overlays/Modal.vue'
import TableToolbar from '@/components/tables/TableToolbar.vue'
import TableFooter from '@/components/tables/TableFooter.vue'
import SearchableSelect from '@/components/primitives/SearchableSelect.vue'

const tab = ref('ALL')
const search = ref('')
const drawer = ref(null)
const showNew = ref(false)

const counts = computed(() => ({
  ALL: INVOICES.length,
  DRAFT: INVOICES.filter(i => i.status === 'DRAFT').length,
  POSTED: INVOICES.filter(i => i.status === 'POSTED').length,
  PAID: INVOICES.filter(i => i.status === 'PAID').length,
}))

const filtered = computed(() => {
  const q = search.value.toLowerCase()
  return INVOICES.filter(i => {
    const matchTab = tab.value === 'ALL' || i.status === tab.value
    const matchSearch = !q || i.ref.toLowerCase().includes(q) || i.customerName.toLowerCase().includes(q)
    return matchTab && matchSearch
  })
})

const totalUnpaid = computed(() =>
  INVOICES.filter(i => i.status === 'POSTED').reduce((s, i) => s + i.balance, 0)
)

const tabs = computed(() => [
  { id: 'ALL', label: 'All', count: counts.value.ALL },
  { id: 'DRAFT', label: 'Drafts', count: counts.value.DRAFT },
  { id: 'POSTED', label: 'Posted', count: counts.value.POSTED },
  { id: 'PAID', label: 'Paid', count: counts.value.PAID },
])

function ageColor(age) {
  if (age == null) return ''
  if (age < 0) return 'var(--pos)'
  if (age > 30) return 'var(--neg)'
  if (age > 14) return 'var(--warn)'
  return ''
}

const newRecognition = ref('POINT_IN_TIME')
const newLines = ref([{ desc: '', qty: 1, unit: 0, tax: 'VAT-16' }])

const newSubtotal = computed(() => newLines.value.reduce((s, l) => s + Number(l.qty) * Number(l.unit), 0))
const newTax = computed(() => newSubtotal.value * 0.16)
const newTotal = computed(() => newSubtotal.value + newTax.value)

function addNewLine() {
  newLines.value.push({ desc: '', qty: 1, unit: 0, tax: 'VAT-16' })
}
function removeNewLine(i) {
  if (newLines.value.length > 1) newLines.value.splice(i, 1)
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="Invoices"
      :meta="`${INVOICES.length} invoices · KES ${fmt(totalUnpaid)} unpaid`"
      :tabs="tabs"
      :activeTab="tab"
      @tab="tab = $event"
    >
      <Button variant="ghost" icon="download">Export</Button>
      <Button variant="primary" icon="plus" @click="showNew = true">New invoice</Button>
    </PageHeader>

    <div class="page-section stack">
      <TableToolbar v-model:search="search" />

      <div class="card">
        <table class="tbl">
          <thead>
            <tr>
              <th>Invoice</th>
              <th>Customer</th>
              <th>Date</th>
              <th>Due</th>
              <th>Recognition</th>
              <th class="num">Subtotal</th>
              <th class="num">Tax</th>
              <th class="num">Total</th>
              <th class="num">Balance</th>
              <th>Status</th>
              <th>Age</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="inv in filtered" :key="inv.id" class="row-link" @click="drawer = inv">
              <td><code>{{ inv.ref }}</code></td>
              <td>{{ inv.customerName }}</td>
              <td>{{ fmtDate(inv.date) }}</td>
              <td>{{ fmtDate(inv.due) }}</td>
              <td>
                <Badge :status="inv.recognition === 'OVER_TIME' ? 'over-time' : 'point-in-time'" :dot="false">{{ inv.recognition }}</Badge>
                <Badge v-if="inv.discount" status="warn" :dot="false" style="margin-left:4px">{{ inv.discount }}% disc</Badge>
              </td>
              <td class="num mono">{{ fmt(inv.subtotal) }}</td>
              <td class="num mono">{{ fmt(inv.tax) }}</td>
              <td class="num mono">{{ fmt(inv.total) }}</td>
              <td class="num mono">{{ fmt(inv.balance) }}</td>
              <td><Badge :status="inv.status" :dot="false" /></td>
              <td class="mono" :style="{ color: ageColor(inv.aging), fontSize: '12px' }">
                {{ inv.aging != null ? (inv.aging >= 0 ? `+${inv.aging}d` : `${inv.aging}d`) : '—' }}
              </td>
              <td @click.stop>
                <IconBtn icon="dots" @click="drawer = inv" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <TableFooter :total="filtered.length" label="invoices" />
    </div>

    <Modal
      :open="drawer !== null"
      :title="drawer?.ref"
      :subtitle="drawer ? `${drawer.customerName} · ${drawer.currency} ${fmt(drawer.total)}` : ''"
      :width="900"
      @close="drawer = null"
    >
      <template v-if="drawer">
        <div class="kpi-grid" style="grid-template-columns:repeat(4,1fr)">
          <Kpi label="Date" :value="fmtDate(drawer.date)" />
          <Kpi label="Due" :value="fmtDate(drawer.due)" />
          <Kpi label="Balance" :value="fmt(drawer.balance)" />
          <Kpi label="Status" :value="drawer.status" />
        </div>

        <div class="card">
          <div class="card-head">Line Items</div>
          <table class="tbl">
            <thead>
              <tr>
                <th>Description</th>
                <th class="num">Qty</th>
                <th class="num">Unit price</th>
                <th>Tax</th>
                <th class="num">Amount</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(line, i) in drawer.lines" :key="i">
                <td>{{ line.desc }}</td>
                <td class="num">{{ line.qty }}</td>
                <td class="num mono">{{ fmt(line.unit) }}</td>
                <td><Badge status="info" :dot="false">{{ line.tax }}</Badge></td>
                <td class="num mono">{{ fmt(line.qty * line.unit) }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="card">
          <div class="card-head">Posting Preview</div>
          <table class="tbl">
            <thead>
              <tr>
                <th>Account</th>
                <th>Description</th>
                <th class="num">Debit</th>
                <th class="num">Credit</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td><code>1-1200</code> Accounts Receivable</td>
                <td>{{ drawer.customerName }}</td>
                <td class="num mono">{{ fmt(drawer.total) }}</td>
                <td class="num mono">—</td>
              </tr>
              <tr>
                <td><code>4-1000</code> Service Revenue</td>
                <td>Revenue recognition</td>
                <td class="num mono">—</td>
                <td class="num mono">{{ fmt(drawer.subtotal) }}</td>
              </tr>
              <tr>
                <td><code>2-2100</code> VAT Payable</td>
                <td>Output VAT 16%</td>
                <td class="num mono">—</td>
                <td class="num mono">{{ fmt(drawer.tax) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>

      <template #footer>
        <template v-if="drawer?.status === 'DRAFT'">
          <Button variant="primary" icon="approve">Approve &amp; post</Button>
          <Button variant="ghost" icon="settings">Edit</Button>
          <Button variant="ghost" icon="x">Void</Button>
        </template>
        <template v-else-if="drawer?.status === 'POSTED'">
          <Button variant="primary" icon="receipt">Record payment</Button>
          <Button variant="ghost" icon="download">Download PDF</Button>
          <Button variant="ghost" icon="doc">Credit note</Button>
        </template>
        <template v-else>
          <Button variant="ghost" icon="eye">View</Button>
          <Button variant="ghost" icon="download">Download PDF</Button>
        </template>
      </template>
    </Modal>

    <Modal
      :open="showNew"
      title="New Invoice"
      subtitle="Create and post invoice"
      :width="900"
      @close="showNew = false"
    >
      <div class="form-grid cols-3">
        <div class="field">
          <label>Reference</label>
          <input class="input mono" type="text" value="INV-2026-0024" />
        </div>
        <div class="field">
          <label>Customer</label>
          <SearchableSelect
            :options="[
              { value: 'Acme Corp', label: 'Acme Corp' },
              { value: 'Nimbus Logistics', label: 'Nimbus Logistics' },
              { value: 'Karibu Hotels Group', label: 'Karibu Hotels Group' },
              { value: 'Greenvale Agritech', label: 'Greenvale Agritech' },
              { value: 'Olympus Media', label: 'Olympus Media' },
              { value: 'Portside Marine', label: 'Portside Marine' },
            ]"
            placeholder="Select customer"
          />
        </div>
        <div class="field">
          <label>Currency</label>
          <SearchableSelect
            :options="[
              { value: 'KES', label: 'KES — Kenyan Shilling' },
              { value: 'USD', label: 'USD — US Dollar' },
              { value: 'EUR', label: 'EUR — Euro' },
            ]"
            placeholder="Select currency"
          />
        </div>
        <div class="field">
          <label>Invoice date</label>
          <input class="input" type="date" value="2026-02-28" />
        </div>
        <div class="field">
          <label>Due date</label>
          <input class="input" type="date" value="2026-03-30" />
        </div>
        <div class="field" style="grid-column:span 2">
          <label>Recognition</label>
          <Segmented
            v-model="newRecognition"
            :options="[{ value: 'POINT_IN_TIME', label: 'Point in time' }, { value: 'OVER_TIME', label: 'Over time' }]"
          />
        </div>
      </div>

      <div class="card">
        <table class="tbl je-editor">
          <thead>
            <tr>
              <th>Description</th>
              <th class="num">Qty</th>
              <th class="num">Unit price</th>
              <th>Tax</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(line, i) in newLines" :key="i">
              <td><input v-model="line.desc" class="input" placeholder="Description" style="width:100%" /></td>
              <td class="num"><AmountInput v-model="line.qty"  class="input mono" style="text-align:right;width:60px" /></td>
              <td class="num"><AmountInput v-model="line.unit" class="input mono" style="text-align:right;width:100%" /></td>
              <td>
                <SearchableSelect
                  v-model="line.tax"
                  :options="[
                    { value: 'VAT-16', label: 'VAT-16' },
                    { value: 'VAT-0', label: 'VAT-0' },
                    { value: 'VAT-EX', label: 'VAT-EX' },
                  ]"
                  :compact="true"
                />
              </td>
              <td><IconBtn icon="x" @click="removeNewLine(i)" /></td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <td colspan="3" class="num muted">Subtotal</td>
              <td colspan="2" class="num mono">{{ fmt(newSubtotal) }}</td>
            </tr>
            <tr>
              <td colspan="3" class="num muted">VAT 16%</td>
              <td colspan="2" class="num mono">{{ fmt(newTax) }}</td>
            </tr>
            <tr>
              <td colspan="3" class="num"><strong>Total</strong></td>
              <td colspan="2" class="num mono"><strong>{{ fmt(newTotal) }}</strong></td>
            </tr>
          </tfoot>
        </table>
        <Button variant="ghost" icon="plus" style="margin-top:12px;padding-left:0" @click="addNewLine">Add line</Button>
      </div>

      <template #footer>
        <Button variant="primary" icon="approve">Approve &amp; post</Button>
        <Button variant="ghost" icon="doc">Save as draft</Button>
        <Button variant="ghost" @click="showNew = false">Cancel</Button>
      </template>
    </Modal>
  </div>
</template>
