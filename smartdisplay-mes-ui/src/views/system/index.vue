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
        <button class="mes-btn primary" @click="showRoleTodo">新建角色</button>
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
            <button class="mes-btn">权限差异对比</button>
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
                <tr><th>变更单</th><th>角色</th><th>状态</th><th>申请人</th><th>审批</th></tr>
              </thead>
              <tbody>
                <tr v-for="change in permissionChanges" :key="change.key">
                  <td>{{ change.changeNo }}</td>
                  <td>{{ change.targetRole }}</td>
                  <td><span class="status-tag" :class="change.type">{{ change.statusText }}</span></td>
                  <td>{{ change.requester }}</td>
                  <td>
                    <button
                      class="mes-btn tiny"
                      :disabled="change.status !== 'PENDING_REVIEW' || !canMaintainPermissions"
                      @click="approvePermissionChange(change)"
                    >
                      通过
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-if="!permissionChanges.length" class="audit-empty">暂无权限变更单</div>
          </div>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">告警与审批规则</div>
          <span class="status-tag amber">3 条需复核</span>
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
            <button class="mes-btn primary">发布规则</button>
            <button class="mes-btn">模拟触发</button>
            <button class="mes-btn warn">停用规则</button>
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
  getSystemUsers,
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
const systemUsers = ref([])

const permissionForm = reactive({
  targetRole: 'QE',
  addButtons: 'ai:equipment-analyze',
  reason: '质量工程师需要联动查看设备异常分析'
})

const canMaintainPermissions = computed(() => hasButton('system:permission-change'))

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
  return [
    { label: '启用用户', value: String(systemUsers.value.length || 18), tag: 'RBAC', type: 'blue', left: '生产 8', right: '工程 6' },
    { label: '权限点', value: '146', tag: '按模块', type: 'green', left: '核心 42', right: '敏感 12' },
    { label: '审计事件', value: String(auditLogs.value.length), tag: '当前', type: 'teal', left: `成功 ${successCount}`, right: `待复核 ${reviewCount}` },
    { label: '权限变更', value: String(permissionChanges.value.length), tag: `待审 ${permissionPending}`, type: permissionPending ? 'amber' : 'green', left: '审批闭环', right: '审计留痕' }
  ]
})

const roles = [
  { name: '生产班长', post: '线体管理', permissions: '派工、Track、Hold 申请', scope: '本基地 / 本产线', status: '启用', type: 'green' },
  { name: '质量工程师', post: '质量处置', permissions: 'Hold Release、MRB、SPC', scope: '本基地 / 全工序', status: '启用', type: 'green' },
  { name: '工艺工程师', post: '工艺维护', permissions: 'Route、Recipe、规格版本', scope: '产品族 / 工艺段', status: '审批中', type: 'amber' },
  { name: '系统管理员', post: '平台治理', permissions: '用户、角色、审计策略', scope: '租户级', status: '受控', type: 'red' }
]

const rules = [
  { name: '关键 Recipe 发布双人复核', status: '启用', type: 'green', meta: '对象：COATING / EVAP / BOND；触发：版本发布、参数范围变更；审批：工艺经理 + 质量经理' },
  { name: 'Hold 超 SLA 升级', status: '需复核', type: 'amber', meta: 'P1 超过 30 分钟推送班长、质量工程师；超过 60 分钟升级制造经理' },
  { name: '跨产线权限访问拦截', status: '启用', type: 'green', meta: '当用户访问非授权基地、产线、工序数据时拦截并写入审计日志' },
  { name: '敏感操作二次确认', status: '启用', type: 'blue', meta: 'Scrap、MRB 报废、Recipe 回退、权限变更必须记录原因码和电子签名' }
]

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
    requester: change.requester || 'system'
  }
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
    return
  }
  loadingPermissionChanges.value = true
  try {
    const data = await getPermissionChangeRequests()
    permissionChanges.value = Array.isArray(data) ? data.map(mapPermissionChange) : []
  } catch (error) {
    console.warn('权限变更单接口不可用', error)
    permissionChanges.value = []
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

function showRoleTodo() {
  ElMessage.info('角色维护入口已预留，后续接入 RBAC 管理接口')
}

onMounted(() => {
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
