import { ref, computed } from 'vue'

const _count = ref(0)

export const isLoading = computed(() => _count.value > 0)

export function useLoading() {
  function start() {
    _count.value++
  }

  function stop() {
    _count.value = Math.max(0, _count.value - 1)
  }

  return { isLoading, start, stop }
}
