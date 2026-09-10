import { defineStore } from 'pinia'
import { login as apiLogin, register as apiRegister, getProfile } from '@/api/user'
import { getToken, setToken, removeToken } from '@/utils/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken() || '',
    userInfo: null,
    role: null
  }),
  getters: {
    isLoggedIn: (s) => !!s.token,
    isAdmin: (s) => s.role === 2,
    isMonitor: (s) => s.role === 1,
    // 班长或管理员
    canManage: (s) => s.role === 1 || s.role === 2
  },
  actions: {
    async login(username, password) {
      const res = await apiLogin(username, password)
      const { token, user } = res.data
      this.token = token
      this.userInfo = user
      this.role = user.role
      setToken(token)
      return user
    },
    async register(form) {
      await apiRegister(form)
    },
    async fetchProfile() {
      const res = await getProfile()
      this.userInfo = res.data
      this.role = res.data.role
      return res.data
    },
    logout() {
      this.token = ''
      this.userInfo = null
      this.role = null
      removeToken()
    }
  }
})
