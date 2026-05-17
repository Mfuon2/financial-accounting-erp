<template>
  <div class="t-acct">
    <div class="t-acct-head">{{ accountCode }} — {{ accountName }}</div>
    <div style="display:flex">
      <div class="t-side">
        <div class="t-side-label">Dr</div>
        <div v-for="(line, i) in drLines" :key="i" class="t-line">
          <span>{{ line.date }}</span>
          <span>{{ line.ref }}</span>
          <span>{{ fmt(line.amount) }}</span>
        </div>
        <div class="t-line t-total">
          <span></span>
          <span>Total</span>
          <span>{{ fmt(drTotal) }}</span>
        </div>
      </div>
      <div class="t-side">
        <div class="t-side-label">Cr</div>
        <div v-for="(line, i) in crLines" :key="i" class="t-line">
          <span>{{ line.date }}</span>
          <span>{{ line.ref }}</span>
          <span>{{ fmt(line.amount) }}</span>
        </div>
        <div class="t-line t-total">
          <span></span>
          <span>Total</span>
          <span>{{ fmt(crTotal) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { fmt } from '@/utils/format.js'

const props = defineProps({
  accountCode: { type: String },
  accountName: { type: String },
  drLines: { type: Array, default: () => [] },
  crLines: { type: Array, default: () => [] },
})

const drTotal = computed(() => props.drLines.reduce((s, l) => s + l.amount, 0))
const crTotal = computed(() => props.crLines.reduce((s, l) => s + l.amount, 0))
</script>
