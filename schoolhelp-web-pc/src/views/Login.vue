<template>
  <div class="login-page">
    <div class="bg-glow g1"></div>
    <div class="bg-glow g2"></div>

    <div class="login-card card">
      <div class="brand">
        <div class="logo-icon">校</div>
        <h1>schoolHelp</h1>
        <p>校园助手 · 哈尔滨学院 · 课程作业一站通</p>
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
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  await formRef.value.validate().catch(() => Promise.reject())
  loading.value = true
  try {
    await userStore.login(form.username.trim(), form.password)
    ElMessage.success('登录成功，欢迎回来 👋')
    const redirect = route.query.redirect
    router.push(typeof redirect === 'string' && redirect ? redirect : '/')
  } catch (e) { /* 已在拦截器提示 */ } finally {
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
  font-size: 26px;
  color: #00a1d6;
}

.brand p {
  font-size: 13px;
  color: var(--text-sub);
  margin-top: 8px;
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
