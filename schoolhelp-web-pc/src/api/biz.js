import request from '@/utils/request'

// 临期作业聚合（我的课表 ∩ 5天内截止 ∩ 未提交）
export const urgentAssignments = () => request.get('/biz/urgent/assignments')

// 作业提交标记
export const assignmentSubmitted = (assignmentId) =>
  request.get(`/biz/assignment/${assignmentId}/submitted`)
export const markSubmitted = (assignmentId) =>
  request.post(`/biz/assignment/${assignmentId}/submit`)
export const unmarkSubmitted = (assignmentId) =>
  request.delete(`/biz/assignment/${assignmentId}/submit`)

// 课程评论
export const commentList = (courseId) => request.get(`/biz/comment/course/${courseId}`)
export const addComment = (courseId, content, isAnonymous) =>
  request.post(`/biz/comment/course/${courseId}`, { content, isAnonymous })
export const deleteComment = (commentId) => request.delete(`/biz/comment/${commentId}`)
export const commentAdminDetail = (commentId) =>
  request.get(`/biz/comment/admin/${commentId}`)
