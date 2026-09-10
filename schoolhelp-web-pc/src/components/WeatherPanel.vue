<template>
  <div class="weather-panel">
    <!-- 天气详情触发按钮（由父组件控制显示位置） -->
    <div v-if="showTrigger" class="weather-trigger" @click="open">
      <span class="w-emoji">{{ briefEmoji }}</span>
      <span class="w-trigger-text">天气详情</span>
      <span v-if="brief" class="w-brief">{{ brief }}</span>
    </div>

    <!-- 天气弹窗 -->
    <el-dialog
      v-model="visible"
      title=""
      :width="dialogWidth"
      :show-close="false"
      class="weather-dialog"
      append-to-body
      align-center
    >
      <div class="weather-body">
        <!-- 顶部区域 -->
        <div class="weather-top">
          <div class="wt-left">
            <div class="loc">
              <el-icon><Location /></el-icon>
              <span>哈尔滨学院 · {{ nowText }}</span>
            </div>
            <div class="temp-main">{{ temp }}<span class="deg">°C</span></div>
            <div class="cond-line">
              <span class="cond">{{ condText }}</span>
              <span class="feels">体感 {{ feels }}°C</span>
              <span class="wind" v-if="wind != null">🌬️ {{ wind }}km/h</span>
            </div>
          </div>
          <div class="wt-icon">
            <span class="big-weather-emoji">{{ weatherIcon }}</span>
          </div>
        </div>

        <!-- 穿衣推荐提示 -->
        <div class="dress-tip" :class="dressLevelClass">
          <div class="dt-head">
            <el-icon><Guide /></el-icon>
            <span>温馨提示 · 穿衣推荐</span>
          </div>
          <div class="dt-text">{{ dressAdvice }}</div>
        </div>

        <!-- 未来 3 天预报 -->
        <div class="daily-row">
          <div v-for="(d, i) in daily" :key="i" class="daily-item">
            <div class="d-day">{{ i === 0 ? '今天' : d.weekday }}</div>
            <div class="d-emoji">{{ d.emoji }}</div>
            <div class="d-cond">{{ d.cond }}</div>
            <div class="d-temp">
              <span class="d-max">{{ d.max }}°</span>
              <span class="d-min">{{ d.min }}°</span>
            </div>
          </div>
        </div>

        <!-- 免责 + 数据源 -->
        <div class="weather-foot">
          <el-checkbox v-model="mutedToday">今日不再提示</el-checkbox>
          <span class="src">天气数据来源：Open-Meteo</span>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" round @click="close">知道了</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Location, Guide } from '@element-plus/icons-vue'

// ===== 配置（哈尔滨学院 · 哈尔滨市南岗区中兴大道109号片区） =====
const LAT = 45.716
const LON = 126.59
const LOCATION_NAME = '哈尔滨学院'
const API_URL = 'https://api.open-meteo.com/v1/forecast'

const props = defineProps({
  // 是否显示触发按钮（移动端课表下方/PC首页日期右侧自行摆放插槽时传 false 用外部按钮）
  showTrigger: { type: Boolean, default: true },
  // 挂载后是否自动弹出（登录后首次进入课表/首页时传 true）
  auto: { type: Boolean, default: false }
})
const emit = defineEmits(['loaded'])

const visible = ref(false)
const loading = ref(false)
const error = ref('')
const mutedToday = ref(false)

const weather = ref(null) // 原始数据
const brief = ref('') // 触发按钮上的简略文案，如 "11° 晴"

// ===== WMO Weather Code 映射（用 emoji 表示天气图标，规避 element-plus 图标缺失） =====
const CODE_MAP = {
  0: { text: '晴', icon: '☀️' },
  1: { text: '大部晴朗', icon: '🌤️' },
  2: { text: '多云', icon: '⛅' },
  3: { text: '阴', icon: '☁️' },
  45: { text: '雾', icon: '🌫️' },
  48: { text: '雾凇', icon: '🌫️' },
  51: { text: '毛毛雨', icon: '🌦️' },
  53: { text: '毛毛雨', icon: '🌦️' },
  55: { text: '毛毛雨', icon: '🌦️' },
  56: { text: '冻雨', icon: '🌧️' },
  57: { text: '冻雨', icon: '🌧️' },
  61: { text: '小雨', icon: '🌧️' },
  63: { text: '中雨', icon: '🌧️' },
  65: { text: '大雨', icon: '🌧️' },
  66: { text: '冻雨', icon: '🌧️' },
  67: { text: '冻雨', icon: '🌧️' },
  71: { text: '小雪', icon: '🌨️' },
  73: { text: '中雪', icon: '🌨️' },
  75: { text: '大雪', icon: '❄️' },
  77: { text: '雪粒', icon: '❄️' },
  80: { text: '阵雨', icon: '🌦️' },
  81: { text: '阵雨', icon: '🌧️' },
  82: { text: '强阵雨', icon: '⛈️' },
  85: { text: '阵雪', icon: '🌨️' },
  86: { text: '强阵雪', icon: '🌨️' },
  95: { text: '雷阵雨', icon: '⛈️' },
  96: { text: '雷阵雨伴冰雹', icon: '⛈️' },
  99: { text: '雷暴伴强冰雹', icon: '⛈️' }
}

function condOf(code) {
  return CODE_MAP[code] || { text: '未知', icon: '☁️' }
}

// ===== 计算属性 =====
const weatherIcon = computed(() => (weather.value ? condOf(weather.value.weatherCode).icon : '☀️'))
const briefEmoji = computed(() => (weather.value ? condOf(weather.value.weatherCode).icon : '🌤️'))
const temp = computed(() => (weather.value ? Math.round(weather.value.temp) : '--'))
const feels = computed(() => (weather.value ? Math.round(weather.value.feels) : '--'))
const wind = computed(() => (weather.value ? Math.round(weather.value.wind) : null))
const condText = computed(() => (weather.value ? condOf(weather.value.weatherCode).text : '加载中…'))

const nowText = computed(() => {
  const n = new Date()
  return `${n.getMonth() + 1}月${n.getDate()}日 ${n.getHours()}:${String(n.getMinutes()).padStart(2, '0')}`
})

// 未来 3 天
const daily = computed(() => {
  if (!weather.value) return []
  return weather.value.daily.map((d, i) => ({
    weekday: i === 0 ? '今天' : ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][new Date(d.date).getDay()],
    cond: d.cond,
    emoji: d.emoji,
    max: Math.round(d.max),
    min: Math.round(d.min),
    code: d.code
  }))
})

// ===== 穿衣推荐规则（基于体感温度 + 天气码 + 风速） =====
const dressAdvice = computed(() => {
  if (!weather.value) return ''
  const t = weather.value.feels // 体感温度
  const code = weather.value.weatherCode
  const windKph = weather.value.wind
  const isRain = [51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99].includes(code)
  const isSnow = [71, 73, 75, 77, 85, 86].includes(code)
  const windy = windKph >= 25

  let base = ''
  if (t >= 28) base = '天气炎热，穿短袖、短裤等清凉夏装，注意防晒补水 ☀️'
  else if (t >= 24) base = '体感舒适偏热，穿短袖或薄长袖即可 🧢'
  else if (t >= 20) base = '体感舒适，单穿长袖衬衫或薄T恤就很合适 👕'
  else if (t >= 16) base = '天气微凉，建议加一件薄外套或卫衣 🧥'
  else if (t >= 12) base = '体感偏凉，建议穿风衣、夹克等外套 🧥'
  else if (t >= 8) base = '天气较冷，建议穿毛衣+外套，注意保暖 🧣'
  else if (t >= 4) base = '天气寒冷，建议穿棉衣或厚呢大衣，戴围巾手套 🧣🧤'
  else if (t >= -5) base = '天气严寒，建议穿羽绒服，戴帽子围巾手套全副武装 ❄️'
  else base = '极寒天气，务必穿厚羽绒服+保暖内衣，尽量减少户外停留 🥶'

  if (isRain) base += ' 有雨，记得带伞 ☔'
  if (isSnow) base += ' 有雪，路面湿滑，注意防滑 ❄️'
  if (windy) base += ' 风较大，注意防风'
  return base
})
const dressLevelClass = computed(() => {
  if (!weather.value) return ''
  const t = weather.value.feels
  if (t >= 24) return 'lvl-hot'
  if (t >= 12) return 'lvl-mild'
  if (t >= 4) return 'lvl-cool'
  return 'lvl-cold'
})

// 弹窗宽度：窄屏 92%，宽屏自适应
const dialogWidth = computed(() => (window.innerWidth < 768 ? '94%' : '460px'))

// ===== 取数 =====
async function fetchWeather() {
  if (weather.value) return
  loading.value = true
  error.value = ''
  try {
    const url = `${API_URL}?latitude=${LAT}&longitude=${LON}` +
      `&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m` +
      `&daily=weather_code,temperature_2m_max,temperature_2m_min` +
      `&timezone=Asia%2FShanghai&forecast_days=3`
    const res = await fetch(url)
    if (!res.ok) throw new Error('HTTP ' + res.status)
    const data = await res.json()
    const cur = data.current || {}
    const cond = condOf(cur.weather_code)
    weather.value = {
      temp: cur.temperature_2m,
      feels: cur.apparent_temperature,
      wind: cur.wind_speed_10m,
      weatherCode: cur.weather_code,
      cond: cond.text,
      daily: (data.daily ? data.daily.time : []).map((date, i) => {
        const dc = condOf(data.daily.weather_code[i])
        return {
          date,
          code: data.daily.weather_code[i],
          cond: dc.text,
          emoji: dc.icon,
          max: data.daily.temperature_2m_max[i],
          min: data.daily.temperature_2m_min[i]
        }
      })
    }
    brief.value = `${Math.round(cur.temperature_2m)}° ${cond.text}`
    emit('loaded', weather.value)
  } catch (e) {
    error.value = '天气加载失败，请稍后重试'
    brief.value = ''
  } finally {
    loading.value = false
  }
}

// ===== 弹窗触发 =====
function canAutoShow() {
  // 会话内已弹过 → 不再弹
  if (sessionStorage.getItem('sh_weather_shown')) return false
  // 今日勾选过"不再提示" → 不弹
  const key = 'sh_weather_muted_' + new Date().toISOString().slice(0, 10)
  return !localStorage.getItem(key)
}
async function open() {
  await fetchWeather()
  visible.value = true
  // 记录会话内已弹（点按钮主动打开也记录，避免后续自动弹重复打扰）
  sessionStorage.setItem('sh_weather_shown', '1')
}
function close() {
  if (mutedToday.value) {
    const key = 'sh_weather_muted_' + new Date().toISOString().slice(0, 10)
    localStorage.setItem(key, '1')
  }
  visible.value = false
}

onMounted(async () => {
  if (props.auto && canAutoShow()) {
    await fetchWeather()
    if (!error.value) {
      visible.value = true
      sessionStorage.setItem('sh_weather_shown', '1')
    }
  }
})

defineExpose({ open })
</script>

<style scoped>
.weather-panel {
  display: inline-flex;
}
.weather-trigger {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 14px;
  border-radius: 20px;
  cursor: pointer;
  font-size: 13px;
  color: #3a7afe;
  background: linear-gradient(135deg, #eef4ff, #e6f7ff);
  border: 1px solid #d6e9ff;
  transition: all 0.2s;
  user-select: none;
}
.weather-trigger:hover {
  background: linear-gradient(135deg, #dfebff, #d0f0ff);
  transform: translateY(-1px);
}
.w-icon {
  font-size: 16px;
}
.w-emoji {
  font-size: 16px;
  line-height: 1;
}
.w-brief {
  color: #6b8fd4;
  font-size: 12px;
  margin-left: 2px;
}

/* 弹窗内容 */
.weather-body {
  padding: 2px 2px 0;
}
.weather-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.loc {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--text-sub, #666);
}
.temp-main {
  font-size: 52px;
  font-weight: 300;
  line-height: 1.15;
  margin: 2px 0 0;
  color: #222;
}
.temp-main .deg {
  font-size: 24px;
  font-weight: 400;
  vertical-align: super;
  color: #888;
}
.cond-line {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  color: #444;
  margin-top: 2px;
}
.cond-line .cond {
  font-size: 16px;
  font-weight: 600;
}
.cond-line .wind {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 12px;
  color: #888;
}
.big-weather-icon {
  font-size: 64px;
  color: #4a9eff;
}
.big-weather-emoji {
  font-size: 60px;
  line-height: 1.2;
  filter: drop-shadow(0 2px 6px rgba(0, 0, 0, 0.08));
}

/* 穿衣推荐 */
.dress-tip {
  margin: 14px 0 0;
  padding: 12px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
}
.dt-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 700;
  margin-bottom: 4px;
  font-size: 14px;
}
.lvl-hot {
  background: #fff7e6;
  border: 1px solid #ffe7ba;
  color: #b25e00;
}
.lvl-mild {
  background: #f0f9eb;
  border: 1px solid #d3f0c6;
  color: #4a8a2e;
}
.lvl-cool {
  background: #eef4ff;
  border: 1px solid #d6e6ff;
  color: #3a6ea5;
}
.lvl-cold {
  background: #e8f4ff;
  border: 1px solid #c4e0ff;
  color: #1d6bb0;
}

/* 3 天预报 */
.daily-row {
  display: flex;
  justify-content: space-between;
  margin-top: 14px;
  padding: 12px 0 4px;
  border-top: 1px dashed #e8e8e8;
}
.daily-item {
  flex: 1;
  text-align: center;
}
.daily-item + .daily-item {
  border-left: 1px solid #f0f0f0;
}
.d-day {
  font-size: 13px;
  font-weight: 600;
  color: #333;
}
.d-icon {
  font-size: 22px;
  color: #4a9eff;
  margin: 6px 0 4px;
}
.d-emoji {
  font-size: 22px;
  margin: 6px 0 4px;
  line-height: 1;
}
.d-cond {
  font-size: 12px;
  color: #666;
}
.d-temp {
  margin-top: 4px;
  font-size: 13px;
}
.d-max {
  font-weight: 700;
  color: #e05c3a;
  margin-right: 6px;
}
.d-min {
  color: #4a9eff;
}

/* 底部 */
.weather-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
  font-size: 12px;
}
.src {
  color: #aaa;
  font-size: 11px;
}
</style>
