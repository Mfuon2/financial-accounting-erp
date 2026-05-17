<template>
  <svg v-if="data && data.length > 1" class="spark" :width="w" :height="h">
    <path :d="areaD" :fill="fill" stroke="none" />
    <path :d="lineD" :stroke="stroke" :stroke-width="strokeWidth" fill="none" stroke-linecap="round" stroke-linejoin="round" />
  </svg>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  data: { type: Array, default: () => [] },
  w: { type: Number, default: 80 },
  h: { type: Number, default: 22 },
  stroke: { type: String, default: 'var(--accent)' },
  fill: { type: String, default: 'var(--accent-soft)' },
  strokeWidth: { type: Number, default: 1.4 },
})

const min = computed(() => props.data.length ? Math.min(...props.data) : 0)
const max = computed(() => props.data.length ? Math.max(...props.data) : 1)

const lineD = computed(() => {
  const { data, w, h } = props
  if (!data || data.length < 2) return ''
  const mn = min.value
  const span = max.value - mn || 1
  const len = data.length
  return data.map((v, i) => {
    const x = (i / (len - 1)) * w
    const y = h - ((v - mn) / span) * (h - 3) - 1.5
    return `${i === 0 ? 'M' : 'L'}${x},${y}`
  }).join(' ')
})

const areaD = computed(() => lineD.value ? `${lineD.value} L${props.w},${props.h} L0,${props.h} Z` : '')
</script>
