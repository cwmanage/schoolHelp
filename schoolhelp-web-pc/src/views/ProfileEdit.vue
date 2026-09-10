<template>
  <div class="form-page">
    <div class="page-header">
      <el-button text @click="router.back()"><el-icon><ArrowLeft /></el-icon></el-button>
      <span class="header-title">编辑资料</span>
    </div>

    <div class="form-body">
      <!-- 头像 -->
      <div class="avatar-card card">
        <el-avatar :size="64" :src="avatarPreview || undefined">
          {{ (form.nickname || '?').charAt(0).toUpperCase() }}
        </el-avatar>
        <div class="avatar-side">
          <el-upload
            :show-file-list="false"
            :http-request="doUpload"
            accept="image/*"
          >
            <el-button size="small" :icon="Upload">更换头像</el-button>
          </el-upload>
          <div class="avatar-tip">支持 jpg/png，建议方形图</div>
        </div>
      </div>

      <el-card shadow="never">
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <el-form-item label="昵称" prop="nickname">
            <el-input v-model="form.nickname" maxlength="20" />
          </el-form-item>
          <el-form-item label="用户名">
            <el-input :model-value="userStore.userInfo?.username" disabled />
          </el-form-item>
          <el-form-item label="学号">
            <el-input v-model="form.schoolId" placeholder="选填" maxlength="20" />
          </el-form-item>
          <el-form-item label="学院">
            <el-input v-model="form.college" placeholder="如：计算机科学与技术学院" maxlength="30" />
          </el-form-item>
          <el-form-item label="班级">
            <el-input v-model="form.className" placeholder="如：计科2301" maxlength="30" />
          </el-form-item>
          <el-form-item label="个性签名">
            <el-input
              v-model="form.signature"
              type="textarea"
              :rows="2"
              maxlength="60"
              show-word-limit
              placeholder="一句话介绍自己"
            />
          </el-form-item>
          <el-button
            type="primary"
            size="large"
            class="submit-btn"
            :loading="saving"
            @click="handleSave"
          >保存</el-button>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Upload } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { updateProfile, uploadAvatar } from '@/api/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const saving = ref(false)
const avatarPreview = ref('')

const form = reactive({
  nickname: userStore.userInfo?.nickname || '',
  schoolId: userStore.userInfo?.schoolId || '',
  college: userStore.userInfo?.college || '',
  className: userStore.userInfo?.className || '',
  signature: userStore.userInfo?.signature || ''
})

const rules = {
  nickname: [{ required: true, message: '昵称不能为空', trigger: 'blur' }]
}

async function doUpload({ file }) {
  const fd = new FormData()
  fd.append('file', file)
  try {
    const res = await uploadAvatar(fd)
    // 若后端返回 url
    if (res.data?.url) avatarPreview.value = res.data.url
    if (res.data?.avatarUrl) avatarPreview.value = res.data.avatarUrl
    ElMessage.success('头像已更新')
    userStore.fetchProfile()
  } catch (e) { /* */ }
}

async function handleSave() {
  await formRef.value.validate().catch(() => Promise.reject())
  saving.value = true
  try {
    await updateProfile({
      nickname: form.nickname.trim(),
      schoolId: form.schoolId.trim() || null,
      college: form.college.trim() || null,
      className: form.className.trim() || null,
      signature: form.signature.trim() || null
    })
    ElMessage.success('已保存')
    await userStore.fetchProfile()
    router.back()
  } catch (e) { /* */ } finally {
    saving.value = false
  }
}
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

.avatar-card {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
}

.avatar-side {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.avatar-tip {
  font-size: 11px;
  color: var(--text-sub);
}

.form-body .el-card {
  border-radius: 14px;
}

.submit-btn {
  width: 100%;
  letter-spacing: 2px;
}
</style>
