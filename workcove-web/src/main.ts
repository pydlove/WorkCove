import { createApp } from 'vue'

import App from './App.vue'
import router from './router'
import { flushUserStorage } from './services/userScopedStorage'
import { pinia } from './stores'
import { useThemeStore } from './stores/theme'
import './styles/fonts.css'
import './styles/tokens.css'
import './styles/global.css'

function flushUserData() {
  void flushUserStorage().catch(() => undefined)
}

const app = createApp(App)

app.use(pinia)
useThemeStore(pinia).initialize()
app.use(router)

router.isReady().then(() => {
  app.mount('#app')
  window.dispatchEvent(new Event('workcove:app-ready'))
})

window.addEventListener('pagehide', flushUserData)
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'hidden') flushUserData()
})
