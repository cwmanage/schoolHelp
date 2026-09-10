import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, removeToken } from '@/utils/auth'
import router from '@/router'

// 后端统一走 /api → 网关
const service = axios.create({
  baseURL: '/api',
  timeout: 20000
})

// 请求拦截：带 token
service.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：统一处理 Result { code, message, data }
service.interceptors.response.use(
  (response) => {
    const res = response.data
    // 非标准结构（如文件流）直接返回
    if (res == null || typeof res !== 'object' || !('code' in res)) {
      return res
    }
    if (res.code === 200) {
      return res
    }
    // 401 无权限 → 跳登录
    if (res.code === 401 || (response.status === 401)) {
      ElMessage.error(res.message || '登录已过期，请重新登录')
      removeToken()
      router.push('/login')
      return Promise.reject(new Error(res.message || 'unauthorized'))
    }
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || 'error'))
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      ElMessage.error('登录已过期，请重新登录')
      removeToken()
      router.push('/login')
    } else if (status === 503) {
      ElMessage.error('服务暂不可用，请稍后再试')
    } else {
      const msg = error.response?.data?.message || error.message || '网络异常'
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  }
)

export default service
