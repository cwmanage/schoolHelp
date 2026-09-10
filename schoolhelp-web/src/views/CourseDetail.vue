<template>
  <div class="detail-page">
    <!-- 课程头 -->
    <div class="course-head" :style="{ background: headBg }">
      <div class="head-back" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
      </div>
      <div class="head-avatar">{{ course?.name?.charAt(0) || '课' }}</div>
      <div class="head-info">
        <div class="head-name">{{ course?.name || '加载中...' }}</div>
        <div class="head-meta">
          <span v-if="course?.teacherName">
            <el-icon><User /></el-icon>{{ course.teacherName }}
          </span>
          <span v-if="course?.className">
            <el-icon><School /></el-icon>{{ course.className }}
          </span>
        </div>
        <div class="head-sem" v-if="course?.semester">{{ course.semester }}</div>
      </div>
      <div v-if="isOwner" class="head-actions">
        <el-button size="small" round :icon="Edit" @click="goEdit">编辑</el-button>
        <el-button size="small" round type="danger" plain :icon="Delete" @click="handleDeleteCourse">
          删除
        </el-button>
      </div>
    </div>

    <div class="desc card" v-if="course?.description">
      <div class="desc-label">课程简介</div>
      <p>{{ course.description }}</p>
    </div>

    <div class="teacher-link card" v-if="course?.teacherLink">
      <el-icon><Link /></el-icon>
      <a :href="course.teacherLink" target="_blank" rel="noopener">教师主页</a>
      <el-icon class="ext"><TopRight /></el-icon>
    </div>

    <!-- Tab -->
    <el-tabs v-model="activeTab" class="detail-tabs">
      <!-- 作业 -->
      <el-tab-pane label="作业" name="assignments">
        <div class="pane-head">
          <el-button
            type="primary"
            size="small"
            round
            :icon="Plus"
            @click="router.push(`/assignment/create/${route.params.id}`)"
          >{{ canManage ? '发布作业' : '申请发布' }}</el-button>
        </div>
        <div v-loading="loadingAssignments">
          <div v-for="a in assignments" :key="a.id" class="item-card card" @click="showAssignment(a)">
            <div class="item-title-row">
              <span class="item-title">{{ a.title }}</span>
              <el-tag size="small" :type="tagType(a.deadline)" round>{{ tagText(a.deadline) }}</el-tag>
            </div>
            <div class="item-content">{{ a.content || '暂无描述' }}</div>
            <div class="item-foot">
              <span><el-icon><Clock /></el-icon> 截止 {{ fmtTime(a.deadline) }}</span>
              <span v-if="submittedMap[a.id]" class="done">✓ 已提交</span>
            </div>
          </div>
          <el-empty v-if="!loadingAssignments && assignments.length === 0" description="暂无作业" :image-size="70" />
        </div>
      </el-tab-pane>

      <!-- 资料 -->
      <el-tab-pane label="资料" name="materials">
        <div class="pane-head">
          <el-upload
            :show-file-list="false"
            :http-request="doUploadMaterial"
            accept="*"
          >
            <el-button type="primary" size="small" round :icon="Upload">{{ canManage ? '上传资料' : '申请上传' }}</el-button>
          </el-upload>
        </div>
        <div v-loading="loadingMaterials">
          <div v-for="m in materials" :key="m.id" class="item-card card file-item">
            <el-icon class="file-icon"><Document /></el-icon>
            <div class="file-info" @click="downloadMaterial(m)">
              <div class="file-name">{{ m.originalName || m.fileName }}</div>
              <div class="file-meta">{{ m.fileSize ? formatSize(m.fileSize) : '' }} · {{ fmtTime(m.createdAt) }}</div>
            </div>
            <el-button
              v-if="canManage"
              text
              type="danger"
              :icon="Delete"
              @click="removeMaterial(m)"
            ></el-button>
          </div>
          <el-empty v-if="!loadingMaterials && materials.length === 0" description="暂无资料" :image-size="70" />
        </div>
      </el-tab-pane>

      <!-- 评论 -->
      <el-tab-pane label="评论" name="comments">
        <div class="comment-input card">
          <el-input
            v-model="commentText"
            type="textarea"
            :rows="2"
            maxlength="300"
            show-word-limit
            placeholder="说点什么吧…"
          />
          <div class="comment-opts">
            <el-checkbox v-model="commentAnon">匿名</el-checkbox>
            <el-button type="primary" size="small" round :loading="commenting" @click="postComment">
              发表评论
            </el-button>
          </div>
          <div class="anon-tip" v-if="commentAnon">匿名后同学只能看到「匿名同学」，管理员可追溯</div>
        </div>

        <div v-loading="loadingComments" class="comment-list">
          <div v-for="c in comments" :key="c.id" class="comment-item card">
            <el-avatar :size="34" class="c-avatar" :class="{ anon: c.isAnonymous === 1 }">
              {{ c.isAnonymous === 1 ? '🕶' : (c.nickname?.charAt(0) || c.userName?.charAt(0) || '?') }}
            </el-avatar>
            <div class="c-body">
              <div class="c-head">
                <span class="c-name" :class="{ 'c-anon': c.isAnonymous === 1 }">
                  {{ c.isAnonymous === 1 ? '匿名同学' : (c.nickname || c.userName || '同学') }}
                </span>
                <span v-if="c.isAnonymous === 1" class="anon-tag">匿名</span>
                <span class="c-time">{{ fmtTime(c.createdAt) }}</span>
              </div>
              <div class="c-content">{{ c.content }}</div>
            </div>
            <el-button
              v-if="canDeleteComment(c)"
              text
              type="danger"
              size="small"
              :icon="Delete"
              @click="delComment(c)"
            ></el-button>
          </div>
          <el-empty v-if="!loadingComments && comments.length === 0" description="还没有评论，来抢沙发" :image-size="70" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 作业详情抽屉 -->
    <el-drawer v-model="assignVisible" :title="curAssignment?.title || '作业'" size="90%" direction="btt">
      <div v-if="curAssignment" class="drawer-body">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="截止时间">
            {{ fmtTime(curAssignment.deadline) }}
          </el-descriptions-item>
          <el-descriptions-item label="发布人">
            {{ course?.teacherName || '班长' }}
          </el-descriptions-item>
        </el-descriptions>
        <div class="drawer-content card">
          <div class="drawer-label">作业内容</div>
          <p>{{ curAssignment.content || '无' }}</p>
        </div>
        <div class="drawer-actions">
          <el-button
            v-if="submittedMap[curAssignment.id]"
            type="warning"
            plain
            style="width: 100%"
            @click="unsubmit(curAssignment)"
          >撤销已提交</el-button>
          <el-button
            v-else
            type="primary"
            style="width: 100%"
            @click="submit(curAssignment)"
          >标记已完成</el-button>
          <el-button
            v-if="canManage"
            text
            type="danger"
            @click="delAssignment(curAssignment)"
          >删除此作业</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  User, School, Edit, Delete, Plus, Upload, Document, Link, TopRight, Clock, ArrowLeft
} from '@element-plus/icons-vue'
import {
  courseDetail, assignmentsByCourse, deleteAssignment, deleteCourse,
  materialsByCourse, uploadMaterial, deleteMaterial
} from '@/api/course'
import { commentList, addComment, deleteComment, markSubmitted, unmarkSubmitted, assignmentSubmitted } from '@/api/biz'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const courseId = route.params.id

const course = ref(null)
const activeTab = ref('assignments')

// 权限
const canManage = computed(() => {
  if (userStore.isAdmin) return true
  return userStore.isMonitor && course.value && course.value.creatorId === userStore.userInfo?.id
})
// 班长/管理员=创建；普通同学=申请（都走各自按钮）
const isOwner = canManage
// 学生也可以上传/发布，但文字/流程不同（由后端审批）
const canApply = computed(() => !userStore.isAdmin && !userStore.isMonitor)

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

const headColors = ['linear-gradient(135deg,#5b8cff,#3b6ef6)', 'linear-gradient(135deg,#34c9a0,#1fae87)', 'linear-gradient(135deg,#ff9f43,#ff7a2f)', 'linear-gradient(135deg,#a66bff,#8457e6)']
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
    assignments.value = res.data || []
    // 批量查提交状态
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

function showAssignment(a) {
  curAssignment.value = a
  assignVisible.value = true
}

async function submit(a) {
  await markSubmitted(a.id)
  ElMessage.success('已标记完成 🎉')
  assignVisible.value = false
  loadAssignments()
}

async function unsubmit(a) {
  await unmarkSubmitted(a.id)
  ElMessage.success('已撤销标记')
  assignVisible.value = false
  loadAssignments()
}

async function delAssignment(a) {
  try {
    await ElMessageBox.confirm(`确定删除作业「${a.title}」？`, '警告', { type: 'warning' })
    await deleteAssignment(a.id)
    ElMessage.success('已删除')
    assignVisible.value = false
    loadAssignments()
  } catch (e) { /* cancel */ }
}

async function handleDeleteCourse() {
  try {
    await ElMessageBox.confirm(`确定删除课程「${course.value?.name}」？课程下的作业资料将一并处理`, '警告', { type: 'warning' })
    await deleteCourse(courseId)
    ElMessage.success('已删除课程')
    router.push('/courses')
  } catch (e) { /* cancel */ }
}

function goEdit() {
  router.push({ path: '/course/create', query: { id: courseId } })
}

async function doUploadMaterial({ file }) {
  const fd = new FormData()
  fd.append('file', file)
  if (!canManage.value) fd.append('applyNote', '同学上传资料，请审批')
  try {
    await uploadMaterial(courseId, fd)
    ElMessage.success('上传成功')
    loadMaterials()
  } catch (e) { /* interceptor */ }
}

function downloadMaterial(m) {
  // 后端返回存储路径；实际文件下载后续接静态资源服务
  ElMessage.info('资料下载将在部署环境完善（当前走存储路径）')
}

async function removeMaterial(m) {
  try {
    await ElMessageBox.confirm('确定删除该资料？', '提示', { type: 'warning' })
    await deleteMaterial(m.id)
    ElMessage.success('已删除')
    loadMaterials()
  } catch (e) { /* */ }
}

async function postComment() {
  if (!commentText.value.trim()) {
    ElMessage.warning('评论内容不能为空')
    return
  }
  commenting.value = true
  try {
    await addComment(courseId, commentText.value.trim(), commentAnon.value)
    ElMessage.success('评论成功')
    commentText.value = ''
    commentAnon.value = false
    loadComments()
  } catch (e) { /* */ } finally {
    commenting.value = false
  }
}

function canDeleteComment(c) {
  if (userStore.isAdmin) return true
  return c.userId && userStore.userInfo && c.userId === userStore.userInfo.id
}

async function delComment(c) {
  try {
    await ElMessageBox.confirm('确定删除这条评论？', '提示', { type: 'warning' })
    await deleteComment(c.id)
    ElMessage.success('已删除')
    loadComments()
  } catch (e) { /* */ }
}

function tagType(d) {
  const diff = daysLeft(d)
  if (diff < 0) return 'danger'
  if (diff <= 3) return 'warning'
  return 'success'
}
function tagText(d) {
  const diff = daysLeft(d)
  if (diff < 0) return `逾期${-diff}天`
  if (diff === 0) return '今天截止'
  return `${diff}天后截止`
}
function daysLeft(d) {
  return Math.ceil((new Date(d) - new Date()) / 86400000)
}
function fmtTime(t) {
  if (!t) return ''
  const d = new Date(t)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}
function formatSize(bytes) {
  if (!bytes) return ''
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + 'KB'
  return (bytes / 1024 / 1024).toFixed(1) + 'MB'
}

onMounted(loadAll)
</script>

<style scoped>
.detail-page {
  padding-bottom: 20px;
}

.course-head {
  border-radius: 16px;
  padding: 18px 16px;
  color: #fff;
  display: flex;
  align-items: center;
  gap: 14px;
  position: relative;
  box-shadow: 0 6px 16px rgba(59, 110, 246, 0.2);
}

.head-back {
  position: absolute;
  top: 8px;
  left: 8px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 5;
}

.head-back:hover {
  background: rgba(255, 255, 255, 0.4);
}

.head-avatar {
  width: 54px;
  height: 54px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  font-weight: 700;
}

.head-info {
  flex: 1;
  min-width: 0;
}

.head-name {
  font-size: 19px;
  font-weight: 700;
}

.head-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  opacity: 0.92;
  margin-top: 4px;
}

.head-meta span {
  display: flex;
  align-items: center;
  gap: 3px;
}

.head-sem {
  font-size: 11px;
  opacity: 0.8;
  margin-top: 4px;
}

.head-actions {
  position: absolute;
  right: 10px;
  top: 10px;
  display: flex;
  gap: 4px;
}

.desc {
  margin: 12px 0;
}

.desc-label {
  font-size: 12px;
  color: var(--text-sub);
  margin-bottom: 4px;
}

.desc p {
  font-size: 14px;
  line-height: 1.6;
}

.teacher-link {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  padding: 10px 14px;
}

.teacher-link a {
  color: var(--primary);
  text-decoration: none;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ext { color: #c8ccd4; }

.detail-tabs {
  margin-top: 6px;
}

.pane-head {
  margin-bottom: 10px;
  display: flex;
  justify-content: flex-end;
}

.item-card {
  margin-bottom: 10px;
  padding: 12px 14px;
}

.item-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.item-title {
  font-size: 15px;
  font-weight: 600;
}

.item-content {
  font-size: 13px;
  color: var(--text-sub);
  margin-top: 5px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.item-foot {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-sub);
}

.item-foot span {
  display: flex;
  align-items: center;
  gap: 4px;
}

.done { color: var(--success); }

.file-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.file-icon {
  font-size: 26px;
  color: var(--primary);
}

.file-info {
  flex: 1;
  min-width: 0;
  cursor: pointer;
}

.file-name {
  font-size: 14px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  font-size: 11px;
  color: var(--text-sub);
  margin-top: 2px;
}

.comment-input {
  margin-bottom: 12px;
}

.comment-opts {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.anon-tip {
  font-size: 11px;
  color: var(--text-sub);
  margin-top: 4px;
}

.comment-item {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
  padding: 12px;
  align-items: flex-start;
}

.c-avatar {
  background: var(--primary-light);
  color: var(--primary);
  flex-shrink: 0;
  font-size: 14px;
}

.c-avatar.anon {
  background: #f3f4f6;
}

.c-body {
  flex: 1;
  min-width: 0;
}

.c-head {
  display: flex;
  align-items: center;
  gap: 6px;
}

.c-name {
  font-size: 13px;
  font-weight: 600;
}

.c-anon {
  color: var(--text-sub);
  font-weight: 500;
}

.anon-tag {
  font-size: 10px;
  background: #f0f0f0;
  color: var(--text-sub);
  padding: 0 5px;
  border-radius: 4px;
}

.c-time {
  font-size: 11px;
  color: #c0c4cc;
  margin-left: auto;
}

.c-content {
  font-size: 14px;
  margin-top: 4px;
  line-height: 1.5;
  word-break: break-word;
}

.drawer-body {
  padding: 4px;
}

.drawer-content {
  margin-top: 12px;
  padding: 12px 14px;
}

.drawer-label {
  font-size: 12px;
  color: var(--text-sub);
  margin-bottom: 6px;
}

.drawer-content p {
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.drawer-actions {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>
