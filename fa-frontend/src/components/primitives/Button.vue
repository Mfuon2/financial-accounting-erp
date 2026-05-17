<template>
  <button :type="type" :class="classes" :disabled="disabled || loading" @click="emit('click', $event)">
    <Ico v-if="loading" name="rotate" :size="size === 'sm' ? 12 : 14" class="btn-spin" />
    <Ico v-else-if="icon" :name="icon" :size="size === 'sm' ? 12 : 14" />
    <slot />
  </button>
</template>

<script setup>
import { computed } from 'vue'
import Ico from './Ico.vue'

const props = defineProps({
  variant: { type: String, default: 'default' },
  size:    { type: String, default: 'md' },
  icon:    { type: String },
  disabled:{ type: Boolean },
  loading: { type: Boolean, default: false },
  type:    { type: String, default: 'button' },
})

const emit = defineEmits(['click'])

const classes = computed(() => {
  const parts = ['btn']
  if (props.variant && props.variant !== 'default') parts.push(props.variant)
  if (props.size && props.size !== 'md') parts.push(props.size)
  if (props.loading) parts.push('loading')
  return parts
})
</script>

<style scoped>
@keyframes spin { to { transform: rotate(360deg); } }
.btn-spin { animation: spin 0.7s linear infinite; }
</style>
