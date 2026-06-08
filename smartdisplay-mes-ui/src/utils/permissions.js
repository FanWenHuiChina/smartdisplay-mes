const ALL_MENUS = ['dashboard', 'order', 'execution', 'quality', 'material', 'trace', 'master', 'recipe', 'equipment', 'ai', 'system']
const ALL_BUTTONS = [
  'order:create', 'order:release',
  'lot:track-in', 'lot:track-out', 'lot:hold', 'lot:release', 'lot:rework', 'lot:scrap',
  'quality:mrb-review', 'quality:mrb-approve', 'quality:mrb-escalate', 'quality:exception-close',
  'material:wms', 'material:iqc', 'material:supplier-manage',
  'bom:change', 'bom:eco-approve',
  'recipe:publish', 'equipment:event-create', 'equipment:eap-ingest', 'equipment:eap-gateway',
  'ai:yield-report', 'ai:equipment-analyze', 'ai:kb-ask', 'ai:kb-import', 'ai:kb-index',
  'system:user-read', 'system:audit-read', 'system:permission-change'
]

const ROLE_PERMISSIONS = {
  ADMIN: { menus: ALL_MENUS, buttons: ALL_BUTTONS, dataScope: 'ALL' },
  PREVIEW: { menus: ALL_MENUS, buttons: ALL_BUTTONS, dataScope: 'ALL' },
  PLANNER: { menus: ['dashboard', 'order', 'trace'], buttons: ['order:create', 'order:release', 'bom:eco-approve'], dataScope: 'LINE' },
  OPERATOR: { menus: ['dashboard', 'execution', 'trace'], buttons: ['lot:track-in', 'lot:track-out'], dataScope: 'SELF_SHIFT' },
  QE: {
    menus: ['dashboard', 'quality', 'material', 'trace', 'ai'],
    buttons: ['lot:hold', 'lot:release', 'lot:rework', 'lot:scrap', 'quality:mrb-review', 'quality:mrb-approve', 'quality:mrb-escalate', 'quality:exception-close', 'material:iqc', 'material:supplier-manage', 'bom:eco-approve', 'ai:yield-report', 'ai:kb-ask', 'ai:kb-import', 'ai:kb-index'],
    dataScope: 'LINE'
  },
  PE: { menus: ['dashboard', 'master', 'recipe', 'quality', 'ai'], buttons: ['quality:mrb-approve', 'quality:mrb-escalate', 'recipe:publish', 'bom:change', 'bom:eco-approve', 'ai:yield-report', 'ai:kb-ask', 'ai:kb-import', 'ai:kb-index'], dataScope: 'LINE' },
  EE: { menus: ['dashboard', 'equipment', 'quality', 'trace', 'ai'], buttons: ['quality:mrb-approve', 'quality:mrb-escalate', 'bom:eco-approve', 'equipment:event-create', 'equipment:eap-ingest', 'equipment:eap-gateway', 'ai:equipment-analyze', 'ai:kb-ask', 'ai:kb-import', 'ai:kb-index'], dataScope: 'LINE' }
}

const PATH_MENU = {
  '/overview': 'dashboard',
  '/dashboard': 'dashboard',
  '/order': 'order',
  '/execution': 'execution',
  '/lot': 'execution',
  '/quality': 'quality',
  '/material': 'material',
  '/trace': 'trace',
  '/master': 'master',
  '/recipe': 'recipe',
  '/equipment': 'equipment',
  '/ai': 'ai',
  '/system': 'system'
}

const MENU_PATH = {
  dashboard: '/overview',
  order: '/order',
  execution: '/execution',
  quality: '/quality',
  material: '/material',
  trace: '/trace',
  master: '/master',
  recipe: '/master',
  equipment: '/equipment',
  ai: '/ai',
  system: '/system'
}

function safeJson(value) {
  try {
    return JSON.parse(value || '{}')
  } catch (error) {
    return {}
  }
}

export function normalizeRole(role) {
  const value = String(role || 'OPERATOR').trim().toUpperCase()
  if (value === 'ENGINEER' || value === 'PROCESS_ENGINEER') return 'PE'
  if (value === 'QUALITY_ENGINEER') return 'QE'
  if (value === 'EQUIPMENT_ENGINEER') return 'EE'
  return value
}

export function permissionsForRole(role) {
  return ROLE_PERMISSIONS[normalizeRole(role)] || ROLE_PERMISSIONS.OPERATOR
}

export function currentPermissions() {
  const stored = safeJson(localStorage.getItem('permissions'))
  const fallback = permissionsForRole(localStorage.getItem('role'))
  return {
    role: normalizeRole(stored.role || localStorage.getItem('role')),
    menus: Array.isArray(stored.menus) && stored.menus.length ? stored.menus : fallback.menus,
    buttons: Array.isArray(stored.buttons) && stored.buttons.length ? stored.buttons : fallback.buttons,
    dataScope: stored.dataScope || fallback.dataScope,
    dataScopeSql: stored.dataScopeSql || null
  }
}

export function setPreviewPermissions() {
  localStorage.setItem('permissions', JSON.stringify({
    role: 'PREVIEW',
    menus: ALL_MENUS,
    buttons: ALL_BUTTONS,
    dataScope: 'ALL'
  }))
}

export function hasMenu(menuKey) {
  if (!menuKey) return true
  const permissions = currentPermissions()
  return permissions.role === 'ADMIN' || permissions.menus.includes(menuKey)
}

export function hasButton(buttonKey) {
  if (!buttonKey) return true
  const permissions = currentPermissions()
  return permissions.role === 'ADMIN' || permissions.buttons.includes(buttonKey)
}

export function menuForPath(path) {
  const normalizedPath = path === '/' ? '/overview' : path
  const candidate = Object.keys(PATH_MENU)
    .sort((left, right) => right.length - left.length)
    .find(prefix => normalizedPath.startsWith(prefix))
  return candidate ? PATH_MENU[candidate] : ''
}

export function firstAccessiblePath() {
  const permissions = currentPermissions()
  const firstMenu = ['dashboard', 'order', 'execution', 'quality', 'material', 'trace', 'master', 'equipment', 'ai', 'system']
    .find(menu => permissions.menus.includes(menu))
  return MENU_PATH[firstMenu] || '/overview'
}
