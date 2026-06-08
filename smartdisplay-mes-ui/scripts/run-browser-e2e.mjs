import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { get } from 'node:http'
import { tmpdir } from 'node:os'
import { dirname, resolve } from 'node:path'

const baseUrl = (process.env.E2E_BASE_URL || 'http://127.0.0.1:8888').replace(/\/$/, '')
const username = process.env.E2E_USERNAME || 'admin'
const password = process.env.E2E_PASSWORD || '123456'
const reportDir = resolve(process.env.E2E_REPORT_DIR || '../docs')
const timestamp = formatTimestamp(new Date())
const reportJsonPath = resolve(reportDir, `SmartDisplay-MES-browser-e2e-${timestamp}.json`)
const reportMdPath = resolve(reportDir, `SmartDisplay-MES-browser-e2e-${timestamp}.md`)

const steps = []
const consoleErrors = []
const networkErrors = []
const browserMessages = []
let chromeProcess
let userDataDir
let client
let e2eOrderNo
let e2eLotNo

main().catch(async error => {
  steps.push({
    name: 'browser-e2e-fatal',
    status: 'FAIL',
    detail: error.stack || error.message || String(error),
    durationMs: 0
  })
  await writeReports('FAIL')
  await cleanup()
  console.error(error.stack || error.message || error)
  process.exit(1)
})

async function main() {
  ensureChrome()
  mkdirSync(reportDir, { recursive: true })
  await launchChrome()
  const pageWs = await getPageWebSocket()
  client = await CdpClient.connect(pageWs)
  client.on('Runtime.consoleAPICalled', event => {
    if (event.type === 'error') {
      consoleErrors.push((event.args || []).map(arg => arg.value || arg.description || '').join(' '))
    }
  })
  client.on('Runtime.exceptionThrown', event => {
    consoleErrors.push(event.exceptionDetails?.text || 'Runtime exception')
  })
  client.on('Network.loadingFailed', event => {
    if (event.type === 'Document' && event.errorText === 'net::ERR_ABORTED') return
    networkErrors.push(`${event.type || 'request'} ${event.errorText || 'failed'}`)
  })
  client.on('Network.responseReceived', event => {
    const status = event.response?.status || 0
    const url = event.response?.url || ''
    if (status >= 400 && !url.endsWith('/favicon.ico')) {
      networkErrors.push(`${status} ${url}`)
    }
  })

  await client.send('Page.enable')
  await client.send('Runtime.enable')
  await client.send('Network.enable')

  await runStep('登录并进入工作台', async () => {
    await navigate(`${baseUrl}/login`)
    await evaluate('localStorage.clear()')
    await navigate(`${baseUrl}/login`)
    await waitForText('SmartDisplay MES')
    await setInputByPlaceholder('用户名', username)
    await setInputByPlaceholder('密码', password)
    await clickByText('登录')
    await waitForExpression(`location.pathname === '/overview' && document.body.innerText.includes('生产总览')`)
    const auth = await evaluate(`({
      token: Boolean(localStorage.getItem('token')),
      role: localStorage.getItem('role'),
      menus: JSON.parse(localStorage.getItem('permissions') || '{}').menus || []
    })`)
    assert(auth.token, '登录后未写入 token')
    assert(Array.isArray(auth.menus) && auth.menus.includes('dashboard'), '登录权限缺少 dashboard 菜单')
    return `role=${auth.role}, menus=${auth.menus.length}`
  })

  await runStep('顶部导航和总览页面可用', async () => {
    await assertCurrentPage('生产总览')
    await assertLayoutClean('overview')
    const text = await bodyText()
    assert(text.includes('设备 OEE') || text.includes('WIP'), '总览页面缺少关键看板内容')
    return 'overview dashboard visible'
  })

  await runStep('计划与工单页面可导航并显示释放入口', async () => {
    e2eOrderNo = `MOE2E${timestamp.replace(/\D/g, '')}`
    await evaluate(`(async () => {
      const token = localStorage.getItem('token')
      const response = await fetch('/api/v1/orders', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
        body: JSON.stringify({
          orderNo: '${escapeJs(e2eOrderNo)}',
          productCode: 'AMOLED_65',
          productName: 'AMOLED 6.5寸柔性屏',
          plannedQty: 100,
          priority: 9,
          lineCode: 'LINE_01'
        })
      })
      const json = await response.json()
      if (json.code !== 200) throw new Error(json.message)
      return json.data.orderNo
    })()`)
    await clickByText('计划与工单')
    await waitForExpression(`location.pathname === '/order' && document.body.innerText.includes('计划与工单 / 工单释放')`)
    await assertLayoutClean('order')
    assert(await textExists(e2eOrderNo), `计划与工单页面未显示 E2E 工单: ${e2eOrderNo}`)
    assert(await textExists('释放工单'), '计划与工单页面未显示释放工单入口')
    await clickByText('释放工单')
    await waitForExpression(`(async () => {
      const token = localStorage.getItem('token')
      const response = await fetch('/api/v1/orders?current=1&size=50', {
        headers: { Authorization: 'Bearer ' + token }
      })
      const json = await response.json()
      return json.data.records.some(order => order.orderNo === '${escapeJs(e2eOrderNo)}' && order.status === 'RELEASED')
    })()`, 15000)
    e2eLotNo = await evaluate(`(async () => {
      const token = localStorage.getItem('token')
      const lotPrefix = '${escapeJs(e2eOrderNo)}'.replace('MO', 'LOT')
      const response = await fetch('/api/v1/lots?current=1&size=20&lotNo=' + encodeURIComponent(lotPrefix), {
        headers: { Authorization: 'Bearer ' + token }
      })
      const json = await response.json()
      return json.data.records[0]?.lotNo || ''
    })()`)
    assert(e2eLotNo, '工单释放后未查询到生成的 E2E Lot')
    return `order=${e2eOrderNo} released, lot=${e2eLotNo}`
  })

  await runStep('生产执行页面通过 UI 完成 Track In/Out', async () => {
    assert(e2eLotNo, '缺少 E2E Lot，无法执行 Track In/Out')
    await clickByText('生产执行')
    await waitForExpression(`location.pathname === '/execution' && document.body.innerText.includes('生产执行 / 电子流程卡')`)
    await assertLayoutClean('execution')
    assert(await textExists('Track In'), '执行页面缺少 Track In')
    assert(await textExists('Track Out'), '执行页面缺少 Track Out')
    await waitForExpression(`document.body.innerText.includes('${escapeJs(e2eLotNo)}')`, 10000)
    await clickTableRowByText(e2eLotNo)
    await clickButtonByText('Track In')
    await waitForExpression(`(async () => {
      const token = localStorage.getItem('token')
      const response = await fetch('/api/v1/lots?current=1&size=10&lotNo=' + encodeURIComponent('${escapeJs(e2eLotNo)}'), {
        headers: { Authorization: 'Bearer ' + token }
      })
      const json = await response.json()
      return json.data.records[0]?.status === 'PROCESSING'
    })()`, 15000)
    await waitForExpression(`Array.from(document.querySelectorAll('tbody tr')).some(row => {
      const text = row.innerText || ''
      return text.includes('${escapeJs(e2eLotNo)}') && text.includes('PROCESSING')
    })`, 15000)
    await clickTableRowByText(e2eLotNo)
    await clickButtonByText('Track Out')
    const lotState = await waitForLotState(`status === 'READY' && currentStepCode !== 'CLEAN'`, 15000)
    return `lot=${e2eLotNo}, status=${lotState.status}, step=${lotState.currentStepCode}`
  })

  await runStep('质量管理页面显示 MRB 和缺陷证据', async () => {
    await clickByText('质量管理')
    await waitForExpression(`location.pathname === '/quality' && document.body.innerText.includes('质量管理 / SPC') && document.body.innerText.includes('缺陷 TopN')`)
    await assertLayoutClean('quality')
    assert(await textExists('缺陷 TopN'), '质量页面缺少缺陷 TopN')
    assert(await textExists('会签待办'), '质量页面缺少 MRB 会签待办')
    return 'quality mrb view visible'
  })

  await runStep('质量页面通过 UI 提交 QMS Adapter OK 上报', async () => {
    assert(e2eLotNo, '缺少 E2E Lot，无法执行 QMS Adapter 上报')
    const qmsItemCode = `QMS_E2E_${timestamp.replace(/\D/g, '')}`
    await setFieldValueByLabel('Lot', e2eLotNo)
    await setSelectValueByLabel('检验结果', 'OK')
    await setFieldValueByLabel('检验项', qmsItemCode)
    await setFieldValueByLabel('检验名称', 'E2E外观检查')
    await setFieldValueByLabel('测量值', '1')
    await setFieldValueByLabel('下限', '0')
    await setFieldValueByLabel('上限', '2')
    await setFieldValueByLabel('单位', 'EA')
    await setFieldValueByLabel('操作人', username)
    await setFieldValueByLabel('备注', 'browser e2e qms adapter ok')
    await clickButtonByText('提交 QMS 上报')
    await waitForExpression(`document.body.innerText.includes('OK /')`, 15000)
    await waitForExpression(`(async () => {
      const token = localStorage.getItem('token')
      const response = await fetch('/api/v1/quality/inspections?lotNo=' + encodeURIComponent('${escapeJs(e2eLotNo)}'), {
        headers: { Authorization: 'Bearer ' + token }
      })
      const json = await response.json()
      return json.data.some(item => item.itemCode === '${escapeJs(qmsItemCode)}' && item.result === 'OK')
    })()`, 15000)
    await assertLayoutClean('quality')
    return `qms adapter ok item=${qmsItemCode}, lot=${e2eLotNo}`
  })

  await runStep('物料页面显示 V1.38 库位任务操作台', async () => {
    await clickByText('物料与载具')
    await waitForExpression(`location.pathname === '/material' && document.body.innerText.includes('库位任务 / 上架移库盘点')`)
    await evaluate(`(async () => {
      const token = localStorage.getItem('token')
      const response = await fetch('/api/v1/material/location-tasks', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
        body: JSON.stringify({
          taskType: 'COUNT',
          batchNo: 'PI260606-A',
          actualQty: 820,
          reason: 'browser e2e visible task',
          operator: '${escapeJs(username)}'
        })
      })
      const json = await response.json()
      if (json.code !== 200) throw new Error(json.message)
      return json.data.task.taskNo
    })()`)
    await navigate(`${baseUrl}/material`)
    await waitForExpression(`location.pathname === '/material' && document.body.innerText.includes('库位任务 / 上架移库盘点')`)
    await assertLayoutClean('material')
    assert(await textExists('创建'), '物料页面缺少库位任务创建入口')
    await waitForExpression(`document.body.innerText.includes('领取') || document.body.innerText.includes('取消')`, 10000)
    return 'material location task workbench visible'
  })

  await runStep('物料页面通过 UI 调用 WMS Adapter 齐套与入库事务', async () => {
    const wmsBatchNo = `WMSE2E${timestamp.replace(/\D/g, '')}`
    await clickButtonByText('Adapter 齐套')
    await waitForExpression(`document.body.innerText.includes('齐套 PASS') || document.body.innerText.includes('齐套 PASS_WITH_WARNING') || document.body.innerText.includes('齐套 BLOCKED')`, 15000)
    await clickByText('入库')
    await waitForExpression(`document.body.innerText.includes('新批次入库后生成库存事务和审计记录')`, 10000)
    await setFieldValueByLabel('批次号', wmsBatchNo)
    await setFieldValueByLabel('物料编码', 'E2E_MAT')
    await setFieldValueByLabel('物料名称', 'E2E测试物料')
    await setFieldValueByLabel('数量', '1')
    await setFieldValueByLabel('单位', 'EA')
    await setFieldValueByLabel('原因', 'browser e2e wms adapter receive')
    await setFieldValueByLabel('操作员', username)
    await clickButtonByText('Adapter 事务')
    await waitForExpression(`document.body.innerText.includes('RECEIVE ACCEPTED')`, 15000)
    await waitForExpression(`(async () => {
      const token = localStorage.getItem('token')
      const response = await fetch('/api/v1/material/batches', {
        headers: { Authorization: 'Bearer ' + token }
      })
      const json = await response.json()
      return json.data.batches.some(batch => batch.batchNo === '${escapeJs(wmsBatchNo)}')
    })()`, 15000)
    await assertLayoutClean('material')
    return `wms adapter readiness and receive batch=${wmsBatchNo}`
  })

  let workflowResult
  await runStep('浏览器会话验证 V1.38 库位任务状态流', async () => {
    workflowResult = await evaluate(`(async () => {
      const token = localStorage.getItem('token')
      const headers = { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token }
      const request = async (path, body) => {
        const response = await fetch('/api/v1' + path, {
          method: 'POST',
          headers,
          body: JSON.stringify(body)
        })
        const json = await response.json()
        if (json.code !== 200) throw new Error(path + ' failed: ' + json.message)
        return json.data
      }
      const payload = {
        taskType: 'COUNT',
        batchNo: 'PI260606-A',
        actualQty: 820,
        reason: 'browser e2e ' + Date.now(),
        operator: '${escapeJs(username)}'
      }
      const created = await request('/material/location-tasks', payload)
      const taskNo = created.task.taskNo
      const assigned = await request('/material/location-tasks/' + taskNo + '/assign', {
        assignedTo: '${escapeJs(username)}',
        operator: '${escapeJs(username)}'
      })
      const completed = await request('/material/location-tasks/' + taskNo + '/complete', {
        operator: '${escapeJs(username)}',
        actualQty: 820,
        reviewer: '${escapeJs(username)}'
      })
      const createdForCancel = await request('/material/location-tasks', payload)
      const cancelTaskNo = createdForCancel.task.taskNo
      const cancelled = await request('/material/location-tasks/' + cancelTaskNo + '/cancel', {
        operator: '${escapeJs(username)}',
        cancelReason: 'browser e2e cancel'
      })
      return {
        created: created.task.status,
        assigned: assigned.task.status,
        completed: completed.task.status,
        cancelled: cancelled.task.status,
        completedTask: taskNo,
        cancelledTask: cancelTaskNo
      }
    })()`)
    assert(workflowResult.created === 'CREATED', '库位任务创建状态不是 CREATED')
    assert(workflowResult.assigned === 'ASSIGNED', '库位任务领取状态不是 ASSIGNED')
    assert(workflowResult.completed === 'DONE', '库位任务完成状态不是 DONE')
    assert(workflowResult.cancelled === 'CANCELLED', '库位任务取消状态不是 CANCELLED')
    return `${workflowResult.completedTask}: ${workflowResult.created}->${workflowResult.assigned}->${workflowResult.completed}; ${workflowResult.cancelledTask}: CANCELLED`
  })

  await runStep('追溯页面完成 Lot 查询', async () => {
    const lotNo = await evaluate(`(async () => {
      const token = localStorage.getItem('token')
      const response = await fetch('/api/v1/lots?current=1&size=1', {
        headers: { Authorization: 'Bearer ' + token }
      })
      const json = await response.json()
      return json.data.records[0].lotNo
    })()`)
    await clickByText('追溯分析')
    await waitForExpression(`location.pathname === '/trace' && document.body.innerText.includes('追溯分析 / 多入口')`)
    await setTraceInput(lotNo)
    await clickByText('查询追溯')
    await waitForExpression(`document.body.innerText.includes(${JSON.stringify(lotNo)}) || document.body.innerText.includes('工艺履历时间线')`)
    await assertLayoutClean('trace')
    return `trace lot=${lotNo}`
  })

  await runStep('AI 页面生成良率日报并保留留痕入口', async () => {
    await clickByText('报表与AI')
    await waitForExpression(`location.pathname === '/ai' && document.body.innerText.includes('AI 良率日报')`)
    await assertLayoutClean('ai')
    await clickByText('生成 AI 报告')
    await waitForExpression(`document.body.innerText.includes('AI 报告留痕') && document.body.innerText.includes('YIELD')`, 15000)
    assert(await textExists('AI 报告留痕'), 'AI 页面缺少报告留痕')
    return 'yield report generated and record table visible'
  })

  await runStep('系统页面和审计入口可访问', async () => {
    await clickByText('系统管理')
    await waitForExpression(`location.pathname === '/system' && document.body.innerText.includes('系统管理')`)
    await assertLayoutClean('system')
    assert(await textExists('审计') || await textExists('权限'), '系统页面缺少审计或权限内容')
    return 'system audit view visible'
  })

  const status = consoleErrors.length || networkErrors.length ? 'FAIL' : 'PASS'
  if (status === 'FAIL') {
    steps.push({
      name: '浏览器运行时错误检查',
      status: 'FAIL',
      detail: `console=${consoleErrors.length}, network=${networkErrors.length}`,
      durationMs: 0
    })
  }

  await writeReports(status)
  await cleanup()

  if (status !== 'PASS') {
    process.exit(1)
  }
  console.log(`Browser E2E passed: ${steps.filter(step => step.status === 'PASS').length} steps`)
  console.log(`Report: ${reportMdPath}`)
}

async function runStep(name, fn) {
  const started = Date.now()
  try {
    const detail = await fn()
    steps.push({ name, status: 'PASS', detail: detail || 'OK', durationMs: Date.now() - started })
  } catch (error) {
    steps.push({ name, status: 'FAIL', detail: error.message || String(error), durationMs: Date.now() - started })
    throw error
  }
}

async function navigate(url) {
  const loaded = waitEvent('Page.loadEventFired', 15000)
  await client.send('Page.navigate', { url })
  await loaded
}

async function setInputByPlaceholder(placeholder, value) {
  const ok = await evaluate(`(() => {
    const input = Array.from(document.querySelectorAll('input')).find(el => el.placeholder === ${JSON.stringify(placeholder)})
    if (!input) return false
    input.focus()
    input.value = ${JSON.stringify(value)}
    input.dispatchEvent(new Event('input', { bubbles: true }))
    input.dispatchEvent(new Event('change', { bubbles: true }))
    return true
  })()`)
  assert(ok, `未找到输入框: ${placeholder}`)
}

async function setTraceInput(value) {
  const ok = await evaluate(`(() => {
    const input = Array.from(document.querySelectorAll('.mes-field input')).find(el => {
      const label = el.closest('.mes-field')?.innerText || ''
      return label.includes('关键字') || label.includes('Lot / SN')
    })
    if (!input) return false
    input.focus()
    input.value = ${JSON.stringify(value)}
    input.dispatchEvent(new Event('input', { bubbles: true }))
    input.dispatchEvent(new Event('change', { bubbles: true }))
    return true
  })()`)
  assert(ok, '未找到追溯查询输入框')
}

async function setFieldValueByLabel(labelText, value) {
  const ok = await evaluate(`(() => {
    const visible = (el) => {
      const rect = el.getBoundingClientRect()
      const style = window.getComputedStyle(el)
      return rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none'
    }
    const field = Array.from(document.querySelectorAll('.mes-field')).find(el => {
      const label = (el.querySelector('label')?.innerText || '').trim()
      const input = el.querySelector('input, textarea')
      return label === ${JSON.stringify(labelText)} && input && visible(input) && !input.disabled
    })
    const input = field?.querySelector('input, textarea')
    if (!input) return false
    input.focus()
    input.value = ${JSON.stringify(value)}
    input.dispatchEvent(new Event('input', { bubbles: true }))
    input.dispatchEvent(new Event('change', { bubbles: true }))
    return true
  })()`)
  assert(ok, `未找到可输入字段: ${labelText}`)
}

async function setSelectValueByLabel(labelText, value) {
  const ok = await evaluate(`(() => {
    const visible = (el) => {
      const rect = el.getBoundingClientRect()
      const style = window.getComputedStyle(el)
      return rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none'
    }
    const field = Array.from(document.querySelectorAll('.mes-field')).find(el => {
      const label = (el.querySelector('label')?.innerText || '').trim()
      const select = el.querySelector('select')
      return label === ${JSON.stringify(labelText)} && select && visible(select) && !select.disabled
    })
    const select = field?.querySelector('select')
    if (!select) return false
    select.focus()
    select.value = ${JSON.stringify(value)}
    select.dispatchEvent(new Event('input', { bubbles: true }))
    select.dispatchEvent(new Event('change', { bubbles: true }))
    return true
  })()`)
  assert(ok, `未找到可选择字段: ${labelText}`)
}

async function clickByText(text) {
  const ok = await evaluate(`(() => {
    const visible = (el) => {
      const rect = el.getBoundingClientRect()
      const style = window.getComputedStyle(el)
      return rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none'
    }
    const elements = Array.from(document.querySelectorAll('button,a,[role="button"]'))
    const target = elements.find(el => visible(el) && (el.innerText || el.textContent || '').trim().includes(${JSON.stringify(text)}))
    if (!target) return false
    target.click()
    return true
  })()`)
  assert(ok, `未找到可点击文本: ${text}`)
}

async function clickButtonByText(text) {
  const ok = await evaluate(`(() => {
    const visible = (el) => {
      const rect = el.getBoundingClientRect()
      const style = window.getComputedStyle(el)
      return rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none'
    }
    const buttons = Array.from(document.querySelectorAll('button,[role="button"]'))
    const target = buttons.find(el => visible(el) && (el.innerText || el.textContent || '').trim().includes(${JSON.stringify(text)}))
    if (!target) return false
    target.click()
    return true
  })()`)
  assert(ok, `未找到可点击按钮: ${text}`)
}

async function clickTableRowByText(text) {
  const ok = await evaluate(`(() => {
    const rows = Array.from(document.querySelectorAll('tbody tr'))
    const target = rows.find(row => (row.innerText || row.textContent || '').includes(${JSON.stringify(text)}))
    if (!target) return false
    target.click()
    return true
  })()`)
  assert(ok, `未找到可点击表格行: ${text}`)
}

async function assertCurrentPage(text) {
  assert(await textExists(text), `页面缺少文本: ${text}`)
}

async function assertLayoutClean(page) {
  const result = await evaluate(`(() => {
    const horizontalOverflow = document.documentElement.scrollWidth > document.documentElement.clientWidth + 2
    const overflowButtons = Array.from(document.querySelectorAll('button')).filter(button => button.scrollWidth > button.clientWidth + 2).map(button => (button.innerText || button.textContent || '').trim()).filter(Boolean)
    const clippedText = Array.from(document.querySelectorAll('h1,.page-title,.mes-card__title,.side-link,.mes-tab')).filter(el => el.scrollWidth > el.clientWidth + 2 && window.getComputedStyle(el).overflow === 'hidden').map(el => (el.innerText || el.textContent || '').trim()).filter(Boolean)
    return { page: ${JSON.stringify(page)}, horizontalOverflow, overflowButtons, clippedText }
  })()`)
  assert(!result.horizontalOverflow, `${page} 存在横向溢出`)
  assert(result.overflowButtons.length === 0, `${page} 按钮文字溢出: ${result.overflowButtons.join(', ')}`)
  assert(result.clippedText.length === 0, `${page} 文本裁切: ${result.clippedText.join(', ')}`)
  return result
}

async function textExists(text) {
  return evaluate(`document.body.innerText.includes(${JSON.stringify(text)})`)
}

async function waitForText(text, timeoutMs = 10000) {
  await waitForExpression(`document.body && document.body.innerText.includes(${JSON.stringify(text)})`, timeoutMs)
}

async function waitForExpression(expression, timeoutMs = 10000) {
  const started = Date.now()
  let lastError = ''
  while (Date.now() - started < timeoutMs) {
    try {
      const value = await evaluate(`(async () => Boolean(await (${expression})))()`)
      if (value) return
    } catch (error) {
      lastError = error.message
    }
    await delay(200)
  }
  throw new Error(`等待条件超时: ${expression}${lastError ? ` (${lastError})` : ''}`)
}

async function waitForLotState(condition, timeoutMs = 10000) {
  const started = Date.now()
  let lastState = null
  while (Date.now() - started < timeoutMs) {
    lastState = await evaluate(`(async () => {
      const token = localStorage.getItem('token')
      const response = await fetch('/api/v1/lots?current=1&size=10&lotNo=' + encodeURIComponent('${escapeJs(e2eLotNo)}'), {
        headers: { Authorization: 'Bearer ' + token }
      })
      const json = await response.json()
      return json.data.records[0] || null
    })()`)
    if (lastState) {
      const matched = Function('state', `with (state) { return ${condition}; }`)(lastState)
      if (matched) return lastState
    }
    await delay(200)
  }
  throw new Error(`等待 Lot 状态超时: ${condition}, last=${JSON.stringify(lastState)}`)
}

async function bodyText() {
  return evaluate('document.body.innerText')
}

async function evaluate(expression) {
  const response = await client.send('Runtime.evaluate', {
    expression,
    awaitPromise: true,
    returnByValue: true
  })
  if (response.exceptionDetails) {
    throw new Error(response.exceptionDetails.exception?.description || response.exceptionDetails.text || 'Runtime.evaluate failed')
  }
  return response.result?.value
}

function waitEvent(method, timeoutMs = 10000) {
  return new Promise((resolveEvent, rejectEvent) => {
    const timeout = setTimeout(() => {
      cleanupListener()
      rejectEvent(new Error(`等待事件超时: ${method}`))
    }, timeoutMs)
    const cleanupListener = client.once(method, event => {
      clearTimeout(timeout)
      resolveEvent(event)
    })
  })
}

async function launchChrome() {
  const chrome = ensureChrome()
  const port = Number(process.env.E2E_REMOTE_DEBUGGING_PORT || 9224)
  userDataDir = resolve(tmpdir(), `smartdisplay-mes-browser-e2e-${Date.now()}`)
  const args = [
    `--remote-debugging-port=${port}`,
    `--user-data-dir=${userDataDir}`,
    '--headless=new',
    '--disable-gpu',
    '--disable-gpu-compositing',
    '--disable-software-rasterizer',
    '--disable-accelerated-2d-canvas',
    '--disable-features=UseSkiaRenderer,VizDisplayCompositor,CanvasOopRasterization',
    '--disable-extensions',
    '--disable-component-extensions-with-background-pages',
    '--disable-dev-shm-usage',
    '--no-sandbox',
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-background-networking',
    '--window-size=1440,1000',
    `${baseUrl}/login`
  ]
  chromeProcess = spawn(chrome, args, { stdio: ['ignore', 'pipe', 'pipe'] })
  chromeProcess.stdout.on('data', data => browserMessages.push(String(data)))
  chromeProcess.stderr.on('data', data => browserMessages.push(String(data)))
  await waitForHttp(`http://127.0.0.1:${port}/json/version`, 15000)
}

async function getPageWebSocket() {
  const port = Number(process.env.E2E_REMOTE_DEBUGGING_PORT || 9224)
  const pages = await waitForJson(`http://127.0.0.1:${port}/json/list`, 15000)
  const page = pages.find(item => item.type === 'page' && item.webSocketDebuggerUrl)
  if (!page) throw new Error('未找到 Chrome 调试页面')
  return page.webSocketDebuggerUrl
}

function ensureChrome() {
  const candidates = [
    process.env.CHROME_PATH,
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
    'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
  ].filter(Boolean)
  const chrome = candidates.find(path => existsSync(path))
  if (!chrome) throw new Error('未找到 Chrome 或 Edge，可设置 CHROME_PATH 后重试')
  return chrome
}

async function waitForHttp(url, timeoutMs) {
  const started = Date.now()
  while (Date.now() - started < timeoutMs) {
    try {
      await httpJson(url)
      return
    } catch {
      await delay(250)
    }
  }
  throw new Error(`等待 HTTP 服务超时: ${url}`)
}

async function waitForJson(url, timeoutMs) {
  const started = Date.now()
  while (Date.now() - started < timeoutMs) {
    try {
      return await httpJson(url)
    } catch {
      await delay(250)
    }
  }
  throw new Error(`等待 JSON 超时: ${url}`)
}

function httpJson(url) {
  return new Promise((resolveHttp, rejectHttp) => {
    const req = get(url, response => {
      let body = ''
      response.setEncoding('utf8')
      response.on('data', chunk => { body += chunk })
      response.on('end', () => {
        if (response.statusCode < 200 || response.statusCode >= 300) {
          rejectHttp(new Error(`HTTP ${response.statusCode}`))
          return
        }
        try {
          resolveHttp(JSON.parse(body))
        } catch (error) {
          rejectHttp(error)
        }
      })
    })
    req.on('error', rejectHttp)
    req.setTimeout(3000, () => {
      req.destroy(new Error('HTTP timeout'))
    })
  })
}

async function writeReports(status) {
  const report = {
    generatedAt: formatLocalDateTime(new Date()),
    baseUrl,
    username,
    status,
    steps,
    consoleErrors,
    networkErrors,
    browserMessages: browserMessages.slice(-10)
  }
  mkdirSync(dirname(reportJsonPath), { recursive: true })
  writeFileSync(reportJsonPath, `${JSON.stringify(report, null, 2)}\n`, 'utf8')
  writeFileSync(reportMdPath, markdownReport(report), 'utf8')
}

function markdownReport(report) {
  const lines = [
    '# SmartDisplay MES 浏览器 E2E 报告',
    '',
    `- Generated at: ${report.generatedAt}`,
    `- Base URL: ${report.baseUrl}`,
    `- User: ${report.username}`,
    `- Status: ${report.status}`,
    '',
    '## Steps',
    '',
    '| Step | Status | Duration | Detail |',
    '| --- | --- | ---: | --- |',
    ...report.steps.map(step => `| ${escapeMd(step.name)} | ${step.status} | ${step.durationMs} ms | ${escapeMd(step.detail)} |`),
    '',
    '## Runtime Checks',
    '',
    `- Console errors: ${report.consoleErrors.length}`,
    `- Network errors: ${report.networkErrors.length}`
  ]
  if (report.consoleErrors.length) {
    lines.push('', '### Console Errors', '', ...report.consoleErrors.map(item => `- ${escapeMd(item)}`))
  }
  if (report.networkErrors.length) {
    lines.push('', '### Network Errors', '', ...report.networkErrors.map(item => `- ${escapeMd(item)}`))
  }
  lines.push('')
  return `${lines.join('\n')}\n`
}

async function cleanup() {
  if (client) {
    try { client.close() } catch {}
  }
  if (chromeProcess && !chromeProcess.killed) {
    chromeProcess.kill()
  }
  if (userDataDir && existsSync(userDataDir)) {
    try { rmSync(userDataDir, { recursive: true, force: true }) } catch {}
  }
}

function delay(ms) {
  return new Promise(resolveDelay => setTimeout(resolveDelay, ms))
}

function formatTimestamp(date) {
  const pad = value => String(value).padStart(2, '0')
  return `${date.getFullYear()}${pad(date.getMonth() + 1)}${pad(date.getDate())}-${pad(date.getHours())}${pad(date.getMinutes())}${pad(date.getSeconds())}`
}

function formatLocalDateTime(date) {
  const pad = value => String(value).padStart(2, '0')
  const offset = -date.getTimezoneOffset()
  const sign = offset >= 0 ? '+' : '-'
  const hours = pad(Math.floor(Math.abs(offset) / 60))
  const minutes = pad(Math.abs(offset) % 60)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())} ${sign}${hours}:${minutes}`
}

function assert(condition, message) {
  if (!condition) throw new Error(message)
}

function escapeMd(value) {
  return String(value || '').replace(/\|/g, '\\|').replace(/\n/g, '<br>')
}

function escapeJs(value) {
  return String(value).replace(/\\/g, '\\\\').replace(/'/g, "\\'")
}

class CdpClient {
  static connect(url) {
    return new Promise((resolveClient, rejectClient) => {
      const ws = new WebSocket(url)
      const client = new CdpClient(ws)
      ws.addEventListener('open', () => resolveClient(client), { once: true })
      ws.addEventListener('error', event => rejectClient(new Error(event.message || 'WebSocket error')), { once: true })
    })
  }

  constructor(ws) {
    this.ws = ws
    this.nextId = 1
    this.pending = new Map()
    this.listeners = new Map()
    ws.addEventListener('message', event => this.handleMessage(event))
    ws.addEventListener('close', () => {
      for (const { reject } of this.pending.values()) {
        reject(new Error('WebSocket closed'))
      }
      this.pending.clear()
    })
  }

  send(method, params = {}) {
    const id = this.nextId++
    const payload = JSON.stringify({ id, method, params })
    return new Promise((resolveSend, rejectSend) => {
      this.pending.set(id, { resolve: resolveSend, reject: rejectSend })
      this.ws.send(payload)
    })
  }

  on(method, listener) {
    if (!this.listeners.has(method)) this.listeners.set(method, new Set())
    this.listeners.get(method).add(listener)
    return () => this.listeners.get(method)?.delete(listener)
  }

  once(method, listener) {
    const off = this.on(method, event => {
      off()
      listener(event)
    })
    return off
  }

  close() {
    this.ws.close()
  }

  handleMessage(event) {
    const raw = typeof event.data === 'string' ? event.data : event.data.toString()
    const message = JSON.parse(raw)
    if (message.id) {
      const pending = this.pending.get(message.id)
      if (!pending) return
      this.pending.delete(message.id)
      if (message.error) {
        pending.reject(new Error(message.error.message || JSON.stringify(message.error)))
      } else {
        pending.resolve(message.result || {})
      }
      return
    }
    if (message.method) {
      for (const listener of this.listeners.get(message.method) || []) {
        listener(message.params || {})
      }
    }
  }
}
