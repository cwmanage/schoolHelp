import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, setToken, removeToken } from '@/utils/auth'
import { cryptoEnabled, encryptJson, decryptPayload } from '@/utils/crypto'
import router from '@/router'

// 后端统一走 /api → 网关
const service = axios.create({
  baseURL: '/api',
  timeout: 20000
})

// 请求拦截：带 token + 报文加密（v4）
service.interceptors.request.use(async (config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // 报文加密：JSON body 全量加密（multipart 上传与 GET/DELETE 无 body 跳过）
  if (cryptoEnabled() && config.data
      && !(config.data instanceof FormData)
      && !['get', 'delete'].includes((config.method || '').toLowerCase())
      && typeof config.data !== 'string') {
    const enc = await encryptJson(config.data)
    if (enc) config.data = enc
  }
  return config
})

// 响应拦截：统一处理 Result { code, message, data }
service.interceptors.response.use(
  async (response) => {
    // 滑动续期：网关重签的新 token 自动更新本地存储（实现七天免登录）
    const renewed = response.headers && response.headers['x-renewed-token']
    if (renewed) {
      setToken(renewed)
    }
    // 报文解密：网关返回 {"payload":"iv.cipher"} → 解密为业务 JSON
    if (cryptoEnabled() && response.data && typeof response.data.payload === 'string') {
      try {
        const plain = await decryptPayload(response.data.payload)
        response.data = plain ?? response.data
      } catch {
        ElMessage.error('响应解密失败，请刷新页面重试')
        return Promise.reject(new Error('decrypt failed'))
      }
    }
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
  async (error) => {
    // 错误响应也可能是密文（网关统一加密），先解密再判断
    if (cryptoEnabled() && error.response?.data && typeof error.response.data.payload === 'string') {
      try {
        const plain = await decryptPayload(error.response.data.payload)
        error.response.data = plain ?? error.response.data
      } catch { /* 解密失败按原样处理 */ }
    }
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
