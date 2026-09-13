<template>
  <div class="schedule-page">
    <!-- 学期切换 -->
    <div class="semester-bar">
      <el-select
        v-model="currentSemester"
        placeholder="选择学期"
        size="small"
        style="width: 150px"
        @change="onSemesterChange"
      >
        <el-option
          v-for="s in semesters"
          :key="s"
          :label="s"
          :value="s"
        />
      </el-select>
      <div class="sem-actions">
        <el-button size="small" :icon="Timer" round @click="openTimeConfig">作息</el-button>
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

    <div class="week-banner">
      <span class="week-badge" v-if="viewWeek">第 {{ viewWeek }} 周<span v-if="weekOffset !== 0">（视图）</span></span>
      <el-button-group v-if="week1Date" size="small" class="week-nav">
        <el-button size="small" :icon="ArrowLeft" @click="weekOffset--" />
        <el-button size="small" @click="weekOffset = 0" :type="weekOffset === 0 ? 'primary' : 'default'">本周</el-button>
        <el-button size="small" :icon="ArrowRight" @click="weekOffset++" />
      </el-button-group>
      <span class="week-warn" v-else-if="currentSemester" @click="openTimeConfig">
        ⓘ 设置学期开始日期后可显示周数、虚化非本周课程
      </span>
      <span class="week-dim-tip" v-else>非本周课程将虚化显示</span>
    </div>

    <!-- 课表主体 -->
    <div class="grid-wrap" v-loading="loading">
      <div class="grid">
        <!-- 表头：星期（带日期，支持翻页） -->
        <div class="grid-header time-col"></div>
        <div
          v-for="(d, di) in daysWithDate"
          :key="'h' + di"
          class="grid-header day-col"
          :class="{ weekend: di >= 5, today: d.iso === todayIso && weekOffset === 0 }"
        >
          <div class="day-name">{{ d.name }}</div>
          <div class="day-date">{{ d.date }}</div>
        </div>

        <!-- 节次行 -->
        <template v-for="sec in visibleSections" :key="'s' + sec">
          <div class="grid-cell time-cell">
            <div class="sec-no">{{ sec }}</div>
            <div class="sec-time" v-if="sectionTime(sec)">
              {{ sectionTime(sec).startTime }}<br />{{ sectionTime(sec).endTime }}
            </div>
            <div class="sec-time" v-else>--</div>
          </div>
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
              :class="['c-' + (item.colorIdx % 8), { dimmed: !isThisWeek(item) }]"
              :style="{ height: blockHeight(item) }"
              @click.stop="onCourseClick(item)"
            >
              <div class="block-name">{{ item.courseName }}</div>
              <div class="block-room" v-if="item.room">{{ item.room }}</div>
              <div class="block-change" v-if="item.isChanged">调</div>
            </div>
          </div>
        </template>

        <!-- 大学活动行：当天考试/竞赛/节假日/活动 -->
        <div class="grid-cell time-cell activity-head">
          <div class="sec-no">活动</div>
        </div>
        <div v-for="di in 7" :key="'act' + di" class="grid-cell slot-cell activity-cell">
          <template v-if="dayEvents(di).length">
            <div class="act-item" v-for="e in dayEvents(di)" :key="e.id">
              <span class="act-dot" :class="'act-' + e.eventType"></span>
              <span class="act-name">{{ e.title }}</span>
              <span class="act-note" v-if="e.timeNote">{{ e.timeNote }}</span>
            </div>
          </template>
          <span v-else class="act-empty">—</span>
        </div>
      </div>
    </div>

    <div class="legend">
      <span class="legend-item"><i class="dot"></i>点击格子可添加</span>
      <span class="legend-item"><i class="dot dot-blue"></i>点击课程查看详情</span>
      <span class="legend-item"><i class="dot dot-dim"></i>非本周上课的课程会虚化显示</span>
    </div>

    <!-- 天气详情入口 -->
    <div class="weather-entry">
      <WeatherPanel ref="weatherRef" :auto="true" />
    </div>

    <!-- 本周校园日历 -->
    <div class="calendar-card" v-if="calendarEvents.length">
      <div class="calendar-title">本周校园日历（考试 · 竞赛 · 节假日）</div>
      <div class="calendar-item" v-for="e in calendarEvents" :key="e.id">
        <el-tag size="small" :type="calTagType(e.eventType)">{{ calTypeText(e.eventType) }}</el-tag>
        <span class="cal-date">{{ calDateRange(e) }}</span>
        <span class="cal-name">{{ e.title }}</span>
        <span class="cal-note" v-if="e.timeNote">{{ e.timeNote }}</span>
      </div>
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
        <el-form-item v-if="editingId && editingGroup.length > 1" label="应用范围">
          <el-radio-group v-model="editScope">
            <el-radio value="one">仅此时段</el-radio>
            <el-radio value="all">全部 {{ editingGroup.length }} 个时段</el-radio>
          </el-radio-group>
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

    <!-- 课程详情弹窗 -->
    <el-dialog v-model="detailVisible" title="课程详情" width="92%" top="8vh">
      <template v-if="detailItem">
        <div class="detail-name">{{ detailItem.courseName }}</div>
        <div class="detail-rows">
          <div class="detail-row"><span class="dl">时间</span><span>{{ weekDayName(detailItem.weekDay) }} 第{{ detailItem.startSection }}-{{ detailItem.endSection }}节</span></div>
          <div class="detail-row"><span class="dl">单双周</span><span>{{ weekTypeText(detailItem.weekType) }}</span></div>
          <div class="detail-row"><span class="dl">周次</span><span>{{ detailItem.weeks || '不限' }}</span></div>
          <div class="detail-row"><span class="dl">教室</span><span>{{ detailItem.room || '—' }}</span></div>
          <div class="detail-row"><span class="dl">教师</span><span>{{ detailItem.teacher || '—' }}</span></div>
          <div class="detail-row"><span class="dl">学期</span><span>{{ detailItem.semester }}</span></div>
          <div class="detail-row">
            <span class="dl">课程库</span>
            <span>
              <template v-if="detailItem.courseId">已关联课程库，可查看课程作业与资料</template>
              <template v-else-if="isInLib">同名同教师的课程已在课程库</template>
              <template v-else>未录入课程库</template>
            </span>
          </div>
        </div>
        <div class="detail-group" v-if="detailGroup.length > 1">
          <div class="detail-group-title">本课共 {{ detailGroup.length }} 个时段：</div>
          <div class="detail-group-item" v-for="g in detailGroup" :key="g.id">
            {{ weekDayName(g.weekDay) }} 第{{ g.startSection }}-{{ g.endSection }}节（{{ g.room || '无教室' }}）
          </div>
        </div>
      </template>
      <template #footer>
        <el-button size="small" @click="detailVisible = false">关闭</el-button>
        <el-button v-if="detailItem && !detailItem.courseId && !isInLib" size="small" type="success" plain @click="openApply">
          申请入库
        </el-button>
        <el-button v-if="detailItem && (detailItem.courseId || isInLib)" size="small" @click="goCourseLib">
          课程详情
        </el-button>
        <el-button size="small" type="primary" @click="editFromDetail">编辑</el-button>
      </template>
    </el-dialog>

    <!-- 申请录入课程库弹窗 -->
    <el-dialog v-model="applyVisible" title="申请录入课程库" width="92%" top="8vh">
      <el-form :model="applyForm" label-width="72px">
        <el-form-item label="课程名" required>
          <el-input v-model="applyForm.name" placeholder="课程名" />
        </el-form-item>
        <el-form-item label="教师">
          <el-input v-model="applyForm.teacherName" placeholder="授课教师" />
        </el-form-item>
        <el-form-item label="教室">
          <el-input v-model="applyForm.room" placeholder="常用教室" />
        </el-form-item>
        <el-form-item label="班级">
          <el-input v-model="applyForm.className" placeholder="如 软件2301" />
        </el-form-item>
        <el-form-item label="学期">
          <el-input v-model="applyForm.semester" placeholder="如 2026-2027-1" />
        </el-form-item>
        <el-form-item label="申请说明">
          <el-input v-model="applyForm.applyNote" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <div class="apply-tip">
        {{ userStore.canManage ? '你是班长/管理员，提交后将直接录入课程库。' : '提交后由管理员审批，可在「我的申请」跟踪进度。' }}
      </div>
      <template #footer>
        <el-button size="small" @click="applyVisible = false">取消</el-button>
        <el-button size="small" type="primary" :loading="applySaving" @click="submitApply">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 作息设置弹窗 -->
    <el-dialog v-model="tcVisible" title="作息设置（每节课时间）" width="94%" top="4vh">
      <div class="tc-bar">
        <span>每天</span>
        <el-select v-model="tcSections" style="width: 82px" size="small" :teleported="false" @change="onTcSectionsChange">
          <el-option v-for="n in 9" :key="n + 3" :label="(n + 3) + ' 节'" :value="n + 3" />
        </el-select>
        <el-button size="small" type="primary" plain :loading="tcSuggesting" @click="aiSuggest">
          AI 建议
        </el-button>
      </div>
      <div class="tc-gap-row">
        <span>课间(分钟)：</span>
        <el-input-number v-model="tcGap" :min="0" :max="60" size="small" style="width: 96px" @change="recalcFrom(0)" />
        <span class="tc-tip">改某节时间，后面自动顺延</span>
      </div>
      <div class="tc-gap-row">
        <span>每节时长：</span>
        <el-input-number v-model="tcDuration" :min="20" :max="120" size="small" style="width: 90px" @change="recalcFrom(0)" />
        <span>课间：</span>
        <el-input-number v-model="tcGap" :min="0" :max="60" size="small" style="width: 90px" @change="recalcFrom(0)" />
        <span class="tc-tip">改上课时间自动算下课时间</span>
      </div>
      <div class="tc-week1">
        <span>学期开始（第一周周一）：</span>
        <el-date-picker
          v-model="week1DateInput"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="第一周任意一天"
          style="width: 140px"
          size="small"
          clearable
        />
        <el-button size="small" @click="saveWeek1">保存</el-button>
      </div>
      <div class="tc-list">
        <div class="tc-row" v-for="(row, idx) in tcRows" :key="row.section">
          <span class="tc-no">{{ row.section }}</span>
          <el-time-select
            v-model="row.startTime"
            start="06:00" end="23:00" step="00:05"
            placeholder="开始"
            :teleported="false"
            @change="onTimeEdit(idx)"
          />
          <span class="tc-sep">—</span>
          <el-time-select
            v-model="row.endTime"
            :start="row.startTime || '06:00'" end="23:59" step="00:05"
            placeholder="结束"
            :teleported="false"
            @change="onTimeEdit(idx)"
          />
        </div>
      </div>
      <template #footer>
        <el-button size="small" @click="tcVisible = false">取消</el-button>
        <el-button size="small" type="primary" :loading="tcSaving" @click="saveTc">保存</el-button>
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
          <div class="ocr-apply-lib">
            <el-switch
              v-model="ocrApplyLib"
              size="small"
              active-text="同时申请录入课程库"
              @change="onApplyLibSwitch"
            />
          </div>
          <div class="ocr-cards">
            <div v-for="(row, idx) in ocrItems" :key="idx" class="ocr-card">
              <div class="ocr-card-head">
                <el-checkbox v-if="ocrApplyLib" v-model="row.applyLib" size="small">入库</el-checkbox>
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
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Camera, Timer, ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { mySchedules, mySemesters, addSchedule, updateSchedule, deleteSchedule, aiScheduleOcr, getTimeConfig, saveTimeConfig, aiTimeSuggest, getSemesterSetting, saveSemesterSetting, getScheduleChanges, addScheduleChange, deleteScheduleChange, getCalendarEvents } from '@/api/user'
import { courseList, createCourse } from '@/api/course'
import { useUserStore } from '@/stores/user'
import WeatherPanel from '@/components/WeatherPanel.vue'

const weatherRef = ref()

const router = useRouter()
const userStore = useUserStore()

const weekDays = [
  { name: '周一' }, { name: '周二' }, { name: '周三' }, { name: '周四' },
  { name: '周五' }, { name: '周六' }, { name: '周日' }
]
// 本周日期（用于表头展示；offset 支持翻页查看其他周）
const weekOffset = ref(0)

/** 视图周数 = 真实当前周 + 翻页偏移 */
const viewWeek = computed(() => {
  if (currentWeek.value == null) return null
  const w = currentWeek.value + weekOffset.value
  return w >= 1 ? w : null
})

/** 是否视图周上课（保持与 PC 一致：视图周无此课则虚化） */
function isThisWeek(item) {
  if (viewWeek.value == null) return true
  return weeksSet(item).has(viewWeek.value)
}

function computeWeekDates(offset = 0) {
  const now = new Date()
  const day = (now.getDay() + 6) % 7 // 周一=0
  const monday = new Date(now)
  monday.setDate(now.getDate() - day + offset * 7)
  return weekDays.map((d, i) => {
    const dt = new Date(monday)
    dt.setDate(monday.getDate() + i)
    const iso = dt.getFullYear() + '-' + String(dt.getMonth() + 1).padStart(2, '0') + '-' + String(dt.getDate()).padStart(2, '0')
    return { ...d, date: `${dt.getMonth() + 1}/${dt.getDate()}`, iso }
  })
}

const daysWithDate = computed(() => computeWeekDates(weekOffset.value))

// 展示的节次 = 作息配置节数与课程最大节次取大（保证已有课程不丢失）
const timeConfig = ref([])
const visibleSections = computed(() => {
  let maxUsed = 0
  for (const s of displaySchedules.value) {
    if ((s.endSection || 0) > maxUsed) maxUsed = s.endSection || 0
  }
  const n = Math.max(timeConfig.value.length, maxUsed, 4)
  return Array.from({ length: n }, (_, i) => i + 1)
})

function sectionTime(sec) {
  return timeConfig.value.find((t) => t.section === sec) || null
}

async function loadTimeConfig() {
  try {
    const res = await getTimeConfig()
    timeConfig.value = res.data || []
  } catch (e) {
    timeConfig.value = []
  }
}

const schedules = ref([])
const semesters = ref([])
const currentSemester = ref('')
const courseOptions = ref([])
const loading = ref(false)
const week1Date = ref('')
const week1DateInput = ref('')
const changes = ref([])
const calendarEvents = ref([])

/** 当前是本学期第几周（未设置第一周日期返回 null） */
const currentWeek = computed(() => {
  if (!week1Date.value) return null
  const w1 = new Date(week1Date.value + 'T00:00:00')
  const now = new Date()
  const diff = Math.floor((now - w1) / 86400000)
  const week = Math.floor(diff / 7) + 1
  return week >= 1 && week <= 30 ? week : null
})

/** 周次文本展开为集合；weeks 为空视为每周都上 */
function weeksSet(item) {
  const s = new Set()
  if (!item.weeks) {
    for (let i = 1; i <= 30; i++) s.add(i)
  } else {
    for (const part of String(item.weeks).split(',')) {
      const p = part.trim()
      if (!p) continue
      if (p.includes('-')) {
        const [a, b] = p.split('-').map(Number)
        if (!isNaN(a) && !isNaN(b)) {
          for (let i = a; i <= b; i++) if (i >= 1) s.add(i)
        }
      } else {
        const n = Number(p)
        if (!isNaN(n) && n >= 1) s.add(n)
      }
    }
  }
  if (item.weekType === 1) return new Set([...s].filter((w) => w % 2 === 1))
  if (item.weekType === 2) return new Set([...s].filter((w) => w % 2 === 0))
  return s
}

/** 是否视图周上课（保持与 PC 一致：视图周无此课则虚化） */

/** 应用本周调课后的渲染数据（原位置隐藏，新位置显示虚拟条目；仅真实本周视图应用调课） */
const displaySchedules = computed(() => {
  const out = []
  const applyChanges = weekOffset.value === 0
  const changedIds = new Set(applyChanges ? changes.value.map((c) => c.scheduleId) : [])
  for (const s of schedules.value) {
    if (!changedIds.has(s.id)) out.push({ ...s, isChanged: false })
  }
  if (applyChanges) {
    for (const c of changes.value) {
      const orig = schedules.value.find((s) => s.id === c.scheduleId)
      if (!orig) continue
      out.push({
        ...orig,
        id: 'chg-' + c.id,
        scheduleId: c.scheduleId,
        changeId: c.id,
        weekDay: c.weekDay,
        startSection: c.startSection,
        endSection: c.endSection,
        isChanged: true
      })
    }
  }
  return out.map((s) => ({ ...s, colorIdx: colorOf(s) }))
})

function getCellItems(dayIdx, section) {
  return displaySchedules.value.filter((s) => {
    if (s.weekDay !== dayIdx) return false
    return s.startSection === section
  })
}

const dialogVisible = ref(false)
const editingId = ref(null)
const saving = ref(false)
const formRef = ref()
const editingGroup = ref([])
const editScope = ref('one') // 编辑/删除应用范围：one=仅此时段 all=该课全部时段

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

// 连堂课跨行高度：行高/行距与下方样式保持一致（46px 行、3px 间距、上下各缩 2px）
const ROW_H = 46
const ROW_GAP = 3
function blockHeight(item) {
  const span = Math.max(1, (item.endSection || item.startSection) - item.startSection + 1)
  return (span * ROW_H + (span - 1) * ROW_GAP - 4) + 'px'
}

function colorOf(item) {
  if (item.courseId) return item.courseId % 8
  // 无关联时按课程名哈希取色：同一门课的多个时段同色
  let h = 0
  const s = item.courseName || ''
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0
  return h % 8
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
    schedules.value = res.data || []
  } catch (e) {
    schedules.value = []
  } finally {
    loading.value = false
  }
}

/** 学期切换：重载课表 + 学期设置 + 调课 + 日历 */
function onSemesterChange() {
  loadSchedules()
  loadWeek1()
  loadChanges()
  loadCalendarEvents()
}

function thisMonday() {
  const d = new Date()
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7))
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0')
}

function dateOffset(base, days) {
  const d = new Date(base + 'T00:00:00')
  d.setDate(d.getDate() + days)
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0')
}

async function loadWeek1() {
  try {
    const res = await getSemesterSetting(currentSemester.value)
    week1Date.value = (res.data && res.data.week1Date) || ''
    week1DateInput.value = week1Date.value
  } catch (e) {
    week1Date.value = ''
  }
}

async function saveWeek1() {
  if (!week1DateInput.value) {
    ElMessage.warning('请选择第一周开始日期')
    return
  }
  try {
    await saveSemesterSetting(currentSemester.value, week1DateInput.value)
    week1Date.value = week1DateInput.value
    ElMessage.success('学期开始日期已保存')
  } catch (e) {
    // 拦截器已提示
  }
}

async function loadChanges() {
  try {
    const res = await getScheduleChanges(thisMonday())
    changes.value = res.data || []
  } catch (e) {
    changes.value = []
  }
}

const todayIso = new Date().getFullYear() + '-' + String(new Date().getMonth() + 1).padStart(2, '0') + '-' + String(new Date().getDate()).padStart(2, '0')

/** 视图周第 di 天（1=周一）的日历事件 */
function dayEvents(di) {
  const dates = computeWeekDates(weekOffset.value)
  const dateStr = dates[di - 1]?.iso
  if (!dateStr) return []
  return calendarEvents.value.filter((e) => {
    if (e.eventDate) return e.eventDate === dateStr
    if (e.dateStart && e.dateEnd) return e.dateStart <= dateStr && dateStr <= e.dateEnd
    return false
  })
}

async function loadCalendarEvents() {
  const monday = dateOffset(thisMonday(), weekOffset.value * 7)
  try {
    const res = await getCalendarEvents(monday, dateOffset(monday, 6))
    calendarEvents.value = res.data || []
  } catch (e) {
    calendarEvents.value = []
  }
}

// 翻页：切周重新加载该周日历
watch(weekOffset, () => {
  loadCalendarEvents()
})

function calTagType(t) {
  return { exam: 'danger', contest: 'warning', holiday: 'success', activity: 'info' }[t] || 'info'
}

function calTypeText(t) {
  return { exam: '考试', contest: '竞赛', holiday: '节假日', activity: '活动' }[t] || '活动'
}

function calDateRange(e) {
  if (e.eventDate) return e.eventDate
  if (e.dateStart && e.dateEnd) return e.dateStart + ' ~ ' + e.dateEnd
  return e.dateStart || ''
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
  // 点击课程统一进详情弹窗（编辑/申请入库等操作在详情内发起）
  openDetail(item)
}

// ==================== 课程详情弹窗 ====================
const detailVisible = ref(false)
const detailItem = ref(null)
const detailGroup = ref([])

function weekDayName(d) {
  return weekDays[d - 1]?.name || ''
}

function weekTypeText(t) {
  return t === 1 ? '单周' : t === 2 ? '双周' : '每周'
}

function openDetail(item) {
  detailItem.value = item
  // 同名同学期的所有时段（一门课每周多次）
  detailGroup.value = schedules.value.filter(
    (s) => s.semester === item.semester && s.courseName === item.courseName
  )
  detailVisible.value = true
}

/** 同名同教师的课程是否已在课程库（已通过状态）——已入库则不再重复申请 */
const isInLib = computed(() => {
  const it = detailItem.value
  if (!it) return false
  return courseOptions.value.some(
    (c) => c.name === it.courseName && ((c.teacherName || '') === (it.teacher || ''))
  )
})

function editFromDetail() {
  detailVisible.value = false
  editItem(detailItem.value)
}

function goCourseLib() {
  const it = detailItem.value
  // 优先用条目关联的课程库 ID；未关联时匹配同名同教师的已入库课程
  let id = it?.courseId
  if (!id && it) {
    const m = courseOptions.value.find(
      (c) => c.name === it.courseName && ((c.teacherName || '') === (it.teacher || ''))
    )
    id = m?.id
  }
  detailVisible.value = false
  if (id) router.push(`/course/${id}`)
}

// ==================== 申请录入课程库 ====================
const applyVisible = ref(false)
const applySaving = ref(false)
const applyForm = reactive({ name: '', teacherName: '', room: '', className: '', semester: '', applyNote: '' })

function openApply() {
  const it = detailItem.value
  Object.assign(applyForm, {
    name: it?.courseName || '',
    teacherName: it?.teacher || '',
    room: it?.room || '',
    className: '',
    semester: it?.semester || currentSemester.value,
    applyNote: '来自个人课表'
  })
  detailVisible.value = false
  applyVisible.value = true
}

async function submitApply() {
  if (!applyForm.name.trim()) {
    ElMessage.warning('课程名必填')
    return
  }
  applySaving.value = true
  try {
    await createCourse({
      name: applyForm.name.trim(),
      teacherName: applyForm.teacherName || null,
      room: applyForm.room || null,
      className: applyForm.className || null,
      semester: applyForm.semester || null,
      applyNote: applyForm.applyNote || null
    })
    applyVisible.value = false
    loadCourseOptions()
    ElMessage.success(userStore.canManage ? '已录入课程库' : '申请已提交，等待管理员审批')
  } catch (e) {
    // 拦截器已提示
  } finally {
    applySaving.value = false
  }
}

function editItem(item) {
  editingId.value = item.id
  // 同课多时段分组（编辑/删除时可选联动范围）
  editingGroup.value = schedules.value.filter(
    (s) => s.semester === item.semester && s.courseName === item.courseName
  )
  editScope.value = 'one'
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
    // 带入课程库的常用教室（课表已有教室则不覆盖）
    if (c.room && !form.room) form.room = c.room
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
      // 联动：该课全部时段同步公共属性（课程名/教师/教室/单双周/周次），时间字段保留各自
      if (editScope.value === 'all' && editingGroup.value.length > 1) {
        for (const g of editingGroup.value) {
          if (g.id === editingId.value) continue
          await updateSchedule(g.id, {
            ...payload,
            weekDay: g.weekDay,
            startSection: g.startSection,
            endSection: g.endSection,
            semester: g.semester
          })
        }
      }
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
  const all = editScope.value === 'all' && editingGroup.value.length > 1
  const tip = all
    ? `将删除「${form.courseName}」的全部 ${editingGroup.value.length} 个时段，确认？`
    : '确定删除这门课？'
  try {
    await ElMessageBox.confirm(tip, '提示', { type: 'warning' })
    if (all) {
      for (const g of editingGroup.value) {
        await deleteSchedule(g.id)
      }
    } else {
      await deleteSchedule(editingId.value)
    }
    ElMessage.success('已删除')
    dialogVisible.value = false
    loadSchedules()
  } catch (e) {
    /* 取消 */
  }
}

onMounted(async () => {
  await loadCourseOptions()
  await loadTimeConfig()
  await loadSemesters()
  await loadSchedules()
  await loadWeek1()
  await loadChanges()
  await loadCalendarEvents()
})

// ==================== 作息设置 ====================
const tcVisible = ref(false)
const tcSaving = ref(false)
const tcSuggesting = ref(false)
const tcSections = ref(12)
const tcGap = ref(10)
const tcDuration = ref(45) // 每节课时长（分钟），改上课时间时自动算下课时间
const tcRows = ref([])

function toMin(t) {
  if (!t || !/^\d{1,2}:\d{2}$/.test(t)) return null
  const [h, m] = t.split(':').map(Number)
  return h * 60 + m
}

function toHHMM(min) {
  const m = ((min % 1440) + 1440) % 1440
  return String(Math.floor(m / 60)).padStart(2, '0') + ':' + String(m % 60).padStart(2, '0')
}

function openTimeConfig() {
  tcRows.value = timeConfig.value.map((t) => ({ ...t }))
  tcSections.value = Math.max(4, tcRows.value.length)
  tcGap.value = 10
  tcDuration.value = 45
  week1DateInput.value = week1Date.value
  tcVisible.value = true
}

/** 节数变化：增加则从末节按课间顺延补齐（每节时长按 tcDuration），减少则截断 */
function onTcSectionsChange(n) {
  const rows = tcRows.value.slice(0, n)
  while (rows.length < n) {
    const sec = rows.length + 1
    if (rows.length === 0) {
      rows.push({ section: 1, startTime: '08:00', endTime: toHHMM(8 * 60 + tcDuration.value) })
      continue
    }
    const prevEnd = toMin(rows[rows.length - 1].endTime) ?? 8 * 60
    const s = prevEnd + tcGap.value
    rows.push({ section: sec, startTime: toHHMM(s), endTime: toHHMM(s + tcDuration.value) })
  }
  tcRows.value = rows
}

/**
 * 编辑某节时间后的联动：
 * - 改「开始时间」：本节结束 = 开始 + 每节时长，后续节按课间顺延
 * - 改「结束时间」：后续节按课间顺延（时长跟随 tcDuration）
 */
function onTimeEdit(idx) {
  const rows = tcRows.value
  if (toMin(rows[idx].startTime) !== null && toMin(rows[idx].endTime) === null) {
    const s = toMin(rows[idx].startTime)
    if (s !== null) rows[idx].endTime = toHHMM(s + tcDuration.value)
  }
  for (let i = idx + 1; i < rows.length; i++) {
    const prevEnd = toMin(rows[i - 1].endTime)
    const s = (prevEnd ?? 0) + tcGap.value
    rows[i].startTime = toHHMM(s)
    rows[i].endTime = toHHMM(s + tcDuration.value)
  }
}

function recalcFrom(idx) {
  if (tcRows.value.length > idx + 1) onTimeEdit(idx)
}

async function aiSuggest() {
  tcSuggesting.value = true
  try {
    const res = await aiTimeSuggest(tcSections.value)
    const list = (res.data || []).map((x, i) => ({
      section: x.section ?? i + 1,
      startTime: x.startTime,
      endTime: x.endTime
    }))
    if (list.length) {
      tcRows.value = list
      ElMessage.success('AI 已生成作息建议，可继续微调')
    }
  } catch (e) {
    // 拦截器已提示
  } finally {
    tcSuggesting.value = false
  }
}

async function saveTc() {
  for (let i = 0; i < tcRows.value.length; i++) {
    const r = tcRows.value[i]
    if (!r.startTime || !r.endTime || toMin(r.startTime) === null || toMin(r.endTime) === null) {
      ElMessage.warning(`第 ${r.section} 节时间不完整`)
      return
    }
    if (toMin(r.startTime) >= toMin(r.endTime)) {
      ElMessage.warning(`第 ${r.section} 节开始时间需早于结束时间`)
      return
    }
    if (i > 0 && toMin(r.startTime) < toMin(tcRows.value[i - 1].endTime)) {
      ElMessage.warning(`第 ${r.section} 节与上一节时间重叠`)
      return
    }
  }
  tcSaving.value = true
  try {
    await saveTimeConfig(tcRows.value.map((r) => ({
      section: r.section,
      startTime: r.startTime,
      endTime: r.endTime
    })))
    ElMessage.success('作息已保存')
    tcVisible.value = false
    loadTimeConfig()
  } catch (e) {
    // 拦截器已提示
  } finally {
    tcSaving.value = false
  }
}

// ==================== AI 截图导入 ====================
const ocrVisible = ref(false)
const ocrLoading = ref(false)
const importing = ref(false)
const ocrItems = ref([])
const ocrSemester = ref('')
const ocrApplyLib = ref(false)

const semesterOptions = computed(() => {
  const set = new Set(semesters.value)
  set.add(defaultSemester())
  return [...set]
})

/** 开关批量入库：开启时全部勾选，可逐条取消 */
function onApplyLibSwitch(on) {
  ocrItems.value.forEach((row) => { row.applyLib = !!on })
}

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
    ocrItems.value = (res.data || []).map((x) => ({ ...x, applyLib: true }))
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
  let libOk = 0
  let libFail = 0
  const appliedNames = new Set() // 同名课程去重：一门课只申请一次
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
      // 勾选的条目同时申请录入课程库（同名课程只申请一次）
      const nameKey = String(item.courseName).trim()
      if (ocrApplyLib.value && item.applyLib && !appliedNames.has(nameKey)) {
        appliedNames.add(nameKey)
        try {
          await createCourse({
            name: nameKey,
            teacherName: item.teacher || null,
            room: item.room || null,
            className: null,
            semester: ocrSemester.value,
            applyNote: '课表截图批量导入'
          })
          libOk++
        } catch (e) {
          libFail++
        }
      }
    }
    let msg = `导入完成：成功 ${ok} 条，失败 ${fail} 条`
    if (ocrApplyLib.value) {
      msg += `；课程库申请 ${libOk} 条${libFail ? `，失败 ${libFail} 条` : ''}`
    }
    if (fail === 0 && libFail === 0) {
      ElMessage.success(msg)
    } else {
      ElMessage.warning(msg)
    }
    ocrVisible.value = false
    currentSemester.value = ocrSemester.value
    loadSchedules()
    loadCourseOptions()
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
.ocr-apply-lib {
  margin-bottom: 8px;
}

/* ===== 课程详情 / 申请录入 ===== */
.detail-name {
  font-size: 17px;
  font-weight: 700;
  color: #00a1d6;
  margin-bottom: 10px;
}
.detail-rows {
  display: flex;
  flex-direction: column;
  gap: 7px;
}
.detail-row {
  display: flex;
  font-size: 13px;
  line-height: 1.5;
}
.detail-row .dl {
  width: 58px;
  flex-shrink: 0;
  color: var(--text-sub);
}
.apply-tip {
  font-size: 12px;
  color: var(--text-sub);
  background: #f6fafd;
  border-radius: 6px;
  padding: 8px 10px;
}

/* 详情：同课多时段聚合 */
.detail-group {
  margin-top: 10px;
  padding: 8px 10px;
  background: #f6fafd;
  border-radius: 8px;
}
.detail-group-title {
  font-size: 11px;
  color: var(--text-sub);
  margin-bottom: 4px;
}
.detail-group-item {
  font-size: 12.5px;
  line-height: 1.7;
}

/* 作息设置 */
.tc-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  font-size: 13px;
}
.tc-gap-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-sub);
  margin-bottom: 10px;
}
.tc-tip {
  font-size: 11px;
}
.tc-list {
  max-height: 44vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.tc-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.tc-row :deep(.el-time-select) {
  flex: 1;
  min-width: 0;
}
.tc-no {
  width: 22px;
  font-size: 13px;
  font-weight: 600;
  text-align: center;
}
.tc-sep {
  color: var(--text-sub);
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
  grid-template-columns: 44px repeat(7, 1fr);
  gap: 3px;
  min-width: 680px;
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
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  font-size: 11px;
  color: var(--text-sub);
  padding-top: 4px;
  gap: 1px;
}

.sec-no {
  font-weight: 600;
  font-size: 11px;
  color: var(--text, #303133);
}

.sec-time {
  font-size: 8.5px;
  line-height: 1.2;
  color: var(--text-sub);
  white-space: nowrap;
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

/* ===== 周数 / 虚化 / 日历 ===== */
.week-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  min-height: 22px;
}
.week-badge {
  font-size: 11px;
  font-weight: 600;
  color: #0086b3;
  background: rgba(0, 161, 214, 0.1);
  border: 1px solid rgba(0, 161, 214, 0.25);
  border-radius: 999px;
  padding: 1px 10px;
}
.week-warn {
  font-size: 11px;
  color: #b8860b;
  cursor: pointer;
}
.week-dim-tip {
  font-size: 11px;
  color: var(--text-sub);
}
.week-badge .week-off {
  font-weight: 400;
  font-size: 10px;
}
.week-nav {
  margin-left: 4px;
}
.course-block.dimmed {
  opacity: 0.38;
  filter: grayscale(0.7);
}
.block-change {
  font-size: 9px;
  margin-top: 2px;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 3px;
  padding: 0 3px;
  display: inline-block;
}

/* 大学活动行 */
.activity-head .sec-no {
  font-size: 9px;
  color: #e06d2a;
  font-weight: 700;
}
.activity-cell {
  min-height: 34px;
  padding: 2px;
  overflow-y: auto;
  max-height: 60px;
  cursor: default;
}
.act-item {
  display: flex;
  align-items: flex-start;
  gap: 2px;
  font-size: 9px;
  line-height: 1.25;
  margin-bottom: 2px;
}
.act-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  flex-shrink: 0;
  margin-top: 2px;
}
.act-exam { background: #f56c6c; }
.act-contest { background: #e6a23c; }
.act-holiday { background: #67c23a; }
.act-activity { background: #909399; }
.act-name {
  color: #444;
  font-weight: 500;
}
.act-note {
  color: var(--text-sub);
  font-size: 8.5px;
}
.act-empty {
  color: #d0d4da;
  font-size: 9px;
}
.legend .dot-dim {
  background: #c8cdd4;
}
.calendar-card {
  margin-top: 12px;
  background: #fff;
  border-radius: var(--radius);
  box-shadow: var(--shadow-card);
  padding: 10px 12px;
}
.calendar-title {
  font-size: 12.5px;
  font-weight: 600;
  margin-bottom: 6px;
}
.calendar-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  padding: 4px 0;
  flex-wrap: wrap;
}
.cal-date {
  color: #0086b3;
  font-weight: 600;
  min-width: 84px;
}
.cal-name {
  font-weight: 500;
}
.cal-note {
  color: var(--text-sub);
  font-size: 10.5px;
}
.tc-week1 {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: var(--text-sub);
  background: #f6fafd;
  border-radius: 6px;
  padding: 6px 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
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
