<template>
  <div class="app-page">
    <div class="page-header">
      <el-button text @click="router.back()"><el-icon><ArrowLeft /></el-icon></el-button>
      <span class="header-title">我的申请</span>
    </div>

    <el-tabs v-model="tab" class="app-tabs" @tab-change="load">
      <el-tab-pane label="课程" name="course">
        <div v-loading="loading" class="list-wrap">
          <div v-for="c in courseApps" :key="c.id" class="app-card card">
            <div class="row1">
              <span class="name">{{ c.name }}</span>
              <el-tag size="small" :type="statusTag(c.status).type" round>{{ statusTag(c.status).text }}</el-tag>
            </div>
            <div class="row2" v-if="c.teacherName || c.className">
              {{ c.teacherName }} · {{ c.className }} · {{ c.semester }}
            </div>
            <div class="row2" v-if="c.applyNote">📝 申请说明：{{ c.applyNote }}</div>
            <div class="row2 reject" v-if="c.status === 2 && c.reviewNote">❌ 驳回原因：{{ c.reviewNote }}</div>
            <div class="row2" v-else-if="c.status === 1 && c.reviewNote">✅ 审批意见：{{ c.reviewNote }}</div>
            <div class="actions" v-if="c.status !== 1">
              <el-button size="small" type="primary" plain round
                v-if="c.status === 2"
                @click="router.push(`/course/create?id=${c.id}`)">修改重提</el-button>
              <el-button size="small" type="danger" plain round @click="delCourse(c)">删除申请</el-button>
            </div>
          </div>
          <el-empty v-if="!loading && courseApps.length === 0" description="暂无课程申请" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="作业" name="assignment">
        <div v-loading="loading" class="list-wrap">
          <div v-for="a in assignmentApps" :key="a.id" class="app-card card">
            <div class="row1">
              <span class="name">{{ a.title }}</span>
              <el-tag size="small" :type="statusTag(a.status).type" round>{{ statusTag(a.status).text }}</el-tag>
            </div>
            <div class="row2">所属课程 ID：{{ a.courseId }}</div>
            <div class="row2" v-if="a.content">{{ a.content }}</div>
            <div class="row2" v-if="a.applyNote">📝 申请说明：{{ a.applyNote }}</div>
            <div class="row2 reject" v-if="a.status === 2 && a.reviewNote">❌ 驳回原因：{{ a.reviewNote }}</div>
            <div class="actions" v-if="a.status !== 1">
              <el-button size="small" type="primary" plain round
                v-if="a.status === 2"
                @click="router.push(`/assignment/create/${a.courseId}?id=${a.id}`)">修改重提</el-button>
              <el-button size="small" type="danger" plain round @click="delAssignment(a)">删除申请</el-button>
            </div>
          </div>
          <el-empty v-if="!loading && assignmentApps.length === 0" description="暂无作业申请" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="资料" name="material">
        <div v-loading="loading" class="list-wrap">
          <div v-for="m in materialApps" :key="m.id" class="app-card card">
            <div class="row1">
              <span class="name">{{ m.title || m.fileName }}</span>
              <el-tag size="small" :type="statusTag(m.status).type" round>{{ statusTag(m.status).text }}</el-tag>
            </div>
            <div class="row2">所属课程 ID：{{ m.courseId }} · {{ m.fileType || '未知类型' }}</div>
            <div class="row2 reject" v-if="m.status === 2 && m.reviewNote">❌ 驳回原因：{{ m.reviewNote }}</div>
            <div class="actions" v-if="m.status !== 1">
              <el-button size="small" type="danger" plain round @click="delMaterial(m)">删除申请</el-button>
            </div>
          </div>
          <el-empty v-if="!loading && materialApps.length === 0" description="暂无资料申请" />
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
  myCourseApplications, myAssignmentApplications, myMaterialApplications,
  deleteCourse, deleteAssignment, deleteMaterial
} from '@/api/course'

const router = useRouter()
const tab = ref('course')
const loading = ref(false)
const courseApps = ref([])
const assignmentApps = ref([])
const materialApps = ref([])

function statusTag(s) {
  if (s === 0) return { type: 'warning', text: '待审批' }
  if (s === 1) return { type: 'success', text: '已通过' }
  if (s === 2) return { type: 'danger', text: '已驳回' }
  return { type: 'info', text: '未知' }
}

async function load() {
  loading.value = true
  try {
    const [c, a, m] = await Promise.all([
      myCourseApplications(), myAssignmentApplications(), myMaterialApplications()
    ])
    courseApps.value = c.data || []
    assignmentApps.value = a.data || []
    materialApps.value = m.data || []
  } catch (e) { /* */ } finally {
    loading.value = false
  }
}

async function delCourse(c) {
  try {
    await ElMessageBox.confirm(`删除课程申请「${c.name}」？`, '提示', { type: 'warning' })
    await deleteCourse(c.id)
    ElMessage.success('已删除')
    load()
  } catch (e) { /* */ }
}
async function delAssignment(a) {
  try {
    await ElMessageBox.confirm(`删除作业申请「${a.title}」？`, '提示', { type: 'warning' })
    await deleteAssignment(a.id)
    ElMessage.success('已删除')
    load()
  } catch (e) { /* */ }
}
async function delMaterial(m) {
  try {
    await ElMessageBox.confirm(`删除资料申请「${m.title || m.fileName}」？`, '提示', { type: 'warning' })
    await deleteMaterial(m.id)
    ElMessage.success('已删除')
    load()
  } catch (e) { /* */ }
}

onMounted(load)
</script>

<style scoped>
.app-page {
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
.app-tabs {
  padding: 0 12px;
  background: #fff;
}
.list-wrap {
  padding: 10px 12px 20px;
  min-height: 200px;
}
.app-card {
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
.row2.reject {
  color: #e65b5b;
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
}
</style>
