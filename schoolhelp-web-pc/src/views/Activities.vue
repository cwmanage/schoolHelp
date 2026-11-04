<template>
  <div class="activities-page">
    <div class="page-head">
      <h2>大学活动分析</h2>
      <p class="page-sub">考试 · 竞赛 · 节假日 · 活动一站式总览</p>
    </div>

    <!-- 统计卡 -->
    <div class="stat-row">
      <div class="stat-card" v-for="s in stats" :key="s.label" :style="{ borderColor: s.color }">
        <div class="stat-num" :style="{ color: s.color }">{{ s.count }}</div>
        <div class="stat-label">{{ s.label }}</div>
      </div>
    </div>

    <!-- 类型筛选 -->
    <div class="filter-tabs">
      <el-radio-group v-model="filterType" size="default">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="exam">考试</el-radio-button>
        <el-radio-button value="contest">竞赛</el-radio-button>
        <el-radio-button value="holiday">节假日</el-radio-button>
        <el-radio-button value="activity">活动</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 列表（行式卡片） -->
    <div v-if="pagedEvents.length" class="act-list">
      <div
        class="act-item"
        v-for="e in pagedEvents"
        :key="e.id"
        :class="'type-' + e.eventType"
      >
        <div class="act-date-box">
          <div class="act-date">{{ shortDate(e) }}</div>
          <div class="act-weekday">{{ weekdayOf(e) }}</div>
        </div>
        <div class="act-main">
          <div class="act-title-row">
            <el-tag size="small" :type="calTagType(e.eventType)">{{ calTypeText(e.eventType) }}</el-tag>
            <span class="act-title">{{ e.title }}</span>
          </div>
          <div class="act-note" v-if="e.timeNote">{{ e.timeNote }}</div>
        </div>
        <div class="act-state" :class="stateOf(e).cls">{{ stateOf(e).text }}</div>
      </div>
    </div>
    <el-empty v-else description="暂无相关活动" :image-size="80" />

    <!-- 分页 -->
    <div v-if="filteredEvents.length > pageSize" class="act-page">
      <el-pagination
        layout="prev, pager, next"
        :page-size="pageSize"
        :total="filteredEvents.length"
        v-model:current-page="page"
        background
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { getCalendarEvents } from '@/api/user'

const events = ref([])
const filterType = ref('')
const page = ref(1)
const pageSize = 8

function calTagType(t) {
  return { exam: 'danger', contest: 'warning', holiday: 'success', activity: 'info' }[t] || 'info'
}
function calTypeText(t) {
  return { exam: '考试', contest: '竞赛', holiday: '节假日', activity: '活动' }[t] || '活动'
}
function eventDateOf(e) {
  return e.eventDate || e.dateStart || ''
}
function shortDate(e) {
  const d = eventDateOf(e)
  return d ? d.slice(5).replace('-', '/') : '--'
}
function weekdayOf(e) {
  const d = eventDateOf(e)
  if (!d) return ''
  const day = new Date(d + 'T00:00:00')
  if (isNaN(day)) return ''
  return { 1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五', 6: '周六', 0: '周日' }[day.getDay()] || ''
}
/** 状态：已过 / 进行中(今天) / 未来 N 天 */
function stateOf(e) {
  const d = eventDateOf(e)
  if (!d) return { text: '', cls: '' }
  const today = new Date(new Date().toLocaleDateString('sv-SE') + 'T00:00:00')
  const target = new Date(d + 'T00:00:00')
  if (isNaN(target)) return { text: '', cls: '' }
  const diff = Math.round((target - today) / 86400000)
  if (diff < 0) return { text: '已结束', cls: 'past' }
  if (diff === 0) return { text: '今天', cls: 'today' }
  if (diff <= 7) return { text: `${diff} 天后`, cls: 'soon' }
  return { text: `${diff} 天后`, cls: '' }
}

const filteredEvents = computed(() => {
  const list = filterType.value
    ? events.value.filter((e) => e.eventType === filterType.value)
    : events.value
  return [...list].sort((a, b) => (eventDateOf(a) < eventDateOf(b) ? -1 : 1))
})
const pagedEvents = computed(() => {
  const start = (page.value - 1) * pageSize
  return filteredEvents.value.slice(start, start + pageSize)
})
const stats = computed(() => {
  const count = (t) => events.value.filter((e) => e.eventType === t).length
  return [
    { label: '考试', count: count('exam'), color: '#f56c6c' },
    { label: '竞赛', count: count('contest'), color: '#e6a23c' },
    { label: '节假日', count: count('holiday'), color: '#67c23a' },
    { label: '活动', count: count('activity'), color: '#909399' }
  ]
})

onMounted(async () => {
  try {
    const res = await getCalendarEvents()
    events.value = res.data || []
  } catch {
    events.value = []
  }
})
</script>

<style scoped>
.activities-page {
  padding: 24px 4px 40px;
  max-width: 960px;
  margin: 0 auto;
}
.page-head h2 {
  font-size: 24px;
  margin: 0 0 6px;
}
.page-sub {
  font-size: 12px;
  color: var(--text-sub);
  margin: 0 0 14px;
}
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 18px;
}
.stat-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px 10px;
  text-align: center;
  border-top: 3px solid #00a1d6;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}
.stat-num {
  font-size: 22px;
  font-weight: 700;
}
.stat-label {
  font-size: 11px;
  color: var(--text-sub);
  margin-top: 2px;
}
.filter-tabs {
  margin-bottom: 12px;
  overflow-x: auto;
}
.act-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.act-item {
  display: flex;
  align-items: center;
  gap: 14px;
  background: #fff;
  border-radius: 10px;
  padding: 16px 18px;
  cursor: default;
  transition: transform 0.2s, box-shadow 0.2s;
}
.act-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.1);
}
  border-left: 4px solid #00a1d6;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}
.act-item.type-exam { border-left-color: #f56c6c; }
.act-item.type-contest { border-left-color: #e6a23c; }
.act-item.type-holiday { border-left-color: #67c23a; }
.act-item.type-activity { border-left-color: #909399; }
.act-date-box {
  text-align: center;
  min-width: 48px;
  flex-shrink: 0;
}
.act-date {
  font-size: 16px;
  font-weight: 700;
  color: #333;
}
.act-weekday {
  font-size: 11px;
  color: var(--text-sub);
}
.act-main {
  flex: 1;
  min-width: 0;
}
.act-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.act-title {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.act-note {
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.act-state {
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
  padding: 3px 8px;
  border-radius: 999px;
  background: #f5f7fa;
  color: var(--text-sub);
}
.act-state.today { background: #fef0f0; color: #f56c6c; }
.act-state.soon { background: #fdf6ec; color: #e6a23c; }
.act-state.past { opacity: 0.55; }
.act-page {
  display: flex;
  justify-content: center;
  margin-top: 14px;
}
</style>
