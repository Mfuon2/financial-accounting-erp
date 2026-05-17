<template>
  <div class="table-toolbar">
    <SearchField
      v-if="search !== undefined"
      :modelValue="search"
      @update:modelValue="$emit('update:search', $event)"
    />
    <slot />
  </div>
</template>

<script setup>
import SearchField from '@/components/primitives/SearchField.vue'

defineProps({
  search: { type: String },
})

defineEmits(['update:search'])
</script>
