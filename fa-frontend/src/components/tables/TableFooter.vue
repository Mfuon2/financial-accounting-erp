<template>
  <div class="tbl-footer">
    <div class="tbl-footer-info">
      {{ start }}–{{ end }} of {{ total }} {{ label }}
    </div>
    <div class="pg-spacer" />
    <div class="tbl-footer-size">
      <SearchableSelect
        v-model="pageSize"
        :options="sizes.map(s => ({ value: s, label: String(s) }))"
        :compact="true"
        @update:modelValue="page = 1"
      />
    </div>
    <div class="tbl-footer-pages">
      <button :disabled="page === 1" @click="page = 1">
        <Ico name="chev-right" :size="10" style="transform:rotate(180deg) scaleX(2)" />
      </button>
      <button :disabled="page === 1" @click="page--">
        <Ico name="chev-right" :size="11" style="transform:rotate(180deg)" />
      </button>
      <span class="pg-indicator">{{ page }} / {{ pages }}</span>
      <button :disabled="page === pages" @click="page++">
        <Ico name="chev-right" :size="11" />
      </button>
      <button :disabled="page === pages" @click="page = pages">
        <Ico name="chev-right" :size="10" style="transform:scaleX(2)" />
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import Ico from '@/components/primitives/Ico.vue'
import SearchableSelect from '@/components/primitives/SearchableSelect.vue'

const props = defineProps({
  total: { type: Number, default: 0 },
  label: { type: String, default: 'rows' },
  defaultSize: { type: Number, default: 25 },
  sizes: { type: Array, default: () => [10, 25, 50, 100] },
})

const pageSize = ref(props.defaultSize)
const page = ref(1)

const pages = computed(() => Math.max(1, Math.ceil(props.total / pageSize.value)))
const start = computed(() => props.total === 0 ? 0 : (page.value - 1) * pageSize.value + 1)
const end = computed(() => Math.min(page.value * pageSize.value, props.total))
</script>
