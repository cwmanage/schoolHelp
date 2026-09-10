<template>
  <div class="pc-layout">
    <!-- 顶栏 -->
    <header class="topbar">
      <div class="topbar-inner">
        <div class="logo" @click="router.push('/')">
          <span class="logo-icon">校</span>
          <span class="logo-text">schoolHelp</span>
          <span class="logo-sub">校园助手 · 哈尔滨学院</span>
        </div>

        <nav class="nav">
          <router-link
            v-for="n in navs"
            :key="n.path"
            :to="n.path"
            class="nav-item"
            :class="{ active: isActive(n.path) }"
          >{{ n.name }}</router-link>
          <router-link
            v-if="userStore.isAdmin"
            to="/admin/review"
            class="nav-item"
            :class="{ active: isActive('/admin/review') }"
          >审批管理</router-link>
          <router-link
            to="/my-applications"
            class="nav-item"
            :class="{ active: isActive('/my-applications') }"
          >我的申请</router-link>
        </nav>

        <div class="search-box">
          <el-input
            v-model="keyword"
            placeholder="搜索课程 / 教师 / 班级"
            :prefix-icon="Search"
            clearable
            size="default"
            @keyup.enter="doSearch"
            @clear="router.push('/')"
          />
          <el-button type="primary" @click="doSearch">搜索</el-button>
        </div>

        <div class="user-area">
          <template v-if="userStore.isLoggedIn">
            <el-dropdown trigger="click" @command="onCommand">
              <div class="user-chip">
                <el-avatar :size="30" :src="userStore.userInfo?.avatarUrl || undefined">
                  {{ avatarText }}
                </el-avatar>
                <span class="uname">{{ userStore.userInfo?.nickname || userStore.userInfo?.username }}</span>
                <el-tag v-if="userStore.isAdmin" size="small" type="danger" round>管理员</el-tag>
                <el-tag v-else-if="userStore.isMonitor" size="small" type="warning" round>班长</el-tag>
                <el-icon class="caret"><ArrowDown /></el-icon>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">个人资料</el-dropdown-item>
                  <el-dropdown-item command="schedule">我的课表</el-dropdown-item>
                  <el-dropdown-item command="applications">我的申请</el-dropdown-item>
                  <el-dropdown-item v-if="userStore.isAdmin" command="review">审批管理</el-dropdown-item>
                  <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <el-button type="primary" @click="router.push('/login')">登录</el-button>
          </template>
        </div>
      </div>
    </header>

    <!-- 内容 -->
    <main class="pc-main">
      <router-view />
    </main>

    <!-- 页脚 -->
    <footer class="pc-footer">
      schoolHelp 校园助手 · 哈尔滨学院 · 数据仅供学习交流使用
    </footer>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, ArrowDown } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const keyword = ref('')

const navs = [
  { path: '/', name: '首页', exact: true },
  { path: '/courses', name: '课程库' },
  { path: '/assignments', name: '作业中心' },
  { path: '/schedule', name: '我的课表' }
]

const avatarText = computed(() => {
  const n = userStore.userInfo?.nickname || userStore.userInfo?.username || '?'
  return n.charAt(0).toUpperCase()
})

function isActive(path) {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}

function doSearch() {
  const k = keyword.value.trim()
  if (k) router.push({ path: '/courses', query: { keyword: k } })
}

function onCommand(cmd) {
  if (cmd === 'logout') {
    userStore.logout()
    router.push('/login')
  } else if (cmd === 'profile') router.push('/profile/edit')
  else if (cmd === 'schedule') router.push('/schedule')
  else if (cmd === 'applications') router.push('/my-applications')
  else if (cmd === 'review') router.push('/admin/review')
}
</script>

<style scoped>
.pc-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 100;
  height: var(--header-h);
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.topbar-inner {
  max-width: 1280px;
  margin: 0 auto;
  height: 100%;
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 0 24px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  flex-shrink: 0;
  user-select: none;
}

.logo-icon {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: linear-gradient(135deg, #00a1d6, #00c8a0);
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  color: #00a1d6;
}

.logo-sub {
  font-size: 12px;
  color: var(--text-weak);
  margin-left: 4px;
}

.nav {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.nav-item {
  padding: 6px 14px;
  border-radius: 4px;
  font-size: 14px;
  color: var(--text-main);
  transition: background 0.15s, color 0.15s;
}

.nav-item:hover {
  background: #f5f6f7;
}

.nav-item.active {
  color: var(--primary);
  font-weight: 600;
  background: var(--primary-light);
}

.search-box {
  flex: 1;
  max-width: 420px;
  display: flex;
  gap: 8px;
}

.user-area {
  margin-left: auto;
  flex-shrink: 0;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 20px;
  transition: background 0.15s;
}

.user-chip:hover {
  background: #f5f6f7;
}

.uname {
  font-size: 14px;
  max-width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.caret {
  color: var(--text-weak);
}

.pc-main {
  flex: 1;
  max-width: 1280px;
  width: 100%;
  margin: 0 auto;
  padding: 20px 24px 40px;
}

.pc-footer {
  text-align: center;
  color: var(--text-weak);
  font-size: 12px;
  padding: 18px 0 30px;
}
</style>
