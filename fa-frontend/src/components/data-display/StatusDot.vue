<template>
  <span
    class="dot"
    :style="{
      background: color,
      boxShadow: glow ? `0 0 4px 1px ${color}` : 'none',
    }"
  />
</template>

<script setup>
defineProps({
  color: { type: String, default: 'var(--accent)' },
  glow: { type: Boolean, default: true },
})
</script>
