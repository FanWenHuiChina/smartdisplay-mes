<template>
  <section>
    <div class="page-head">
      <div>
        <h1 class="page-title">生产总览 / 产线总控</h1>
        <p class="page-desc">按产线、工段、工序实时查看 WIP、良率、设备稼动、Hold 与瓶颈状态。</p>
      </div>
      <div class="page-actions">
        <button class="mes-btn" :disabled="loadingOverview" @click="refreshLineOverview">
          {{ loadingOverview ? '刷新中' : '刷新产线' }}
        </button>
        <button class="mes-btn" @click="exportShiftReport">导出班报</button>
        <button class="mes-btn primary" @click="goToQualityExceptions">查看异常</button>
      </div>
    </div>

    <div class="mes-grid cols-4">
      <div v-for="metric in metrics" :key="metric.label" class="mes-card metric-card">
        <div class="metric-label">
          <span>{{ metric.label }}</span>
          <span class="status-tag" :class="metric.tagType">{{ metric.tag }}</span>
        </div>
        <div class="metric-value">{{ metric.value }}</div>
        <div class="metric-meta">
          <span>{{ metric.left }}</span>
          <span>{{ metric.right }}</span>
        </div>
      </div>
    </div>

    <div class="mes-grid cols-2-wide section-gap">
      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">设备 OEE 拆解 <small>近 {{ equipmentOee.windowHours }} 小时</small></div>
          <span class="status-tag" :class="equipmentOee.oeeRate >= 95 ? 'green' : 'amber'">{{ equipmentOee.oeeText }}</span>
        </div>
        <div class="mes-card__body">
          <div class="oee-strip">
            <div v-for="factor in oeeFactors" :key="factor.label" class="oee-factor">
              <div class="oee-factor__top">
                <span>{{ factor.label }}</span>
                <span class="status-tag" :class="factor.type">{{ factor.tag }}</span>
              </div>
              <strong>{{ factor.value }}</strong>
              <span>{{ factor.meta }}</span>
            </div>
          </div>
          <div class="oee-note">{{ equipmentOee.calculationNote }}</div>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">停机原因 TopN</div>
          <span class="status-tag red">非计划 {{ equipmentOee.unplannedDowntimeMinutes }} 分</span>
        </div>
        <div class="mes-card__body">
          <table class="mes-table">
            <thead>
              <tr><th>原因</th><th>类型</th><th>次数</th><th>分钟</th></tr>
            </thead>
            <tbody>
              <tr v-for="item in downtimeReasons" :key="item.reasonCode" :class="{ danger: item.type === 'red' }">
                <td>{{ item.reasonName }}</td>
                <td><span class="status-tag" :class="item.type">{{ item.downtimeType }}</span></td>
                <td>{{ item.eventCount }}</td>
                <td>{{ item.durationMinutes }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="mes-card section-gap">
      <div class="mes-card__head">
        <div class="mes-card__title">AMOLED 工艺段 WIP 状态 <small>Array / Cell / Module</small></div>
        <span class="status-tag teal">实时刷新</span>
      </div>
      <div class="mes-card__body">
        <div class="route-line">
          <div
            v-for="step in routeSteps"
            :key="step.code"
            class="route-step"
            :class="{ current: step.current, warn: step.warn }"
          >
            <strong>{{ step.code }} {{ step.name }}</strong>
            <span>{{ step.meta }}</span>
            <span class="status-tag" :class="step.tagType">{{ step.status }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="mes-grid cols-3 section-gap">
      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">实时异常队列</div>
          <span class="status-tag red">{{ alertCount }} 待处理</span>
        </div>
        <div class="mes-card__body cards">
          <div v-for="item in alerts" :key="item.title" class="mini-card">
            <div class="mini-top">
              <span>{{ item.title }}</span>
              <span class="status-tag" :class="item.type">{{ item.level }}</span>
            </div>
            <div class="mini-meta">{{ item.meta }}</div>
          </div>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">近 7 日良率趋势</div>
          <span class="status-tag green">目标 97%</span>
        </div>
        <div class="mes-card__body">
          <div class="chart-box">
            <div class="trend">
              <span v-for="point in trendPoints" :key="point.left" class="point" :style="{ left: point.left, bottom: point.bottom }"></span>
              <span v-for="line in trendLines" :key="line.left" class="line" :style="{ left: line.left, bottom: line.bottom, width: line.width, transform: line.transform }"></span>
            </div>
            <div class="axis">
              <span v-for="day in days" :key="day">{{ day }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">瓶颈建议</div>
          <span class="status-tag purple">AI 摘要</span>
        </div>
        <div class="mes-card__body cards">
          <div v-for="item in aiSuggestions" :key="item.title" class="ai-box">
            <h3>{{ item.title }}</h3>
            <p>{{ item.text }}</p>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getOverview } from '@/api/pilot'
import { warnDevFallback } from '@/utils/devFallback'

const fallbackMetrics = [
  { label: '今日投入 Lot', tag: 'WIP', tagType: 'blue', value: '128', left: '等待 18', right: 'Hot Lot 6' },
  { label: '综合良率', tag: '接近目标', tagType: 'green', value: '96.82%', left: '目标 97.00%', right: '-0.18%' },
  { label: 'Hold 待处置', tag: '质量介入', tagType: 'red', value: '7', left: '超 SLA 2', right: '新增 3' },
  { label: '设备稼动率', tag: '需关注', tagType: 'amber', value: '91.4%', left: 'Alarm 3', right: 'PM 1' }
]

const fallbackRouteSteps = [
  { code: 'CLEAN', name: '清洗', meta: 'WIP 14 / 等待 3', status: '正常', tagType: 'green' },
  { code: 'COATING', name: '涂胶', meta: 'WIP 26 / 排队 11', status: '瓶颈', tagType: 'amber', current: true },
  { code: 'EXPOSURE', name: '曝光', meta: 'WIP 17 / 设备 3', status: '正常', tagType: 'green' },
  { code: 'ETCH', name: '蚀刻', meta: 'WIP 12 / NG 1', status: '正常', tagType: 'green' },
  { code: 'EVAP', name: '蒸镀', meta: 'WIP 9 / 异常 2', status: 'Alarm', tagType: 'red', warn: true },
  { code: 'ENCAP', name: '封装', meta: 'WIP 18 / 等待 5', status: '正常', tagType: 'green' },
  { code: 'AOI', name: '检测', meta: 'WIP 19 / FPY 97.1%', status: '检测中', tagType: 'teal' },
  { code: 'BOND', name: '绑定', meta: 'WIP 22 / 等待 6', status: '正常', tagType: 'green' }
]

const fallbackAlerts = [
  { title: 'LOT260606-017 涂胶膜厚超限', level: 'P1', type: 'red', meta: 'COATER_02 / RCP_COAT_65_V12 / Hold 38 分钟 / 质量工程师待复判' },
  { title: 'EVAP_01 真空波动', level: 'P2', type: 'amber', meta: '影响 3 批 Lot / 最近 2 小时出现 4 次 / 设备工程师处理中' },
  { title: 'BOND_03 绑定偏移预警', level: 'P2', type: 'amber', meta: 'MODULE_BOND_03 缺陷上升 / 已触发 SPC 规则 2' }
]

const fallbackAiSuggestions = [
  { title: '主要瓶颈：涂胶 / 蒸镀', text: '涂胶等待队列高于过去 7 日均值 28%，蒸镀设备异常与 Mura 不良上升存在时间相关性。' },
  { title: '建议动作', text: '优先释放 COATER_01 高优先级 Lot；EVAP_01 下一批进站前执行真空稳定性点检。' }
]

const fallbackEquipmentOee = {
  windowHours: 24,
  oeeRate: 94.99,
  oeeText: '94.99%',
  availabilityText: '99.43%',
  performanceText: '98.75%',
  qualityText: '96.82%',
  plannedDowntimeMinutes: 35,
  unplannedDowntimeMinutes: 66,
  reasonTopN: [
    { reasonCode: 'VACUUM_PUMP_DOWN', reasonName: '真空泵停机', downtimeType: 'UNPLANNED', durationMinutes: 38, eventCount: 1, type: 'red' },
    { reasonCode: 'CHAMBER_PRESSURE', reasonName: '腔体压力报警', downtimeType: 'UNPLANNED', durationMinutes: 28, eventCount: 1, type: 'red' },
    { reasonCode: 'PM_NOZZLE_CLEAN', reasonName: '喷嘴清洁', downtimeType: 'PLANNED', durationMinutes: 35, eventCount: 1, type: 'amber' }
  ],
  calculationNote: '试点口径：可用率来自近24小时设备停机事件，性能率按当前可执行设备状态估算。'
}

const emptyEquipmentOee = {
  windowHours: 0,
  oeeRate: 0,
  oeeText: '-',
  availabilityText: '-',
  performanceText: '-',
  qualityText: '-',
  plannedDowntimeMinutes: 0,
  unplannedDowntimeMinutes: 0,
  reasonTopN: [],
  calculationNote: '等待接口数据'
}

const metrics = ref(__DEV_MOCK_FALLBACK__ ? fallbackMetrics : [])
const routeSteps = ref(__DEV_MOCK_FALLBACK__ ? fallbackRouteSteps : [])
const alerts = ref(__DEV_MOCK_FALLBACK__ ? fallbackAlerts : [])
const aiSuggestions = ref(__DEV_MOCK_FALLBACK__ ? fallbackAiSuggestions : [])
const equipmentOee = ref(__DEV_MOCK_FALLBACK__ ? fallbackEquipmentOee : emptyEquipmentOee)
const loadingOverview = ref(false)
const router = useRouter()

const days = ref(__DEV_MOCK_FALLBACK__ ? ['05/31', '06/01', '06/02', '06/03', '06/04', '06/05', '06/06'] : [])
const trendPoints = ref(__DEV_MOCK_FALLBACK__ ? [
  { left: '0%', bottom: '58%' },
  { left: '16%', bottom: '64%' },
  { left: '32%', bottom: '47%' },
  { left: '48%', bottom: '72%' },
  { left: '64%', bottom: '62%' },
  { left: '80%', bottom: '78%' },
  { left: '96%', bottom: '69%' }
] : [])
const trendLines = ref(__DEV_MOCK_FALLBACK__ ? [
  { left: '0%', bottom: '61%', width: '17%', transform: 'rotate(-8deg)' },
  { left: '16%', bottom: '66%', width: '17%', transform: 'rotate(15deg)' },
  { left: '32%', bottom: '50%', width: '18%', transform: 'rotate(-22deg)' },
  { left: '48%', bottom: '75%', width: '17%', transform: 'rotate(10deg)' },
  { left: '64%', bottom: '65%', width: '17%', transform: 'rotate(-13deg)' },
  { left: '80%', bottom: '80%', width: '17%', transform: 'rotate(9deg)' }
] : [])

const alertCount = computed(() => alerts.value.length)
const oeeFactors = computed(() => [
  {
    label: '可用率',
    value: equipmentOee.value.availabilityText || '-',
    tag: 'Availability',
    type: 'blue',
    meta: `计划停机 ${equipmentOee.value.plannedDowntimeMinutes ?? 0} 分`
  },
  {
    label: '性能率',
    value: equipmentOee.value.performanceText || '-',
    tag: 'Performance',
    type: 'teal',
    meta: equipmentOee.value.performanceSampleCount
      ? `EAP 节拍样本 ${equipmentOee.value.performanceSampleCount} 条`
      : '缺少节拍样本，按设备状态估算'
  },
  {
    label: '质量率',
    value: equipmentOee.value.qualityText || '-',
    tag: 'Quality',
    type: 'green',
    meta: '来自过程参数/质检结果'
  }
])
const downtimeReasons = computed(() => {
  const rows = Array.isArray(equipmentOee.value.reasonTopN) ? equipmentOee.value.reasonTopN : []
  return rows.length ? rows : (__DEV_MOCK_FALLBACK__ ? fallbackEquipmentOee.reasonTopN : [])
})

const statusMap = {
  NORMAL: { label: '正常', type: 'green' },
  BOTTLENECK: { label: '瓶颈', type: 'amber' },
  ALARM: { label: 'Alarm', type: 'red' }
}

function mapMetric(metric) {
  return {
    label: metric.label,
    tag: metric.tag,
    tagType: metric.type || metric.tagType || 'blue',
    value: metric.value,
    left: metric.left,
    right: metric.right
  }
}

function mapRouteStep(step) {
  const status = statusMap[step.status] || { label: step.status || '正常', type: 'green' }
  return {
    code: step.code,
    name: step.name,
    meta: `WIP ${step.wip ?? 0} / ${step.segment || '工段'}`,
    status: status.label,
    tagType: status.type,
    current: status.type === 'amber',
    warn: status.type === 'red'
  }
}

function mapAlert(alert) {
  const type = alert.level === 'P1' ? 'red' : 'amber'
  return {
    title: alert.title,
    level: alert.level,
    type,
    meta: `${alert.type || '异常'} / ${alert.status || 'OPEN'}`
  }
}

function normalizeEquipmentOee(value) {
  if (!value || typeof value !== 'object') return __DEV_MOCK_FALLBACK__ ? fallbackEquipmentOee : emptyEquipmentOee
  const baseOee = __DEV_MOCK_FALLBACK__ ? fallbackEquipmentOee : emptyEquipmentOee
  return {
    ...baseOee,
    ...value,
    oeeText: value.oeeText || `${value.oeeRate ?? baseOee.oeeRate}%`,
    availabilityText: value.availabilityText || `${value.availabilityRate ?? '-'}%`,
    performanceText: value.performanceText || `${value.performanceRate ?? '-'}%`,
    qualityText: value.qualityText || `${value.qualityRate ?? '-'}%`,
    reasonTopN: Array.isArray(value.reasonTopN) ? value.reasonTopN : (__DEV_MOCK_FALLBACK__ ? fallbackEquipmentOee.reasonTopN : [])
  }
}

function applyTrend(trend = []) {
  if (!trend.length) return
  days.value = trend.map(item => item.date.slice(5).replace('-', '/'))
  const values = trend.map(item => Number(item.yield || 0))
  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = Math.max(0.1, max - min)
  trendPoints.value = values.map((value, index) => ({
    left: `${index * 16}%`,
    bottom: `${42 + ((value - min) / range) * 38}%`
  }))
  trendLines.value = trendPoints.value.slice(0, -1).map((point, index) => ({
    left: point.left,
    bottom: point.bottom,
    width: '17%',
    transform: `rotate(${values[index + 1] >= values[index] ? '-' : ''}${8 + Math.abs(values[index + 1] - values[index]) * 6}deg)`
  }))
}

async function loadOverview() {
  loadingOverview.value = true
  try {
    const data = await getOverview()
    metrics.value = Array.isArray(data.metrics) ? data.metrics.map(mapMetric) : (__DEV_MOCK_FALLBACK__ ? fallbackMetrics : [])
    routeSteps.value = Array.isArray(data.routeSteps) ? data.routeSteps.map(mapRouteStep) : (__DEV_MOCK_FALLBACK__ ? fallbackRouteSteps : [])
    alerts.value = Array.isArray(data.alerts) ? data.alerts.map(mapAlert) : (__DEV_MOCK_FALLBACK__ ? fallbackAlerts : [])
    aiSuggestions.value = Array.isArray(data.aiSuggestions) ? data.aiSuggestions : (__DEV_MOCK_FALLBACK__ ? fallbackAiSuggestions : [])
    equipmentOee.value = normalizeEquipmentOee(data.equipmentOee)
    applyTrend(data.yieldTrend)
  } catch (error) {
    warnDevFallback('总览接口不可用', error)
  } finally {
    loadingOverview.value = false
  }
}

async function refreshLineOverview() {
  await loadOverview()
  ElMessage.success('产线总览已刷新')
}

function exportShiftReport() {
  const rows = [
    ['模块', '指标', '值', '说明1', '说明2'],
    ...metrics.value.map(item => ['KPI', item.label, item.value, item.left, item.right]),
    ...routeSteps.value.map(item => ['工序WIP', `${item.code} ${item.name}`, item.status, item.meta, item.tagType]),
    ...alerts.value.map(item => ['异常', item.title, item.level, item.meta, item.type]),
    ...downtimeReasons.value.map(item => ['停机', item.reasonName, item.durationMinutes, item.downtimeType, item.eventCount])
  ]
  downloadCsv(`shift-report-${Date.now()}.csv`, rows)
  ElMessage.success('班报已导出')
}

function downloadCsv(filename, rows) {
  const csv = rows.map(row => row.map(cell => `"${String(cell ?? '').replace(/"/g, '""')}"`).join(',')).join('\n')
  const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

function goToQualityExceptions() {
  router.push('/quality')
}

onMounted(loadOverview)
</script>

<style scoped>
.oee-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.oee-factor {
  border: 1px solid var(--mes-line);
  border-radius: 7px;
  background: #fff;
  padding: 10px;
  display: grid;
  gap: 7px;
  min-height: 96px;
}

.oee-factor__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--mes-sub);
  font-size: 12px;
}

.oee-factor strong {
  font-size: 24px;
  line-height: 1;
}

.oee-factor > span {
  color: var(--mes-sub);
  font-size: 12px;
}

.oee-note {
  margin-top: 10px;
  color: var(--mes-sub);
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 1180px) {
  .oee-strip {
    grid-template-columns: 1fr;
  }
}
</style>
