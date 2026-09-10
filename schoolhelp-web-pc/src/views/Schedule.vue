<template>
  <div class="schedule-page">
    <!-- 学期切换 -->
    <div class="semester-bar">
      <div class="sem-left">
        <el-select
          v-model="currentSemester"
          placeholder="选择学期"
          size="default"
          style="width: 200px"
          @change="loadSchedules"
        >
          <el-option
            v-for="s in semesters"
            :key="s"
            :label="s"
            :value="s"
          />
        </el-select>
        <span class="sem-tip">点击空白格子可快速添加课程</span>
      </div>
      <el-button
        type="primary"
        :icon="Plus"
        round
        @click="openAddDialog"
      >添加课程</el-button>
    </div>

    <!-- 课表主体 -->
    <div class="grid-wrap" v-loading="loading">
      <div class="grid">
        <!-- 表头：星期 -->
        <div class="grid-header time-col"></div>
        <div
          v-for="(d, di) in weekDays"
          :key="'h' + di"
          class="grid-header day-col"
          :class="{ weekend: di >= 5 }"
        >
          <div class="day-name">{{ d.name }}</div>
          <div class="day-date">{{ d.date }}</div>
        </div>

        <!-- 节次行 -->
        <template v-for="sec in sections" :key="'s' + sec">
          <div class="grid-cell time-cell">{{ sec }}</div>
          <div
            v-for="di in 7"
            :key="di"
            class="grid-cell slot-cell"
            :class="{ weekend: di >= 6 }"
            @click="onSlotClick(di, sec)"
          >
            <div
              v-for="item in getCellItems(di, sec)"
              :key="item.id"
              class="course-block"
              :class="'c-' + (item.colorIdx % 8)"
              @click.stop="onCourseClick(item)"
            >
              <div class="block-name">{{ item.courseName }}</div>
              <div class="block-room" v-if="item.room">{{ item.room }}</div>
            </div>
          </div>
        </template>
      </div>
    </div>

    <div class="legend">
      <span class="legend-item"><i class="dot"></i>点击格子可添加</span>
      <span class="legend-item"><i class="dot dot-blue"></i>点击课程查看详情</span>
    </div>

    <!-- 添加/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑课程' : '添加到课表'"
      width="620px"
      top="8vh"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="72px">
        <el-form-item label="课程" prop="courseId">
          <el-select
            v-model="form.courseId"
            placeholder="从课程库选择，或手动填写"
            filterable
            clearable
            style="width: 100%"
            @change="onCourseSelected"
          >
            <el-option
              v-for="c in courseOptions"
              :key="c.id"
              :label="c.name + (c.teacherName ? ' · ' + c.teacherName : '')"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="课程名" prop="courseName" v-if="!form.courseId">
          <el-input v-model="form.courseName" placeholder="手动输入课程名" />
        </el-form-item>
        <el-form-item label="时间">
          <div class="time-row">
            <el-select v-model="form.weekDay" style="width: 90px">
              <el-option
                v-for="(d, i) in weekDays"
                :key="i"
                :label="d.name"
                :value="i + 1"
              />
            </el-select>
            <span class="sep">第</span>
            <el-select v-model="form.startSection" style="width: 90px">
              <el-option v-for="s in 12" :key="s" :label="s + '节'" :value="s" />
            </el-select>
            <span class="sep">-</span>
            <el-select v-model="form.endSection" style="width: 90px">
              <el-option v-for="s in 12" :key="s" :label="s + '节'" :value="s" />
            </el-select>
          </div>
        </el-form-item>
        <el-form-item label="单双周">
          <el-radio-group v-model="form.weekType">
            <el-radio-button :value="0">每周</el-radio-button>
            <el-radio-button :value="1">单周</el-radio-button>
            <el-radio-button :value="2">双周</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="周次">
          <el-input v-model="form.weeks" placeholder="如 1-16 或 1,3,5-16" />
        </el-form-item>
        <el-form-item label="教室">
          <el-input v-model="form.room" placeholder="如 A101" />
        </el-form-item>
        <el-form-item label="教师">
          <el-input v-model="form.teacher" placeholder="教师姓名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-if="editingId" type="danger" plain @click="handleDelete">
          删除
        </el-button>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { mySchedules, mySemesters, addSchedule, updateSchedule, deleteSchedule } from '@/api/user'
import { courseList } from '@/api/course'

const router = useRouter()

const weekDays = [
  { name: '周一' }, { name: '周二' }, { name: '周三' }, { name: '周四' },
  { name: '周五' }, { name: '周六' }, { name: '周日' }
]
// 本周日期（用于表头展示）
function computeWeekDates() {
  const now = new Date()
  const day = (now.getDay() + 6) % 7 // 周一=0
  const monday = new Date(now)
  monday.setDate(now.getDate() - day)
  return weekDays.map((d, i) => {
    const dt = new Date(monday)
    dt.setDate(monday.getDate() + i)
    return { ...d, date: `${dt.getMonth() + 1}/${dt.getDate()}` }
  })
}

const daysWithDate = computed(() => {
  const arr = computeWeekDates()
  return weekDays.map((d, i) => ({ ...d, date: arr[i].date }))
})

const sections = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]

const schedules = ref([])
const semesters = ref([])
const currentSemester = ref('')
const courseOptions = ref([])
const loading = ref(false)

const dialogVisible = ref(false)
const editingId = ref(null)
const saving = ref(false)
const formRef = ref()

const form = reactive({
  courseId: null,
  courseName: '',
  weekDay: 1,
  startSection: 1,
  endSection: 2,
  weekType: 0,
  weeks: '1-16',
  room: '',
  teacher: '',
  semester: ''
})

const rules = {
  courseName: [{ required: true, message: '课程名必填', trigger: 'blur' }]
}

// 展开第 n 节所在行（考虑连堂课）
function getCellItems(dayIdx, section) {
  return schedules.value.filter((s) => {
    if (s.weekDay !== dayIdx) return false
    // 单双周过滤：当前节次行不受周次影响，整条展示（简化：只在首节展示）
    return s.startSection === section
  })
}

function colorOf(item) {
  return item.courseId ? (item.courseId % 8) : ((item.id || 1) % 8)
}

async function loadSemesters() {
  try {
    const res = await mySemesters()
    semesters.value = res.data || []
    if (!semesters.value.includes(currentSemester.value)) {
      // 默认选择当前学期或第一个
      const now = new Date()
      const cur = `${now.getFullYear()}-${now.getFullYear() + 1}-${now.getMonth() >= 7 ? 1 : 2}`
      currentSemester.value = semesters.value.includes(cur)
        ? cur
        : semesters.value[0] || cur
    }
  } catch (e) {
    currentSemester.value = defaultSemester()
  }
}

function defaultSemester() {
  const now = new Date()
  return `${now.getFullYear()}-${now.getFullYear() + 1}-${now.getMonth() >= 7 ? 1 : 2}`
}

async function loadSchedules() {
  if (!currentSemester.value) return
  loading.value = true
  try {
    const res = await mySchedules(currentSemester.value)
    schedules.value = (res.data || []).map((s, i) => ({ ...s, colorIdx: colorOf(s) }))
  } catch (e) {
    schedules.value = []
  } finally {
    loading.value = false
  }
}

async function loadCourseOptions() {
  try {
    const res = await courseList({})
    courseOptions.value = res.data || []
  } catch (e) {
    courseOptions.value = []
  }
}

function openAddDialog() {
  editingId.value = null
  Object.assign(form, {
    courseId: null,
    courseName: '',
    weekDay: 1,
    startSection: 1,
    endSection: 2,
    weekType: 0,
    weeks: '1-16',
    room: '',
    teacher: '',
    semester: currentSemester.value
  })
  dialogVisible.value = true
}

function onSlotClick(dayIdx, section) {
  openAddDialog()
  form.weekDay = dayIdx
  form.startSection = section
  form.endSection = section + 1
}

function onCourseClick(item) {
  if (item.courseId) {
    router.push(`/course/${item.courseId}`)
  } else {
    // 纯手动录入，无关联课程 → 编辑
    editItem(item)
  }
}

function editItem(item) {
  editingId.value = item.id
  Object.assign(form, {
    courseId: item.courseId || null,
    courseName: item.courseName || '',
    weekDay: item.weekDay,
    startSection: item.startSection,
    endSection: item.endSection,
    weekType: item.weekType || 0,
    weeks: item.weeks || '',
    room: item.room || '',
    teacher: item.teacher || '',
    semester: item.semester
  })
  dialogVisible.value = true
}

function onCourseSelected(id) {
  const c = courseOptions.value.find((x) => x.id === id)
  if (c) {
    form.courseName = c.name
    form.teacher = c.teacherName || ''
    form.room = form.room || ''
  }
}

async function handleSave() {
  await formRef.value.validate().catch(() => Promise.reject())
  // 校验 endSection >= startSection
  if (form.endSection < form.startSection) {
    ElMessage.warning('结束节次不能早于开始节次')
    return
  }
  saving.value = true
  try {
    const payload = { ...form }
    if (!payload.courseId) payload.courseId = null
    if (!payload.teacher) payload.teacher = null
    if (!payload.room) payload.room = null
    if (!payload.weeks) payload.weeks = null
    if (editingId.value) {
      await updateSchedule(editingId.value, payload)
      ElMessage.success('已更新')
    } else {
      await addSchedule(payload)
      ElMessage.success('已添加到课表')
    }
    dialogVisible.value = false
    loadSchedules()
  } catch (e) {
    // 拦截器提示
  } finally {
    saving.value = false
  }
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm('确定删除这门课？', '提示', { type: 'warning' })
    await deleteSchedule(editingId.value)
    ElMessage.success('已删除')
    dialogVisible.value = false
    loadSchedules()
  } catch (e) {
    /* 取消 */
  }
}

onMounted(async () => {
  await loadCourseOptions()
  await loadSemesters()
  await loadSchedules()
})
</script>

<style scoped>
.schedule-page {
  min-height: 100%;
}

.semester-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.sem-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.sem-tip {
  font-size: 12px;
  color: var(--text-weak);
}

.grid-wrap {
  background: #fff;
  border-radius: var(--radius);
  box-shadow: var(--shadow-card);
  padding: 14px;
}

.grid {
  display: grid;
  grid-template-columns: 46px repeat(7, 1fr);
  gap: 4px;
}

.grid-header {
  text-align: center;
  padding: 6px 2px 10px;
  font-weight: 600;
}

.day-name {
  font-size: 14px;
}

.day-date {
  font-size: 11px;
  color: var(--text-sub);
  font-weight: 400;
}

.weekend .day-name,
.weekend .day-date {
  color: #c0c4cc;
}

.time-cell {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  font-size: 11px;
  color: var(--text-sub);
  padding-top: 8px;
}

.slot-cell {
  min-height: 56px;
  background: #fafbfc;
  border: 1px solid #f0f1f3;
  position: relative;
  cursor: pointer;
  transition: background 0.15s;
  border-radius: 4px;
}

.slot-cell:hover {
  background: var(--primary-light);
}

.slot-cell.weekend {
  background: #f7f8f9;
}

.course-block {
  position: absolute;
  inset: 2px;
  border-radius: 6px;
  padding: 5px 7px;
  font-size: 12px;
  line-height: 1.4;
  overflow: hidden;
  cursor: pointer;
  color: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.14);
}

.block-name {
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.block-room {
  font-size: 11px;
  opacity: 0.9;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-top: 1px;
}

.time-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.sep {
  font-size: 12px;
  color: var(--text-sub);
}
</style>
