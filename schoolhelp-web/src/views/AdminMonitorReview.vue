<template>
  <div class="rev-page">
    <div class="page-header">
      <el-button text @click="router.back()"><el-icon><ArrowLeft /></el-icon></el-button>
      <span class="header-title">班长审批</span>
    </div>

    <div v-loading="loading" class="list-wrap">
      <div v-for="u in pendingList" :key="u.id" class="rev-card card">
        <div class="row1">
          <span class="name">{{ u.nickname || u.username }}</span>
          <el-tag type="warning" size="small" round>待审批</el-tag>
        </div>
        <div class="row2">账号：{{ u.username }}<span v-if="u.schoolId"> · 学号 {{ u.schoolId }}</span></div>
        <div class="row2" v-if="u.college || u.className">{{ u.college }} {{ u.className }}</div>
        <div class="row2" v-if="u.createdAt">申请时间：{{ fmtTime(u.createdAt) }}</div>
        <div class="actions">
          <el-button size="small" type="success" plain round @click="approve(u)">通过</el-button>
          <el-button size="small" type="danger" plain round @click="reject(u)">驳回</el-button>
        </div>
      </div>
      <el-empty v-if="!loading && pendingList.length === 0" description="暂无待审批的班长申请" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { pendingMonitors, reviewMonitor } from '@/api/user'

const router = useRouter()
const loading = ref(false)
const pendingList = ref([])

function fmtTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

async function loadPending() {
  loading.value = true
  try {
    const res = await pendingMonitors()
    pendingList.value = res.data || []
  } catch (e) { /* */ } finally {
    loading.value = false
  }
}

async function approve(u) {
  await reviewMonitor(u.id, 1, '')
  ElMessage.success(`已通过《${u.nickname || u.username}》`)
  loadPending()
}

async function reject(u) {
  try {
    const { value } = await ElMessageBox.prompt(
      `驳回《${u.nickname || u.username}》的班长申请，请填写原因：`,
      '驳回申请',
      {
        confirmButtonText: '确认驳回',
        cancelButtonText: '取消',
        inputPlaceholder: '必填：说明驳回原因',
        inputValidator: (v) => (v && v.trim() ? true : '请填写驳回原因')
      }
    )
    await reviewMonitor(u.id, 2, value.trim())
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
