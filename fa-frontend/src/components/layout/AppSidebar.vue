<script setup>
import Ico from '@/components/primitives/Ico.vue'
import IconBtn from '@/components/primitives/IconBtn.vue'
import { computed } from 'vue'

const props = defineProps({
  activeRoute: { type: String },
  tweaks: { type: Object },
})

const emit = defineEmits(['navigate', 'update:tweaks'])

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
  { id: "organization", label: "Organization",      icon: "building",   route: "/organization", group: "Setup" },
  { id: "users",        label: "Users",             icon: "users",      route: "/users",        group: "Setup" },
  { id: "api-keys",     label: "API Keys",          icon: "key",        route: "/api-keys",     group: "Setup" },
  { id: "tax",          label: "Tax & Currency",    icon: "tag",        route: "/tax",          group: "Setup" },
  { id: "security",     label: "Security",          icon: "shield",     route: "/security",     group: "Setup" },
]

const GROUP_ORDER = ["Overview","Ledger","Parties","Assets","Payables","Revenue","Period-End","Statements","Reports","Setup"]

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
        <div class="ent-name">Apollo Enterprises Ltd</div>
        <div class="ent-meta">ORG-1A3F · FY 2026 · KES</div>
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
          </div>
        </div>
      </div>
    </nav>
    <div class="sidebar-foot">
      <div class="user-chip">
        <div class="user-avatar">JM</div>
        <div style="flex:1;min-width:0">
          <div class="u-name">Jane Muriuki</div>
          <div class="u-role">ADMIN · MFA on</div>
        </div>
      </div>
      <IconBtn icon="settings" title="Settings" @click="emit('navigate', '/organization')"/>
    </div>
  </aside>
</template>
