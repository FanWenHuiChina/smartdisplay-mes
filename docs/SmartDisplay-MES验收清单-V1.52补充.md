# 2026-06-14 补充验收：ERP批量导入差异快照与必填校验

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| ERP导入差异快照 | `ERP_ORDER_IMPORT` 审计快照包含 `request + summary` 结构，保留同批次重复跳过明细（`EXISTING/DUPLICATE_IN_BATCH`）和工单编号列表 | 已落地，与 Lot 批量处置审计快照结构对齐 |
| 工单创建必填校验 | `productCode` 和 `plannedQty>0` 必须显式传入，拒绝默认值静默创建 | 已落地，后端测试覆盖漏传场景 |
| AI设备分析必填校验 | `equipmentCode` 必填，拒绝默认值静默归档到错误设备 | 已落地，后端测试覆盖漏传场景 |
| 库存操作审计快照 | 冻结/解冻/退料操作审计从无快照升级为 `before/after/changedFields/request` 四段式结构化快照 | 已落地，零额外查询开销 |
| AI审计快照结构化 | `AI_YIELD_REPORT/AI_EQUIPMENT_ANALYZE/AI_KB_ASK` 审计从 `requestSnapshot=null` 升级为包含 `reportNo/reportType/promptTemplateVersion/model/request` | 已落地，补齐AI合规证据链 |
| JWT密钥外置配置 | JWT签名密钥和过期时间从硬编码改为 `mes.security.jwt.secret` 和 `mes.security.jwt.expiration-ms`，支持环境变量 `${MES_JWT_SECRET}` 覆盖 | 已落地，向后兼容既有Token |

# 2026-06-14 补充验收：ISA-101工业配色换肤

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| ISA-101标准配色 | 采用ISA-101工业标准高饱和语义色：绿色 `#00B42A`、红色 `#F53F3F`、琥珀 `#FF7D00`、蓝色 `#3370FF`，背景 `#F0F2F5`，主文字 `#1F2329` | 已落地，单文件 `style.css` 改造（+54/-43行） |
| 等宽KPI数值 | KPI数值应用 JetBrains Mono/Cascadia Code 等宽字体 + `tabular-nums`，避免刷新跳动、列对齐更整齐 | 已落地 |
| 增强对比度 | 状态tag浅底色和边框色升级为高饱和色系，卡片阴影从 `rgba 0.03` 增强到 `rgba(31,35,41,0.08)` | 已落地 |
| 视觉回归验证 | 21步浏览器E2E全绿，Console/Network错误数为0，无横向溢出/文字裁切/布局回归 | 已通过 `npm run e2e:browser`，报告 `SmartDisplay-MES-browser-e2e-20260617-134419.md` |
| 零侵入改造 | 仅修改 `style.css` 一个文件，13个页面自动受益，未动页面结构、未引入新组件库 | 已验证 |

# 2026-06-11 补充验收：WMS复核差异升级异常MRB闭环

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| 复核驳回待处置队列 | `GET /api/v1/material/location-tasks?pendingDispositionOnly=true` 只返回 `DONE + REJECTED + PENDING` 待处置任务，支持 `reviewResult/dispositionStatus` 筛选 | 已落地，前端物料页独立队列展示待处置数量、批次数、高优先级数量 |
| 升级处置入口 | 待处置队列和最近任务表提供"升级"按钮，调用 `POST /api/v1/material/location-tasks/{taskNo}/disposition` 提交 `ESCALATE` | 已落地，确认框 + 前端契约覆盖 |
| 升级生成异常事件 | `ESCALATE` 创建 `exception_event`（`eventType=MATERIAL/eventLevel=P2/sourceModule=WMS_LOCATION_TASK/ownerRole=QE`），响应返回 `exception.eventNo` | 已落地，V1.51 已写入 `sourceRefType/sourceRefNo/sourcePayload` |
| 异常来源筛选 | `/api/v1/quality/exceptions` 支持 `sourceModule=WMS_LOCATION_TASK` 筛选，质量页展示来源标签和来源任务/批次证据 | 已落地，前端契约覆盖 |
| WMS异常动作边界 | WMS来源且无Lot上下文异常只显示"复判/关闭"，不显示"放行/返工/报废"Lot处置按钮 | 已落地，`lotActionable` 判断 |
| MRB关闭回写任务 | V1.52 新增 `linkedExceptionEventNo/exceptionCloseAction/exceptionCloseConclusion/exceptionClosedBy/exceptionClosedTime`，质量异常关闭后回写原任务并置 `dispositionStatus=CLOSED` | 已落地，浏览器E2E第21步覆盖完整闭环 |
| 前端契约覆盖 | 427项检查，覆盖待处置筛选、升级处置、来源筛选、动作边界和MRB关闭回写展示 | 已通过 `npm run verify:frontend-contract` |
| 浏览器E2E闭环 | 第21步串联"入库 → COUNT任务 → complete → review REJECTED → disposition ESCALATE → 按sourceModule筛选 → closeException → 查任务回写字段" | 已通过，断言 `ESCALATED/linkedExceptionEventNo/exceptionCloseAction=RELEASE/dispositionStatus=CLOSED` |

# V1.43-V1.52 Flyway迁移验收

| 版本 | 说明 | 验收状态 |
| --- | --- | --- |
| V1.43 | 激活 `LOCAL_RAG_HYBRID` 作为 SOP_QA 配置，Prompt 版本升级为 `rag-sop-qa-v3` | 已落地，Docker运行库已迁移 |
| V1.44 | Recipe单一生效版本治理：创建部分唯一索引 `uk_recipe_single_active_context`，迁移时保留最新ACTIVE并停用旧版本 | 已落地，发布自动停用旧版，数据库兜底同上下文单一ACTIVE |
| V1.45 | Route单一生效版本治理：创建部分唯一索引 `uk_route_single_active_product`，同产品只能有一条ACTIVE Route | 已落地，工单释放/Track In防跳站读取确定路线 |
| V1.46 | BOM单一生效版本治理：创建部分唯一索引 `uk_bom_single_active_product`，发布时自动停用同产品旧ACTIVE BOM | 已落地，物料齐套校验读取确定BOM |
| V1.47 | 加固EAP影子协议驱动：SECS/GEM、OPC UA、厂商HTTP升级为 `SHADOW` 模式，增强协议帧校验和健康检查 | 已落地，健康检查返回WARN（待真机握手） |
| V1.48 | WMS库位任务SLA：新增 `priority` 和 `due_time`，按未完成+逾期+优先级+到期时间排序 | 已落地，前端展示正常/临期/逾期/已关闭 |
| V1.49 | WMS库位任务复核结果：新增 `review_result` 和 `review_conclusion`，历史已复核任务回填为 `APPROVED` | 已落地，前端拆分通过/驳回按钮 |
| V1.50 | WMS库位任务复核驳回处置：新增 `disposition_status/result/conclusion/by/time`，支持让步接收/显式调库/升级处置 | 已落地，历史驳回任务回填为PENDING |
| V1.51 | 异常来源证据结构化：新增 `source_ref_type/source_ref_no/source_payload`，WMS升级写入任务号和来源快照 | 已落地，质量页展示来源任务/批次证据 |
| V1.52 | WMS异常MRB关闭回写：新增 `linkedExceptionEventNo/exceptionCloseAction/exceptionCloseConclusion/exceptionClosedBy/exceptionClosedTime` | 已落地，历史任务回填关联事件号，浏览器E2E覆盖完整闭环 |

# 测试验收状态汇总

| 测试类型 | 当前状态 | 最新数据 |
| --- | --- | --- |
| 后端单元测试 | 已通过 | 274项（含必填校验、审计快照、异常回写用例） |
| 前端契约验收 | 已通过 | 427项（含WMS复核驳回处置、异常来源筛选、MRB关闭回写） |
| 前端生产构建 | 已通过 | 仅第三方pure annotation和chunk size警告 |
| 生产包样例扫描 | 已通过 | 14个JS产物clean，未发现mock/fallback样例标识 |
| 浏览器E2E | 已通过 | 21步全绿，Console/Network错误数为0 |
| Flyway静态验收 | 已通过 | 识别V1.1-V1.52共52个迁移文件 |
| Docker三服务启动 | 已通过 | Flyway已迁移到V1.52，jar/dist已覆盖容器 |
