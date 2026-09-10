import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/Register.vue'),
    meta: { public: true, title: '注册' }
  },
  {
    path: '/',
    component: () => import('@/layout/MainLayout.vue'),
    redirect: '/schedule',
    children: [
      {
        path: 'schedule',
        name: 'schedule',
        component: () => import('@/views/Schedule.vue'),
        meta: { title: '课表' }
      },
      {
        path: 'courses',
        name: 'courses',
        component: () => import('@/views/Courses.vue'),
        meta: { title: '课程' }
      },
      {
        path: 'assignments',
        name: 'assignments',
        component: () => import('@/views/Assignments.vue'),
        meta: { title: '作业' }
      },
      {
        path: 'mine',
        name: 'mine',
        component: () => import('@/views/Mine.vue'),
        meta: { title: '我的' }
      },
      {
        path: 'course/:id',
        name: 'course-detail',
        component: () => import('@/views/CourseDetail.vue'),
        meta: { title: '课程详情' }
      },
      {
        path: 'course/create',
        name: 'course-create',
        component: () => import('@/views/CourseForm.vue'),
        meta: { title: '创建课程' }
      },
      {
        path: 'assignment/create/:courseId',
        name: 'assignment-create',
        component: () => import('@/views/AssignmentForm.vue'),
        meta: { title: '发布作业' }
      },
      {
        path: 'profile/edit',
        name: 'profile-edit',
        component: () => import('@/views/ProfileEdit.vue'),
        meta: { title: '编辑资料' }
      },
      {
        path: 'password/change',
        name: 'password-change',
        component: () => import('@/views/PasswordChange.vue'),
        meta: { title: '修改密码' }
      },
      {
        path: 'my-applications',
        name: 'my-applications',
        component: () => import('@/views/MyApplications.vue'),
        meta: { title: '我的申请' }
      },
      {
        path: 'admin/review',
        name: 'admin-review',
        component: () => import('@/views/AdminReview.vue'),
        meta: { title: '审批管理', admin: true }
      },
      {
        path: 'admin/monitor-review',
        name: 'admin-monitor-review',
        component: () => import('@/views/AdminMonitorReview.vue'),
        meta: { title: '班长审批', admin: true }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/schedule' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 登录守卫：未登录跳登录页（白名单除外）
router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.meta.public) return true
  if (!userStore.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · schoolHelp` : 'schoolHelp'
})

export default router
