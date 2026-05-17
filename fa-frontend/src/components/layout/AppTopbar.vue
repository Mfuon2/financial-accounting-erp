<script setup>
import Ico from '@/components/primitives/Ico.vue'
import IconBtn from '@/components/primitives/IconBtn.vue'
import { useAppMode } from '@/composables/useAppMode.js'

const props = defineProps({
  crumbs: { type: Array, default: () => [] },
  currentPeriod: { type: Object, default: null },
})

const emit = defineEmits(['command', 'tweak', 'sidebar-toggle', 'logout', 'notifications'])

const { isDemo } = useAppMode()
</script>

<template>
  <header class="topbar">
    <IconBtn icon="menu" title="Toggle sidebar" @click="emit('sidebar-toggle')"/>
    <div class="crumbs">
      <template v-for="(crumb, i) in crumbs" :key="i">
        <span v-if="i > 0" class="crumb-sep">/</span>
        <span :class="i === crumbs.length - 1 ? 'crumb-current' : ''">{{ crumb }}</span>
      </template>
    </div>
    <div class="spacer"/>
    <div
      v-if="currentPeriod"
      :class="['period-pill', currentPeriod.status.toLowerCase()]"
      title="Current period"
    >
      <span class="p-status"/>
      <code>{{ currentPeriod.code }}</code>
      <span class="muted">{{ currentPeriod.status }}</span>
    </div>
    <div :class="['mode-pill', isDemo ? 'mode-pill--demo' : 'mode-pill--live']" title="Environment mode">
      <span class="mode-dot"/>
      {{ isDemo ? 'DEMO' : 'LIVE' }}
    </div>
    <button class="cmd-trigger" @click="emit('command')">
      <Ico name="search" :size="12"/>
      <span>Search or run a command…</span>
      <span class="kbd">⌘K</span>
    </button>
    <div class="topbar-icons">
      <IconBtn icon="bell" title="Notifications" :hasDot="true" @click="emit('notifications')"/>
      <IconBtn icon="sliders" title="Tweaks panel" @click="emit('tweak')"/>
      <IconBtn icon="logout" title="Log out" @click="emit('logout')"/>
    </div>
  </header>
</template>

<style scoped>
.mode-pill {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.06em;
  padding: 3px 8px;
  border-radius: 4px;
  user-select: none;
}
.mode-pill--demo {
  background: color-mix(in oklab, oklch(0.75 0.18 55) 14%, transparent);
  color: oklch(0.55 0.18 55);
}
.mode-pill--live {
  background: color-mix(in oklab, oklch(0.55 0.18 145) 14%, transparent);
  color: oklch(0.45 0.18 145);
}
.mode-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}
</style>
