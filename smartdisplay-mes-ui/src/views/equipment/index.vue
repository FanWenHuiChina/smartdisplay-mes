<template>
  <section>
    <div class="page-head">
      <div>
        <h1 class="page-title">设备与自动化 / EAP 参数、报警与 PM</h1>
        <p class="page-desc">设备域从状态、能力、报警、PM 和过程参数形成闭环，所有写操作进入审计链路。</p>
      </div>
      <div class="page-actions">
        <button class="mes-btn" :disabled="loading" @click="loadEquipmentData">同步 EAP</button>
        <button v-if="canWriteEquipment" class="mes-btn" :disabled="submitting" @click="submitEquipmentEvent">创建事件</button>
        <button v-if="canWriteEquipment" class="mes-btn" :disabled="submitting" @click="submitRecipeDownload">下发 Recipe</button>
        <button v-if="canWriteEquipment" class="mes-btn primary" :disabled="submitting" @click="submitParameterReport">上报参数</button>
      </div>
    </div>

    <div class="mes-grid cols-4">
      <div v-for="item in metrics" :key="item.label" class="mes-card metric-card">
        <div class="metric-label">
          <span>{{ item.label }}</span>
          <span class="status-tag" :class="item.type">{{ item.tag }}</span>
        </div>
        <div class="metric-value">{{ item.value }}</div>
        <div class="metric-meta"><span>{{ item.left }}</span><span>{{ item.right }}</span></div>
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
              <span>{{ factor.label }}</span>
              <strong>{{ factor.value }}</strong>
              <em>{{ factor.meta }}</em>
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
            <thead><tr><th>原因</th><th>类型</th><th>次数</th><th>分钟</th></tr></thead>
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

      <div class="mes-card gateway-wide">
        <div class="mes-card__head">
          <div class="mes-card__title">EAP 网关连接 <small>{{ gatewayDriverRows.length }} 驱动</small></div>
          <span class="status-tag" :class="connectedGatewayCount ? 'green' : 'red'">{{ connectedGatewayCount }} CONNECTED</span>
        </div>
        <div class="mes-card__body">
          <div class="gateway-list">
            <button
              v-for="gateway in gatewayRows"
              :key="gateway.gatewayCode"
              class="check-cell gateway-cell"
              :class="[gateway.type, { selected: selectedGatewayCode === gateway.gatewayCode }]"
              type="button"
              @click="selectGateway(gateway)"
            >
              <strong>{{ gateway.gatewayCode }}</strong>
              <span>{{ gateway.protocolType }} / {{ gateway.status }}</span>
              <em>{{ gateway.driverCode }} / {{ gateway.time }}</em>
            </button>
          </div>

          <div class="equipment-form gateway-form section-gap">
            <div class="mes-field">
              <label>网关编码</label>
              <input v-model="gatewayForm.gatewayCode" class="mes-input" />
            </div>
            <div class="mes-field">
              <label>协议</label>
              <select v-model="gatewayForm.protocolType" class="mes-select">
                <option value="SIMULATED_HTTP">SIMULATED_HTTP</option>
                <option value="SECS_GEM">SECS_GEM</option>
                <option value="OPC_UA">OPC_UA</option>
                <option value="VENDOR_HTTP">VENDOR_HTTP</option>
              </select>
            </div>
            <div class="mes-field">
              <label>状态</label>
              <select v-model="gatewayForm.status" class="mes-select">
                <option value="CONNECTED">CONNECTED</option>
                <option value="DISCONNECTED">DISCONNECTED</option>
                <option value="DEGRADED">DEGRADED</option>
              </select>
            </div>
            <div class="mes-field">
              <label>驱动模式</label>
              <select v-model="gatewayForm.driverMode" class="mes-select">
                <option value="SIMULATED">SIMULATED</option>
                <option value="SHADOW">SHADOW</option>
                <option value="LIVE">LIVE</option>
              </select>
            </div>
            <div class="mes-field">
              <label>TLS</label>
              <select v-model="gatewayForm.tlsEnabled" class="mes-select">
                <option value="0">关闭</option>
                <option value="1">开启</option>
              </select>
            </div>
            <div class="mes-field wide">
              <label>网关名称</label>
              <input v-model="gatewayForm.gatewayName" class="mes-input" />
            </div>
            <div class="mes-field wide">
              <label>Endpoint</label>
              <input v-model="gatewayForm.endpointUri" class="mes-input" />
            </div>
            <div class="mes-field">
              <label>产线</label>
              <input v-model="gatewayForm.lineCode" class="mes-input" />
            </div>
            <div class="mes-field">
              <label>心跳ms</label>
              <input v-model="gatewayForm.heartbeatIntervalMs" class="mes-input" inputmode="numeric" />
            </div>
            <div class="mes-field">
              <label>连接超时ms</label>
              <input v-model="gatewayForm.connectionTimeoutMs" class="mes-input" inputmode="numeric" />
            </div>
            <div class="mes-field">
              <label>读取超时ms</label>
              <input v-model="gatewayForm.readTimeoutMs" class="mes-input" inputmode="numeric" />
            </div>
            <div class="mes-field wide">
              <label>绑定设备</label>
              <input v-model="gatewayForm.equipmentCodes" class="mes-input" />
            </div>
          </div>

          <div class="toolbar">
            <button v-if="canManageGateway" class="mes-btn primary" :disabled="submitting" @click="submitGatewayRegister">保存网关</button>
            <button v-if="canManageGateway" class="mes-btn" :disabled="submitting" @click="submitGatewayHeartbeat('CONNECTED')">发送心跳</button>
            <button v-if="canManageGateway" class="mes-btn" :disabled="submitting" @click="submitGatewayHealthCheck">健康检查</button>
            <button v-if="canManageGateway" class="mes-btn warn" :disabled="submitting" @click="submitGatewayHeartbeat('DEGRADED')">模拟降级</button>
          </div>
        </div>
      </div>
    </div>

    <div class="mes-card section-gap">
      <div class="mes-card__head">
        <div class="mes-card__title">EAP 网关健康检查履历</div>
        <span class="status-tag" :class="warningGatewayHealthCount ? 'amber' : 'green'">{{ warningGatewayHealthCount }} WARN/FAIL</span>
      </div>
      <div class="mes-card__body">
        <div class="toolbar">
          <button class="mes-btn" :disabled="loading" @click="loadGatewayHealthChecks">刷新检查</button>
          <button v-if="canManageGateway" class="mes-btn primary" :disabled="submitting" @click="submitGatewayHealthCheck">手动检查</button>
        </div>
        <table class="mes-table section-gap">
          <thead><tr><th>检查单</th><th>网关</th><th>协议</th><th>驱动</th><th>类型</th><th>结果</th><th>延迟</th><th>说明</th><th>时间</th></tr></thead>
          <tbody>
            <tr v-for="check in gatewayHealthRows" :key="check.checkNo" :class="{ danger: check.type === 'red' }">
              <td>{{ check.checkNo }}</td>
              <td>{{ check.gatewayCode }}</td>
              <td>{{ check.protocolType }}</td>
              <td>{{ check.driverCode }}</td>
              <td>{{ check.checkType }}</td>
              <td><span class="status-tag" :class="check.type">{{ check.resultStatus }}</span></td>
              <td>{{ check.latencyText }}</td>
              <td>{{ check.errorMessage || '-' }}</td>
              <td>{{ check.time }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="mes-card section-gap">
      <div class="mes-card__head">
        <div class="mes-card__title">EAP 网关消息履历</div>
        <span class="status-tag" :class="failedGatewayMessageCount ? 'red' : 'green'">{{ failedGatewayMessageCount }} FAILED</span>
      </div>
      <div class="mes-card__body">
        <div class="equipment-form gateway-message-form">
          <div class="mes-field">
            <label>网关</label>
            <select v-model="selectedGatewayCode" class="mes-select" @change="selectGateway(selectedGatewayCode)">
              <option v-for="gateway in gatewayRows" :key="gateway.gatewayCode" :value="gateway.gatewayCode">{{ gateway.gatewayCode }}</option>
            </select>
          </div>
          <div class="mes-field">
            <label>消息类型</label>
            <select v-model="gatewayMessageForm.messageType" class="mes-select">
              <option value="STATUS">STATUS</option>
              <option value="CYCLE">CYCLE</option>
            </select>
          </div>
          <div class="mes-field">
            <label>设备</label>
            <select v-model="gatewayMessageForm.equipmentCode" class="mes-select" @change="selectEquipment(gatewayMessageForm.equipmentCode)">
              <option v-for="eq in equipmentRows" :key="eq.code" :value="eq.code">{{ eq.code }}</option>
            </select>
          </div>
          <div class="mes-field">
            <label>状态</label>
            <select v-model="gatewayMessageForm.status" class="mes-select">
              <option value="RUNNING">RUNNING</option>
              <option value="IDLE">IDLE</option>
              <option value="ALARM">ALARM</option>
              <option value="DOWN">DOWN</option>
            </select>
          </div>
          <div class="mes-field">
            <label>实际节拍</label>
            <input v-model="gatewayMessageForm.actualCycleSeconds" class="mes-input" inputmode="decimal" />
          </div>
        </div>
        <div class="toolbar">
          <button class="mes-btn" :disabled="loading" @click="loadGatewayMessages">刷新消息</button>
          <button v-if="canIngestEap" class="mes-btn primary" :disabled="submitting" @click="submitGatewayMessage">模拟入站</button>
        </div>
        <table class="mes-table section-gap">
          <thead><tr><th>消息</th><th>网关</th><th>设备</th><th>协议</th><th>驱动</th><th>类型</th><th>方向</th><th>处理</th><th>时间</th></tr></thead>
          <tbody>
            <tr v-for="message in gatewayMessageRows" :key="message.messageNo" :class="{ danger: message.type === 'red' }">
              <td>{{ message.messageNo }}</td>
              <td>{{ message.gatewayCode }}</td>
              <td>{{ message.equipmentCode }}</td>
              <td>{{ message.protocolType }}</td>
              <td>{{ message.driverCode }}</td>
              <td>{{ message.messageType }}</td>
              <td>{{ message.direction }}</td>
              <td><span class="status-tag" :class="message.type">{{ message.processStatus }}</span></td>
              <td>{{ message.time }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="mes-grid cols-2 section-gap">
      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">EAP 状态上报 / 状态历史</div>
          <span class="status-tag blue">{{ statusForm.status }}</span>
        </div>
        <div class="mes-card__body">
          <div class="equipment-form status-form">
            <div class="mes-field">
              <label>设备</label>
              <select v-model="statusForm.equipmentCode" class="mes-select" @change="selectEquipment(statusForm.equipmentCode)">
                <option v-for="eq in equipmentRows" :key="eq.code" :value="eq.code">{{ eq.code }}</option>
              </select>
            </div>
            <div class="mes-field">
              <label>目标状态</label>
              <select v-model="statusForm.status" class="mes-select">
                <option value="RUNNING">RUNNING</option>
                <option value="IDLE">IDLE</option>
                <option value="ALARM">ALARM</option>
                <option value="DOWN">DOWN</option>
                <option value="PM">PM</option>
              </select>
            </div>
            <div class="mes-field wide">
              <label>原因</label>
              <input v-model="statusForm.changeReason" class="mes-input" />
            </div>
          </div>
          <div class="toolbar">
            <button v-if="canWriteEquipment" class="mes-btn primary" :disabled="submitting" @click="submitStatusReport">上报状态</button>
          </div>
          <table class="mes-table section-gap">
            <thead><tr><th>设备</th><th>变更</th><th>原因</th><th>来源</th><th>时间</th></tr></thead>
            <tbody>
              <tr v-for="item in statusRows" :key="item.historyNo">
                <td>{{ item.equipmentCode }}</td>
                <td><span class="status-tag" :class="item.type">{{ item.fromStatus }} → {{ item.toStatus }}</span></td>
                <td>{{ item.changeReason }}</td>
                <td>{{ item.sourceSystem }}</td>
                <td>{{ item.time }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">EAP 标准节拍 / 实际节拍</div>
          <span class="status-tag teal">{{ equipmentOee.performanceSampleCount || 0 }} 样本</span>
        </div>
        <div class="mes-card__body">
          <div class="equipment-form cycle-form">
            <div class="mes-field">
              <label>设备</label>
              <select v-model="cycleForm.equipmentCode" class="mes-select" @change="selectEquipment(cycleForm.equipmentCode)">
                <option v-for="eq in equipmentRows" :key="eq.code" :value="eq.code">{{ eq.code }}</option>
              </select>
            </div>
            <div class="mes-field">
              <label>Lot</label>
              <input v-model="cycleForm.lotNo" class="mes-input" />
            </div>
            <div class="mes-field">
              <label>工序</label>
              <input v-model="cycleForm.stepCode" class="mes-input" />
            </div>
            <div class="mes-field">
              <label>标准秒(可空)</label>
              <input v-model="cycleForm.standardCycleSeconds" class="mes-input" inputmode="decimal" />
            </div>
            <div class="mes-field">
              <label>实际秒</label>
              <input v-model="cycleForm.actualCycleSeconds" class="mes-input" inputmode="decimal" />
            </div>
            <div class="mes-field">
              <label>良品/产出</label>
              <input v-model="cycleForm.goodOutputText" class="mes-input" />
            </div>
          </div>
          <div class="toolbar">
            <button v-if="canWriteEquipment" class="mes-btn primary" :disabled="submitting" @click="submitCycleReport">上报节拍</button>
            <button class="mes-btn" @click="fillFastCycle">正常样本</button>
            <button class="mes-btn warn" @click="fillSlowCycle">慢节拍样本</button>
            <button class="mes-btn" @click="useStandardCycleMaster">留空匹配主数据</button>
          </div>
          <table class="mes-table section-gap">
            <thead><tr><th>设备</th><th>Lot</th><th>标准/实际</th><th>性能</th><th>结果</th></tr></thead>
            <tbody>
              <tr v-for="item in cycleRows" :key="item.sampleNo" :class="{ danger: item.type === 'red' }">
                <td>{{ item.equipmentCode }}</td>
                <td>{{ item.lotNo }}</td>
                <td>{{ item.cycleText }}</td>
                <td>{{ item.performanceText }}</td>
                <td><span class="status-tag" :class="item.type">{{ item.result }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="mes-grid cols-2 section-gap">
      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">标准节拍主数据</div>
          <span class="status-tag teal">{{ standardCycleRows.length }} ACTIVE</span>
        </div>
        <div class="mes-card__body">
          <div class="equipment-form cycle-master-form">
            <div class="mes-field">
              <label>产品</label>
              <input v-model="standardCycleForm.productCode" class="mes-input" />
            </div>
            <div class="mes-field">
              <label>工序</label>
              <input v-model="standardCycleForm.stepCode" class="mes-input" />
            </div>
            <div class="mes-field">
              <label>设备</label>
              <select v-model="standardCycleForm.equipmentCode" class="mes-select" @change="selectEquipment(standardCycleForm.equipmentCode)">
                <option v-for="eq in equipmentRows" :key="eq.code" :value="eq.code">{{ eq.code }}</option>
              </select>
            </div>
            <div class="mes-field">
              <label>Recipe</label>
              <input v-model="standardCycleForm.recipeCode" class="mes-input" />
            </div>
            <div class="mes-field">
              <label>版本</label>
              <input v-model="standardCycleForm.cycleVersion" class="mes-input" />
            </div>
            <div class="mes-field">
              <label>标准秒</label>
              <input v-model="standardCycleForm.standardCycleSeconds" class="mes-input" inputmode="decimal" />
            </div>
            <div class="mes-field">
              <label>下限秒</label>
              <input v-model="standardCycleForm.lowerCycleSeconds" class="mes-input" inputmode="decimal" />
            </div>
            <div class="mes-field">
              <label>上限秒</label>
              <input v-model="standardCycleForm.upperCycleSeconds" class="mes-input" inputmode="decimal" />
            </div>
          </div>
          <div class="toolbar">
            <button v-if="canWriteEquipment" class="mes-btn primary" :disabled="submitting" @click="submitStandardCycle">发布标准节拍</button>
            <button class="mes-btn" @click="fillCoatingStandardCycle">COATING 模板</button>
            <button class="mes-btn" @click="fillEvapStandardCycle">EVAP 模板</button>
          </div>
          <table class="mes-table section-gap">
            <thead><tr><th>产品/工序</th><th>设备</th><th>Recipe</th><th>标准窗口</th><th>版本</th></tr></thead>
            <tbody>
              <tr v-for="item in standardCycleRows" :key="item.cycleNo">
                <td>{{ item.productCode }} / {{ item.stepCode }}</td>
                <td>{{ item.equipmentCode }}</td>
                <td>{{ item.recipeCode || '-' }}</td>
                <td>{{ item.windowText }}</td>
                <td><span class="status-tag" :class="item.type">{{ item.cycleVersion }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="mes-grid cols-2 section-gap">
      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">设备状态矩阵</div>
          <span class="status-tag teal">能力矩阵</span>
        </div>
        <div class="mes-card__body">
          <div class="matrix">
            <button
              v-for="eq in equipmentRows"
              :key="eq.code"
              class="check-cell equipment-cell"
              :class="[eq.type, { selected: selectedEquipmentCode === eq.code }]"
              type="button"
              @click="selectEquipment(eq.code)"
            >
              <strong>{{ eq.code }}</strong>
              <span>{{ eq.text }}</span>
            </button>
          </div>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">设备事件队列</div>
          <span class="status-tag" :class="openEventCount ? 'red' : 'green'">{{ openEventCount }} OPEN</span>
        </div>
        <div class="mes-card__body">
          <table class="mes-table">
            <thead><tr><th>事件</th><th>设备</th><th>等级</th><th>来源</th><th>状态</th><th>时间</th><th>动作</th></tr></thead>
            <tbody>
              <tr v-for="event in eventRows" :key="event.eventNo" :class="{ danger: event.type === 'red' }">
                <td>{{ event.title }}</td>
                <td>{{ event.equipmentCode }}</td>
                <td>{{ event.eventLevel }}</td>
                <td>{{ event.sourceSystem }}</td>
                <td><span class="status-tag" :class="event.type">{{ event.status }}</span></td>
                <td>{{ event.time }}</td>
                <td>
                  <button
                    v-if="canWriteEquipment && event.status !== 'CLOSED'"
                    class="mes-btn tiny"
                    :disabled="submitting"
                    @click="closeEvent(event)"
                  >关闭</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="mes-grid cols-2 section-gap">
      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">Recipe 下发 / 回读确认</div>
          <span class="status-tag blue">{{ downloadForm.recipeCode }}</span>
        </div>
        <div class="mes-card__body">
          <div class="equipment-form">
            <div class="mes-field">
              <label>设备</label>
              <select v-model="downloadForm.equipmentCode" class="mes-select" @change="selectEquipment(downloadForm.equipmentCode)">
                <option v-for="eq in equipmentRows" :key="eq.code" :value="eq.code">{{ eq.code }}</option>
              </select>
            </div>
            <div class="mes-field">
              <label>Lot</label>
              <input v-model="downloadForm.lotNo" class="mes-input" />
            </div>
            <div class="mes-field">
              <label>产品</label>
              <input v-model="downloadForm.productCode" class="mes-input" />
            </div>
            <div class="mes-field">
              <label>工序</label>
              <input v-model="downloadForm.stepCode" class="mes-input" />
            </div>
            <div class="mes-field">
              <label>Recipe</label>
              <input v-model="downloadForm.recipeCode" class="mes-input" />
            </div>
            <div class="mes-field">
              <label>回读结果</label>
              <select v-model="downloadForm.readbackStatus" class="mes-select">
                <option value="MATCH">MATCH</option>
                <option value="MISMATCH">MISMATCH</option>
              </select>
            </div>
          </div>
          <div class="toolbar">
            <button v-if="canWriteEquipment" class="mes-btn primary" :disabled="submitting" @click="submitRecipeDownload">
              {{ submitting ? '提交中' : '模拟下发' }}
            </button>
            <button class="mes-btn" @click="fillRecipeMatch">MATCH 模板</button>
            <button class="mes-btn warn" @click="fillRecipeMismatch">MISMATCH 模板</button>
          </div>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">Recipe 下发履历</div>
          <span class="status-tag blue">{{ recipeRows.length }} 条</span>
        </div>
        <div class="mes-card__body">
          <table class="mes-table">
            <thead><tr><th>命令</th><th>设备</th><th>Recipe</th><th>下发</th><th>回读</th><th>时间</th></tr></thead>
            <tbody>
              <tr v-for="cmd in recipeRows" :key="cmd.commandNo" :class="{ danger: cmd.type === 'red' }">
                <td>{{ cmd.commandNo }}</td>
                <td>{{ cmd.equipmentCode }}</td>
                <td>{{ cmd.recipeCode }}</td>
                <td><span class="status-tag" :class="cmd.type">{{ cmd.commandStatus }}</span></td>
                <td>{{ cmd.readbackStatus }}</td>
                <td>{{ cmd.time }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="mes-grid cols-2 section-gap">
      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">EAP 参数上报</div>
          <span class="status-tag blue">{{ selectedEquipmentCode }}</span>
        </div>
        <div class="mes-card__body">
          <div class="equipment-form">
            <div class="mes-field">
              <label>设备</label>
              <select v-model="parameterForm.equipmentCode" class="mes-select" @change="selectEquipment(parameterForm.equipmentCode)">
                <option v-for="eq in equipmentRows" :key="eq.code" :value="eq.code">{{ eq.code }}</option>
              </select>
            </div>
            <div class="mes-field">
              <label>Lot</label>
              <input v-model="parameterForm.lotNo" class="mes-input" />
            </div>
            <div class="mes-field">
              <label>工序</label>
              <input v-model="parameterForm.stepCode" class="mes-input" />
            </div>
            <div class="mes-field">
              <label>Recipe</label>
              <input v-model="parameterForm.recipeCode" class="mes-input" />
            </div>
            <div class="mes-field">
              <label>参数编码</label>
              <input v-model="parameterForm.paramCode" class="mes-input" />
            </div>
            <div class="mes-field">
              <label>测量值</label>
              <input v-model="parameterForm.paramValue" class="mes-input" inputmode="decimal" />
            </div>
            <div class="mes-field">
              <label>下限</label>
              <input v-model="parameterForm.lowerLimit" class="mes-input" inputmode="decimal" />
            </div>
            <div class="mes-field">
              <label>上限</label>
              <input v-model="parameterForm.upperLimit" class="mes-input" inputmode="decimal" />
            </div>
            <div class="mes-field">
              <label>单位</label>
              <input v-model="parameterForm.unit" class="mes-input" />
            </div>
          </div>
          <div class="toolbar">
            <button v-if="canWriteEquipment" class="mes-btn primary" :disabled="submitting" @click="submitParameterReport">
              {{ submitting ? '提交中' : '模拟 EAP 上报' }}
            </button>
            <button class="mes-btn" @click="fillOkSample">填入 OK 样本</button>
            <button class="mes-btn warn" @click="fillNgSample">填入 NG 样本</button>
          </div>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">参数采样履历</div>
          <span class="status-tag blue">{{ sampleRows.length }} 条</span>
        </div>
        <div class="mes-card__body">
          <table class="mes-table">
            <thead><tr><th>设备</th><th>Lot</th><th>参数</th><th>测量值</th><th>结果</th><th>时间</th></tr></thead>
            <tbody>
              <tr v-for="sample in sampleRows" :key="sample.sampleNo" :class="{ danger: sample.type === 'red' }">
                <td>{{ sample.equipmentCode }}</td>
                <td>{{ sample.lotNo }}</td>
                <td>{{ sample.paramCode }}</td>
                <td>{{ sample.valueText }}</td>
                <td><span class="status-tag" :class="sample.type">{{ sample.result }}</span></td>
                <td>{{ sample.time }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="mes-grid cols-2 section-gap">
      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">报警 / 状态事件录入</div>
          <span class="status-tag" :class="canWriteEquipment ? 'green' : 'gray'">{{ canWriteEquipment ? '可写' : '只读' }}</span>
        </div>
        <div class="mes-card__body">
          <div class="equipment-form event-form">
            <div class="mes-field">
              <label>设备</label>
              <select v-model="eventForm.equipmentCode" class="mes-select">
                <option v-for="eq in equipmentRows" :key="eq.code" :value="eq.code">{{ eq.code }}</option>
              </select>
            </div>
            <div class="mes-field">
              <label>类型</label>
              <select v-model="eventForm.eventType" class="mes-select">
                <option value="ALARM">ALARM</option>
                <option value="PARAMETER">PARAMETER</option>
                <option value="STATUS">STATUS</option>
                <option value="PM">PM</option>
                <option value="DOWN">DOWN</option>
              </select>
            </div>
            <div class="mes-field">
              <label>等级</label>
              <select v-model="eventForm.eventLevel" class="mes-select">
                <option value="P1">P1</option>
                <option value="P2">P2</option>
                <option value="P3">P3</option>
              </select>
            </div>
            <div class="mes-field">
              <label>设备状态</label>
              <select v-model="eventForm.equipmentStatus" class="mes-select">
                <option value="ALARM">ALARM</option>
                <option value="RUNNING">RUNNING</option>
                <option value="IDLE">IDLE</option>
                <option value="PM">PM</option>
                <option value="DOWN">DOWN</option>
              </select>
            </div>
            <div class="mes-field wide">
              <label>标题</label>
              <input v-model="eventForm.title" class="mes-input" />
            </div>
            <div class="mes-field wide">
              <label>描述</label>
              <input v-model="eventForm.description" class="mes-input" />
            </div>
          </div>
          <div class="toolbar">
            <button v-if="canWriteEquipment" class="mes-btn primary" :disabled="submitting" @click="submitEquipmentEvent">提交事件</button>
            <button class="mes-btn" @click="fillStatusBackToIdle">恢复 IDLE 模板</button>
          </div>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">PM 计划与完成</div>
          <span class="status-tag amber">{{ duePmCount }} 待处理</span>
        </div>
        <div class="mes-card__body">
          <table class="mes-table">
            <thead><tr><th>任务</th><th>设备</th><th>类型</th><th>到期</th><th>状态</th><th>动作</th></tr></thead>
            <tbody>
              <tr v-for="task in pmRows" :key="task.taskNo" :class="{ danger: task.type === 'red' }">
                <td>{{ task.taskNo }}</td>
                <td>{{ task.equipmentCode }}</td>
                <td>{{ task.pmType }}</td>
                <td>{{ task.time }}</td>
                <td><span class="status-tag" :class="task.type">{{ task.status }}</span></td>
                <td>
                  <button
                    v-if="canWriteEquipment && task.status !== 'COMPLETED'"
                    class="mes-btn tiny"
                    :disabled="submitting"
                    @click="completePm(task)"
                  >完成</button>
                </td>
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
  checkEquipmentGatewayHealth,
  closeEquipmentEvent,
  completeEquipmentPmTask,
  createEquipmentEvent,
  downloadEquipmentRecipe,
  getEquipmentCycleSamples,
  getEquipmentEvents,
  getEquipmentGatewayDrivers,
  getEquipmentGatewayHealthChecks,
  getEquipmentGatewayMessages,
  getEquipmentGateways,
  getEquipmentOee,
  getEquipmentParameterSamples,
  getEquipmentPmTasks,
  getEquipmentRecipeCommands,
  getEquipmentStatusHistory,
  getEquipmentStandardCycles,
  getEquipments,
  heartbeatEquipmentGateway,
  ingestEapMessage,
  publishEquipmentStandardCycle,
  registerEquipmentGateway,
  reportEquipmentCycleSample,
  reportEquipmentParameters,
  reportEquipmentStatus
} from '@/api/pilot'
import { hasButton } from '@/utils/permissions'
import { warnDevFallback } from '@/utils/devFallback'

const fallbackEquipments = [
  { equipmentCode: 'COATER_01', equipmentName: '涂胶机-1', status: 'IDLE', capabilitySteps: '["COATING"]', lineCode: 'LINE_01' },
  { equipmentCode: 'COATER_02', equipmentName: '涂胶机-2', status: 'RUNNING', capabilitySteps: '["COATING"]', lineCode: 'LINE_01' },
  { equipmentCode: 'EVAP_01', equipmentName: '蒸镀机-1', status: 'ALARM', capabilitySteps: '["EVAPORATION"]', lineCode: 'LINE_01' },
  { equipmentCode: 'INSPECT_01', equipmentName: 'AOI检测机-1', status: 'IDLE', capabilitySteps: '["INSPECTION"]', lineCode: 'LINE_01' },
  { equipmentCode: 'ETCH_01', equipmentName: '蚀刻机-1', status: 'ALARM', capabilitySteps: '["ETCH"]', lineCode: 'LINE_01' }
]

const fallbackEvents = [
  { eventNo: 'EVT-FALLBACK-001', title: '真空波动', equipmentCode: 'EVAP_01', eventType: 'ALARM', eventLevel: 'P2', status: 'OPEN', sourceSystem: 'eap-adapter', occurredTime: new Date().toISOString() },
  { eventNo: 'EVT-FALLBACK-002', title: '涂胶膜厚超限', equipmentCode: 'COATER_02', eventType: 'PARAMETER', eventLevel: 'P1', status: 'OPEN', sourceSystem: 'eap-adapter', occurredTime: new Date().toISOString() }
]

const fallbackSamples = [
  { sampleNo: 'EPS-FALLBACK-001', equipmentCode: 'COATER_02', lotNo: 'LOT202406003', stepCode: 'COATING', recipeCode: 'RCP_COAT_002', paramCode: 'THICKNESS', paramValue: 2.26, lowerLimit: 1.8, upperLimit: 2.2, unit: 'um', result: 'NG', sampleTime: new Date().toISOString() },
  { sampleNo: 'EPS-FALLBACK-002', equipmentCode: 'COATER_01', lotNo: 'LOT202406001', stepCode: 'COATING', recipeCode: 'RCP_COAT_001', paramCode: 'TEMP_COATING', paramValue: 150.2, lowerLimit: 145, upperLimit: 155, unit: 'C', result: 'OK', sampleTime: new Date().toISOString() }
]

const fallbackPmTasks = [
  { taskNo: 'PM-FALLBACK-001', equipmentCode: 'COATER_01', pmType: 'NOZZLE_CLEAN', pmLevel: 'SHIFT', status: 'OPEN', planEndTime: new Date().toISOString() },
  { taskNo: 'PM-FALLBACK-002', equipmentCode: 'EVAP_01', pmType: 'VACUUM_CHECK', pmLevel: 'DAILY', status: 'OVERDUE', planEndTime: new Date().toISOString() }
]

const fallbackRecipeCommands = [
  { commandNo: 'RDL-FALLBACK-001', equipmentCode: 'COATER_01', recipeCode: 'RCP_COAT_001', commandStatus: 'SUCCESS', readbackStatus: 'MATCH', downloadTime: new Date().toISOString() },
  { commandNo: 'RDL-FALLBACK-002', equipmentCode: 'COATER_02', recipeCode: 'RCP_COAT_002', commandStatus: 'FAILED', readbackStatus: 'MISMATCH', downloadTime: new Date().toISOString() }
]

const fallbackStatusHistories = [
  { historyNo: 'ESH-FALLBACK-001', equipmentCode: 'COATER_01', fromStatus: 'PM', toStatus: 'IDLE', changeReason: 'PM completed and ready for dispatch', sourceSystem: 'eap-adapter', changedTime: new Date().toISOString() },
  { historyNo: 'ESH-FALLBACK-002', equipmentCode: 'EVAP_01', fromStatus: 'RUNNING', toStatus: 'DOWN', changeReason: 'Vacuum pump down', sourceSystem: 'eap-adapter', changedTime: new Date().toISOString() }
]

const fallbackCycleSamples = [
  { sampleNo: 'ECS-FALLBACK-001', equipmentCode: 'COATER_01', lotNo: 'LOT202406001', stepCode: 'COATING', standardCycleSeconds: 58, actualCycleSeconds: 61, outputQty: 1, goodQty: 1, result: 'OK', sampleTime: new Date().toISOString() },
  { sampleNo: 'ECS-FALLBACK-002', equipmentCode: 'COATER_02', lotNo: 'LOT202406003', stepCode: 'COATING', standardCycleSeconds: 58, actualCycleSeconds: 72, outputQty: 1, goodQty: 0, result: 'NG', sampleTime: new Date().toISOString() }
]

const fallbackStandardCycles = [
  { cycleNo: 'ESC-FALLBACK-001', productCode: 'AMOLED_65', stepCode: 'COATING', equipmentCode: 'COATER_01', recipeCode: 'RCP_COAT_001', cycleVersion: 'V1.0', standardCycleSeconds: 58, lowerCycleSeconds: 52, upperCycleSeconds: 70, status: 'ACTIVE' },
  { cycleNo: 'ESC-FALLBACK-002', productCode: 'AMOLED_67', stepCode: 'COATING', equipmentCode: 'COATER_02', recipeCode: 'RCP_COAT_002', cycleVersion: 'V1.0', standardCycleSeconds: 58, lowerCycleSeconds: 52, upperCycleSeconds: 70, status: 'ACTIVE' },
  { cycleNo: 'ESC-FALLBACK-003', productCode: 'AMOLED_65', stepCode: 'EVAPORATION', equipmentCode: 'EVAP_01', recipeCode: 'RCP_EVAP_001', cycleVersion: 'V1.0', standardCycleSeconds: 420, lowerCycleSeconds: 390, upperCycleSeconds: 480, status: 'ACTIVE' }
]

const fallbackGateways = [
  { gatewayCode: 'GW-SIM-HTTP-01', gatewayName: '试点模拟EAP网关', protocolType: 'SIMULATED_HTTP', driverCode: 'simulated-http-driver', driverMode: 'SIMULATED', endpointUri: 'http://localhost:8080/api/v1/adapters/eap/messages', lineCode: 'LINE_01', equipmentCodes: '["COATER_01","COATER_02","EVAP_01","INSPECT_01"]', status: 'CONNECTED', heartbeatIntervalMs: 1000, tlsEnabled: 0, connectionTimeoutMs: 3000, readTimeoutMs: 5000, lastHeartbeatTime: new Date().toISOString(), type: 'green' },
  { gatewayCode: 'GW-SECSGEM-PLACEHOLDER', gatewayName: 'SECS/GEM预留网关', protocolType: 'SECS_GEM', driverCode: 'secs-gem-driver', driverMode: 'SIMULATED', endpointUri: 'secs://192.168.10.20:5000', lineCode: 'LINE_01', equipmentCodes: '["EVAP_01"]', status: 'DISCONNECTED', heartbeatIntervalMs: 500, tlsEnabled: 0, connectionTimeoutMs: 3000, readTimeoutMs: 5000, type: 'red' },
  { gatewayCode: 'GW-OPCUA-PLACEHOLDER', gatewayName: 'OPC UA预留网关', protocolType: 'OPC_UA', driverCode: 'opc-ua-driver', driverMode: 'SIMULATED', endpointUri: 'opc.tcp://192.168.10.30:4840', lineCode: 'LINE_01', equipmentCodes: '["COATER_01","COATER_02"]', status: 'DISCONNECTED', heartbeatIntervalMs: 1000, tlsEnabled: 0, connectionTimeoutMs: 3000, readTimeoutMs: 5000, type: 'red' }
]

const fallbackGatewayMessages = [
  { messageNo: 'EGM-FALLBACK-001', gatewayCode: 'GW-SIM-HTTP-01', equipmentCode: 'COATER_01', protocolType: 'SIMULATED_HTTP', driverCode: 'simulated-http-driver', direction: 'INBOUND', messageType: 'STATUS', correlationId: 'SEED-STATUS-001', processStatus: 'PROCESSED', occurredTime: new Date().toISOString(), type: 'green' },
  { messageNo: 'EGM-FALLBACK-002', gatewayCode: 'GW-SIM-HTTP-01', equipmentCode: 'COATER_02', protocolType: 'SIMULATED_HTTP', driverCode: 'simulated-http-driver', direction: 'INBOUND', messageType: 'CYCLE', correlationId: 'SEED-CYCLE-001', processStatus: 'PROCESSED', occurredTime: new Date().toISOString(), type: 'green' }
]

const fallbackGatewayHealthChecks = [
  { checkNo: 'EGH-FALLBACK-001', gatewayCode: 'GW-SIM-HTTP-01', protocolType: 'SIMULATED_HTTP', driverCode: 'simulated-http-driver', endpointUri: 'http://localhost:8080/api/v1/adapters/eap/messages', checkType: 'SEED', resultStatus: 'PASS', latencyMs: 12, errorMessage: '', checkedTime: new Date().toISOString(), type: 'green' },
  { checkNo: 'EGH-FALLBACK-002', gatewayCode: 'GW-SECSGEM-PLACEHOLDER', protocolType: 'SECS_GEM', driverCode: 'secs-gem-driver', endpointUri: 'secs://192.168.10.20:5000', checkType: 'SEED', resultStatus: 'WARN', latencyMs: 0, errorMessage: '真实 SECS/GEM 握手待联调', checkedTime: new Date().toISOString(), type: 'amber' }
]

const fallbackGatewayDrivers = [
  { protocolType: 'SIMULATED_HTTP', driverCode: 'simulated-http-driver', driverMode: 'SIMULATED' },
  { protocolType: 'SECS_GEM', driverCode: 'secs-gem-driver', driverMode: 'SIMULATED' },
  { protocolType: 'OPC_UA', driverCode: 'opc-ua-driver', driverMode: 'SIMULATED' },
  { protocolType: 'VENDOR_HTTP', driverCode: 'vendor-http-driver', driverMode: 'SIMULATED' }
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

const equipmentRows = ref(__DEV_MOCK_FALLBACK__ ? fallbackEquipments.map(mapEquipment) : [])
const eventRows = ref(__DEV_MOCK_FALLBACK__ ? fallbackEvents.map(mapEvent) : [])
const sampleRows = ref(__DEV_MOCK_FALLBACK__ ? fallbackSamples.map(mapSample) : [])
const pmRows = ref(__DEV_MOCK_FALLBACK__ ? fallbackPmTasks.map(mapPmTask) : [])
const recipeRows = ref(__DEV_MOCK_FALLBACK__ ? fallbackRecipeCommands.map(mapRecipeCommand) : [])
const statusRows = ref(__DEV_MOCK_FALLBACK__ ? fallbackStatusHistories.map(mapStatusHistory) : [])
const cycleRows = ref(__DEV_MOCK_FALLBACK__ ? fallbackCycleSamples.map(mapCycleSample) : [])
const standardCycleRows = ref(__DEV_MOCK_FALLBACK__ ? fallbackStandardCycles.map(mapStandardCycle) : [])
const gatewayDriverRows = ref(__DEV_MOCK_FALLBACK__ ? fallbackGatewayDrivers : [])
const gatewayRows = ref(__DEV_MOCK_FALLBACK__ ? fallbackGateways.map(mapGateway) : [])
const gatewayMessageRows = ref(__DEV_MOCK_FALLBACK__ ? fallbackGatewayMessages.map(mapGatewayMessage) : [])
const gatewayHealthRows = ref(__DEV_MOCK_FALLBACK__ ? fallbackGatewayHealthChecks.map(mapGatewayHealthCheck) : [])
const equipmentOee = ref(__DEV_MOCK_FALLBACK__ ? fallbackEquipmentOee : emptyEquipmentOee)
const selectedEquipmentCode = ref(__DEV_MOCK_FALLBACK__ ? 'COATER_02' : '')
const selectedGatewayCode = ref(__DEV_MOCK_FALLBACK__ ? 'GW-SIM-HTTP-01' : '')
const loading = ref(false)
const submitting = ref(false)

const parameterForm = reactive({
  equipmentCode: selectedEquipmentCode.value,
  lotNo: __DEV_MOCK_FALLBACK__ ? 'LOT202406003' : '',
  stepCode: 'COATING',
  recipeCode: __DEV_MOCK_FALLBACK__ ? 'RCP_COAT_002' : '',
  paramCode: 'THICKNESS',
  paramName: '涂胶厚度',
  paramValue: '2.26',
  lowerLimit: '1.8',
  upperLimit: '2.2',
  unit: 'um'
})

const eventForm = reactive({
  equipmentCode: selectedEquipmentCode.value,
  eventType: 'ALARM',
  eventLevel: 'P2',
  equipmentStatus: 'ALARM',
  title: '设备报警事件',
  description: 'EAP 模拟适配器上报设备异常'
})

const statusForm = reactive({
  equipmentCode: selectedEquipmentCode.value,
  status: 'RUNNING',
  changeReason: 'EAP 心跳状态变更'
})

const cycleForm = reactive({
  equipmentCode: selectedEquipmentCode.value,
  lotNo: __DEV_MOCK_FALLBACK__ ? 'LOT202406003' : '',
  stepCode: 'COATING',
  recipeCode: __DEV_MOCK_FALLBACK__ ? 'RCP_COAT_002' : '',
  standardCycleSeconds: '',
  actualCycleSeconds: '72',
  goodOutputText: '0/1'
})

const standardCycleForm = reactive({
  equipmentCode: __DEV_MOCK_FALLBACK__ ? 'COATER_02' : '',
  productCode: __DEV_MOCK_FALLBACK__ ? 'AMOLED_67' : '',
  stepCode: 'COATING',
  recipeCode: __DEV_MOCK_FALLBACK__ ? 'RCP_COAT_002' : '',
  cycleVersion: 'V1.1',
  standardCycleSeconds: '58',
  lowerCycleSeconds: '52',
  upperCycleSeconds: '70'
})

const downloadForm = reactive({
  equipmentCode: selectedEquipmentCode.value,
  lotNo: __DEV_MOCK_FALLBACK__ ? 'LOT202406003' : '',
  productCode: __DEV_MOCK_FALLBACK__ ? 'AMOLED_67' : '',
  stepCode: 'COATING',
  recipeCode: __DEV_MOCK_FALLBACK__ ? 'RCP_COAT_002' : '',
  readbackStatus: 'MATCH'
})

const gatewayForm = reactive({
  gatewayCode: selectedGatewayCode.value,
  gatewayName: '试点模拟EAP网关',
  protocolType: 'SIMULATED_HTTP',
  driverMode: 'SIMULATED',
  tlsEnabled: '0',
  endpointUri: 'http://localhost:8080/api/v1/adapters/eap/messages',
  lineCode: 'LINE_01',
  equipmentCodes: __DEV_MOCK_FALLBACK__ ? 'COATER_01 COATER_02 EVAP_01 INSPECT_01' : '',
  heartbeatIntervalMs: '1000',
  connectionTimeoutMs: '3000',
  readTimeoutMs: '5000',
  status: 'CONNECTED'
})

const gatewayMessageForm = reactive({
  messageType: 'STATUS',
  equipmentCode: selectedEquipmentCode.value,
  status: 'RUNNING',
  actualCycleSeconds: '62'
})

const canWriteEquipment = computed(() => hasButton('equipment:event-create'))
const canManageGateway = computed(() => hasButton('equipment:eap-gateway'))
const canIngestEap = computed(() => hasButton('equipment:eap-ingest'))
const openEventCount = computed(() => eventRows.value.filter(item => item.status !== 'CLOSED').length)
const duePmCount = computed(() => pmRows.value.filter(item => item.status !== 'COMPLETED').length)
const connectedGatewayCount = computed(() => gatewayRows.value.filter(item => item.status === 'CONNECTED').length)
const failedGatewayMessageCount = computed(() => gatewayMessageRows.value.filter(item => item.processStatus === 'FAILED').length)
const warningGatewayHealthCount = computed(() => gatewayHealthRows.value.filter(item => item.resultStatus !== 'PASS').length)
const oeeFactors = computed(() => [
  { label: '可用率', value: equipmentOee.value.availabilityText || '-', meta: `计划停机 ${equipmentOee.value.plannedDowntimeMinutes ?? 0} 分` },
  { label: '性能率', value: equipmentOee.value.performanceText || '-', meta: '按可执行设备状态估算' },
  { label: '质量率', value: equipmentOee.value.qualityText || '-', meta: '来自参数/质检结果' }
])
const downtimeReasons = computed(() => {
  const rows = Array.isArray(equipmentOee.value.reasonTopN) ? equipmentOee.value.reasonTopN : []
  return rows.length ? rows : (__DEV_MOCK_FALLBACK__ ? fallbackEquipmentOee.reasonTopN : [])
})
const metrics = computed(() => {
  const total = equipmentRows.value.length
  const online = equipmentRows.value.filter(item => ['IDLE', 'RUNNING'].includes(item.status)).length
  const alarm = equipmentRows.value.filter(item => ['ALARM', 'DOWN'].includes(item.status)).length
  const ngSamples = sampleRows.value.filter(item => item.result !== 'OK').length
  const failedRecipes = recipeRows.value.filter(item => item.commandStatus !== 'SUCCESS').length
  return [
    { label: '在线设备', tag: 'Online', type: online === total ? 'green' : 'amber', value: String(online), left: `总数 ${total}`, right: `异常 ${alarm}` },
    { label: '报警事件', tag: 'OPEN', type: openEventCount.value ? 'red' : 'green', value: String(openEventCount.value), left: 'P1/P2', right: '审计留痕' },
    { label: 'Recipe 下发', tag: 'EAP', type: failedRecipes ? 'amber' : 'green', value: String(recipeRows.value.length), left: `失败 ${failedRecipes}`, right: '回读确认' },
    { label: 'PM 待办', tag: '维护', type: duePmCount.value ? 'amber' : 'green', value: String(duePmCount.value), left: 'OPEN/OVERDUE', right: '完成审计' }
  ]
})

function mapEquipment(item) {
  const code = item.equipmentCode || item.code
  const status = item.status || 'IDLE'
  const capability = capabilityText(item.capabilitySteps)
  return {
    code,
    status,
    text: `${status} / ${capability || item.equipmentName || '-'}`,
    type: statusType(status)
  }
}

function mapEvent(item, index = 0) {
  const status = item.status || 'OPEN'
  const level = item.eventLevel || 'P2'
  return {
    eventNo: item.eventNo || `EVT-${index}`,
    title: item.title || item.description || item.eventType || '设备事件',
    equipmentCode: item.equipmentCode || '-',
    eventType: item.eventType || '-',
    eventLevel: level,
    sourceSystem: item.sourceSystem || 'eap-adapter',
    status,
    time: item.time || formatTime(item.occurredTime),
    type: eventType(status, level)
  }
}

function mapSample(item, index = 0) {
  const result = item.result || 'OK'
  return {
    sampleNo: item.sampleNo || `EPS-${index}`,
    equipmentCode: item.equipmentCode || '-',
    lotNo: item.lotNo || '-',
    paramCode: item.paramCode || item.paramName || '-',
    paramValue: item.paramValue,
    unit: item.unit || '',
    result,
    valueText: `${formatNumber(item.paramValue)}${item.unit || ''}`,
    time: item.time || formatTime(item.sampleTime),
    type: result === 'OK' ? 'green' : 'red'
  }
}

function mapPmTask(item, index = 0) {
  const status = item.status || 'OPEN'
  return {
    taskNo: item.taskNo || `PM-${index}`,
    equipmentCode: item.equipmentCode || '-',
    pmType: item.pmType || '-',
    status,
    time: item.time || formatTime(item.planEndTime),
    type: statusType(status)
  }
}

function mapRecipeCommand(item, index = 0) {
  const commandStatus = item.commandStatus || 'SUCCESS'
  const readbackStatus = item.readbackStatus || 'MATCH'
  return {
    commandNo: item.commandNo || `RDL-${index}`,
    equipmentCode: item.equipmentCode || '-',
    recipeCode: item.recipeCode || '-',
    commandStatus,
    readbackStatus,
    time: item.time || formatTime(item.downloadTime),
    type: commandStatus === 'SUCCESS' && readbackStatus === 'MATCH' ? 'green' : 'red'
  }
}

function mapStatusHistory(item, index = 0) {
  const toStatus = item.toStatus || item.status || 'IDLE'
  return {
    historyNo: item.historyNo || `ESH-${index}`,
    equipmentCode: item.equipmentCode || '-',
    fromStatus: item.fromStatus || '-',
    toStatus,
    changeReason: item.changeReason || '-',
    sourceSystem: item.sourceSystem || 'eap-adapter',
    time: item.time || formatTime(item.changedTime),
    type: statusType(toStatus)
  }
}

function mapCycleSample(item, index = 0) {
  const standard = Number(item.standardCycleSeconds || 0)
  const actual = Number(item.actualCycleSeconds || 0)
  const performance = actual > 0 ? Math.min(100, (standard * 100) / actual) : 0
  const result = item.result || (performance >= 95 ? 'OK' : 'WARN')
  return {
    sampleNo: item.sampleNo || `ECS-${index}`,
    equipmentCode: item.equipmentCode || '-',
    lotNo: item.lotNo || '-',
    stepCode: item.stepCode || '-',
    standardCycleSeconds: standard,
    actualCycleSeconds: actual,
    outputQty: item.outputQty ?? 1,
    goodQty: item.goodQty ?? item.outputQty ?? 1,
    result,
    cycleText: `${formatNumber(standard)}s / ${formatNumber(actual)}s`,
    performanceText: item.performanceText || `${formatNumber(performance)}%`,
    time: item.time || formatTime(item.sampleTime),
    type: statusType(result)
  }
}

function mapStandardCycle(item, index = 0) {
  const standard = Number(item.standardCycleSeconds || 0)
  const lower = item.lowerCycleSeconds === null || item.lowerCycleSeconds === undefined ? null : Number(item.lowerCycleSeconds)
  const upper = item.upperCycleSeconds === null || item.upperCycleSeconds === undefined ? null : Number(item.upperCycleSeconds)
  const status = item.status || 'ACTIVE'
  return {
    cycleNo: item.cycleNo || `ESC-${index}`,
    productCode: item.productCode || '-',
    stepCode: item.stepCode || '-',
    equipmentCode: item.equipmentCode || '-',
    recipeCode: item.recipeCode || '',
    cycleVersion: item.cycleVersion || 'V1.0',
    standardCycleSeconds: standard,
    lowerCycleSeconds: lower,
    upperCycleSeconds: upper,
    status,
    windowText: `${lower === null ? '-' : `${formatNumber(lower)}s`} / ${formatNumber(standard)}s / ${upper === null ? '-' : `${formatNumber(upper)}s`}`,
    time: item.time || formatTime(item.updatedTime),
    type: statusType(status)
  }
}

function mapGateway(item, index = 0) {
  const status = item.status || 'DISCONNECTED'
  const code = item.gatewayCode || `GW-${index}`
  return {
    gatewayCode: code,
    gatewayName: item.gatewayName || code,
    protocolType: item.protocolType || 'SIMULATED_HTTP',
    driverCode: item.driverCode || driverCodeForProtocol(item.protocolType),
    driverMode: item.driverMode || 'SIMULATED',
    endpointUri: item.endpointUri || '-',
    lineCode: item.lineCode || '-',
    equipmentCodes: equipmentCodesText(item.equipmentCodes),
    status,
    heartbeatIntervalMs: item.heartbeatIntervalMs ?? '-',
    tlsEnabled: item.tlsEnabled ?? 0,
    connectionTimeoutMs: item.connectionTimeoutMs ?? 3000,
    readTimeoutMs: item.readTimeoutMs ?? 5000,
    lastError: item.lastError || '',
    enabled: item.enabled ?? 1,
    time: item.time || formatTime(item.lastHeartbeatTime || item.updatedTime),
    type: item.type || gatewayStatusType(status)
  }
}

function mapGatewayMessage(item, index = 0) {
  const processStatus = item.processStatus || 'RECEIVED'
  return {
    messageNo: item.messageNo || `EGM-${index}`,
    gatewayCode: item.gatewayCode || '-',
    equipmentCode: item.equipmentCode || '-',
    protocolType: item.protocolType || '-',
    driverCode: item.driverCode || driverCodeForProtocol(item.protocolType),
    direction: item.direction || 'INBOUND',
    messageType: item.messageType || 'UNKNOWN',
    correlationId: item.correlationId || '-',
    processStatus,
    errorMessage: item.errorMessage || '',
    time: item.time || formatTime(item.occurredTime),
    type: item.type || gatewayStatusType(processStatus)
  }
}

function mapGatewayHealthCheck(item, index = 0) {
  const resultStatus = item.resultStatus || 'WARN'
  const latency = item.latencyMs
  return {
    checkNo: item.checkNo || `EGH-${index}`,
    gatewayCode: item.gatewayCode || '-',
    protocolType: item.protocolType || '-',
    driverCode: item.driverCode || driverCodeForProtocol(item.protocolType),
    endpointUri: item.endpointUri || '',
    checkType: item.checkType || 'MANUAL',
    resultStatus,
    latencyMs: latency,
    latencyText: latency === null || latency === undefined ? '-' : `${latency}ms`,
    errorMessage: item.errorMessage || '',
    checkedBy: item.checkedBy || '-',
    time: item.time || formatTime(item.checkedTime),
    type: item.type || gatewayStatusType(resultStatus)
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

function capabilityText(value) {
  if (Array.isArray(value)) return value.join(',')
  if (!value) return ''
  try {
    const parsed = JSON.parse(value)
    if (Array.isArray(parsed)) return parsed.join(',')
  } catch (error) {
    return String(value)
  }
  return String(value)
}

function equipmentCodesText(value) {
  if (Array.isArray(value)) return value.join(', ')
  if (!value) return '-'
  try {
    const parsed = JSON.parse(value)
    if (Array.isArray(parsed)) return parsed.join(', ')
  } catch (error) {
    return String(value)
  }
  return String(value)
}

function driverCodeForProtocol(protocolType) {
  const protocol = String(protocolType || 'SIMULATED_HTTP').toUpperCase()
  const driver = gatewayDriverRows.value.find(item => item.protocolType === protocol)
  if (driver?.driverCode) return driver.driverCode
  if (protocol === 'SECS_GEM') return 'secs-gem-driver'
  if (protocol === 'OPC_UA') return 'opc-ua-driver'
  if (protocol === 'VENDOR_HTTP') return 'vendor-http-driver'
  return 'simulated-http-driver'
}

function selectEquipment(code) {
  if (!code) return
  selectedEquipmentCode.value = code
  parameterForm.equipmentCode = code
  eventForm.equipmentCode = code
  statusForm.equipmentCode = code
  cycleForm.equipmentCode = code
  downloadForm.equipmentCode = code
  standardCycleForm.equipmentCode = code
  gatewayMessageForm.equipmentCode = code
}

function selectGateway(row) {
  const gateway = typeof row === 'string'
    ? gatewayRows.value.find(item => item.gatewayCode === row)
    : row
  if (!gateway) return
  selectedGatewayCode.value = gateway.gatewayCode
  gatewayForm.gatewayCode = gateway.gatewayCode
  gatewayForm.gatewayName = gateway.gatewayName
  gatewayForm.protocolType = gateway.protocolType
  gatewayForm.driverMode = gateway.driverMode || 'SIMULATED'
  gatewayForm.tlsEnabled = String(gateway.tlsEnabled ?? 0)
  gatewayForm.endpointUri = gateway.endpointUri
  gatewayForm.lineCode = gateway.lineCode
  gatewayForm.equipmentCodes = gateway.equipmentCodes
  gatewayForm.heartbeatIntervalMs = String(gateway.heartbeatIntervalMs || 1000)
  gatewayForm.connectionTimeoutMs = String(gateway.connectionTimeoutMs || 3000)
  gatewayForm.readTimeoutMs = String(gateway.readTimeoutMs || 5000)
  gatewayForm.status = gateway.status
}

function fillOkSample() {
  parameterForm.paramCode = 'TEMP_COATING'
  parameterForm.paramName = '涂胶温度'
  parameterForm.paramValue = '150.2'
  parameterForm.lowerLimit = '145'
  parameterForm.upperLimit = '155'
  parameterForm.unit = 'C'
}

function fillNgSample() {
  parameterForm.paramCode = 'THICKNESS'
  parameterForm.paramName = '涂胶厚度'
  parameterForm.paramValue = '2.26'
  parameterForm.lowerLimit = '1.8'
  parameterForm.upperLimit = '2.2'
  parameterForm.unit = 'um'
}

function fillStatusBackToIdle() {
  eventForm.eventType = 'STATUS'
  eventForm.eventLevel = 'P3'
  eventForm.equipmentStatus = 'IDLE'
  eventForm.title = '设备恢复待命'
  eventForm.description = '工程师确认设备点检完成，恢复 IDLE。'
}

function fillRecipeMatch() {
  if (__DEV_MOCK_FALLBACK__) {
    downloadForm.equipmentCode = 'COATER_01'
    downloadForm.productCode = 'AMOLED_65'
    downloadForm.stepCode = 'COATING'
    downloadForm.recipeCode = 'RCP_COAT_001'
    downloadForm.readbackStatus = 'MATCH'
    selectEquipment(downloadForm.equipmentCode)
  }
}

function fillRecipeMismatch() {
  if (__DEV_MOCK_FALLBACK__) {
    downloadForm.equipmentCode = 'COATER_02'
    downloadForm.productCode = 'AMOLED_67'
    downloadForm.stepCode = 'COATING'
    downloadForm.recipeCode = 'RCP_COAT_002'
    downloadForm.readbackStatus = 'MISMATCH'
    selectEquipment(downloadForm.equipmentCode)
  }
}

function fillFastCycle() {
  if (__DEV_MOCK_FALLBACK__) {
    cycleForm.equipmentCode = 'COATER_01'
    cycleForm.lotNo = 'LOT202406001'
    cycleForm.stepCode = 'COATING'
    cycleForm.recipeCode = 'RCP_COAT_001'
    cycleForm.standardCycleSeconds = '58'
    cycleForm.actualCycleSeconds = '61'
    cycleForm.goodOutputText = '1/1'
    selectEquipment(cycleForm.equipmentCode)
  }
}

function fillSlowCycle() {
  if (__DEV_MOCK_FALLBACK__) {
    cycleForm.equipmentCode = 'COATER_02'
    cycleForm.lotNo = 'LOT202406003'
    cycleForm.stepCode = 'COATING'
    cycleForm.recipeCode = 'RCP_COAT_002'
    cycleForm.standardCycleSeconds = ''
    cycleForm.actualCycleSeconds = '72'
    cycleForm.goodOutputText = '0/1'
    selectEquipment(cycleForm.equipmentCode)
  }
}

function useStandardCycleMaster() {
  cycleForm.standardCycleSeconds = ''
}

function fillCoatingStandardCycle() {
  if (__DEV_MOCK_FALLBACK__) {
    standardCycleForm.equipmentCode = 'COATER_02'
    standardCycleForm.productCode = 'AMOLED_67'
    standardCycleForm.stepCode = 'COATING'
    standardCycleForm.recipeCode = 'RCP_COAT_002'
    standardCycleForm.cycleVersion = 'V1.1'
    standardCycleForm.standardCycleSeconds = '58'
    standardCycleForm.lowerCycleSeconds = '52'
    standardCycleForm.upperCycleSeconds = '70'
    selectEquipment(standardCycleForm.equipmentCode)
  }
}

function fillEvapStandardCycle() {
  standardCycleForm.equipmentCode = 'EVAP_01'
  standardCycleForm.productCode = 'AMOLED_65'
  standardCycleForm.stepCode = 'EVAPORATION'
  standardCycleForm.recipeCode = 'RCP_EVAP_001'
  standardCycleForm.cycleVersion = 'V1.1'
  standardCycleForm.standardCycleSeconds = '420'
  standardCycleForm.lowerCycleSeconds = '390'
  standardCycleForm.upperCycleSeconds = '480'
  selectEquipment(standardCycleForm.equipmentCode)
}

function numeric(value, field, required = false) {
  if (value === null || value === undefined || value === '') {
    if (required) throw new Error(`${field}不能为空`)
    return undefined
  }
  const number = Number(value)
  if (!Number.isFinite(number)) throw new Error(`${field}必须是数字`)
  return number
}

function parseGoodOutput(value) {
  const [goodText, outputText] = String(value || '1/1').split('/')
  const goodQty = Number(goodText)
  const outputQty = Number(outputText)
  if (!Number.isInteger(goodQty) || !Number.isInteger(outputQty) || outputQty <= 0 || goodQty < 0 || goodQty > outputQty) {
    throw new Error('良品/产出格式应为 1/1，且良品不能大于产出')
  }
  return { goodQty, outputQty }
}

async function submitParameterReport() {
  if (!canWriteEquipment.value) {
    ElMessage.warning('当前角色无权上报设备参数')
    return
  }
  try {
    submitting.value = true
    await reportEquipmentParameters({
      ...parameterForm,
      paramValue: numeric(parameterForm.paramValue, '测量值', true),
      lowerLimit: numeric(parameterForm.lowerLimit, '下限'),
      upperLimit: numeric(parameterForm.upperLimit, '上限'),
      operator: localStorage.getItem('username') || 'ee1001',
      sourceSystem: 'eap-adapter'
    })
    ElMessage.success('EAP 参数已上报')
    await loadEquipmentData()
  } catch (error) {
    ElMessage.warning(error?.message || 'EAP 参数上报失败')
  } finally {
    submitting.value = false
  }
}

async function submitEquipmentEvent() {
  if (!canWriteEquipment.value) {
    ElMessage.warning('当前角色无权创建设备事件')
    return
  }
  try {
    submitting.value = true
    await createEquipmentEvent({
      ...eventForm,
      operator: localStorage.getItem('username') || 'ee1001',
      sourceSystem: 'eap-adapter'
    })
    ElMessage.success('设备事件已创建')
    await loadEquipmentData()
  } catch (error) {
    ElMessage.warning(error?.message || '设备事件创建失败')
  } finally {
    submitting.value = false
  }
}

async function submitStatusReport() {
  if (!canWriteEquipment.value) {
    ElMessage.warning('当前角色无权上报设备状态')
    return
  }
  try {
    submitting.value = true
    await reportEquipmentStatus({
      ...statusForm,
      operator: localStorage.getItem('username') || 'ee1001',
      sourceSystem: 'eap-adapter'
    })
    ElMessage.success('EAP 状态已上报')
    await loadEquipmentData()
  } catch (error) {
    ElMessage.warning(error?.message || 'EAP 状态上报失败')
  } finally {
    submitting.value = false
  }
}

async function submitCycleReport() {
  if (!canWriteEquipment.value) {
    ElMessage.warning('当前角色无权上报设备节拍')
    return
  }
  try {
    const qty = parseGoodOutput(cycleForm.goodOutputText)
    const standardCycleSeconds = numeric(cycleForm.standardCycleSeconds, '标准秒')
    const payload = {
      ...cycleForm,
      ...qty,
      actualCycleSeconds: numeric(cycleForm.actualCycleSeconds, '实际秒', true),
      operator: localStorage.getItem('username') || 'ee1001',
      sourceSystem: 'eap-adapter'
    }
    if (standardCycleSeconds !== undefined) {
      payload.standardCycleSeconds = standardCycleSeconds
    } else {
      delete payload.standardCycleSeconds
    }
    submitting.value = true
    await reportEquipmentCycleSample(payload)
    ElMessage.success('EAP 节拍样本已上报')
    await loadEquipmentData()
  } catch (error) {
    ElMessage.warning(error?.message || 'EAP 节拍上报失败')
  } finally {
    submitting.value = false
  }
}

async function submitStandardCycle() {
  if (!canWriteEquipment.value) {
    ElMessage.warning('当前角色无权发布标准节拍')
    return
  }
  try {
    submitting.value = true
    await publishEquipmentStandardCycle({
      ...standardCycleForm,
      standardCycleSeconds: numeric(standardCycleForm.standardCycleSeconds, '标准秒', true),
      lowerCycleSeconds: numeric(standardCycleForm.lowerCycleSeconds, '下限秒'),
      upperCycleSeconds: numeric(standardCycleForm.upperCycleSeconds, '上限秒'),
      operator: localStorage.getItem('username') || 'ee1001'
    })
    ElMessage.success('标准节拍已发布')
    await loadEquipmentData()
  } catch (error) {
    ElMessage.warning(error?.message || '标准节拍发布失败')
  } finally {
    submitting.value = false
  }
}

async function submitGatewayRegister() {
  if (!canManageGateway.value) {
    ElMessage.warning('当前角色无权维护 EAP 网关')
    return
  }
  try {
    submitting.value = true
    await registerEquipmentGateway({
      ...gatewayForm,
      heartbeatIntervalMs: numeric(gatewayForm.heartbeatIntervalMs, '心跳间隔', true),
      tlsEnabled: Number(gatewayForm.tlsEnabled),
      connectionTimeoutMs: numeric(gatewayForm.connectionTimeoutMs, '连接超时', true),
      readTimeoutMs: numeric(gatewayForm.readTimeoutMs, '读取超时', true),
      operator: localStorage.getItem('username') || 'ee1001'
    })
    ElMessage.success('EAP 网关配置已保存')
    selectedGatewayCode.value = gatewayForm.gatewayCode
    await loadEquipmentData()
  } catch (error) {
    ElMessage.warning(error?.message || 'EAP 网关保存失败')
  } finally {
    submitting.value = false
  }
}

async function submitGatewayHeartbeat(status = 'CONNECTED') {
  if (!canManageGateway.value) {
    ElMessage.warning('当前角色无权维护 EAP 网关')
    return
  }
  try {
    submitting.value = true
    await heartbeatEquipmentGateway(selectedGatewayCode.value, {
      status,
      operator: localStorage.getItem('username') || 'ee1001'
    })
    ElMessage.success('EAP 网关心跳已记录')
    await loadEquipmentData()
  } catch (error) {
    ElMessage.warning(error?.message || 'EAP 网关心跳失败')
  } finally {
    submitting.value = false
  }
}

async function submitGatewayHealthCheck() {
  if (!canManageGateway.value) {
    ElMessage.warning('当前角色无权维护 EAP 网关')
    return
  }
  try {
    submitting.value = true
    await checkEquipmentGatewayHealth(selectedGatewayCode.value, {
      checkType: 'MANUAL',
      operator: localStorage.getItem('username') || 'ee1001'
    })
    ElMessage.success('EAP 网关健康检查已记录')
    await loadEquipmentData()
  } catch (error) {
    ElMessage.warning(error?.message || 'EAP 网关健康检查失败')
  } finally {
    submitting.value = false
  }
}

async function submitGatewayMessage() {
  if (!canIngestEap.value) {
    ElMessage.warning('当前角色无权模拟 EAP 入站消息')
    return
  }
  try {
    const messageType = gatewayMessageForm.messageType
    const payload = {
      equipmentCode: gatewayMessageForm.equipmentCode,
      operator: localStorage.getItem('username') || 'ee1001'
    }
    if (messageType === 'STATUS') {
      payload.status = gatewayMessageForm.status
      payload.changeReason = '设备网关模拟心跳状态'
    }
    if (messageType === 'CYCLE') {
      payload.lotNo = cycleForm.lotNo
      payload.stepCode = cycleForm.stepCode
      payload.recipeCode = cycleForm.recipeCode
      payload.actualCycleSeconds = numeric(gatewayMessageForm.actualCycleSeconds, '实际节拍', true)
      payload.goodQty = 1
      payload.outputQty = 1
    }
    submitting.value = true
    await ingestEapMessage({
      gatewayCode: selectedGatewayCode.value,
      messageType,
      correlationId: `UI-${Date.now()}`,
      payload,
      operator: localStorage.getItem('username') || 'ee1001',
      sourceSystem: 'equipment-gateway-ui'
    })
    ElMessage.success('EAP 入站消息已处理')
    await loadEquipmentData()
  } catch (error) {
    ElMessage.warning(error?.message || 'EAP 入站消息处理失败')
  } finally {
    submitting.value = false
  }
}

async function submitRecipeDownload() {
  if (!canWriteEquipment.value) {
    ElMessage.warning('当前角色无权下发 Recipe')
    return
  }
  try {
    submitting.value = true
    await downloadEquipmentRecipe({
      ...downloadForm,
      operator: localStorage.getItem('username') || 'ee1001',
      sourceSystem: 'eap-adapter'
    })
    ElMessage.success('Recipe 下发命令已提交')
    await loadEquipmentData()
  } catch (error) {
    ElMessage.warning(error?.message || 'Recipe 下发失败')
  } finally {
    submitting.value = false
  }
}

async function completePm(task) {
  try {
    submitting.value = true
    await completeEquipmentPmTask(task.taskNo, {
      result: 'PASS',
      operator: localStorage.getItem('username') || 'ee1001',
      equipmentStatus: 'IDLE'
    })
    ElMessage.success('PM 任务已完成')
    await loadEquipmentData()
  } catch (error) {
    ElMessage.warning(error?.message || 'PM 完成失败')
  } finally {
    submitting.value = false
  }
}

async function closeEvent(event) {
  try {
    submitting.value = true
    await closeEquipmentEvent(event.eventNo, {
      operator: localStorage.getItem('username') || 'ee1001',
      closeConclusion: '设备工程师确认异常已关闭',
      equipmentStatus: 'IDLE'
    })
    ElMessage.success('设备事件已关闭')
    await loadEquipmentData()
  } catch (error) {
    ElMessage.warning(error?.message || '设备事件关闭失败')
  } finally {
    submitting.value = false
  }
}

async function loadGatewayMessages() {
  try {
    const gatewayMessageData = await getEquipmentGatewayMessages({ gatewayCode: selectedGatewayCode.value })
    if (Array.isArray(gatewayMessageData)) gatewayMessageRows.value = gatewayMessageData.map(mapGatewayMessage)
  } catch (error) {
    console.warn('EAP 网关消息接口不可用，保留当前消息履历', error)
  }
}

async function loadGatewayHealthChecks() {
  try {
    const gatewayHealthData = await getEquipmentGatewayHealthChecks({ gatewayCode: selectedGatewayCode.value })
    if (Array.isArray(gatewayHealthData)) gatewayHealthRows.value = gatewayHealthData.map(mapGatewayHealthCheck)
  } catch (error) {
    console.warn('EAP 网关健康检查接口不可用，保留当前检查履历', error)
  }
}

async function loadEquipmentData() {
  try {
    loading.value = true
    const [equipmentData, eventData, sampleData, pmData, recipeData, oeeData, statusData, cycleData, standardCycleData, gatewayData, gatewayDriverData] = await Promise.all([
      getEquipments(),
      getEquipmentEvents(),
      getEquipmentParameterSamples(),
      getEquipmentPmTasks(),
      getEquipmentRecipeCommands(),
      getEquipmentOee(),
      getEquipmentStatusHistory(),
      getEquipmentCycleSamples(),
      getEquipmentStandardCycles({ status: 'ACTIVE' }),
      getEquipmentGateways(),
      getEquipmentGatewayDrivers()
    ])
    if (Array.isArray(equipmentData)) {
      equipmentRows.value = equipmentData.map(mapEquipment)
      if (!equipmentRows.value.some(item => item.code === selectedEquipmentCode.value)) {
        selectEquipment(equipmentRows.value[0]?.code || selectedEquipmentCode.value)
      }
    }
    if (Array.isArray(eventData)) eventRows.value = eventData.map(mapEvent)
    if (Array.isArray(sampleData)) sampleRows.value = sampleData.map(mapSample)
    if (Array.isArray(pmData)) pmRows.value = pmData.map(mapPmTask)
    if (Array.isArray(recipeData)) recipeRows.value = recipeData.map(mapRecipeCommand)
    if (Array.isArray(statusData)) statusRows.value = statusData.map(mapStatusHistory)
    if (Array.isArray(cycleData)) cycleRows.value = cycleData.map(mapCycleSample)
    if (Array.isArray(standardCycleData)) standardCycleRows.value = standardCycleData.map(mapStandardCycle)
    if (Array.isArray(gatewayDriverData)) gatewayDriverRows.value = gatewayDriverData
    if (Array.isArray(gatewayData)) {
      gatewayRows.value = gatewayData.map(mapGateway)
      if (!gatewayRows.value.some(item => item.gatewayCode === selectedGatewayCode.value)) {
        selectGateway(gatewayRows.value[0] || selectedGatewayCode.value)
      }
    }
    equipmentOee.value = normalizeEquipmentOee(oeeData)
    await loadGatewayHealthChecks()
    await loadGatewayMessages()
  } catch (error) {
    warnDevFallback('设备接口不可用', error)
    if (__DEV_MOCK_FALLBACK__) {
      equipmentRows.value = fallbackEquipments.map(mapEquipment)
      eventRows.value = fallbackEvents.map(mapEvent)
      sampleRows.value = fallbackSamples.map(mapSample)
      pmRows.value = fallbackPmTasks.map(mapPmTask)
      recipeRows.value = fallbackRecipeCommands.map(mapRecipeCommand)
      statusRows.value = fallbackStatusHistories.map(mapStatusHistory)
      cycleRows.value = fallbackCycleSamples.map(mapCycleSample)
      standardCycleRows.value = fallbackStandardCycles.map(mapStandardCycle)
      gatewayDriverRows.value = fallbackGatewayDrivers
      gatewayRows.value = fallbackGateways.map(mapGateway)
      gatewayMessageRows.value = fallbackGatewayMessages.map(mapGatewayMessage)
      gatewayHealthRows.value = fallbackGatewayHealthChecks.map(mapGatewayHealthCheck)
      equipmentOee.value = fallbackEquipmentOee
    }
  } finally {
    loading.value = false
  }
}

function formatNumber(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return value ?? '-'
  return number.toFixed(Math.abs(number) < 1 ? 6 : 2).replace(/0+$/, '').replace(/\.$/, '')
}

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toTimeString().slice(0, 5)
}

function statusType(status) {
  if (['RUNNING', 'OK', 'COMPLETED', 'CLOSED', 'PASS'].includes(status)) return 'green'
  if (['IDLE'].includes(status)) return 'blue'
  if (['OPEN', 'OVERDUE', 'ALARM', 'DOWN', 'NG'].includes(status)) return 'red'
  if (['PM', 'PROCESSING', 'WARN', 'WARNING'].includes(status)) return 'amber'
  return 'gray'
}

function gatewayStatusType(status) {
  if (['CONNECTED', 'PROCESSED', 'ACTIVE', 'PASS'].includes(status)) return 'green'
  if (['DEGRADED', 'RECEIVED', 'PROCESSING', 'WARN', 'CHECKING'].includes(status)) return 'amber'
  if (['FAILED', 'FAIL', 'DISCONNECTED', 'DOWN'].includes(status)) return 'red'
  return 'blue'
}

function eventType(status, level) {
  if (level === 'P1' || status === 'OPEN') return 'red'
  if (status === 'PROCESSING') return 'amber'
  if (status === 'CLOSED') return 'green'
  return 'blue'
}

onMounted(loadEquipmentData)
</script>

<style scoped>
.equipment-cell {
  border-color: var(--mes-line);
  color: var(--mes-text);
  cursor: pointer;
  text-align: left;
}

.equipment-cell.selected {
  border-color: #c3c3ba;
  background: var(--mes-paper-muted);
  box-shadow: inset 0 0 0 1px var(--mes-line-soft);
}

.gateway-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.gateway-wide {
  grid-column: 1 / -1;
}

.gateway-cell {
  min-height: 92px;
  border-color: var(--mes-line);
  color: var(--mes-text);
  cursor: pointer;
  text-align: left;
}

.gateway-cell.selected {
  border-color: #c3c3ba;
  background: var(--mes-paper-muted);
  box-shadow: inset 0 0 0 1px var(--mes-line-soft);
}

.gateway-cell em {
  color: var(--mes-sub);
  font-size: 12px;
  font-style: normal;
}

.equipment-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.equipment-form .wide {
  grid-column: span 2;
}

.mes-btn.tiny {
  height: 26px;
  padding: 0 9px;
  font-size: 12px;
}

.mes-btn:disabled {
  cursor: not-allowed;
  opacity: 0.52;
}

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

.oee-factor span {
  color: var(--mes-sub);
  font-size: 12px;
}

.oee-factor strong {
  font-size: 24px;
  line-height: 1;
}

.oee-factor em {
  color: var(--mes-sub);
  font-size: 12px;
  font-style: normal;
}

.oee-note {
  margin-top: 10px;
  color: var(--mes-sub);
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 960px) {
  .equipment-form {
    grid-template-columns: 1fr;
  }

  .equipment-form .wide {
    grid-column: span 1;
  }

  .oee-strip {
    grid-template-columns: 1fr;
  }

  .gateway-list {
    grid-template-columns: 1fr;
  }
}
</style>
