<template>
  <div class="kpi">
    <div class="kpi-label">
      <Ico v-if="icon" :name="icon" />
      <span>{{ label }}</span>
    </div>
    <div class="kpi-value">
      {{ fmt(value) }}<span v-if="unit" class="kpi-unit">{{ unit }}</span>
    </div>
    <div v-if="delta != null" class="kpi-delta" :style="{ color: delta >= 0 ? 'var(--pos)' : 'var(--neg)' }">
      <Ico :name="delta >= 0 ? 'trend-up' : 'trend-down'" :size="12" />
      {{ delta >= 0 ? '+' : '' }}{{ delta }}%
      <span v-if="deltaLabel" class="kpi-delta-label">{{ deltaLabel }}</span>
    </div>
    <div v-if="spark && spark.length" class="kpi-spark">
      <Sparkline :data="spark" :stroke="sparkColor || 'var(--accent)'" :fill="sparkColor ? sparkColor + '33' : 'var(--accent-soft)'" />
    </div>
  </div>
</template>

<script setup>
import Ico from '@/components/primitives/Ico.vue'
import Sparkline from '@/components/data-display/Sparkline.vue'
import { fmt } from '@/utils/format.js'

defineProps({
  label: { type: String },
  icon: { type: String },
  value: {},
  unit: { type: String },
  delta: { type: Number },
  deltaLabel: { type: String },
  spark: { type: Array },
  sparkColor: { type: String },
})
</script>
