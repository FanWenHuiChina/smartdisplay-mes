# SmartDisplay MES 生产级试点验收清单

## 验收范围

- 单基地、单产线、单 PostgreSQL 实例。
- 外部 ERP/EAP/QMS/WMS 使用模拟适配器。
- AI 只提供辅助分析和知识问答，不执行写生产动作。

## 功能验收

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| JWT 登录 | 6类角色可登录，未登录接口被拒绝 | 已落地 |
| 角色写权限 | 计划员释放工单、操作员过站、质量/工艺/设备角色按域写权限控制 | 已落地 |
| 前端菜单/按钮权限 | 菜单按角色裁剪，关键写按钮按按钮权限隐藏并兜底拦截 | 已落地 |
| 权限变更审计闭环 | 权限变更单保存前后快照，支持提交、审批通过/驳回、审计留痕、运行期权限快照应用、启动恢复和手动重载 | 已落地 |
| 审计请求上下文 | 关键审计记录自动保存请求方法、URI、客户端IP和User-Agent | 已落地 |
| 关键写接口失败审计 | 业务异常、参数校验异常和系统异常按关键写接口动作写入 `result=FAIL` 审计记录 | 已落地 |
| 核心执行审计差异快照 | 工单创建/释放、Track In/Out、Hold/Release、Rework/Scrap 审计快照包含 before、after、changedFields 和 request | 已落地 |
| 数据范围 SQL | 按 ALL/LINE/SELF_SHIFT/SELF 生成安全 SQL 条件，工单、Lot、质量、异常、物料消耗、载具等列表按域过滤 | 已落地，组织/产线/班次主数据已补 |
| 组织/产线/班次主数据 | 基地、产线、班次有正式表、种子数据和 `/api/v1/master/**` 查询接口 | 已落地 |
| ERP模拟工单导入 | 支持 `/api/v1/adapters/erp/orders` 下发工单、批量查重、1000 条模拟导入、成功/失败审计和角色权限控制 | 已落地 |
| 工单释放 | 工单释放后生成 Lot 并写审计 | 已落地 |
| Route 防跳站 | Track In 必须匹配 Route 下一站 | 已落地 |
| Recipe 校验 | Track In 校验产品+工序+设备生效 Recipe | 已落地 |
| 班次校验 | Track In 校验 Lot 产线当前时间处于 ACTIVE 班次窗口 | 已落地 |
| 物料齐套 | Track In 校验 BOM 关键物料并锁定批次 | 已落地 |
| BOM变更审批 | 支持变更草稿、审批通过/驳回、发布生效、旧版本失效和审计留痕 | 已落地 |
| BOM/ECO跨部门会签 | BOM变更提交后生成 ECO 包快照、风险等级、会签角色和 SLA；PE/QE/计划员/设备角色可会签，通过前禁止发布，驳回后阻断发布 | 已落地，V1.39 已验证 3 角色会签后发布 |
| 替代料策略 | Track In 按 substitute_group 和 substitute_priority 自动选择可用主料/替代料 | 已落地 |
| 替代料验证报告附件 | BOM 变更提交/审批可保存验证报告附件元数据，BOM 变更列表返回附件数量和附件明细 | 已落地，首版附件元数据 |
| WMS库存事务 | 支持入库、冻结、解冻、退料、盘点、事务履历、审计和批次行锁 | 已落地 |
| WMS库位策略 | 支持库位主数据、存储类型、物料类别、容量、环境窗口、优先级、锁定状态；入库校验库位状态/容量/类别/单位并更新占用 | 已落地，首版策略 |
| WMS库位任务 | 支持上架、整批移库、盘点任务，记录任务单、库存事务和审计；支持创建、领取、完成、取消分步状态流；物料页提供任务操作台和最近任务表 | 已落地，V1.38 已验证 `CREATED/ASSIGNED/DONE/CANCELLED` |
| 来料IQC/COA | 支持供应商批次来料判定、COA编号、附件元数据、批次质量状态联动和审计 | 已落地 |
| 供应商绩效评分/趋势 | 基于物料批次、来料IQC与8D记录聚合批次数、PASS/HOLD/NG、通过率、风险批次、评分、风险等级和最近6个月月度趋势，并提供物料页只读看板 | 已落地，首版聚合评分和趋势 |
| 供应商准入/8D整改 | 支持供应商主数据、准入状态评估、8D整改单创建/关闭、IQC NG/HOLD 自动开8D、供应商风险降级、审计与前端处置工作区 | 已落地，V1.40 已通过后端/前端/Flyway 验收 |
| 供应商准入复审任务 | 支持周期复审任务查询、创建、通过/驳回决策、建议准入状态回写、重复 OPEN 任务拦截、审计与前端复审工作区 | 已落地，V1.41 已通过后端/前端/Flyway 验收 |
| 设备/EAP事件 | 支持设备事件落库、EAP参数采样、参数越限自动事件、设备状态联动、PM任务完成和审计 | 已落地 |
| Recipe下发/回读 | 支持校验 ACTIVE Recipe、下发参数快照、EAP回读确认、Mismatch自动设备事件和审计 | 已落地 |
| 设备OEE/停机原因 | 支持近24小时 OEE 拆解、计划/非计划停机、停机原因TopN、事件关闭回填时长和审计 | 已落地 |
| 设备状态历史/节拍采样 | 支持 EAP 状态上报、设备状态变化历史、标准/实际节拍采样、良品/产出数量和 OEE 性能率样本口径 | 已落地 |
| 标准节拍主数据 | 支持产品+工序+设备+Recipe+版本的标准节拍、上下限窗口、ACTIVE发布、旧版本失效和审计；节拍样本可自动匹配主数据 | 已落地 |
| EAP统一适配器 | `/api/v1/adapters/eap/messages` 支持状态、节拍、参数、Recipe下发标准化消息，预留真实协议驱动替换点 | 已落地，占位适配 |
| EAP网关连接 | 支持网关注册/更新、心跳、连接状态、消息入站履历、处理成功/失败状态和失败降级留痕 | 已落地，占位网关 |
| EAP协议驱动配置 | 支持模拟HTTP、厂商HTTP、SECS/GEM、OPC UA驱动能力列表、协议帧归一化、驱动配置快照和消息归一化快照 | 已落地，占位驱动 |
| EAP网关健康检查 | 支持手动健康检查、PASS/WARN/FAIL履历、延迟与错误说明、状态联动和审计留痕 | 已落地，占位检查 |
| Track Out | 记录参数快照、人员、设备、结果并推动 Lot 流转 | 已落地 |
| 质量异常 | NG/关键参数超限生成质检、缺陷、异常并 Hold Lot | 已落地 |
| Hold/Release | 记录原因、处置结论、责任角色、人员和时间 | 已落地，支持 MRB 结论联动 |
| Rework/Scrap | Rework 必须选择返工 Route 和允许返工的起始工序；Scrap 必须二次确认并记录原因、责任模块、审批人和审计快照 | 已落地，支持异常关闭联动 |
| 追溯 | Lot 查询返回工单、Route、设备、Recipe、质量、Hold、物料、审计 | 已落地 |
| 看板 | WIP、良率、异常、缺陷 TopN、设备异常 TopN | 已落地 |
| AI 良率日报 | 输入快照、Prompt版本、模型、模型配置快照、证据质量和输出JSON落 `ai_report_record` | 已落地 |
| AI模型配置 | `ai_model_config` 保存良率日报、设备分析、SOP问答配置，区分模拟/影子/外部模式，外部模型占位默认禁用 | 已落地 |
| AI报告留痕查询 | 支持按报告类型、业务编号、证据等级和依据不足标志查询 `ai_report_record`，详情可回看输入/输出/模型配置快照 | 已落地 |
| SOP 问答 | 返回答案、引用切片、证据等级、最高证据分，依据不足时明确提示 | 已落地 |
| 知识库索引任务 | 支持关键词索引重建、pgvector-ready 待联调标记、切片索引状态、任务履历和审计留痕 | 已落地，真实向量检索待联调 |
| MRB复判 | 异常事件可记录复判动作、意见、处置动作、关闭结论、会议号、参与人、审批状态和附件元数据 | 已落地 |
| MRB会签 | P1/返工/报废等高风险处置生成多角色会签待办，支持通过/驳回，未完成或驳回时禁止关闭异常 | 已落地 |
| MRB会签SLA | 按风险等级、处置动作和审批角色计算 SLA，逾期可升级到责任主管，升级中仍禁止关闭异常并写审计 | 已落地 |
| MRB会议纪要 | 支持按 MRB 单号保存会议纪要正文多版本、摘要、行动项、风险说明、编辑人、变更原因和审计留痕 | 已落地 |
| SOP导入 | 文本/Markdown 导入后自动生成知识库切片并可被问答引用 | 已落地 |

## 交付验收

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| Docker Compose | PostgreSQL、后端、前端三服务配置可解析并可容器级启动 | 已通过；`smartdisplay-mes-postgres` healthy，后端 `8080`、前端 `8888` 已启动；本轮 Flyway 静态验收已升级到 `V1.41` |
| 后端构建 | `mvn.cmd -DskipTests package` 生成 `*-exec.jar` | 已通过 |
| 前端构建 | `npm.cmd run build` 通过 | 已通过，有第三方 warning |
| 前端契约验收 | 路由、API 封装、请求拦截、RBAC 菜单/按钮权限、关键页面接线和生产 mock fallback 禁用可自动检查 | 已通过 `npm.cmd run verify:frontend-contract`，302 项检查 |
| 前端视觉冒烟 | 浅色 Codex app 风格、低饱和按钮、紧凑工作台；关键页面无横向溢出、按钮文字溢出、文本裁切和控制台错误 | 已通过 `/login`、`/overview`、`/material`、`/equipment`、`/system` 视觉检查；本轮补充 `material-codex-style-desktop.png`、`material-codex-style-suppliers.png` |
| 前端 mock fallback | 开发环境可保留样例 fallback，生产环境接口失败时不静默展示样例生产数据 | 已落地，关键页面统一使用编译期 `__DEV_MOCK_FALLBACK__` 与 `src/utils/devFallback.js` |
| 前端生产包样例标识 | 默认生产构建不携带典型 mock/fallback 样例 Lot、工单、设备、Recipe、SOP、COA 编号 | 已通过 `npm.cmd run verify:production-bundle`，扫描 14 个 JS 产物 |
| 前端浏览器 E2E | 覆盖登录、导航权限、工单释放、Lot 过站、QMS/WMS Adapter 页面操作、质量证据、物料库位任务、追溯、AI 报告和系统审计入口 | 已通过 `npm.cmd run e2e:browser`，12 步通过，Console/Network 错误数为 0，最新报告 `SmartDisplay-MES-browser-e2e-20260608-161858.md` |
| Flyway | `db/migration/V1.1-V1.41` 打包并自动迁移 | 已落地 |
| Flyway验收 | 迁移静态验收脚本、全新库迁移演练、备份恢复校验、回滚策略和变更审批清单 | 已落地；全新库演练报告生成于 `V1.38`，当前 V1.41 已通过静态验收 |
| 真实数据库 API 闭环 | 在 Docker Compose PostgreSQL 上完成登录、工单创建/释放、Lot Track In/Out、NG 自动 Hold、Release、追溯、看板、AI 报告和审计落库校验 | 已通过 `tools\run-real-db-api-flow.ps1`，报告 `SmartDisplay-MES-real-db-api-flow-20260608-060901.md` |
| README | 启动、账号、API 示例、Docker 说明齐全 | 已更新 |
| 演示脚本 | 5分钟和15分钟脚本 | 已新增 |
| ER/流程图 | Mermaid 文档可审阅 | 已新增 |
| 测试报告 | 记录单元测试、服务级闭环、构建、迁移、Docker 启动和真实数据库 API 闭环实测结果 | 已新增 `SmartDisplay-MES测试报告.md` |
| 性能冒烟脚本 | 提供登录、1000 工单导入、核心列表、良率看板、Lot 追溯 P95 采集、阈值判定、Markdown/JSON 报告和失败退出码 | 已通过一轮容器环境实测；订单列表 P95 15.67ms、Lot 列表 P95 13.72ms、良率看板 P95 17.28ms、Lot 追溯 P95 60.08ms |
| 多轮性能基线 | 基于单轮性能冒烟脚本连续执行多轮采样，汇总 P95、标准差、漂移比例、稳定性告警和 Markdown/JSON 报告 | 已通过 `tools\run-pilot-performance-baseline.ps1`；3 轮各导入 1000 条工单，订单/Lot/良率/追溯最大 P95 分别为 8.59ms、7.32ms、13.25ms、20.01ms，报告 `SmartDisplay-MES-performance-baseline-20260608-061856.md` |

## 未完成的生产级增强

- 替代料验证报告附件、BOM/ECO跨部门会签、供应商绩效评分/趋势、供应商准入/复审/8D整改、库位策略和库位任务已具备首版能力。
- 后续可扩展供应商门户协同、供应商复审自动提醒，以及异步领取/复核式 WMS 任务流。
- 真实 SECS/GEM、OPC UA 或厂商 HTTP 协议驱动真机联调和毫秒级设备状态采集。
- 真实 pgvector 向量检索、真实外部模型联调和引用召回率评估。
- 真实数据库 API 闭环集成验证已补；Flyway 全新库迁移演练、前端静态契约验收、Codex app 风格视觉冒烟、真实浏览器 E2E、生产 mock fallback 收口、生产包样例标识扫描、Docker Compose 容器级启动复验和一轮性能冒烟实测已补。
- 性能验收已完成一轮冒烟实测和三轮稳定基线；后续仍建议在更接近试点数据规模、固定硬件和 CI 环境下持续采集趋势。
# 2026-06-08 补充验收：Lot 页 Rework/Scrap 前端闭环

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| Lot 页 Rework 操作 | `HOLD` Lot 可在前端选择返工 Route、返工起始工序、原因与操作人，并调用 `POST /api/v1/lots/{lotNo}/rework` | 已落地，受 `lot:rework` 按钮权限控制 |
| Lot 页 Scrap 操作 | `HOLD` Lot 可在前端填写原因、责任模块、审批人、操作人，并输入 `SCRAP:{lotNo}` 二次确认后调用 `POST /api/v1/lots/{lotNo}/scrap` | 已落地，受 `lot:scrap` 按钮权限控制 |
| Lot 页 API 口径 | Lot 列表、Track In/Out、Hold/Release、Rework/Scrap 使用 `/api/v1/lots` 试点接口 | 已统一 |
| 前端验证 | 契约检查、生产构建、生产包样例标识扫描通过 | 已通过：302 项契约检查，14 个 JS 产物扫描 |

# 2026-06-08 补充验收：质量页 MRB 报废处置入口

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| MRB 报废建议 | 质量页 MRB 待处置卡片提供 `SCRAP` 复判入口，提交后形成报废处置意见、MRB 履历和会签待办 | 已落地 |
| MRB 前端权限 | 放行/返工/报废复判只受 `quality:mrb-review` 控制，关闭异常只受 `quality:exception-close` 控制 | 已落地 |
| 前端契约 | 自动检查质量页必须包含 `SCRAP` 复判入口和复判/关闭权限拆分 | 已落地 |

# 2026-06-08 补充验收：Rework Lot 重新进站

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| REWORK 状态 Track In | 返工 Lot 状态为 `REWORK` 时，可按返工起始工序重新 Track In，并继续执行完整校验链 | 已落地 |
| 前端执行入口 | Lot 管理页和生产执行台均允许 `READY/REWORK` 状态触发 Track In | 已落地 |
| 回归测试 | 后端测试覆盖 `REWORK -> PROCESSING`，前端契约覆盖 REWORK Track In 入口 | 已落地 |

# 2026-06-08 补充验收：Rework/Scrap 处置释放 Hold

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| HOLD Lot Rework | 从 `HOLD` 发起 Rework 时，必须关闭最新打开的 `lot_hold_record`、写入处置结论、清零 `holdFlag`，最终 Lot 状态保持 `REWORK` | 已落地 |
| HOLD Lot Scrap | 从 `HOLD` 发起 Scrap 时，必须关闭最新打开的 `lot_hold_record`、写入报废原因、清零 `holdFlag`，最终 Lot 状态保持 `SCRAP` | 已落地 |
| 状态一致性 | Rework/Scrap 后追溯不应出现 Lot 已返工或报废但仍有打开 Hold 的冲突状态 | 已落地 |
| 回归测试 | `PilotMesServiceTest` 覆盖 Rework/Scrap 释放 Hold 记录，`TrackInServiceTest` 覆盖返工进站校验链 | 已通过 |

# 2026-06-08 补充验收：AI 知识库索引失败审计

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| 索引任务成功审计 | 知识库索引任务成功时写入 `AI_KB_INDEX / SOP_KB` 审计，保留检索策略、模型、切片数量和边界说明 | 已落地 |
| 索引任务失败审计 | `POST /api/v1/ai/kb/index-jobs` 失败时也写入 `AI_KB_INDEX / SOP_KB` 失败审计，便于追溯 AI 知识库维护动作 | 已落地 |
| 回归测试 | Resolver 和 FailureService 覆盖 AI 索引任务失败映射与落库调用 | 已通过 |

# 2026-06-08 补充验收：系统页权限快照重载

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| 权限重载入口 | 系统管理页提供真实“重载权限”按钮，调用 `POST /api/v1/system/permissions/reload`，不再使用占位提示 | 已落地 |
| 权限控制 | 重载按钮受 `system:permission-change` 控制，无权限时不可执行 | 已落地 |
| 前端契约 | `reloadPermissions` API 封装和系统页接线由 `verify:frontend-contract` 自动检查 | 已通过 |

# 2026-06-08 补充验收：权限变更差异对比

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| 权限差异对比 | 系统页可展示权限变更单 `beforeSnapshot/afterSnapshot` 的菜单、按钮、数据范围和领域权限差异 | 已落地 |
| 审批决策完整性 | 权限变更单支持对比、通过和驳回，驳回走既有 review 接口并刷新审计日志 | 已落地 |
| 前端契约 | 差异对比和驳回审批由 `verify:frontend-contract` 自动检查 | 已通过 |

# 2026-06-08 补充验收：系统页角色矩阵接口驱动

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| 角色权限矩阵 | 系统页角色矩阵由 `/api/v1/system/summary` 的 `permissions` 快照生成，不依赖固定静态角色数据 | 已落地 |
| 权限指标 | 启用用户、权限点、敏感权限和待审变更指标由接口数据实时派生，生产环境不静默使用样例数字 | 已落地 |
| 前端契约 | `getSystemSummary` 接线和摘要驱动角色矩阵由 `verify:frontend-contract` 自动检查 | 已通过 |

# 2026-06-08 补充验收：总览导航徽标接口驱动

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| 导航总览徽标 | 左侧生产总览的良率/WIP/异常/瓶颈徽标由 `/api/v1/dashboard/overview` 派生，生产环境不硬编码样例数字 | 已落地 |
| 旧静态看板清理 | 未被路由使用的旧 `views/dashboard/index.vue` 静态页面不再保留，避免双看板数据源 | 已落地 |
| 前端契约 | 布局徽标接口驱动、禁止静态徽标和旧看板清理由 `verify:frontend-contract` 自动检查 | 已通过 |
## 2026-06-08 QMS/WMS Adapter 验收补充

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| QMS 模拟检验上报 | `/api/v1/adapters/qms/inspections` 支持外部检验项上报，NG 自动生成检验、缺陷、异常和 Lot Hold，并写成功/失败审计 | 已落地，单元测试通过 |
| WMS 齐套查询入口 | `/api/v1/adapters/wms/material-readiness` 返回物料齐套摘要并写 adapter 审计 | 已落地，单元测试通过 |
| WMS 库存事务入口 | `/api/v1/adapters/wms/inventory-transactions` 支持入库、冻结、解冻、退料、盘点别名归一，复用物料服务并写 adapter 审计 | 已落地，单元测试通过 |
| Adapter 权限控制 | QMS adapter 绑定质量处置权限，WMS adapter 绑定 `material:wms`，跨角色写操作被拒绝 | 已落地，RBAC 回归通过 |
| Adapter 前端契约 | 前端 API 层封装 QMS/WMS adapter 调用，并纳入静态契约检查 | 已落地，`verify:frontend-contract` 通过 |

# 2026-06-08 补充验收：QMS/WMS Adapter 前端可演示入口

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| QMS 页面上报入口 | 质量管理页提供可录入 Lot、结果、检验项、参数、设备、缺陷代码和操作人的 QMS 模拟上报表单，并调用 `/api/v1/adapters/qms/inspections` | 已落地，Docker HTTP 冒烟通过 |
| QMS NG 风险提示 | 前端在选择 NG 时明确提示会自动生成异常并 Hold Lot，避免演示人员误把 NG 当作无副作用查询 | 已落地 |
| WMS Adapter 操作条 | 物料与载具页提供 WMS Adapter 齐套查询和库存事务入口，并复用当前 WMS 表单上下文生成事务 payload | 已落地，Docker HTTP 冒烟通过 |
| 页面风格一致性 | Adapter 新入口采用浅色、低饱和、细边框工作台样式，不回到深色侧栏或重色按钮风格 | 已落地 |
| 前端契约 | 质量页和物料页的 adapter API 接线由 `verify:frontend-contract` 自动检查 | 已通过，321 项检查 |

# 2026-06-08 补充验收：QMS/WMS Adapter 浏览器 E2E

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| QMS Adapter 页面级 E2E | 浏览器脚本必须从质量页填写 QMS 表单、点击“提交 QMS 上报”，并校验检验项真实落库 | 已通过，`QMS_E2E_*` 检验项落库 |
| WMS Adapter 页面级 E2E | 浏览器脚本必须从物料页点击“Adapter 齐套”和“Adapter 事务”，并校验新入库批次真实落库 | 已通过，`WMSE2E*` 批次落库 |
| E2E 稳定性 | Track In 后等待后端状态和页面行状态都刷新为 `PROCESSING` 后再执行 Track Out | 已落地，12 步 E2E 通过 |
