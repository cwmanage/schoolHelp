<template>
  <div class="login-page">
    <div class="bg-glow g1"></div>
    <div class="bg-glow g2"></div>

    <div class="login-card card">
      <div class="brand">
        <div class="logo-icon">屿</div>
        <h1>课屿</h1>
        <p class="tagline">课程如屿 · 学海同航</p>
        <div class="chips">
          <span class="chip">课程</span>
          <span class="chip">作业</span>
          <span class="chip">课表</span>
          <span class="chip">AI 助教</span>
        </div>
        <p class="school">哈尔滨学院 · 课程作业一站通</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="学号 / 用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item prop="captchaCode">
          <div class="captcha-row">
            <el-input
              v-model="form.captchaCode"
              placeholder="验证码"
              maxlength="4"
              :prefix-icon="Key"
              @keyup.enter="handleLogin"
            />
            <img
              v-if="captchaImage"
              :src="captchaImage"
              class="captcha-img"
              title="点击刷新验证码"
              alt="验证码"
              @click="loadCaptcha"
            />
            <div v-else class="captcha-img captcha-loading" @click="loadCaptcha">加载中…</div>
          </div>
        </el-form-item>
        <el-button
          type="primary"
          class="login-btn"
          size="large"
          :loading="loading"
          @click="handleLogin"
        >登 录</el-button>
      </el-form>

      <div class="foot">
        还没有账号？
        <router-link to="/register">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Key } from '@element-plus/icons-vue'
import { fetchCaptcha } from '@/api/user'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '', captchaId: '', captchaCode: '' })
const captchaImage = ref('')

async function loadCaptcha() {
  try {
    const res = await fetchCaptcha()
    form.captchaId = res.data.captchaId
    captchaImage.value = res.data.image
  } catch { /* 加载失败点击重试 */ }
}
loadCaptcha()

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

async function handleLogin() {
  await formRef.value.validate().catch(() => Promise.reject())
  loading.value = true
  try {
    await userStore.login(form.username.trim(), form.password, form.captchaId, form.captchaCode)
    ElMessage.success('登录成功，欢迎回来 👋')
    const redirect = route.query.redirect
    router.push(typeof redirect === 'string' && redirect ? redirect : '/')
  } catch (e) {
    // 验证码错误/过期后自动刷新，无需手动点图
    loadCaptcha()
    form.captchaCode = ''
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #eef7fb 0%, #e8f4ff 100%);
  position: relative;
  overflow: hidden;
}

.bg-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
  opacity: 0.5;
}
.g1 {
  width: 420px;
  height: 420px;
  background: #6ec9e8;
  top: -120px;
  left: -100px;
}
.g2 {
  width: 360px;
  height: 360px;
  background: #7ee0c5;
  bottom: -100px;
  right: -80px;
}

.login-card {
  width: 420px;
  padding: 44px 40px 36px;
  position: relative;
  z-index: 1;
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(0, 161, 214, 0.12);
}

.brand {
  text-align: center;
  margin-bottom: 30px;
}

.logo-icon {
  width: 60px;
  height: 60px;
  margin: 0 auto 14px;
  border-radius: 16px;
  background: linear-gradient(135deg, #00a1d6, #00c8a0);
  color: #fff;
  font-size: 30px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand h1 {
  font-size: 30px;
  color: #00a1d6;
  letter-spacing: 10px;
  margin-left: 10px; /* 视觉平衡 letter-spacing 末字间距 */
}

.brand .tagline {
  font-size: 14px;
  color: var(--text-sub);
  margin-top: 10px;
  letter-spacing: 3px;
}

.brand .chips {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 14px;
}

.brand .chip {
  font-size: 12px;
  padding: 3px 12px;
  border-radius: 999px;
  background: rgba(0, 161, 214, 0.08);
  color: #0086b3;
  border: 1px solid rgba(0, 161, 214, 0.18);
}

.brand .school {
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 14px;
}

.captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;
}
.captcha-row .el-input {
  flex: 1;
}
.captcha-img {
  width: 120px;
  height: 40px;
  border-radius: 6px;
  border: 1px solid #dcdfe6;
  cursor: pointer;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--text-sub);
}
.captcha-loading {
  background: #f5f7fa;
}

.login-btn {
  width: 100%;
  letter-spacing: 6px;
  margin-top: 6px;
  background: linear-gradient(135deg, #00a1d6, #00b3e5);
  border: none;
}

.login-btn:hover {
  background: linear-gradient(135deg, #00b3e5, #00c8a0);
  opacity: 0.95;
}

.foot {
  text-align: center;
  font-size: 13px;
  color: var(--text-sub);
  margin-top: 18px;
}
</style>
