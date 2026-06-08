<template>
  <section>
    <div class="page-head">
      <div>
        <h1 class="page-title">物料与载具 / 批次消耗、FIFO 与 Carrier 管控</h1>
        <p class="page-desc">物料、载具、Panel/SN 的关联是追溯根因分析的重要证据。</p>
      </div>
      <div class="page-actions">
        <button class="mes-btn" :disabled="loading" @click="loadMaterialData">刷新</button>
        <button v-if="canWmsAction" class="mes-btn" @click="setWmsAction('RECEIVE')">入库</button>
        <button v-if="canWmsAction" class="mes-btn" @click="setWmsAction('FREEZE')">冻结</button>
        <button class="mes-btn primary" :disabled="loading" @click="loadMaterialData">齐套检查</button>
      </div>
    </div>

    <div class="mes-grid cols-3">
      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">物料批次库存</div>
          <span class="status-tag" :class="readinessType">{{ readinessText }}</span>
        </div>
        <div class="mes-card__body cards">
          <div v-for="item in materialLots" :key="item.code" class="mini-card" @click="selectBatch(item.code)">
            <div class="mini-top">
              <span>{{ item.code }}</span>
              <span class="status-tag" :class="item.type">{{ item.status }}</span>
            </div>
            <div class="mini-meta">{{ item.meta }}</div>
            <div class="mini-stock">
              <span>冻结 {{ item.frozen }}</span>
              <span>退料 {{ item.returned }}</span>
              <span>质量 {{ item.quality }}</span>
              <span>版本 {{ item.version }}</span>
            </div>
          </div>
          <div class="adapter-strip">
            <div class="adapter-status">
              <span class="status-tag" :class="wmsAdapterResultType">{{ wmsAdapterResultText }}</span>
              <span>通过模拟 WMS Adapter 进入 MES，成功与失败都会写审计。</span>
            </div>
            <div class="adapter-actions">
              <button
                v-if="canWmsAction"
                class="mes-btn"
                :disabled="wmsAdapterSubmitting !== ''"
                @click="checkWmsReadinessByAdapter"
              >Adapter 齐套</button>
              <button
                v-if="canWmsAction"
                class="mes-btn"
                :disabled="wmsAdapterSubmitting !== ''"
                @click="submitWmsAdapterTransaction"
              >Adapter 事务</button>
            </div>
          </div>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">Carrier / Cassette</div>
          <span class="status-tag teal">在用 {{ boundCarrierCount }}</span>
        </div>
        <div class="mes-card__body">
          <table class="mes-table">
            <thead><tr><th>载具</th><th>Lot</th><th>工序</th><th>状态</th></tr></thead>
            <tbody>
              <tr v-for="carrier in carriers" :key="carrier.code">
                <td>{{ carrier.code }}</td><td>{{ carrier.lot }}</td><td>{{ carrier.step }}</td>
                <td><span class="status-tag" :class="carrier.type">{{ carrier.status }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">投料校验</div>
          <span class="status-tag" :class="readinessType">{{ readinessText }}</span>
        </div>
        <div class="mes-card__body">
          <div class="matrix one">
            <div v-for="check in checks" :key="check.title" class="check-cell" :class="check.type">
              <strong>{{ check.title }}</strong><span>{{ check.text }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="mes-grid cols-2 section-gap">
      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">WMS 库存事务</div>
          <span class="status-tag" :class="canWmsAction ? 'green' : 'gray'">{{ canWmsAction ? '可执行' : '只读' }}</span>
        </div>
        <div class="mes-card__body">
          <div class="wms-actions">
            <button
              v-for="action in wmsActions"
              :key="action.value"
              class="wms-action"
              :class="{ active: wmsForm.action === action.value }"
              type="button"
              @click="setWmsAction(action.value)"
            >
              {{ action.label }}
            </button>
          </div>

          <div class="wms-form">
            <div v-if="wmsForm.action === 'RECEIVE'" class="mes-field">
              <label>批次号</label>
              <input v-model="wmsForm.batchNo" class="mes-input" placeholder="留空自动生成" />
            </div>
            <div v-else class="mes-field">
              <label>目标批次</label>
              <select v-model="wmsForm.batchNo" class="mes-select">
                <option value="">请选择</option>
                <option v-for="batch in materialLots" :key="batch.code" :value="batch.code">{{ batch.code }}</option>
              </select>
            </div>

            <div v-if="wmsForm.action === 'RECEIVE'" class="mes-field">
              <label>物料编码</label>
              <input v-model="wmsForm.materialCode" class="mes-input" />
            </div>
            <div v-if="wmsForm.action === 'RECEIVE'" class="mes-field">
              <label>物料名称</label>
              <input v-model="wmsForm.materialName" class="mes-input" />
            </div>

            <div v-if="wmsForm.action !== 'COUNT'" class="mes-field">
              <label>数量</label>
              <input v-model="wmsForm.qty" class="mes-input" inputmode="decimal" />
            </div>
            <div v-else class="mes-field">
              <label>盘点可用量</label>
              <input v-model="wmsForm.countedAvailableQty" class="mes-input" inputmode="decimal" />
            </div>

            <div class="mes-field">
              <label>单位</label>
              <input v-model="wmsForm.unit" class="mes-input" />
            </div>
            <div v-if="wmsForm.action === 'RECEIVE'" class="mes-field">
              <label>库位</label>
              <select v-model="wmsForm.location" class="mes-select">
                <option value="">自动分配</option>
                <option v-for="location in materialLocationRows" :key="location.locationCode" :value="location.locationCode">
                  {{ location.locationCode }} / {{ location.storageType }}
                </option>
              </select>
            </div>
            <div class="mes-field wide">
              <label>原因</label>
              <input v-model="wmsForm.reason" class="mes-input" />
            </div>
            <div class="mes-field">
              <label>操作员</label>
              <input v-model="wmsForm.operator" class="mes-input" />
            </div>
          </div>

          <div class="wms-footer">
            <div class="wms-stock">
              <span class="status-tag blue">{{ currentActionLabel }}</span>
              <span>{{ stockSummary }}</span>
            </div>
            <button
              v-if="canWmsAction"
              class="mes-btn primary"
              :disabled="wmsSubmitting"
              @click="submitWmsAction"
            >
              {{ wmsSubmitting ? '提交中' : `执行${currentActionLabel}` }}
            </button>
          </div>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">来料质检 / COA 留痕</div>
          <span class="status-tag" :class="canIqcAction ? 'green' : 'gray'">{{ canIqcAction ? '可判定' : '只读' }}</span>
        </div>
        <div class="mes-card__body">
          <div class="wms-actions">
            <button
              v-for="result in iqcResults"
              :key="result.value"
              class="wms-action"
              :class="{ active: iqcForm.result === result.value }"
              type="button"
              @click="setIqcResult(result.value)"
            >
              {{ result.label }}
            </button>
          </div>

          <div class="wms-form iqc-form">
            <div class="mes-field">
              <label>目标批次</label>
              <select v-model="iqcForm.batchNo" class="mes-select">
                <option value="">请选择</option>
                <option v-for="batch in materialLots" :key="batch.code" :value="batch.code">{{ batch.code }}</option>
              </select>
            </div>
            <div class="mes-field">
              <label>检验数量</label>
              <input v-model="iqcForm.inspectedQty" class="mes-input" inputmode="decimal" />
            </div>
            <div class="mes-field">
              <label>抽检数量</label>
              <input v-model="iqcForm.sampleQty" class="mes-input" inputmode="decimal" />
            </div>
            <div class="mes-field">
              <label>COA 编号</label>
              <input v-model="iqcForm.coaNo" class="mes-input" />
            </div>
            <div class="mes-field">
              <label>附件名</label>
              <input v-model="iqcForm.fileName" class="mes-input" />
            </div>
            <div class="mes-field">
              <label>附件地址</label>
              <input v-model="iqcForm.fileUrl" class="mes-input" />
            </div>
            <div class="mes-field">
              <label>校验摘要</label>
              <input v-model="iqcForm.fileHash" class="mes-input" />
            </div>
            <div class="mes-field">
              <label>缺陷代码</label>
              <input v-model="iqcForm.defectCode" class="mes-input" />
            </div>
            <div class="mes-field">
              <label>检验员</label>
              <input v-model="iqcForm.inspector" class="mes-input" />
            </div>
            <div class="mes-field wide">
              <label>处置结论</label>
              <input v-model="iqcForm.conclusion" class="mes-input" />
            </div>
          </div>

          <div class="wms-footer">
            <div class="wms-stock">
              <span class="status-tag" :class="statusType(iqcForm.result)">{{ iqcForm.result }}</span>
              <span>{{ iqcSummary }}</span>
            </div>
            <button
              v-if="canIqcAction"
              class="mes-btn primary"
              :disabled="iqcSubmitting"
              @click="submitIqcAction"
            >
              {{ iqcSubmitting ? '提交中' : '提交来料判定' }}
            </button>
          </div>

          <div class="iqc-history">
            <table class="mes-table">
              <thead><tr><th>结果</th><th>批次</th><th>COA</th><th>结论</th><th>附件</th><th>时间</th></tr></thead>
              <tbody>
                <tr v-for="record in iqcInspections" :key="record.key">
                  <td><span class="status-tag" :class="record.type">{{ record.result }}</span></td>
                  <td>{{ record.batchNo }}</td>
                  <td>{{ record.coaNo }}</td>
                  <td>{{ record.conclusion }}</td>
                  <td>{{ record.attachmentCount }}</td>
                  <td>{{ record.time }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">库存事务履历</div>
          <span class="status-tag blue">{{ inventoryTxns.length }} 条</span>
        </div>
        <div class="mes-card__body">
          <table class="mes-table">
            <thead><tr><th>类型</th><th>批次</th><th>变更</th><th>可用量</th><th>操作员</th><th>时间</th></tr></thead>
            <tbody>
              <tr v-for="txn in inventoryTxns" :key="txn.key">
                <td><span class="status-tag" :class="txn.type">{{ txn.txnType }}</span></td>
                <td>{{ txn.batchNo }}</td>
                <td>{{ txn.qty }}</td>
                <td>{{ txn.available }}</td>
                <td>{{ txn.operator }}</td>
                <td>{{ txn.time }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="mes-card section-gap">
      <div class="mes-card__head">
        <div class="mes-card__title">库位策略 / 容量管控</div>
        <span class="status-tag" :class="lockedLocationCount > 0 ? 'amber' : 'green'">
          锁定 {{ lockedLocationCount }}
        </span>
      </div>
      <div class="mes-card__body">
        <table class="mes-table">
          <thead>
            <tr>
              <th>库位</th>
              <th>区域</th>
              <th>类型</th>
              <th>物料类</th>
              <th>占用</th>
              <th>环境</th>
              <th>优先级</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="location in materialLocationRows" :key="location.locationCode">
              <td>{{ location.locationCode }}</td>
              <td>{{ location.zoneCode }}</td>
              <td>{{ location.storageType }}</td>
              <td>{{ location.materialClass }}</td>
              <td>{{ location.usageText }}</td>
              <td>{{ location.temperatureWindow }} / {{ location.humidityWindow }}</td>
              <td>{{ location.strategyPriority }}</td>
              <td><span class="status-tag" :class="location.type">{{ location.status }}</span></td>
            </tr>
            <tr v-if="!materialLocationRows.length">
              <td colspan="8" class="empty-cell">暂无库位策略数据</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="mes-card section-gap">
      <div class="mes-card__head">
        <div class="mes-card__title">库位任务 / 上架移库盘点</div>
        <span class="status-tag blue">{{ locationTaskRows.length }} 条</span>
      </div>
      <div class="mes-card__body">
        <div class="location-task-shell">
          <div class="location-task-panel">
            <div class="wms-actions">
              <button
                v-for="taskType in locationTaskTypes"
                :key="taskType.value"
                class="wms-action"
                :class="{ active: locationTaskForm.taskType === taskType.value }"
                type="button"
                @click="setLocationTaskType(taskType.value)"
              >
                {{ taskType.label }}
              </button>
            </div>

            <div class="wms-form location-task-form">
              <div class="mes-field">
                <label>目标批次</label>
                <select v-model="locationTaskForm.batchNo" class="mes-select">
                  <option value="">请选择</option>
                  <option v-for="batch in materialLots" :key="batch.code" :value="batch.code">{{ batch.code }}</option>
                </select>
              </div>
              <div v-if="locationTaskForm.taskType !== 'COUNT'" class="mes-field">
                <label>目标库位</label>
                <select v-model="locationTaskForm.targetLocation" class="mes-select">
                  <option value="">请选择</option>
                  <option
                    v-for="location in materialLocationRows"
                    :key="location.locationCode"
                    :value="location.locationCode"
                    :disabled="location.status !== 'ACTIVE'"
                  >
                    {{ location.locationCode }} / {{ location.storageType }} / {{ location.status }}
                  </option>
                </select>
              </div>
              <div class="mes-field">
                <label>{{ locationTaskForm.taskType === 'COUNT' ? '实盘可用' : '任务数量' }}</label>
                <input v-model="locationTaskForm.qty" class="mes-input" inputmode="decimal" :placeholder="locationTaskForm.taskType === 'COUNT' ? '必填' : '留空整批'" />
              </div>
              <div class="mes-field wide">
                <label>原因</label>
                <input v-model="locationTaskForm.reason" class="mes-input" />
              </div>
              <div class="mes-field">
                <label>操作员</label>
                <input v-model="locationTaskForm.operator" class="mes-input" />
              </div>
            </div>

            <div class="wms-footer">
              <div class="wms-stock">
                <span class="status-tag blue">{{ currentLocationTaskLabel }}</span>
                <span>{{ locationTaskSummary }}</span>
              </div>
              <button
                v-if="canWmsAction"
                class="mes-btn primary"
                :disabled="locationTaskSubmitting"
                @click="submitLocationTask"
              >
                {{ locationTaskSubmitting ? '提交中' : `创建${currentLocationTaskLabel}` }}
              </button>
            </div>
          </div>

          <div class="location-task-table">
            <table class="mes-table">
              <thead>
                <tr>
                  <th>任务</th>
                  <th>批次</th>
                  <th>源/目标库位</th>
                  <th>数量</th>
                  <th>状态</th>
                  <th>执行人</th>
                  <th>时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="task in locationTaskRows" :key="task.key">
                  <td>
                    <div class="task-main">
                      <strong>{{ task.taskLabel }}</strong>
                      <span>{{ task.taskNo }}</span>
                    </div>
                  </td>
                  <td>{{ task.batchNo }}</td>
                  <td>{{ task.sourceLocation }} → {{ task.targetLocation }}</td>
                  <td>{{ task.qty }}</td>
                  <td><span class="status-tag" :class="task.type">{{ task.status }}</span></td>
                  <td>{{ task.assignedTo || task.operator }}</td>
                  <td>{{ task.time }}</td>
                  <td>
                    <div class="task-actions">
                      <button
                        v-if="canWmsAction && task.canAssign"
                        class="mes-btn tiny"
                        :disabled="locationTaskSubmitting"
                        @click="assignLocationTask(task)"
                      >
                        领取
                      </button>
                      <button
                        v-if="canWmsAction && task.canComplete"
                        class="mes-btn tiny primary"
                        :disabled="locationTaskSubmitting"
                        @click="completeLocationTask(task)"
                      >
                        完成
                      </button>
                      <button
                        v-if="canWmsAction && task.canCancel"
                        class="mes-btn tiny warn"
                        :disabled="locationTaskSubmitting"
                        @click="cancelLocationTask(task)"
                      >
                        取消
                      </button>
                    </div>
                  </td>
                </tr>
                <tr v-if="!locationTaskRows.length">
                  <td colspan="8" class="empty-cell">暂无库位任务</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>

    <div class="mes-grid cols-2 section-gap">
      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">供应商准入 / 绩效</div>
          <span class="status-tag" :class="highRiskSupplierCount > 0 ? 'red' : 'green'">
            风险 {{ highRiskSupplierCount }}
          </span>
        </div>
        <div class="mes-card__body supplier-panel">
          <table class="mes-table">
            <thead>
              <tr>
                <th>供应商</th>
                <th>准入</th>
                <th>评分 / 通过率</th>
                <th>NG/HOLD</th>
                <th>8D</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="supplier in supplierRows" :key="supplier.key">
                <td>
                  <div class="supplier-main">
                    <strong>{{ supplier.supplierCode }}</strong>
                    <span>{{ supplier.supplierName }} / {{ supplier.materialClass }}</span>
                  </div>
                </td>
                <td><span class="status-tag" :class="supplier.type">{{ supplier.qualificationStatus }}</span></td>
                <td>
                  <div class="supplier-score">
                    <strong>{{ supplier.scoreText }}</strong>
                    <span><i :style="{ width: `${supplier.score}%` }"></i></span>
                  </div>
                </td>
                <td>{{ supplier.passRateText }}</td>
                <td>{{ supplier.ngCount }} / {{ supplier.holdCount }}</td>
                <td>{{ supplier.openActionCount }} / {{ supplier.overdueActionCount }}</td>
                <td>
                  <div class="task-actions">
                    <button
                      v-if="canSupplierAction"
                      class="mes-btn tiny"
                      :disabled="supplierSubmitting"
                      @click="evaluateSupplier(supplier)"
                    >
                      评估
                    </button>
                    <button
                      v-if="canSupplierAction"
                      class="mes-btn tiny"
                      :disabled="supplierSubmitting"
                      @click="createSupplierReview(supplier)"
                    >
                      复审
                    </button>
                  </div>
                </td>
              </tr>
              <tr v-if="!supplierRows.length">
                <td colspan="6" class="empty-cell">暂无供应商准入数据</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">供应商 8D 整改</div>
          <span class="status-tag" :class="openSupplierActionCount > 0 ? 'amber' : 'green'">
            未关闭 {{ openSupplierActionCount }}
          </span>
        </div>
        <div class="mes-card__body supplier-panel">
          <div class="supplier-action-form">
            <select v-model="supplierActionForm.supplierCode" class="mes-select">
              <option value="">供应商</option>
              <option v-for="supplier in supplierRows" :key="supplier.supplierCode" :value="supplier.supplierCode">
                {{ supplier.supplierCode }}
              </option>
            </select>
            <select v-model="supplierActionForm.severity" class="mes-select">
              <option value="LOW">LOW</option>
              <option value="MEDIUM">MEDIUM</option>
              <option value="HIGH">HIGH</option>
              <option value="CRITICAL">CRITICAL</option>
            </select>
            <input v-model="supplierActionForm.dueDays" class="mes-input" inputmode="numeric" />
            <input v-model="supplierActionForm.issueSummary" class="mes-input action-summary" />
            <button
              v-if="canSupplierAction"
              class="mes-btn primary"
              :disabled="supplierSubmitting"
              @click="submitSupplierAction"
            >
              创建8D
            </button>
          </div>
          <table class="mes-table">
            <thead>
              <tr>
                <th>整改单</th>
                <th>供应商</th>
                <th>严重度</th>
                <th>状态</th>
                <th>到期</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="action in supplierActionRows" :key="action.key">
                <td>
                  <div class="supplier-main">
                    <strong>{{ action.actionNo }}</strong>
                    <span>{{ action.issueSummary }}</span>
                  </div>
                </td>
                <td>{{ action.supplierCode }}</td>
                <td>{{ action.severity }}</td>
                <td><span class="status-tag" :class="action.type">{{ action.status }}</span></td>
                <td>{{ action.dueTime }}</td>
                <td>
                  <button
                    v-if="canSupplierAction && action.canClose"
                    class="mes-btn tiny"
                    :disabled="supplierSubmitting"
                    @click="closeSupplierAction(action)"
                  >
                    关闭
                  </button>
                </td>
              </tr>
              <tr v-if="!supplierActionRows.length">
                <td colspan="6" class="empty-cell">暂无供应商8D整改单</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="mes-card">
        <div class="mes-card__head">
          <div class="mes-card__title">供应商准入复审</div>
          <span class="status-tag" :class="openSupplierReviewCount > 0 ? 'amber' : 'green'">
            待办 {{ openSupplierReviewCount }}
          </span>
        </div>
        <div class="mes-card__body supplier-panel">
          <table class="mes-table">
            <thead>
              <tr>
                <th>任务</th>
                <th>供应商</th>
                <th>建议</th>
                <th>状态</th>
                <th>到期</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="review in supplierReviewRows" :key="review.key">
                <td>
                  <div class="supplier-main">
                    <strong>{{ review.taskNo }}</strong>
                    <span>{{ review.triggerReason }}</span>
                  </div>
                </td>
                <td>{{ review.supplierCode }}</td>
                <td>{{ review.suggestedQualification }} / {{ review.suggestedRisk }}</td>
                <td><span class="status-tag" :class="review.type">{{ review.reviewStatus }}</span></td>
                <td>{{ review.dueTime }}</td>
                <td>
                  <div class="task-actions">
                    <button
                      v-if="canSupplierAction && review.canDecide"
                      class="mes-btn tiny primary"
                      :disabled="supplierSubmitting"
                      @click="decideSupplierReview(review, 'APPROVE')"
                    >
                      通过
                    </button>
                    <button
                      v-if="canSupplierAction && review.canDecide"
                      class="mes-btn tiny warn"
                      :disabled="supplierSubmitting"
                      @click="decideSupplierReview(review, 'REJECT')"
                    >
                      驳回
                    </button>
                  </div>
                </td>
              </tr>
              <tr v-if="!supplierReviewRows.length">
                <td colspan="6" class="empty-cell">暂无供应商准入复审任务</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <div class="mes-card section-gap">
      <div class="mes-card__head">
        <div class="mes-card__title">供应商月度评分趋势</div>
        <span class="status-tag" :class="supplierTrendRiskCount > 0 ? 'amber' : 'green'">
          关注 {{ supplierTrendRiskCount }}
        </span>
      </div>
      <div class="mes-card__body supplier-trend-list">
        <div v-for="supplier in supplierTrendRows" :key="supplier.key" class="supplier-trend-row">
          <div class="supplier-trend-main">
            <div class="supplier-main">
              <strong>{{ supplier.supplierCode }}</strong>
              <span>{{ supplier.supplierName }} / {{ supplier.materialClass }}</span>
            </div>
            <span class="status-tag" :class="supplier.type">{{ supplier.latestRiskLevel }}</span>
          </div>
          <div class="supplier-trend-bars" aria-label="供应商评分趋势">
            <div v-for="point in supplier.trend" :key="point.period" class="supplier-trend-point">
              <span class="supplier-trend-bar">
                <i :class="point.type" :style="{ height: `${point.barHeight}%` }"></i>
              </span>
              <b>{{ point.scoreText }}</b>
              <small>{{ point.periodShort }}</small>
            </div>
          </div>
          <div class="supplier-trend-meta">
            <span>最新 {{ supplier.latestScoreText }} / 通过率 {{ supplier.latestPassRateText }}</span>
            <span>8D {{ supplier.actionWindowCount }} / 超期 {{ supplier.overdueWindowCount }}</span>
          </div>
          <p>{{ supplier.summary }}</p>
        </div>
        <div v-if="!supplierTrendRows.length" class="empty-cell">暂无供应商月度评分趋势</div>
      </div>
    </div>

    <div class="mes-card section-gap">
      <div class="mes-card__head">
        <div class="mes-card__title">物料消耗履历</div>
        <span class="status-tag blue">{{ consumeRecords.length }} 条</span>
      </div>
      <div class="mes-card__body">
        <table class="mes-table">
          <thead><tr><th>Lot</th><th>工序</th><th>物料批次</th><th>消耗</th><th>操作员</th><th>时间</th><th>追溯状态</th></tr></thead>
          <tbody>
            <tr v-for="record in consumeRecords" :key="record.lot + record.time">
              <td>{{ record.lot }}</td><td>{{ record.step }}</td><td>{{ record.batch }}</td><td>{{ record.qty }}</td>
              <td>{{ record.operator }}</td><td>{{ record.time }}</td>
              <td><span class="status-tag" :class="record.type">{{ record.status }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  assignMaterialLocationTask,
  cancelMaterialLocationTask,
  checkWmsMaterialReadiness,
  completeMaterialLocationTask,
  countMaterialInventory,
  createMaterialIncomingInspection,
  createMaterialLocationTask,
  createSupplierCorrectiveAction,
  createSupplierQualificationReview,
  closeSupplierCorrectiveAction,
  decideSupplierQualificationReview,
  evaluateMaterialSupplierQualification,
  freezeMaterial,
  getCarriers,
  getMaterialBatches,
  getMaterialConsumptions,
  getMaterialIncomingInspections,
  getMaterialInventoryTransactions,
  getMaterialLocationTasks,
  getMaterialLocations,
  getMaterialSupplierPerformance,
  getMaterialSupplierTrends,
  getMaterialSuppliers,
  getSupplierCorrectiveActions,
  getSupplierQualificationReviews,
  ingestWmsInventoryTransaction,
  receiveMaterial,
  returnMaterial,
  unfreezeMaterial
} from '@/api/pilot'
import { hasButton } from '@/utils/permissions'
import { warnDevFallback } from '@/utils/devFallback'

const fallbackMaterialLots = [
  { batchNo: 'PI260606-A', materialCode: 'PI_INK', materialName: 'PI 胶', availableQty: 820, reservedQty: 120, frozenQty: 0, returnedQty: 0, stockVersion: 1, unit: 'g', remainPercent: 18, status: 'WARNING', location: 'WMS-A01' },
  { batchNo: 'OLED-R-260605-B', materialCode: 'OLED_R', materialName: '红光有机材料', availableQty: 310, reservedQty: 42, frozenQty: 12, returnedQty: 0, stockVersion: 2, unit: 'g', remainPercent: 62, status: 'OK', location: 'COLD-02' },
  { batchNo: 'ENCAP260604-C', materialCode: 'ENCAP_GLUE', materialName: '封装胶', availableQty: 540, reservedQty: 80, frozenQty: 0, returnedQty: 6, stockVersion: 3, unit: 'g', remainPercent: 76, status: 'OK', location: 'WMS-B03' }
]

const fallbackCarriers = [
  { code: 'CST-260606-001', lot: 'LOT202406001', step: 'COATING', status: 'BOUND', type: 'green' },
  { code: 'CST-260606-002', lot: '-', step: '-', status: 'IDLE', type: 'blue' },
  { code: 'TRAY-260606-009', lot: '-', step: '-', status: 'CLEANING', type: 'amber' }
]

const fallbackChecks = [
  { title: 'BOM关键物料', text: '已配置 3 批', type: 'green' },
  { title: '批次质量', text: '来料质量 PASS', type: 'green' },
  { title: 'FIFO库存', text: 'PI 胶低库存', type: 'amber' },
  { title: '齐套结果', text: 'PASS_WITH_WARNING', type: 'amber' }
]

const fallbackConsumeRecords = [
  { lot: 'LOT202406001', step: 'COATING', batch: 'PI260606-A', qty: '42.8g', operator: 'op1007', time: '13:42', status: 'TRACEABLE', type: 'green' },
  { lot: 'LOT202406004', step: 'EVAPORATION', batch: 'OLED-R-260605-B', qty: '8.2g', operator: 'op1011', time: '14:08', status: 'TRACEABLE', type: 'green' }
]

const fallbackTxns = [
  { txnNo: 'TXN-FALLBACK-001', txnType: 'FREEZE', batchNo: 'OLED-R-260605-B', qtyDelta: -12, availableBefore: 322, availableAfter: 310, unit: 'g', operator: 'admin', txnTime: new Date().toISOString() },
  { txnNo: 'TXN-FALLBACK-002', txnType: 'RETURN', batchNo: 'ENCAP260604-C', qtyDelta: 6, availableBefore: 534, availableAfter: 540, unit: 'g', operator: 'admin', txnTime: new Date().toISOString() }
]

const fallbackIqcInspections = [
  { inspectionNo: 'MIQC-FALLBACK-001', batchNo: 'PI260606-A', result: 'PASS', coaNo: 'COA-PI260606-A', conclusion: '来料黏度、固含量与外观复核通过。', attachmentCount: 1, inspector: 'qe1003', inspectionTime: new Date().toISOString() },
  { inspectionNo: 'MIQC-FALLBACK-002', batchNo: 'OLED-R-260605-B', result: 'PASS', coaNo: 'COA-OLED-R-260605-B', conclusion: 'COA 参数与抽检结果一致。', attachmentCount: 1, inspector: 'qe1003', inspectionTime: new Date().toISOString() }
]

const fallbackSupplierPerformance = [
  { supplierCode: 'SUP-OLED-02', supplierName: 'OLED有机材料供应商B', qualificationStatus: 'CONDITIONAL', materialClass: 'ORGANIC', score: 84, scoreText: '84.0', passRate: 92, passRateText: '92.0%', batchCount: 4, inspectionCount: 4, passCount: 2, holdCount: 1, ngCount: 1, riskBatchCount: 2, riskLevel: 'HIGH', openActionCount: 1, overdueActionCount: 0, latestActionNo: 'SCA-SEED-OLED-001', type: 'amber' },
  { supplierCode: 'SUP-PI-01', supplierName: 'PI材料供应商A', qualificationStatus: 'QUALIFIED', materialClass: 'CHEMICAL', score: 100, scoreText: '100.0', passRate: 100, passRateText: '100.0%', batchCount: 7, inspectionCount: 6, passCount: 6, holdCount: 0, ngCount: 0, riskBatchCount: 0, riskLevel: 'LOW', openActionCount: 0, overdueActionCount: 0, latestActionNo: '', type: 'green' }
]

const fallbackSupplierActions = [
  { actionNo: 'SCA-SEED-OLED-001', supplierCode: 'SUP-OLED-02', sourceType: 'IQC', sourceNo: 'MIQC-SEED-OLED-R-260605-B', issueSummary: '有机材料批次稳定性需持续确认', owner: 'qe1003', severity: 'MEDIUM', status: 'OPEN', dueTime: new Date(Date.now() + 5 * 86400000).toISOString(), createdTime: new Date().toISOString(), type: 'amber' }
]

const fallbackSupplierReviews = [
  {
    taskNo: 'SQR-SEED-OLED-001',
    supplierCode: 'SUP-OLED-02',
    reviewType: 'PERIODIC',
    sourceNo: 'SCA-SEED-OLED-001',
    triggerReason: '供应商存在未关闭8D和批次稳定性风险，需完成月度准入复审',
    qualificationBefore: 'CONDITIONAL',
    riskBefore: 'MEDIUM',
    suggestedQualification: 'CONDITIONAL',
    suggestedRisk: 'HIGH',
    reviewStatus: 'OPEN',
    dueTime: new Date(Date.now() + 7 * 86400000).toISOString(),
    canDecide: true,
    type: 'red'
  }
]

const fallbackSupplierTrends = [
  {
    supplierCode: 'SUP-OLED-02',
    supplierName: 'OLED有机材料供应商B',
    materialClass: 'ORGANIC',
    latestScore: 55,
    latestScoreText: '55.0',
    latestPassRate: 50,
    latestPassRateText: '50.0%',
    latestRiskLevel: 'HIGH',
    actionWindowCount: 2,
    overdueWindowCount: 0,
    summary: '近周期存在 IQC NG 与未关闭8D，需维持条件准入并跟踪三批趋势。',
    type: 'red',
    trend: [
      fallbackTrendPoint(-5, 88, 96, 'MEDIUM', 'amber'),
      fallbackTrendPoint(-4, 86.5, 95, 'MEDIUM', 'amber'),
      fallbackTrendPoint(-3, 84, 92, 'MEDIUM', 'amber'),
      fallbackTrendPoint(-2, 77.5, 88, 'MEDIUM', 'amber'),
      fallbackTrendPoint(-1, 62, 76, 'HIGH', 'red'),
      fallbackTrendPoint(0, 55, 50, 'HIGH', 'red', 1)
    ]
  },
  {
    supplierCode: 'SUP-PI-01',
    supplierName: 'PI材料供应商A',
    materialClass: 'CHEMICAL',
    latestScore: 100,
    latestScoreText: '100.0',
    latestPassRate: 100,
    latestPassRateText: '100.0%',
    latestRiskLevel: 'LOW',
    actionWindowCount: 0,
    overdueWindowCount: 0,
    summary: '近周期供应商批次、IQC 与8D表现稳定。',
    type: 'green',
    trend: [
      fallbackTrendPoint(-5, 98, 100, 'LOW', 'green'),
      fallbackTrendPoint(-4, 99, 100, 'LOW', 'green'),
      fallbackTrendPoint(-3, 100, 100, 'LOW', 'green'),
      fallbackTrendPoint(-2, 100, 100, 'LOW', 'green'),
      fallbackTrendPoint(-1, 99.5, 100, 'LOW', 'green'),
      fallbackTrendPoint(0, 100, 100, 'LOW', 'green')
    ]
  }
]

const fallbackMaterialLocations = [
  { locationCode: 'WH-A01', zoneCode: 'CHEM-A', storageType: 'CHEMICAL', materialClass: 'CHEMICAL', status: 'ACTIVE', capacityQty: 5000, usedQty: 940, availableQty: 4060, unit: 'g', temperatureWindow: '18 ~ 25℃', humidityWindow: '30 ~ 55%RH', strategyPriority: 20, type: 'green' },
  { locationCode: 'WH-C02', zoneCode: 'ORG-COLD', storageType: 'COLD', materialClass: 'ORGANIC', status: 'ACTIVE', capacityQty: 2000, usedQty: 322, availableQty: 1678, unit: 'g', temperatureWindow: '2 ~ 8℃', humidityWindow: '20 ~ 45%RH', strategyPriority: 30, type: 'green' },
  { locationCode: 'WH-HOLD', zoneCode: 'HOLD', storageType: 'QUARANTINE', materialClass: 'ANY', status: 'LOCKED', capacityQty: 1000, usedQty: 0, availableQty: 1000, unit: 'EA', temperatureWindow: '18 ~ 28℃', humidityWindow: '30 ~ 70%RH', strategyPriority: 900, type: 'red' }
]

const fallbackLocationTasks = [
  { taskNo: 'MLT-FB-001', taskType: 'PUTAWAY', batchNo: 'PI260606-A', materialCode: 'PI_INK', materialName: 'PI 胶', sourceLocation: 'WMS-IN', targetLocation: 'WH-A01', plannedQty: 820, actualQty: 0, unit: 'g', status: 'CREATED', reason: '来料上架', operator: 'wms1001', createdTime: new Date().toISOString(), type: 'amber' },
  { taskNo: 'MLT-FB-002', taskType: 'MOVE', batchNo: 'ENCAP260604-C', materialCode: 'ENCAP_GLUE', materialName: '封装胶', sourceLocation: 'WMS-B03', targetLocation: 'WH-A01', plannedQty: 626, actualQty: 0, unit: 'g', status: 'ASSIGNED', assignedTo: 'wms1002', reason: '产线补料前移库', operator: 'wms1002', assignedTime: new Date().toISOString(), type: 'amber' },
  { taskNo: 'MLT-FB-003', taskType: 'COUNT', batchNo: 'OLED-R-260605-B', materialCode: 'OLED_R', materialName: '红光有机材料', sourceLocation: 'COLD-02', targetLocation: 'COLD-02', plannedQty: 310, actualQty: 310, unit: 'g', status: 'DONE', reason: '低温库日盘', operator: 'wms1001', executedTime: new Date().toISOString(), type: 'green' }
]

const wmsActions = [
  { value: 'RECEIVE', label: '入库' },
  { value: 'FREEZE', label: '冻结' },
  { value: 'UNFREEZE', label: '解冻' },
  { value: 'RETURN', label: '退料' },
  { value: 'COUNT', label: '盘点' }
]

const locationTaskTypes = [
  { value: 'MOVE', label: '移库' },
  { value: 'PUTAWAY', label: '上架' },
  { value: 'COUNT', label: '盘点' }
]

const iqcResults = [
  { value: 'PASS', label: 'PASS' },
  { value: 'HOLD', label: 'HOLD' },
  { value: 'NG', label: 'NG' }
]

const rawBatches = ref(__DEV_MOCK_FALLBACK__ ? fallbackMaterialLots : [])
const materialLots = ref(__DEV_MOCK_FALLBACK__ ? fallbackMaterialLots.map(mapBatch) : [])
const carriers = ref(__DEV_MOCK_FALLBACK__ ? fallbackCarriers : [])
const checks = ref(__DEV_MOCK_FALLBACK__ ? fallbackChecks : [])
const consumeRecords = ref(__DEV_MOCK_FALLBACK__ ? fallbackConsumeRecords : [])
const inventoryTxns = ref(__DEV_MOCK_FALLBACK__ ? fallbackTxns.map(mapTransaction) : [])
const iqcInspections = ref(__DEV_MOCK_FALLBACK__ ? fallbackIqcInspections.map(mapInspection) : [])
const supplierRows = ref(__DEV_MOCK_FALLBACK__ ? fallbackSupplierPerformance.map(mapSupplier) : [])
const supplierActionRows = ref(__DEV_MOCK_FALLBACK__ ? fallbackSupplierActions.map(mapSupplierAction) : [])
const supplierReviewRows = ref(__DEV_MOCK_FALLBACK__ ? fallbackSupplierReviews.map(mapSupplierReview) : [])
const supplierTrendRows = ref(__DEV_MOCK_FALLBACK__ ? fallbackSupplierTrends.map(mapSupplierTrend) : [])
const materialLocationRows = ref(__DEV_MOCK_FALLBACK__ ? fallbackMaterialLocations.map(mapMaterialLocation) : [])
const locationTaskRows = ref(__DEV_MOCK_FALLBACK__ ? fallbackLocationTasks.map(mapLocationTask) : [])
const readiness = ref(__DEV_MOCK_FALLBACK__ ? 'PASS_WITH_WARNING' : 'NO_DATA')
const loading = ref(false)
const wmsSubmitting = ref(false)
const wmsAdapterSubmitting = ref('')
const wmsAdapterResult = ref(null)
const locationTaskSubmitting = ref(false)
const iqcSubmitting = ref(false)
const supplierSubmitting = ref(false)

const wmsForm = reactive({
  action: 'FREEZE',
  batchNo: __DEV_MOCK_FALLBACK__ ? fallbackMaterialLots[0].batchNo : '',
  materialCode: 'PI_INK',
  materialName: 'PI 胶',
  qty: '10',
  countedAvailableQty: '100',
  unit: 'g',
  location: __DEV_MOCK_FALLBACK__ ? fallbackMaterialLocations[0].locationCode : 'WMS-IN',
  reason: '试点WMS库存调整',
  operator: localStorage.getItem('username') || 'admin'
})

const locationTaskForm = reactive({
  taskType: 'MOVE',
  batchNo: __DEV_MOCK_FALLBACK__ ? fallbackMaterialLots[0].batchNo : '',
  targetLocation: __DEV_MOCK_FALLBACK__ ? fallbackMaterialLocations[0].locationCode : '',
  qty: '',
  reason: '试点库位任务',
  operator: localStorage.getItem('username') || 'admin'
})

const iqcForm = reactive({
  batchNo: __DEV_MOCK_FALLBACK__ ? fallbackMaterialLots[0].batchNo : '',
  result: 'PASS',
  inspectedQty: '10',
  sampleQty: '3',
  coaNo: __DEV_MOCK_FALLBACK__ ? 'COA-PI260606-A' : '',
  fileName: __DEV_MOCK_FALLBACK__ ? 'COA-PI260606-A.pdf' : '',
  fileUrl: __DEV_MOCK_FALLBACK__ ? 'qms://coa/COA-PI260606-A.pdf' : '',
  fileHash: 'sha256:sample',
  defectCode: '',
  conclusion: '来料复核通过，允许投料。',
  inspector: localStorage.getItem('username') || 'qe1003'
})

const supplierActionForm = reactive({
  supplierCode: __DEV_MOCK_FALLBACK__ ? fallbackSupplierPerformance[0].supplierCode : '',
  issueSummary: '来料批次稳定性异常，要求供应商8D整改',
  severity: 'MEDIUM',
  sourceType: 'MANUAL',
  sourceNo: '',
  owner: localStorage.getItem('username') || 'qe1003',
  dueDays: '7'
})

const canWmsAction = computed(() => hasButton('material:wms'))
const canIqcAction = computed(() => hasButton('material:iqc'))
const canSupplierAction = computed(() => hasButton('material:supplier-manage'))
const boundCarrierCount = computed(() => carriers.value.filter(item => item.status === 'BOUND').length)
const highRiskSupplierCount = computed(() => supplierRows.value.filter(item => item.riskLevel === 'HIGH' || item.qualificationStatus === 'BLOCKED').length)
const openSupplierActionCount = computed(() => supplierActionRows.value.filter(item => item.status !== 'CLOSED').length)
const openSupplierReviewCount = computed(() => supplierReviewRows.value.filter(item => item.reviewStatus === 'OPEN').length)
const supplierTrendRiskCount = computed(() => supplierTrendRows.value.filter(item => item.latestRiskLevel !== 'LOW' || item.overdueWindowCount > 0).length)
const lockedLocationCount = computed(() => materialLocationRows.value.filter(item => item.status !== 'ACTIVE').length)
const readinessText = computed(() => readiness.value || '待检查')
const readinessType = computed(() => {
  if (readiness.value === 'PASS') return 'green'
  if (readiness.value === 'PASS_WITH_WARNING') return 'amber'
  if (readiness.value === 'BLOCKED' || readiness.value === 'NO_DATA') return 'red'
  return 'blue'
})
const currentActionLabel = computed(() => wmsActions.find(item => item.value === wmsForm.action)?.label || '操作')
const currentLocationTaskLabel = computed(() => locationTaskTypes.find(item => item.value === locationTaskForm.taskType)?.label || '任务')
const wmsAdapterResultType = computed(() => {
  const result = wmsAdapterResult.value
  if (!result) return 'blue'
  if (result.result === 'ACCEPTED' || result.readiness === 'PASS') return 'green'
  if (result.readiness === 'PASS_WITH_WARNING') return 'amber'
  return 'red'
})
const wmsAdapterResultText = computed(() => {
  const result = wmsAdapterResult.value
  if (!result) return 'Adapter 待调用'
  if (result.messageType === 'MATERIAL_READINESS') return `齐套 ${result.readiness || '-'}`
  if (result.messageType === 'INVENTORY_TRANSACTION') return `${result.transactionType || '-'} ${result.result || '-'}`
  return result.messageType || 'Adapter 已返回'
})
const selectedBatch = computed(() => rawBatches.value.find(item => item.batchNo === wmsForm.batchNo))
const selectedTaskBatch = computed(() => rawBatches.value.find(item => item.batchNo === locationTaskForm.batchNo))
const selectedIqcBatch = computed(() => rawBatches.value.find(item => item.batchNo === iqcForm.batchNo))
const stockSummary = computed(() => {
  if (wmsForm.action === 'RECEIVE') return '新批次入库后生成库存事务和审计记录'
  const batch = selectedBatch.value
  if (!batch) return '未选择批次'
  return `${batch.materialName || batch.materialCode} / 可用 ${formatQty(batch.availableQty, batch.unit)} / 冻结 ${formatQty(batch.frozenQty, batch.unit)}`
})
const locationTaskSummary = computed(() => {
  const batch = selectedTaskBatch.value
  if (!batch) return '未选择批次'
  if (locationTaskForm.taskType === 'COUNT') {
    return `${batch.materialName || batch.materialCode} / 当前可用 ${formatQty(batch.availableQty, batch.unit)} / 当前库位 ${batch.location || '-'}`
  }
  return `${batch.materialName || batch.materialCode} / 整批在库 ${formatQty(batchPhysicalQty(batch), batch.unit)} / 当前库位 ${batch.location || '-'}`
})
const iqcSummary = computed(() => {
  const batch = selectedIqcBatch.value
  if (!batch) return '未选择批次'
  return `${batch.materialName || batch.materialCode} / 当前质量 ${batch.qualityStatus || '-'} / 库存 ${batch.status || '-'}`
})

function fallbackTrendPoint(monthOffset, score, passRate, riskLevel, type, openActionCount = 0) {
  const date = new Date()
  date.setMonth(date.getMonth() + monthOffset)
  const period = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
  return {
    period,
    score,
    scoreText: score.toFixed(1),
    passRate,
    passRateText: `${passRate.toFixed(1)}%`,
    riskLevel,
    type,
    batchCount: 2,
    inspectionCount: 2,
    riskBatchCount: type === 'red' ? 1 : 0,
    passCount: passRate >= 99 ? 2 : passRate >= 80 ? 1 : 0,
    holdCount: type === 'amber' ? 1 : 0,
    ngCount: type === 'red' ? 1 : 0,
    actionCount: openActionCount,
    openActionCount,
    overdueActionCount: 0
  }
}

function mapBatch(item) {
  const remain = item.remainPercent ?? 0
  const statusText = item.status === 'OK' ? `${remain}%` : item.status || `${remain}%`
  return {
    code: item.batchNo || item.materialCode,
    status: statusText,
    type: item.type || statusType(item.status),
    meta: `${item.materialName || item.materialCode} / 可用 ${formatQty(item.availableQty, item.unit)} / 预留 ${formatQty(item.reservedQty, item.unit)} / ${item.location || 'WMS'}`,
    frozen: formatQty(item.frozenQty, item.unit),
    returned: formatQty(item.returnedQty, item.unit),
    quality: item.qualityStatus || '-',
    version: item.stockVersion || 1
  }
}

function mapCarrier(item) {
  return {
    code: item.code || item.carrierNo,
    lot: item.lot || item.lotNo || '-',
    step: item.step || item.stepCode || '-',
    status: item.status || 'IDLE',
    type: item.type || statusType(item.status)
  }
}

function mapConsumption(item) {
  return {
    lot: item.lot || item.lotNo,
    step: item.step || item.stepCode,
    batch: item.batch || item.batchNo,
    qty: item.qty || formatQty(item.consumedQty, item.unit),
    operator: item.operator || 'system',
    time: item.time || formatTime(item.consumeTime),
    status: item.status || item.traceStatus || 'TRACEABLE',
    type: item.type || statusType(item.status || item.traceStatus)
  }
}

function mapTransaction(item, index = 0) {
  return {
    key: item.txnNo || `${item.batchNo}-${item.txnTime}-${index}`,
    txnType: item.txnType || 'TXN',
    batchNo: item.batchNo || '-',
    qty: formatQty(item.qtyDelta, item.unit),
    available: `${formatQty(item.availableBefore, item.unit)} -> ${formatQty(item.availableAfter, item.unit)}`,
    operator: item.operator || 'system',
    time: formatTime(item.txnTime),
    type: txnType(item.txnType)
  }
}

function mapInspection(item, index = 0) {
  const attachments = Array.isArray(item.attachments) ? item.attachments : []
  return {
    key: item.inspectionNo || `${item.batchNo}-${item.inspectionTime}-${index}`,
    result: item.result || item.status || 'PASS',
    batchNo: item.batchNo || '-',
    coaNo: item.coaNo || '-',
    conclusion: item.conclusion || '-',
    attachmentCount: item.attachmentCount ?? attachments.length,
    inspector: item.inspector || 'system',
    time: formatTime(item.inspectionTime),
    type: item.type || statusType(item.result || item.status)
  }
}

function mapSupplier(item, index = 0) {
  const score = Number(item.score ?? 0)
  const passRate = Number(item.passRate ?? 0)
  const riskLevel = item.riskLevel || 'LOW'
  const qualificationStatus = item.qualificationStatus || 'PENDING'
  return {
    key: item.supplierCode || `supplier-${index}`,
    supplierCode: item.supplierCode || 'UNKNOWN',
    supplierName: item.supplierName || item.supplierCode || 'UNKNOWN',
    materialClass: item.materialClass || 'GENERAL',
    qualificationStatus,
    score: Math.max(0, Math.min(100, Number.isFinite(score) ? score : 0)),
    scoreText: item.scoreText || (Number.isFinite(score) ? score.toFixed(1) : '0.0'),
    passRateText: item.passRateText || `${Number.isFinite(passRate) ? passRate.toFixed(1) : '0.0'}%`,
    batchCount: item.batchCount ?? 0,
    inspectionCount: item.inspectionCount ?? 0,
    passCount: item.passCount ?? 0,
    holdCount: item.holdCount ?? 0,
    ngCount: item.ngCount ?? 0,
    riskBatchCount: item.riskBatchCount ?? 0,
    riskLevel,
    openActionCount: item.openActionCount ?? 0,
    overdueActionCount: item.overdueActionCount ?? 0,
    latestActionNo: item.latestActionNo || '',
    nextAuditDue: item.nextAuditDue ? formatDate(item.nextAuditDue) : '-',
    owner: item.owner || '-',
    type: item.type || qualificationType(qualificationStatus, riskLevel)
  }
}

function mapSupplierAction(item, index = 0) {
  const status = item.status || 'OPEN'
  const severity = item.severity || 'MEDIUM'
  return {
    key: item.actionNo || `${item.supplierCode}-${index}`,
    actionNo: item.actionNo || '-',
    supplierCode: item.supplierCode || 'UNKNOWN',
    sourceType: item.sourceType || 'MANUAL',
    sourceNo: item.sourceNo || '-',
    issueSummary: item.issueSummary || '-',
    owner: item.owner || '-',
    severity,
    status,
    dueTime: item.dueTime ? formatDate(item.dueTime) : '-',
    createdTime: item.createdTime ? formatTime(item.createdTime) : '-',
    canClose: status !== 'CLOSED',
    type: item.type || supplierActionType(status, severity, item.overdue)
  }
}

function mapSupplierReview(item, index = 0) {
  const status = item.reviewStatus || 'OPEN'
  const suggestedRisk = item.suggestedRisk || 'MEDIUM'
  return {
    key: item.taskNo || `${item.supplierCode}-${index}`,
    taskNo: item.taskNo || '-',
    supplierCode: item.supplierCode || 'UNKNOWN',
    reviewType: item.reviewType || 'PERIODIC',
    sourceNo: item.sourceNo || '-',
    triggerReason: item.triggerReason || '-',
    qualificationBefore: item.qualificationBefore || '-',
    riskBefore: item.riskBefore || '-',
    suggestedQualification: item.suggestedQualification || 'PENDING',
    suggestedRisk,
    reviewStatus: status,
    dueTime: item.dueTime ? formatDate(item.dueTime) : '-',
    reviewer: item.reviewer || '-',
    decision: item.decision || '',
    decisionComment: item.decisionComment || '',
    canDecide: item.canDecide ?? status === 'OPEN',
    type: item.type || supplierReviewType(status, suggestedRisk, item.overdue)
  }
}

function mapSupplierTrend(item, index = 0) {
  const latestScore = Number(item.latestScore ?? 0)
  const latestPassRate = Number(item.latestPassRate ?? 0)
  const trend = Array.isArray(item.trend) ? item.trend.map(mapSupplierTrendPoint) : []
  const latestPoint = trend.slice().reverse().find(point => point.activityCount > 0) || trend[trend.length - 1]
  const latestRiskLevel = item.latestRiskLevel || latestPoint?.riskLevel || 'LOW'
  return {
    key: item.supplierCode || `supplier-trend-${index}`,
    supplierCode: item.supplierCode || 'UNKNOWN',
    supplierName: item.supplierName || item.supplierCode || 'UNKNOWN',
    materialClass: item.materialClass || 'GENERAL',
    latestScore: Number.isFinite(latestScore) ? latestScore : Number(latestPoint?.score || 0),
    latestScoreText: item.latestScoreText || latestPoint?.scoreText || '0.0',
    latestPassRateText: item.latestPassRateText || (Number.isFinite(latestPassRate) ? `${latestPassRate.toFixed(1)}%` : latestPoint?.passRateText || '0.0%'),
    latestRiskLevel,
    actionWindowCount: item.actionWindowCount ?? 0,
    overdueWindowCount: item.overdueWindowCount ?? 0,
    summary: item.summary || '暂无供应商趋势结论。',
    type: item.type || riskType(latestRiskLevel),
    trend
  }
}

function mapSupplierTrendPoint(item, index = 0) {
  const score = Number(item.score ?? 0)
  const period = item.period || `P${index + 1}`
  const safeScore = Number.isFinite(score) ? Math.max(0, Math.min(100, score)) : 0
  const riskLevel = item.riskLevel || 'LOW'
  return {
    period,
    periodShort: period.includes('-') ? period.slice(5) : period,
    score: safeScore,
    scoreText: item.scoreText || safeScore.toFixed(1),
    passRateText: item.passRateText || '0.0%',
    riskLevel,
    batchCount: item.batchCount ?? 0,
    inspectionCount: item.inspectionCount ?? 0,
    actionCount: item.actionCount ?? 0,
    openActionCount: item.openActionCount ?? 0,
    overdueActionCount: item.overdueActionCount ?? 0,
    activityCount: item.activityCount ?? ((item.batchCount ?? 0) + (item.inspectionCount ?? 0) + (item.actionCount ?? 0)),
    type: item.type || riskType(riskLevel),
    barHeight: Math.max(8, safeScore)
  }
}

function mapMaterialLocation(item) {
  return {
    locationCode: item.locationCode || '-',
    zoneCode: item.zoneCode || '-',
    storageType: item.storageType || 'NORMAL',
    materialClass: item.materialClass || 'ANY',
    status: item.status || 'ACTIVE',
    capacityQty: item.capacityQty ?? 0,
    usedQty: item.usedQty ?? 0,
    availableQty: item.availableQty ?? 0,
    usageText: `${formatQty(item.usedQty, item.unit)} / ${formatQty(item.capacityQty, item.unit)} (${item.utilizationText || '-'})`,
    temperatureWindow: item.temperatureWindow || '-',
    humidityWindow: item.humidityWindow || '-',
    strategyPriority: item.strategyPriority ?? 100,
    type: item.type || statusType(item.status)
  }
}

function mapLocationTask(item, index = 0) {
  const status = item.status || 'CREATED'
  const timeSource = item.completedTime || item.executedTime || item.assignedTime || item.createdTime
  return {
    key: item.taskNo || `${item.batchNo}-${item.taskType}-${index}`,
    taskNo: item.taskNo || '-',
    taskType: item.taskType || 'MOVE',
    taskLabel: locationTaskLabel(item.taskType),
    batchNo: item.batchNo || '-',
    materialName: item.materialName || item.materialCode || '-',
    sourceLocation: item.sourceLocation || '-',
    targetLocation: item.targetLocation || '-',
    qty: `${formatQty(item.actualQty ?? item.plannedQty, item.unit)} / ${formatQty(item.plannedQty, item.unit)}`,
    actualQty: item.actualQty ?? 0,
    plannedQty: item.plannedQty ?? 0,
    unit: item.unit || '',
    status,
    reason: item.reason || '-',
    operator: item.operator || 'system',
    assignedTo: item.assignedTo || '',
    time: formatTime(timeSource),
    type: item.type || statusType(status),
    canAssign: status === 'CREATED',
    canComplete: ['CREATED', 'ASSIGNED', 'EXECUTING'].includes(status),
    canCancel: ['CREATED', 'ASSIGNED'].includes(status)
  }
}

function locationTaskLabel(taskType) {
  return locationTaskTypes.find(item => item.value === taskType)?.label || taskType || '任务'
}

function setWmsAction(action) {
  wmsForm.action = action
  if (action !== 'RECEIVE' && !wmsForm.batchNo) {
    wmsForm.batchNo = rawBatches.value[0]?.batchNo || ''
  }
}

function setLocationTaskType(taskType) {
  locationTaskForm.taskType = taskType
  if (!locationTaskForm.batchNo) {
    locationTaskForm.batchNo = rawBatches.value[0]?.batchNo || ''
  }
  if (taskType !== 'COUNT' && !locationTaskForm.targetLocation) {
    locationTaskForm.targetLocation = materialLocationRows.value.find(item => item.status === 'ACTIVE')?.locationCode || ''
  }
}

function setIqcResult(result) {
  iqcForm.result = result
  if (result === 'PASS' && !iqcForm.conclusion) {
    iqcForm.conclusion = '来料复核通过，允许投料。'
  }
  if (result !== 'PASS' && !iqcForm.defectCode) {
    iqcForm.defectCode = 'D-IQC'
  }
}

function selectBatch(batchNo) {
  wmsForm.batchNo = batchNo
  locationTaskForm.batchNo = batchNo
  iqcForm.batchNo = batchNo
  if (wmsForm.action === 'RECEIVE') wmsForm.action = 'FREEZE'
}

function batchPhysicalQty(batch) {
  return Number(batch?.availableQty || 0) + Number(batch?.reservedQty || 0) + Number(batch?.frozenQty || 0)
}

function formatQty(value, unit = '') {
  if (value === null || value === undefined || value === '') return `0${unit || ''}`
  const number = Number(value)
  if (!Number.isFinite(number)) return `${value}${unit || ''}`
  return `${number.toFixed(number % 1 === 0 ? 0 : 2)}${unit || ''}`
}

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toTimeString().slice(0, 5)
}

function formatDate(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return `${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${date.toTimeString().slice(0, 5)}`
}

function statusType(status) {
  if (['OK', 'PASS', 'AVAILABLE', 'BOUND', 'TRACEABLE', 'DONE'].includes(status)) return 'green'
  if (['WARNING', 'RESERVED', 'CLEANING', 'FROZEN', 'CREATED', 'ASSIGNED'].includes(status)) return 'amber'
  if (['BLOCKED', 'HOLD', 'LOCKED', 'NG', 'CANCELLED', 'REJECTED'].includes(status)) return 'red'
  if (['IDLE', 'EXECUTING'].includes(status)) return 'blue'
  return 'gray'
}

function riskType(level) {
  if (level === 'HIGH') return 'red'
  if (level === 'MEDIUM') return 'amber'
  if (level === 'LOW') return 'green'
  return 'gray'
}

function qualificationType(status, riskLevel) {
  if (status === 'BLOCKED' || riskLevel === 'HIGH') return 'red'
  if (status === 'CONDITIONAL' || riskLevel === 'MEDIUM') return 'amber'
  if (status === 'QUALIFIED') return 'green'
  return 'blue'
}

function supplierActionType(status, severity, overdue = false) {
  if (status === 'CLOSED') return 'green'
  if (overdue || ['CRITICAL', 'HIGH'].includes(severity)) return 'red'
  if (severity === 'MEDIUM') return 'amber'
  return 'blue'
}

function supplierReviewType(status, riskLevel, overdue = false) {
  if (status === 'APPROVED') return 'green'
  if (status === 'REJECTED' || overdue || riskLevel === 'HIGH') return 'red'
  if (riskLevel === 'MEDIUM') return 'amber'
  return 'blue'
}

function txnType(type) {
  if (type === 'RECEIVE') return 'green'
  if (type === 'FREEZE' || type === 'COUNT') return 'amber'
  if (type === 'UNFREEZE' || type === 'RETURN') return 'blue'
  return 'gray'
}

function numberValue(value, field, allowZero = false) {
  const number = Number(value)
  if (!Number.isFinite(number) || (allowZero ? number < 0 : number <= 0)) {
    throw new Error(`${field}必须是${allowZero ? '非负' : '大于0的'}数字`)
  }
  return number
}

function commonPayload() {
  return {
    reason: wmsForm.reason || `${currentActionLabel.value}库存事务`,
    operator: wmsForm.operator || localStorage.getItem('username') || 'admin'
  }
}

function wmsAdapterPayload() {
  const payload = {
    ...commonPayload(),
    transactionType: wmsForm.action,
    action: wmsForm.action,
    batchNo: wmsForm.batchNo || undefined,
    sourceSystem: 'wms-adapter',
    adapterCode: 'simulated-wms-adapter'
  }
  if (wmsForm.action === 'RECEIVE') {
    if (!wmsForm.materialCode) throw new Error('物料编码不能为空')
    return {
      ...payload,
      materialCode: wmsForm.materialCode,
      materialName: wmsForm.materialName || wmsForm.materialCode,
      qty: numberValue(wmsForm.qty, '数量'),
      unit: wmsForm.unit || 'EA',
      location: wmsForm.location || undefined,
      qualityStatus: 'PASS'
    }
  }
  if (!wmsForm.batchNo) throw new Error('目标批次不能为空')
  if (wmsForm.action === 'COUNT') {
    return {
      ...payload,
      countedAvailableQty: numberValue(wmsForm.countedAvailableQty, '盘点可用量', true)
    }
  }
  return {
    ...payload,
    qty: numberValue(wmsForm.qty, '数量')
  }
}

async function checkWmsReadinessByAdapter() {
  if (!canWmsAction.value) {
    ElMessage.warning('当前角色无权调用 WMS Adapter')
    return
  }
  try {
    wmsAdapterSubmitting.value = 'readiness'
    const result = await checkWmsMaterialReadiness({
      lineCode: 'LINE_01',
      operator: wmsForm.operator || localStorage.getItem('username') || 'admin'
    })
    wmsAdapterResult.value = result
    readiness.value = result?.readiness || readiness.value
    if (Array.isArray(result?.checks) && result.checks.length) {
      checks.value = result.checks
    }
    ElMessage.success(`WMS Adapter 齐套返回：${result?.readiness || '-'}`)
  } catch (error) {
    ElMessage.warning(error?.message || 'WMS Adapter 齐套查询失败')
  } finally {
    wmsAdapterSubmitting.value = ''
  }
}

async function submitWmsAdapterTransaction() {
  if (!canWmsAction.value) {
    ElMessage.warning('当前角色无权调用 WMS Adapter')
    return
  }
  try {
    wmsAdapterSubmitting.value = 'transaction'
    const result = await ingestWmsInventoryTransaction(wmsAdapterPayload())
    wmsAdapterResult.value = result
    ElMessage.success(`WMS Adapter 事务已接收：${result?.transactionType || wmsForm.action}`)
    await loadMaterialData()
  } catch (error) {
    ElMessage.warning(error?.message || 'WMS Adapter 库存事务失败')
  } finally {
    wmsAdapterSubmitting.value = ''
  }
}

async function submitWmsAction() {
  if (!canWmsAction.value) {
    ElMessage.warning('当前角色无权执行 WMS 库存事务')
    return
  }
  try {
    wmsSubmitting.value = true
    if (wmsForm.action === 'RECEIVE') {
      if (!wmsForm.materialCode) throw new Error('物料编码不能为空')
      await receiveMaterial({
        ...commonPayload(),
        batchNo: wmsForm.batchNo || undefined,
        materialCode: wmsForm.materialCode,
        materialName: wmsForm.materialName || wmsForm.materialCode,
        qty: numberValue(wmsForm.qty, '数量'),
        unit: wmsForm.unit || 'EA',
        location: wmsForm.location || undefined,
        qualityStatus: 'PASS'
      })
    } else {
      if (!wmsForm.batchNo) throw new Error('目标批次不能为空')
      if (wmsForm.action === 'FREEZE') {
        await freezeMaterial(wmsForm.batchNo, { ...commonPayload(), qty: numberValue(wmsForm.qty, '数量') })
      }
      if (wmsForm.action === 'UNFREEZE') {
        await unfreezeMaterial(wmsForm.batchNo, { ...commonPayload(), qty: numberValue(wmsForm.qty, '数量') })
      }
      if (wmsForm.action === 'RETURN') {
        await returnMaterial(wmsForm.batchNo, { ...commonPayload(), qty: numberValue(wmsForm.qty, '数量') })
      }
      if (wmsForm.action === 'COUNT') {
        await countMaterialInventory(wmsForm.batchNo, {
          ...commonPayload(),
          countedAvailableQty: numberValue(wmsForm.countedAvailableQty, '盘点可用量', true)
        })
      }
    }
    ElMessage.success(`WMS${currentActionLabel.value}已提交`)
    await loadMaterialData()
  } catch (error) {
    ElMessage.warning(error?.message || 'WMS库存事务提交失败')
  } finally {
    wmsSubmitting.value = false
  }
}

async function submitLocationTask() {
  if (!canWmsAction.value) {
    ElMessage.warning('当前角色无权执行库位任务')
    return
  }
  try {
    locationTaskSubmitting.value = true
    if (!locationTaskForm.batchNo) throw new Error('目标批次不能为空')
    const payload = {
      taskType: locationTaskForm.taskType,
      batchNo: locationTaskForm.batchNo,
      reason: locationTaskForm.reason || `${currentLocationTaskLabel.value}库位任务`,
      operator: locationTaskForm.operator || localStorage.getItem('username') || 'admin'
    }
    if (locationTaskForm.taskType === 'COUNT') {
      payload.actualQty = numberValue(locationTaskForm.qty, '实盘可用数量', true)
    } else {
      if (!locationTaskForm.targetLocation) throw new Error('目标库位不能为空')
      payload.targetLocation = locationTaskForm.targetLocation
      if (locationTaskForm.qty !== '') {
        payload.actualQty = numberValue(locationTaskForm.qty, '任务数量')
      }
    }
    await createMaterialLocationTask(payload)
    ElMessage.success(`${currentLocationTaskLabel.value}任务已创建`)
    locationTaskForm.qty = ''
    await loadMaterialData()
  } catch (error) {
    ElMessage.warning(error?.message || '库位任务提交失败')
  } finally {
    locationTaskSubmitting.value = false
  }
}

async function assignLocationTask(task) {
  try {
    locationTaskSubmitting.value = true
    await assignMaterialLocationTask(task.taskNo, {
      assignedTo: locationTaskForm.operator || localStorage.getItem('username') || 'admin',
      operator: locationTaskForm.operator || localStorage.getItem('username') || 'admin'
    })
    ElMessage.success('库位任务已领取')
    await loadMaterialData()
  } catch (error) {
    ElMessage.warning(error?.message || '库位任务领取失败')
  } finally {
    locationTaskSubmitting.value = false
  }
}

async function completeLocationTask(task) {
  try {
    locationTaskSubmitting.value = true
    const payload = {
      operator: locationTaskForm.operator || localStorage.getItem('username') || 'admin'
    }
    if (task.taskType === 'COUNT' && task.actualQty !== undefined) {
      payload.actualQty = task.actualQty
    }
    await completeMaterialLocationTask(task.taskNo, payload)
    ElMessage.success('库位任务已完成')
    await loadMaterialData()
  } catch (error) {
    ElMessage.warning(error?.message || '库位任务完成失败')
  } finally {
    locationTaskSubmitting.value = false
  }
}

async function cancelLocationTask(task) {
  try {
    locationTaskSubmitting.value = true
    await cancelMaterialLocationTask(task.taskNo, {
      operator: locationTaskForm.operator || localStorage.getItem('username') || 'admin',
      cancelReason: locationTaskForm.reason || '人工取消库位任务'
    })
    ElMessage.success('库位任务已取消')
    await loadMaterialData()
  } catch (error) {
    ElMessage.warning(error?.message || '库位任务取消失败')
  } finally {
    locationTaskSubmitting.value = false
  }
}

async function submitIqcAction() {
  if (!canIqcAction.value) {
    ElMessage.warning('当前角色无权执行来料质检判定')
    return
  }
  try {
    iqcSubmitting.value = true
    if (!iqcForm.batchNo) throw new Error('目标批次不能为空')
    const attachments = []
    if (iqcForm.fileName || iqcForm.fileUrl || iqcForm.fileHash) {
      attachments.push({
        fileName: iqcForm.fileName,
        fileUrl: iqcForm.fileUrl,
        fileHash: iqcForm.fileHash,
        fileType: 'COA',
        uploadedBy: iqcForm.inspector || localStorage.getItem('username') || 'qe1003'
      })
    }
    await createMaterialIncomingInspection(iqcForm.batchNo, {
      result: iqcForm.result,
      inspectedQty: numberValue(iqcForm.inspectedQty, '检验数量', true),
      sampleQty: numberValue(iqcForm.sampleQty, '抽检数量', true),
      coaNo: iqcForm.coaNo,
      defectCode: iqcForm.defectCode,
      conclusion: iqcForm.conclusion,
      inspector: iqcForm.inspector || localStorage.getItem('username') || 'qe1003',
      sourceSystem: 'qms-adapter',
      attachments
    })
    ElMessage.success('来料质检判定已提交')
    await loadMaterialData()
  } catch (error) {
    ElMessage.warning(error?.message || '来料质检提交失败')
  } finally {
    iqcSubmitting.value = false
  }
}

async function evaluateSupplier(supplier) {
  if (!canSupplierAction.value) {
    ElMessage.warning('当前角色无权执行供应商准入评估')
    return
  }
  try {
    supplierSubmitting.value = true
    await evaluateMaterialSupplierQualification(supplier.supplierCode, {
      operator: localStorage.getItem('username') || 'qe1003',
      remark: '前端触发供应商准入复核'
    })
    ElMessage.success('供应商准入评估已完成')
    await loadMaterialData()
  } catch (error) {
    ElMessage.warning(error?.message || '供应商准入评估失败')
  } finally {
    supplierSubmitting.value = false
  }
}

async function createSupplierReview(supplier) {
  if (!canSupplierAction.value) {
    ElMessage.warning('当前角色无权创建供应商准入复审')
    return
  }
  try {
    supplierSubmitting.value = true
    await createSupplierQualificationReview(supplier.supplierCode, {
      reviewType: 'PERIODIC',
      triggerReason: '前端触发供应商准入周期复审',
      operator: localStorage.getItem('username') || 'qe1003'
    })
    ElMessage.success('供应商准入复审任务已创建')
    await loadMaterialData()
  } catch (error) {
    ElMessage.warning(error?.message || '供应商准入复审创建失败')
  } finally {
    supplierSubmitting.value = false
  }
}

async function decideSupplierReview(review, decision) {
  if (!canSupplierAction.value) {
    ElMessage.warning('当前角色无权处理供应商准入复审')
    return
  }
  try {
    supplierSubmitting.value = true
    await decideSupplierQualificationReview(review.taskNo, {
      decision,
      qualificationStatus: review.suggestedQualification,
      riskLevel: review.suggestedRisk,
      decisionComment: decision === 'APPROVE' ? '复审通过，按建议准入状态更新' : '复审驳回，需补充供应商证据',
      operator: localStorage.getItem('username') || 'qe1003'
    })
    ElMessage.success(`供应商准入复审已${decision === 'APPROVE' ? '通过' : '驳回'}`)
    await loadMaterialData()
  } catch (error) {
    ElMessage.warning(error?.message || '供应商准入复审处理失败')
  } finally {
    supplierSubmitting.value = false
  }
}

async function submitSupplierAction() {
  if (!canSupplierAction.value) {
    ElMessage.warning('当前角色无权创建供应商8D')
    return
  }
  try {
    supplierSubmitting.value = true
    if (!supplierActionForm.supplierCode) throw new Error('供应商不能为空')
    if (!supplierActionForm.issueSummary) throw new Error('问题摘要不能为空')
    await createSupplierCorrectiveAction({
      supplierCode: supplierActionForm.supplierCode,
      sourceType: supplierActionForm.sourceType,
      sourceNo: supplierActionForm.sourceNo,
      issueSummary: supplierActionForm.issueSummary,
      severity: supplierActionForm.severity,
      owner: supplierActionForm.owner || localStorage.getItem('username') || 'qe1003',
      dueDays: numberValue(supplierActionForm.dueDays, 'SLA天数'),
      operator: localStorage.getItem('username') || 'qe1003'
    })
    ElMessage.success('供应商8D整改单已创建')
    await loadMaterialData()
  } catch (error) {
    ElMessage.warning(error?.message || '供应商8D创建失败')
  } finally {
    supplierSubmitting.value = false
  }
}

async function closeSupplierAction(action) {
  if (!canSupplierAction.value) {
    ElMessage.warning('当前角色无权关闭供应商8D')
    return
  }
  try {
    supplierSubmitting.value = true
    await closeSupplierCorrectiveAction(action.actionNo, {
      rootCause: '已完成供应商根因复盘',
      containmentAction: '异常批次隔离并加严复检',
      correctiveAction: '供应商更新制程控制与COA复核规则',
      preventiveAction: '连续三批趋势复核',
      verificationResult: 'MES试点复验通过',
      operator: localStorage.getItem('username') || 'qe1003'
    })
    ElMessage.success('供应商8D已关闭')
    await loadMaterialData()
  } catch (error) {
    ElMessage.warning(error?.message || '供应商8D关闭失败')
  } finally {
    supplierSubmitting.value = false
  }
}

async function loadMaterialData() {
  try {
    loading.value = true
    const [materialData, carrierData, consumptionData, txnData, iqcData, supplierData, supplierActionsData, supplierReviewData, supplierTrendData, locationData, locationTaskData] = await Promise.all([
      getMaterialBatches(),
      getCarriers(),
      getMaterialConsumptions(),
      getMaterialInventoryTransactions(),
      getMaterialIncomingInspections(),
      getMaterialSuppliers().catch(() => getMaterialSupplierPerformance()),
      getSupplierCorrectiveActions(),
      getSupplierQualificationReviews(),
      getMaterialSupplierTrends({ months: 6 }),
      getMaterialLocations(),
      getMaterialLocationTasks()
    ])
    if (Array.isArray(materialData?.batches) && materialData.batches.length) {
      rawBatches.value = materialData.batches
      materialLots.value = materialData.batches.map(mapBatch)
      readiness.value = materialData.readiness || readiness.value
      if (!wmsForm.batchNo && materialData.batches[0]?.batchNo) {
        wmsForm.batchNo = materialData.batches[0].batchNo
      }
      if (!iqcForm.batchNo && materialData.batches[0]?.batchNo) {
        iqcForm.batchNo = materialData.batches[0].batchNo
      }
      if (!locationTaskForm.batchNo && materialData.batches[0]?.batchNo) {
        locationTaskForm.batchNo = materialData.batches[0].batchNo
      }
    }
    if (Array.isArray(materialData?.checks) && materialData.checks.length) {
      checks.value = materialData.checks
    }
    if (Array.isArray(carrierData) && carrierData.length) {
      carriers.value = carrierData.map(mapCarrier)
    }
    if (Array.isArray(consumptionData) && consumptionData.length) {
      consumeRecords.value = consumptionData.map(mapConsumption)
    }
    if (Array.isArray(txnData) && txnData.length) {
      inventoryTxns.value = txnData.map(mapTransaction)
    }
    if (Array.isArray(iqcData) && iqcData.length) {
      iqcInspections.value = iqcData.map(mapInspection)
    }
    if (Array.isArray(supplierData) && supplierData.length) {
      supplierRows.value = supplierData.map(mapSupplier)
      if (!supplierActionForm.supplierCode && supplierRows.value[0]?.supplierCode) {
        supplierActionForm.supplierCode = supplierRows.value[0].supplierCode
      }
    }
    if (Array.isArray(supplierActionsData) && supplierActionsData.length) {
      supplierActionRows.value = supplierActionsData.map(mapSupplierAction)
    }
    if (Array.isArray(supplierReviewData) && supplierReviewData.length) {
      supplierReviewRows.value = supplierReviewData.map(mapSupplierReview)
    }
    if (Array.isArray(supplierTrendData) && supplierTrendData.length) {
      supplierTrendRows.value = supplierTrendData.map(mapSupplierTrend)
    }
    if (Array.isArray(locationData) && locationData.length) {
      materialLocationRows.value = locationData.map(mapMaterialLocation)
      if (!locationTaskForm.targetLocation) {
        locationTaskForm.targetLocation = materialLocationRows.value.find(item => item.status === 'ACTIVE')?.locationCode || ''
      }
    }
    if (Array.isArray(locationTaskData) && locationTaskData.length) {
      locationTaskRows.value = locationTaskData.map(mapLocationTask)
    }
  } catch (error) {
    warnDevFallback('物料接口不可用', error)
    if (__DEV_MOCK_FALLBACK__) {
      rawBatches.value = fallbackMaterialLots
      materialLots.value = fallbackMaterialLots.map(mapBatch)
      carriers.value = fallbackCarriers
      checks.value = fallbackChecks
      consumeRecords.value = fallbackConsumeRecords
      inventoryTxns.value = fallbackTxns.map(mapTransaction)
      iqcInspections.value = fallbackIqcInspections.map(mapInspection)
      supplierRows.value = fallbackSupplierPerformance.map(mapSupplier)
      supplierActionRows.value = fallbackSupplierActions.map(mapSupplierAction)
      supplierReviewRows.value = fallbackSupplierReviews.map(mapSupplierReview)
      supplierTrendRows.value = fallbackSupplierTrends.map(mapSupplierTrend)
      materialLocationRows.value = fallbackMaterialLocations.map(mapMaterialLocation)
      locationTaskRows.value = fallbackLocationTasks.map(mapLocationTask)
      readiness.value = 'PASS_WITH_WARNING'
    }
  } finally {
    loading.value = false
  }
}

onMounted(loadMaterialData)
</script>

<style scoped>
.mini-card {
  cursor: pointer;
}

.mini-stock {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  margin-top: 8px;
  color: var(--mes-weak);
  font-size: 12px;
}

.adapter-strip {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  padding: 10px;
  border: 1px solid var(--mes-line-soft);
  border-radius: 7px;
  background: var(--mes-paper-muted);
}

.adapter-status {
  display: grid;
  gap: 6px;
  min-width: 0;
  color: var(--mes-sub);
  font-size: 12px;
  line-height: 1.45;
}

.adapter-status .status-tag {
  width: fit-content;
}

.adapter-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
  flex-shrink: 0;
}

.wms-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
  padding: 3px;
  border: 1px solid var(--mes-line-soft);
  border-radius: 7px;
  background: var(--mes-paper-muted);
  width: fit-content;
}

.wms-action {
  height: 27px;
  border: 1px solid transparent;
  border-radius: 5px;
  background: transparent;
  color: var(--mes-sub);
  padding: 0 10px;
  font-size: 12px;
  cursor: pointer;
}

.wms-action.active {
  border-color: var(--mes-line);
  background: var(--mes-paper);
  color: var(--mes-ink);
  font-weight: 600;
  box-shadow: 0 1px 0 rgba(24, 24, 22, 0.02);
}

.wms-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.wms-form .wide {
  grid-column: span 2;
}

.iqc-form {
  margin-top: 2px;
}

.wms-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--mes-line-soft);
}

.wms-stock {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  color: var(--mes-sub);
  font-size: 12px;
  min-width: 0;
}

.iqc-history {
  margin-top: 14px;
  max-height: 220px;
  overflow: auto;
}

.location-task-shell {
  display: grid;
  grid-template-columns: minmax(330px, 0.82fr) minmax(500px, 1.18fr);
  gap: 14px;
  align-items: start;
}

.location-task-panel {
  min-width: 0;
  border: 1px solid var(--mes-line-soft);
  border-radius: 7px;
  padding: 12px;
  background: var(--mes-paper);
}

.location-task-table {
  min-width: 0;
  max-height: 334px;
  overflow: auto;
  border: 1px solid var(--mes-line-soft);
  border-radius: 7px;
}

.location-task-table .mes-table th,
.location-task-table .mes-table td {
  padding: 8px 9px;
}

.task-main {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.task-main strong {
  font-size: 13px;
  font-weight: 700;
}

.task-main span {
  color: var(--mes-weak);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.task-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  align-items: center;
}

.supplier-panel {
  overflow-x: auto;
}

.supplier-panel .mes-table {
  min-width: 0;
}

.supplier-panel .mes-table th,
.supplier-panel .mes-table td {
  padding: 8px 9px;
}

.supplier-main {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.supplier-main strong {
  font-weight: 680;
}

.supplier-main span {
  color: var(--mes-weak);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.supplier-action-form {
  display: grid;
  grid-template-columns: minmax(120px, 1fr) 96px 60px 72px;
  gap: 8px;
  margin-bottom: 12px;
}

.supplier-action-form .action-summary {
  grid-column: 1 / 4;
  min-width: 0;
}

.supplier-action-form .mes-btn {
  grid-column: 4;
}

.supplier-score {
  display: grid;
  gap: 5px;
  min-width: 88px;
}

.supplier-score strong {
  color: var(--mes-text);
  font-size: 13px;
}

.supplier-score span {
  display: block;
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--mes-soft-2);
}

.supplier-score i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #8f928b;
}

.supplier-trend-list {
  display: grid;
  gap: 0;
}

.supplier-trend-row {
  display: grid;
  grid-template-columns: minmax(190px, 0.8fr) minmax(360px, 1.2fr) minmax(220px, 0.9fr);
  gap: 12px;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid var(--mes-line-soft);
}

.supplier-trend-row:first-child {
  padding-top: 0;
}

.supplier-trend-row:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.supplier-trend-main {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
}

.supplier-trend-bars {
  display: grid;
  grid-template-columns: repeat(6, minmax(38px, 1fr));
  gap: 8px;
  align-items: end;
  min-height: 94px;
}

.supplier-trend-point {
  display: grid;
  gap: 4px;
  justify-items: center;
  min-width: 0;
}

.supplier-trend-bar {
  width: 100%;
  height: 56px;
  border: 1px solid var(--mes-line-soft);
  border-radius: 5px;
  background: var(--mes-paper-muted);
  display: flex;
  align-items: end;
  justify-content: center;
  padding: 3px;
}

.supplier-trend-bar i {
  width: 100%;
  max-width: 18px;
  border-radius: 4px;
  background: #8f928b;
}

.supplier-trend-bar i.green {
  background: var(--mes-green);
}

.supplier-trend-bar i.amber {
  background: var(--mes-amber);
}

.supplier-trend-bar i.red {
  background: var(--mes-red);
}

.supplier-trend-point b {
  font-size: 12px;
  font-weight: 650;
  color: var(--mes-text);
}

.supplier-trend-point small {
  color: var(--mes-weak);
  font-size: 11px;
}

.supplier-trend-meta {
  display: grid;
  gap: 4px;
  color: var(--mes-sub);
  font-size: 12px;
}

.supplier-trend-row p {
  grid-column: 1 / -1;
  margin: -2px 0 0;
  color: var(--mes-sub);
  font-size: 12px;
  line-height: 1.45;
}

.empty-cell {
  color: var(--mes-weak);
  text-align: center;
}

.mes-btn:disabled {
  cursor: not-allowed;
  opacity: 0.52;
}

@media (max-width: 960px) {
  .wms-form {
    grid-template-columns: 1fr;
  }

  .location-task-shell {
    grid-template-columns: 1fr;
  }

  .wms-form .wide {
    grid-column: span 1;
  }

  .wms-footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .adapter-strip {
    align-items: flex-start;
    flex-direction: column;
  }

  .adapter-actions {
    justify-content: flex-start;
  }

  .supplier-action-form {
    grid-template-columns: 1fr;
  }

  .supplier-trend-row {
    grid-template-columns: 1fr;
  }

  .supplier-trend-bars {
    grid-template-columns: repeat(6, minmax(0, 1fr));
  }
}
</style>
