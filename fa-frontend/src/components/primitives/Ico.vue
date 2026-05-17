<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 16 16"
    fill="none"
    stroke="currentColor"
    stroke-width="1.4"
    stroke-linecap="round"
    stroke-linejoin="round"
    v-bind="$attrs"
    v-html="paths"
  />
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  name: { type: String, required: true },
  size: { type: Number, default: 14 },
})

const iconMap = {
  search: '<circle cx="7" cy="7" r="4.5"/><path d="M13.5 13.5l-3-3"/>',
  filter: '<path d="M2 4h12M4 8h8M6 12h4"/>',
  sort: '<path d="M5 3v10M3 11l2 2 2-2M11 13V3M9 5l2-2 2 2"/>',
  plus: '<path d="M8 3v10M3 8h10"/>',
  chevron: '<path d="M5 3l4 5-4 5"/>',
  'chev-down': '<path d="M3 5l5 4 5-4"/>',
  'chev-right': '<path d="M5 3l4 5-4 5"/>',
  menu: '<path d="M2 4h12M2 8h12M2 12h12"/>',
  dots: '<circle cx="3" cy="8" r="1" fill="currentColor"/><circle cx="8" cy="8" r="1" fill="currentColor"/><circle cx="13" cy="8" r="1" fill="currentColor"/>',
  x: '<path d="M3 3l10 10M13 3L3 13"/>',
  check: '<path d="M2.5 8.5l3 3 8-8"/>',
  circle: '<circle cx="8" cy="8" r="6"/>',
  user: '<circle cx="8" cy="5.5" r="2.5"/><path d="M3 14c1-2.5 3-3.5 5-3.5s4 1 5 3.5"/>',
  users: '<circle cx="6" cy="5.5" r="2"/><path d="M2 13.5c.5-2 2-3 4-3s3.5 1 4 3"/><circle cx="11.5" cy="6" r="1.6"/><path d="M14.5 13c-.3-1.5-1.4-2.4-3-2.4"/>',
  key: '<circle cx="5" cy="11" r="2.5"/><path d="M6.8 9.2l5.7-5.7M10 6.5l1.5 1.5M11.5 5l1.5 1.5"/>',
  shield: '<path d="M8 2l5 1.5v4.2c0 3.4-2.3 5.7-5 6.3-2.7-.6-5-2.9-5-6.3V3.5L8 2z"/>',
  doc: '<path d="M4 1.5h5l3 3V14a.5.5 0 0 1-.5.5h-7A.5.5 0 0 1 4 14V2a.5.5 0 0 1 .5-.5z"/><path d="M9 1.5V4a.5.5 0 0 0 .5.5H12"/>',
  docs: '<path d="M3 4.5h6l2.5 2.5V13.5a.5.5 0 0 1-.5.5h-8A.5.5 0 0 1 3 13.5V5z"/><path d="M5 2.5h6l2.5 2.5"/>',
  ledger: '<rect x="2.5" y="2.5" width="11" height="11" rx="1"/><path d="M2.5 6h11M2.5 10h11M6 2.5v11"/>',
  journal: '<rect x="3" y="2" width="10" height="12" rx="1"/><path d="M5 5h6M5 8h6M5 11h4"/>',
  scale: '<path d="M8 2v12M3 5l3-1 3 1 3-1 3 1M5.5 4l-2 5a2 2 0 0 0 4 0l-2-5M10.5 4l-2 5a2 2 0 0 0 4 0l-2-5"/>',
  coin: '<circle cx="8" cy="8" r="6"/><path d="M8 4.5v7M5.5 6.5c.5-.7 1.4-1 2.5-1s2 .5 2 1.5-1 1.3-2 1.3-2 .3-2 1.3 1 1.5 2 1.5 2-.3 2.5-1"/>',
  bank: '<path d="M2 7h12M3 7l5-4 5 4M4 7v6M7 7v6M10 7v6M13 7v6M2 14h12"/>',
  card: '<rect x="2" y="4" width="12" height="9" rx="1.2"/><path d="M2 7h12"/>',
  'trend-up': '<path d="M2 12l4-4 3 3 5-5M9 6h4v4"/>',
  'trend-down': '<path d="M2 4l4 4 3-3 5 5M9 10h4V6"/>',
  bell: '<path d="M4 12V8a4 4 0 0 1 8 0v4M3 12h10M7 14h2"/>',
  clock: '<circle cx="8" cy="8" r="6"/><path d="M8 4.5V8l2 1.5"/>',
  calendar: '<rect x="2.5" y="3.5" width="11" height="10" rx="1"/><path d="M2.5 6.5h11M5 2v3M11 2v3"/>',
  tag: '<path d="M2 8.5V3.5A1 1 0 0 1 3 2.5h5l5.5 5.5a1 1 0 0 1 0 1.4l-4 4a1 1 0 0 1-1.4 0L2 8.5z"/><circle cx="5.5" cy="5.5" r=".7" fill="currentColor"/>',
  settings: '<circle cx="8" cy="8" r="2"/><path d="M8 1.5v2M8 12.5v2M1.5 8h2M12.5 8h2M3 3l1.5 1.5M11.5 11.5L13 13M3 13l1.5-1.5M11.5 4.5L13 3"/>',
  sliders: '<path d="M2 4h12M2 8h12M2 12h12"/><circle cx="5" cy="4" r="1.5" fill="var(--surface)"/><circle cx="10" cy="8" r="1.5" fill="var(--surface)"/><circle cx="7" cy="12" r="1.5" fill="var(--surface)"/>',
  globe: '<circle cx="8" cy="8" r="6"/><path d="M2 8h12M8 2c2 2 2 10 0 12M8 2c-2 2-2 10 0 12"/>',
  building: '<path d="M3 14V3h7v11M10 7h3v7M5 6h1M5 8.5h1M5 11h1M7.5 6h1M7.5 8.5h1M7.5 11h1"/>',
  truck: '<path d="M1.5 4.5h8v6h-8z M9.5 7h3l1.5 2v1.5h-4.5"/><circle cx="4" cy="12" r="1.3"/><circle cx="11.5" cy="12" r="1.3"/>',
  package: '<path d="M2.5 4.5L8 2l5.5 2.5L8 7 2.5 4.5z M2.5 4.5V11L8 13.5l5.5-2.5V4.5M8 7v6.5"/>',
  receipt: '<path d="M3.5 1.5v13L5 13l1.5 1.5L8 13l1.5 1.5L11 13l1.5 1.5v-13M5 4h6M5 6.5h6M5 9h4"/>',
  envelope: '<rect x="2" y="3.5" width="12" height="9" rx="1"/><path d="M2 4.5l6 4 6-4"/>',
  lock: '<rect x="3" y="7" width="10" height="6.5" rx="1"/><path d="M5 7V5a3 3 0 0 1 6 0v2"/>',
  external: '<path d="M6 3H3v10h10v-3M9 3h4v4M13 3L7.5 8.5"/>',
  download: '<path d="M8 2v9M4 7l4 4 4-4M2 14h12"/>',
  upload: '<path d="M8 11V2M4 6l4-4 4 4M2 14h12"/>',
  refresh: '<path d="M13 4.5A6 6 0 0 0 2.5 7M13 2v3h-3M3 11.5A6 6 0 0 0 13.5 9M3 14v-3h3"/>',
  play: '<path d="M4 3l8 5-8 5z" fill="currentColor"/>',
  pause: '<path d="M5 3h2v10H5zM9 3h2v10H9z" fill="currentColor"/>',
  approve: '<circle cx="8" cy="8" r="6"/><path d="M5 8l2 2 4-4"/>',
  reject: '<circle cx="8" cy="8" r="6"/><path d="M5.5 5.5l5 5M10.5 5.5l-5 5"/>',
  warn: '<path d="M8 2l6 11H2L8 2z"/><path d="M8 6.5v3M8 11v.5"/>',
  info: '<circle cx="8" cy="8" r="6"/><path d="M8 5.5h.01M8 7.5v3.5"/>',
  link: '<path d="M6.5 8.5l3-3M5.5 10.5L4 12a2.1 2.1 0 0 1-3-3l1.5-1.5M10.5 5.5L12 4a2.1 2.1 0 0 1 3 3l-1.5 1.5"/>',
  branch: '<circle cx="4" cy="3.5" r="1.5"/><circle cx="4" cy="12.5" r="1.5"/><circle cx="12" cy="6" r="1.5"/><path d="M4 5v6M4 8.5h4.5a3 3 0 0 0 3-3"/>',
  fa: '<path d="M2 13.5h12M3.5 13.5V9l4.5-3 4.5 3v4.5M6 13.5v-3h4v3"/>',
  asset: '<rect x="2" y="3" width="12" height="10" rx="1"/><path d="M5 6.5h6M5 9h6M5 11.5h3"/>',
  spark: '<path d="M1 12l3-4 3 2 3-5 3 3 2-2"/>',
  fx: '<path d="M2 6h6M5 3v6M9 13h5M9 10h5M11.5 8L9 10.5l2.5 2.5"/>',
  chart: '<path d="M2 14V2M2 14h12M5 11V8M8 11V5M11 11v-4"/>',
  command: '<path d="M5 5.5A1.5 1.5 0 1 1 6.5 7H5zM11 5.5A1.5 1.5 0 1 0 9.5 7H11zM5 10.5A1.5 1.5 0 1 0 6.5 9H5zM11 10.5A1.5 1.5 0 1 1 9.5 9H11zM6.5 7v2M9.5 7v2M6.5 7h3M6.5 9h3"/>',
  logout: '<path d="M6 2H2v12h4M10 4l4 4-4 4M14 8H6"/>',
  eye: '<path d="M1.5 8C3 4.5 5.5 3 8 3s5 1.5 6.5 5C13 11.5 10.5 13 8 13S3 11.5 1.5 8z"/><circle cx="8" cy="8" r="2"/>',
  rotate: '<path d="M2 8a6 6 0 1 0 1.5-4M2 3v4h4"/>',
  default: '<circle cx="8" cy="8" r="3"/>',
}

const paths = computed(() => iconMap[props.name] ?? iconMap.default)
</script>
