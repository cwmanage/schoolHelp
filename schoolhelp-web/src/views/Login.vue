<template>
  <div class="login-page">
    <div class="login-bg">
      <div class="logo-area">
        <div class="logo-badge">屿</div>
        <h1>课屿</h1>
        <p class="slogan">课程如屿 · 学海同航</p>
        <p class="sub">哈尔滨学院 · 课程作业一站通</p>
      </div>

      <div class="login-card">
        <el-form :model="form" @submit.prevent>
          <el-form-item>
            <el-input
              v-model="form.username"
              placeholder="学号 / 用户名"
              size="large"
              :prefix-icon="User"
              clearable
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-form-item>
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              size="large"
              :prefix-icon="Lock"
              show-password
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-form-item>
            <div class="captcha-row">
              <el-input
                v-model="form.captchaCode"
                placeholder="验证码"
                size="large"
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
            size="large"
            class="login-btn"
            :loading="loading"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form>
        <div class="register-link">
          还没有账号？
          <router-link to="/register">立即注册</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Key } from '@element-plus/icons-vue'
import { fetchCaptcha } from '@/api/user'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const form = reactive({ username: '', password: '', captchaId: '', captchaCode: '' })
const captchaImage = ref('')
const loading = ref(false)

async function loadCaptcha() {
  try {
    const res = await fetchCaptcha()
    form.captchaId = res.data.captchaId
    captchaImage.value = res.data.image
  } catch { /* 加载失败点击重试 */ }
}
loadCaptcha()

async function handleLogin() {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  if (!form.captchaCode.trim()) {
    ElMessage.warning('请输入验证码')
    return
  }
  loading.value = true
  try {
    await userStore.login(form.username.trim(), form.password, form.captchaId, form.captchaCode)
    ElMessage.success('登录成功')
    router.push(route.query.redirect || '/schedule')
  } catch (e) {
    // 错误已由拦截器提示；验证码失败自动刷新
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
  background: linear-gradient(160deg, #3b6ef6 0%, #5b8cff 45%, #8ab4ff 100%);
  display: flex;
  justify-content: center;
}

.login-bg {
  width: 100%;
  max-width: 520px;
  padding: 0 20px;
}

.logo-area {
  text-align: center;
  color: #fff;
  padding: 80px 0 36px;
}

.logo-badge {
  width: 72px;
  height: 72px;
  margin: 0 auto 12px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.35);
  color: #fff;
  font-size: 34px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.logo-area h1 {
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 8px;
  margin-left: 8px; /* 视觉平衡 letter-spacing 末字间距 */
}

.slogan {
  margin-top: 10px;
  font-size: 14px;
  opacity: 0.95;
  letter-spacing: 2px;
}

.sub {
  margin-top: 6px;
  font-size: 12px;
  opacity: 0.75;
}

.login-card {
  background: #fff;
  border-radius: 16px;
  padding: 28px 20px 20px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.12);
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
  width: 112px;
  height: 40px;
  border-radius: 8px;
  border: 1px solid #e4e9f0;
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
  font-size: 16px;
  letter-spacing: 4px;
  border-radius: 8px;
  margin-top: 6px;
}

.register-link {
  text-align: center;
  margin-top: 16px;
  font-size: 13px;
  color: var(--text-sub);
}

.register-link a {
  color: var(--primary);
  text-decoration: none;
  font-weight: 500;
}
</style>
