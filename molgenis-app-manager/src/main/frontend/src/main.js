import Vue from 'vue'
import AppManager from './AppManager.vue'

import store from './store'
import 'font-awesome/css/font-awesome.min.css'

Vue.config.productionTip = false

new Vue({
  el: '#app-manager',
  template: '<AppManager/>',
  components: {AppManager},
  store
})
