<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import Button from '@/components/primitives/Button.vue'
import Ico from '@/components/primitives/Ico.vue'
import Badge from '@/components/primitives/Badge.vue'
import Banner from '@/components/data-display/Banner.vue'
import SearchableSelect from '@/components/primitives/SearchableSelect.vue'

const router = useRouter()
const step = ref(0)
const steps = ["Organization", "Admin user", "Currency & period", "Done"]

function onLogin() { router.push('/dashboard') }
function onCancel() { router.push('/login') }

const COUNTRY_OPTIONS = [
  { value: 'KE', label: 'Kenya (KE)' },
  { value: 'UG', label: 'Uganda (UG)' },
  { value: 'TZ', label: 'Tanzania (TZ)' },
  { value: 'RW', label: 'Rwanda (RW)' },
]

const CURRENCY_OPTIONS = [
  { value: 'KES', label: 'KES — Kenyan Shilling' },
  { value: 'USD', label: 'USD — US Dollar' },
  { value: 'EUR', label: 'EUR — Euro' },
]

const REPORTING_CURRENCY_OPTIONS = [
  { value: 'KES', label: 'KES' },
  { value: 'USD', label: 'USD' },
]

const FISCAL_YEAR_OPTIONS = [
  { value: '01', label: 'January' },
  { value: '02', label: 'February' },
  { value: '03', label: 'March' },
  { value: '04', label: 'April' },
  { value: '05', label: 'May' },
  { value: '06', label: 'June' },
  { value: '07', label: 'July' },
  { value: '08', label: 'August' },
  { value: '09', label: 'September' },
  { value: '10', label: 'October' },
  { value: '11', label: 'November' },
  { value: '12', label: 'December' },
]

const COA_OPTIONS = [
  { value: 'SERVICE',       label: 'SERVICE (IFRS)' },
  { value: 'TRADING',       label: 'TRADING (IFRS)' },
  { value: 'MANUFACTURING', label: 'MANUFACTURING (IFRS)' },
  { value: 'EMPTY',         label: 'Empty' },
]

const signupForm = ref({
  country: 'KE',
  functionalCurrency: 'KES',
  reportingCurrency: 'KES',
  fiscalYearStart: '01',
  coaTemplate: 'SERVICE',
})
</script>

<template>
  <div class="auth-screen" style="grid-template-columns:1fr 560px">
    <div class="auth-poster">
      <div class="auth-brand">
        <div class="brand-mark"><span>Q</span></div>
        <div class="brand-name" style="font-size:16px">QeSuite</div>
        <div class="brand-suffix">IFRS · ENTERPRISE</div>
      </div>
      <div>
        <h1 class="auth-headline">Set up your <em>book of record</em> in 3 minutes.</h1>
        <div class="auth-sub">We'll bootstrap your org, seed an IFRS-compliant chart of accounts...</div>
      </div>
      <div class="auth-stats">
        <div><div class="v">IFRS COA</div><div class="l">Seeded on first run</div></div>
        <div><div class="v">Multi-FX</div><div class="l">IAS 21 ready</div></div>
        <div><div class="v">RBAC + MFA</div><div class="l">Enforced from day one</div></div>
      </div>
    </div>
    <div class="auth-form-pane" style="align-items:flex-start;padding-top:60px">
      <div class="auth-form" style="max-width:420px">
        <h3>Create your organization</h3>

        <div class="stepper" style="margin-top:10px;margin-bottom:16px">
          <div v-for="(s, i) in steps" :key="i" :class="['step', i < step ? 'done' : i === step ? 'active' : '']">
            <div class="step-num"><Ico v-if="i < step" name="check" :size="10"/><span v-else>{{ i+1 }}</span></div>
            <div>{{ s }}</div>
            <div class="step-line"/>
          </div>
        </div>

        <div v-if="step === 0" class="stack">
          <div class="field"><label>Trading name</label><input class="input" value="Apollo Enterprises Ltd"/></div>
          <div class="field"><label>Legal name</label><input class="input" value="Apollo Enterprises Limited"/></div>
          <div class="field"><label>Registration No.</label><input class="input mono" value="PVT-20240001"/></div>
          <div class="field"><label>Tax ID (KRA PIN)</label><input class="input mono" value="A001234567A"/></div>
          <div class="field"><label>Country</label>
            <SearchableSelect
              v-model="signupForm.country"
              :options="COUNTRY_OPTIONS"
              placeholder="Select country"
            />
          </div>
        </div>

        <div v-if="step === 1" class="stack">
          <div class="field"><label>Your full name</label><input class="input" value="Jane Muriuki"/></div>
          <div class="field"><label>Email</label><input class="input" value="j.muriuki@apollo.co.ke"/></div>
          <div class="field"><label>Username</label><input class="input mono" value="j.muriuki"/></div>
          <div class="field"><label>Password</label><input class="input" type="password" value="••••••••"/></div>
          <Banner kind="info">You'll be created with <Badge status="posted" :dot="false">ADMIN</Badge> role. MFA setup follows after first login.</Banner>
        </div>

        <div v-if="step === 2" class="stack">
          <div class="form-grid cols-2">
            <div class="field"><label>Functional currency</label>
              <SearchableSelect
                v-model="signupForm.functionalCurrency"
                :options="CURRENCY_OPTIONS"
                placeholder="Select currency"
              />
            </div>
            <div class="field"><label>Reporting currency</label>
              <SearchableSelect
                v-model="signupForm.reportingCurrency"
                :options="REPORTING_CURRENCY_OPTIONS"
                placeholder="Select currency"
              />
            </div>
            <div class="field"><label>Fiscal year start</label>
              <SearchableSelect
                v-model="signupForm.fiscalYearStart"
                :options="FISCAL_YEAR_OPTIONS"
                placeholder="Select month"
              />
            </div>
            <div class="field"><label>COA template</label>
              <SearchableSelect
                v-model="signupForm.coaTemplate"
                :options="COA_OPTIONS"
                placeholder="Select template"
              />
            </div>
          </div>
          <Banner kind="success" icon="check">We'll seed your COA, generate FY 2026 periods, register VAT 16% and KES exchange rates.</Banner>
        </div>

        <div v-if="step === 3" class="stack" style="align-items:center;text-align:center;padding:20px 0">
          <div style="width:56px;height:56px;border-radius:50%;background:color-mix(in oklab, var(--pos) 16%, transparent);color:var(--pos);display:flex;align-items:center;justify-content:center">
            <Ico name="check" :size="28"/>
          </div>
          <h3 style="margin:0">You're all set</h3>
          <div class="muted" style="font-size:12.5px">Organization, admin user, COA, periods, tax codes & currencies provisioned.</div>
        </div>

        <div class="h-row" style="gap:8px;margin-top:12px">
          <Button v-if="step > 0 && step < 3" variant="ghost" @click="step--">Back</Button>
          <Button v-if="step < 3" variant="primary" @click="step++">Continue <Ico name="chev-right" :size="12"/></Button>
          <Button v-else variant="primary" @click="onLogin">Open dashboard</Button>
          <div style="flex:1"/>
          <Button variant="ghost" @click="onCancel">Cancel</Button>
        </div>
      </div>
    </div>
  </div>
</template>
