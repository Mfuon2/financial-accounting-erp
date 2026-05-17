<script setup>
import { computed } from 'vue'
import { IAS1_CHECKS } from '@/data/index.js'
import PageHeader from '@/components/PageHeader.vue'
import Badge from '@/components/primitives/Badge.vue'
import Ico from '@/components/primitives/Ico.vue'

const pass = computed(() => IAS1_CHECKS.filter(c => c.status === 'PASS').length)
</script>

<template>
  <div class="page">
    <PageHeader
      title="IAS 1 Compliance Check"
      :meta="`${pass} of ${IAS1_CHECKS.length} checks pass · for period 2026-02`"
    />

    <div class="page-section stack">
      <div class="card">
        <div class="card-head">
          <Ico name="shield" :size="13" />
          Disclosure &amp; presentation requirements
          <div class="h-meta">{{ pass }}/{{ IAS1_CHECKS.length }} passed</div>
        </div>
        <table class="tbl">
          <thead>
            <tr>
              <th style="width:44px"></th>
              <th>Check</th>
              <th>Detail</th>
              <th style="width:120px">Status</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="c in IAS1_CHECKS" :key="c.id">
              <td>
                <div
                  :style="{
                    width: '28px',
                    height: '28px',
                    borderRadius: '7px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    background: c.status === 'PASS'
                      ? 'color-mix(in oklab, var(--pos) 14%, transparent)'
                      : 'color-mix(in oklab, var(--warn) 14%, transparent)',
                    color: c.status === 'PASS' ? 'var(--pos)' : 'var(--warn)',
                  }"
                >
                  <Ico :name="c.status === 'PASS' ? 'check' : 'warn'" :size="13" />
                </div>
              </td>
              <td style="font-weight:500;white-space:nowrap">{{ c.name }}</td>
              <td class="muted" style="font-size:11.5px">{{ c.detail }}</td>
              <td>
                <Badge :status="c.status === 'PASS' ? 'approved' : 'pending'" :dot="false" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
