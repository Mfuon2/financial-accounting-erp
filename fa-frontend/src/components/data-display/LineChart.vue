<template>
  <svg :width="w" :height="h">
    <g v-for="(t, i) in 4" :key="'g' + i">
      <line
        :x1="padL"
        :y1="padT + (innerH / 3) * i"
        :x2="padL + innerW"
        :y2="padT + (innerH / 3) * i"
        stroke="var(--border)"
        stroke-dasharray="2 3"
      />
    </g>
    <line
      :x1="padL"
      :y1="padT + innerH"
      :x2="padL + innerW"
      :y2="padT + innerH"
      stroke="var(--border)"
      stroke-dasharray="2 3"
    />

    <g v-for="s in resolvedSeries" :key="s.name">
      <path :d="seriesPath(s.data)" :stroke="s.color" stroke-width="1.4" fill="none" stroke-linecap="round" stroke-linejoin="round" />
      <circle
        v-for="(v, i) in s.data"
        :key="i"
        :cx="pointX(i, s.data.length)"
        :cy="pointY(v)"
        r="2.5"
        :fill="s.color"
        stroke="var(--surface)"
        stroke-width="1"
      />
    </g>

    <text
      v-for="(lbl, i) in resolvedLabels"
      :key="'xl' + i"
      :x="pointX(i, resolvedLabels.length)"
      :y="h - 5"
      font-size="9"
      fill="var(--muted)"
      text-anchor="middle"
      :font-family="'var(--font-mono)'"
    >{{ lbl }}</text>

    <text
      v-for="(t, i) in 4"
      :key="'yl' + i"
      :x="padL - 4"
      :y="padT + (innerH / 3) * i + 3"
      font-size="9"
      fill="var(--muted)"
      text-anchor="end"
      :font-family="'var(--font-mono)'"
    >{{ yLabel(i) }}</text>
  </svg>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  series: { type: Array, default: () => [] },
  data: { type: Object },
  w: { type: Number, default: 600 },
  h: { type: Number, default: 180 },
  labels: { type: Array, default: () => [] },
})

const padL = 36
const padR = 8
const padT = 8
const padB = 22

const innerW = computed(() => props.w - padL - padR)
const innerH = computed(() => props.h - padT - padB)

const resolvedSeries = computed(() => {
  if (props.series && props.series.length > 0) return props.series
  if (props.data && props.data.datasets) {
    return props.data.datasets.map(ds => ({
      name: ds.label,
      data: ds.data || [],
      color: ds.borderColor || ds.backgroundColor || 'var(--accent)'
    }))
  }
  return []
})

const resolvedLabels = computed(() => {
  if (props.labels && props.labels.length > 0) return props.labels
  if (props.data && props.data.labels) return props.data.labels
  return []
})

const allValues = computed(() => resolvedSeries.value.flatMap(s => s.data))
const minVal = computed(() => allValues.value.length ? Math.min(...allValues.value, 0) : 0)
const maxVal = computed(() => allValues.value.length ? Math.max(...allValues.value, 1) : 1)

function pointX(i, len) {
  return padL + (i / Math.max(len - 1, 1)) * innerW.value
}

function pointY(v) {
  return padT + innerH.value - ((v - minVal.value) / (maxVal.value - minVal.value || 1)) * innerH.value
}

function seriesPath(data) {
  if (!data || data.length === 0) return ''
  return data.map((v, i) => `${i === 0 ? 'M' : 'L'}${pointX(i, data.length)},${pointY(v)}`).join(' ')
}

function yLabel(i) {
  const range = maxVal.value - minVal.value
  const v = maxVal.value - (i / 3) * range
  if (v >= 1_000_000) return (v / 1_000_000).toFixed(1) + 'M'
  if (v >= 1_000) return (v / 1_000).toFixed(1) + 'k'
  return Math.round(v).toString()
}
</script>
