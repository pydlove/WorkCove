import { createApp } from 'vue'

import App from './App.vue'
import router from './router'
import { pinia } from './stores'
import { useThemeStore } from './stores/theme'
import './styles/tokens.css'
import './styles/global.css'

const app = createApp(App)

app.use(pinia)
useThemeStore(pinia).initialize()
app.use(router).mount('#app')
