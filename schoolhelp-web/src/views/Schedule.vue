<template>
  <div class="schedule-page">
    <!-- 学期切换 -->
    <div class="semester-bar">
      <el-select
        v-model="currentSemester"
        placeholder="选择学期"
        size="small"
        style="width: 180px"
        @change="loadSchedules"
      >
        <el-option
          v-for="s in semesters"
          :key="s"
          :label="s"
          :value="s"
        />
      </el-select>
      <div class="sem-actions">
        <el-button size="small" :icon="Camera" round @click="ocrVisible = true">识图</el-button>
        <el-button
          type="primary"
          size="small"
          :icon="Plus"
          round
          @click="openAddDialog"
        >添加课程</el-button>
      </div>
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
              :style="{ height: blockHeight(item) }"
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

    <!-- 天气详情入口 -->
    <div class="weather-entry">
      <WeatherPanel ref="weatherRef" :auto="true" />
    </div>

    <!-- 添加/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑课程' : '添加到课表'"
      width="92%"
      top="6vh"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="72px">
        <el-form-item label="课程" prop="courseId">
          <el-select :teleported="false"
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
            <el-select :teleported="false" v-model="form.weekDay" style="width: 90px">
              <el-option
                v-for="(d, i) in weekDays"
                :key="i"
                :label="d.name"
                :value="i + 1"
              />
            </el-select>
            <span class="sep">第</span>
            <el-select :teleported="false" v-model="form.startSection" style="width: 90px">
              <el-option v-for="s in 12" :key="s" :label="s + '节'" :value="s" />
            </el-select>
            <span class="sep">-</span>
            <el-select :teleported="false" v-model="form.endSection" style="width: 90px">
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

    <!-- AI 截图导入对话框 -->
    <el-dialog v-model="ocrVisible" title="AI 识别课表截图" width="94%" top="4vh" @closed="resetOcr">
      <div class="ocr-body">
        <div v-if="!ocrLoading && ocrItems.length === 0" class="ocr-upload">
          <el-upload
            accept="image/jpeg,image/png,image/webp"
            :auto-upload="false"
            :show-file-list="false"
            :on-change="onOcrFile"
          >
            <div class="ocr-upload-box">
              <el-icon :size="34"><Camera /></el-icon>
              <div>点此选择课表截图（可拍照）</div>
            </div>
          </el-upload>
          <div class="ocr-tip">支持 jpg / png / webp，≤5MB；识别后可核对修改再导入</div>
        </div>

        <div v-else-if="ocrLoading" class="ocr-loading" v-loading="true" element-loading-text="AI 识别中…"></div>

        <template v-else>
          <div class="ocr-bar">
            <span>识别 {{ ocrItems.length }} 条，请核对后导入</span>
            <div class="ocr-bar-right">
              <el-select :teleported="false" v-model="ocrSemester" filterable allow-create style="width: 150px" size="small">
                <el-option v-for="s in semesterOptions" :key="s" :label="s" :value="s" />
              </el-select>
              <el-button size="small" @click="resetOcr">重选</el-button>
            </div>
          </div>
          <div class="ocr-cards">
            <div v-for="(row, idx) in ocrItems" :key="idx" class="ocr-card">
              <div class="ocr-card-head">
                <el-input v-model="row.courseName" size="small" placeholder="课程名" />
                <el-button link type="danger" size="small" @click="ocrItems.splice(idx, 1)">删除</el-button>
              </div>
              <div class="ocr-card-row">
                <el-select :teleported="false" v-model="row.weekDay" size="small" style="width: 84px">
                  <el-option v-for="(d, i) in weekDays" :key="i" :label="d.name" :value="i + 1" />
                </el-select>
                <el-select :teleported="false" v-model="row.startSection" size="small" style="width: 72px">
                  <el-option v-for="s in 12" :key="s" :label="s + '节'" :value="s" />
                </el-select>
                <span class="sep">-</span>
                <el-select :teleported="false" v-model="row.endSection" size="small" style="width: 72px">
                  <el-option v-for="s in 12" :key="s" :label="s + '节'" :value="s" />
                </el-select>
                <el-select :teleported="false" v-model="row.weekType" size="small" style="width: 78px">
                  <el-option :value="0" label="每周" />
                  <el-option :value="1" label="单周" />
                  <el-option :value="2" label="双周" />
                </el-select>
              </div>
              <div class="ocr-card-row">
                <el-input v-model="row.weeks" size="small" placeholder="周次，如 1-16" />
                <el-input v-model="row.room" size="small" placeholder="教室" />
                <el-input v-model="row.teacher" size="small" placeholder="教师" />
              </div>
            </div>
          </div>
        </template>
      </div>
      <template #footer>
        <el-button size="small" @click="ocrVisible = false">取消</el-button>
        <el-button size="small" type="primary" :disabled="ocrItems.length === 0" :loading="importing" @click="importOcr">
          确认导入（{{ ocrItems.length }}）
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Camera } from '@element-plus/icons-vue'
import { mySchedules, mySemesters, addSchedule, updateSchedule, deleteSchedule, aiScheduleOcr } from '@/api/user'
import { courseList } from '@/api/course'
import WeatherPanel from '@/components/WeatherPanel.vue'

const weatherRef = ref()

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

// 连堂课跨行高度：行高/行距与下方样式保持一致（46px 行、3px 间距、上下各缩 2px）
const ROW_H = 46
const ROW_GAP = 3
function blockHeight(item) {
  const span = Math.max(1, (item.endSection || item.startSection) - item.startSection + 1)
  return (span * ROW_H + (span - 1) * ROW_GAP - 4) + 'px'
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

// ==================== AI 截图导入 ====================
const ocrVisible = ref(false)
const ocrLoading = ref(false)
const importing = ref(false)
const ocrItems = ref([])
const ocrSemester = ref('')

const semesterOptions = computed(() => {
  const set = new Set(semesters.value)
  set.add(defaultSemester())
  return [...set]
})

async function onOcrFile(uploadFile) {
  const raw = uploadFile?.raw
  if (!raw || ocrLoading.value) return
  if (raw.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片不能超过 5MB')
    return
  }
  ocrLoading.value = true
  ocrItems.value = []
  try {
    const fd = new FormData()
    fd.append('file', raw)
    const res = await aiScheduleOcr(fd)
    ocrItems.value = res.data || []
    if (!ocrSemester.value) ocrSemester.value = currentSemester.value || defaultSemester()
    ElMessage.success(`识别出 ${ocrItems.value.length} 条课程，请核对`)
  } catch (e) {
    // 拦截器已提示
  } finally {
    ocrLoading.value = false
  }
}

function resetOcr() {
  ocrItems.value = []
  ocrLoading.value = false
}

async function importOcr() {
  if (!ocrSemester.value) {
    ElMessage.warning('请选择学期')
    return
  }
  const invalid = ocrItems.value.find((x) => !String(x.courseName).trim() || x.endSection < x.startSection)
  if (invalid) {
    ElMessage.warning('存在课程名为空或节次倒置的条目，请修正或删除')
    return
  }
  importing.value = true
  let ok = 0
  let fail = 0
  try {
    for (const item of ocrItems.value) {
      try {
        await addSchedule({
          courseId: null,
          courseName: String(item.courseName).trim(),
          weekDay: item.weekDay,
          startSection: item.startSection,
          endSection: item.endSection,
          weekType: item.weekType ?? 0,
          weeks: item.weeks || null,
          room: item.room || null,
          teacher: item.teacher || null,
          semester: ocrSemester.value
        })
        ok++
      } catch (e) {
        fail++
      }
    }
    if (fail === 0) {
      ElMessage.success(`已导入 ${ok} 条课程`)
    } else {
      ElMessage.warning(`导入完成：成功 ${ok} 条，失败 ${fail} 条`)
    }
    ocrVisible.value = false
    currentSemester.value = ocrSemester.value
    loadSchedules()
  } finally {
    importing.value = false
  }
}
</script>

<style scoped>
.schedule-page {
  min-height: 100%;
}

.semester-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.sem-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

/* ===== AI 截图导入 ===== */
.ocr-upload-box {
  width: 100%;
  padding: 26px 12px;
  border: 1px dashed #c0cfe0;
  border-radius: 12px;
  background: #f7fbfe;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: #4a7a9b;
  font-size: 13px;
}
.ocr-tip {
  text-align: center;
  font-size: 11px;
  color: var(--text-sub);
  margin-top: 8px;
}
.ocr-loading {
  height: 200px;
}
.ocr-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--text-sub);
  flex-wrap: wrap;
  gap: 6px;
}
.ocr-bar-right {
  display: flex;
  align-items: center;
  gap: 6px;
}
.ocr-cards {
  max-height: 52vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.ocr-card {
  border: 1px solid #eef2f6;
  border-radius: 10px;
  padding: 8px;
  background: #fafcfe;
}
.ocr-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.ocr-card-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
}

.grid-wrap {
  background: #fff;
  border-radius: var(--radius);
  overflow-x: auto;
  box-shadow: var(--shadow-card);
  padding: 6px;
}

.grid {
  display: grid;
  grid-template-columns: 30px repeat(7, 1fr);
  gap: 3px;
  min-width: 640px;
}

.grid-header {
  text-align: center;
  padding: 6px 2px;
  font-size: 12px;
  font-weight: 500;
}

.day-name {
  font-size: 12px;
}

.day-date {
  font-size: 10px;
  color: var(--text-sub);
}

.weekend .day-name,
.weekend .day-date {
  color: #c0c4cc;
}

.grid-cell {
  border-radius: 6px;
}

.time-cell {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  font-size: 11px;
  color: var(--text-sub);
  padding-top: 6px;
}

.slot-cell {
  min-height: 46px;
  background: #fafbfc;
  border: 1px solid #f0f1f3;
  position: relative;
  cursor: pointer;
  transition: background 0.15s;
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
  padding: 3px 4px;
  font-size: 11px;
  line-height: 1.3;
  overflow: hidden;
  cursor: pointer;
  color: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12);
  z-index: 5; /* 连堂课跨行时盖住下方格子 */
}

.block-name {
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.block-room {
  font-size: 10px;
  opacity: 0.9;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.c-0 { background: #5b8cff; }
.c-1 { background: #34c9a0; }
.c-2 { background: #ff9f43; }
.c-3 { background: #a66bff; }
.c-4 { background: #ff6b81; }
.c-5 { background: #2ec5d9; }
.c-6 { background: #f6a5c0; }
.c-7 { background: #7f8ff4; }

.legend {
  display: flex;
  gap: 14px;
  margin-top: 10px;
  font-size: 11px;
  color: var(--text-sub);
  padding: 0 4px;
}

.weather-entry {
  margin-top: 12px;
  display: flex;
  justify-content: center;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--primary-light);
  border: 1px dashed var(--primary);
  display: inline-block;
}

.dot-blue {
  background: #5b8cff;
  border: none;
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
