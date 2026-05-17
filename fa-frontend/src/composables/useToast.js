import { ref } from 'vue'

const toasts = ref([])
let _id = 0

function add(message, type = 'info', duration = 4000) {
  const id = ++_id
  toasts.value.push({ id, message, type, duration })
  if (duration > 0) setTimeout(() => remove(id), duration)
  return id
}

function remove(id) {
  const i = toasts.value.findIndex(t => t.id === id)
  if (i !== -1) toasts.value.splice(i, 1)
}

const toast = {
  success: (msg, dur = 5000) => add(msg, 'success', dur),
  error:   (msg, dur = 5000) => add(msg, 'error',   dur),
  warn:    (msg, dur = 5000) => add(msg, 'warn',    dur),
  info:    (msg, dur = 5000) => add(msg, 'info',    dur),
}

export function useToast() {
  return { toasts, toast, remove }
}
