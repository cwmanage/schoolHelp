import request from '@/utils/request'

// 课程
export const courseList = (params) => request.get('/course/course/list', { params })
export const courseDetail = (id) => request.get(`/course/course/${id}`)
export const createCourse = (data) => request.post('/course/course', data)
export const updateCourse = (id, data) => request.put(`/course/course/${id}`, data)
export const deleteCourse = (id) => request.delete(`/course/course/${id}`)

// 我的申请（课程）
export const myCourseApplications = () => request.get('/course/course/my-applications')
// 管理端：课程列表按状态（0待审/1通过/2驳回）
export const adminCourseList = (status) =>
  request.get('/course/course/admin/list', { params: { status } })
// 审批课程：action 1通过/2驳回，note 意见
export const reviewCourse = (id, action, note) =>
  request.post(`/course/course/admin/${id}/review`, { action, note })

// 作业
export const assignmentsByCourse = (courseId) =>
  request.get(`/course/assignment/course/${courseId}`)
export const assignmentDetail = (id) => request.get(`/course/assignment/${id}`)
export const createAssignment = (data) => request.post('/course/assignment', data)
export const updateAssignment = (id, data) => request.put(`/course/assignment/${id}`, data)
export const deleteAssignment = (id) => request.delete(`/course/assignment/${id}`)

// 我的申请（作业）
export const myAssignmentApplications = () => request.get('/course/assignment/my-applications')
export const adminAssignmentList = (status, courseId) =>
  request.get('/course/assignment/admin/list', { params: { status, courseId } })
export const reviewAssignment = (id, action, note) =>
  request.post(`/course/assignment/admin/${id}/review`, { action, note })

// 课程资料
export const materialsByCourse = (courseId) =>
  request.get(`/course/material/course/${courseId}`)
export const uploadMaterial = (courseId, formData) =>
  request.post(`/course/material/course/${courseId}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
export const deleteMaterial = (id) => request.delete(`/course/material/${id}`)

// 我的申请（资料）
export const myMaterialApplications = () => request.get('/course/material/my-applications')
export const adminMaterialList = (status, courseId) =>
  request.get('/course/material/admin/list', { params: { status, courseId } })
export const reviewMaterial = (id, action, note) =>
  request.post(`/course/material/admin/${id}/review`, { action, note })
