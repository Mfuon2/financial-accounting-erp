import { reactive, watch } from 'vue'

const defaults = {
  theme: 'light',
  density: 'compact',
  accent: 'emerald',
  font: 'manrope',
  sidebar: 'labeled',
  tableStyle: 'zebra',
  showCommandHint: true,
}

const tweaks = reactive({ ...defaults })

function applyTweaks() {
  document.body.dataset.theme = tweaks.theme
  document.body.dataset.density = tweaks.density
  document.body.dataset.accent = tweaks.accent
  document.body.dataset.font = tweaks.font
  document.body.dataset.tableStyle = tweaks.tableStyle
}

export function useTweaks() {
  applyTweaks()
  watch(tweaks, applyTweaks, { deep: true })

  function setTweak(key, value) {
    tweaks[key] = value
  }

  return { tweaks, setTweak }
}
