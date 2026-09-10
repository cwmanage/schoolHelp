<template>
  <div class="home-page">
    <!-- 临期作业横幅 -->
    <div v-if="urgents.length" class="urgent-banner">
      <div class="ub-title"><el-icon><AlarmClock /></el-icon> 临期作业提醒</div>
      <div class="ub-items">
        <div v-for="u in urgents" :key="u.assignmentId" class="ub-item" @click="goCourse(u.courseId)">
          <span class="ub-course">{{ u.courseName }}</span>
          <span class="ub-title2">{{ u.title }}</span>
          <el-tag size="small" :type="u.daysLeft <= 1 ? 'danger' : 'warning'" round>
            {{ u.daysLeft <= 0 ? '今天截止' : `${u.daysLeft} 天后截止` }}
          </el-tag>
        </div>
      </div>
    </div>

    <!-- 欢迎条 -->
    <div class="welcome card">
      <div class="w-text">
        <h2>{{ greeting }}，{{ (userStore.userInfo?.nickname || userStore.userInfo?.username || '同学') }} 👋</h2>
        <p>今天也要元气满满地学习呀 · {{ todayStr }}</p>
      </div>
      <div class="w-actions">
        <!-- 天气详情（哈尔滨学院） -->
        <WeatherPanel ref="weatherRef" :auto="true" />
        <el-button type="primary" :icon="Plus" @click="router.push('/course/create')">申请课程</el-button>
        <el-button :icon="Calendar" @click="router.push('/schedule')">我的课表</el-button>
      </div>
    </div>

    <!-- 课程库推荐流（B站分区标题） -->
    <div class="section-head">
      <h3><span class="bar"></span>课程库 · 推荐</h3>
      <router-link to="/courses" class="more">查看全部 ›</router-link>
    </div>
    <div v-loading="loading" class="video-grid">
      <div
        v-for="c in courses"
        :key="c.id"
        class="video-card"
        @click="router.push(`/course/${c.id}`)"
      >
        <div class="cover" :style="{ background: coverBg(c.id) }">
          <span class="cover-letter">{{ (c.name || '课').charAt(0) }}</span>
          <span class="cover-sem" v-if="c.semester">{{ c.semester }}</span>
        </div>
        <div class="v-info">
          <div class="v-title">{{ c.name }}</div>
          <div class="v-meta">
            <span v-if="c.teacherName"><el-icon><User /></el-icon>{{ c.teacherName }}</span>
            <span v-if="c.className"><el-icon><School /></el-icon>{{ c.className }}</span>
          </div>
          <div class="v-desc" v-if="c.description">{{ c.description }}</div>
        </div>
      </div>

      <el-empty
        v-if="!loading && courses.length === 0"
        description="课程库暂无课程，点击右上角申请第一门课吧"
        style="grid-column: 1 / -1"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { AlarmClock, Plus, Calendar, User, School } from '@element-plus/icons-vue'
import { courseList } from '@/api/course'
import { urgentAssignments } from '@/api/biz'
import { useUserStore } from '@/stores/user'
import WeatherPanel from '@/components/WeatherPanel.vue'

const router = useRouter()
const userStore = useUserStore()
const weatherRef = ref()
const courses = ref([])
const urgents = ref([])
const loading = ref(false)

const todayStr = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric', month: 'long', day: 'numeric', weekday: 'long'
})

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 9) return '早上好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const coverColors = [
  'linear-gradient(135deg,#00a1d6,#7ed0e8)',
  'linear-gradient(135deg,#ff8f5e,#ffb88a)',
  'linear-gradient(135deg,#7c6ff0,#b3a6ff)',
  'linear-gradient(135deg,#3ecf8e,#a0e6c6)',
  'linear-gradient(135deg,#ff7eb3,#ffa8c9)',
  'linear-gradient(135deg,#f7b733,#fc4a1a)'
]
function coverBg(id) {
  return coverColors[(Number(id) || 1) % coverColors.length]
}

async function load() {
  loading.value = true
  try {
    const [cs, us] = await Promise.all([
      courseList({}), urgentAssignments()
    ])
    courses.value = (cs.data || []).slice(0, 12)
    urgents.value = us.data || []
  } catch (e) {
    ElMessage.warning('部分数据加载失败')
  } finally {
    loading.value = false
  }
}

function goCourse(id) {
  router.push(`/course/${id}`)
}

onMounted(async () => {
  try { await userStore.fetchProfile() } catch (e) { /* 未登录守卫已处理 */ }
  load()
})
</script>

<style scoped>
.home-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

/* 临期横幅 */
.urgent-banner {
  background: linear-gradient(135deg, #fff3e8, #ffe8f0);
  border: 1px solid #ffd9c0;
  border-radius: 8px;
  padding: 14px 18px;
}
.ub-title {
  font-size: 15px;
  font-weight: 700;
  color: #e06d2a;
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
}
.ub-items {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.ub-item {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fff;
  border-radius: 6px;
  padding: 6px 12px;
  cursor: pointer;
  font-size: 13px;
  transition: transform 0.15s;
}
.ub-item:hover {
  transform: translateY(-1px);
}
.ub-course {
  color: var(--primary);
  font-weight: 600;
}

/* 欢迎条 */
.welcome {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px 28px;
}
.w-text h2 {
  font-size: 22px;
}
.w-text p {
  color: var(--text-sub);
  font-size: 13px;
  margin-top: 6px;
}
.w-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* 分区标题 */
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.section-head h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
}
.bar {
  display: inline-block;
  width: 4px;
  height: 18px;
  background: var(--primary);
  border-radius: 2px;
}
.more {
  font-size: 13px;
  color: var(--text-sub);
}
.more:hover {
  color: var(--primary);
}

/* B站风格视频网格 */
.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 20px 18px;
}
.video-card {
  cursor: pointer;
  transition: transform 0.2s;
}
.video-card:hover {
  transform: translateY(-4px);
}
.video-card:hover .cover {
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.12);
}
.cover {
  position: relative;
  height: 124px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  transition: box-shadow 0.2s;
}
.cover-letter {
  font-size: 44px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.9);
}
.cover-sem {
  position: absolute;
  right: 6px;
  bottom: 6px;
  background: rgba(0, 0, 0, 0.35);
  color: #fff;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 3px;
}
.v-info {
  padding: 8px 2px 0;
}
.v-title {
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.v-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 6px;
}
.v-meta span {
  display: flex;
  align-items: center;
  gap: 3px;
}
.v-desc {
  font-size: 12px;
  color: var(--text-weak);
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
