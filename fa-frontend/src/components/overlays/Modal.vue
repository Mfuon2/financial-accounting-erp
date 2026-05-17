<template>
  <Teleport to="body">
    <div v-if="open" class="scrim center" @click.self="$emit('close')">
      <div class="modal" :style="{ width: width + 'px' }">
        <div class="modal-head">
          <div class="modal-title-block">
            <div class="modal-title">{{ title }}</div>
            <div v-if="subtitle" class="modal-subtitle">{{ subtitle }}</div>
          </div>
          <IconBtn icon="x" @click="$emit('close')" />
        </div>
        <div class="modal-body">
          <slot />
        </div>
        <div v-if="$slots.footer" class="modal-foot">
          <slot name="footer" />
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { watch, onUnmounted } from 'vue'
import IconBtn from '@/components/primitives/IconBtn.vue'

const props = defineProps({
  open: { type: Boolean },
  title: { type: String },
  subtitle: { type: String },
  width: { type: Number, default: 620 },
})

const emit = defineEmits(['close'])

function onKey(e) {
  if (e.key === 'Escape') emit('close')
}

watch(() => props.open, (v) => {
  if (v) document.addEventListener('keydown', onKey)
  else document.removeEventListener('keydown', onKey)
}, { immediate: true })

onUnmounted(() => document.removeEventListener('keydown', onKey))
</script>
