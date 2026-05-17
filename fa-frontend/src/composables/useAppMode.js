import { ref, computed, readonly } from 'vue'

const _mode = ref(localStorage.getItem('qs_mode') ?? 'demo')

const isDemo = computed(() => _mode.value === 'demo')
const isProd = computed(() => _mode.value === 'production')

export function useAppMode() {
  function setMode(m) {
    _mode.value = m
    localStorage.setItem('qs_mode', m)
  }
  return { mode: readonly(_mode), isDemo, isProd, setMode }
}

export { isDemo, isProd }
