<template>
  <div class="login-page">
    <div class="login-bg">
      <div class="logo-area">
        <div class="logo-icon">📘</div>
        <h1>schoolHelp</h1>
        <p class="slogan">校园助手 · 哈尔滨学院</p>
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
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const form = reactive({ username: '', password: '' })
const loading = ref(false)

async function handleLogin() {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await userStore.login(form.username.trim(), form.password)
    ElMessage.success('登录成功')
    router.push(route.query.redirect || '/schedule')
  } catch (e) {
    // 错误已由拦截器提示
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

.logo-icon {
  font-size: 56px;
  margin-bottom: 10px;
}

.logo-area h1 {
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 1px;
}

.slogan {
  margin-top: 8px;
  font-size: 14px;
  opacity: 0.9;
}

.login-card {
  background: #fff;
  border-radius: 16px;
  padding: 28px 20px 20px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.12);
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
