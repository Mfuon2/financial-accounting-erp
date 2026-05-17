import { createApp }            from 'vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import App                       from './App.vue'
import './assets/base.css'
import { routes, setupGuards }  from './router/index.js'
import { useAuth }              from '@/composables/useAuth.js'

// Unregister any stale service workers
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.getRegistrations().then(regs => regs.forEach(r => r.unregister()))
}

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior() { return { top: 0 } },
})

// Attach navigation guards before any navigation happens
setupGuards(router)

// Init auth first — performs silent refresh if a refresh token is in sessionStorage.
// The guard waits for authReady so routing is safe as soon as init() resolves.
useAuth().init().then(() => {
  createApp(App).use(router).mount('#app')
})
