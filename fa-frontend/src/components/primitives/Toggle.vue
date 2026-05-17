<template>
  <label class="toggle" :data-on="modelValue || undefined">
    <span class="toggle-track" @click="emit('update:modelValue', !modelValue)" />
    <span v-if="label" class="toggle-label">{{ label }}</span>
  </label>
</template>

<script setup>
defineProps({
  modelValue: { type: Boolean },
  label: { type: String },
})

const emit = defineEmits(['update:modelValue'])
</script>
