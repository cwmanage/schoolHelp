<template>
  <div class="page main-layout">
    <header v-if="showHeader" class="app-header">
      <div class="header-title">{{ title }}</div>
      <div class="header-right" v-if="showAction">
        <slot name="header-action" />
      </div>
    </header>

    <main class="app-main">
      <router-view v-slot="{ Component }">
        <keep-alive :max="5">
          <component :is="Component" />
        </keep-alive>
      </router-view>
    </main>

    <nav class="tab-bar" v-if="!hideTabBar">
      <router-link
        v-for="tab in tabs"
        :key="tab.path"
        :to="tab.path"
        class="tab-item"
        :class="{ active: isActive(tab.path) }"
      >
        <el-icon :size="22"><component :is="tab.icon" /></el-icon>
        <span>{{ tab.name }}</span>
      </router-link>
    </nav>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Calendar, Notebook, EditPen, User } from '@element-plus/icons-vue'

const route = useRoute()

const tabs = [
  { path: '/schedule', name: '课表', icon: Calendar },
  { path: '/courses', name: '课程', icon: Notebook },
  { path: '/assignments', name: '作业', icon: EditPen },
  { path: '/mine', name: '我的', icon: User }
]

const titleMap = {
  '/schedule': '我的课表',
  '/courses': '课程库',
  '/assignments': '作业中心',
  '/mine': '个人中心'
}

const title = computed(() => {
  const m = route.meta.title
  return m && m !== '课表' && m !== '课程' && m !== '作业' && m !== '我的' ? m : (titleMap[route.path] || 'schoolHelp')
})

const showHeader = computed(() => !['/schedule', '/courses', '/assignments', '/mine'].includes(route.path))
const hideTabBar = computed(() => !['/schedule', '/courses', '/assignments', '/mine'].includes(route.path))
const showAction = computed(() => !!route.meta.title && showHeader.value)

function isActive(path) {
  // 详情页也算对应 tab 激活
  if (route.path.startsWith('/course')) return path === '/courses'
  if (route.path.startsWith('/assignment')) return path === '/assignments'
  return route.path === path
}
</script>

<style scoped>
.main-layout {
  display: flex;
  flex-direction: column;
  padding-bottom: 64px;
}

.app-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: #fff;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f0f0f0;
}

.header-title {
  font-size: 17px;
  font-weight: 600;
}

.app-main {
  flex: 1;
  padding: 12px;
}

.tab-bar {
  position: fixed;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 100%;
  max-width: 520px;
  background: #fff;
  border-top: 1px solid #f0f0f0;
  display: flex;
  z-index: 100;
  padding-bottom: env(safe-area-inset-bottom);
}

.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 8px 0 6px;
  font-size: 11px;
  color: #86909c;
  text-decoration: none;
  transition: color 0.2s;
}

.tab-item.active {
  color: var(--primary);
}

.tab-item.active .el-icon {
  transform: scale(1.05);
}
</style>
