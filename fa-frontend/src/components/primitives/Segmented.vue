<template>
  <div class="segmented">
    <div
      v-for="opt in options"
      :key="opt.value"
      :class="['seg', modelValue === opt.value && 'active']"
      @click="emit('update:modelValue', opt.value)"
    >
      {{ opt.label }}
    </div>
  </div>
</template>

<script setup>
defineProps({
  modelValue: { type: [String, Number] },
  options: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:modelValue'])
</script>
