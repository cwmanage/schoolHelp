<template>
  <div class="ai-chat-entry">
    <!-- 浮动按钮（避开底部 tab bar） -->
    <button v-if="userStore.isLoggedIn" class="ai-fab" @click="open = true">
      <el-icon :size="24"><ChatDotRound /></el-icon>
    </button>

    <!-- 对话抽屉 -->
    <el-drawer v-model="open" size="88%" class="ai-drawer" :with-header="false">
      <div class="ai-panel">
        <div class="ai-head">
          <div class="ai-head-brand">
            <span class="ai-logo">屿</span>
            <div>
              <div class="ai-title">课屿 AI 助教</div>
              <div class="ai-sub">免费大模型 · 仅供参考</div>
            </div>
          </div>
          <el-button link size="small" @click="clearHistory">清空</el-button>
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
            placeholder="输入问题"
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

const CONTEXT_COUNT = 6
const MAX_SAVED = 50

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
  } catch { /* ignore */ }
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
  right: 16px;
  bottom: 84px;
  width: 50px;
  height: 50px;
  border: none;
  border-radius: 50%;
  background: linear-gradient(135deg, #00a1d6, #00c8a0);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 6px 20px rgba(0, 161, 214, 0.4);
  z-index: 999;
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
  padding: 12px 14px;
  background: #fff;
  border-bottom: 1px solid #eef2f6;
}
.ai-head-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}
.ai-logo {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: linear-gradient(135deg, #00a1d6, #00c8a0);
  color: #fff;
  font-weight: 700;
  font-size: 17px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.ai-title {
  font-size: 15px;
  font-weight: 600;
}
.ai-sub {
  font-size: 11px;
  color: var(--text-sub, #909399);
  margin-top: 2px;
}

.ai-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ai-msg {
  max-width: 85%;
  padding: 9px 12px;
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
  font-size: 12px;
  max-width: 100%;
}

.ai-input {
  display: flex;
  gap: 8px;
  align-items: flex-end;
  padding: 10px 12px;
  background: #fff;
  border-top: 1px solid #eef2f6;
  padding-bottom: calc(10px + env(safe-area-inset-bottom));
}
.ai-input :deep(.el-button) {
  height: 54px;
}

:deep(.el-drawer__body) {
  padding: 0;
  overflow: hidden;
}
</style>
