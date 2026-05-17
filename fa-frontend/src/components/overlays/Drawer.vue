<template>
  <Teleport to="body">
    <div v-if="open" class="scrim" @click.self="$emit('close')">
      <div :class="`drawer ${size}`">
        <div class="drawer-head">
          <div>
            <div v-if="title" class="drawer-title">{{ title }}</div>
            <div v-if="subtitle" class="drawer-subtitle">{{ subtitle }}</div>
          </div>
          <IconBtn name="x" @click="$emit('close')" />
        </div>
        <div class="drawer-body">
          <slot />
        </div>
        <div v-if="$slots.footer" class="drawer-foot">
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
  size: { type: String, default: 'md' },
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
