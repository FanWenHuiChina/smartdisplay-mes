<template>
  <section>
    <div class="page-head">
      <div>
        <h1 class="page-title">追溯分析 / 多入口全链路证据</h1>
        <p class="page-desc">按 Lot、SN、工单、设备、物料批次或缺陷代码定位影响范围，并展开首个 Lot 的生产证据链。</p>
      </div>
      <div class="page-actions">
        <button class="mes-btn" :disabled="loading || !traceInfo.length" @click="reverseTrace">反向追溯</button>
        <button class="mes-btn" :disabled="!traceInfo.length" @click="exportEvidenceChain">导出证据链</button>
        <button class="mes-btn primary" @click="loadTrace">刷新追溯</button>
      </div>
    </div>

    <div class="mes-grid trace-grid">
      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">追溯对象</div>
          <span class="status-tag blue">{{ resolvedTypeLabel }}</span>
        </div>
        <div class="mes-card__body detail-list">
          <div class="mes-field">
            <label>查询类型</label>
            <select v-model="traceType" class="mes-select">
              <option v-for="option in traceTypeOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </div>
          <div class="mes-field">
            <label>关键字</label>
            <input
              v-model="traceQuery"
              class="mes-input"
              placeholder="输入 Lot / SN / 工单 / 设备 / 物料批次 / 缺陷代码"
              @keyup.enter="loadTrace"
            />
          </div>
          <button class="mes-btn primary" :disabled="loading" @click="loadTrace">
            {{ loading ? '查询中' : '查询追溯' }}
          </button>
          <div v-if="errorMessage" class="detail-row">
            <b>提示</b>
            <span class="status-tag red">{{ errorMessage }}</span>
          </div>
          <div v-for="item in traceInfo" :key="item.label" class="detail-row">
            <b>{{ item.label }}</b>
            <span v-if="item.tag" class="status-tag" :class="item.tagType">{{ item.value }}</span>
            <span v-else>{{ item.value }}</span>
          </div>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">工艺履历时间线</div>
          <span class="status-tag teal">正向追溯</span>
        </div>
        <div class="mes-card__body timeline">
          <div v-for="event in timeline" :key="event.title + event.time" class="timeline-event">
            <div class="event-dot" :class="event.type"></div>
            <div class="event-card">
              <div class="event-main"><span>{{ event.title }}</span><span>{{ event.time }}</span></div>
              <div class="event-meta">{{ event.meta }}</div>
            </div>
          </div>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">影响范围与证据</div>
          <span class="status-tag purple">{{ matches.length }} Lot</span>
        </div>
        <div class="mes-card__body cards">
          <div v-for="card in summaryCards" :key="card.title" class="mini-card">
            <div class="mini-top">
              <span>{{ card.title }}</span>
              <span class="status-tag" :class="card.type">{{ card.value }}</span>
            </div>
            <div class="mini-meta">{{ card.meta }}</div>
          </div>
          <div v-for="match in matches" :key="match.lotNo" class="mini-card">
            <div class="mini-top">
              <span>{{ match.lotNo }}</span>
              <span class="status-tag" :class="statusType(match.status)">{{ match.status || '-' }}</span>
            </div>
            <div class="mini-meta">
              {{ match.orderNo || '-' }} / {{ match.currentStepCode || '-' }} / {{ match.matchField }}={{ match.evidence }}
            </div>
          </div>
          <div v-for="evidence in evidences" :key="evidence.title" class="mini-card">
            <div class="mini-top">
              <span>{{ evidence.title }}</span>
              <span class="status-tag" :class="evidence.type">{{ evidence.status }}</span>
            </div>
            <div class="mini-meta">{{ evidence.meta }}</div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { searchTrace } from '@/api/pilot'
import { warnDevFallback } from '@/utils/devFallback'

const traceTypeOptions = [
  { label: '自动识别', value: 'AUTO' },
  { label: 'Lot', value: 'LOT' },
  { label: 'SN', value: 'SN' },
  { label: '工单', value: 'ORDER' },
  { label: '设备', value: 'EQUIPMENT' },
  { label: '物料批次', value: 'MATERIAL_BATCH' },
  { label: '缺陷代码', value: 'DEFECT_CODE' }
]

const typeLabels = Object.fromEntries(traceTypeOptions.map(option => [option.value, option.label]))

const traceType = ref('AUTO')
const resolvedType = ref('AUTO')
const traceQuery = ref(__DEV_MOCK_FALLBACK__ ? 'LOT202406001' : '')
const loading = ref(false)
const errorMessage = ref('')

const fallbackTraceInfo = [
  { label: '查询类型', value: 'Lot' },
  { label: '产品', value: 'AMOLED_65 柔性屏' },
  { label: '工单', value: 'MO20260606012' },
  { label: '路线', value: 'RTE_G6_V08' },
  { label: '首选 Lot', value: 'LOT202406001' },
  { label: '状态', value: 'HOLD', tag: true, tagType: 'red' }
]

const fallbackTimeline = [
  { title: '工单释放并生成 Lot', time: '08:10', meta: 'MO20260606012 / route=RTE_G6_V08 / creator=planner01', type: 'green' },
  { title: 'COATING Track In', time: '09:18', meta: 'EQ=COATER_02 / Recipe=RCP_COAT_65_V12 / 物料 PI-ADH-240606-A', type: 'amber' },
  { title: 'Track Out NG 并自动 Hold', time: '10:06', meta: '膜厚 1.72um，规格 1.8-2.2um；质量待复判', type: 'red' },
  { title: 'MRB 处置中', time: '当前', meta: '质量复判 -> 工艺确认 -> Release / Rework / Scrap', type: 'purple' }
]

const fallbackEvidences = [
  { title: 'Recipe 快照', status: '已锁定', type: 'blue', meta: 'RCP_COAT_65_V12 / 温度 150.2C / 压力 0.82kPa / 速度 302mm/s' },
  { title: '设备事件', status: '相关', type: 'amber', meta: 'COATER_02 在 09:40 出现压力波动告警。' },
  { title: '物料批次', status: '待排查', type: 'amber', meta: 'PI-ADH-240606-A 同批影响 6 Lot。' }
]

const traceInfo = ref(__DEV_MOCK_FALLBACK__ ? fallbackTraceInfo : [])
const timeline = ref(__DEV_MOCK_FALLBACK__ ? fallbackTimeline : [])
const evidences = ref(__DEV_MOCK_FALLBACK__ ? fallbackEvidences : [])
const matches = ref([])
const summaryCards = ref([])
const lastTraceEnvelope = ref(null)

const resolvedTypeLabel = computed(() => typeLabels[resolvedType.value] || resolvedType.value || '追溯')

function statusType(status) {
  const map = {
    READY: 'blue',
    PROCESSING: 'green',
    HOLD: 'red',
    COMPLETED: 'teal',
    REWORK: 'amber',
    SCRAP: 'red'
  }
  return map[status] || 'gray'
}

function formatTime(value) {
  if (!value) return '当前'
  return String(value).slice(11, 16) || String(value)
}

function listText(values, fallback = '-') {
  return Array.isArray(values) && values.length ? values.join(' / ') : fallback
}

function applyTraceEnvelope(envelope) {
  lastTraceEnvelope.value = envelope
  const query = envelope.query || {}
  const trace = envelope.trace || envelope
  const lot = trace.lot || {}
  const order = trace.order || {}
  const route = trace.route || {}
  const sn = trace.sn || {}
  const impact = envelope.impactSummary || {}
  const dimensions = envelope.relatedDimensions || {}
  const materials = trace.materialConsumptions || []
  const material = materials[0] || {}
  const serialNumbers = trace.serialNumbers || []
  const serialNumberSummary = trace.serialNumberSummary || {}

  resolvedType.value = query.resolvedType || traceType.value
  matches.value = envelope.matches || []

  traceInfo.value = [
    { label: '查询类型', value: resolvedTypeLabel.value },
    { label: '首选 Lot', value: query.selectedLotNo || lot.lotNo || '-' },
    ...(sn.sn ? [{ label: 'SN', value: sn.sn }] : []),
    ...(serialNumberSummary.totalCount !== undefined ? [{ label: 'SN 数量', value: String(serialNumberSummary.totalCount) }] : []),
    ...(serialNumberSummary.firstSn ? [{ label: '首个 SN', value: serialNumberSummary.firstSn }] : []),
    { label: '产品', value: lot.productCode || order.productCode || '未知' },
    { label: '工单', value: lot.orderNo || order.orderNo || '未知' },
    { label: '路线', value: route.routeCode || '未绑定' },
    { label: '物料', value: material.batchNo || '暂无消耗' },
    { label: '设备', value: lot.currentEquipmentCode || '待分配' },
    { label: '状态', value: lot.status || 'UNKNOWN', tag: true, tagType: statusType(lot.status) }
  ]

  const stepRecords = trace.stepRecords || []
  const holdRecords = trace.holdRecords || []
  timeline.value = [
    {
      title: '工单释放并生成 Lot',
      time: formatTime(order.createdTime || lot.createdTime),
      meta: `${lot.orderNo || order.orderNo || '-'} / route=${route.routeCode || '未绑定'} / creator=${order.createdBy || lot.createdBy || 'system'}`,
      type: 'green'
    },
    ...stepRecords.map(record => ({
      title: `${record.stepCode} Track In / Out`,
      time: `${formatTime(record.trackInTime)}-${formatTime(record.trackOutTime)}`,
      meta: `EQ=${record.equipmentCode || '-'} / Recipe=${record.recipeCode || '-'} / result=${record.result || '加工中'} / operator=${record.operator || '-'}`,
      type: record.result === 'NG' ? 'red' : 'green'
    })),
    ...holdRecords.map(record => ({
      title: `${record.holdType || 'QUALITY'} Hold / Release`,
      time: `${formatTime(record.holdTime)}-${formatTime(record.releaseTime)}`,
      meta: `${record.holdReason || '-'} / ${record.disposition || '待处置'} / by=${record.holdBy || '-'}`,
      type: record.status === 'HOLD' ? 'red' : 'purple'
    }))
  ]

  const qualityRecords = trace.qualityRecords || []
  summaryCards.value = [
    { title: 'SN 数量', value: String(impact.serialNumberCount ?? serialNumberSummary.totalCount ?? serialNumbers.length), type: 'teal', meta: serialNumberSummary.limited ? `显示前 ${serialNumberSummary.returnedCount || serialNumbers.length} 个，首个 ${serialNumberSummary.firstSn || '-'}` : `首个 ${serialNumberSummary.firstSn || '-'}` },
    { title: '命中 Lot', value: String(impact.matchedLotCount ?? matches.value.length), type: 'blue', meta: `当前查询命中 ${matches.value.length} 个受影响 Lot` },
    { title: 'Hold Lot', value: String(impact.holdLotCount ?? 0), type: (impact.holdLotCount || 0) > 0 ? 'red' : 'green', meta: '用于判断是否需要 MRB 或复判介入' },
    { title: 'NG 检验', value: String(impact.ngInspectionCount ?? 0), type: (impact.ngInspectionCount || 0) > 0 ? 'amber' : 'green', meta: `缺陷代码：${listText(dimensions.defectCodes)}` },
    { title: '关联设备', value: String(impact.equipmentCount ?? 0), type: 'purple', meta: listText(dimensions.equipmentCodes) }
  ]

  evidences.value = [
    { title: 'SN 绑定', status: String(serialNumberSummary.totalCount ?? serialNumbers.length), type: serialNumbers.length ? 'teal' : 'gray', meta: serialNumbers.slice(0, 5).map(item => item.sn).join(' / ') || '暂无 SN 绑定记录' },
    { title: 'Route 快照', status: route.status || 'ACTIVE', type: 'blue', meta: `${route.routeCode || '未绑定'} / steps=${listText(route.steps, '-')}` },
    { title: '物料批次', status: String(impact.materialBatchCount ?? materials.length), type: materials.length ? 'green' : 'gray', meta: materials.map(item => `${item.materialName || item.materialCode}:${item.batchNo}`).join(' / ') || '暂无消耗记录' },
    { title: '质检记录', status: `${qualityRecords.length} 条`, type: qualityRecords.some(item => item.result !== 'OK') ? 'amber' : 'green', meta: qualityRecords.map(item => `${item.itemCode}:${item.result}`).join(' / ') || '暂无质检记录' },
    { title: '审计日志', status: `${(trace.auditLogs || []).length} 条`, type: 'purple', meta: (trace.auditLogs || []).map(item => `${item.action}:${item.result}`).join(' / ') || '暂无审计记录' }
  ]
}

async function reverseTrace() {
  const envelope = lastTraceEnvelope.value || {}
  const trace = envelope.trace || envelope
  const dimensions = envelope.relatedDimensions || {}
  const lot = trace.lot || {}
  const material = (trace.materialConsumptions || [])[0] || {}
  const candidates = [
    { type: 'EQUIPMENT', keyword: firstValue(dimensions.equipmentCodes) || lot.currentEquipmentCode },
    { type: 'MATERIAL_BATCH', keyword: firstValue(dimensions.materialBatchNos) || material.batchNo },
    { type: 'DEFECT_CODE', keyword: firstValue(dimensions.defectCodes) },
    { type: 'LOT', keyword: firstValue((envelope.matches || []).map(item => item.lotNo)) || lot.lotNo }
  ]
  const next = candidates.find(item => item.keyword && item.keyword !== traceQuery.value)
  if (!next) {
    ElMessage.warning('当前证据链没有可反向展开的设备、物料或缺陷维度')
    return
  }
  traceType.value = next.type
  traceQuery.value = next.keyword
  await loadTrace()
  ElMessage.success(`已切换到${typeLabels[next.type] || next.type}反向追溯`)
}

function exportEvidenceChain() {
  const rows = [
    ['类型', '名称', '值', '说明'],
    ...traceInfo.value.map(item => ['追溯对象', item.label, item.value, item.tag ? item.tagType : '']),
    ...summaryCards.value.map(item => ['影响范围', item.title, item.value, item.meta]),
    ...matches.value.map(item => ['命中Lot', item.lotNo, item.status || '-', `${item.matchField || '-'}=${item.evidence || '-'}`]),
    ...timeline.value.map(item => ['时间线', item.title, item.time, item.meta]),
    ...evidences.value.map(item => ['证据', item.title, item.status, item.meta])
  ]
  downloadCsv(`trace-evidence-${Date.now()}.csv`, rows)
  ElMessage.success('证据链已导出')
}

function firstValue(values) {
  return Array.isArray(values) && values.length ? values[0] : ''
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

async function loadTrace() {
  const keyword = String(traceQuery.value || '').trim()
  if (!keyword) {
    traceInfo.value = []
    timeline.value = []
    evidences.value = []
    matches.value = []
    summaryCards.value = []
    lastTraceEnvelope.value = null
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const data = await searchTrace({ type: traceType.value, keyword })
    applyTraceEnvelope(data)
  } catch (error) {
    warnDevFallback('追溯接口不可用', error)
    errorMessage.value = error.response?.data?.message || error.message || '追溯查询失败'
    if (__DEV_MOCK_FALLBACK__) {
      traceInfo.value = fallbackTraceInfo
      timeline.value = fallbackTimeline
      evidences.value = fallbackEvidences
    }
  } finally {
    loading.value = false
  }
}

onMounted(loadTrace)
</script>
