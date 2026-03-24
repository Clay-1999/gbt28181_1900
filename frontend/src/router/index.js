import { createRouter, createWebHistory } from 'vue-router'
import LocalConfigView from '../views/LocalConfigView.vue'
import InterconnectsView from '../views/InterconnectsView.vue'
import DevicesView from '../views/DevicesView.vue'
import Ivs1900ConfigView from '../views/Ivs1900ConfigView.vue'
import AlarmView from '../views/AlarmView.vue'
import RecordingView from '../views/RecordingView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/local-config' },
    { path: '/local-config', component: LocalConfigView },
    { path: '/interconnects', component: InterconnectsView },
    { path: '/devices', component: DevicesView },
    { path: '/ivs1900-config', component: Ivs1900ConfigView },
    { path: '/alarms', component: AlarmView },
    { path: '/recordings', component: RecordingView }
  ]
})

export default router
