<template>
  <div class="mine-page">
    <!-- 用户卡片 -->
    <div class="user-card" @click="router.push('/profile/edit')">
      <div class="avatar-wrap">
        <el-avatar :size="56" :src="avatarUrl || undefined" class="avatar">
          {{ avatarText }}
        </el-avatar>
        <span v-if="userStore.canManage" class="role-badge">
          {{ userStore.isAdmin ? '管理员' : '班长' }}
        </span>
      </div>
      <div class="user-info">
        <div class="user-name">
          {{ userInfo?.nickname || userInfo?.username || '未登录' }}
        </div>
        <div class="user-sub">
          <span v-if="userInfo?.username">{{ userInfo.username }}</span>
          <span v-if="userInfo?.className"> · {{ userInfo.className }}</span>
        </div>
        <div class="user-sign" v-if="userInfo?.signature">{{ userInfo.signature }}</div>
      </div>
      <el-icon class="chevron"><ArrowRight /></el-icon>
    </div>

    <!-- 统计 -->
    <div class="stat-row card" v-if="statsLoaded">
      <div class="stat-item">
        <div class="stat-num">{{ statData.scheduleCount }}</div>
        <div class="stat-label">课表课程</div>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-item">
        <div class="stat-num">{{ statData.urgentCount }}</div>
        <div class="stat-label">临期作业</div>
      </div>
      <div class="stat-divider"></div>
      <div class="stat-item">
        <div class="stat-num">{{ statData.doneCount }}</div>
        <div class="stat-label">已完成</div>
      </div>
    </div>

    <!-- 功能列表 -->
    <div class="menu card">
      <div class="menu-item" @click="router.push('/profile/edit')">
        <el-icon class="mi-icon i-edit"><EditPen /></el-icon>
        <span>编辑资料</span>
        <el-icon class="mi-arrow"><ArrowRight /></el-icon>
      </div>
      <div class="menu-item" @click="router.push('/password/change')">
        <el-icon class="mi-icon i-lock"><Lock /></el-icon>
        <span>修改密码</span>
        <el-icon class="mi-arrow"><ArrowRight /></el-icon>
      </div>
      <div class="menu-item" @click="router.push('/schedule')">
        <el-icon class="mi-icon i-cal"><Calendar /></el-icon>
        <span>我的课表</span>
        <el-icon class="mi-arrow"><ArrowRight /></el-icon>
      </div>
      <div class="menu-item" @click="router.push('/my-applications')">
        <el-icon class="mi-icon i-app"><Stamp /></el-icon>
        <span>我的申请</span>
        <el-icon class="mi-arrow"><ArrowRight /></el-icon>
      </div>
      <template v-if="userStore.isAdmin">
        <div class="menu-item" @click="router.push('/admin/monitor-review')">
          <el-icon class="mi-icon i-review"><Checked /></el-icon>
          <span>班长审批</span>
          <el-icon class="mi-arrow"><ArrowRight /></el-icon>
        </div>
        <div class="menu-item" @click="router.push('/admin/review')">
          <el-icon class="mi-icon i-review"><Checked /></el-icon>
          <span>审批管理</span>
          <el-icon class="mi-arrow"><ArrowRight /></el-icon>
        </div>
        <div class="menu-item" @click="router.push('/courses')">
          <el-icon class="mi-icon i-admin"><DataBoard /></el-icon>
          <span>课程管理（管理员）</span>
          <el-icon class="mi-arrow"><ArrowRight /></el-icon>
        </div>
      </template>
    </div>

    <!-- 退出 -->
    <el-button class="logout-btn" plain type="danger" @click="handleLogout">
      退出登录
    </el-button>

    <div class="version">schoolHelp v1.0 · 哈尔滨学院</div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  ArrowRight, EditPen, Lock, Calendar, DataBoard, Stamp, Checked
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { mySchedules } from '@/api/user'
import { urgentAssignments } from '@/api/biz'
import { assignmentSubmitted } from '@/api/biz'

const router = useRouter()
const userStore = useUserStore()

const userInfo = computed(() => userStore.userInfo)
const avatarUrl = computed(() => userInfo.value?.avatarUrl || '')
const avatarText = computed(() => {
  const name = userInfo.value?.nickname || userInfo.value?.username || '?'
  return name.charAt(0).toUpperCase()
})

const statsLoaded = ref(false)
const statData = ref({ scheduleCount: 0, urgentCount: 0, doneCount: 0 })

async function loadStats() {
  try {
    const sem = await mySchedules(null)
    const scheds = sem.data || []
    statData.value.scheduleCount = scheds.length

    const ur = await urgentAssignments()
    const urg = ur.data || []
    statData.value.urgentCount = urg.length

    // 已完成数 = 课表课程的作业 - 未提交数（粗略：查每门课作业）
    // 简化：从临期接口拿不到全部，只展示未完成提醒数
    statData.value.doneCount = 0
    statsLoaded.value = true
  } catch (e) {
    statsLoaded.value = true
  }
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定退出登录？', '提示', { type: 'warning' })
    userStore.logout()
    router.push('/login')
  } catch (e) {
    /* 取消 */
  }
}

onMounted(async () => {
  try {
    await userStore.fetchProfile()
  } catch (e) {
    /* token 失效会跳登录 */
  }
  loadStats()
})
</script>

<style scoped>
.mine-page {
  min-height: 100%;
}

.user-card {
  background: linear-gradient(135deg, #4a7cff 0%, #6ea0ff 100%);
  border-radius: 16px;
  padding: 20px 16px;
  display: flex;
  align-items: center;
  gap: 14px;
  color: #fff;
  cursor: pointer;
  box-shadow: 0 6px 18px rgba(74, 124, 255, 0.3);
}

.avatar-wrap {
  position: relative;
}

.avatar {
  background: rgba(255, 255, 255, 0.25);
  font-size: 24px;
  color: #fff;
  border: 2px solid rgba(255, 255, 255, 0.5);
}

.role-badge {
  position: absolute;
  bottom: -4px;
  right: -6px;
  background: #ffb347;
  color: #fff;
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 8px;
  white-space: nowrap;
}

.user-info {
  flex: 1;
  min-width: 0;
}

.user-name {
  font-size: 19px;
  font-weight: 700;
}

.user-sub {
  font-size: 12px;
  opacity: 0.9;
  margin-top: 3px;
}

.user-sign {
  font-size: 12px;
  opacity: 0.85;
  margin-top: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chevron {
  opacity: 0.7;
}

.stat-row {
  display: flex;
  align-items: center;
  margin: 12px 0;
  padding: 14px;
}

.stat-item {
  flex: 1;
  text-align: center;
}

.stat-num {
  font-size: 20px;
  font-weight: 700;
  color: var(--primary);
}

.stat-label {
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 2px;
}

.stat-divider {
  width: 1px;
  height: 26px;
  background: #eee;
}

.menu {
  padding: 6px 14px;
  margin-bottom: 16px;
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 13px 0;
  cursor: pointer;
  border-bottom: 1px solid #f7f8fa;
  font-size: 14px;
}

.menu-item:last-child {
  border-bottom: none;
}

.menu-item:active {
  opacity: 0.7;
}

.mi-icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 10px;
  color: #fff;
}

.i-edit { background: #5b8cff; }
.i-lock { background: #34c9a0; }
.i-cal { background: #ff9f43; }
.i-admin { background: #a66bff; }
.i-app { background: #2ec5d9; }
.i-review { background: #e67e22; }

.menu-item > span {
  flex: 1;
}

.mi-arrow {
  color: #c8ccd4;
}

.logout-btn {
  width: 100%;
  margin-bottom: 20px;
  border-radius: 10px;
}

.version {
  text-align: center;
  font-size: 11px;
  color: #c0c4cc;
  padding-bottom: 20px;
}
</style>
