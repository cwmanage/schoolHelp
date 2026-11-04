<template>
  <div class="ai-chat-entry">
    <!-- 浮动按钮 -->
    <button v-if="userStore.isLoggedIn" class="ai-fab" title="课屿 AI 助教" @click="open = true">
      <el-icon :size="26"><ChatDotRound /></el-icon>
    </button>

    <!-- 对话抽屉 -->
    <el-drawer v-model="open" :size="fullscreen ? '100%' : '420px'" class="ai-drawer" :with-header="false">
      <div class="ai-panel" :class="{ 'is-full': fullscreen }">
        <div class="ai-head">
          <div class="ai-head-brand">
            <span class="ai-logo">屿</span>
            <div>
              <div class="ai-title">课屿 AI 助教</div>
              <div class="ai-sub">免费大模型驱动 · 回答仅供参考</div>
            </div>
          </div>
          <div class="ai-head-actions">
            <el-button link size="small" @click="clearHistory">清空记录</el-button>
            <el-button link class="ai-icon-btn" :title="fullscreen ? '切换小窗' : '切换大屏'" @click="fullscreen = !fullscreen">
              <el-icon :size="16"><FullScreen /></el-icon>
            </el-button>
            <el-button v-if="fullscreen" link class="ai-icon-btn" title="关闭" @click="open = false">
              <el-icon :size="16"><Close /></el-icon>
            </el-button>
          </div>
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
          >
            <div v-if="m.role === 'assistant'" class="md-body" v-html="renderMd(m.content)"></div>
            <template v-else>{{ m.content }}</template>
            <button v-if="m.role === 'assistant'" class="copy-btn" title="复制" @click="copyMsg(m.content)">
              <el-icon :size="13"><CopyDocument /></el-icon>
            </button>
          </div>
          <div v-if="typing" class="ai-msg assistant typing">
            <span class="typing-dots"><span></span><span></span><span></span></span>
          </div>
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
import { ElMessage } from 'element-plus'
import { ChatDotRound, CopyDocument, FullScreen, Close } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import { aiAgentChat, clearAgentHistory } from '@/api/user'
import { useUserStore } from '@/stores/user'

// Markdown 渲染（html:false 转义内嵌 HTML 防 XSS；链接新窗口打开）
const md = new MarkdownIt({ html: false, linkify: true, breaks: true })
const defaultLink = md.renderer.rules.link_open
  || ((tokens, idx, options, env, self) => self.renderToken(tokens, idx, options))
md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  tokens[idx].attrSet('target', '_blank')
  tokens[idx].attrSet('rel', 'noopener')
  return defaultLink(tokens, idx, options, env, self)
}
function renderMd(text) {
  return md.render(text || '')
}

const userStore = useUserStore()
const open = ref(false)
const fullscreen = ref(false)
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

async function copyMsg(text) {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
    } else {
      // 非安全上下文兜底（localhost 之外的 http 场景）
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败，请手动选择复制')
  }
}

function clearHistory() {
  clearAgentHistory().catch(() => {})
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
  } else {
    fullscreen.value = false   // 关闭后复位为小窗模式
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
    // 记忆按 userId 存服务端 MySQL（跨设备），前端只传本轮提问
    const res = await aiAgentChat(text)
    history.value.push({ role: 'assistant', content: res.data.answer })
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

/* 思考中三点跳动动画 */
.typing-dots {
  display: inline-flex;
  gap: 5px;
  padding: 4px 2px;
}
.typing-dots span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #00a1d6;
  animation: ai-blink 1.2s infinite;
}
.typing-dots span:nth-child(2) { animation-delay: 0.2s; }
.typing-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes ai-blink {
  0%, 80%, 100% { opacity: 0.2; transform: translateY(0); }
  40% { opacity: 1; transform: translateY(-3px); }
}

/* Markdown 正文样式（v-html 内容需 :deep） */
.md-body :deep(p) { margin: 0 0 6px; }
.md-body :deep(p:last-child) { margin: 0; }
.md-body :deep(h1), .md-body :deep(h2), .md-body :deep(h3),
.md-body :deep(h4) { margin: 8px 0 6px; font-size: 14px; }
.md-body :deep(ul), .md-body :deep(ol) { margin: 4px 0; padding-left: 20px; }
.md-body :deep(li) { margin: 2px 0; }
.md-body :deep(code) {
  font-family: Consolas, Menlo, monospace;
  font-size: 12.5px;
  background: rgba(0, 161, 214, 0.08);
  padding: 1px 5px;
  border-radius: 4px;
}
.md-body :deep(pre) {
  background: #f3f7fa;
  border: 1px solid #e8eef3;
  padding: 10px 12px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 6px 0;
}
.md-body :deep(pre code) { background: none; padding: 0; font-size: 12px; }
.md-body :deep(table) { border-collapse: collapse; margin: 6px 0; width: 100%; }
.md-body :deep(th), .md-body :deep(td) {
  border: 1px solid #e5e9ef;
  padding: 4px 8px;
  font-size: 12.5px;
  text-align: left;
}
.md-body :deep(th) { background: #f3f7fa; }
.md-body :deep(blockquote) {
  margin: 6px 0;
  padding: 2px 10px;
  border-left: 3px solid #00a1d6;
  color: #606266;
  background: #f8fbfd;
}
.md-body :deep(a) { color: #00a1d6; }
.md-body :deep(hr) { border: none; border-top: 1px solid #e5e9ef; margin: 8px 0; }
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

/* ===== v4：复制按钮 + 大屏模式 ===== */
.ai-msg.assistant {
  position: relative;
  padding-right: 34px;
}
.copy-btn {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 22px;
  height: 22px;
  border: none;
  border-radius: 5px;
  background: rgba(0, 161, 214, 0.08);
  color: #5aa8c7;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  opacity: 0.55;
  transition: opacity 0.15s, background 0.15s;
}
.copy-btn:hover {
  opacity: 1;
  background: rgba(0, 161, 214, 0.18);
}
.ai-head-actions {
  display: flex;
  align-items: center;
  gap: 2px;
}
.ai-head-actions .ai-icon-btn {
  padding: 4px;
  color: #7a8a99;
}
.ai-head-actions .ai-icon-btn:hover {
  color: #00a1d6;
}
/* 大屏模式：抽屉全屏，内容居中为大卡片 */
.ai-panel.is-full {
  max-width: 960px;
  margin: 0 auto;
  height: calc(100% - 28px);
  border-radius: 0 0 14px 14px;
  box-shadow: 0 10px 40px rgba(0, 40, 60, 0.18);
  padding: 0 18px;
}
.ai-panel.is-full .ai-list {
  font-size: 15px;
  padding: 20px 24px;
  gap: 14px;
}
/* 大屏气泡自适应：字号/内距/宽度跟随放大（修：气泡沿用小窗尺寸显得怪） */
.ai-panel.is-full .ai-msg {
  max-width: 72%;
  font-size: 15px;
  line-height: 1.7;
  padding: 12px 16px;
  border-radius: 14px;
}
.ai-panel.is-full .ai-msg.user {
  max-width: 56%;
}
.ai-panel.is-full .ai-msg.welcome {
  max-width: 100%;
  font-size: 15px;
}
.ai-panel.is-full .ai-msg.error {
  font-size: 13px;
}
.ai-panel.is-full .md-body :deep(code) { font-size: 13px; }
.ai-panel.is-full .md-body :deep(table) { font-size: 14px; }
.ai-panel.is-full .md-body :deep(th),
.ai-panel.is-full .md-body :deep(td) { padding: 6px 10px; }
.ai-panel.is-full .typing-dots span { width: 8px; height: 8px; }
:deep(.el-drawer__body) {
  padding: 0;
  overflow: hidden;
  background: #eef3f8;
}
