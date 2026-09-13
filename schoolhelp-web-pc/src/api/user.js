import request from '@/utils/request'

// 认证
export const login = (username, password) =>
  request.post('/user/auth/login', { username, password })

export const register = (data) =>
  request.post('/user/auth/register', data)

export const checkUsername = (username) =>
  request.get('/user/auth/check-username', { params: { username } })

export const passwordStrength = (password) =>
  request.get('/user/auth/password-strength', { params: { password } })

// 个人资料
export const getProfile = () => request.get('/user/profile')
export const updateProfile = (data) => request.put('/user/profile', data)
export const changePassword = (data) => request.put('/user/profile/password', data)

// 头像上传（multipart）
export const uploadAvatar = (formData) =>
  request.post('/user/profile/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })

// 课表
export const mySchedules = (semester) =>
  request.get('/user/schedule', { params: { semester } })

export const mySemesters = () => request.get('/user/schedule/semesters')
export const addSchedule = (data) => request.post('/user/schedule', data)
export const updateSchedule = (id, data) => request.put(`/user/schedule/${id}`, data)
export const deleteSchedule = (id) => request.delete(`/user/schedule/${id}`)

// 班长审批（管理员）
export const pendingMonitors = () => request.get('/user/admin/monitors/pending')
export const reviewMonitor = (id, action, note) =>
  request.post(`/user/admin/monitors/${id}/review`, { action, note })

// AI 问答（免费大模型代理）
export const aiChat = (messages) =>
  request.post('/user/ai/chat', { messages }, { timeout: 60000 })

// AI 课表截图识别（multipart 图片，返回结构化课程条目）
export const aiScheduleOcr = (formData) =>
  request.post('/user/ai/schedule-ocr', formData, {
    timeout: 60000,
    headers: { 'Content-Type': 'multipart/form-data' }
  })

