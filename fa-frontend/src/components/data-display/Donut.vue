<template>
  <svg :width="size" :height="size">
    <circle
      :cx="c"
      :cy="c"
      :r="r"
      fill="none"
      stroke="var(--border)"
      :stroke-width="thickness"
    />
    <circle
      v-for="(seg, i) in segmentProps"
      :key="i"
      :cx="c"
      :cy="c"
      :r="r"
      fill="none"
      :stroke="seg.color"
      :stroke-width="thickness"
      :stroke-dasharray="seg.dasharray"
      :stroke-dashoffset="seg.dashoffset"
      :transform="`rotate(-90 ${c} ${c})`"
      stroke-linecap="butt"
    />
  </svg>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  segments: { type: Array, default: () => [] },
  data: { type: Object },
  size: { type: Number, default: 110 },
  thickness: { type: Number, default: 14 },
})

const r = computed(() => (props.size - props.thickness) / 2)
const c = computed(() => props.size / 2)
const circ = computed(() => 2 * Math.PI * r.value)

const resolvedSegments = computed(() => {
  if (props.segments && props.segments.length > 0) {
    return props.segments
  }
  
  if (props.data && props.data.datasets && props.data.datasets[0]) {
    const dataset = props.data.datasets[0]
    const colors = ['var(--accent)', 'var(--pos)', 'var(--warn)', 'var(--neg)', 'var(--info)', 'var(--muted-status)']
    return (dataset.data || []).map((val, i) => ({
      value: val,
      color: colors[i % colors.length]
    }))
  }
  
  return []
})

const total = computed(() => resolvedSegments.value.reduce((s, seg) => s + (seg.value || 0), 0))

const segmentProps = computed(() => {
  let offset = 0
  return resolvedSegments.value.map(seg => {
    const pct = (seg.value || 0) / (total.value || 1)
    const dash = pct * circ.value
    const result = {
      color: seg.color || 'var(--border)',
      dasharray: `${dash} ${circ.value - dash}`,
      dashoffset: -offset,
    }
    offset += dash
    return result
  })
})
</script>
