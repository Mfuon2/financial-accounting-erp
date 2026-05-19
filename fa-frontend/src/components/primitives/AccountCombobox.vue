<script setup>
import { ref, computed, onUnmounted } from 'vue'

const props = defineProps({
  modelValue:  { type: String, default: '' },
  accounts:    { type: Array, default: () => [] },
  allAccounts: { type: Array, default: () => [] }, // full account list incl. headers, for "is header" detection
  placeholder: { type: String, default: 'Search by code or name…' },
  error:       { type: Boolean, default: false },
  tableCell:   { type: Boolean, default: false }, // compact style when inside a table
})
const emit = defineEmits(['update:modelValue'])

const inputEl  = ref(null)
const open     = ref(false)
const cursor   = ref(-1)
const dropStyle = ref({})

const filtered = computed(() => {
  const q = (props.modelValue || '').toLowerCase().trim()
  if (!q) return props.accounts.slice(0, 15)
  return props.accounts
    .filter(a =>
      a.accountCode.toLowerCase().includes(q) ||
      a.accountName.toLowerCase().includes(q)
    )
    .slice(0, 15)
})

// When user typed something but got no results, check if they typed a header account code
const isHeaderAccount = computed(() => {
  const q = (props.modelValue || '').trim()
  if (!q || filtered.value.length > 0) return false
  const pool = props.allAccounts.length ? props.allAccounts : props.accounts
  const match = pool.find(a => a.accountCode.toLowerCase() === q.toLowerCase())
  return !!match?.isHeader
})

function positionDrop() {
  const r = inputEl.value?.getBoundingClientRect()
  if (!r) return
  dropStyle.value = {
    top:   (r.bottom + 3) + 'px',
    left:   r.left + 'px',
    width:  Math.max(r.width, 320) + 'px',
  }
}

function onFocus() {
  cursor.value = -1
  positionDrop()
  open.value = true
}

function onBlur() {
  setTimeout(() => { open.value = false }, 160)
}

function onInput(e) {
  emit('update:modelValue', e.target.value)
  cursor.value = -1
  positionDrop()
  open.value = true
}

function pick(acct) {
  emit('update:modelValue', acct.accountCode)
  open.value = false
  inputEl.value?.blur()
}

function onKeydown(e) {
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    cursor.value = Math.min(cursor.value + 1, filtered.value.length - 1)
    if (!open.value) { positionDrop(); open.value = true }
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    cursor.value = Math.max(cursor.value - 1, 0)
  } else if (e.key === 'Enter' && open.value && cursor.value >= 0) {
    e.preventDefault()
    pick(filtered.value[cursor.value])
  } else if (e.key === 'Tab' && open.value && filtered.value.length === 1) {
    pick(filtered.value[0])
  } else if (e.key === 'Escape') {
    open.value = false
  }
}
</script>

<template>
  <div style="position:relative;width:100%">
    <input
      ref="inputEl"
      :value="modelValue"
      class="input mono"
      :class="{ 'input-error': error }"
      :placeholder="placeholder"
      style="width:100%;box-sizing:border-box"
      autocomplete="off"
      spellcheck="false"
      @focus="onFocus"
      @blur="onBlur"
      @input="onInput"
      @keydown="onKeydown"
    />

    <Teleport to="body">
      <div v-if="open && (filtered.length || (modelValue && !filtered.length))" class="acct-drop" :style="dropStyle">
        <div
          v-for="(a, i) in filtered"
          :key="a.id || a.accountCode"
          class="acct-opt"
          :class="{ 'acct-opt-hi': i === cursor }"
          @mousedown.prevent="pick(a)"
        >
          <code class="acct-code">{{ a.accountCode }}</code>
          <span class="acct-name">{{ a.accountName }}</span>
          <span class="acct-sub">{{ (a.accountSubtype || '').replace(/_/g, ' ') }}</span>
        </div>
        <div v-if="!filtered.length && modelValue" class="acct-empty">
          <span v-if="isHeaderAccount">
            <strong>{{ modelValue }}</strong> is a summary account — select a child account to post to.
          </span>
          <span v-else>No matching posting accounts for "{{ modelValue }}"</span>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.acct-drop {
  position: fixed;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 8px;
  box-shadow: var(--shadow-pop);
  z-index: 10000;
  max-height: 260px;
  overflow-y: auto;
  padding: 4px 0;
}

.acct-opt {
  display: grid;
  grid-template-columns: 72px 1fr auto;
  align-items: center;
  gap: 8px;
  padding: 7px 12px;
  cursor: pointer;
  font-size: 12px;
  transition: background 0.08s;
}
.acct-opt:hover, .acct-opt-hi { background: var(--surface-2); }

.acct-code {
  font-size: 11px;
  font-weight: 700;
  color: var(--accent);
  flex-shrink: 0;
}
.acct-name {
  color: var(--fg);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
}
.acct-sub {
  font-size: 10px;
  color: var(--muted);
  white-space: nowrap;
  text-transform: uppercase;
  letter-spacing: 0.03em;
}
.acct-empty {
  padding: 10px 12px;
  font-size: 12px;
  color: var(--muted);
  font-style: italic;
}
</style>
