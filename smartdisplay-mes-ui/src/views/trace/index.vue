<template>
  <section>
    <div class="page-head">
      <div>
        <h1 class="page-title">追溯分析 / Lot 与 SN 全链路证据</h1>
        <p class="page-desc">正向追溯看一个 Lot 怎么生产，反向追溯看异常影响了哪些 Lot/SN。</p>
      </div>
      <div class="page-actions">
        <button class="mes-btn">反向追溯</button>
        <button class="mes-btn">导出证据链</button>
        <button class="mes-btn primary" @click="loadTrace">生成报告</button>
      </div>
    </div>

    <div class="mes-grid trace-grid">
      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">追溯对象</div>
          <span class="status-tag blue">Lot</span>
        </div>
        <div class="mes-card__body detail-list">
          <div class="mes-field">
            <label>Lot / SN / 工单 / 物料批次</label>
            <input v-model="traceQuery" class="mes-input" />
          </div>
          <button class="mes-btn primary" @click="loadTrace">查询追溯</button>
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
          <div v-for="event in timeline" :key="event.title" class="timeline-event">
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
          <div class="mes-card__title">关联证据</div>
          <span class="status-tag purple">根因排查</span>
        </div>
        <div class="mes-card__body cards">
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
import { onMounted, ref } from 'vue'
import { getTraceLot } from '@/api/pilot'
import { warnDevFallback } from '@/utils/devFallback'

const traceQuery = ref(__DEV_MOCK_FALLBACK__ ? 'LOT202406001' : '')

const fallbackTraceInfo = [
  { label: '产品', value: 'AMOLED_65 柔性屏' },
  { label: '工单', value: 'MO20260606012' },
  { label: '路线', value: 'RTE_G6_V08' },
  { label: '物料', value: 'PI-ADH-240606-A' },
  { label: '设备', value: 'COATER_02' },
  { label: '状态', value: 'HOLD', tag: true, tagType: 'red' }
]

const fallbackTimeline = [
  { title: '工单释放并生成 Lot', time: '08:10', meta: 'MO20260606012 / route=RTE_G6_V08 / creator=planner01', type: 'green' },
  { title: 'CLEAN Track In / Out', time: '08:35-09:02', meta: 'EQ=CLEAN_01 / result=OK / operator=op1007', type: 'green' },
  { title: 'COATING Track In', time: '09:18', meta: 'EQ=COATER_02 / Recipe=RCP_COAT_65_V12 / 物料 PI-ADH-240606-A', type: 'amber' },
  { title: 'Track Out NG 并自动 Hold', time: '10:06', meta: '膜厚 1.72 μm，规格 1.8-2.2 μm；缺陷 CELL_MURA_02；质量待复判', type: 'red' },
  { title: 'MRB 处置中', time: '当前', meta: '质量工程师复判 → 工艺工程师确认 → Release / Rework / Scrap', type: 'purple' }
]

const fallbackEvidences = [
  { title: 'Recipe 快照', status: '已锁定', type: 'blue', meta: 'RCP_COAT_65_V12 / 温度 150.2℃ / 压力 0.82kPa / 速度 302mm/s' },
  { title: '设备事件', status: '相关', type: 'amber', meta: 'COATER_02 在 09:40 出现压力波动告警，持续 4 分 12 秒。' },
  { title: '物料批次', status: '待排除', type: 'amber', meta: 'PI-ADH-240606-A 同批影响 6 Lot，其中 2 Lot 出现膜厚偏低。' },
  { title: '影响范围', status: '8 SN', type: 'red', meta: '反向追溯发现 8 个 Panel 需要隔离复检。' }
]

const traceInfo = ref(__DEV_MOCK_FALLBACK__ ? fallbackTraceInfo : [])
const timeline = ref(__DEV_MOCK_FALLBACK__ ? fallbackTimeline : [])
const evidences = ref(__DEV_MOCK_FALLBACK__ ? fallbackEvidences : [])

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

function applyTrace(data) {
  const lot = data.lot || {}
  const order = data.order || {}
  const route = data.route || {}
  const materials = data.materialConsumptions || []
  const material = materials[0] || {}
  traceInfo.value = [
    { label: '产品', value: lot.productCode || order.productCode || '未知' },
    { label: '工单', value: lot.orderNo || order.orderNo || '未知' },
    { label: '路线', value: route.routeCode || 'RTE_G6_V08' },
    { label: '物料', value: material.batchNo || '待绑定' },
    { label: '设备', value: lot.currentEquipmentCode || '待分配' },
    { label: '状态', value: lot.status || 'UNKNOWN', tag: true, tagType: statusType(lot.status) }
  ]

  const stepRecords = data.stepRecords || []
  const holdRecords = data.holdRecords || []
  timeline.value = [
    { title: '工单释放并生成 Lot', time: formatTime(order.createdTime || lot.createdTime), meta: `${lot.orderNo || order.orderNo || '-'} / route=${route.routeCode || 'RTE_G6_V08'} / creator=${order.createdBy || lot.createdBy || 'system'}`, type: 'green' },
    ...stepRecords.map(record => ({
      title: `${record.stepCode} Track In / Out`,
      time: `${formatTime(record.trackInTime)}-${formatTime(record.trackOutTime)}`,
      meta: `EQ=${record.equipmentCode} / Recipe=${record.recipeCode || '-'} / result=${record.result || '加工中'} / operator=${record.operator || '-'}`,
      type: record.result === 'NG' ? 'red' : 'green'
    })),
    ...holdRecords.map(record => ({
      title: `${record.holdType || 'QUALITY'} Hold / Release`,
      time: `${formatTime(record.holdTime)}-${formatTime(record.releaseTime)}`,
      meta: `${record.holdReason || '-'} / ${record.disposition || '待处置'} / by=${record.holdBy || '-'}`,
      type: record.status === 'HOLD' ? 'red' : 'purple'
    }))
  ]

  const qualityRecords = data.qualityRecords || []
  evidences.value = [
    { title: 'Route 快照', status: route.status || 'ACTIVE', type: 'blue', meta: `${route.routeCode || 'RTE_G6_V08'} / steps=${(route.steps || []).join(' → ')}` },
    { title: '物料批次', status: material.status || 'OK', type: material.status === 'WARNING' ? 'amber' : 'green', meta: materials.map(item => `${item.materialName}:${item.batchNo}`).join(' / ') || '暂无消耗记录' },
    { title: '质检记录', status: `${qualityRecords.length} 条`, type: qualityRecords.some(item => item.result !== 'OK') ? 'amber' : 'green', meta: qualityRecords.map(item => `${item.itemCode}:${item.result}`).join(' / ') || '暂无质检记录' },
    { title: '审计日志', status: `${(data.auditLogs || []).length} 条`, type: 'purple', meta: (data.auditLogs || []).map(item => `${item.action}:${item.result}`).join(' / ') || '暂无审计记录' }
  ]
}

async function loadTrace() {
  const lotNo = String(traceQuery.value || '').trim()
  if (!lotNo) {
    traceInfo.value = []
    timeline.value = []
    evidences.value = []
    return
  }
  try {
    const data = await getTraceLot(lotNo)
    applyTrace(data)
  } catch (error) {
    warnDevFallback('追溯接口不可用', error)
    if (__DEV_MOCK_FALLBACK__) {
      traceInfo.value = fallbackTraceInfo
      timeline.value = fallbackTimeline
      evidences.value = fallbackEvidences
    }
  }
}

onMounted(loadTrace)
</script>
