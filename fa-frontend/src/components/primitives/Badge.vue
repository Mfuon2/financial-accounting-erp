<template>
  <span :class="classes">
    <slot>{{ displayLabel }}</slot>
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: { type: String },
  dot: { type: Boolean, default: true },
})

const classes = computed(() => {
  const parts = ['badge']
  if (props.dot) parts.push('dot')
  if (props.status) parts.push(props.status.toLowerCase().replace(/_/g, '-'))
  return parts
})

const displayLabel = computed(() => {
  if (!props.status) return ''
  return props.status
    .replace(/_/g, ' ')
    .toLowerCase()
    .replace(/\b\w/g, c => c.toUpperCase())
})
</script>
