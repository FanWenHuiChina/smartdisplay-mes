import { existsSync, readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = dirname(fileURLToPath(import.meta.url))
const projectRoot = resolve(scriptDir, '..')

const failures = []
const passes = []

function read(relativePath) {
  const file = join(projectRoot, relativePath)
  if (!existsSync(file)) {
    failures.push({ name: `file:${relativePath}`, detail: 'missing file' })
    return ''
  }
  return readFileSync(file, 'utf8')
}

function check(name, condition, detail = '') {
  if (condition) {
    passes.push(name)
    return
  }
  failures.push({ name, detail })
}

function hasAll(source, values) {
  return values.every(value => source.includes(value))
}

function exportedNames(source) {
  const names = new Set()
  const pattern = /export\s+const\s+([A-Za-z0-9_]+)\s*=/g
  for (const match of source.matchAll(pattern)) {
    names.add(match[1])
  }
  return names
}

function hasExport(source, name, endpointFragment) {
  const names = exportedNames(source)
  return names.has(name) && source.includes(endpointFragment)
}

const router = read('src/router/index.js')
const request = read('src/api/request.js')
const authApi = read('src/api/auth.js')
const pilotApi = read('src/api/pilot.js')
const permissions = read('src/utils/permissions.js')
const devFallbackUtil = read('src/utils/devFallback.js')
const viteConfig = read('vite.config.js')
const packageJson = JSON.parse(read('package.json') || '{}')

const requiredRoutes = [
  'overview',
  'order',
  'master',
  'execution',
  'equipment',
  'quality',
  'material',
  'trace',
  'ai',
  'system'
]

check('router:login-route', router.includes("path: '/login'"))
for (const route of requiredRoutes) {
  check(`router:${route}`, router.includes(`path: '${route}'`))
}
for (const redirect of ['dashboard', 'lot', 'recipe']) {
  check(`router:${redirect}-redirect`, router.includes(`path: '${redirect}'`) && router.includes('redirect:'))
}
check('router:auth-guard', hasAll(router, ['beforeEach', "localStorage.getItem('token')", 'hasMenu', 'firstAccessiblePath']))
check('router:preview-permission', hasAll(router, ['preview', 'setPreviewPermissions']))

check('request:api-base', request.includes("baseURL: '/api'"))
check('request:bearer-token', hasAll(request, ['Authorization', 'Bearer']))
check('request:401-handling', hasAll(request, ['code === 401', 'clearAuthState', "window.location.assign('/login')"]))
check('request:403-handling', request.includes('code === 403'))
check('auth:login-api', hasExport(authApi, 'login', '/v1/auth/login'))
check('dev-fallback:compile-gated', hasAll(devFallbackUtil, ['__DEV_MOCK_FALLBACK__', 'devFallback', 'warnDevFallback']))
check('vite:mock-fallback-define', hasAll(viteConfig, ['defineConfig(({ mode })', 'VITE_ENABLE_MOCK_FALLBACK', '__DEV_MOCK_FALLBACK__', "mode !== 'production'"]))

const requiredApiExports = [
  ['getOverview', '/v1/dashboard/overview'],
  ['getYieldDashboard', '/v1/dashboard/yield'],
  ['getOrders', '/v1/orders'],
  ['createOrder', '/v1/orders'],
  ['releaseOrder', '/v1/orders/${orderNo}/release'],
  ['getLots', '/v1/lots'],
  ['trackInLot', '/v1/lots/${lotNo}/track-in'],
  ['trackOutLot', '/v1/lots/${lotNo}/track-out'],
  ['holdLot', '/v1/lots/${lotNo}/hold'],
  ['releaseLot', '/v1/lots/${lotNo}/release'],
  ['reworkLot', '/v1/lots/${lotNo}/rework'],
  ['scrapLot', '/v1/lots/${lotNo}/scrap'],
  ['getProducts', '/v1/master/products'],
  ['getSites', '/v1/master/sites'],
  ['getProductionLines', '/v1/master/production-lines'],
  ['getShifts', '/v1/master/shifts'],
  ['getProcessSteps', '/v1/master/process-steps'],
  ['getEquipments', '/v1/master/equipments'],
  ['getDefectCodes', '/v1/master/defect-codes'],
  ['getRoutes', '/v1/routes'],
  ['getBoms', '/v1/boms'],
  ['getBomChangeRequests', '/v1/boms/change-requests'],
  ['submitBomChange', '/v1/boms/change-requests'],
  ['reviewBomChange', '/v1/boms/change-requests/${changeNo}/review'],
  ['publishBomChange', '/v1/boms/change-requests/${changeNo}/publish'],
  ['getRecipes', '/v1/recipes'],
  ['publishRecipe', '/v1/recipes/${id}/publish'],
  ['getTraceLot', '/v1/trace/lots/${lotNo}'],
  ['getTraceSn', '/v1/trace/sn/${sn}'],
  ['getQualityInspections', '/v1/quality/inspections'],
  ['getQualityExceptions', '/v1/quality/exceptions'],
  ['getQualityMrbRecords', '/v1/quality/exceptions/${eventNo}/mrb-records'],
  ['getQualityMrbMinutes', '/v1/quality/mrb-records/${mrbNo}/minutes'],
  ['createQualityMrbMinutes', '/v1/quality/mrb-records/${mrbNo}/minutes'],
  ['getQualityMrbApprovals', '/v1/quality/mrb-approvals'],
  ['refreshQualityMrbApprovalSla', '/v1/quality/mrb-approvals/refresh-sla'],
  ['approveQualityMrbTask', '/v1/quality/mrb-approvals/${taskNo}/approve'],
  ['rejectQualityMrbTask', '/v1/quality/mrb-approvals/${taskNo}/reject'],
  ['reviewQualityException', '/v1/quality/exceptions/${eventNo}/mrb-review'],
  ['closeQualityException', '/v1/quality/exceptions/${eventNo}/close'],
  ['getEquipmentEvents', '/v1/equipment/events'],
  ['createEquipmentEvent', '/v1/equipment/events'],
  ['closeEquipmentEvent', '/v1/equipment/events/${eventNo}/close'],
  ['getEquipmentOee', '/v1/equipment/oee'],
  ['getEquipmentStatusHistory', '/v1/equipment/status-history'],
  ['reportEquipmentStatus', '/v1/equipment/status/report'],
  ['getEquipmentCycleSamples', '/v1/equipment/cycle-samples'],
  ['reportEquipmentCycleSample', '/v1/equipment/cycle-samples/report'],
  ['getEquipmentStandardCycles', '/v1/equipment/standard-cycles'],
  ['publishEquipmentStandardCycle', '/v1/equipment/standard-cycles'],
  ['getEquipmentGateways', '/v1/equipment/gateways'],
  ['registerEquipmentGateway', '/v1/equipment/gateways'],
  ['heartbeatEquipmentGateway', '/v1/equipment/gateways/${gatewayCode}/heartbeat'],
  ['checkEquipmentGatewayHealth', '/v1/equipment/gateways/${gatewayCode}/health-check'],
  ['getEquipmentGatewayHealthChecks', '/v1/equipment/gateway-health-checks'],
  ['getEquipmentGatewayMessages', '/v1/equipment/gateway-messages'],
  ['getEquipmentGatewayDrivers', '/v1/equipment/gateway-drivers'],
  ['getEquipmentParameterSamples', '/v1/equipment/parameters'],
  ['reportEquipmentParameters', '/v1/equipment/parameters/report'],
  ['getEquipmentPmTasks', '/v1/equipment/pm-tasks'],
  ['completeEquipmentPmTask', '/v1/equipment/pm-tasks/${taskNo}/complete'],
  ['getEquipmentRecipeCommands', '/v1/equipment/recipe-downloads'],
  ['downloadEquipmentRecipe', '/v1/equipment/recipe-downloads'],
  ['ingestEapMessage', '/v1/adapters/eap/messages'],
  ['importErpOrders', '/v1/adapters/erp/orders'],
  ['getMaterialBatches', '/v1/material/batches'],
  ['receiveMaterial', '/v1/material/receive'],
  ['freezeMaterial', '/v1/material/batches/${batchNo}/freeze'],
  ['unfreezeMaterial', '/v1/material/batches/${batchNo}/unfreeze'],
  ['returnMaterial', '/v1/material/batches/${batchNo}/return'],
  ['countMaterialInventory', '/v1/material/batches/${batchNo}/inventory-count'],
  ['getMaterialInventoryTransactions', '/v1/material/inventory-transactions'],
  ['getMaterialIncomingInspections', '/v1/material/incoming-inspections'],
  ['createMaterialIncomingInspection', '/v1/material/batches/${batchNo}/incoming-inspection'],
  ['getMaterialConsumptions', '/v1/material/consumptions'],
  ['getMaterialSupplierPerformance', '/v1/material/suppliers/performance'],
  ['getMaterialSupplierTrends', '/v1/material/suppliers/trends'],
  ['getMaterialSuppliers', '/v1/material/suppliers'],
  ['evaluateMaterialSupplierQualification', '/v1/material/suppliers/${supplierCode}/qualification/evaluate'],
  ['getSupplierQualificationReviews', '/v1/material/suppliers/qualification-reviews'],
  ['createSupplierQualificationReview', '/v1/material/suppliers/${supplierCode}/qualification-reviews'],
  ['decideSupplierQualificationReview', '/v1/material/suppliers/qualification-reviews/${taskNo}/decision'],
  ['getSupplierCorrectiveActions', '/v1/material/suppliers/corrective-actions'],
  ['createSupplierCorrectiveAction', '/v1/material/suppliers/corrective-actions'],
  ['closeSupplierCorrectiveAction', '/v1/material/suppliers/corrective-actions/${actionNo}/close'],
  ['getMaterialLocations', '/v1/material/locations'],
  ['getMaterialLocationTasks', '/v1/material/location-tasks'],
  ['createMaterialLocationTask', '/v1/material/location-tasks'],
  ['assignMaterialLocationTask', '/v1/material/location-tasks/${taskNo}/assign'],
  ['completeMaterialLocationTask', '/v1/material/location-tasks/${taskNo}/complete'],
  ['cancelMaterialLocationTask', '/v1/material/location-tasks/${taskNo}/cancel'],
  ['getCarriers', '/v1/carriers'],
  ['createYieldReport', '/v1/ai/reports/yield'],
  ['analyzeEquipment', '/v1/ai/equipment/analyze'],
  ['askKnowledgeBase', '/v1/ai/kb/ask'],
  ['getAiModelConfigs', '/v1/ai/model-configs'],
  ['getAiReportRecords', '/v1/ai/report-records'],
  ['getKnowledgeDocuments', '/v1/ai/kb/documents'],
  ['importKnowledgeDocument', '/v1/ai/kb/import'],
  ['getKnowledgeIndexJobs', '/v1/ai/kb/index-jobs'],
  ['createKnowledgeIndexJob', '/v1/ai/kb/index-jobs'],
  ['getSystemSummary', '/v1/system/summary'],
  ['getSystemUsers', '/v1/system/users'],
  ['getAuditLogs', '/v1/system/audit-logs'],
  ['getPermissionChangeRequests', '/v1/system/permission-change-requests'],
  ['createPermissionChangeRequest', '/v1/system/permission-change-requests'],
  ['reviewPermissionChangeRequest', '/v1/system/permission-change-requests/${changeNo}/review'],
  ['reloadPermissions', '/v1/system/permissions/reload']
]

for (const [name, endpoint] of requiredApiExports) {
  check(`api:${name}`, hasExport(pilotApi, name, endpoint), endpoint)
}

const requiredRoles = ['ADMIN', 'PLANNER', 'OPERATOR', 'QE', 'PE', 'EE']
for (const role of requiredRoles) {
  check(`rbac:role:${role}`, permissions.includes(`${role}:`) || permissions.includes(`${role}`))
}

const requiredMenus = ['dashboard', 'order', 'execution', 'quality', 'material', 'trace', 'master', 'equipment', 'ai', 'system']
for (const menu of requiredMenus) {
  check(`rbac:menu:${menu}`, permissions.includes(`'${menu}'`))
}

const requiredButtons = [
  'order:create',
  'order:release',
  'lot:track-in',
  'lot:track-out',
  'lot:hold',
  'lot:release',
  'lot:rework',
  'lot:scrap',
  'quality:mrb-review',
  'quality:mrb-approve',
  'quality:mrb-escalate',
  'quality:exception-close',
  'material:wms',
  'material:iqc',
  'material:supplier-manage',
  'bom:change',
  'recipe:publish',
  'equipment:event-create',
  'equipment:eap-ingest',
  'equipment:eap-gateway',
  'ai:yield-report',
  'ai:equipment-analyze',
  'ai:kb-ask',
  'ai:kb-import',
  'ai:kb-index',
  'system:permission-change'
]

for (const button of requiredButtons) {
  check(`rbac:button:${button}`, permissions.includes(`'${button}'`))
}

const pageContracts = [
  ['views/overview/index.vue', ['getOverview'], []],
  ['views/order/index.vue', ['getOrders', 'getLots', 'releaseOrder'], ['order:create', 'order:release']],
  ['views/master/index.vue', ['getSites', 'getProductionLines', 'getShifts', 'getBoms', 'getBomChangeRequests', 'getRecipes', 'publishRecipe', 'publishBomChange'], ['recipe:publish', 'bom:change']],
  ['views/execution/index.vue', ['getLots', 'trackInLot', 'trackOutLot', 'holdLot'], ['lot:track-in', 'lot:track-out', 'lot:hold']],
  ['views/equipment/index.vue', ['getEquipments', 'getEquipmentEvents', 'createEquipmentEvent', 'ingestEapMessage', 'registerEquipmentGateway', 'checkEquipmentGatewayHealth'], ['equipment:event-create', 'equipment:eap-ingest', 'equipment:eap-gateway']],
  ['views/quality/index.vue', ['getQualityInspections', 'getQualityExceptions', 'getQualityMrbRecords', 'getQualityMrbApprovals', 'refreshQualityMrbApprovalSla', 'approveQualityMrbTask', 'rejectQualityMrbTask', 'reviewQualityException', 'closeQualityException'], ['quality:mrb-review', 'quality:mrb-approve', 'quality:mrb-escalate', 'quality:exception-close']],
  ['views/material/index.vue', ['getMaterialBatches', 'receiveMaterial', 'freezeMaterial', 'unfreezeMaterial', 'returnMaterial', 'countMaterialInventory', 'createMaterialIncomingInspection', 'getMaterialSupplierPerformance', 'getMaterialSupplierTrends', 'getMaterialSuppliers', 'evaluateMaterialSupplierQualification', 'getSupplierQualificationReviews', 'createSupplierQualificationReview', 'decideSupplierQualificationReview', 'getSupplierCorrectiveActions', 'createSupplierCorrectiveAction', 'closeSupplierCorrectiveAction', 'getMaterialLocations', 'getMaterialLocationTasks', 'createMaterialLocationTask', 'assignMaterialLocationTask', 'completeMaterialLocationTask', 'cancelMaterialLocationTask', 'getCarriers'], ['material:wms', 'material:iqc', 'material:supplier-manage']],
  ['views/trace/index.vue', ['getTraceLot'], []],
  ['views/ai/index.vue', ['getYieldDashboard', 'createYieldReport', 'askKnowledgeBase', 'getAiModelConfigs', 'getAiReportRecords', 'getKnowledgeDocuments', 'importKnowledgeDocument', 'createKnowledgeIndexJob'], ['ai:yield-report', 'ai:kb-ask', 'ai:kb-import', 'ai:kb-index']],
  ['views/system/index.vue', ['getAuditLogs', 'getSystemUsers', 'getPermissionChangeRequests', 'createPermissionChangeRequest', 'reviewPermissionChangeRequest', 'reloadPermissions'], ['system:permission-change']]
]

for (const [relativePath, apiNames, buttonKeys] of pageContracts) {
  const source = read(`src/${relativePath}`)
  for (const apiName of apiNames) {
    check(`page:${relativePath}:api:${apiName}`, source.includes(apiName), apiName)
  }
  for (const button of buttonKeys) {
    check(`page:${relativePath}:button:${button}`, source.includes(`hasButton('${button}')`), button)
  }
  if (source.includes('fallback')) {
    check(`page:${relativePath}:dev-fallback-import`, source.includes("@/utils/devFallback"), 'fallback data must be gated by src/utils/devFallback.js')
    check(`page:${relativePath}:dev-fallback-guard`, source.includes('__DEV_MOCK_FALLBACK__') || source.includes('devFallback(') || source.includes('isDevFallbackEnabled()'), 'fallback data must not be unconditional')
    check(`page:${relativePath}:no-direct-fallback-ref`, !/ref\s*\(\s*fallback[A-Za-z0-9_]/.test(source), 'initial fallback refs must use compile-time fallback guard')
    check(`page:${relativePath}:no-raw-fallback-warning`, !source.includes('使用开发 fallback 数据'), 'use warnDevFallback(...) instead of raw console fallback warnings')
  }
}

const masterView = read('src/views/master/index.vue')
check('page:views/master/index.vue:bom-change-validation-file', masterView.includes('validationFileName') && masterView.includes('validationFileHash'), 'BOM change submit must carry substitute validation attachment metadata')
check('page:views/master/index.vue:bom-change-attachment-count', masterView.includes('attachmentCount'), 'BOM change list must show validation attachment count')

const qualityView = read('src/views/quality/index.vue')
check('page:views/quality/index.vue:mrb-scrap-action', qualityView.includes("handleReview(item, 'SCRAP')"), 'quality MRB queue must expose SCRAP disposition action')
check('page:views/quality/index.vue:mrb-review-close-permission-split', hasAll(qualityView, ['canReviewAction', 'canCloseAction', "hasButton('quality:mrb-review')", "hasButton('quality:exception-close')"]), 'MRB review and close actions must use separate button permissions')

const systemView = read('src/views/system/index.vue')
check('page:views/system/index.vue:permission-diff', hasAll(systemView, ['comparePermissionChange', 'permissionDiffRows', 'beforeSnapshot', 'afterSnapshot']), 'System permission changes must expose before/after diff')
check('page:views/system/index.vue:permission-reject', hasAll(systemView, ['rejectPermissionChange', "decision: 'REJECT'"]), 'System permission changes must support reject decision')

const lotView = read('src/views/lot/index.vue')
const executionView = read('src/views/execution/index.vue')
check('page:views/lot/index.vue:track-in-rework-status', lotView.includes("['READY', 'REWORK'].includes(row.status)"), 'Lot page Track In action must allow rework lots')
check('page:views/execution/index.vue:track-in-rework-status', executionView.includes("['READY', 'REWORK'].includes(lot.status)"), 'Execution page Track In action must allow rework lots')

check('package:verify-script', packageJson.scripts?.['verify:frontend-contract'] === 'node scripts/verify-frontend-contract.mjs')

if (failures.length) {
  console.error(`Frontend contract failed: ${failures.length} failed, ${passes.length} passed`)
  for (const failure of failures) {
    console.error(`- ${failure.name}${failure.detail ? ` :: ${failure.detail}` : ''}`)
  }
  process.exit(1)
}

console.log(`Frontend contract passed: ${passes.length} checks`)
