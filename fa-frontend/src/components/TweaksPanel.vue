<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import SearchableSelect from '@/components/primitives/SearchableSelect.vue'

const ACCENT_OPTIONS = [
  { value: 'emerald',  label: 'Emerald' },
  { value: 'blue',     label: 'Blue' },
  { value: 'violet',   label: 'Violet' },
  { value: 'amber',    label: 'Amber' },
  { value: 'graphite', label: 'Graphite' },
]

const props = defineProps({
  tweaks: { type: Object, required: true },
})

const emit = defineEmits(['update:tweaks'])

const open = ref(false)
const panelRef = ref(null)
const pos = ref({ x: 16, y: 16 })

let dragging = false
let dragStart = { mx: 0, my: 0, px: 0, py: 0 }

function set(key, value) {
  emit('update:tweaks', { ...props.tweaks, [key]: value })
}

function thumbStyle(current, options) {
  const total = options.length
  const n = options.indexOf(current)
  const idx = n < 0 ? 0 : n
  return {
    left: `calc(2px + ${idx} * (100% - 4px) / ${total})`,
    width: `calc((100% - 4px) / ${total})`,
  }
}

function startDrag(e) {
  dragging = true
  dragStart = {
    mx: e.clientX,
    my: e.clientY,
    px: pos.value.x,
    py: pos.value.y,
  }
  e.preventDefault()
}

function onMouseMove(e) {
  if (!dragging) return
  const dx = dragStart.mx - e.clientX
  const dy = dragStart.my - e.clientY
  pos.value = {
    x: Math.max(0, dragStart.px + dx),
    y: Math.max(0, dragStart.py + dy),
  }
}

function onMouseUp() {
  dragging = false
}

function close() {
  open.value = false
  window.postMessage({ type: '__edit_mode_dismissed' }, '*')
}

function onMessage(e) {
  if (e.data && e.data.type === '__activate_edit_mode') {
    open.value = true
  }
}

onMounted(() => {
  window.addEventListener('message', onMessage)
  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
})

onBeforeUnmount(() => {
  window.removeEventListener('message', onMessage)
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="twk-panel"
      ref="panelRef"
      :style="{ right: pos.x + 'px', bottom: pos.y + 'px' }"
    >
      <div class="twk-hd" @mousedown="startDrag">
        <b>Look &amp; feel</b>
        <button class="twk-x" @mousedown.stop @click="close">✕</button>
      </div>
      <div class="twk-body">
        <!-- Theme -->
        <div class="twk-sect">Theme</div>

        <div class="twk-row">
          <div class="twk-lbl"><span>Mode</span></div>
          <div class="twk-seg">
            <div class="twk-seg-thumb" :style="thumbStyle(tweaks.theme, ['light','dark'])"/>
            <button @click="set('theme','light')">Light</button>
            <button @click="set('theme','dark')">Dark</button>
          </div>
        </div>

        <div class="twk-row">
          <div class="twk-lbl"><span>Accent</span></div>
          <SearchableSelect
            :modelValue="tweaks.accent"
            :options="ACCENT_OPTIONS"
            placeholder="Select accent"
            @update:modelValue="set('accent', $event)"
          />
        </div>

        <!-- Layout -->
        <div class="twk-sect">Layout</div>

        <div class="twk-row">
          <div class="twk-lbl"><span>Density</span></div>
          <div class="twk-seg">
            <div class="twk-seg-thumb" :style="thumbStyle(tweaks.density, ['compact','comfortable'])"/>
            <button @click="set('density','compact')">Compact</button>
            <button @click="set('density','comfortable')">Comfortable</button>
          </div>
        </div>

        <div class="twk-row">
          <div class="twk-lbl"><span>Sidebar</span></div>
          <div class="twk-seg">
            <div class="twk-seg-thumb" :style="thumbStyle(tweaks.sidebar, ['labeled','iconly'])"/>
            <button @click="set('sidebar','labeled')">Labeled</button>
            <button @click="set('sidebar','iconly')">Icons</button>
          </div>
        </div>

        <div class="twk-row">
          <div class="twk-lbl"><span>Tables</span></div>
          <div class="twk-seg">
            <div class="twk-seg-thumb" :style="thumbStyle(tweaks.tableStyle, ['lined','zebra'])"/>
            <button @click="set('tableStyle','lined')">Lined</button>
            <button @click="set('tableStyle','zebra')">Zebra</button>
          </div>
        </div>

        <!-- Typography -->
        <div class="twk-sect">Typography</div>

        <div class="twk-row">
          <div class="twk-lbl"><span>Font</span></div>
          <div class="twk-seg">
            <div class="twk-seg-thumb" :style="thumbStyle(tweaks.font, ['manrope','geist','serif'])"/>
            <button @click="set('font','manrope')">Manrope</button>
            <button @click="set('font','geist')">Geist</button>
            <button @click="set('font','serif')">Serif</button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>
