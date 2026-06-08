<template>
  <section>
    <div class="page-head">
      <div>
        <h1 class="page-title">质量管理 / SPC、缺陷与 MRB 处置</h1>
        <p class="page-desc">质量模块从检测结果驱动异常隔离、Hold、复判、返工和报废处置。</p>
      </div>
      <div class="page-actions">
        <button v-if="canEscalateAction" class="mes-btn" :disabled="actionLoading === 'mrb-sla'" @click="handleRefreshSla">刷新 SLA</button>
        <button class="mes-btn" @click="loadQualityData">刷新</button>
        <button v-if="canMrbAction" class="mes-btn">MRB 会议</button>
        <button v-if="canMrbAction" class="mes-btn primary">创建复判单</button>
      </div>
    </div>

    <div class="mes-grid cols-4">
      <div v-for="metric in metrics" :key="metric.label" class="mes-card metric-card">
        <div class="metric-label">
          <span>{{ metric.label }}</span>
          <span class="status-tag" :class="metric.type">{{ metric.tag }}</span>
        </div>
        <div class="metric-value">{{ metric.value }}</div>
        <div class="metric-meta"><span>{{ metric.left }}</span><span>{{ metric.right }}</span></div>
      </div>
    </div>

    <div class="mes-card section-gap">
      <div class="mes-card__head">
        <div class="mes-card__title">QMS 模拟检验上报</div>
        <span class="status-tag" :class="qmsResultType">{{ qmsResultText }}</span>
      </div>
      <div class="mes-card__body">
        <div class="qms-form">
          <div class="mes-field">
            <label>Lot</label>
            <input v-model="qmsForm.lotNo" class="mes-input" placeholder="请输入 Lot" />
          </div>
          <div class="mes-field">
            <label>检验结果</label>
            <select v-model="qmsForm.result" class="mes-select">
              <option value="OK">OK</option>
              <option value="NG">NG</option>
            </select>
          </div>
          <div class="mes-field">
            <label>检验项</label>
            <input v-model="qmsForm.itemCode" class="mes-input" />
          </div>
          <div class="mes-field">
            <label>检验名称</label>
            <input v-model="qmsForm.itemName" class="mes-input" />
          </div>
          <div class="mes-field">
            <label>测量值</label>
            <input v-model="qmsForm.measuredValue" class="mes-input" inputmode="decimal" />
          </div>
          <div class="mes-field">
            <label>下限</label>
            <input v-model="qmsForm.lowerLimit" class="mes-input" inputmode="decimal" />
          </div>
          <div class="mes-field">
            <label>上限</label>
            <input v-model="qmsForm.upperLimit" class="mes-input" inputmode="decimal" />
          </div>
          <div class="mes-field">
            <label>单位</label>
            <input v-model="qmsForm.unit" class="mes-input" />
          </div>
          <div class="mes-field">
            <label>工序</label>
            <input v-model="qmsForm.stepCode" class="mes-input" />
          </div>
          <div class="mes-field">
            <label>设备</label>
            <input v-model="qmsForm.equipmentCode" class="mes-input" />
          </div>
          <div class="mes-field">
            <label>缺陷代码</label>
            <input v-model="qmsForm.defectCode" class="mes-input" :disabled="qmsForm.result === 'OK'" />
          </div>
          <div class="mes-field">
            <label>操作人</label>
            <input v-model="qmsForm.operator" class="mes-input" />
          </div>
          <div class="mes-field qms-remark-field">
            <label>备注</label>
            <input v-model="qmsForm.remark" class="mes-input" />
          </div>
        </div>
        <div class="qms-footer">
          <div class="qms-summary">
            <span class="status-tag" :class="qmsForm.result === 'NG' ? 'red' : 'green'">{{ qmsForm.result }}</span>
            <span>{{ qmsSubmitHint }}</span>
          </div>
          <button
            v-if="canMrbAction"
            class="mes-btn primary"
            :disabled="qmsSubmitting"
            @click="submitQmsInspection"
          >
            {{ qmsSubmitting ? '上报中' : '提交 QMS 上报' }}
          </button>
        </div>
      </div>
    </div>

    <div class="mes-grid cols-2 section-gap">
      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">SPC 控制图 / 膜厚</div>
          <span class="status-tag" :class="spcStatus.type">{{ spcStatus.text }}</span>
        </div>
        <div class="mes-card__body">
          <div class="chart-box">
            <div class="trend">
              <span v-for="point in spcPoints" :key="point.left" class="point" :style="{ left: point.left, bottom: point.bottom }"></span>
              <span v-for="line in spcLines" :key="line.left" class="line" :style="{ left: line.left, bottom: line.bottom, width: line.width, transform: line.transform }"></span>
            </div>
            <div class="axis"><span v-for="label in axisLabels" :key="label">{{ label }}</span></div>
          </div>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">MRB 待处置</div>
          <span class="status-tag red">{{ openExceptionCount }} 单</span>
        </div>
        <div class="mes-card__body cards">
          <div
            v-for="item in mrbItems"
            :key="item.eventNo"
            class="mini-card"
            :class="{ selected: selectedEventNo === item.eventNo }"
            @click="selectMrbItem(item)"
          >
            <div class="mini-top">
              <span>{{ item.title }}</span>
              <span class="status-tag" :class="item.type">{{ item.status }}</span>
            </div>
            <div class="mini-meta">{{ item.meta }}</div>
            <div class="mini-stock">
              <span>MRB {{ item.recordCount }}</span>
              <span>纪要 {{ item.minutesCount }}</span>
              <span>附件 {{ item.attachmentCount }}</span>
            </div>
            <div v-if="item.conclusion" class="mini-conclusion">{{ item.conclusion }}</div>
            <div v-if="item.status !== 'CLOSED' && (canReviewAction || canCloseAction)" class="mini-actions">
              <button v-if="canReviewAction" class="mini-action" :disabled="actionLoading === item.eventNo" @click.stop="handleReview(item, 'RELEASE')">放行</button>
              <button v-if="canReviewAction" class="mini-action" :disabled="actionLoading === item.eventNo" @click.stop="handleReview(item, 'REWORK')">返工</button>
              <button v-if="canReviewAction" class="mini-action danger" :disabled="actionLoading === item.eventNo" @click.stop="handleReview(item, 'SCRAP')">报废</button>
              <button v-if="canCloseAction" class="mini-action" :disabled="actionLoading === item.eventNo" @click.stop="handleClose(item)">关闭</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="mes-card section-gap">
      <div class="mes-card__head">
        <div class="mes-card__title">MRB 处置履历 / 附件</div>
        <span class="status-tag blue">{{ selectedEventNo || '未选择' }}</span>
      </div>
      <div class="mes-card__body">
        <div class="mrb-form">
          <div class="mes-field">
            <label>会议号</label>
            <input v-model="mrbForm.meetingNo" class="mes-input" />
          </div>
          <div class="mes-field">
            <label>参与人</label>
            <input v-model="mrbForm.participants" class="mes-input" />
          </div>
          <div class="mes-field">
            <label>风险等级</label>
            <select v-model="mrbForm.riskLevel" class="mes-select">
              <option value="P1">P1</option>
              <option value="P2">P2</option>
              <option value="P3">P3</option>
            </select>
          </div>
          <div class="mes-field mrb-minutes-field">
            <label>MRB会议纪要</label>
            <textarea v-model="mrbForm.meetingMinutes" class="mes-input" rows="3"></textarea>
          </div>
          <div class="mes-field">
            <label>附件名</label>
            <input v-model="mrbForm.fileName" class="mes-input" />
          </div>
          <div class="mes-field">
            <label>附件地址</label>
            <input v-model="mrbForm.fileUrl" class="mes-input" />
          </div>
          <div class="mes-field">
            <label>校验摘要</label>
            <input v-model="mrbForm.fileHash" class="mes-input" />
          </div>
        </div>
        <table class="mes-table mrb-table">
          <thead><tr><th>类型</th><th>处置</th><th>审批</th><th>复判人</th><th>意见</th><th>附件</th><th>时间</th></tr></thead>
          <tbody>
            <tr v-for="record in mrbRecordRows" :key="record.key">
              <td>{{ record.reviewType }}</td>
              <td>{{ record.dispositionAction }}</td>
              <td><span class="status-tag" :class="record.type">{{ record.approvalStatus }}</span></td>
              <td>{{ record.reviewer }}</td>
              <td>{{ record.opinion }}</td>
              <td>{{ record.attachmentCount }}</td>
              <td>{{ record.time }}</td>
            </tr>
          </tbody>
        </table>
        <div class="mrb-subtitle">会签待办</div>
        <table class="mes-table mrb-table">
          <thead><tr><th>角色</th><th>状态</th><th>审批人</th><th>意见</th><th>SLA</th><th>升级</th><th>到期</th><th>动作</th></tr></thead>
          <tbody>
            <tr v-for="task in mrbApprovalRows" :key="task.taskNo">
              <td>{{ task.approvalRole }}</td>
              <td><span class="status-tag" :class="task.type">{{ task.approvalStatus }}</span></td>
              <td>{{ task.approver }}</td>
              <td>{{ task.opinion }}</td>
              <td>
                <span class="status-tag" :class="task.slaType">{{ task.slaText }}</span>
              </td>
              <td>{{ task.escalationText }}</td>
              <td>{{ task.dueTime }}</td>
              <td>
                <button
                  v-if="canApproveAction && task.actionable"
                  class="mini-action"
                  :disabled="actionLoading === task.taskNo"
                  @click="handleApproval(task, 'APPROVE')"
                >通过</button>
                <button
                  v-if="canApproveAction && task.actionable"
                  class="mini-action danger"
                  :disabled="actionLoading === task.taskNo"
                  @click="handleApproval(task, 'REJECT')"
                >驳回</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="mes-grid cols-2 section-gap">
      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">缺陷 TopN</div>
          <span class="status-tag red">按数量</span>
        </div>
        <div class="mes-card__body">
          <table class="mes-table">
            <thead><tr><th>缺陷代码</th><th>名称</th><th>等级</th><th>数量</th><th>处置</th></tr></thead>
            <tbody>
              <tr v-for="defect in defects" :key="defect.code">
                <td>{{ defect.code }}</td>
                <td>{{ defect.name }}</td>
                <td>{{ defect.level }}</td>
                <td>{{ defect.qty }}</td>
                <td><span class="status-tag" :class="defect.type">{{ defect.action }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">最近检验记录</div>
          <span class="status-tag blue">{{ inspections.length }} 条</span>
        </div>
        <div class="mes-card__body">
          <table class="mes-table">
            <thead><tr><th>Lot</th><th>工序</th><th>检测项</th><th>测量值</th><th>结果</th></tr></thead>
            <tbody>
              <tr v-for="record in inspectionRows" :key="record.key" :class="{ danger: record.result === 'NG' }">
                <td>{{ record.lotNo }}</td>
                <td>{{ record.stepCode }}</td>
                <td>{{ record.itemName }}</td>
                <td>{{ record.valueText }}</td>
                <td><span class="status-tag" :class="record.type">{{ record.result }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  approveQualityMrbTask,
  closeQualityException,
  getQualityExceptions,
  getQualityInspections,
  getQualityMrbApprovals,
  getQualityMrbRecords,
  getYieldDashboard,
  ingestQmsInspection,
  rejectQualityMrbTask,
  refreshQualityMrbApprovalSla,
  reviewQualityException
} from '@/api/pilot'
import { hasButton } from '@/utils/permissions'
import { warnDevFallback } from '@/utils/devFallback'

const fallbackInspections = [
  { inspectionNo: 'QI-FALLBACK-001', lotNo: 'LOT202406006', stepCode: 'COATING', itemCode: 'THICKNESS', itemName: '涂胶厚度', measuredValue: 2.26, upperLimit: 2.2, lowerLimit: 1.8, unit: 'μm', result: 'NG', defectCode: 'D-THICKNESS' },
  { inspectionNo: 'QI-FALLBACK-002', lotNo: 'LOT202406004', stepCode: 'EVAPORATION', itemCode: 'VACUUM', itemName: '真空度', measuredValue: 0.00062, upperLimit: 0.0005, lowerLimit: 0.00001, unit: 'Pa', result: 'NG', defectCode: 'D-VACUUM' },
  { inspectionNo: 'QI-FALLBACK-003', lotNo: 'LOT202406001', stepCode: 'COATING', itemCode: 'THICKNESS', itemName: '涂胶厚度', measuredValue: 2.04, upperLimit: 2.2, lowerLimit: 1.8, unit: 'μm', result: 'OK' }
]

const fallbackExceptions = [
  { eventNo: 'EX-FALLBACK-001', title: '涂胶膜厚超限', eventType: 'QUALITY', eventLevel: 'P1', lotNo: 'LOT202406006', stepCode: 'COATING', equipmentCode: 'COATER_02', status: 'OPEN', ownerRole: 'QE' },
  { eventNo: 'EX-FALLBACK-002', title: '蒸镀真空度波动', eventType: 'EQUIPMENT', eventLevel: 'P2', lotNo: 'LOT202406004', stepCode: 'EVAPORATION', equipmentCode: 'EVAP_01', status: 'PROCESSING', ownerRole: 'EE' }
]

const fallbackDefects = [
  { defectCode: 'D-THICKNESS', defectName: '涂胶厚度超限', level: 'MAJOR', qty: 2 },
  { defectCode: 'D-VACUUM', defectName: '真空度超限', level: 'MAJOR', qty: 1 },
  { defectCode: 'D-MURA', defectName: 'Mura', level: 'MAJOR', qty: 1 }
]

const fallbackMrbRecords = [
  { mrbNo: 'MRB-FALLBACK-001', eventNo: 'EX-FALLBACK-001', reviewType: 'REVIEW', dispositionAction: 'CONTINUE_HOLD', opinion: '等待补充膜厚复测记录。', meetingNo: 'MRB-DEMO-001', participants: 'qe,pe,ee', riskLevel: 'P1', approvalStatus: 'APPROVED', reviewer: 'qe', attachmentCount: 1, reviewTime: new Date().toISOString() }
]

const fallbackMrbApprovals = [
  { taskNo: 'MRBT-FALLBACK-QE', mrbNo: 'MRB-FALLBACK-001', eventNo: 'EX-FALLBACK-001', approvalRole: 'QE', approvalStatus: 'APPROVED', approver: 'qe', opinion: '质量复测通过', dueTime: new Date().toISOString(), slaLevel: 'CRITICAL', slaHours: 2, escalationCount: 0 },
  { taskNo: 'MRBT-FALLBACK-PE', mrbNo: 'MRB-FALLBACK-001', eventNo: 'EX-FALLBACK-001', approvalRole: 'PE', approvalStatus: 'ESCALATED', approver: '-', opinion: '-', dueTime: new Date(Date.now() - 60 * 60 * 1000).toISOString(), slaLevel: 'CRITICAL', slaHours: 3, escalatedTo: 'pm1001', escalationCount: 1 }
]

const inspections = ref(__DEV_MOCK_FALLBACK__ ? fallbackInspections : [])
const exceptions = ref(__DEV_MOCK_FALLBACK__ ? fallbackExceptions : [])
const defectTopN = ref(__DEV_MOCK_FALLBACK__ ? fallbackDefects : [])
const actionLoading = ref('')
const selectedEventNo = ref(__DEV_MOCK_FALLBACK__ ? fallbackExceptions[0]?.eventNo || '' : '')
const mrbRecords = ref(__DEV_MOCK_FALLBACK__ ? fallbackMrbRecords : [])
const mrbApprovals = ref(__DEV_MOCK_FALLBACK__ ? fallbackMrbApprovals : [])
const qmsSubmitting = ref(false)
const qmsResult = ref(null)

const mrbForm = reactive({
  meetingNo: 'MRB-DEMO-001',
  participants: 'qe,pe,ee',
  riskLevel: 'P1',
  meetingMinutes: 'MRB确认异常影响范围、处置动作、责任人与后续验证要求。',
  fileName: 'MRB复判记录.pdf',
  fileUrl: 'qms://mrb/demo/review.pdf',
  fileHash: 'sha256:mrb-demo'
})

const qmsForm = reactive({
  lotNo: __DEV_MOCK_FALLBACK__ ? fallbackInspections[0]?.lotNo || '' : '',
  result: 'OK',
  itemCode: 'VISUAL_CHECK',
  itemName: '外观检查',
  measuredValue: '',
  lowerLimit: '',
  upperLimit: '',
  unit: '',
  stepCode: '',
  equipmentCode: '',
  defectCode: 'D-QMS-NG',
  operator: localStorage.getItem('username') || 'qe1001',
  remark: 'QMS模拟检验上报'
})

const openExceptionCount = computed(() => exceptions.value.filter(item => item.status !== 'CLOSED').length)
const ngCount = computed(() => inspections.value.filter(item => item.result === 'NG').length)
const okCount = computed(() => inspections.value.filter(item => item.result === 'OK').length)
const yieldRate = computed(() => {
  const total = inspections.value.length || 1
  return `${((okCount.value / total) * 100).toFixed(1)}%`
})

const metrics = computed(() => [
  { label: '检验记录', tag: '今日', type: 'blue', value: String(inspections.value.length), left: `OK ${okCount.value}`, right: `NG ${ngCount.value}` },
  { label: '质量良率', tag: '试点', type: ngCount.value > 0 ? 'amber' : 'green', value: yieldRate.value, left: '按检验项', right: '实时判定' },
  { label: '异常事件', tag: 'MRB', type: openExceptionCount.value > 0 ? 'red' : 'green', value: String(openExceptionCount.value), left: 'OPEN/处理中', right: '自动 Hold' },
  { label: '缺陷类型', tag: 'TopN', type: 'purple', value: String(defectTopN.value.length), left: '按数量', right: '质量看板' }
])

const mrbItems = computed(() => exceptions.value.slice(0, 5).map(item => ({
  eventNo: item.eventNo,
  title: `${item.lotNo || '-'} ${item.title || item.eventType}`,
  status: item.status || 'OPEN',
  type: item.status === 'CLOSED' ? 'green' : item.eventLevel === 'P1' ? 'red' : 'amber',
  meta: `${item.stepCode || '-'} / ${item.equipmentCode || '-'} / ${item.ownerRole || 'QE'} / ${item.mrbOpinion || item.description || '等待处置'}`,
  mrbResult: item.mrbResult,
  dispositionAction: item.dispositionAction,
  conclusion: item.closeConclusion,
  rootCause: item.rootCause,
  recordCount: item.mrbRecordCount || 0,
  attachmentCount: item.mrbAttachmentCount || 0,
  minutesCount: item.mrbMinutesCount || 0
})))

const canReviewAction = computed(() => hasButton('quality:mrb-review'))
const canCloseAction = computed(() => hasButton('quality:exception-close'))
const canMrbAction = computed(() => canReviewAction.value || canCloseAction.value)
const canApproveAction = computed(() => hasButton('quality:mrb-approve'))
const canEscalateAction = computed(() => hasButton('quality:mrb-escalate'))
const qmsResultType = computed(() => {
  if (!qmsResult.value) return 'blue'
  if (qmsResult.value.holdApplied || qmsResult.value.result === 'NG') return 'red'
  return 'green'
})
const qmsResultText = computed(() => {
  if (!qmsResult.value) return '待上报'
  return `${qmsResult.value.result || '-'} / ${qmsResult.value.inspectionCount || 0}项`
})
const qmsSubmitHint = computed(() => {
  if (!qmsForm.lotNo) return '选择 Lot 后可模拟外部 QMS 推送'
  if (qmsForm.result === 'NG') return 'NG 将自动生成异常并 Hold Lot'
  return 'OK 上报只写检验记录和审计'
})

const defects = computed(() => defectTopN.value.map(item => ({
  code: item.defectCode,
  name: item.defectName,
  level: item.level || item.defectLevel || 'MAJOR',
  qty: item.qty,
  action: 'MRB',
  type: Number(item.qty || 0) > 1 ? 'red' : 'amber'
})))

const inspectionRows = computed(() => inspections.value.slice(0, 8).map((item, index) => ({
  key: item.inspectionNo || `${item.lotNo}-${item.itemCode}-${index}`,
  lotNo: item.lotNo,
  stepCode: item.stepCode,
  itemName: item.itemName || item.itemCode,
  valueText: formatValue(item),
  result: item.result,
  type: item.result === 'NG' ? 'red' : item.result === 'WARNING' ? 'amber' : 'green'
})))

const mrbRecordRows = computed(() => mrbRecords.value.map((item, index) => ({
  key: item.mrbNo || `${item.eventNo}-${item.reviewTime}-${index}`,
  reviewType: item.reviewType || '-',
  dispositionAction: item.dispositionAction || '-',
  approvalStatus: item.approvalStatus || 'APPROVED',
  reviewer: item.reviewer || 'system',
  opinion: item.opinion || '-',
  attachmentCount: item.attachmentCount || 0,
  time: formatTime(item.reviewTime),
  type: item.type || statusType(item.approvalStatus)
})))

const mrbApprovalRows = computed(() => mrbApprovals.value.map((item, index) => ({
  taskNo: item.taskNo || `${item.mrbNo}-${item.approvalRole}-${index}`,
  approvalRole: item.approvalRole || '-',
  approvalStatus: item.approvalStatus || 'PENDING',
  approver: item.approver || '-',
  opinion: item.opinion || '-',
  slaText: slaText(item),
  slaType: slaType(item),
  escalationText: escalationText(item),
  dueTime: formatTime(item.dueTime),
  actionable: ['PENDING', 'ESCALATED'].includes(item.approvalStatus),
  type: item.type || statusType(item.approvalStatus)
})))

const thicknessValues = computed(() => inspections.value
  .filter(item => String(item.itemCode || '').toUpperCase().includes('THICKNESS'))
  .map(item => Number(item.measuredValue))
  .filter(value => Number.isFinite(value))
  .slice(-8))

const spcStatus = computed(() => ngCount.value > 0
  ? { text: `触发 ${ngCount.value}`, type: 'red' }
  : { text: '受控', type: 'green' })

const axisLabels = computed(() => {
  const count = Math.max(4, thicknessValues.value.length || 4)
  return Array.from({ length: count }, (_, index) => `${index + 1}`)
})

const spcPoints = computed(() => {
  const values = thicknessValues.value.length ? thicknessValues.value : [2.0, 2.04, 2.12, 2.26]
  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = Math.max(0.01, max - min)
  return values.map((value, index) => ({
    left: `${(index / Math.max(1, values.length - 1)) * 96}%`,
    bottom: `${38 + ((value - min) / range) * 42}%`
  }))
})

const spcLines = computed(() => spcPoints.value.slice(0, -1).map((point, index) => ({
  left: point.left,
  bottom: point.bottom,
  width: `${96 / Math.max(1, spcPoints.value.length - 1)}%`,
  transform: `rotate(${index % 2 === 0 ? '-' : ''}${8 + index * 2}deg)`
})))

function formatValue(item) {
  if (item.measuredValue === null || item.measuredValue === undefined) return '-'
  return `${item.measuredValue}${item.unit || ''}`
}

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toTimeString().slice(0, 5)
}

function statusType(status) {
  if (['APPROVED', 'OK', 'PASS', 'CLOSED'].includes(status)) return 'green'
  if (['PENDING', 'PROCESSING', 'CONTINUE_HOLD', 'REWORK'].includes(status)) return 'amber'
  if (['ESCALATED', 'REJECTED', 'SCRAP', 'NG'].includes(status)) return 'red'
  return 'gray'
}

function slaText(item) {
  const level = item.slaLevel || 'STANDARD'
  const hours = item.slaHours || '-'
  if (item.approvalStatus === 'ESCALATED') return `${level} / 已升级`
  if (item.slaOverdue) return `${level} / 逾期`
  if (item.slaRemainingMinutes !== null && item.slaRemainingMinutes !== undefined) return `${level} / ${item.slaRemainingMinutes}m`
  return `${level} / ${hours}h`
}

function slaType(item) {
  if (item.approvalStatus === 'ESCALATED' || item.slaOverdue) return 'red'
  if (item.slaLevel === 'CRITICAL') return 'amber'
  return 'blue'
}

function escalationText(item) {
  if (item.escalatedTo) return `${item.escalatedTo} / ${item.escalationRole || '-'}`
  return item.escalationRole || '-'
}

function optionalDecimal(value) {
  if (value === null || value === undefined || value === '') return undefined
  const number = Number(value)
  if (!Number.isFinite(number)) {
    throw new Error('QMS数值字段必须是数字')
  }
  return number
}

function actionLabel(action) {
  return {
    RELEASE: '放行',
    REWORK: '返工',
    SCRAP: '报废'
  }[action] || '继续 Hold'
}

function actionText(action) {
  return {
    RELEASE: '复判通过，允许解除 Hold 并继续流转',
    REWORK: '复判不通过，建议返工处理',
    SCRAP: '缺陷不可恢复，建议报废'
  }[action] || '继续 Hold，等待补充分析'
}

function mrbPayload(action, opinion) {
  const attachments = []
  if (mrbForm.fileName || mrbForm.fileUrl || mrbForm.fileHash) {
    attachments.push({
      fileName: mrbForm.fileName,
      fileUrl: mrbForm.fileUrl,
      fileHash: mrbForm.fileHash,
      fileType: 'MRB_EVIDENCE',
      uploadedBy: localStorage.getItem('username') || 'qe'
    })
  }
  return {
    dispositionAction: action,
    mrbOpinion: opinion,
    meetingNo: mrbForm.meetingNo,
    participants: mrbForm.participants,
    riskLevel: mrbForm.riskLevel,
    meetingMinutes: mrbForm.meetingMinutes || opinion,
    minutesSummary: opinion,
    approvalStatus: 'APPROVED',
    attachments
  }
}

async function selectMrbItem(item) {
  selectedEventNo.value = item.eventNo
  await loadMrbRecords(item.eventNo)
  await loadMrbApprovals(item.eventNo)
}

async function loadMrbRecords(eventNo = selectedEventNo.value) {
  if (!eventNo) return
  try {
    const data = await getQualityMrbRecords(eventNo)
    mrbRecords.value = Array.isArray(data) ? data : []
  } catch (error) {
    warnDevFallback('MRB履历接口不可用', error)
    if (__DEV_MOCK_FALLBACK__) mrbRecords.value = fallbackMrbRecords
  }
}

async function loadMrbApprovals(eventNo = selectedEventNo.value) {
  if (!eventNo) return
  try {
    const data = await getQualityMrbApprovals({ eventNo })
    mrbApprovals.value = Array.isArray(data) ? data : []
  } catch (error) {
    warnDevFallback('MRB会签接口不可用', error)
    if (__DEV_MOCK_FALLBACK__) mrbApprovals.value = fallbackMrbApprovals
  }
}

async function handleRefreshSla() {
  if (!canEscalateAction.value) {
    ElMessage.warning('当前角色无权刷新 MRB SLA')
    return
  }
  try {
    actionLoading.value = 'mrb-sla'
    const result = await refreshQualityMrbApprovalSla({
      eventNo: selectedEventNo.value,
      operator: localStorage.getItem('username') || 'qe',
      escalationReason: 'MRB会签超过SLA，升级到责任主管'
    })
    ElMessage.success(`SLA刷新完成，升级 ${result?.escalatedCount || 0} 项`)
    await loadQualityData()
    await loadMrbApprovals(selectedEventNo.value)
  } finally {
    actionLoading.value = ''
  }
}

async function handleApproval(task, decision) {
  if (!canApproveAction.value) {
    ElMessage.warning('当前角色无权执行 MRB 会签')
    return
  }
  try {
    actionLoading.value = task.taskNo
    const payload = {
      opinion: decision === 'APPROVE' ? '会签通过，同意处置结论' : '会签驳回，需要补充分析',
      approver: localStorage.getItem('username') || 'qe'
    }
    if (decision === 'APPROVE') {
      await approveQualityMrbTask(task.taskNo, payload)
    } else {
      await rejectQualityMrbTask(task.taskNo, payload)
    }
    ElMessage.success(decision === 'APPROVE' ? '会签已通过' : '会签已驳回')
    await loadQualityData()
    await loadMrbRecords(selectedEventNo.value)
    await loadMrbApprovals(selectedEventNo.value)
  } finally {
    actionLoading.value = ''
  }
}

async function handleReview(item, action) {
  if (!canReviewAction.value) {
    ElMessage.warning('当前角色无权执行 MRB 复判')
    return
  }
  try {
    actionLoading.value = item.eventNo
    const opinion = actionText(action)
    await reviewQualityException(item.eventNo, {
      ...mrbPayload(action, opinion),
      reviewer: localStorage.getItem('username') || 'qe'
    })
    ElMessage.success(`MRB复判已提交：${actionLabel(action)}`)
    selectedEventNo.value = item.eventNo
    await loadQualityData()
    await loadMrbRecords(item.eventNo)
    await loadMrbApprovals(item.eventNo)
  } finally {
    actionLoading.value = ''
  }
}

async function handleClose(item) {
  if (!canCloseAction.value) {
    ElMessage.warning('当前角色无权关闭异常')
    return
  }
  const action = item.dispositionAction || item.mrbResult || 'RELEASE'
  try {
    actionLoading.value = item.eventNo
    await closeQualityException(item.eventNo, {
      ...mrbPayload(action, item.conclusion || actionText(action)),
      dispositionAction: action,
      closeConclusion: item.conclusion || actionText(action),
      rootCause: item.rootCause || 'MRB复判确认并完成处置',
      closedBy: localStorage.getItem('username') || 'qe'
    })
    ElMessage.success('异常已关闭')
    selectedEventNo.value = item.eventNo
    await loadQualityData()
    await loadMrbRecords(item.eventNo)
    await loadMrbApprovals(item.eventNo)
  } finally {
    actionLoading.value = ''
  }
}

async function submitQmsInspection() {
  if (!canMrbAction.value) {
    ElMessage.warning('当前角色无权执行 QMS 检验上报')
    return
  }
  if (!qmsForm.lotNo) {
    ElMessage.warning('Lot不能为空')
    return
  }
  try {
    qmsSubmitting.value = true
    const item = {
      itemCode: qmsForm.itemCode || 'QMS_RESULT',
      itemName: qmsForm.itemName || qmsForm.itemCode || 'QMS检验结果',
      result: qmsForm.result,
      measuredValue: optionalDecimal(qmsForm.measuredValue),
      lowerLimit: optionalDecimal(qmsForm.lowerLimit),
      upperLimit: optionalDecimal(qmsForm.upperLimit),
      unit: qmsForm.unit,
      defectCode: qmsForm.result === 'NG' ? qmsForm.defectCode : undefined,
      remark: qmsForm.remark
    }
    const result = await ingestQmsInspection({
      lotNo: qmsForm.lotNo,
      stepCode: qmsForm.stepCode || undefined,
      equipmentCode: qmsForm.equipmentCode || undefined,
      operator: qmsForm.operator || localStorage.getItem('username') || 'qe1001',
      result: qmsForm.result,
      items: [item]
    })
    qmsResult.value = result
    ElMessage.success(result?.holdApplied ? 'QMS NG 已上报并触发 Hold' : 'QMS 检验已上报')
    await loadQualityData()
  } catch (error) {
    ElMessage.warning(error?.message || 'QMS 检验上报失败')
  } finally {
    qmsSubmitting.value = false
  }
}

async function loadQualityData() {
  try {
    const [inspectionData, exceptionData, yieldData] = await Promise.all([
      getQualityInspections(),
      getQualityExceptions(),
      getYieldDashboard()
    ])
    if (Array.isArray(inspectionData) && inspectionData.length) inspections.value = inspectionData
    if (Array.isArray(exceptionData) && exceptionData.length) {
      exceptions.value = exceptionData
      if (!selectedEventNo.value || !exceptionData.some(item => item.eventNo === selectedEventNo.value)) {
        selectedEventNo.value = exceptionData[0].eventNo
      }
    }
    if (!qmsForm.lotNo) {
      qmsForm.lotNo = inspectionData?.[0]?.lotNo || exceptionData?.[0]?.lotNo || ''
    }
    if (Array.isArray(yieldData?.defectTopN) && yieldData.defectTopN.length) defectTopN.value = yieldData.defectTopN
    await loadMrbRecords(selectedEventNo.value)
    await loadMrbApprovals(selectedEventNo.value)
  } catch (error) {
    warnDevFallback('质量接口不可用', error)
    if (__DEV_MOCK_FALLBACK__) {
      inspections.value = fallbackInspections
      exceptions.value = fallbackExceptions
      defectTopN.value = fallbackDefects
      selectedEventNo.value = fallbackExceptions[0]?.eventNo || ''
    }
  }
}

onMounted(loadQualityData)
</script>

<style scoped>
.mini-card {
  cursor: pointer;
}

.mini-card.selected {
  border-color: #c3c3ba;
  background: var(--mes-paper-muted);
  box-shadow: inset 0 0 0 1px var(--mes-line-soft);
}

.mini-stock {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  margin-top: 8px;
  color: var(--mes-weak);
  font-size: 12px;
}

.mini-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.mini-action {
  min-width: 56px;
  height: 28px;
  border: 1px solid var(--mes-line);
  border-radius: 6px;
  background: #fff;
  color: var(--mes-text);
  font-size: 12px;
  cursor: pointer;
}

.mini-action:hover:not(:disabled) {
  border-color: #c3c3ba;
  background: var(--mes-control-hover);
  color: var(--mes-ink);
}

.mini-action.danger:hover:not(:disabled) {
  border-color: #dfc3bf;
  background: var(--mes-red-soft);
  color: var(--mes-red);
}

.mini-action:disabled,
.mes-btn:disabled {
  cursor: not-allowed;
  opacity: 0.52;
}

.mini-conclusion {
  margin-top: 8px;
  color: var(--mes-sub);
  font-size: 12px;
  line-height: 1.5;
}

.mrb-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.mrb-minutes-field {
  grid-column: 1 / -1;
}

.mrb-minutes-field textarea {
  min-height: 74px;
  resize: vertical;
  line-height: 1.5;
}

.mrb-table td:nth-child(5) {
  max-width: 360px;
}

.mrb-subtitle {
  margin: 14px 0 8px;
  color: var(--mes-sub);
  font-size: 12px;
  font-weight: 700;
}

.qms-form {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.qms-remark-field {
  grid-column: span 2;
}

.qms-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}

.qms-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  color: var(--mes-sub);
  font-size: 12px;
}

@media (max-width: 960px) {
  .mrb-form {
    grid-template-columns: 1fr;
  }

  .qms-form {
    grid-template-columns: 1fr;
  }

  .qms-remark-field {
    grid-column: span 1;
  }

  .qms-footer {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
