<template>
  <button
    type="button"
    :class="['icon-btn', hasDot && 'has-dot']"
    :title="title"
    @click="emit('click', $event)"
  >
    <Ico :name="icon" :size="14" />
  </button>
</template>

<script setup>
import Ico from './Ico.vue'

defineProps({
  icon: { type: String },
  title: { type: String },
  hasDot: { type: Boolean, default: false },
})

const emit = defineEmits(['click'])
</script>
