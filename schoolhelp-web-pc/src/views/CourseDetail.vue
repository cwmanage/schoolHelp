<template>
  <div class="detail-page">
    <div class="back-row">
      <el-button text :icon="ArrowLeft" @click="goBack">返回</el-button>
    </div>

    <div class="detail-grid">
      <!-- 左：主内容 -->
      <div class="main-col">
        <!-- 封面 -->
        <div class="cover-banner" :style="{ background: headBg }">
          <div class="cb-letter">{{ (course?.name || '课').charAt(0) }}</div>
          <div class="cb-info">
            <h1>{{ course?.name || '加载中...' }}</h1>
            <p class="cb-meta">
              <span v-if="course?.teacherName"><el-icon><User /></el-icon>{{ course.teacherName }}</span>
              <span v-if="course?.className"><el-icon><School /></el-icon>{{ course.className }}</span>
              <span v-if="course?.semester"><el-icon><Calendar /></el-icon>{{ course.semester }}</span>
            </p>
            <a v-if="course?.teacherLink" :href="course.teacherLink" target="_blank" rel="noopener" class="cb-link">
              <el-icon><Link /></el-icon>教师主页
            </a>
          </div>
          <div v-if="isOwner" class="cb-actions">
            <el-button round :icon="Edit" @click="router.push(`/course/create?id=${course.id}`)">编辑</el-button>
            <el-button round type="danger" plain :icon="Delete" @click="handleDeleteCourse">删除</el-button>
          </div>
        </div>

        <!-- 简介 -->
        <div class="desc card" v-if="course?.description">
          <h3>课程简介</h3>
          <p>{{ course.description }}</p>
        </div>

        <!-- Tab -->
        <el-tabs v-model="activeTab" class="detail-tabs">
          <!-- 作业 -->
          <el-tab-pane label="作业" name="assignments">
            <div class="pane-toolbar">
              <el-button type="primary" size="small" :icon="Plus" @click="router.push(`/assignment/create/${courseId}`)">
                {{ canManage ? '发布作业' : '申请发布作业' }}
              </el-button>
            </div>
            <div v-loading="loadingAssignments" class="asn-list">
              <div v-for="a in assignments" :key="a.id" class="asn-item card" @click="showAssignment(a)">
                <div class="asn-head">
                  <span class="asn-title">{{ a.title }}</span>
                  <el-tag size="small" :type="tagType(a.deadline)" round>{{ tagText(a.deadline) }}</el-tag>
                </div>
                <div class="asn-content">{{ a.content || '暂无描述' }}</div>
                <div class="asn-foot">
                  <span><el-icon><Clock /></el-icon> 截止 {{ fmtTime(a.deadline) }}</span>
                  <span v-if="submittedMap[a.id]" class="done">✓ 已提交</span>
                  <el-button
                    v-else-if="!a.isOwner"
                    size="small"
                    type="primary"
                    plain
                    round
                    @click.stop="handleSubmit(a)"
                  >标记已提交</el-button>
                </div>
              </div>
              <el-empty v-if="!loadingAssignments && assignments.length === 0" description="暂无作业" />
            </div>
          </el-tab-pane>

          <!-- 资料 -->
          <el-tab-pane label="资料" name="materials">
            <div class="pane-toolbar">
              <el-upload :show-file-list="false" :http-request="doUploadMaterial" accept="*">
                <el-button type="primary" size="small" :icon="Upload">
                  {{ canManage ? '上传资料' : '申请上传资料' }}
                </el-button>
              </el-upload>
            </div>
            <div v-loading="loadingMaterials" class="mat-list">
              <div v-for="m in materials" :key="m.id" class="mat-item card">
                <el-icon class="mat-icon"><Document /></el-icon>
                <div class="mat-info" @click="downloadMaterial(m)">
                  <div class="mat-name">{{ m.title || m.fileName }}</div>
                  <div class="mat-meta">{{ m.fileType || '未知' }} · {{ fmtSize(m.fileSize) }} · {{ fmtTime(m.createdAt) }}</div>
                </div>
                <el-button v-if="canManage" text type="danger" :icon="Delete" @click="removeMaterial(m)"></el-button>
              </div>
              <el-empty v-if="!loadingMaterials && materials.length === 0" description="暂无资料" />
            </div>
          </el-tab-pane>

          <!-- 评论 -->
          <el-tab-pane label="评论" name="comments">
            <div class="comment-box card">
              <div class="comment-input-row">
                <el-input
                  v-model="commentText"
                  type="textarea"
                  :rows="2"
                  maxlength="300"
                  show-word-limit
                  placeholder="友善发言，共建学习社区…"
                />
                <div class="comment-opt">
                  <el-checkbox v-model="commentAnon">匿名评论</el-checkbox>
                  <el-button type="primary" :loading="commenting" @click="handleComment">发表评论</el-button>
                </div>
              </div>
            </div>
            <div v-loading="loadingComments" class="comment-list">
              <div v-for="c in comments" :key="c.id" class="comment-item card">
                <el-avatar :size="34" class="c-avatar">{{ commentName(c).charAt(0) }}</el-avatar>
                <div class="c-body">
                  <div class="c-name">
                    {{ commentName(c) }}
                    <el-tag v-if="c.isAnonymous === 1" size="small" round>匿名</el-tag>
                  </div>
                  <div class="c-content">{{ c.content }}</div>
                  <div class="c-foot">
                    <span>{{ fmtTime(c.createdAt) }}</span>
                    <el-button
                      v-if="userStore.isAdmin || c.userId === userStore.userInfo?.id"
                      text
                      type="danger"
                      size="small"
                      @click="removeComment(c)"
                    >删除</el-button>
                  </div>
                </div>
              </div>
              <el-empty v-if="!loadingComments && comments.length === 0" description="暂无评论，抢沙发~" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <!-- 右：侧栏 -->
      <aside class="side-col">
        <div class="card side-card">
          <h3>课程信息</h3>
          <div class="info-row"><span>任课教师</span><b>{{ course?.teacherName || '-' }}</b></div>
          <div class="info-row"><span>面向班级</span><b>{{ course?.className || '-' }}</b></div>
          <div class="info-row"><span>学期</span><b>{{ course?.semester || '-' }}</b></div>
          <div class="info-row"><span>作业数</span><b>{{ assignments.length }}</b></div>
          <div class="info-row"><span>资料数</span><b>{{ materials.length }}</b></div>
          <div class="info-row"><span>评论数</span><b>{{ comments.length }}</b></div>
        </div>

        <div class="card side-card">
          <h3>快捷操作</h3>
          <el-button class="side-btn" :icon="Plus" @click="router.push(`/assignment/create/${courseId}`)">
            {{ canManage ? '发布作业' : '申请作业' }}
          </el-button>
          <el-button class="side-btn" :icon="Upload" @click="clickUpload">
            {{ canManage ? '上传资料' : '申请资料' }}
          </el-button>
        </div>
      </aside>
    </div>

    <!-- 作业详情抽屉 -->
    <el-drawer v-model="assignVisible" :title="curAssignment?.title || '作业详情'" size="420px">
      <div v-if="curAssignment">
        <div class="drawer-label">作业内容</div>
        <p class="drawer-content">{{ curAssignment.content || '暂无描述' }}</p>
        <div class="drawer-label">截止时间</div>
        <p class="drawer-content">{{ fmtTime(curAssignment.deadline) }}</p>
        <div class="drawer-actions" v-if="!submittedMap[curAssignment.id]">
          <el-button type="primary" @click="handleSubmit(curAssignment)">标记已提交</el-button>
        </div>
        <div v-else class="done-tip">✓ 你已提交该作业</div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, User, School, Calendar, Link, Edit, Delete,
  Plus, Upload, Document, Clock
} from '@element-plus/icons-vue'
import {
  courseDetail, assignmentsByCourse, deleteCourse, materialsByCourse,
  uploadMaterial, deleteMaterial
} from '@/api/course'
import {
  commentList, addComment, deleteComment, markSubmitted, assignmentSubmitted
} from '@/api/biz'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const courseId = route.params.id

const course = ref(null)
const activeTab = ref('assignments')

const canManage = computed(() => {
  if (userStore.isAdmin) return true
  return userStore.isMonitor && course.value && course.value.creatorId === userStore.userInfo?.id
})
const isOwner = canManage

// 作业
const assignments = ref([])
const submittedMap = ref({})
const loadingAssignments = ref(false)
const assignVisible = ref(false)
const curAssignment = ref(null)

// 资料
const materials = ref([])
const loadingMaterials = ref(false)

// 评论
const comments = ref([])
const loadingComments = ref(false)
const commentText = ref('')
const commentAnon = ref(false)
const commenting = ref(false)

const headColors = [
  'linear-gradient(135deg,#00a1d6,#4fc3e8)',
  'linear-gradient(135deg,#7c6ff0,#a99dff)',
  'linear-gradient(135deg,#ff8f5e,#ffb38a)',
  'linear-gradient(135deg,#3ecf8e,#8ae0b8)'
]
const headBg = computed(() => headColors[(Number(courseId) || 1) % headColors.length])

async function loadAll() {
  try {
    const res = await courseDetail(courseId)
    course.value = res.data
  } catch (e) { /* */ }
  loadAssignments()
  loadMaterials()
  loadComments()
}

async function loadAssignments() {
  loadingAssignments.value = true
  try {
    const res = await assignmentsByCourse(courseId)
    assignments.value = (res.data || []).map((a) => ({
      ...a,
      isOwner: a.creatorId === userStore.userInfo?.id
    }))
    const map = {}
    for (const a of assignments.value) {
      try {
        const s = await assignmentSubmitted(a.id)
        map[a.id] = s.data === true
      } catch (e) { map[a.id] = false }
    }
    submittedMap.value = map
  } catch (e) {
    assignments.value = []
  } finally {
    loadingAssignments.value = false
  }
}

async function loadMaterials() {
  loadingMaterials.value = true
  try {
    const res = await materialsByCourse(courseId)
    materials.value = res.data || []
  } catch (e) {
    materials.value = []
  } finally {
    loadingMaterials.value = false
  }
}

async function loadComments() {
  loadingComments.value = true
  try {
    const res = await commentList(courseId)
    comments.value = res.data || []
  } catch (e) {
    comments.value = []
  } finally {
    loadingComments.value = false
  }
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/')
}

function tagType(deadline) {
  const diff = (new Date(deadline) - new Date()) / 86400000
  if (diff < 0) return 'info'
  if (diff <= 2) return 'danger'
  if (diff <= 5) return 'warning'
  return 'success'
}
function tagText(deadline) {
  const diff = (new Date(deadline) - new Date()) / 86400000
  if (diff < 0) return '已截止'
  if (diff < 1) return '今天截止'
  return `${Math.ceil(diff)} 天后截止`
}
function fmtTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').substring(0, 16)
}
function fmtSize(bytes) {
  if (!bytes) return '-'
  if (bytes > 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + ' MB'
  if (bytes > 1024) return (bytes / 1024).toFixed(0) + ' KB'
  return bytes + ' B'
}

function showAssignment(a) {
  curAssignment.value = a
  assignVisible.value = true
}

async function handleSubmit(a) {
  try {
    await markSubmitted(a.id)
    ElMessage.success('已标记提交 🎉')
    submittedMap.value[a.id] = true
    assignVisible.value = false
  } catch (e) { /* */ }
}

async function handleDeleteCourse() {
  try {
    await ElMessageBox.confirm(`确定删除课程「${course.value?.name}」？`, '警告', { type: 'warning' })
    await deleteCourse(courseId)
    ElMessage.success('已删除')
    router.push('/courses')
  } catch (e) { /* */ }
}

async function doUploadMaterial({ file }) {
  const fd = new FormData()
  fd.append('file', file)
  if (!canManage.value) fd.append('applyNote', '同学上传资料，请审批')
  try {
    await uploadMaterial(courseId, fd)
    ElMessage.success(canManage.value ? '上传成功' : '已提交申请，等待管理员审批 📮')
    loadMaterials()
  } catch (e) { /* */ }
}
function clickUpload() {
  const input = document.createElement('input')
  input.type = 'file'
  input.onchange = (e) => {
    const f = e.target.files[0]
    if (f) doUploadMaterial({ file: f })
  }
  input.click()
}

async function removeMaterial(m) {
  try {
    await ElMessageBox.confirm('确定删除该资料？', '提示', { type: 'warning' })
    await deleteMaterial(m.id)
    ElMessage.success('已删除')
    loadMaterials()
  } catch (e) { /* */ }
}

function downloadMaterial(m) {
  // 后端暂存本地路径；部署后接静态下载
  ElMessage.info('文件下载将在部署环境完善')
}

function commentName(c) {
  if (c.isAnonymous === 1 && !userStore.isAdmin) return '匿名同学'
  return c.nickname || c.username || `同学${c.userId || ''}`
}

async function handleComment() {
  const text = commentText.value.trim()
  if (!text) {
    ElMessage.warning('写点内容再发表吧')
    return
  }
  commenting.value = true
  try {
    await addComment(courseId, text, commentAnon.value)
    ElMessage.success('评论成功')
    commentText.value = ''
    commentAnon.value = false
    loadComments()
  } catch (e) { /* */ } finally {
    commenting.value = false
  }
}

async function removeComment(c) {
  try {
    await ElMessageBox.confirm('删除这条评论？', '提示', { type: 'warning' })
    await deleteComment(c.id)
    ElMessage.success('已删除')
    loadComments()
  } catch (e) { /* */ }
}

onMounted(loadAll)
</script>

<style scoped>
.detail-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.back-row {
  margin-bottom: -6px;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 20px;
  align-items: start;
}

.main-col {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

/* 封面 */
.cover-banner {
  border-radius: 8px;
  color: #fff;
  padding: 26px 28px;
  display: flex;
  align-items: center;
  gap: 22px;
  min-height: 120px;
}
.cb-letter {
  width: 72px;
  height: 72px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 38px;
  font-weight: 700;
  flex-shrink: 0;
}
.cb-info {
  flex: 1;
  min-width: 0;
}
.cb-info h1 {
  font-size: 24px;
  font-weight: 700;
}
.cb-meta {
  display: flex;
  gap: 16px;
  margin-top: 10px;
  font-size: 13px;
  opacity: 0.95;
}
.cb-meta span {
  display: flex;
  align-items: center;
  gap: 4px;
}
.cb-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 10px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.92);
  text-decoration: underline;
}
.cb-actions {
  align-self: flex-end;
}

/* 简介 / 卡片区 */
.desc {
  padding: 18px 22px;
}
.desc h3 {
  font-size: 16px;
  margin-bottom: 8px;
}
.desc p {
  color: var(--text-sub);
  line-height: 1.7;
  white-space: pre-wrap;
}

/* 作业/资料列表 */
.pane-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 10px;
}
.asn-item,
.mat-item {
  padding: 14px 16px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: box-shadow 0.15s;
  border-radius: 8px;
}
.asn-item:hover {
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.1);
}
.asn-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.asn-title {
  font-size: 15px;
  font-weight: 600;
}
.asn-content {
  font-size: 13px;
  color: var(--text-sub);
  margin-top: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.asn-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-weak);
  margin-top: 10px;
}
.done {
  color: var(--success);
}

.mat-item {
  display: flex;
  align-items: center;
  gap: 12px;
}
.mat-icon {
  font-size: 30px;
  color: var(--primary);
  flex-shrink: 0;
}
.mat-info {
  flex: 1;
  cursor: pointer;
}
.mat-name {
  font-size: 14px;
  font-weight: 500;
}
.mat-meta {
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 3px;
}

/* 评论 */
.comment-box {
  padding: 14px;
  margin-bottom: 10px;
}
.comment-opt {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}
.comment-item {
  display: flex;
  gap: 12px;
  padding: 12px 16px;
  margin-bottom: 10px;
}
.c-avatar {
  flex-shrink: 0;
  background: linear-gradient(135deg, #00a1d6, #7c6ff0);
  color: #fff;
}
.c-body {
  flex: 1;
  min-width: 0;
}
.c-name {
  font-size: 13px;
  color: var(--text-sub);
  display: flex;
  align-items: center;
  gap: 6px;
}
.c-content {
  font-size: 14px;
  margin: 6px 0;
  line-height: 1.6;
  word-break: break-word;
}
.c-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-weak);
}

/* 侧栏 */
.side-col {
  display: flex;
  flex-direction: column;
  gap: 14px;
  position: sticky;
  top: calc(var(--header-h) + 20px);
}
.side-card {
  padding: 16px 18px;
  border-radius: 8px;
}
.side-card h3 {
  font-size: 15px;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f1f2;
}
.info-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  padding: 6px 0;
}
.info-row span {
  color: var(--text-sub);
}
.side-btn {
  width: 100%;
  margin: 0 0 8px;
}

/* 抽屉 */
.drawer-label {
  font-size: 13px;
  color: var(--text-sub);
  margin: 14px 0 6px;
}
.drawer-content {
  line-height: 1.7;
}
.drawer-actions {
  margin-top: 20px;
}
.done-tip {
  color: var(--success);
  font-size: 14px;
  margin-top: 20px;
}

@media (max-width: 960px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
  .side-col {
    position: static;
    order: -1;
  }
}
</style>
