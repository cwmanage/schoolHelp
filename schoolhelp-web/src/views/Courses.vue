<template>
  <div class="courses-page">
    <!-- 搜索/筛选 -->
    <div class="filter-bar">
      <el-input
        v-model="keyword"
        placeholder="搜索课程名 / 教师"
        :prefix-icon="Search"
        clearable
        size="default"
        @input="filterCourses"
      />
      <el-button
        v-if="userStore.canManage"
        type="primary"
        :icon="Plus"
        round
        @click="router.push('/course/create')"
      >创建课程</el-button>
      <el-button
        v-else
        type="primary"
        :icon="Plus"
        plain
        round
        @click="router.push('/course/create')"
      >申请课程</el-button>
    </div>

    <!-- 课程卡片列表 -->
    <div v-loading="loading" class="course-list">
      <div
        v-for="c in filteredCourses"
        :key="c.id"
        class="course-card card"
        @click="router.push(`/course/${c.id}`)"
      >
        <div class="course-color" :style="{ background: colorOf(c.id) }">
          {{ c.name.charAt(0) }}
        </div>
        <div class="course-info">
          <div class="course-name">{{ c.name }}</div>
          <div class="course-meta">
            <span v-if="c.teacherName" class="meta-item">
              <el-icon><User /></el-icon>{{ c.teacherName }}
            </span>
            <span v-if="c.className" class="meta-item">
              <el-icon><School /></el-icon>{{ c.className }}
            </span>
          </div>
          <div class="course-semester" v-if="c.semester">{{ c.semester }}</div>
        </div>
        <el-icon class="arrow"><ArrowRight /></el-icon>
      </div>

      <el-empty
        v-if="!loading && filteredCourses.length === 0"
        description="暂无课程"
      >
        <el-button
          type="primary"
          @click="router.push('/course/create')"
        >{{ userStore.canManage ? '创建第一门课' : '申请第一门课' }}</el-button>
      </el-empty>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Plus, ArrowRight, User, School } from '@element-plus/icons-vue'
import { courseList } from '@/api/course'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const courses = ref([])
const keyword = ref('')
const loading = ref(false)

const filteredCourses = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return courses.value
  return courses.value.filter(
    (c) =>
      (c.name || '').toLowerCase().includes(k) ||
      (c.teacherName || '').toLowerCase().includes(k) ||
      (c.className || '').toLowerCase().includes(k)
  )
})

function colorOf(id) {
  const colors = ['#5b8cff', '#34c9a0', '#ff9f43', '#a66bff', '#ff6b81', '#2ec5d9', '#7f8ff4']
  return colors[(id || 1) % colors.length]
}

async function loadCourses() {
  loading.value = true
  try {
    const res = await courseList({})
    courses.value = res.data || []
  } catch (e) {
    courses.value = []
  } finally {
    loading.value = false
  }
}

function filterCourses() {
  // computed 自动处理
}

onMounted(loadCourses)
</script>

<style scoped>
.courses-page {
  min-height: 100%;
}

.filter-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.filter-bar .el-input {
  flex: 1;
}

.course-card {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}

.course-card:active {
  transform: scale(0.98);
}

.course-color {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 20px;
  font-weight: 600;
  flex-shrink: 0;
}

.course-info {
  flex: 1;
  min-width: 0;
}

.course-name {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 4px;
}

.course-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--text-sub);
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 3px;
}

.course-semester {
  font-size: 11px;
  color: #b6bcc4;
  margin-top: 3px;
}

.arrow {
  color: #c8ccd4;
  flex-shrink: 0;
}
</style>
