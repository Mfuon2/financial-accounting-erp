<script setup>
const props = defineProps({
  title: { type: String },
  meta: { type: String },
  tabs: { type: Array, default: () => [] },
  activeTab: { type: String },
})

const emit = defineEmits(['tab'])
</script>

<template>
  <div class="subtopbar">
    <div>
      <div class="page-title">{{ title }}</div>
      <div v-if="meta" class="page-meta">{{ meta }}</div>
    </div>
    <div v-if="tabs && tabs.length" class="tabs">
      <div
        v-for="tab in tabs"
        :key="tab.id"
        class="tab"
        :class="{ active: activeTab === tab.id }"
        @click="emit('tab', tab.id)"
      >
        {{ tab.label }}
        <span v-if="tab.count != null" class="badge-mini">{{ tab.count }}</span>
      </div>
    </div>
    <div class="spacer"/>
    <slot/>
  </div>
</template>
