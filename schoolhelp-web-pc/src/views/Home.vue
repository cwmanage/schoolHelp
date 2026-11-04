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

    <!-- 本周校园日历（考试 · 竞赛 · 节假日） -->
    <div class="calendar-card">
      <div class="cc-head">
        <h3><span class="bar"></span>本周校园日历 <span class="cc-sub">考试 · 竞赛 · 节假日</span></h3>
        <div class="cc-actions">
          <router-link to="/activities">
            <el-button size="small" round>大学活动分析</el-button>
          </router-link>
          <router-link to="/schedule">
            <el-button type="primary" size="small" round>去课表</el-button>
          </router-link>
        </div>
      </div>
      <div v-if="calendarEvents.length" class="cc-list">
        <div
          class="cc-item"
          v-for="e in pagedEvents"
          :key="e.id"
          :class="'type-' + e.eventType"
        >
          <div class="cc-main">
            <el-tag size="small" :type="calTagType(e.eventType)">{{ calTypeText(e.eventType) }}</el-tag>
            <span class="cc-date">{{ calDateRange(e) }}</span>
            <span class="cc-name">{{ e.title }}</span>
          </div>
          <span class="cc-note" v-if="e.timeNote">{{ e.timeNote }}</span>
        </div>
      </div>
      <div v-if="calendarEvents.length > 5" class="cc-page">
        <el-pagination
          layout="prev, pager, next"
          :page-size="5"
          :total="calendarEvents.length"
          v-model:current-page="calPage"
          background
        />
      </div>
      <el-empty v-if="!calendarEvents.length" description="本周暂无考试 / 竞赛 / 节假日安排" :image-size="60" />
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
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import { AlarmClock, Plus, Calendar, User, School } from '@element-plus/icons-vue'
import { courseList } from '@/api/course'
import { urgentAssignments } from '@/api/biz'
import { getCalendarEvents, calendarHasUpdate, calendarMarkRead } from '@/api/user'
import { useUserStore } from '@/stores/user'
import WeatherPanel from '@/components/WeatherPanel.vue'

const router = useRouter()
const userStore = useUserStore()
const weatherRef = ref()
const courses = ref([])
const urgents = ref([])
const loading = ref(false)
const calendarEvents = ref([])
const calPage = ref(1)
// 分页：每页 5 条（用户反馈 v4）
const pagedEvents = computed(() => {
  const start = (calPage.value - 1) * 5
  return calendarEvents.value.slice(start, start + 5)
})
watch(calendarEvents, () => { calPage.value = 1 })

function thisMonday() {
  const d = new Date()
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7))
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0')
}

function dateOffset(base, days) {
  const d = new Date(base + 'T00:00:00')
  d.setDate(d.getDate() + days)
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0')
}

function calTagType(t) {
  return { exam: 'danger', contest: 'warning', holiday: 'success', activity: 'info' }[t] || 'info'
}

function calTypeText(t) {
  return { exam: '考试', contest: '竞赛', holiday: '节假日', activity: '活动' }[t] || '活动'
}

function calDateRange(e) {
  if (e.eventDate) return e.eventDate
  if (e.dateStart && e.dateEnd) return e.dateStart + ' ~ ' + e.dateEnd
  return e.dateStart || ''
}

async function loadCalendar() {
  const monday = thisMonday()
  try {
    const res = await getCalendarEvents(monday, dateOffset(monday, 6))
    calendarEvents.value = res.data || []
  } catch (e) {
    calendarEvents.value = []
  }
}

/** 登录后检查日历更新（有更新弹提示并标记已读） */
async function checkCalendarUpdate() {
  if (!userStore.isLoggedIn) return
  try {
    const res = await calendarHasUpdate()
    if (res.data) {
      ElNotification({
        title: '日历更新',
        message: '您的日历有新大学活动更新，请及时关注',
        type: 'info',
        duration: 6000
      })
      await calendarMarkRead()
    }
  } catch (e) { /* 忽略 */ }
}

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
  loadCalendar()
  checkCalendarUpdate()
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
/* ===== 本周校园日历（v4：课程库同风格行式卡片 + 分页）===== */
.calendar-card {
  background: #fff;
  border-radius: 10px;
  padding: 18px 20px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
  margin-bottom: 24px;
}
.cc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
.cc-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
.cc-head h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
}
.cc-head .bar {
  width: 4px;
  height: 16px;
  border-radius: 2px;
  background: linear-gradient(135deg, #00a1d6, #00c8a0);
}
.cc-sub {
  font-size: 12px;
  color: var(--text-sub);
  font-weight: 400;
}
.cc-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.cc-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: #fafbfd;
  border-radius: 8px;
  padding: 12px 14px;
  border-left: 4px solid #00a1d6;
  transition: transform 0.2s, box-shadow 0.2s;
}
.cc-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.1);
  background: #fff;
}
.cc-item.type-exam { border-left-color: var(--el-color-danger, #f56c6c); }
.cc-item.type-contest { border-left-color: var(--el-color-warning, #e6a23c); }
.cc-item.type-holiday { border-left-color: var(--el-color-success, #67c23a); }
.cc-item.type-activity { border-left-color: var(--el-color-info, #909399); }
.cc-main {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}
.cc-name {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.cc-date {
  font-size: 12px;
  color: var(--text-sub);
  white-space: nowrap;
}
.cc-note {
  font-size: 12px;
  color: var(--text-sub);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 220px;
}
.cc-page {
  display: flex;
  justify-content: center;
  margin-top: 14px;
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
