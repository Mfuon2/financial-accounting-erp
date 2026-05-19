<script setup>
/**
 * SearchableSelect — drop-in replacement for <select class="select">.
 *
 * Props:
 *   modelValue   — currently selected value (v-model)
 *   options      — [{ value, label, group? }]  flat list; group key triggers group headers
 *   placeholder  — shown when nothing is selected
 *   disabled     — disables interaction
 *   loading      — shows spinner, disables interaction
 *   compact      — smaller height for use inside tables / tight layouts
 *   mono         — monospace font (for code / prefix selects)
 *
 * Usage:
 *   <SearchableSelect
 *     v-model="form.periodId"
 *     :options="periods.map(p => ({ value: p.id, label: p.periodName }))"
 *     placeholder="Select period…"
 *   />
 */
import { ref, computed, nextTick, onUnmounted } from 'vue'

const props = defineProps({
  modelValue:        { default: null },
  options:           { type: Array, default: () => [] },
  placeholder:       { type: String, default: 'Select…' },
  searchPlaceholder: { type: String, default: 'Search…' },
  disabled:          { type: Boolean, default: false },
  loading:           { type: Boolean, default: false },
  compact:           { type: Boolean, default: false },
  mono:              { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue'])

const triggerEl = ref(null)
const searchEl  = ref(null)
const open      = ref(false)
const query     = ref('')
const cursor    = ref(-1)
const dropStyle = ref({})

// ── Derived lists ─────────────────────────────────────────────────────────────

const filteredGroups = computed(() => {
  const q = query.value.toLowerCase().trim()
  const matched = q
    ? props.options.filter(o => String(o.label ?? '').toLowerCase().includes(q))
    : props.options

  // Preserve insertion order of groups
  const map = new Map()
  for (const opt of matched) {
    const g = opt.group ?? ''
    if (!map.has(g)) map.set(g, [])
    map.get(g).push(opt)
  }
  return [...map.entries()].map(([group, items]) => ({ group, items }))
})

const flatFiltered = computed(() => filteredGroups.value.flatMap(g => g.items))

const selectedLabel = computed(() => {
  const v = props.modelValue
  if (v === null || v === undefined || v === '') return null
  return props.options.find(o => String(o.value) === String(v))?.label ?? null
})

// ── Positioning ───────────────────────────────────────────────────────────────

function positionDrop() {
  const r = triggerEl.value?.getBoundingClientRect()
  if (!r) return
  const spaceBelow = window.innerHeight - r.bottom - 8
  const spaceAbove = r.top - 8
  const needHeight = Math.min(300, flatFiltered.value.length * 34 + 48)

  if (spaceBelow >= needHeight || spaceBelow >= spaceAbove) {
    dropStyle.value = {
      top:       (r.bottom + 3) + 'px',
      left:      r.left + 'px',
      width:     r.width + 'px',
      maxHeight: Math.min(300, spaceBelow) + 'px',
    }
  } else {
    // Flip upward
    dropStyle.value = {
      bottom:    (window.innerHeight - r.top + 3) + 'px',
      left:      r.left + 'px',
      width:     r.width + 'px',
      maxHeight: Math.min(300, spaceAbove) + 'px',
    }
  }
}

// ── Open / close ──────────────────────────────────────────────────────────────

async function openDrop() {
  if (props.disabled || props.loading) return
  query.value = ''
  cursor.value = -1
  positionDrop()
  open.value = true
  await nextTick()
  searchEl.value?.focus()
}

function closeDrop() {
  open.value = false
  query.value = ''
}

function pick(opt) {
  emit('update:modelValue', opt.value)
  closeDrop()
  triggerEl.value?.focus()
}

// ── Keyboard nav ──────────────────────────────────────────────────────────────

function onTriggerKeydown(e) {
  if (['Enter', ' ', 'ArrowDown'].includes(e.key)) {
    e.preventDefault()
    openDrop()
  }
}

function onSearchKeydown(e) {
  const len = flatFiltered.value.length
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    cursor.value = Math.min(cursor.value + 1, len - 1)
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    cursor.value = Math.max(cursor.value - 1, 0)
  } else if (e.key === 'Enter') {
    e.preventDefault()
    const target = cursor.value >= 0 ? flatFiltered.value[cursor.value]
                 : len === 1 ? flatFiltered.value[0] : null
    if (target) pick(target)
  } else if (e.key === 'Escape' || e.key === 'Tab') {
    e.stopPropagation()
    closeDrop()
    triggerEl.value?.focus()
  }
}

function onSearchBlur() {
  // Delay so mousedown on an option fires first
  setTimeout(closeDrop, 150)
}

// ── Close on outside scroll / resize ─────────────────────────────────────────

function onScroll() { if (open.value) positionDrop() }
window.addEventListener('scroll', onScroll, { capture: true, passive: true })
window.addEventListener('resize', onScroll, { passive: true })
onUnmounted(() => {
  window.removeEventListener('scroll', onScroll, true)
  window.removeEventListener('resize', onScroll)
})
</script>

<template>
  <div class="ss-root">
    <!-- ── Trigger ─────────────────────────────────────────────────────── -->
    <button
      ref="triggerEl"
      type="button"
      class="ss-trigger"
      :class="{
        'ss-open':     open,
        'ss-disabled': disabled || loading,
        'ss-compact':  compact,
        'ss-mono':     mono,
      }"
      :disabled="disabled || loading"
      :tabindex="disabled ? -1 : 0"
      @click="openDrop"
      @keydown="onTriggerKeydown"
    >
      <span v-if="loading" class="ss-placeholder">Loading…</span>
      <span v-else-if="selectedLabel" class="ss-val">{{ selectedLabel }}</span>
      <span v-else class="ss-placeholder">{{ placeholder }}</span>

      <span class="ss-caret" aria-hidden="true">
        <svg width="10" height="6" viewBox="0 0 10 6" fill="none">
          <path d="M1 1l4 4 4-4" stroke="currentColor" stroke-width="1.5"
                stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </span>
    </button>

    <!-- ── Dropdown (portal to body) ──────────────────────────────────── -->
    <Teleport to="body">
      <div v-if="open" class="ss-drop" :style="dropStyle">
        <!-- Search input -->
        <div class="ss-search-wrap">
          <input
            ref="searchEl"
            v-model="query"
            class="ss-search"
            :placeholder="searchPlaceholder"
            autocomplete="off"
            spellcheck="false"
            @keydown="onSearchKeydown"
            @blur="onSearchBlur"
          />
        </div>

        <!-- Option list -->
        <div class="ss-list">
          <div v-if="flatFiltered.length === 0" class="ss-empty">No results</div>
          <template v-for="grp in filteredGroups" :key="grp.group">
            <div v-if="grp.group" class="ss-group">{{ grp.group }}</div>
            <div
              v-for="(opt, idx) in grp.items"
              :key="String(opt.value)"
              class="ss-opt"
              :class="{
                'ss-opt-selected': String(opt.value) === String(modelValue),
                'ss-opt-hi':       flatFiltered.indexOf(opt) === cursor,
              }"
              @mousedown.prevent="pick(opt)"
            >{{ opt.label }}</div>
          </template>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.ss-root {
  position: relative;
  width: 100%;
}

/* ── Trigger ──────────────────────────────────────────────────────────────── */
.ss-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 36px;
  padding: 0 10px 0 12px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 6px;
  font-size: 13px;
  font-family: inherit;
  color: var(--fg);
  cursor: pointer;
  text-align: left;
  gap: 8px;
  box-sizing: border-box;
  transition: border-color 0.12s, box-shadow 0.12s;
}
.ss-trigger:hover:not(.ss-disabled) { border-color: var(--accent); }
.ss-trigger:focus-visible {
  outline: none;
  border-color: var(--accent);
  box-shadow: 0 0 0 3px color-mix(in oklab, var(--accent) 18%, transparent);
}
.ss-open {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px color-mix(in oklab, var(--accent) 18%, transparent);
}
.ss-disabled { opacity: 0.5; cursor: not-allowed; }
.ss-compact { min-height: 28px; font-size: 12px; padding: 0 8px 0 10px; }
.ss-mono { font-family: 'JetBrains Mono', 'Fira Code', ui-monospace, monospace; font-size: 12px; }

.ss-val         { flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ss-placeholder { flex: 1; color: var(--fg-3); }
.ss-caret       { flex-shrink: 0; color: var(--fg-3); display: flex; align-items: center; transition: transform 0.15s; }
.ss-open .ss-caret { transform: rotate(180deg); }

/* ── Dropdown ─────────────────────────────────────────────────────────────── */
.ss-drop {
  position: fixed;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 8px;
  box-shadow: var(--shadow-pop);
  z-index: 10000;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.ss-search-wrap {
  padding: 6px;
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}
.ss-search {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid var(--border);
  border-radius: 5px;
  background: var(--surface-2);
  color: var(--fg);
  font-size: 12px;
  font-family: inherit;
  padding: 5px 8px;
  outline: none;
  transition: border-color 0.12s;
}
.ss-search:focus { border-color: var(--accent); }

.ss-list {
  overflow-y: auto;
  padding: 4px 0;
  flex: 1;
}

.ss-group {
  padding: 8px 12px 3px;
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--fg-3);
  user-select: none;
}

.ss-opt {
  padding: 7px 12px;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: background 0.08s;
}
.ss-opt:hover,
.ss-opt-hi       { background: var(--surface-2); }
.ss-opt-selected { color: var(--accent); font-weight: 600; }
.ss-opt-selected::after { content: ' ✓'; font-size: 11px; }

.ss-empty {
  padding: 14px 12px;
  text-align: center;
  font-size: 12px;
  color: var(--fg-3);
}
</style>
