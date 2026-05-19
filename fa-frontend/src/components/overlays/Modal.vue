<template>
  <Teleport to="body">
    <div v-if="open" class="scrim center" @click.self="$emit('close')">
      <div ref="modalEl" class="modal" :style="{ width: width + 'px' }">
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
import { ref, watch, nextTick, onUnmounted } from 'vue'
import IconBtn from '@/components/primitives/IconBtn.vue'

const props = defineProps({
  open: { type: Boolean },
  title: { type: String },
  subtitle: { type: String },
  width: { type: Number, default: 620 },
})

const emit = defineEmits(['close'])

const modalEl = ref(null)

const FOCUSABLE_SELECTORS = 'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

function focusableEls() {
  return modalEl.value ? [...modalEl.value.querySelectorAll(FOCUSABLE_SELECTORS)] : []
}

function onKey(e) {
  if (e.key === 'Escape') {
    if (document.querySelector('.ss-drop')) return
    emit('close')
    return
  }
  // Trap Tab within the modal so focus never reaches background elements
  if (e.key === 'Tab') {
    const els = focusableEls()
    if (!els.length) return
    const first = els[0]
    const last  = els[els.length - 1]
    if (e.shiftKey) {
      if (document.activeElement === first || !modalEl.value?.contains(document.activeElement)) {
        e.preventDefault()
        last.focus()
      }
    } else {
      if (document.activeElement === last || !modalEl.value?.contains(document.activeElement)) {
        e.preventDefault()
        first.focus()
      }
    }
  }
}

let previouslyFocused = null

watch(() => props.open, async (v) => {
  if (v) {
    previouslyFocused = document.activeElement
    document.addEventListener('keydown', onKey)
    await nextTick()
    // Move focus into the modal so background inputs don't receive keystrokes
    const els = focusableEls()
    if (els.length) els[0].focus()
  } else {
    document.removeEventListener('keydown', onKey)
    // Restore focus to wherever it was before the modal opened
    previouslyFocused?.focus?.()
    previouslyFocused = null
  }
}, { immediate: true })

onUnmounted(() => document.removeEventListener('keydown', onKey))
</script>
