<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuth } from '@/composables/useAuth.js'
import AppSidebar from '@/components/layout/AppSidebar.vue'
import AppTopbar from '@/components/layout/AppTopbar.vue'
import LoadingBar from '@/components/layout/LoadingBar.vue'
import CommandPalette from '@/components/overlays/CommandPalette.vue'
import ToastStack from '@/components/overlays/ToastStack.vue'
import TweaksPanel from '@/components/TweaksPanel.vue'
import { useTweaks } from '@/composables/useTweaks.js'
import { useAppMode } from '@/composables/useAppMode.js'
import { useActivePeriod } from '@/composables/useActivePeriod.js'
import { useOrganization } from '@/composables/useOrganization.js'
import { useSetupChecks } from '@/composables/useSetupChecks.js'

const router = useRouter()
const route  = useRoute()
const { logout, currentUser } = useAuth()
const { tweaks, setTweak } = useTweaks()
const { mode } = useAppMode()
const { runChecks } = useSetupChecks()
const { activePeriod, load: loadPeriod } = useActivePeriod()
const { load: loadOrg } = useOrganization()

const paletteOpen = ref(false)

const CRUMBS = {
  '/dashboard':     ['Overview', 'Dashboard'],
  '/approvals':     ['Overview', 'Approvals'],
  '/coa':           ['Ledger', 'Chart of Accounts'],
  '/periods':       ['Ledger', 'Periods'],
  '/journals':      ['Ledger', 'Journal Entries'],
  '/source-docs':   ['Ledger', 'Source Documents'],
  '/customers':     ['Parties', 'Customers'],
  '/suppliers':     ['Parties', 'Suppliers'],
  '/assets':        ['Assets', 'Fixed Asset Register'],
  '/depreciation':  ['Assets', 'Depreciation Run'],
  '/invoices':      ['Revenue', 'Invoices'],
  '/credit-notes':  ['Revenue', 'Credit Notes'],
  '/payments':      ['Revenue', 'Payments'],
  '/receipts':      ['Revenue', 'Receipts'],
  '/ar-ageing':     ['Revenue', 'AR Ageing'],
  '/trial-balance': ['Period-End', 'Trial Balance'],
  '/period-end':    ['Period-End', 'Workflow'],
  '/fx':            ['Period-End', 'FX Revaluation'],
  '/pnl':           ['Statements', 'Profit & Loss'],
  '/balance-sheet': ['Statements', 'Balance Sheet'],
  '/cash-flow':     ['Statements', 'Cash Flow'],
  '/close':         ['Statements', 'Close Period'],
  '/t-account':     ['Reports', 'T-Account'],
  '/sub-ledger':    ['Reports', 'Sub-Ledgers'],
  '/audit':         ['Reports', 'Audit Trail'],
  '/ias1':          ['Reports', 'IAS 1 Compliance'],
  '/comparative':   ['Reports', 'Comparative TB'],
  '/profile':       ['Setup', 'My Profile'],
  '/organization':  ['Setup', 'Organization'],
  '/users':         ['Setup', 'Users'],
  '/api-keys':      ['Setup', 'API Keys'],
  '/tax':           ['Setup', 'Tax & Currency'],
  '/security':      ['Setup', 'Security'],
}

const PALETTE_ITEMS = [
  { icon: 'chart',      label: 'Dashboard',             route: '/dashboard',    group: 'Navigate', tags: ['Overview'] },
  { icon: 'approve',    label: 'Approvals',             route: '/approvals',    group: 'Navigate', tags: ['Overview'] },
  { icon: 'ledger',     label: 'Chart of Accounts',     route: '/coa',          group: 'Navigate', tags: ['Ledger'] },
  { icon: 'calendar',   label: 'Periods',               route: '/periods',      group: 'Navigate', tags: ['Ledger'] },
  { icon: 'journal',    label: 'Journal Entries',       route: '/journals',     group: 'Navigate', tags: ['Ledger'] },
  { icon: 'docs',       label: 'Source Documents',      route: '/source-docs',  group: 'Navigate', tags: ['Ledger'] },
  { icon: 'users',      label: 'Customers',             route: '/customers',    group: 'Navigate', tags: ['Parties'] },
  { icon: 'truck',      label: 'Suppliers',             route: '/suppliers',    group: 'Navigate', tags: ['Parties'] },
  { icon: 'asset',      label: 'Fixed Assets',          route: '/assets',       group: 'Navigate', tags: ['Assets'] },
  { icon: 'trend-down', label: 'Depreciation Run',      route: '/depreciation', group: 'Navigate', tags: ['Assets'] },
  { icon: 'doc',        label: 'Invoices',              route: '/invoices',     group: 'Navigate', tags: ['Revenue'] },
  { icon: 'receipt',    label: 'Credit Notes',          route: '/credit-notes', group: 'Navigate', tags: ['Revenue'] },
  { icon: 'card',       label: 'Payments',              route: '/payments',     group: 'Navigate', tags: ['Revenue'] },
  { icon: 'receipt',    label: 'Receipts',              route: '/receipts',     group: 'Navigate', tags: ['Revenue'] },
  { icon: 'clock',      label: 'AR Ageing',             route: '/ar-ageing',    group: 'Navigate', tags: ['Revenue'] },
  { icon: 'scale',      label: 'Trial Balance',         route: '/trial-balance',group: 'Navigate', tags: ['Period-End'] },
  { icon: 'branch',     label: 'Period-End Tasks',      route: '/period-end',   group: 'Navigate', tags: ['Period-End'] },
  { icon: 'fx',         label: 'FX Revaluation',        route: '/fx',           group: 'Navigate', tags: ['Period-End'] },
  { icon: 'trend-up',   label: 'Profit & Loss',         route: '/pnl',          group: 'Navigate', tags: ['Statements'] },
  { icon: 'scale',      label: 'Balance Sheet',         route: '/balance-sheet',group: 'Navigate', tags: ['Statements'] },
  { icon: 'coin',       label: 'Cash Flow',             route: '/cash-flow',    group: 'Navigate', tags: ['Statements'] },
  { icon: 'lock',       label: 'Close Period',          route: '/close',        group: 'Navigate', tags: ['Statements'] },
  { icon: 'ledger',     label: 'T-Account View',        route: '/t-account',    group: 'Navigate', tags: ['Reports'] },
  { icon: 'branch',     label: 'Sub-Ledgers',           route: '/sub-ledger',   group: 'Navigate', tags: ['Reports'] },
  { icon: 'shield',     label: 'Audit Trail',           route: '/audit',        group: 'Navigate', tags: ['Reports'] },
  { icon: 'check',      label: 'IAS 1 Compliance',      route: '/ias1',         group: 'Navigate', tags: ['Reports'] },
  { icon: 'scale',      label: 'Comparative TB',        route: '/comparative',  group: 'Navigate', tags: ['Reports'] },
  { icon: 'building',   label: 'Organization',          route: '/organization', group: 'Navigate', tags: ['Setup'] },
  { icon: 'users',      label: 'Users',                 route: '/users',        group: 'Navigate', tags: ['Setup'] },
  { icon: 'key',        label: 'API Keys',              route: '/api-keys',     group: 'Navigate', tags: ['Setup'] },
  { icon: 'tag',        label: 'Tax & Currency',        route: '/tax',          group: 'Navigate', tags: ['Setup'] },
  { icon: 'shield',     label: 'Security',              route: '/security',     group: 'Navigate', tags: ['Setup'] },
  { icon: 'plus',       label: 'New journal entry',     route: '/journals',     group: 'Quick actions', meta: 'J' },
  { icon: 'plus',       label: 'New invoice',           route: '/invoices',     group: 'Quick actions', meta: 'I' },
  { icon: 'card',       label: 'Record a payment',      route: '/payments',     group: 'Quick actions', meta: 'P' },
  { icon: 'play',       label: 'Run depreciation batch',route: '/depreciation', group: 'Quick actions' },
  { icon: 'fx',         label: 'Run FX revaluation',    route: '/fx',           group: 'Quick actions' },
  { icon: 'lock',       label: 'Close period',          route: '/close',        group: 'Quick actions' },
]

const currentPath = computed(() => route.path)
const crumbs = computed(() => CRUMBS[currentPath.value] || ['Dashboard'])
const currentPeriod = activePeriod
const entityId = computed(() => currentUser.value?.entityId ?? null)
watch(entityId, (id) => {
  if (id) {
    runChecks(id)
    loadPeriod()
    loadOrg()
  }
}, { immediate: true })

function navigate(path) {
  router.push(path)
}

function handleSidebarToggle() {
  setTweak('sidebar', tweaks.sidebar === 'iconly' ? 'labeled' : 'iconly')
}

async function handleLogout() {
  await logout()
  router.push('/login')
}

function handleNotifications() {
  router.push('/approvals')
}

function activateTweaks() {
  window.postMessage({ type: '__activate_edit_mode' }, '*')
}

onMounted(() => {
  const h = (e) => {
    if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
      e.preventDefault()
      paletteOpen.value = true
    }
    if (e.key === '/' && document.activeElement === document.body) {
      e.preventDefault()
      paletteOpen.value = true
    }
  }
  window.addEventListener('keydown', h)
})
</script>

<template>
  <div class="app" :data-sidebar="tweaks.sidebar" :data-table-style="tweaks.tableStyle">
    <AppSidebar
      :activeRoute="currentPath"
      :tweaks="tweaks"
      @navigate="navigate"
    />
    <AppTopbar
      :crumbs="crumbs"
      :currentPeriod="currentPeriod"
      @command="paletteOpen = true"
      @tweak="activateTweaks"
      @sidebar-toggle="handleSidebarToggle"
      @logout="handleLogout"
      @notifications="handleNotifications"
    />
    <main class="main">
      <router-view :key="mode"/>
    </main>
    <CommandPalette
      :open="paletteOpen"
      :routes="PALETTE_ITEMS"
      @close="paletteOpen = false"
      @navigate="(r) => { navigate(r); paletteOpen = false }"
    />
    <TweaksPanel
      :tweaks="tweaks"
      @update:tweaks="(t) => Object.entries(t).forEach(([k, v]) => setTweak(k, v))"
    />
    <LoadingBar />
    <ToastStack />
  </div>
</template>
