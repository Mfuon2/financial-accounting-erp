<template>
  <div class="tl-row">
    <div class="tl-time">{{ time }}</div>
    <div class="tl-dot" />
    <div class="tl-body">
      <div>{{ body }}</div>
      <div v-if="actor" class="tl-actor">{{ actor }}</div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  time: { type: String },
  body: { type: String },
  actor: { type: String },
})
</script>
