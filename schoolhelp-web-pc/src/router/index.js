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
    component: () => import('@/layout/PcLayout.vue'),
    redirect: '/',
    children: [
      {
        path: '',
        name: 'home',
        component: () => import('@/views/Home.vue'),
        meta: { title: '首页' }
      },
      {
        path: 'courses',
        name: 'courses',
        component: () => import('@/views/Courses.vue'),
        meta: { title: '课程库' }
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
        meta: { title: '创建/申请课程' }
      },
      {
        path: 'assignments',
        name: 'assignments',
        component: () => import('@/views/Assignments.vue'),
        meta: { title: '作业中心' }
      },
      {
        path: 'assignment/create/:courseId',
        name: 'assignment-create',
        component: () => import('@/views/AssignmentForm.vue'),
        meta: { title: '发布/申请作业' }
      },
      {
        path: 'schedule',
        name: 'schedule',
        component: () => import('@/views/Schedule.vue'),
        meta: { title: '我的课表' }
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
        meta: { title: '审批管理' }
      },
      {
        path: 'profile/edit',
        name: 'profile-edit',
        component: () => import('@/views/ProfileEdit.vue'),
        meta: { title: '个人资料' }
      },
      {
        path: 'password/change',
        name: 'password-change',
        component: () => import('@/views/PasswordChange.vue'),
        meta: { title: '修改密码' }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 登录守卫
router.beforeEach((to) => {
  const userStore = useUserStore()
  if (to.meta.public) return true
  if (!userStore.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  // 管理员页仅管理员
  if (to.meta.admin && !userStore.isAdmin) {
    return { path: '/' }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · schoolHelp` : 'schoolHelp'
})

export default router
