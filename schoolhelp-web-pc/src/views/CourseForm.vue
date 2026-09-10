<template>
  <div class="form-page">
    <div class="page-header">
      <el-button text @click="goBack"><el-icon><ArrowLeft /></el-icon></el-button>
      <span class="header-title">{{ isEdit ? '编辑课程' : '创建课程' }}</span>
    </div>

    <div class="form-body">
      <el-card shadow="never">
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <el-form-item label="课程名称" prop="name">
            <el-input v-model="form.name" placeholder="如：软件工程" maxlength="50" />
          </el-form-item>
          <el-form-item label="任课教师" prop="teacherName">
            <el-input v-model="form.teacherName" placeholder="教师姓名" maxlength="20" />
          </el-form-item>
          <el-form-item label="教师主页链接">
            <el-input v-model="form.teacherLink" placeholder="https://...（可选）" />
          </el-form-item>
          <el-form-item label="面向班级" prop="className">
            <el-input v-model="form.className" placeholder="如：计科2301" maxlength="30" />
          </el-form-item>
          <el-form-item label="学期" prop="semester">
            <el-select v-model="form.semester" placeholder="选择学期" style="width: 100%">
              <el-option
                v-for="s in semesterOptions"
                :key="s"
                :label="s"
                :value="s"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="课程简介">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="3"
              maxlength="300"
              show-word-limit
              placeholder="这门课讲什么、适合谁…"
            />
          </el-form-item>
          <el-form-item v-if="!userStore.canManage && !isEdit" label="申请说明（为什么需要这门课？）">
            <el-input
              v-model="form.applyNote"
              type="textarea"
              :rows="2"
              maxlength="200"
              show-word-limit
              placeholder="如：这门课是必修课，但课程库还没有，希望收录…"
            />
          </el-form-item>
          <el-button
            type="primary"
            size="large"
            class="submit-btn"
            :loading="saving"
            @click="handleSave"
          >
            {{ isEdit ? '保存修改' : (userStore.canManage ? '创建课程' : '提交申请') }}
          </el-button>
          <div class="tip-text">
            <template v-if="!isEdit && !userStore.canManage">提交后需管理员审批通过，才会出现在课程库</template>
            <template v-else-if="isEdit">已驳回的申请可修改后重新提交</template>
            <template v-else>创建后仅你和管理员可修改</template>
          </div>
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
import { createCourse, updateCourse, courseDetail } from '@/api/course'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const saving = ref(false)

const isEdit = computed(() => !!route.query.id)
const courseId = computed(() => route.query.id || route.params.id)

// 学期选项：当前学年及前后各 1 个
function genSemesters() {
  const now = new Date()
  const y = now.getFullYear()
  const firstHalf = now.getMonth() >= 6 // 下半年 → 下学期(1)
  const list = []
  for (let i = -1; i <= 1; i++) {
    list.push(`${y + i}-${y + i + 1}-1`)
    list.push(`${y + i}-${y + i + 1}-2`)
  }
  return list
}
const semesterOptions = genSemesters()
const defaultSem = semesterOptions.find((s) => s.startsWith(`${new Date().getFullYear()}-`)) || semesterOptions[1]

const form = reactive({
  name: '',
  teacherName: '',
  teacherLink: '',
  className: '',
  semester: defaultSem,
  description: '',
  applyNote: ''
})

const rules = {
  name: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  teacherName: [{ required: true, message: '请输入教师姓名', trigger: 'blur' }],
  className: [{ required: true, message: '请输入面向班级', trigger: 'blur' }],
  semester: [{ required: true, message: '请选择学期', trigger: 'change' }]
}

async function loadDetail() {
  try {
    const res = await courseDetail(courseId.value)
    const c = res.data
    Object.assign(form, {
      name: c.name || '',
      teacherName: c.teacherName || '',
      teacherLink: c.teacherLink || '',
      className: c.className || '',
      semester: c.semester || defaultSem,
      description: c.description || ''
    })
  } catch (e) { /* */ }
}

async function handleSave() {
  await formRef.value.validate().catch(() => Promise.reject())
  saving.value = true
  try {
    const payload = {
      ...form,
      teacherLink: form.teacherLink.trim() || null,
      description: form.description.trim() || null,
      applyNote: form.applyNote.trim() || null
    }
    if (isEdit.value) {
      await updateCourse(courseId.value, payload)
      ElMessage.success('已保存修改')
    } else if (userStore.canManage) {
      await createCourse(payload)
      ElMessage.success('课程创建成功 🎉')
    } else {
      await createCourse(payload)
      ElMessage.success('申请已提交，等待管理员审批 📮')
    }
    router.back()
  } catch (e) { /* */ } finally {
    saving.value = false
  }
}

function goBack() {
  if (isEdit.value && courseId.value) router.push(`/course/${courseId.value}`)
  else router.back()
}

onMounted(() => {
  if (isEdit.value) loadDetail()
})
</script>

<style scoped>
.form-page {
  min-height: calc(100vh - var(--header-h));
  background: var(--bg-page);
}

.page-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 0 14px;
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

.submit-btn {
  width: 100%;
  letter-spacing: 2px;
  margin-top: 4px;
}

.tip-text {
  text-align: center;
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 10px;
}
</style>
