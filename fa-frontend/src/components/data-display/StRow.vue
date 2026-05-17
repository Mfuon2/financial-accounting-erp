<template>
  <div :class="type ? `st-row ${type}` : `st-row indent-${indent}`">
    <div class="st-label">{{ label }}</div>
    <template v-if="type !== 'section'">
      <div class="st-num">{{ fmt(current, { decimals: 0, parens: true }) }}</div>
      <div class="st-num">{{ fmt(prior, { decimals: 0, parens: true }) }}</div>
    </template>
  </div>
</template>

<script setup>
import { fmt } from '@/utils/format.js'

defineProps({
  type: { type: String },
  label: { type: String },
  indent: { type: Number, default: 0 },
  current: { type: Number },
  prior: { type: Number },
})
</script>
