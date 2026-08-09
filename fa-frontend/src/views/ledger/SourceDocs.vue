<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { SOURCE_DOCS } from '@/data/index.js'
import { sourceDocs as sourceDocsApi, periods as periodsApi } from '@/api/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { useAuth } from '@/composables/useAuth.js'
import { useToast } from '@/composables/useToast.js'
import { useCategoryCache } from '@/composables/useCategoryCache.js'
import { fmt, fmtDate } from '@/utils/format.js'
import PageHeader   from '@/components/PageHeader.vue'
import Button       from '@/components/primitives/Button.vue'
import IconBtn      from '@/components/primitives/IconBtn.vue'
import Badge        from '@/components/primitives/Badge.vue'
import Ico          from '@/components/primitives/Ico.vue'
import Modal        from '@/components/overlays/Modal.vue'
import TableToolbar from '@/components/tables/TableToolbar.vue'
import TableFooter  from '@/components/tables/TableFooter.vue'
import ChipFilter   from '@/components/primitives/ChipFilter.vue'
import AmountInput       from '@/components/primitives/AmountInput.vue'
import SearchableSelect  from '@/components/primitives/SearchableSelect.vue'

const { toast } = useToast()
const { currentUser } = useAuth()
const entityId = computed(() => currentUser.value?.entityId ?? 'current')

// ── Constants ─────────────────────────────────────────────────────────────────
// Document types are entity-managed dynamic data (CLAUDE.md §2) — see shared/categories on the
// backend and setup/Categories.vue for where they're created/edited. Cached at module level.
const docTypesCache = useCategoryCache('DOCUMENT_TYPE')
const DOC_TYPES = computed(() => docTypesCache.list.value.map(c => c.code))
const STATUSES  = ['ALL','DRAFT','SUBMITTED','REVIEWED','APPROVED','ARCHIVED','VOID']
const WORKFLOW  = ['DRAFT','SUBMITTED','REVIEWED','APPROVED','ARCHIVED']

// ── State ─────────────────────────────────────────────────────────────────────
const docList     = ref([])
const allPeriods  = ref([])
const loading     = ref(false)
const drawer      = ref(null)       // viewed document
const attachments = ref([])
const search      = ref('')
const statusFilter = ref('ALL')

// ── Helpers to normalise demo vs real API field names ─────────────────────────
function norm(d) {
  if (!d) return d
  return {
    ...d,
    _ref:      d.referenceNumber ?? d.ref ?? d.documentRef ?? d.id,
    _type:     d.type            ?? d.documentType ?? '—',
    _date:     d.docDate         ?? d.date ?? d.documentDate,
    _amount:   d.amount,
    _currency: d.currencyCode    ?? d.currency ?? 'KES',
    _desc:     d.description,
    _attCount: d.attachments     ?? 0,
  }
}

// ── Load ──────────────────────────────────────────────────────────────────────
async function load() {
  loading.value = true
  docTypesCache.load(entityId.value)
  if (isDemo.value) {
    docList.value = SOURCE_DOCS.map(norm)
    loading.value = false
    return
  }
  try {
    const [docs, per] = await Promise.all([
      sourceDocsApi.list({ entityId: entityId.value }),
      periodsApi.list({ entityId: entityId.value }),
    ])
    const arr = Array.isArray(docs) ? docs : (docs?.content ?? [])
    docList.value    = arr.map(norm)
    allPeriods.value = Array.isArray(per) ? per : (per?.content ?? [])
  } catch {
    toast.error('Failed to load source documents.')
  } finally { loading.value = false }
}
onMounted(load)

// reload attachments when drawer opens
watch(drawer, async (doc) => {
  attachments.value = []
  if (!doc) return
  try {
    const list = await sourceDocsApi.listAttachments(doc.id)
    attachments.value = Array.isArray(list) ? list : []
  } catch { /* non-critical */ }
})

// keep the list-row file count in sync whenever attachments change
watch(attachments, (list) => {
  if (!drawer.value) return
  const idx = docList.value.findIndex(d => d.id === drawer.value.id)
  if (idx !== -1) docList.value[idx] = { ...docList.value[idx], _attCount: list.length }
})

// ── Filtered list ─────────────────────────────────────────────────────────────
const filtered = computed(() => {
  const q = search.value.toLowerCase()
  return docList.value.filter(d => {
    if (statusFilter.value !== 'ALL' && d.status !== statusFilter.value) return false
    if (!q) return true
    return (d._ref   || '').toLowerCase().includes(q) ||
           (d._type  || '').toLowerCase().includes(q) ||
           (d._desc  || '').toLowerCase().includes(q)
  })
})

const statusIdx = computed(() => drawer.value ? WORKFLOW.indexOf(drawer.value.status) : 0)

// ── Workflow transitions ───────────────────────────────────────────────────────
const transiting = ref(false)
async function transition(id, action) {
  transiting.value = true
  try {
    const updated = await sourceDocsApi[action](id)
    refreshDoc(id, updated)
    toast.success(`Document ${action}d.`)
  } catch { /* handled by client */ } finally { transiting.value = false }
}

function refreshDoc(id, updated) {
  const patched = updated ? norm({ ...docList.value.find(d => d.id === id), ...updated }) : null
  const idx = docList.value.findIndex(d => d.id === id)
  if (idx !== -1 && patched) docList.value[idx] = patched
  if (drawer.value?.id === id && patched) drawer.value = patched
}

// ── Delete document ────────────────────────────────────────────────────────────
const showDelete  = ref(false)
const deleting    = ref(false)
async function confirmDelete() {
  deleting.value = true
  try {
    await sourceDocsApi.delete(drawer.value.id)
    docList.value = docList.value.filter(d => d.id !== drawer.value.id)
    drawer.value = null
    showDelete.value = false
    toast.success('Document deleted.')
  } catch { /* handled */ } finally { deleting.value = false }
}

// ── Attachments ───────────────────────────────────────────────────────────────
const fileInput   = ref(null)
const uploading   = ref(false)
async function uploadFile(e) {
  const file = e.target.files?.[0]
  if (!file || !drawer.value) return
  uploading.value = true
  const fd = new FormData()
  fd.append('file', file)
  try {
    const att = await sourceDocsApi.uploadAttachment(drawer.value.id, fd)
    if (att) attachments.value = [...attachments.value, att]
    toast.success(`${file.name} attached.`)
  } catch { /* handled */ } finally {
    uploading.value = false
    if (fileInput.value) fileInput.value.value = ''
  }
}

const removingAtt = ref(null)
async function removeAttachment(att) {
  removingAtt.value = att.id
  try {
    await sourceDocsApi.removeAttachment(drawer.value.id, att.id)
    attachments.value = attachments.value.filter(a => a.id !== att.id)
    toast.success('Attachment removed.')
  } catch { /* handled */ } finally { removingAtt.value = null }
}

async function downloadAtt(att) {
  try {
    await sourceDocsApi.downloadAttachment(drawer.value.id, att.id, att.fileName)
  } catch { /* handled */ }
}

function fmtSize(bytes) {
  if (!bytes) return ''
  if (bytes < 1024)        return `${bytes} B`
  if (bytes < 1048576)     return `${(bytes/1024).toFixed(1)} KB`
  return `${(bytes/1048576).toFixed(1)} MB`
}

// ── Bulk upload ───────────────────────────────────────────────────────────────
const showBulk     = ref(false)
const bulkFiles    = ref([])
const bulkUploading = ref(false)
const bulkDropEl   = ref(null)

function onBulkDrop(e) {
  e.preventDefault()
  const files = [...(e.dataTransfer?.files ?? [])]
  addBulkFiles(files)
}
function onBulkPick(e) { addBulkFiles([...e.target.files]) }
function addBulkFiles(files) {
  const accepted = files.filter(f =>
    f.type === 'application/pdf' ||
    f.type.startsWith('image/') ||
    f.name.match(/\.(xlsx?|csv|docx?)$/i)
  )
  const newEntries = accepted.map(f => ({
    file:    f,
    name:    f.name,
    size:    f.size,
    docType: 'PURCHASE_INVOICE',
    ref:         '',
    description: '',
    amount:      '',
    currencyCode: 'KES',
    date:    new Date().toISOString().slice(0, 10),
    status:  'pending', // pending | done | error
  }))
  bulkFiles.value = [...bulkFiles.value, ...newEntries]
}
function removeBulkFile(i) { bulkFiles.value.splice(i, 1) }

async function submitBulk() {
  if (!bulkFiles.value.length) return
  bulkUploading.value = true
  let done = 0, failed = 0
  for (const entry of bulkFiles.value) {
    if (!entry.ref.trim() || !entry.description.trim() || !entry.amount) {
      entry.status = 'error'; failed++; continue
    }
    try {
      // Create the document first, then attach the file
      const created = await sourceDocsApi.create({
        entityId:        entityId.value,
        documentType:    entry.docType,
        documentDate:    entry.date,
        referenceNumber: entry.ref.trim(),
        description:     entry.description.trim(),
        amount:          Number(entry.amount),
        currencyCode:    entry.currencyCode || 'KES',
      })
      const fd = new FormData()
      fd.append('file', entry.file)
      await sourceDocsApi.uploadAttachment(created.id, fd)
      entry.status = 'done'
      docList.value.unshift(norm(created))
      done++
    } catch { entry.status = 'error'; failed++ }
  }
  bulkUploading.value = false
  toast.success(`Bulk upload: ${done} created${failed ? `, ${failed} failed` : ''}.`)
  if (!failed) { showBulk.value = false; bulkFiles.value = [] }
}

// ── New document ──────────────────────────────────────────────────────────────
const showNew   = ref(false)
const newSaving = ref(false)
const newForm   = ref({
  documentType: 'PURCHASE_INVOICE',
  referenceNumber: '',
  documentDate: new Date().toISOString().slice(0, 10),
  description: '',
  amount: '',
  currencyCode: 'KES',
  periodId: '',
})
const newFile   = ref(null)
const newFileEl = ref(null)

function openNew() {
  newForm.value = {
    documentType: 'PURCHASE_INVOICE',
    referenceNumber: '',
    documentDate: new Date().toISOString().slice(0, 10),
    description: '',
    amount: '',
    currencyCode: 'KES',
    periodId: '',
  }
  newFile.value = null
  showNew.value = true
}

async function saveNew() {
  if (!newForm.value.referenceNumber.trim()) { toast.warn('Reference number is required.'); return }
  if (!newForm.value.documentDate)           { toast.warn('Document date is required.'); return }
  if (!newForm.value.description.trim())     { toast.warn('Description is required.'); return }
  if (!newForm.value.amount || Number(newForm.value.amount) <= 0) { toast.warn('Amount is required and must be greater than 0.'); return }
  newSaving.value = true
  try {
    const body = {
      entityId:        entityId.value,
      documentType:    newForm.value.documentType,
      documentDate:    newForm.value.documentDate,
      referenceNumber: newForm.value.referenceNumber.trim(),
      description:     newForm.value.description.trim(),
      amount:          Number(newForm.value.amount),
      currencyCode:    newForm.value.currencyCode || 'KES',
      periodId:        newForm.value.periodId || undefined,
    }
    const created = await sourceDocsApi.create(body)
    // Attach file if one was picked
    if (newFile.value && created?.id) {
      const fd = new FormData()
      fd.append('file', newFile.value)
      await sourceDocsApi.uploadAttachment(created.id, fd)
    }
    docList.value.unshift(norm(created))
    toast.success('Document created.')
    showNew.value = false
    if (newFileEl.value) newFileEl.value.value = ''
  } catch { /* handled */ } finally { newSaving.value = false }
}

const openPeriods = computed(() =>
  allPeriods.value.filter(p => p.status === 'OPEN' || p.status === 'ADJUSTING')
)

function typeLabel(t) {
  return docTypesCache.list.value.find(c => c.code === t)?.label ?? (t || '').replace(/_/g, ' ')
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="Source Documents"
      :meta="`${docList.length} documents · DRAFT → SUBMITTED → REVIEWED → APPROVED → ARCHIVED`"
    >
      <Button variant="ghost" icon="upload" @click="showBulk = true">Bulk upload</Button>
      <Button variant="primary" icon="plus" @click="openNew">New document</Button>
    </PageHeader>

    <div class="page-section stack">
      <TableToolbar v-model:search="search">
        <ChipFilter v-for="s in STATUSES" :key="s" :active="statusFilter === s" @click="statusFilter = s">
          {{ s }}
        </ChipFilter>
      </TableToolbar>

      <div class="card">
        <div v-if="loading" style="padding:32px;text-align:center;color:var(--muted);font-size:13px">Loading…</div>
        <table v-else class="tbl">
          <thead>
            <tr>
              <th>Reference</th>
              <th>Type</th>
              <th>Date</th>
              <th>Description</th>
              <th class="num">Amount</th>
              <th>Files</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="d in filtered" :key="d.id" class="row-link" @click="drawer = d">
              <td><code>{{ d._ref }}</code></td>
              <td style="font-size:11px">{{ typeLabel(d._type) }}</td>
              <td>{{ fmtDate(d._date) }}</td>
              <td class="muted" style="font-size:12px;max-width:220px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">
                {{ d._desc || '—' }}
              </td>
              <td class="num mono">{{ d._amount ? `${d._currency} ${fmt(d._amount)}` : '—' }}</td>
              <td>
                <Badge status="outline" :dot="false" style="gap:4px">
                  <Ico name="docs" :size="11" /> {{ d._attCount || 0 }}
                </Badge>
              </td>
              <td><Badge :status="d.status" :dot="false" /></td>
              <td @click.stop><IconBtn icon="dots" @click="drawer = d" /></td>
            </tr>
            <tr v-if="!filtered.length">
              <td colspan="8" style="padding:32px;text-align:center;color:var(--muted)">No documents found.</td>
            </tr>
          </tbody>
        </table>
      </div>

      <TableFooter :total="filtered.length" label="documents" />
    </div>

    <!-- ── View / manage document ──────────────────────────────────────────── -->
    <Modal
      :open="!!drawer && !showDelete"
      :title="drawer?._ref"
      :subtitle="typeLabel(drawer?._type) + (drawer?._date ? ' · ' + fmtDate(drawer._date) : '')"
      :width="820"
      @close="drawer = null"
    >
      <template v-if="drawer">
        <!-- Workflow stepper -->
        <div class="stepper">
          <div v-for="(s, i) in WORKFLOW" :key="s" :class="['step', i < statusIdx ? 'done' : i === statusIdx ? 'active' : '']">
            <div class="step-dot">
              <Ico v-if="i < statusIdx" name="check" :size="10" />
              <span v-else>{{ i + 1 }}</span>
            </div>
            <div class="step-label">{{ s }}</div>
            <div v-if="i < WORKFLOW.length - 1" class="step-line" />
          </div>
        </div>

        <!-- Fields -->
        <div class="form-grid cols-3">
          <div class="field">
            <label>Reference</label>
            <input class="input mono" :value="drawer._ref" readonly />
          </div>
          <div class="field">
            <label>Type</label>
            <input class="input" :value="typeLabel(drawer._type)" readonly />
          </div>
          <div class="field">
            <label>Status</label>
            <div style="padding-top:4px"><Badge :status="drawer.status" :dot="false" /></div>
          </div>
          <div class="field">
            <label>Date</label>
            <input class="input" :value="fmtDate(drawer._date)" readonly />
          </div>
          <div class="field">
            <label>Amount</label>
            <input class="input mono" :value="drawer._amount ? `${drawer._currency} ${fmt(drawer._amount)}` : '—'" readonly />
          </div>
          <div class="field">
            <label>Currency</label>
            <input class="input mono" :value="drawer._currency" readonly />
          </div>
          <div v-if="drawer._desc" class="field" style="grid-column:1/-1">
            <label>Description</label>
            <input class="input" :value="drawer._desc" readonly />
          </div>
        </div>

        <!-- Attachments -->
        <div class="card">
          <div class="card-head" style="display:flex;justify-content:space-between;align-items:center">
            <span>Attachments ({{ attachments.length }})</span>
            <label class="attach-btn" :class="{ loading: uploading }">
              <Ico name="upload" :size="12" />
              {{ uploading ? 'Uploading…' : 'Attach file' }}
              <input ref="fileInput" type="file" style="display:none" :disabled="uploading" @change="uploadFile" />
            </label>
          </div>
          <div class="att-list">
            <div v-if="!attachments.length" class="muted" style="font-size:12px;font-style:italic;padding:8px 0">
              No files attached yet.
            </div>
            <div v-for="att in attachments" :key="att.id" class="att-row">
              <Ico name="docs" :size="14" style="color:var(--accent);flex-shrink:0" />
              <span class="att-name">{{ att.fileName }}</span>
              <span class="att-size muted">{{ fmtSize(att.fileSize) }}</span>
              <div style="display:flex;gap:4px;margin-left:auto">
                <IconBtn icon="download" title="Download" @click="downloadAtt(att)" />
                <IconBtn
                  icon="x"
                  title="Remove"
                  style="color:oklch(0.55 0.18 15)"
                  :disabled="removingAtt === att.id"
                  @click="removeAttachment(att)"
                />
              </div>
            </div>
          </div>
        </div>
      </template>

      <template #footer>
        <!-- Workflow actions -->
        <template v-if="drawer?.status === 'DRAFT'">
          <Button variant="primary" icon="approve" :loading="transiting" @click="transition(drawer.id, 'submit')">Submit for review</Button>
          <Button variant="ghost" icon="reject" :loading="transiting" @click="transition(drawer.id, 'void')">Void</Button>
        </template>
        <template v-else-if="drawer?.status === 'SUBMITTED'">
          <Button variant="primary" icon="approve" :loading="transiting" @click="transition(drawer.id, 'review')">Mark reviewed</Button>
          <Button variant="ghost" icon="reject" :loading="transiting" @click="transition(drawer.id, 'void')">Void</Button>
        </template>
        <template v-else-if="drawer?.status === 'REVIEWED'">
          <Button variant="primary" icon="approve" :loading="transiting" @click="transition(drawer.id, 'approve')">Approve</Button>
          <Button variant="ghost" icon="rotate" :loading="transiting" @click="transition(drawer.id, 'submit')">Send back</Button>
        </template>
        <template v-else-if="drawer?.status === 'APPROVED'">
          <Button variant="ghost" icon="doc" :loading="transiting" @click="transition(drawer.id, 'archive')">Archive</Button>
        </template>
        <template v-else-if="drawer?.status === 'ARCHIVED'">
          <Button variant="ghost" icon="rotate" :loading="transiting" @click="transition(drawer.id, 'restore')">Restore to Draft</Button>
        </template>

        <div style="flex:1" />

        <!-- Delete — only for DRAFT or VOID -->
        <Button
          v-if="drawer?.status === 'DRAFT' || drawer?.status === 'VOID'"
          variant="danger"
          icon="x"
          @click="showDelete = true"
        >Delete</Button>

        <Button variant="ghost" @click="drawer = null">Close</Button>
      </template>
    </Modal>

    <!-- ── Delete confirmation ─────────────────────────────────────────────── -->
    <Modal :open="showDelete" title="Delete document?" :width="440" @close="showDelete = false">
      <p style="font-size:13px;line-height:1.6;color:var(--fg)">
        Permanently delete <strong>{{ drawer?._ref }}</strong>?
        This will also remove all attachments. This cannot be undone.
      </p>
      <template #footer>
        <Button variant="danger" :loading="deleting" @click="confirmDelete">Yes, delete permanently</Button>
        <Button variant="ghost" @click="showDelete = false">Cancel</Button>
      </template>
    </Modal>

    <!-- ── New document ────────────────────────────────────────────────────── -->
    <Modal :open="showNew" title="New Source Document" subtitle="Created in DRAFT status" :width="580" @close="showNew = false">
      <div class="form-grid cols-2">
        <div class="field">
          <label>Reference number <span class="req">*</span></label>
          <input v-model="newForm.referenceNumber" class="input mono" placeholder="e.g. INV-2026-001" />
        </div>
        <div class="field">
          <label>Document type <span class="req">*</span></label>
          <SearchableSelect
            v-model="newForm.documentType"
            :options="DOC_TYPES.map(t => ({ value: t, label: typeLabel(t) }))"
            placeholder="Select type…"
          />
        </div>
        <div class="field">
          <label>Document date <span class="req">*</span></label>
          <input v-model="newForm.documentDate" class="input" type="date" />
        </div>
        <div class="field">
          <label>Period</label>
          <SearchableSelect
            v-model="newForm.periodId"
            :options="[{ value: '', label: '— None —' }, ...openPeriods.map(p => ({ value: p.id, label: (p.periodName || p.startDate?.substring(0,7)) + ' (' + p.status + ')' }))]"
            placeholder="— None —"
          />
        </div>
        <div class="field">
          <label>Amount <span class="req">*</span></label>
          <AmountInput v-model="newForm.amount" class="input mono" />
        </div>
        <div class="field">
          <label>Currency</label>
          <input v-model="newForm.currencyCode" class="input mono" maxlength="3" placeholder="KES" />
        </div>
        <div class="field" style="grid-column:1/-1">
          <label>Description <span class="req">*</span></label>
          <input v-model="newForm.description" class="input" placeholder="e.g. Office supplies — Q1 purchase" />
        </div>
        <div class="field" style="grid-column:1/-1">
          <label>Attach file <span class="muted" style="font-weight:400;font-size:10px">(optional — you can add more later)</span></label>
          <label class="file-pick">
            <Ico name="upload" :size="14" />
            <span>{{ newFile ? newFile.name : 'Click to select a file' }}</span>
            <input ref="newFileEl" type="file" style="display:none" @change="e => newFile = e.target.files[0]" />
          </label>
        </div>
      </div>
      <template #footer>
        <Button variant="primary" icon="plus" :loading="newSaving" @click="saveNew">Create document</Button>
        <Button variant="ghost" @click="showNew = false">Cancel</Button>
      </template>
    </Modal>

    <!-- ── Bulk upload ─────────────────────────────────────────────────────── -->
    <Modal :open="showBulk" title="Bulk upload documents" subtitle="Each file creates one source document in DRAFT" :width="1000" @close="showBulk = false">
      <!-- Drop zone -->
      <div
        ref="bulkDropEl"
        class="drop-zone"
        @dragover.prevent
        @drop="onBulkDrop"
        @click="$refs.bulkPickEl.click()"
      >
        <Ico name="upload" :size="24" style="color:var(--muted)" />
        <div style="font-size:13px;font-weight:600;margin-top:8px">Drop files here or click to select</div>
        <div style="font-size:11px;color:var(--muted);margin-top:4px">PDF, images, Excel, Word, CSV</div>
        <input ref="bulkPickEl" type="file" multiple accept=".pdf,.png,.jpg,.jpeg,.xlsx,.xls,.csv,.docx,.doc" style="display:none" @change="onBulkPick" />
      </div>

      <!-- File rows -->
      <div v-if="bulkFiles.length" class="bulk-list">
        <div class="bulk-header">
          <span class="bc-file">File</span>
          <span class="bc-ref">Ref # <span class="req">*</span></span>
          <span class="bc-desc">Description <span class="req">*</span></span>
          <span class="bc-type">Type</span>
          <span class="bc-amt">Amount <span class="req">*</span></span>
          <span class="bc-ccy">CCY</span>
          <span class="bc-date">Date</span>
          <span class="bc-del"></span>
        </div>
        <div v-for="(entry, i) in bulkFiles" :key="i" class="bulk-row" :class="entry.status">
          <div class="bulk-fname bc-file">
            <Ico name="docs" :size="12" style="color:var(--accent);flex-shrink:0" />
            <span class="bulk-fname-text">{{ entry.name }}</span>
            <Ico v-if="entry.status === 'done'"  name="check" :size="12" style="color:var(--accent);flex-shrink:0" />
            <Ico v-if="entry.status === 'error'" name="warn"  :size="12" style="color:oklch(0.55 0.18 15);flex-shrink:0" />
          </div>
          <input v-model="entry.ref"         class="input mono bc-ref"  style="height:28px;font-size:11px;box-sizing:border-box" placeholder="INV-001" :disabled="entry.status === 'done'" />
          <input v-model="entry.description" class="input      bc-desc" style="height:28px;font-size:11px;box-sizing:border-box" placeholder="Description…" :disabled="entry.status === 'done'" />
          <SearchableSelect
            v-model="entry.docType"
            :options="DOC_TYPES.map(t => ({ value: t, label: typeLabel(t) }))"
            placeholder="Select type…"
            :compact="true"
            :disabled="entry.status === 'done'"
            class="bc-type"
          />
          <AmountInput v-model="entry.amount" class="input mono bc-amt" style="height:28px;font-size:11px;text-align:right;box-sizing:border-box" :disabled="entry.status === 'done'" />
          <input v-model="entry.currencyCode" class="input mono bc-ccy" style="height:28px;font-size:11px;box-sizing:border-box" maxlength="3" placeholder="KES" :disabled="entry.status === 'done'" />
          <input v-model="entry.date"        class="input       bc-date" style="height:28px;font-size:11px;box-sizing:border-box" type="date" :disabled="entry.status === 'done'" />
          <div class="bc-del"><IconBtn icon="x" :disabled="entry.status === 'done'" @click="removeBulkFile(i)" /></div>
        </div>
        <!-- Validation hint if any row is missing required fields -->
        <div v-if="bulkFiles.some(f => f.status === 'pending' && (!f.ref.trim() || !f.description.trim() || !f.amount))" class="bulk-hint">
          <Ico name="warn" :size="12" /> Rows highlighted in red are missing required fields and will be skipped.
        </div>
      </div>

      <template #footer>
        <Button
          variant="primary"
          icon="upload"
          :loading="bulkUploading"
          :disabled="!bulkFiles.length || bulkFiles.every(f => f.status === 'done')"
          @click="submitBulk"
        >Upload {{ bulkFiles.filter(f => f.status === 'pending').length || '' }} documents</Button>
        <Button variant="ghost" @click="() => { showBulk = false; bulkFiles = [] }">Cancel</Button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.req { color: oklch(0.55 0.18 15); }

/* ── Stepper ── */
.stepper {
  display: flex;
  align-items: flex-start;
  gap: 0;
  margin-bottom: 4px;
}
.step {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
  position: relative;
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--muted);
}
.step.active { color: var(--accent); }
.step.done   { color: var(--accent); opacity: 0.6; }
.step-dot {
  width: 22px; height: 22px;
  border-radius: 50%;
  border: 2px solid var(--border);
  background: var(--surface);
  display: flex; align-items: center; justify-content: center;
  font-size: 10px; font-weight: 700;
  margin-bottom: 4px;
  z-index: 1;
}
.step.active .step-dot { border-color: var(--accent); background: var(--accent); color: #fff; }
.step.done   .step-dot { border-color: var(--accent); background: var(--accent); color: #fff; }
.step-label { font-size: 9.5px; text-align: center; }
.step-line {
  position: absolute;
  top: 11px;
  left: 50%;
  width: 100%;
  height: 2px;
  background: var(--border);
  z-index: 0;
}
.step.done .step-line, .step.active .step-line { background: var(--accent); opacity: 0.4; }

/* ── Attachments ── */
.attach-btn {
  display: inline-flex; align-items: center; gap: 5px;
  font-size: 12px; font-weight: 500;
  padding: 4px 10px; border-radius: 6px;
  border: 1px solid var(--border);
  cursor: pointer; color: var(--fg);
  background: var(--surface-2);
  transition: border-color 0.1s, background 0.1s;
}
.attach-btn:hover { border-color: var(--accent); background: var(--surface); }
.attach-btn.loading { opacity: 0.6; cursor: not-allowed; }

.att-list { display: flex; flex-direction: column; gap: 0; padding: 0 0 4px; }
.att-row {
  display: flex; align-items: center; gap: 8px;
  padding: 7px 0;
  border-bottom: 1px solid var(--border-faint, var(--border));
  font-size: 12px;
}
.att-row:last-child { border-bottom: none; }
.att-name { flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.att-size  { font-size: 11px; white-space: nowrap; }

/* ── New document file pick ── */
.file-pick {
  display: flex; align-items: center; gap: 8px;
  border: 2px dashed var(--border);
  border-radius: 8px;
  padding: 12px 16px;
  cursor: pointer; font-size: 12px; color: var(--muted);
  transition: border-color 0.15s;
}
.file-pick:hover { border-color: var(--accent); }

/* ── Bulk upload ── */
.drop-zone {
  border: 2px dashed var(--border);
  border-radius: 10px;
  padding: 32px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.drop-zone:hover { border-color: var(--accent); background: color-mix(in oklab, var(--accent) 4%, var(--surface)); }

.bulk-list  { display: flex; flex-direction: column; gap: 0; margin-top: 4px; }
.bulk-header {
  display: flex; align-items: center; gap: 6px;
  padding: 6px 4px;
  font-size: 10px; font-weight: 700; text-transform: uppercase;
  letter-spacing: 0.04em; color: var(--muted);
  border-bottom: 1px solid var(--border);
}
.bulk-row {
  display: flex; align-items: center; gap: 6px;
  padding: 4px 0;
  border-bottom: 1px solid var(--border-faint, var(--border));
}
.bulk-row.done  { opacity: 0.5; }
.bulk-row.error { background: color-mix(in oklab, oklch(0.55 0.18 15) 6%, var(--surface)); }

/* Column widths */
.bc-file { flex: 0 0 160px; min-width: 0; }
.bc-ref  { flex: 0 0 100px; }
.bc-desc { flex: 1;          min-width: 120px; }
.bc-type { flex: 0 0 140px; }
.bc-amt  { flex: 0 0 90px;  }
.bc-ccy  { flex: 0 0 46px;  }
.bc-date { flex: 0 0 110px; }
.bc-del  { flex: 0 0 28px;  }

.bulk-fname {
  display: flex; align-items: center; gap: 5px;
  font-size: 11px; min-width: 0;
}
.bulk-fname-text { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; flex: 1; }
.bulk-hint {
  display: flex; align-items: center; gap: 6px;
  font-size: 11.5px; color: oklch(0.45 0.16 40);
  padding: 8px 4px 4px;
}
</style>
