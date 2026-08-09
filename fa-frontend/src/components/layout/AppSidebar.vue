<script setup>
import Ico from '@/components/primitives/Ico.vue'
import IconBtn from '@/components/primitives/IconBtn.vue'
import { computed } from 'vue'
import { useAuth } from '@/composables/useAuth.js'
import { useSetupChecks } from '@/composables/useSetupChecks.js'
import { useOrganization } from '@/composables/useOrganization.js'
import { useActivePeriod } from '@/composables/useActivePeriod.js'
const { totalIssues } = useSetupChecks()
const { org } = useOrganization()
const { activePeriod } = useActivePeriod()

const props = defineProps({
  activeRoute: { type: String },
  tweaks: { type: Object },
})

const emit = defineEmits(['navigate', 'update:tweaks'])

const { currentUser } = useAuth()

const userInitials = computed(() => {
  const name = currentUser.value?.fullName ?? ''
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length >= 2) return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
  return name.slice(0, 2).toUpperCase() || '?'
})

const userDisplayName = computed(() => currentUser.value?.fullName ?? 'User')
const userRole = computed(() => (currentUser.value?.role ?? '').replace(/_/g, ' '))

const orgName = computed(() => org.value?.name ?? 'Loading…')
const orgMeta = computed(() => {
  const reg = org.value?.registrationNumber
  const shortId = reg ?? (org.value?.id ? String(org.value.id).slice(0, 8).toUpperCase() : '—')
  const fy = activePeriod.value?.startDate?.slice(0, 4) ?? new Date().getFullYear()
  const ccy = org.value?.functionalCurrency ?? '—'
  return `${shortId} · FY ${fy} · ${ccy}`
})

const NAV_ROUTES = [
  { id: "dashboard",    label: "Dashboard",         icon: "chart",      route: "/dashboard",    group: "Overview" },
  { id: "approvals",    label: "Approvals",         icon: "approve",    route: "/approvals",    group: "Overview" },
  { id: "coa",          label: "Chart of Accounts", icon: "ledger",     route: "/coa",          group: "Ledger" },
  { id: "periods",      label: "Periods",           icon: "calendar",   route: "/periods",      group: "Ledger" },
  { id: "journals",     label: "Journal Entries",   icon: "journal",    route: "/journals",     group: "Ledger" },
  { id: "source-docs",  label: "Source Documents",  icon: "docs",       route: "/source-docs",  group: "Ledger" },
  { id: "customers",    label: "Customers",         icon: "users",      route: "/customers",    group: "Parties" },
  { id: "suppliers",    label: "Suppliers",         icon: "truck",      route: "/suppliers",    group: "Parties" },
  { id: "assets",       label: "Fixed Assets",      icon: "asset",      route: "/assets",       group: "Assets" },
  { id: "depreciation", label: "Depreciation Run",  icon: "trend-down", route: "/depreciation", group: "Assets" },
  { id: "budgets",      label: "Budgets",           icon: "chart",      route: "/budgets",      group: "Budgeting" },
  { id: "bank-reconciliation", label: "Reconciliation", icon: "bank",  route: "/bank-reconciliation", group: "Banking" },
  { id: "bills",        label: "Vendor Bills",      icon: "doc",        route: "/bills",         group: "Payables" },
  { id: "ap-ageing",   label: "AP Ageing",         icon: "clock",      route: "/ap-ageing",    group: "Payables" },
  { id: "invoices",    label: "Invoices",          icon: "doc",        route: "/invoices",     group: "Revenue" },
  { id: "credit-notes", label: "Credit Notes",      icon: "receipt",    route: "/credit-notes", group: "Revenue" },
  { id: "payments",     label: "Payments",          icon: "card",       route: "/payments",     group: "Revenue" },
  { id: "receipts",     label: "Receipts",          icon: "receipt",    route: "/receipts",     group: "Revenue" },
  { id: "ar-ageing",    label: "AR Ageing",         icon: "clock",      route: "/ar-ageing",    group: "Revenue" },
  { id: "trial-balance",label: "Trial Balance",     icon: "scale",      route: "/trial-balance",group: "Period-End" },
  { id: "period-end",   label: "Period-End Tasks",  icon: "branch",     route: "/period-end",   group: "Period-End" },
  { id: "fx",           label: "FX Revaluation",    icon: "fx",         route: "/fx",           group: "Period-End" },
  { id: "pnl",          label: "Profit & Loss",     icon: "trend-up",   route: "/pnl",          group: "Statements" },
  { id: "balance-sheet",label: "Balance Sheet",     icon: "scale",      route: "/balance-sheet",group: "Statements" },
  { id: "cash-flow",    label: "Cash Flow",         icon: "coin",       route: "/cash-flow",    group: "Statements" },
  { id: "close",        label: "Close Period",      icon: "lock",       route: "/close",        group: "Statements" },
  { id: "t-account",    label: "T-Account View",    icon: "ledger",     route: "/t-account",    group: "Reports" },
  { id: "sub-ledger",   label: "Sub-Ledgers",       icon: "branch",     route: "/sub-ledger",   group: "Reports" },
  { id: "audit",        label: "Audit Trail",       icon: "shield",     route: "/audit",        group: "Reports" },
  { id: "ias1",         label: "IAS 1 Compliance",  icon: "check",      route: "/ias1",         group: "Reports" },
  { id: "comparative",  label: "Comparative TB",    icon: "scale",      route: "/comparative",  group: "Reports" },
  { id: "setup-health", label: "System Health",     icon: "shield",     route: "/setup-health", group: "Setup" },
  { id: "organization", label: "Organization",      icon: "building",   route: "/organization", group: "Setup" },
  { id: "users",        label: "Users",             icon: "users",      route: "/users",        group: "Setup" },
  { id: "api-keys",     label: "API Keys",          icon: "key",        route: "/api-keys",     group: "Setup" },
  { id: "tax",          label: "Tax & Currency",    icon: "tag",        route: "/tax",          group: "Setup" },
  { id: "categories",   label: "Categories",        icon: "sliders",    route: "/categories",   group: "Setup" },
  { id: "security",     label: "Security",          icon: "shield",     route: "/security",     group: "Setup" },
]

const GROUP_ORDER = ["Overview","Ledger","Parties","Assets","Budgeting","Banking","Payables","Revenue","Period-End","Statements","Reports","Setup"]

const groups = computed(() => {
  return GROUP_ORDER.map(name => ({
    name,
    items: NAV_ROUTES.filter(r => r.group === name),
  }))
})
</script>

<template>
  <aside class="sidebar">
    <div class="sidebar-brand">
      <div class="brand-mark"><span>Q</span></div>
      <div class="brand-name">QeSuite</div>
      <div class="brand-suffix">IFRS</div>
    </div>
    <div class="entity-switcher">
      <span class="entity-dot"/>
      <div class="ent-text">
        <div class="ent-name">{{ orgName }}</div>
        <div class="ent-meta">{{ orgMeta }}</div>
      </div>
      <Ico name="chev-down" :size="11" style="color:var(--muted)"/>
    </div>
    <nav class="nav">
      <div
        v-for="group in groups"
        :key="group.name"
        class="nav-group"
      >
        <div class="nav-group-label">{{ group.name }}</div>
        <div class="nav-items">
          <div
            v-for="item in group.items"
            :key="item.id"
            class="nav-item"
            :class="{ active: activeRoute === item.route }"
            @click="emit('navigate', item.route)"
          >
            <span class="nav-ico">
              <Ico :name="item.icon" :size="14"/>
            </span>
            <span>{{ item.label }}</span>
            <span
              v-if="item.id === 'setup-health' && totalIssues > 0"
              class="health-badge"
            >{{ totalIssues }}</span>
          </div>
        </div>
      </div>
    </nav>
    <div class="sidebar-foot">
      <div class="user-chip" style="cursor:pointer" title="My Profile" @click="emit('navigate', '/profile')">
        <div class="user-avatar">{{ userInitials }}</div>
        <div style="flex:1;min-width:0">
          <div class="u-name">{{ userDisplayName }}</div>
          <div class="u-role">{{ userRole }}</div>
        </div>
      </div>
      <IconBtn icon="settings" title="Settings" @click="emit('navigate', '/organization')"/>
    </div>
  </aside>
</template>

<style scoped>
.health-badge {
  margin-left: auto;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--neg);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

</style>
