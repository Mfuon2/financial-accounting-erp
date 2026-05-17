<template>
  <Teleport to="body">
    <div v-if="open" class="scrim" @click.self="$emit('close')">
      <div class="cmdk">
        <div>
          <input
            ref="inputRef"
            class="cmdk-input"
            v-model="q"
            placeholder="Search…"
            @keydown="onKey"
          />
        </div>
        <div class="cmdk-list">
          <template v-for="group in groups" :key="group.name">
            <div class="cmdk-group-label">{{ group.name }}</div>
            <div
              v-for="item in group.items"
              :key="item.route"
              :class="['cmdk-item', { active: flatIndex(item) === sel }]"
              @click="navigate(item.route)"
              @mouseenter="sel = flatIndex(item)"
            >
              <Ico v-if="item.icon" :name="item.icon" :size="13" />
              <span>{{ item.label }}</span>
              <span v-if="item.meta" class="cmdk-meta">{{ item.meta }}</span>
            </div>
          </template>
        </div>
        <div class="cmdk-foot">
          <span class="kbd">↑</span><span class="kbd">↓</span> navigate &nbsp;
          <span class="kbd">↵</span> select &nbsp;
          <span class="kbd">esc</span> close
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import Ico from '@/components/primitives/Ico.vue'

const props = defineProps({
  open: { type: Boolean },
  routes: { type: Array, default: () => [] },
})

const emit = defineEmits(['close', 'navigate'])

const q = ref('')
const sel = ref(0)
const inputRef = ref(null)

const filtered = computed(() => {
  const s = q.value.toLowerCase()
  if (!s) return props.routes
  return props.routes.filter(r =>
    r.label.toLowerCase().includes(s) ||
    (r.tags && r.tags.some(t => t.toLowerCase().includes(s)))
  )
})

const groups = computed(() => {
  const map = {}
  filtered.value.forEach(r => {
    const g = r.group || ''
    if (!map[g]) map[g] = { name: g, items: [] }
    map[g].items.push(r)
  })
  return Object.values(map)
})

const flatList = computed(() => groups.value.flatMap(g => g.items))

function flatIndex(item) {
  return flatList.value.indexOf(item)
}

function navigate(route) {
  emit('navigate', route)
  emit('close')
}

function onKey(e) {
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    sel.value = Math.min(sel.value + 1, flatList.value.length - 1)
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    sel.value = Math.max(sel.value - 1, 0)
  } else if (e.key === 'Enter') {
    const item = flatList.value[sel.value]
    if (item) navigate(item.route)
  } else if (e.key === 'Escape') {
    emit('close')
  }
}

watch(() => props.open, async (v) => {
  if (v) {
    q.value = ''
    sel.value = 0
    await nextTick()
    inputRef.value?.focus()
  }
})
</script>
