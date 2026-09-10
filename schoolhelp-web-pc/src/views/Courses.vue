<template>
  <div class="courses-page">
    <div class="page-toolbar">
      <div class="left">
        <h2>课程库</h2>
        <el-select v-model="semester" placeholder="全部学期" clearable style="width: 180px" @change="loadCourses">
          <el-option v-for="s in semesters" :key="s" :label="s" :value="s" />
        </el-select>
        <el-select v-model="className" placeholder="全部班级" clearable filterable style="width: 160px" @change="loadCourses">
          <el-option v-for="c in classNames" :key="c" :label="c" :value="c" />
        </el-select>
      </div>
      <div class="right">
        <el-input
          v-model="keyword"
          placeholder="搜索课程 / 教师"
          :prefix-icon="Search"
          clearable
          style="width: 220px"
          @input="applyKeyword"
        />
        <el-button type="primary" :icon="Plus" @click="router.push('/course/create')">
          {{ userStore.canManage ? '创建课程' : '申请课程' }}
        </el-button>
      </div>
    </div>

    <!-- 关键词过滤提示 -->
    <div v-if="kw" class="kw-tip">
      关键词「{{ kw }}」的搜索结果
      <a @click="clearKw">清除</a>
    </div>

    <div v-loading="loading" class="course-grid">
      <div v-for="c in filtered" :key="c.id" class="course-card card" @click="router.push(`/course/${c.id}`)">
        <div class="cc-cover" :style="{ background: colorOf(c.id) }">
          <span class="cc-letter">{{ (c.name || '课').charAt(0) }}</span>
        </div>
        <div class="cc-body">
          <div class="cc-name">{{ c.name }}</div>
          <div class="cc-meta">
            <span v-if="c.teacherName"><el-icon><User /></el-icon>{{ c.teacherName }}</span>
            <span v-if="c.className"><el-icon><School /></el-icon>{{ c.className }}</span>
          </div>
          <div class="cc-sem" v-if="c.semester">{{ c.semester }}</div>
          <div class="cc-desc" v-if="c.description">{{ c.description }}</div>
          <div class="cc-foot">
            <span v-if="c.teacherLink" class="teacher-link">
              <el-icon><Link /></el-icon>教师主页
            </span>
          </div>
        </div>
      </div>

      <el-empty
        v-if="!loading && filtered.length === 0"
        description="没有找到相关课程"
        style="grid-column: 1 / -1"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, Plus, User, School, Link } from '@element-plus/icons-vue'
import { courseList } from '@/api/course'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const courses = ref([])
const keyword = ref('')
const kw = ref('')
const semester = ref('')
const className = ref('')
const loading = ref(false)

const semesters = computed(() => [...new Set(courses.value.map((c) => c.semester).filter(Boolean))])
const classNames = computed(() => [...new Set(courses.value.map((c) => c.className).filter(Boolean))])

const filtered = computed(() => {
  let list = courses.value
  if (kw.value) {
    const k = kw.value.toLowerCase()
    list = list.filter(
      (c) =>
        (c.name || '').toLowerCase().includes(k) ||
        (c.teacherName || '').toLowerCase().includes(k) ||
        (c.className || '').toLowerCase().includes(k)
    )
  }
  return list
})

const colorPalette = [
  'linear-gradient(135deg,#00a1d6,#6cc7e0)',
  'linear-gradient(135deg,#7c6ff0,#b3a6ff)',
  'linear-gradient(135deg,#ff8f5e,#ffc3a0)',
  'linear-gradient(135deg,#3ecf8e,#a4e8c8)',
  'linear-gradient(135deg,#ff7eb3,#ffb3d1)',
  'linear-gradient(135deg,#f7b733,#ffd98a)'
]
function colorOf(id) {
  return colorPalette[(Number(id) || 1) % colorPalette.length]
}

async function loadCourses() {
  loading.value = true
  try {
    const params = {}
    if (semester.value) params.semester = semester.value
    if (className.value) params.className = className.value
    const res = await courseList(params)
    courses.value = res.data || []
  } catch (e) {
    courses.value = []
  } finally {
    loading.value = false
  }
}

function applyKeyword() {
  kw.value = keyword.value.trim()
}
function clearKw() {
  keyword.value = ''
  kw.value = ''
}

onMounted(() => {
  const k = route.query.keyword
  if (k) {
    kw.value = String(k)
    keyword.value = String(k)
  }
  loadCourses()
})
</script>

<style scoped>
.courses-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}
.left,
.right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.left h2 {
  font-size: 20px;
  margin-right: 8px;
}

.kw-tip {
  font-size: 13px;
  color: var(--text-sub);
}
.kw-tip a {
  cursor: pointer;
  margin-left: 6px;
}

.course-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 20px 18px;
}

.course-card {
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  border-radius: 8px;
}
.course-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.12);
}

.cc-cover {
  height: 108px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}
.cc-letter {
  font-size: 40px;
  font-weight: 700;
  color: rgba(255, 255, 255, 0.92);
}

.cc-body {
  padding: 12px 14px 14px;
}
.cc-name {
  font-size: 16px;
  font-weight: 600;
}
.cc-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--text-sub);
  margin-top: 6px;
}
.cc-meta span {
  display: flex;
  align-items: center;
  gap: 3px;
}
.cc-sem {
  font-size: 11px;
  color: var(--text-weak);
  margin-top: 4px;
}
.cc-desc {
  font-size: 12px;
  color: var(--text-weak);
  margin-top: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cc-foot {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}
.teacher-link {
  font-size: 12px;
  color: var(--link);
  display: flex;
  align-items: center;
  gap: 2px;
}
</style>
