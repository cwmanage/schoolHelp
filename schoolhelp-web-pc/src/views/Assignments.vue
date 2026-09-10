<template>
  <div class="assign-page">
    <div class="page-head">
      <h2>作业中心</h2>
      <div class="head-right">
        <el-select v-model="filterCourse" placeholder="全部课程" clearable filterable style="width: 220px" @change="applyFilter">
          <el-option v-for="c in allCourses" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-checkbox v-model="onlyPending" @change="applyFilter">只看未提交</el-checkbox>
      </div>
    </div>

    <!-- 临期横幅 -->
    <div v-if="urgents.length" class="urgent-banner">
      <el-icon :size="16"><BellFilled /></el-icon>
      <span class="ub-text">{{ urgents.length }} 门作业即将截止，最急的「{{ urgents[0].courseName }} · {{ urgents[0].title }}」还有 {{ urgents[0].daysLeft }} 天</span>
      <el-button size="small" text type="primary" @click="goFirstUrgent">去看看</el-button>
    </div>

    <!-- 分组列表 -->
    <div v-loading="loading" class="group-list">
      <template v-for="g in visibleGroups" :key="g.courseId">
        <div class="group-head">
          <span class="gh-name" @click="router.push(`/course/${g.courseId}`)">
            <span class="dot"></span>{{ g.courseName }}
            <el-icon class="gh-arrow"><ArrowRight /></el-icon>
          </span>
          <span class="gh-count">{{ g.items.length }} 个作业</span>
        </div>
        <div class="asn-grid">
          <div v-for="a in g.items" :key="a.id" class="asn-card card" @click="openDetail(g, a)">
            <div class="asn-top">
              <span class="asn-title">{{ a.title }}</span>
              <el-tag size="small" :type="tagType(a.deadline)" round>{{ tagText(a.deadline) }}</el-tag>
            </div>
            <div class="asn-content">{{ a.content || '暂无描述' }}</div>
            <div class="asn-bottom">
              <span class="dl"><el-icon><Clock /></el-icon>截止 {{ fmt(a.deadline) }}</span>
              <span class="state" :class="{ done: a.submitted }">
                <el-icon v-if="a.submitted"><CircleCheck /></el-icon>
                {{ a.submitted ? '已提交' : '未提交' }}
              </span>
            </div>
          </div>
        </div>
        <div v-if="g.items.length === 0 && !loading" class="no-items">该课程暂无作业</div>
      </template>

      <el-empty v-if="!loading && visibleGroups.length === 0" description="暂无可显示的作业" />
    </div>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawer" :title="detail?.title || '作业详情'" size="460px">
      <template v-if="detail">
        <div class="d-course">{{ curGroup?.courseName }} · {{ curGroup?.courseId }}</div>
        <div class="d-row"><el-icon><Clock /></el-icon> 截止 {{ fmt(detail.deadline) }}</div>
        <div class="d-label">作业内容</div>
        <p class="d-content">{{ detail.content || '暂无描述' }}</p>
        <div class="d-actions">
          <el-button v-if="!submittedNow" type="primary" size="large" class="w100" @click="doSubmit">
            <el-icon><CircleCheck /></el-icon> 标记已提交
          </el-button>
          <el-button v-else type="warning" size="large" plain class="w100" @click="doUnsubmit">撤销已提交</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { BellFilled, ArrowRight, Clock, CircleCheck } from '@element-plus/icons-vue'
import { courseList, assignmentsByCourse, assignmentDetail } from '@/api/course'
import { urgentAssignments, assignmentSubmitted, markSubmitted, unmarkSubmitted } from '@/api/biz'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const allCourses = ref([])
const groups = ref([])
const urgents = ref([])
const loading = ref(false)
const filterCourse = ref(null)
const onlyPending = ref(false)

const drawer = ref(false)
const detail = ref(null)
const curGroup = ref(null)
const submittedNow = ref(false)

const visibleGroups = computed(() => {
  let list = groups.value
  if (filterCourse.value) {
    list = list.filter((g) => g.courseId === filterCourse.value)
  }
  if (onlyPending.value) {
    list = list
      .map((g) => ({ ...g, items: g.items.filter((a) => !a.submitted) }))
      .filter((g) => g.items.length > 0)
  }
  return list
})

function applyFilter() { /* computed */ }

async function load() {
  loading.value = true
  try {
    const [cs, us] = await Promise.all([courseList({}), urgentAssignments()])
    allCourses.value = cs.data || []
    urgents.value = us.data || []

    const result = []
    for (const c of allCourses.value) {
      try {
        const as = await assignmentsByCourse(c.id)
        const items = (as.data || []).map((a) => ({ ...a, submitted: false }))
        if (!items.length) continue
        result.push({ courseId: c.id, courseName: c.name, items })
      } catch (e) { /* skip */ }
    }
    // 批量提交状态
    for (const g of result) {
      for (const a of g.items) {
        try {
          const r = await assignmentSubmitted(a.id)
          a.submitted = r.data === true
        } catch (e) { a.submitted = false }
      }
    }
    groups.value = result
  } catch (e) {
    groups.value = []
  } finally {
    loading.value = false
  }
}

function goFirstUrgent() {
  const u = urgents.value[0]
  if (!u) return
  const g = groups.value.find((x) => x.courseId === u.courseId)
  const a = g?.items.find((i) => i.id === u.assignmentId)
  if (g && a) openDetail(g, a)
  else router.push(`/course/${u.courseId}`)
}

async function openDetail(g, a) {
  curGroup.value = g
  try {
    const r = await assignmentDetail(a.id)
    detail.value = r.data
    const s = await assignmentSubmitted(a.id)
    submittedNow.value = s.data === true
    drawer.value = true
  } catch (e) { /* */ }
}

async function doSubmit() {
  await markSubmitted(detail.value.id)
  ElMessage.success('已标记提交 🎉')
  drawer.value = false
  load()
}
async function doUnsubmit() {
  await unmarkSubmitted(detail.value.id)
  ElMessage.success('已撤销标记')
  drawer.value = false
  load()
}

function daysLeft(d) {
  return Math.ceil((new Date(d) - new Date()) / 86400000)
}
function tagType(d) {
  const n = daysLeft(d)
  if (n < 0) return 'info'
  if (n <= 2) return 'danger'
  if (n <= 5) return 'warning'
  return 'success'
}
function tagText(d) {
  const n = daysLeft(d)
  if (n < 0) return '已截止'
  if (n === 0) return '今天截止'
  return `${n} 天后截止`
}
function fmt(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').substring(0, 16)
}

onMounted(load)
</script>

<style scoped>
.assign-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}
.page-head h2 {
  font-size: 20px;
}
.head-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.urgent-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  background: linear-gradient(135deg, #fff5f0, #ffefe8);
  border: 1px solid #ffd6c0;
  color: #d25a2a;
  border-radius: 8px;
  padding: 10px 16px;
  font-size: 13px;
}
.ub-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 20px 0 10px;
}
.gh-name {
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: var(--text-main);
}
.gh-name:hover {
  color: var(--primary);
}
.dot {
  width: 4px;
  height: 16px;
  background: var(--primary);
  border-radius: 2px;
}
.gh-arrow {
  font-size: 13px;
  color: var(--text-weak);
}
.gh-count {
  font-size: 12px;
  color: var(--text-weak);
}

.asn-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 12px;
}
.asn-card {
  padding: 14px 16px;
  cursor: pointer;
  border-radius: 8px;
  transition: transform 0.15s, box-shadow 0.15s;
}
.asn-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.1);
}
.asn-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.asn-title {
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.asn-content {
  font-size: 13px;
  color: var(--text-sub);
  margin-top: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.asn-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--text-weak);
  margin-top: 10px;
}
.dl {
  display: flex;
  align-items: center;
  gap: 4px;
}
.state {
  color: var(--warning);
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 3px;
}
.state.done {
  color: var(--success);
}
.no-items {
  color: var(--text-weak);
  font-size: 13px;
  padding: 6px 0;
}

.d-course {
  color: var(--primary);
  font-size: 14px;
  margin-bottom: 8px;
}
.d-row {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--text-sub);
  font-size: 13px;
  margin-bottom: 10px;
}
.d-label {
  font-size: 13px;
  color: var(--text-sub);
  margin: 14px 0 6px;
}
.d-content {
  line-height: 1.7;
  white-space: pre-wrap;
}
.d-actions {
  margin-top: 24px;
}
.w100 {
  width: 100%;
}
</style>
