<template>
  <div class="mon-page">
    <div class="page-bar">
      <el-button text @click="router.back()">&larr; 返回</el-button>
      <span class="page-title">班长审批</span>
      <span class="page-sub">管理员专属 · 通过后班长方可登录</span>
    </div>

    <el-card shadow="never" class="list-card">
      <div v-loading="loading">
        <div v-for="u in pendingList" :key="u.id" class="row">
          <div class="info">
            <div class="line1">
              <span class="name">{{ u.nickname || u.username }}</span>
              <el-tag type="warning" size="small" round>待审批</el-tag>
            </div>
            <div class="sub">账号：{{ u.username }}<span v-if="u.schoolId"> · 学号 {{ u.schoolId }}</span><span v-if="u.college"> · {{ u.college }}</span><span v-if="u.className"> {{ u.className }}</span></div>
            <div class="sub" v-if="u.createdAt">申请时间：{{ fmtTime(u.createdAt) }}</div>
          </div>
          <div class="ops">
            <el-button size="small" type="success" plain @click="approve(u)">通过</el-button>
            <el-button size="small" type="danger" plain @click="reject(u)">驳回</el-button>
          </div>
        </div>
        <el-empty v-if="!loading && pendingList.length === 0" description="暂无待审批的班长申请" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
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
.mon-page {
  padding-bottom: 30px;
}
.page-bar {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}
.page-title {
  font-size: 20px;
  font-weight: 700;
}
.page-sub {
  font-size: 13px;
  color: var(--text-weak);
}
.list-card {
  border-radius: 12px;
}
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 6px;
  border-bottom: 1px solid #f2f3f5;
}
.row:last-child {
  border-bottom: none;
}
.line1 {
  display: flex;
  align-items: center;
  gap: 10px;
}
.name {
  font-size: 15px;
  font-weight: 600;
}
.sub {
  font-size: 13px;
  color: var(--text-sub);
  margin-top: 6px;
}
.ops {
  flex-shrink: 0;
}
</style>
