<template>
  <div class="register-page">
    <div class="page-header">
      <el-button text @click="router.back()"><el-icon><ArrowLeft /></el-icon></el-button>
      <span class="header-title">注册账号</span>
    </div>

    <div class="register-body">
      <el-card shadow="never" class="register-card">
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <el-form-item label="我是" prop="role">
            <el-radio-group v-model="form.role" class="role-group">
              <el-radio-button :value="0">同学</el-radio-button>
              <el-radio-button :value="1">班长</el-radio-button>
            </el-radio-group>
            <div class="role-tip">
              {{ form.role === 1 ? '班长可创建课程、发布作业、上传资料' : '同学可查看课表、作业、参与评论' }}
            </div>
          </el-form-item>

          <el-form-item label="用户名 / 学号" prop="username">
            <el-input
              v-model="form.username"
              placeholder="建议使用学号"
              clearable
              @blur="checkUsernameBlur"
            >
              <template #suffix>
                <span v-if="usernameState === 'ok'" class="check-ok">✓ 可用</span>
                <span v-else-if="usernameState === 'dup'" class="check-dup">已被占用</span>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="昵称" prop="nickname">
            <el-input v-model="form.nickname" placeholder="不填默认用用户名" clearable />
          </el-form-item>

          <el-form-item label="学院" prop="college">
            <el-input v-model="form.college" placeholder="如：计算机科学与技术学院" clearable />
          </el-form-item>

          <el-form-item label="班级" prop="className">
            <el-input v-model="form.className" placeholder="如：计科2301" clearable />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="至少8位，含字母和数字"
              show-password
              @input="checkStrength"
            />
            <div class="strength-row" v-if="form.password">
              <div class="strength-bar">
                <div
                  class="strength-fill"
                  :class="'level-' + strengthLevel"
                  :style="{ width: strengthPercent + '%' }"
                ></div>
              </div>
              <span class="strength-text" :class="'text-' + strengthLevel">
                {{ strengthText }}
              </span>
            </div>
          </el-form-item>

          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              placeholder="再次输入密码"
              show-password
            />
          </el-form-item>

          <el-button
            type="primary"
            size="large"
            class="submit-btn"
            :loading="loading"
            @click="handleRegister"
          >
            注 册
          </el-button>
          <div class="login-link">
            已有账号？<router-link to="/login">去登录</router-link>
          </div>
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
import { useUserStore } from '@/stores/user'
import { checkUsername } from '@/api/user'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref()
const form = reactive({
  role: 0,
  username: '',
  nickname: '',
  college: '',
  className: '',
  password: '',
  confirmPassword: ''
})

const usernameState = ref('')
const loading = ref(false)

// 密码强度（0-4）
const strengthLevel = ref(0)
const strengthTexts = ['', '弱', '一般', '较强', '很强']
const strengthColors = ['', '#f56c6c', '#e6a23c', '#67c23a', '#3b6ef6']
const strengthPercent = ref(0)
const strengthText = ref('')

async function checkStrength() {
  if (!form.password) {
    strengthLevel.value = 0
    strengthPercent.value = 0
    strengthText.value = ''
    return
  }
  // 本地估算四档：长度/数字/大小写/特殊字符
  const p = form.password
  let score = 0
  if (p.length >= 8) score++
  if (/[A-Za-z]/.test(p) && /\d/.test(p)) score++
  if (/[A-Z]/.test(p) && /[a-z]/.test(p)) score++
  if (/[^A-Za-z0-9]/.test(p)) score++
  score = Math.max(1, Math.min(4, score))
  strengthLevel.value = score
  strengthPercent.value = score * 25
  strengthText.value = strengthTexts[score]
}

async function checkUsernameBlur() {
  const u = form.username.trim()
  if (!u) return
  try {
    const res = await checkUsername(u)
    usernameState.value = res.data > 0 ? 'dup' : 'ok'
  } catch (e) {
    /* 忽略 */
  }
}

const rules = {
  role: [{ required: true, message: '请选择身份', trigger: 'change' }],
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '长度 3-20 个字符', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]+$/, message: '仅限字母、数字、下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
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
        if (!value) cb(new Error('请再次输入密码'))
        else if (value !== form.password) cb(new Error('两次密码不一致'))
        else cb()
      },
      trigger: 'blur'
    }
  ]
}

async function handleRegister() {
  await formRef.value.validate().catch(() => Promise.reject())
  loading.value = true
  try {
    await userStore.register({
      username: form.username.trim(),
      password: form.password,
      confirmPassword: form.confirmPassword,
      nickname: form.nickname.trim() || undefined,
      college: form.college.trim() || undefined,
      className: form.className.trim() || undefined,
      role: form.role
    })
    if (form.role === 1) {
      ElMessage.success('班长注册申请已提交，待管理员审批通过后方可登录')
    } else {
      ElMessage.success('注册成功，请登录')
    }
    router.push('/login')
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-page {
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

.register-body {
  padding: 14px;
}

.register-card {
  border-radius: 14px;
}

.role-group {
  display: flex;
  width: 100%;
}

.role-group :deep(.el-radio-button) {
  flex: 1;
}

.role-group :deep(.el-radio-button__inner) {
  width: 100%;
}

.role-tip {
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 6px;
  width: 100%;
}

.submit-btn {
  width: 100%;
  margin-top: 4px;
  letter-spacing: 2px;
  font-size: 15px;
}

.login-link {
  text-align: center;
  margin-top: 14px;
  font-size: 13px;
  color: var(--text-sub);
}

.login-link a {
  color: var(--primary);
  text-decoration: none;
}

.check-ok {
  color: var(--success);
  font-size: 12px;
}

.check-dup {
  color: var(--danger);
  font-size: 12px;
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
</style>
