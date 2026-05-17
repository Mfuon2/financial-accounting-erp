<template>
  <div class="tbl-search" :style="width ? { width: typeof width === 'number' ? width + 'px' : width } : undefined">
    <Ico name="search" :size="12" />
    <input
      :value="modelValue"
      :placeholder="placeholder"
      @input="emit('update:modelValue', $event.target.value)"
    />
    <span class="kbd">/</span>
  </div>
</template>

<script setup>
import Ico from './Ico.vue'

defineProps({
  modelValue: { type: String },
  placeholder: { type: String, default: 'Search…' },
  width: { type: [String, Number] },
})

const emit = defineEmits(['update:modelValue'])
</script>
