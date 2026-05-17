<script setup>
import { computed } from 'vue'
import Ico from '@/components/primitives/Ico.vue'

const props = defineProps({
  steps: { type: Array, required: true },
  current: { type: String, required: true },
})

const currentIdx = computed(() => props.steps.findIndex(s => s.id === props.current))
</script>

<template>
  <div class="stepper">
    <template v-for="(step, i) in steps" :key="step.id">
      <div
        class="step"
        :class="{ done: i < currentIdx, active: i === currentIdx }"
      >
        <div class="step-dot">
          <Ico v-if="i < currentIdx" name="check" :size="10"/>
          <span v-else>{{ i + 1 }}</span>
        </div>
        <span class="step-label">{{ step.label }}</span>
      </div>
      <div
        v-if="i < steps.length - 1"
        class="step-line"
        :class="{ done: i < currentIdx }"
      />
    </template>
  </div>
</template>
