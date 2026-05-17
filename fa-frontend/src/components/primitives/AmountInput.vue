<template>
  <input
    v-bind="inputAttrs"
    type="text"
    inputmode="decimal"
    autocomplete="off"
    :value="displayValue"
    @focus="onFocus"
    @input="onInput"
    @blur="onBlur"
    @keydown="onKeydown"
  />
</template>

<script setup>
import { ref, computed, useAttrs } from 'vue'

defineOptions({ inheritAttrs: false })

const props = defineProps({
  modelValue: { default: null },
  placeholder: { type: String, default: '0.00' },
  disabled:    { type: Boolean, default: false },
  decimals:    { type: Number,  default: 2 },
})

const emit = defineEmits(['update:modelValue'])
const attrs = useAttrs()

// Forward every attr except the ones we own or that are meaningless for type=text
const inputAttrs = computed(() => {
  const { min, max, step, ...rest } = attrs
  return { ...rest, placeholder: props.placeholder, disabled: props.disabled }
})

const focused  = ref(false)
const editText = ref('')

function format(val) {
  if (val === null || val === undefined || val === '') return ''
  const n = Number(val)
  if (isNaN(n)) return ''
  return n.toLocaleString('en-US', {
    minimumFractionDigits:  props.decimals,
    maximumFractionDigits:  props.decimals,
  })
}

const displayValue = computed(() =>
  focused.value ? editText.value : format(props.modelValue)
)

function onFocus() {
  focused.value = true
  const v = props.modelValue
  editText.value = (v !== null && v !== undefined && v !== '') ? String(v) : ''
}

function onInput(e) {
  editText.value = e.target.value
  const parsed = parseFloat(editText.value)
  emit('update:modelValue', isNaN(parsed) ? null : parsed)
}

function onBlur() {
  focused.value = false
  const parsed = parseFloat(editText.value)
  emit('update:modelValue', isNaN(parsed) ? null : parsed)
}

// Only allow digits, one decimal point, and one leading minus
function onKeydown(e) {
  if (
    e.ctrlKey || e.metaKey ||
    ['Backspace','Delete','Tab','Enter','Escape',
     'ArrowLeft','ArrowRight','ArrowUp','ArrowDown','Home','End'].includes(e.key)
  ) return
  if (e.key === '-' && editText.value === '') return  // allow leading minus
  if (e.key === '.' && !editText.value.includes('.')) return  // one decimal point
  if (/^\d$/.test(e.key)) return
  e.preventDefault()
}
</script>
