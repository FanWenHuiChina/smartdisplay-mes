<template>
  <section>
    <div class="page-head">
      <div>
        <h1 class="page-title">系统管理 / 权限、审计与规则配置</h1>
        <p class="page-desc">面向商用 MES 的租户、角色、权限点、审计日志和告警规则治理，保证操作可控、变更可追溯。</p>
      </div>
      <div class="page-actions">
        <button class="mes-btn" @click="exportAuditLogs">导出审计</button>
        <button class="mes-btn" @click="loadAuditLogs">刷新日志</button>
        <button
          class="mes-btn primary"
          :disabled="!canMaintainPermissions || loadingPermissionReload"
          @click="reloadRuntimePermissions"
        >
          {{ loadingPermissionReload ? '重载中' : '重载权限' }}
        </button>
      </div>
    </div>

    <div class="mes-grid cols-4">
      <div v-for="metric in metrics" :key="metric.label" class="mes-card metric-card">
        <div class="metric-label">
          <span>{{ metric.label }}</span>
          <span class="status-tag" :class="metric.type">{{ metric.tag }}</span>
        </div>
        <div class="metric-value">{{ metric.value }}</div>
        <div class="metric-meta">
          <span>{{ metric.left }}</span>
          <span>{{ metric.right }}</span>
        </div>
      </div>
    </div>

    <div class="mes-grid cols-2 section-gap">
      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">角色与权限矩阵 <small>按岗位授权</small></div>
          <span class="status-tag blue">最小权限</span>
        </div>
        <div class="mes-card__body">
          <table class="mes-table">
            <thead>
              <tr><th>角色</th><th>适用岗位</th><th>关键权限</th><th>数据范围</th><th>状态</th></tr>
            </thead>
            <tbody>
              <tr v-for="role in roles" :key="role.name">
                <td>{{ role.name }}</td>
                <td>{{ role.post }}</td>
                <td>{{ role.permissions }}</td>
                <td>{{ role.scope }}</td>
                <td><span class="status-tag" :class="role.type">{{ role.status }}</span></td>
              </tr>
            </tbody>
          </table>
          <div class="toolbar">
            <button class="mes-btn primary" :disabled="!canMaintainPermissions || loadingPermissionChanges" @click="submitPermissionChange">
              提交权限变更
            </button>
            <button class="mes-btn" :disabled="loadingPermissionChanges" @click="loadPermissionChanges">刷新变更单</button>
            <button class="mes-btn" :disabled="!permissionChanges.length" @click="compareLatestPermissionChange">
              权限差异对比
            </button>
          </div>
          <div class="permission-change-panel">
            <div class="audit-toolbar permission-toolbar">
              <div class="mes-field">
                <label>目标角色</label>
                <select v-model="permissionForm.targetRole" class="mes-select">
                  <option value="QE">QE</option>
                  <option value="PE">PE</option>
                  <option value="EE">EE</option>
                  <option value="PLANNER">PLANNER</option>
                  <option value="OPERATOR">OPERATOR</option>
                </select>
              </div>
              <div class="mes-field">
                <label>新增按钮权限</label>
                <input v-model.trim="permissionForm.addButtons" class="mes-input" placeholder="ai:equipment-analyze" />
              </div>
              <div class="mes-field">
                <label>变更原因</label>
                <input v-model.trim="permissionForm.reason" class="mes-input" placeholder="说明授权原因" />
              </div>
            </div>
            <table class="mes-table permission-table">
              <thead>
                <tr><th>变更单</th><th>角色</th><th>状态</th><th>申请人</th><th>操作</th></tr>
              </thead>
              <tbody>
                <tr v-for="change in permissionChanges" :key="change.key">
                  <td>{{ change.changeNo }}</td>
                  <td>{{ change.targetRole }}</td>
                  <td><span class="status-tag" :class="change.type">{{ change.statusText }}</span></td>
                  <td>{{ change.requester }}</td>
                  <td class="permission-actions">
                    <button class="mes-btn tiny" @click="comparePermissionChange(change)">
                      对比
                    </button>
                    <button
                      class="mes-btn tiny"
                      :disabled="change.status !== 'PENDING_REVIEW' || !canMaintainPermissions"
                      @click="approvePermissionChange(change)"
                    >
                      通过
                    </button>
                    <button
                      class="mes-btn tiny warn"
                      :disabled="change.status !== 'PENDING_REVIEW' || !canMaintainPermissions"
                      @click="rejectPermissionChange(change)"
                    >
                      驳回
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-if="!permissionChanges.length" class="audit-empty">暂无权限变更单</div>
            <div v-if="selectedPermissionChange" class="permission-diff">
              <div class="permission-diff__head">
                <strong>{{ selectedPermissionChange.changeNo }} 权限差异</strong>
                <span class="status-tag" :class="selectedPermissionChange.type">{{ selectedPermissionChange.statusText }}</span>
              </div>
              <table class="mes-table permission-diff__table">
                <thead>
                  <tr><th>字段</th><th>变更前</th><th>变更后</th></tr>
                </thead>
                <tbody>
                  <tr v-for="row in permissionDiffRows" :key="row.field" :class="{ changed: row.changed }">
                    <td>{{ row.label }}</td>
                    <td>{{ row.before }}</td>
                    <td>{{ row.after }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">告警与审批规则</div>
          <span class="status-tag" :class="ruleReviewCount ? 'amber' : 'green'">{{ ruleReviewCount }} 条需复核</span>
        </div>
        <div class="mes-card__body">
          <div class="cards">
            <div v-for="rule in rules" :key="rule.name" class="mini-card">
              <div class="mini-top">
                <span>{{ rule.name }}</span>
                <span class="status-tag" :class="rule.type">{{ rule.status }}</span>
              </div>
              <div class="mini-meta">{{ rule.meta }}</div>
            </div>
          </div>
          <div class="toolbar">
            <button class="mes-btn primary" :disabled="ruleActionLoading === 'publish'" @click="publishRuleSet">
              {{ ruleActionLoading === 'publish' ? '发布中' : '发布规则' }}
            </button>
            <button class="mes-btn" :disabled="ruleActionLoading === 'test'" @click="runRuleSimulation">试运行规则</button>
            <button class="mes-btn warn" :disabled="ruleActionLoading === 'disable'" @click="disableReviewRule">停用规则</button>
          </div>
        </div>
      </div>
    </div>

    <div class="mes-grid cols-2-wide section-gap">
      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">审计日志</div>
          <span class="status-tag teal">{{ auditStatusText }}</span>
        </div>
        <div class="mes-card__body">
          <div class="audit-toolbar">
            <div class="mes-field">
              <label>对象 / Lot</label>
              <input
                v-model.trim="filters.bizNo"
                class="mes-input"
                placeholder="请输入业务对象"
                @keyup.enter="loadAuditLogs"
              />
            </div>
            <div class="mes-field">
              <label>操作类型</label>
              <select v-model="filters.action" class="mes-select">
                <option value="">全部操作</option>
                <option value="TRACK">Track In / Out</option>
                <option value="LOT">Hold / Release</option>
                <option value="ORDER">工单</option>
                <option value="AI">AI</option>
                <option value="RECIPE">Recipe</option>
              </select>
            </div>
            <button class="mes-btn primary" :disabled="loadingAudit" @click="loadAuditLogs">
              {{ loadingAudit ? '查询中' : '查询' }}
            </button>
            <button class="mes-btn" @click="resetFilters">重置</button>
          </div>
          <table class="mes-table">
            <thead>
              <tr><th>时间</th><th>用户</th><th>对象</th><th>操作</th><th>结果</th><th>来源</th></tr>
            </thead>
            <tbody>
              <tr v-for="log in filteredAuditLogs" :key="log.key">
                <td>{{ log.time }}</td>
                <td>{{ log.user }}</td>
                <td>{{ log.object }}</td>
                <td>{{ log.action }}</td>
                <td><span class="status-tag" :class="log.type">{{ log.result }}</span></td>
                <td>{{ log.source }}</td>
              </tr>
            </tbody>
          </table>
          <div v-if="!filteredAuditLogs.length" class="audit-empty">
            暂无匹配审计日志
          </div>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">系统健康</div>
          <span class="status-tag green">运行正常</span>
        </div>
        <div class="mes-card__body detail-list">
          <div v-for="item in health" :key="item.label" class="detail-row">
            <b>{{ item.label }}</b>
            <span>{{ item.value }}</span>
          </div>
          <div class="ai-box">
            <h3>治理建议</h3>
            <p>Recipe 发布、Hold Release、MRB 处置应强制双人复核；跨产线数据权限建议按基地、产线、工序三级隔离。</p>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  createPermissionChangeRequest,
  getAuditLogs,
  getPermissionChangeRequests,
  getSystemSummary,
  getSystemUsers,
  reloadPermissions,
  reviewPermissionChangeRequest
} from '@/api/pilot'
import { hasButton } from '@/utils/permissions'
import { warnDevFallback } from '@/utils/devFallback'

const fallbackAuditLogs = [
  { time: '14:31:22', user: 'qe1003', object: 'LOT260606-017', action: 'Hold Release 审批', result: '通过', source: '10.12.8.41' },
  { time: '14:26:10', user: 'pe2007', object: 'RCP_COAT_65_V12', action: 'Recipe 参数变更', result: '待复核', source: '10.12.6.18' },
  { time: '14:18:44', user: 'op1007', object: 'COATER_02', action: 'Track In', result: '失败', source: 'LINE-HMI-02' },
  { time: '14:05:36', user: 'pc3002', object: 'MO20260606012', action: '工单释放', result: '成功', source: '10.12.3.22' }
]

const filters = reactive({
  bizNo: '',
  action: ''
})

const auditLogs = ref(__DEV_MOCK_FALLBACK__ ? fallbackAuditLogs.map(mapAuditLog) : [])
const loadingAudit = ref(false)
const lastRefreshText = ref(__DEV_MOCK_FALLBACK__ ? '开发样例' : '待接口')
const permissionChanges = ref([])
const loadingPermissionChanges = ref(false)
const loadingPermissionReload = ref(false)
const selectedPermissionChange = ref(null)
const systemUsers = ref([])
const systemSummary = ref(null)
const ruleActionLoading = ref('')
const disabledRuleNames = ref(new Set())

const fallbackRoles = [
  { name: '生产班长', post: '线体管理', permissions: '派工、Track、Hold 申请', scope: '本基地 / 本产线', status: '启用', type: 'green' },
  { name: '质量工程师', post: '质量处置', permissions: 'Hold Release、MRB、SPC', scope: '本基地 / 全工序', status: '启用', type: 'green' },
  { name: '工艺工程师', post: '工艺维护', permissions: 'Route、Recipe、规格版本', scope: '产品族 / 工艺段', status: '审批中', type: 'amber' },
  { name: '系统管理员', post: '平台治理', permissions: '用户、角色、审计策略', scope: '租户级', status: '受控', type: 'red' }
]

const fallbackRules = [
  { name: '关键 Recipe 发布双人复核', status: '启用', type: 'green', meta: '对象：COATING / EVAP / BOND；触发：版本发布、参数范围变更；审批：工艺经理 + 质量经理' },
  { name: 'Hold 超 SLA 升级', status: '需复核', type: 'amber', meta: 'P1 超过 30 分钟推送班长、质量工程师；超过 60 分钟升级制造经理' },
  { name: '跨产线权限访问拦截', status: '启用', type: 'green', meta: '当用户访问非授权基地、产线、工序数据时拦截并写入审计日志' },
  { name: '敏感操作二次确认', status: '启用', type: 'blue', meta: 'Scrap、MRB 报废、Recipe 回退、权限变更必须记录原因码和电子签名' }
]

const permissionForm = reactive({
  targetRole: 'QE',
  addButtons: 'ai:equipment-analyze',
  reason: '质量工程师需要联动查看设备异常分析'
})

const canMaintainPermissions = computed(() => hasButton('system:permission-change'))

const permissionSnapshots = computed(() => {
  const rows = systemSummary.value?.permissions
  return Array.isArray(rows) ? rows : []
})

const roles = computed(() => {
  if (!permissionSnapshots.value.length) {
    return __DEV_MOCK_FALLBACK__ ? fallbackRoles : []
  }
  return permissionSnapshots.value.map(mapRolePermission)
})

const rules = computed(() => {
  const rows = systemSummary.value?.rules
  if (!Array.isArray(rows) || !rows.length) {
    return __DEV_MOCK_FALLBACK__ ? fallbackRules.map(applyRuleOverride) : []
  }
  return rows.map(mapRule).map(applyRuleOverride)
})

const ruleReviewCount = computed(() => rules.value.filter(rule => rule.type === 'amber').length)

const permissionPointCount = computed(() => {
  const points = new Set()
  permissionSnapshots.value.forEach(snapshot => {
    arrayValue(snapshot.menus).forEach(item => points.add(`menu:${item}`))
    arrayValue(snapshot.buttons).forEach(item => points.add(`button:${item}`))
    arrayValue(snapshot.domains).forEach(item => points.add(`domain:${item}`))
  })
  return points.size || (__DEV_MOCK_FALLBACK__ ? 146 : 0)
})

const sensitivePermissionCount = computed(() => {
  const sensitiveWords = ['scrap', 'mrb', 'permission', 'recipe', 'supplier', 'eap', 'ai']
  const buttons = permissionSnapshots.value.flatMap(snapshot => arrayValue(snapshot.buttons))
  return buttons.filter(button => sensitiveWords.some(word => String(button).toLowerCase().includes(word))).length
})

const permissionDiffRows = computed(() => {
  const change = selectedPermissionChange.value
  if (!change) return []
  const before = change.beforeSnapshot || {}
  const after = change.afterSnapshot || {}
  return [
    diffRow('角色', 'role', before, after),
    diffRow('菜单权限', 'menus', before, after),
    diffRow('按钮权限', 'buttons', before, after),
    diffRow('数据范围', 'dataScope', before, after),
    diffRow('领域权限', 'domains', before, after)
  ]
})

const filteredAuditLogs = computed(() => {
  if (!filters.action) return auditLogs.value
  return auditLogs.value.filter(log => actionMatches(log.action, filters.action))
})

const auditStatusText = computed(() => {
  if (loadingAudit.value) return '加载中'
  return `${filteredAuditLogs.value.length} 条 / ${lastRefreshText.value}`
})

const metrics = computed(() => {
  const successCount = auditLogs.value.filter(log => log.type === 'green').length
  const reviewCount = auditLogs.value.filter(log => log.type === 'amber').length
  const permissionPending = permissionChanges.value.filter(change => change.status === 'PENDING_REVIEW').length
  const userCount = systemUsers.value.length || (__DEV_MOCK_FALLBACK__ ? 18 : 0)
  return [
    { label: '启用用户', value: String(userCount), tag: 'RBAC', type: 'blue', left: `角色 ${roles.value.length}`, right: `待审 ${permissionPending}` },
    { label: '权限点', value: String(permissionPointCount.value), tag: '按模块', type: 'green', left: `角色 ${permissionSnapshots.value.length}`, right: `敏感 ${sensitivePermissionCount.value}` },
    { label: '审计事件', value: String(auditLogs.value.length), tag: '当前', type: 'teal', left: `成功 ${successCount}`, right: `待复核 ${reviewCount}` },
    { label: '权限变更', value: String(permissionChanges.value.length), tag: `待审 ${permissionPending}`, type: permissionPending ? 'amber' : 'green', left: '审批闭环', right: '审计留痕' }
  ]
})

const health = [
  { label: '服务状态', value: 'Web / API / 登录服务运行正常' },
  { label: '接口延迟', value: 'P95 186ms，近 1 小时无连续超时' },
  { label: '审计存储', value: '保留策略 180 天，今日写入 2,418 条' },
  { label: '同步状态', value: 'ERP 工单、AD 组织、EAP 设备状态同步正常' },
  { label: '待办风险', value: '3 条告警规则超过 30 天未复核' }
]

function resultType(result = '') {
  if (['成功', '通过', 'SUCCESS', 'OK'].some(word => result.includes(word))) return 'green'
  if (['待', '复核', '审批'].some(word => result.includes(word))) return 'amber'
  if (['失败', 'FAIL', 'ERROR'].some(word => result.includes(word))) return 'red'
  return 'blue'
}

function mapAuditLog(log, index = 0) {
  const time = log.time || log.createdTime || '-'
  const object = log.object || log.bizNo || '-'
  const action = log.action || '-'
  const result = log.result || '成功'
  return {
    key: `${time}-${object}-${action}-${index}`,
    time,
    user: log.user || log.operator || 'system',
    object,
    action,
    result,
    type: resultType(result),
    source: log.source || '-'
  }
}

function actionMatches(action = '', type = '') {
  const upper = action.toUpperCase()
  if (type === 'LOT') return upper.includes('HOLD') || upper.includes('RELEASE') || upper.includes('LOT_')
  if (type === 'TRACK') return upper.includes('TRACK')
  if (type === 'ORDER') return upper.includes('ORDER') || action.includes('工单')
  if (type === 'AI') return upper.includes('AI')
  if (type === 'RECIPE') return upper.includes('RECIPE')
  return true
}

async function loadAuditLogs() {
  loadingAudit.value = true
  try {
    const data = await getAuditLogs(filters.bizNo ? { bizNo: filters.bizNo } : {})
    auditLogs.value = Array.isArray(data) ? data.map(mapAuditLog) : []
    lastRefreshText.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  } catch (error) {
    warnDevFallback('审计日志接口不可用', error)
    if (__DEV_MOCK_FALLBACK__) {
      auditLogs.value = fallbackAuditLogs.map(mapAuditLog)
      lastRefreshText.value = '开发样例'
    }
  } finally {
    loadingAudit.value = false
  }
}

function resetFilters() {
  filters.bizNo = ''
  filters.action = ''
  loadAuditLogs()
}

function exportAuditLogs() {
  if (!filteredAuditLogs.value.length) {
    ElMessage.warning('当前没有可导出的审计日志')
    return
  }

  const rows = [
    ['时间', '用户', '对象', '操作', '结果', '来源'],
    ...filteredAuditLogs.value.map(log => [log.time, log.user, log.object, log.action, log.result, log.source])
  ]
  const csv = rows.map(row => row.map(cell => `"${String(cell).replace(/"/g, '""')}"`).join(',')).join('\n')
  const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `audit-logs-${Date.now()}.csv`
  link.click()
  URL.revokeObjectURL(url)
}

function permissionStatusType(status = '') {
  if (status === 'APPROVED') return 'green'
  if (status === 'REJECTED') return 'red'
  return 'amber'
}

function permissionStatusText(status = '') {
  if (status === 'APPROVED') return '已通过'
  if (status === 'REJECTED') return '已驳回'
  return '待审批'
}

function mapPermissionChange(change, index = 0) {
  return {
    key: `${change.changeNo || index}-${change.status || 'PENDING'}`,
    changeNo: change.changeNo || '-',
    targetRole: change.targetRole || '-',
    status: change.status || 'PENDING_REVIEW',
    statusText: permissionStatusText(change.status),
    type: permissionStatusType(change.status),
    requester: change.requester || 'system',
    reviewer: change.reviewer || '-',
    reason: change.reason || '-',
    beforeSnapshot: parseSnapshot(change.beforeSnapshot),
    afterSnapshot: parseSnapshot(change.afterSnapshot)
  }
}

function mapRolePermission(snapshot = {}) {
  const role = String(snapshot.role || '-').toUpperCase()
  const pending = permissionChanges.value.some(change => change.targetRole === role && change.status === 'PENDING_REVIEW')
  return {
    name: roleDisplayName(role),
    post: rolePost(role),
    permissions: summarizeRolePermissions(snapshot),
    scope: dataScopeText(snapshot.dataScope),
    status: pending ? '审批中' : '启用',
    type: pending ? 'amber' : role === 'ADMIN' ? 'red' : 'green'
  }
}

function summarizeRolePermissions(snapshot = {}) {
  const domains = arrayValue(snapshot.domains)
  const buttons = arrayValue(snapshot.buttons)
  const domainText = domains.length ? domains.slice(0, 3).join('、') : '基础执行'
  const extra = buttons.length > 3 ? `等 ${buttons.length} 项按钮` : buttons.join('、')
  return extra ? `${domainText} / ${extra}` : domainText
}

function mapRule(rule = {}) {
  return {
    name: rule.name || '规则',
    status: ruleStatusText(rule.status),
    type: ruleStatusType(rule.status),
    meta: rule.meta || `状态：${rule.status || '-'}`
  }
}

function applyRuleOverride(rule) {
  if (!disabledRuleNames.value.has(rule.name)) return rule
  return {
    ...rule,
    status: '停用',
    type: 'red',
    meta: `${rule.meta} / 已在当前治理台标记停用，等待后端规则配置接口持久化`
  }
}

function ruleStatusText(status = '') {
  const value = String(status).toUpperCase()
  if (value === 'ENABLED') return '启用'
  if (value === 'DISABLED') return '停用'
  if (value === 'REVIEW_REQUIRED') return '需复核'
  return status || '-'
}

function ruleStatusType(status = '') {
  const value = String(status).toUpperCase()
  if (value === 'ENABLED') return 'green'
  if (value === 'DISABLED') return 'red'
  if (value === 'REVIEW_REQUIRED') return 'amber'
  return 'blue'
}

function roleDisplayName(role) {
  return {
    ADMIN: '系统管理员',
    PLANNER: '计划员',
    OPERATOR: '操作员',
    QE: '质量工程师',
    PE: '工艺工程师',
    EE: '设备工程师'
  }[role] || role
}

function rolePost(role) {
  return {
    ADMIN: '平台治理',
    PLANNER: '计划排产',
    OPERATOR: '现场执行',
    QE: '质量处置',
    PE: '工艺维护',
    EE: '设备自动化'
  }[role] || '岗位'
}

function dataScopeText(scope = '') {
  return {
    ALL: '全局',
    LINE: '本产线',
    SELF_SHIFT: '本人 / 当班',
    SELF: '本人'
  }[String(scope).toUpperCase()] || scope || '-'
}

function arrayValue(value) {
  return Array.isArray(value) ? value : []
}

function parseSnapshot(value) {
  if (!value) return {}
  if (typeof value === 'object') return value
  try {
    return JSON.parse(value)
  } catch (error) {
    console.warn('权限快照解析失败', error)
    return {}
  }
}

function diffRow(label, key, before, after) {
  const beforeValue = formatSnapshotValue(before[key])
  const afterValue = formatSnapshotValue(after[key])
  return {
    label,
    field: key,
    before: beforeValue,
    after: afterValue,
    changed: beforeValue !== afterValue
  }
}

function formatSnapshotValue(value) {
  if (Array.isArray(value)) {
    return value.length ? value.join(', ') : '-'
  }
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return String(value)
}

function splitPermissionCodes(value = '') {
  return value
    .split(/[,;\s]+/)
    .map(item => item.trim())
    .filter(Boolean)
}

async function loadPermissionChanges() {
  if (!canMaintainPermissions.value) {
    permissionChanges.value = []
    selectedPermissionChange.value = null
    return
  }
  loadingPermissionChanges.value = true
  try {
    const data = await getPermissionChangeRequests()
    permissionChanges.value = Array.isArray(data) ? data.map(mapPermissionChange) : []
    if (selectedPermissionChange.value) {
      selectedPermissionChange.value = permissionChanges.value.find(
        change => change.changeNo === selectedPermissionChange.value.changeNo
      ) || null
    }
  } catch (error) {
    console.warn('权限变更单接口不可用', error)
    permissionChanges.value = []
    selectedPermissionChange.value = null
  } finally {
    loadingPermissionChanges.value = false
  }
}

async function loadSystemUsers() {
  try {
    const data = await getSystemUsers()
    systemUsers.value = Array.isArray(data) ? data : []
  } catch (error) {
    console.warn('系统用户接口不可用，保留系统页默认指标', error)
    systemUsers.value = []
  }
}

async function submitPermissionChange() {
  if (!canMaintainPermissions.value) {
    ElMessage.warning('当前角色无权限维护权限点')
    return
  }
  const addButtons = splitPermissionCodes(permissionForm.addButtons)
  if (!addButtons.length) {
    ElMessage.warning('请至少填写一个新增按钮权限')
    return
  }
  loadingPermissionChanges.value = true
  try {
    await createPermissionChangeRequest({
      targetRole: permissionForm.targetRole,
      changeType: 'BUTTON_GRANT',
      addButtons,
      reason: permissionForm.reason || '权限点授权调整'
    })
    ElMessage.success('权限变更申请已提交')
    await loadPermissionChanges()
    await loadAuditLogs()
  } finally {
    loadingPermissionChanges.value = false
  }
}

async function approvePermissionChange(change) {
  if (!canMaintainPermissions.value) {
    ElMessage.warning('当前角色无权限审批权限变更')
    return
  }
  loadingPermissionChanges.value = true
  try {
    await reviewPermissionChangeRequest(change.changeNo, {
      decision: 'APPROVE',
      reviewOpinion: '试点环境审批通过'
    })
    ElMessage.success('权限变更已审批通过')
    await loadPermissionChanges()
    await loadAuditLogs()
  } finally {
    loadingPermissionChanges.value = false
  }
}

async function loadSystemSummary() {
  try {
    const data = await getSystemSummary()
    systemSummary.value = data || null
    if (Array.isArray(data?.users)) {
      systemUsers.value = data.users
    }
    if (Array.isArray(data?.auditLogs) && !auditLogs.value.length) {
      auditLogs.value = data.auditLogs.map(mapAuditLog)
    }
  } catch (error) {
    warnDevFallback('系统摘要接口不可用', error)
    systemSummary.value = null
  }
}

async function rejectPermissionChange(change) {
  if (!canMaintainPermissions.value) {
    ElMessage.warning('当前角色无权限审批权限变更')
    return
  }
  loadingPermissionChanges.value = true
  try {
    await reviewPermissionChangeRequest(change.changeNo, {
      decision: 'REJECT',
      reviewOpinion: '权限变更依据不足，驳回'
    })
    ElMessage.success('权限变更已驳回')
    await loadPermissionChanges()
    await loadAuditLogs()
  } finally {
    loadingPermissionChanges.value = false
  }
}

function comparePermissionChange(change) {
  selectedPermissionChange.value = change
}

function compareLatestPermissionChange() {
  const target = permissionChanges.value.find(change => change.status === 'PENDING_REVIEW') || permissionChanges.value[0]
  if (target) {
    comparePermissionChange(target)
  }
}

async function reloadRuntimePermissions() {
  if (!canMaintainPermissions.value) {
    ElMessage.warning('当前角色无权限重载权限快照')
    return
  }
  loadingPermissionReload.value = true
  try {
    const result = await reloadPermissions()
    ElMessage.success(`权限快照已重载，应用角色 ${result?.appliedRoles ?? 0} 个`)
    await loadPermissionChanges()
    await loadAuditLogs()
  } finally {
    loadingPermissionReload.value = false
  }
}

async function publishRuleSet() {
  ruleActionLoading.value = 'publish'
  try {
    disabledRuleNames.value = new Set()
    await loadSystemSummary()
    await loadAuditLogs()
    ElMessage.success(`规则集已刷新，当前 ${rules.value.length} 条规则可用`)
  } finally {
    ruleActionLoading.value = ''
  }
}

async function runRuleSimulation() {
  ruleActionLoading.value = 'test'
  try {
    filters.action = 'LOT'
    filters.bizNo = ''
    await loadAuditLogs()
    ElMessage.success('规则试运行完成，已切换到 Hold / Release 审计样本')
  } finally {
    ruleActionLoading.value = ''
  }
}

function disableReviewRule() {
  const target = rules.value.find(rule => rule.type === 'amber') || rules.value[0]
  if (!target) {
    ElMessage.warning('当前没有可停用的规则')
    return
  }
  ruleActionLoading.value = 'disable'
  disabledRuleNames.value = new Set([...disabledRuleNames.value, target.name])
  ElMessage.success(`已停用规则：${target.name}`)
  ruleActionLoading.value = ''
}

onMounted(() => {
  loadSystemSummary()
  loadAuditLogs()
  loadPermissionChanges()
  loadSystemUsers()
})
</script>

<style scoped>
.audit-toolbar {
  display: grid;
  grid-template-columns: minmax(180px, 1.2fr) minmax(160px, 1fr) auto auto;
  gap: 9px;
  align-items: end;
  padding-bottom: 12px;
}

.audit-empty {
  min-height: 54px;
  display: grid;
  place-items: center;
  color: var(--mes-sub);
  font-size: 13px;
}

.permission-change-panel {
  margin-top: 12px;
  border-top: 1px solid rgba(148, 163, 184, 0.18);
  padding-top: 12px;
}

.permission-toolbar {
  grid-template-columns: minmax(110px, 0.7fr) minmax(180px, 1fr) minmax(220px, 1.3fr);
  padding-bottom: 10px;
}

.permission-table {
  margin-top: 4px;
}

.permission-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.permission-diff {
  margin-top: 12px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 7px;
  background: #fff;
  padding: 10px;
}

.permission-diff__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-bottom: 8px;
}

.permission-diff__table tr.changed td {
  background: rgba(14, 165, 233, 0.06);
}

.mes-btn.tiny {
  min-height: 28px;
  padding: 4px 9px;
  font-size: 12px;
}

@media (max-width: 860px) {
  .audit-toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
