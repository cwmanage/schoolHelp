<template>
  <div class="assign-page">
    <!-- 临期红色条幅 -->
    <div
      v-if="urgents.length > 0"
      class="banner-urgent"
      @click="goToFirstUrgent"
    >
      <div class="urgent-left">
        <el-icon :size="18"><BellFilled /></el-icon>
        <span class="urgent-title">
          {{ urgents.length }} 门作业即将截止
        </span>
      </div>
      <div class="urgent-right">
        <span>{{ urgents[0].daysLeft }} 天后截止</span>
        <el-icon><ArrowRight /></el-icon>
      </div>
    </div>

    <!-- 课表作业列表（每门课一个分组） -->
    <div v-loading="loading">
      <!-- 我的课表课程作业 -->
      <template v-if="grouped.length">
        <div
          v-for="g in grouped"
          :key="g.courseId"
          class="assign-group"
        >
          <div class="group-head">
            <div class="group-title">
              <span class="dot-mark"></span>
              {{ g.courseName }}
            </div>
            <el-button
              v-if="userStore.canManage"
              text
              type="primary"
              size="small"
              :icon="Plus"
              @click="router.push(`/assignment/create/${g.courseId}`)"
            >发布</el-button>
          </div>
          <div class="assign-card card" v-for="a in g.items" :key="a.id">
            <div class="assign-main" @click="openDetail(g, a)">
              <div class="assign-top">
                <span class="assign-title">{{ a.title }}</span>
                <el-tag
                  size="small"
                  :type="deadlineType(a.deadline)"
                  effect="light"
                  round
                >{{ daysLeftText(a.deadline) }}</el-tag>
              </div>
              <div class="assign-content">{{ a.content || '暂无描述' }}</div>
              <div class="assign-bottom">
                <span class="deadline">
                  <el-icon><Clock /></el-icon>
                  截止 {{ formatTime(a.deadline) }}
                </span>
                <span class="submit-state" :class="{ done: a.submitted }">
                  {{ a.submitted ? '✓ 已提交' : '未提交' }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </template>

      <el-empty
        v-else-if="!loading"
        description="课表里还没有课程，去添加吧"
      >
        <el-button type="primary" @click="router.push('/schedule')">
          去课表添加
        </el-button>
      </el-empty>
    </div>

    <!-- 作业详情抽屉 -->
    <el-drawer
      v-model="detailVisible"
      :title="detail?.assignment?.title || '作业详情'"
      size="92%"
      direction="btt"
    >
      <div v-if="detail" class="detail-body">
        <div class="detail-course">{{ detail.courseName }}</div>
        <div class="detail-row">
          <el-icon><Clock /></el-icon>
          <span>截止：{{ formatTime(detail.assignment.deadline) }}</span>
        </div>
        <div class="detail-content card">
          <div class="detail-label">作业内容</div>
          <p>{{ detail.assignment.content || '无' }}</p>
        </div>

        <div class="submit-area">
          <el-button
            v-if="!detail.submitted"
            type="primary"
            size="large"
            class="submit-btn"
            @click="handleSubmit"
          >
            <el-icon><CircleCheck /></el-icon> 标记已完成
          </el-button>
          <el-button
            v-else
            size="large"
            class="submit-btn"
            plain
            type="warning"
            @click="handleUnsubmit"
          >
            撤销已提交标记
          </el-button>
          <div class="submit-tip">
            {{ detail.submitted ? '已完成，注意核对作业质量' : '完成后点击标记，首页不再提醒' }}
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  BellFilled, ArrowRight, Plus, Clock, CircleCheck
} from '@element-plus/icons-vue'
import { urgentAssignments, markSubmitted, unmarkSubmitted, assignmentSubmitted } from '@/api/biz'
import { assignmentsByCourse, assignmentDetail } from '@/api/course'
import { mySchedules } from '@/api/user'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const urgents = ref([])
const groups = ref([]) // [{ courseId, courseName, items: [{...assignment, submitted, deadline}] }]
const loading = ref(false)

const detailVisible = ref(false)
const detail = ref(null)

const grouped = computed(() => groups.value)

async function loadData() {
  loading.value = true
  try {
    // 1. 临期聚合
    const ur = await urgentAssignments()
    urgents.value = ur.data || []

    // 2. 我的课表 → 每门课的作业
    const semRes = await mySchedules(null)
    const schedules = semRes.data || []
    // 去重课表里的课程
    const courseIds = [...new Set(schedules.filter((s) => s.courseId).map((s) => s.courseId))]

    const result = []
    for (const cid of courseIds) {
      try {
        const as = await assignmentsByCourse(cid)
        const items = as.data || []
        if (items.length === 0) continue
        const sch = schedules.find((s) => s.courseId === cid)
        result.push({
          courseId: cid,
          courseName: sch?.courseName || '课程' + cid,
          items: items.map((a) => ({ ...a, submitted: false }))
        })
      } catch (e) {
        /* skip */
      }
    }
    // 获取提交状态（逐个）
    for (const g of result) {
      for (const a of g.items) {
        try {
          const sr = await assignmentSubmittedLocal(a.id)
          a.submitted = sr
        } catch (e) {
          a.submitted = false
        }
      }
    }
    groups.value = result
  } catch (e) {
    groups.value = []
  } finally {
    loading.value = false
  }
}

async function assignmentSubmittedLocal(id) {
  const res = await assignmentSubmitted(id)
  return res.data === true
}

function goToFirstUrgent() {
  const g = groups.value.find((x) => x.courseId === urgents.value[0]?.courseId)
  const a = g?.items.find((i) => i.id === urgents.value[0]?.assignmentId)
  if (a) openDetail(g, a)
}

async function openDetail(g, a) {
  try {
    const res = await assignmentDetail(a.id)
    const submitted = await assignmentSubmittedLocal(a.id)
    detail.value = {
      courseName: g.courseName,
      assignment: res.data,
      submitted
    }
    detailVisible.value = true
  } catch (e) {
    /* ignore */
  }
}

async function handleSubmit() {
  try {
    await markSubmitted(detail.value.assignment.id)
    ElMessage.success('已标记完成 🎉')
    detailVisible.value = false
    loadData()
  } catch (e) { /* ignore */ }
}

async function handleUnsubmit() {
  try {
    await unmarkSubmitted(detail.value.assignment.id)
    ElMessage.success('已撤销')
    detailVisible.value = false
    loadData()
  } catch (e) { /* ignore */ }
}

function daysLeftText(deadline) {
  const diff = daysLeft(deadline)
  if (diff < 0) return `已逾期 ${Math.abs(diff)} 天`
  if (diff === 0) return '今天截止'
  if (diff <= 3) return `${diff} 天后截止`
  return `${diff} 天后截止`
}

function deadlineType(deadline) {
  const diff = daysLeft(deadline)
  if (diff < 0) return 'danger'
  if (diff <= 3) return 'warning'
  return 'success'
}

function daysLeft(deadline) {
  const d = new Date(deadline)
  const now = new Date()
  return Math.ceil((d - now) / (1000 * 60 * 60 * 24))
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}月${d.getDate()}日 ${p(d.getHours())}:${p(d.getMinutes())}`
}

onMounted(loadData)
</script>

<style scoped>
.assign-page {
  min-height: 100%;
}

.banner-urgent .urgent-left,
.banner-urgent .urgent-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.urgent-title {
  font-weight: 600;
  font-size: 14px;
}

.urgent-right {
  font-size: 12px;
  opacity: 0.95;
}

.assign-group {
  margin-bottom: 4px;
}

.group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 14px 2px 8px;
}

.group-title {
  font-size: 15px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}

.dot-mark {
  width: 4px;
  height: 14px;
  border-radius: 2px;
  background: var(--primary);
}

.assign-card {
  margin-bottom: 10px;
  padding: 12px 14px;
}

.assign-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.assign-title {
  font-size: 15px;
  font-weight: 600;
}

.assign-content {
  font-size: 13px;
  color: var(--text-sub);
  margin: 6px 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.assign-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 8px;
}

.deadline {
  display: flex;
  align-items: center;
  gap: 4px;
}

.submit-state {
  color: var(--warning);
  font-weight: 500;
}

.submit-state.done {
  color: var(--success);
}

.detail-body {
  padding: 4px;
}

.detail-course {
  font-size: 14px;
  color: var(--primary);
  margin-bottom: 8px;
  font-weight: 500;
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-sub);
  margin-bottom: 12px;
}

.detail-content {
  padding: 12px 14px;
}

.detail-label {
  font-size: 13px;
  color: var(--text-sub);
  margin-bottom: 6px;
}

.detail-content p {
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.submit-area {
  margin-top: 20px;
  text-align: center;
}

.submit-btn {
  width: 100%;
}

.submit-tip {
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 8px;
}
</style>
