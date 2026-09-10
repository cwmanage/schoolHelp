<template>
  <div class="rev-page">
    <div class="page-header">
      <el-button text @click="router.back()"><el-icon><ArrowLeft /></el-icon></el-button>
      <span class="header-title">审批管理</span>
    </div>

    <el-tabs v-model="tab" class="rev-tabs" @tab-change="switchTab">
      <el-tab-pane label="课程申请" name="course">
        <div v-loading="loading" class="list-wrap">
          <div v-for="c in pendingCourses" :key="c.id" class="rev-card card">
            <div class="row1">
              <span class="name">{{ c.name }}</span>
              <el-tag type="warning" size="small" round>待审批</el-tag>
            </div>
            <div class="row2">{{ c.teacherName }} · {{ c.className }} · {{ c.semester }}</div>
            <div class="row2" v-if="c.description">{{ c.description }}</div>
            <div class="row2">👤 申请人 ID：{{ c.applyUserId }}</div>
            <div class="row2" v-if="c.applyNote">📝 {{ c.applyNote }}</div>
            <div class="actions">
              <el-button size="small" type="success" plain round @click="approveCourse(c)">通过</el-button>
              <el-button size="small" type="danger" plain round @click="rejectCourse(c)">驳回</el-button>
            </div>
          </div>
          <el-empty v-if="!loading && pendingCourses.length === 0" description="暂无待审批课程" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="作业申请" name="assignment">
        <div v-loading="loading" class="list-wrap">
          <div v-for="a in pendingAssignments" :key="a.id" class="rev-card card">
            <div class="row1">
              <span class="name">{{ a.title }}</span>
              <el-tag type="warning" size="small" round>待审批</el-tag>
            </div>
            <div class="row2">所属课程 ID：{{ a.courseId }}</div>
            <div class="row2" v-if="a.content">{{ a.content }}</div>
            <div class="row2">👤 申请人 ID：{{ a.applyUserId }}</div>
            <div class="row2" v-if="a.applyNote">📝 {{ a.applyNote }}</div>
            <div class="actions">
              <el-button size="small" type="success" plain round @click="approveAssignment(a)">通过</el-button>
              <el-button size="small" type="danger" plain round @click="rejectAssignment(a)">驳回</el-button>
            </div>
          </div>
          <el-empty v-if="!loading && pendingAssignments.length === 0" description="暂无待审批作业" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="资料申请" name="material">
        <div v-loading="loading" class="list-wrap">
          <div v-for="m in pendingMaterials" :key="m.id" class="rev-card card">
            <div class="row1">
              <span class="name">{{ m.title || m.fileName }}</span>
              <el-tag type="warning" size="small" round>待审批</el-tag>
            </div>
            <div class="row2">所属课程 ID：{{ m.courseId }} · {{ m.fileType || '未知类型' }} · {{ fmtSize(m.fileSize) }}</div>
            <div class="row2">👤 申请人 ID：{{ m.applyUserId }}</div>
            <div class="actions">
              <el-button size="small" type="success" plain round @click="approveMaterial(m)">通过</el-button>
              <el-button size="small" type="danger" plain round @click="rejectMaterial(m)">驳回</el-button>
            </div>
          </div>
          <el-empty v-if="!loading && pendingMaterials.length === 0" description="暂无待审批资料" />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import {
  adminCourseList, reviewCourse,
  adminAssignmentList, reviewAssignment,
  adminMaterialList, reviewMaterial
} from '@/api/course'

const router = useRouter()
const tab = ref('course')
const loading = ref(false)
const pendingCourses = ref([])
const pendingAssignments = ref([])
const pendingMaterials = ref([])

function fmtSize(bytes) {
  if (!bytes) return '-'
  if (bytes > 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + ' MB'
  if (bytes > 1024) return (bytes / 1024).toFixed(0) + ' KB'
  return bytes + ' B'
}

async function loadPending() {
  loading.value = true
  try {
    const [c, a, m] = await Promise.all([
      adminCourseList(0), adminAssignmentList(0), adminMaterialList(0)
    ])
    pendingCourses.value = c.data || []
    pendingAssignments.value = a.data || []
    pendingMaterials.value = m.data || []
  } catch (e) { /* */ } finally {
    loading.value = false
  }
}

function switchTab() { /* 已统一加载 */ }

// ---- 通用驳回输入 ----
async function askNote(name) {
  const { value } = await ElMessageBox.prompt(`驳回「${name}」，请填写原因：`, '驳回申请', {
    confirmButtonText: '确认驳回',
    cancelButtonText: '取消',
    inputPlaceholder: '必填：说明驳回原因',
    inputValidator: (v) => (v && v.trim() ? true : '请填写驳回原因')
  })
  return value.trim()
}

async function approveCourse(c) {
  await reviewCourse(c.id, 1, '')
  ElMessage.success(`已通过「${c.name}」`)
  loadPending()
}
async function rejectCourse(c) {
  try {
    const note = await askNote(c.name)
    await reviewCourse(c.id, 2, note)
    ElMessage.success('已驳回')
    loadPending()
  } catch (e) { /* 取消 */ }
}

async function approveAssignment(a) {
  await reviewAssignment(a.id, 1, '')
  ElMessage.success(`已通过「${a.title}」`)
  loadPending()
}
async function rejectAssignment(a) {
  try {
    const note = await askNote(a.title)
    await reviewAssignment(a.id, 2, note)
    ElMessage.success('已驳回')
    loadPending()
  } catch (e) { /* 取消 */ }
}

async function approveMaterial(m) {
  await reviewMaterial(m.id, 1, '')
  ElMessage.success('已通过')
  loadPending()
}
async function rejectMaterial(m) {
  try {
    const note = await askNote(m.title || m.fileName)
    await reviewMaterial(m.id, 2, note)
    ElMessage.success('已驳回')
    loadPending()
  } catch (e) { /* 取消 */ }
}

onMounted(loadPending)
</script>

<style scoped>
.rev-page {
  min-height: 100vh;
  background: var(--bg-page);
}
.page-header {
  background: #fff;
  padding: 12px 8px;
  display: flex;
  align-items: center;
  gap: 4px;
  border-bottom: 1px solid #f0f0f0;
  position: sticky;
  top: 0;
  z-index: 10;
}
.header-title {
  font-size: 16px;
  font-weight: 600;
}
.rev-tabs {
  padding: 0 12px;
  background: #fff;
}
.list-wrap {
  padding: 10px 12px 20px;
  min-height: 200px;
}
.rev-card {
  padding: 12px 14px;
  margin-bottom: 10px;
  border-radius: 12px;
}
.row1 {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.name {
  font-size: 15px;
  font-weight: 600;
}
.row2 {
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 6px;
  word-break: break-all;
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
}
</style>
