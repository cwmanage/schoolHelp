<template>
  <div class="form-page">
    <div class="page-header">
      <el-button text @click="router.back()"><el-icon><ArrowLeft /></el-icon></el-button>
      <span class="header-title">修改密码</span>
    </div>

    <div class="form-body">
      <el-card shadow="never">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="密码建议至少 8 位，包含字母和数字，强度越高越安全"
          class="pwd-alert"
        />
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <el-form-item label="原密码" prop="oldPassword">
            <el-input
              v-model="form.oldPassword"
              type="password"
              show-password
              placeholder="输入当前密码"
            />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input
              v-model="form.newPassword"
              type="password"
              show-password
              placeholder="至少 8 位，含字母和数字"
              @input="checkStrength"
            />
            <div class="strength-row" v-if="form.newPassword">
              <div class="strength-bar">
                <div
                  class="strength-fill"
                  :class="'level-' + strengthLevel"
                  :style="{ width: strengthLevel * 25 + '%' }"
                ></div>
              </div>
              <span class="strength-text" :class="'text-' + strengthLevel">
                {{ ['', '弱', '一般', '较强', '很强'][strengthLevel] }}
              </span>
            </div>
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              show-password
              placeholder="再次输入新密码"
            />
          </el-form-item>
          <el-button
            type="primary"
            size="large"
            class="submit-btn"
            :loading="saving"
            @click="handleSave"
          >确认修改</el-button>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { changePassword } from '@/api/user'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const saving = ref(false)

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const strengthLevel = ref(0)

function checkStrength() {
  const p = form.newPassword
  if (!p) { strengthLevel.value = 0; return }
  let score = 0
  if (p.length >= 8) score++
  if (/[A-Za-z]/.test(p) && /\d/.test(p)) score++
  if (/[A-Z]/.test(p) && /[a-z]/.test(p)) score++
  if (/[^A-Za-z0-9]/.test(p)) score++
  strengthLevel.value = Math.max(1, Math.min(4, score))
}

const rules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, message: '密码至少 8 位', trigger: 'blur' },
    {
      validator: (rule, value, cb) => {
        if (value && !/(?=.*[A-Za-z])(?=.*\d)/.test(value)) {
          cb(new Error('需同时包含字母和数字'))
        } else cb()
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    {
      validator: (rule, value, cb) => {
        if (!value) cb(new Error('请再次输入新密码'))
        else if (value !== form.newPassword) cb(new Error('两次密码不一致'))
        else cb()
      },
      trigger: 'blur'
    }
  ]
}

async function handleSave() {
  await formRef.value.validate().catch(() => Promise.reject())
  saving.value = true
  try {
    await changePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
      confirmPassword: form.confirmPassword
    })
    ElMessage.success('密码修改成功，请重新登录')
    userStore.logout()
    router.push('/login')
  } catch (e) {
    // 拦截器提示
  } finally {
    saving.value = false
  }
}
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

.pwd-alert {
  margin-bottom: 14px;
}

.form-body .el-card {
  border-radius: 14px;
}

.strength-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  width: 100%;
}

.strength-bar {
  flex: 1;
  height: 4px;
  background: #eee;
  border-radius: 2px;
  overflow: hidden;
}

.strength-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.3s;
}

.level-1 { background: var(--danger); }
.level-2 { background: var(--warning); }
.level-3 { background: #95d475; }
.level-4 { background: var(--primary); }

.strength-text {
  font-size: 12px;
  width: 32px;
}

.text-1 { color: var(--danger); }
.text-2 { color: var(--warning); }
.text-3 { color: var(--success); }
.text-4 { color: var(--primary); }

.submit-btn {
  width: 100%;
  letter-spacing: 2px;
}
</style>
