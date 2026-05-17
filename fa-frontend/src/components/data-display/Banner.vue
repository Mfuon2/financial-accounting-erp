<template>
  <div :class="`banner ${kind}`">
    <Ico :name="icon" :size="13" class="b-ico" />
    <div style="flex:1"><slot /></div>
    <slot name="action" />
  </div>
</template>

<script setup>
import Ico from '@/components/primitives/Ico.vue'

defineProps({
  kind: { type: String, default: 'info' },
  icon: { type: String, default: 'info' },
})
</script>
