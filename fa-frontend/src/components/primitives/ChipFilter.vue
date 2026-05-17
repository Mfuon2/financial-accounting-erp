<template>
  <div :class="['chip-filter', active && 'active']" @click="emit('click', $event)">
    <slot />
    <Ico v-if="removable" name="x" :size="10" />
  </div>
</template>

<script setup>
import Ico from './Ico.vue'

defineProps({
  active: { type: Boolean },
  removable: { type: Boolean, default: false },
})

const emit = defineEmits(['click'])
</script>
