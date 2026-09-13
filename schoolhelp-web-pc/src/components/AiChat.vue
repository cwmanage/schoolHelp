<template>
  <div class="ai-chat-entry">
    <!-- 浮动按钮 -->
    <button v-if="userStore.isLoggedIn" class="ai-fab" title="课屿 AI 助教" @click="open = true">
      <el-icon :size="26"><ChatDotRound /></el-icon>
    </button>

    <!-- 对话抽屉 -->
    <el-drawer v-model="open" size="420px" class="ai-drawer" :with-header="false">
      <div class="ai-panel">
        <div class="ai-head">
          <div class="ai-head-brand">
            <span class="ai-logo">屿</span>
            <div>
              <div class="ai-title">课屿 AI 助教</div>
              <div class="ai-sub">免费大模型驱动 · 回答仅供参考</div>
            </div>
          </div>
          <el-button link size="small" @click="clearHistory">清空记录</el-button>
        </div>

        <div ref="listRef" class="ai-list">
          <div class="ai-msg welcome">
            你好，我是课屿 AI 助教 👋<br />
            课程疑问、作业思路、校园生活都可以问我～
          </div>
          <div
            v-for="(m, i) in history"
            :key="i"
            class="ai-msg"
            :class="m.role"
          >{{ m.content }}</div>
          <div v-if="typing" class="ai-msg assistant typing">思考中…</div>
          <div v-if="errorText" class="ai-msg error">{{ errorText }}</div>
        </div>

        <div class="ai-input">
          <el-input
            v-model="draft"
            type="textarea"
            :rows="2"
            maxlength="1000"
            resize="none"
            placeholder="输入问题，Enter 发送 / Shift+Enter 换行"
            @keydown.enter.exact.prevent="send"
          />
          <el-button type="primary" :loading="typing" @click="send">发送</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, nextTick, watch } from 'vue'
import { ChatDotRound } from '@element-plus/icons-vue'
import { aiChat } from '@/api/user'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const open = ref(false)
const draft = ref('')
const typing = ref(false)
const errorText = ref('')
const history = ref([])
const listRef = ref()

const CONTEXT_COUNT = 6   // 携带最近 6 条做上下文
const MAX_SAVED = 50      // 本地最多保存条数

function storeKey() {
  return `keyu_ai_history_${userStore.userInfo?.id ?? 'anon'}`
}

function loadHistory() {
  try {
    return JSON.parse(localStorage.getItem(storeKey()) || '[]')
  } catch {
    return []
  }
}

function saveHistory() {
  try {
    localStorage.setItem(storeKey(), JSON.stringify(history.value.slice(-MAX_SAVED)))
  } catch { /* 存储满等异常忽略 */ }
}

function clearHistory() {
  history.value = []
  errorText.value = ''
  saveHistory()
}

function scrollBottom() {
  nextTick(() => {
    if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
  })
}

watch(open, (v) => {
  if (v) {
    history.value = loadHistory()
    scrollBottom()
  }
})

async function send() {
  const text = draft.value.trim()
  if (!text || typing.value) return
  draft.value = ''
  errorText.value = ''
  history.value.push({ role: 'user', content: text })
  typing.value = true
  scrollBottom()
  try {
    const payload = history.value.slice(-CONTEXT_COUNT).map((m) => ({ role: m.role, content: m.content }))
    const res = await aiChat(payload)
    history.value.push({ role: 'assistant', content: res.data.reply })
  } catch (e) {
    errorText.value = e.message || '请求失败，请稍后再试'
  } finally {
    typing.value = false
    saveHistory()
    scrollBottom()
  }
}
</script>

<style scoped>
.ai-fab {
  position: fixed;
  right: 28px;
  bottom: 28px;
  width: 54px;
  height: 54px;
  border: none;
  border-radius: 50%;
  background: linear-gradient(135deg, #00a1d6, #00c8a0);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 8px 24px rgba(0, 161, 214, 0.35);
  z-index: 999;
  transition: transform 0.2s, box-shadow 0.2s;
}
.ai-fab:hover {
  transform: translateY(-2px) scale(1.05);
  box-shadow: 0 12px 28px rgba(0, 161, 214, 0.45);
}

.ai-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f6fafd;
}

.ai-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: #fff;
  border-bottom: 1px solid #eef2f6;
}
.ai-head-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ai-logo {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, #00a1d6, #00c8a0);
  color: #fff;
  font-weight: 700;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.ai-title {
  font-size: 15px;
  font-weight: 600;
}
.ai-sub {
  font-size: 12px;
  color: var(--text-sub, #909399);
  margin-top: 2px;
}

.ai-list {
  flex: 1;
  overflow-y: auto;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ai-msg {
  max-width: 82%;
  padding: 10px 12px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.ai-msg.welcome {
  background: #e8f6fb;
  color: #14748f;
  max-width: 100%;
  border-radius: 12px;
}
.ai-msg.user {
  align-self: flex-end;
  background: linear-gradient(135deg, #00a1d6, #00b3e5);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.ai-msg.assistant {
  align-self: flex-start;
  background: #fff;
  border: 1px solid #e8eef3;
  color: #333;
  border-bottom-left-radius: 4px;
}
.ai-msg.assistant.typing {
  color: #909399;
}
.ai-msg.error {
  align-self: center;
  background: #fef0f0;
  color: #c45656;
  font-size: 13px;
  max-width: 100%;
}

.ai-input {
  display: flex;
  gap: 8px;
  align-items: flex-end;
  padding: 12px 14px;
  background: #fff;
  border-top: 1px solid #eef2f6;
}
.ai-input :deep(.el-button) {
  height: 54px;
}

:deep(.el-drawer__body) {
  padding: 0;
  overflow: hidden;
}
</style>
