<template>
  <div class="form-page">
    <div class="page-header">
      <el-button text @click="router.back()"><el-icon><ArrowLeft /></el-icon></el-button>
      <span class="header-title">{{ isEdit ? '编辑作业' : (userStore.canManage ? '发布作业' : '申请发布') }}</span>
    </div>

    <div class="form-body">
      <el-card shadow="never">
        <div class="course-tag" v-if="courseName">
          课程：<b>{{ courseName }}</b>
        </div>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <el-form-item label="作业标题" prop="title">
            <el-input v-model="form.title" placeholder="如：第三章课后习题" maxlength="100" />
          </el-form-item>
          <el-form-item label="截止时间" prop="deadline">
            <el-date-picker
              v-model="form.deadline"
              type="datetime"
              placeholder="选择截止时间"
              style="width: 100%"
              :disabled-date="disablePast"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DDTHH:mm:ss"
            />
          </el-form-item>
          <el-form-item label="作业内容">
            <el-input
              v-model="form.content"
              type="textarea"
              :rows="5"
              maxlength="1000"
              show-word-limit
              placeholder="作业要求、提交方式…"
            />
          </el-form-item>
          <el-form-item v-if="!userStore.canManage" label="申请说明（为什么发布这门作业？）">
            <el-input
              v-model="form.applyNote"
              type="textarea"
              :rows="2"
              maxlength="200"
              show-word-limit
              placeholder="如：老师课后布置的作业，同步到课程库…"
            />
          </el-form-item>
          <el-button
            type="primary"
            size="large"
            class="submit-btn"
            :loading="saving"
            @click="handleSave"
          >
            {{ isEdit ? '保存修改' : (userStore.canManage ? '发布作业' : '提交申请') }}
          </el-button>
          <div v-if="!isEdit && !userStore.canManage" class="tip-text">提交后需管理员审批通过，同学才可见</div>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { createAssignment, updateAssignment, assignmentDetail, courseDetail } from '@/api/course'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const saving = ref(false)

const isEdit = computed(() => !!route.query.id)
const courseId = computed(() => route.params.courseId)
const courseName = ref('')

// 发布后默认 7 天后
function defaultDeadline() {
  const d = new Date()
  d.setDate(d.getDate() + 7)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:00`
}

const form = reactive({
  title: '',
  deadline: defaultDeadline(),
  content: '',
  applyNote: ''
})

const rules = {
  title: [{ required: true, message: '请输入作业标题', trigger: 'blur' }],
  deadline: [{ required: true, message: '请选择截止时间', trigger: 'change' }]
}

function disablePast(date) {
  return date.getTime() < Date.now() - 86400000
}

async function loadCourse() {
  try {
    const res = await courseDetail(courseId.value)
    courseName.value = res.data?.name || ''
  } catch (e) { /* */ }
}

async function loadAssignment() {
  try {
    const res = await assignmentDetail(route.query.id)
    const a = res.data
    Object.assign(form, {
      title: a.title || '',
      deadline: a.deadline || defaultDeadline(),
      content: a.content || ''
    })
  } catch (e) { /* */ }
}

async function handleSave() {
  await formRef.value.validate().catch(() => Promise.reject())
  saving.value = true
  try {
    const payload = { ...form, content: form.content.trim() || null, applyNote: form.applyNote.trim() || null }
    if (isEdit.value) {
      await updateAssignment(route.query.id, payload)
      ElMessage.success('已保存')
    } else if (userStore.canManage) {
      await createAssignment({ ...payload, courseId: Number(courseId.value) })
      ElMessage.success('作业已发布 🎉')
    } else {
      await createAssignment({ ...payload, courseId: Number(courseId.value) })
      ElMessage.success('申请已提交，等待管理员审批 📮')
    }
    router.push(`/course/${courseId.value}`)
  } catch (e) { /* */ } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadCourse()
  if (isEdit.value) loadAssignment()
})
</script>

<style scoped>
.form-page {
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

.form-body {
  padding: 14px;
}

.form-body .el-card {
  border-radius: 14px;
}

.course-tag {
  font-size: 13px;
  color: var(--text-sub);
  margin-bottom: 14px;
  padding: 8px 12px;
  background: var(--primary-light);
  border-radius: 8px;
}

.submit-btn {
  width: 100%;
  letter-spacing: 2px;
}
.tip-text {
  text-align: center;
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 10px;
}
</style>
