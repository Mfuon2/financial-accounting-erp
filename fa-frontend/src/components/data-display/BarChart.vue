<template>
  <svg :width="w" :height="h + 14">
    <rect
      v-for="(v, i) in data"
      :key="i"
      :x="i * bw + 2"
      :y="h - barH(v)"
      :width="bw - 4"
      :height="barH(v)"
      :fill="color"
      rx="2"
    />
    <text
      v-for="(lbl, i) in labels"
      :key="'l' + i"
      :x="i * bw + bw / 2"
      :y="h + 11"
      font-size="9"
      fill="var(--muted)"
      text-anchor="middle"
      :font-family="'var(--font-mono)'"
    >{{ lbl }}</text>
  </svg>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  data: { type: Array, default: () => [] },
  w: { type: Number, default: 280 },
  h: { type: Number, default: 90 },
  color: { type: String, default: 'var(--accent)' },
  labels: { type: Array, default: () => [] },
})

const bw = computed(() => (props.w - 8) / (props.data.length || 1))
const maxVal = computed(() => props.data.length ? Math.max(...props.data, 1) : 1)

function barH(v) {
  return (v / maxVal.value) * props.h
}
</script>
