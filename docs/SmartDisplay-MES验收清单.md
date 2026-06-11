# SmartDisplay MES 生产级试点验收清单

## 验收范围

- 单基地、单产线、单 PostgreSQL 实例。
- 外部 ERP/EAP/QMS/WMS 使用模拟适配器。
- AI 只提供辅助分析和知识问答，不执行写生产动作。

## 功能验收

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| JWT 登录 | 6类角色可登录，未登录接口被拒绝 | 已落地 |
| 角色写权限 | 计划员释放工单、操作员过站、质量/工艺/设备角色按域写权限控制 | 已落地，浏览器 E2E 已覆盖操作员越权释放工单返回 403 |
| 前端菜单/按钮权限 | 菜单按角色裁剪，关键写按钮按按钮权限隐藏并兜底拦截 | 已落地，后端登录权限快照与前端权限矩阵已统一 PE/EE 质量菜单口径 |
| 权限变更审计闭环 | 权限变更单保存前后快照，支持提交、审批通过/驳回、审计留痕、运行期权限快照应用、启动恢复和手动重载 | 已落地 |
| 审计请求上下文 | 关键审计记录自动保存请求方法、URI、客户端IP和User-Agent | 已落地 |
| 系统审计分页筛选 | 系统审计支持分页查询，按业务对象、动作分组、结果、来源、操作人和日期范围过滤，并可导出请求上下文与快照字段 | 已落地，后端定向测试 34 项通过，前端契约已覆盖分页筛选和上下文导出 |
| 关键写接口失败审计 | 业务异常、参数校验异常和系统异常按关键写接口动作写入 `result=FAIL` 审计记录 | 已落地 |
| 核心执行审计差异快照 | 工单创建/释放、Track In/Out、Hold/Release、Rework/Scrap 审计快照包含 before、after、changedFields 和 request；Lot 批量 Hold/Release 包含整批汇总快照 | 已落地 |
| 数据范围 SQL | 按 ALL/LINE/SELF_SHIFT/SELF 生成安全 SQL 条件，工单、Lot、质量、异常、物料消耗、载具等列表按域过滤 | 已落地，组织/产线/班次主数据已补 |
| 组织/产线/班次主数据 | 基地、产线、班次有正式表、种子数据和 `/api/v1/master/**` 查询接口 | 已落地 |
| ERP模拟工单导入 | 支持 `/api/v1/adapters/erp/orders` 下发工单、批量查重、1000 条模拟导入、成功/失败审计和角色权限控制 | 已落地，工单页已提供 Adapter 批次、样例工单和审计动作回执 |
| 工单释放 | 工单释放前必须通过状态、产品编码、Route首站、生效BOM、设备能力、Recipe覆盖、Lot拆分和权限审计预校验；通过后生成 Lot 并写审计 | 已落地，新增 `/api/v1/orders/{orderNo}/release-checks`，工单页释放校验已改为接口驱动 |
| Track In预校验 | Track In 前必须返回 Lot状态、Route下一站、设备状态、设备能力、Recipe、Hold、班次、物料齐套、操作权限和审计留痕矩阵，失败项阻断进站 | 已落地，新增 `/api/v1/lots/{lotNo}/track-in-checks`，执行页校验链已改为接口驱动，正式 Track In 复用同一套阻断逻辑 |
| Route 防跳站 | Track In 必须匹配 Route 下一站 | 已落地，已纳入 Track In 预校验矩阵 |
| Recipe 校验 | Track In 校验产品+工序+设备生效 Recipe | 已落地，已纳入 Track In 预校验矩阵 |
| 班次校验 | Track In 校验 Lot 产线当前时间处于 ACTIVE 班次窗口 | 已落地，已纳入 Track In 预校验矩阵 |
| 物料齐套 | Track In 校验 BOM 关键物料并锁定批次 | 已落地，已纳入 Track In 预校验矩阵 |
| BOM变更审批 | 支持变更草稿、审批通过/驳回、发布生效、旧版本失效和审计留痕 | 已落地 |
| BOM/ECO跨部门会签 | BOM变更提交后生成 ECO 包快照、风险等级、会签角色和 SLA；PE/QE/计划员/设备角色可会签，通过前禁止发布，驳回后阻断发布 | 已落地，V1.39 已验证 3 角色会签后发布 |
| 替代料策略 | Track In 按 substitute_group 和 substitute_priority 自动选择可用主料/替代料 | 已落地 |
| 替代料验证报告附件 | BOM 变更提交/审批可保存验证报告附件元数据，BOM 变更列表返回附件数量和附件明细 | 已落地，首版附件元数据 |
| WMS库存事务 | 支持入库、冻结、解冻、退料、盘点、事务履历、审计和批次行锁 | 已落地 |
| WMS库位策略 | 支持库位主数据、存储类型、物料类别、容量、环境窗口、优先级、锁定状态；入库校验库位状态/容量/类别/单位并更新占用 | 已落地，首版策略 |
| WMS库位任务 | 支持上架、整批移库、拆批、盘点任务，记录任务单、库存事务和审计；支持创建、领取、完成、取消、完成后复核分步状态流；支持复核通过/驳回、复核结论、异常原因、复核驳回后的让步接收/显式调库/升级处置、优先级、SLA 到期时间和逾期待办排序；物料页提供任务操作台和最近任务表 | 已落地，V1.38 已验证 `CREATED/ASSIGNED/DONE/CANCELLED`；2026-06-09 已补齐创建/领取/完成/取消审计 `before/after/changedFields/request` 快照；2026-06-10 已补齐多库位拆批任务、完成后复核闭环、SLA 待办排序、复核结果留痕和复核驳回处置 |
| 来料IQC/COA | 支持供应商批次来料判定、COA编号、附件元数据、批次质量状态联动和审计 | 已落地 |
| 供应商绩效评分/趋势 | 基于物料批次、来料IQC与8D记录聚合批次数、PASS/HOLD/NG、通过率、风险批次、评分、风险等级和最近6个月月度趋势，并提供物料页只读看板 | 已落地，首版聚合评分和趋势 |
| 供应商准入/8D整改 | 支持供应商主数据、准入状态评估、8D整改单创建/关闭、IQC NG/HOLD 自动开8D、供应商风险降级、审计与前端处置工作区 | 已落地，V1.40 已通过后端/前端/Flyway 验收 |
| 供应商准入复审任务 | 支持周期复审任务查询、创建、通过/驳回决策、建议准入状态回写、重复 OPEN 任务拦截、审计与前端复审工作区 | 已落地，V1.41 已通过后端/前端/Flyway 验收 |
| 设备/EAP事件 | 支持设备事件落库、EAP参数采样、参数越限自动事件、设备状态联动、PM任务完成和审计 | 已落地 |
| Recipe下发/回读 | 支持校验 ACTIVE Recipe、下发参数快照、EAP回读确认、Mismatch自动设备事件和审计 | 已落地 |
| 设备OEE/停机原因 | 支持近24小时 OEE 拆解、计划/非计划停机、停机原因TopN、事件关闭回填时长和审计 | 已落地 |
| 设备状态历史/节拍采样 | 支持 EAP 状态上报、设备状态变化历史、标准/实际节拍采样、良品/产出数量和 OEE 性能率样本口径 | 已落地 |
| 标准节拍主数据 | 支持产品+工序+设备+Recipe+版本的标准节拍、上下限窗口、ACTIVE发布、旧版本失效和审计；节拍样本可自动匹配主数据 | 已落地 |
| EAP统一适配器 | `/api/v1/adapters/eap/messages` 支持状态、节拍、参数、Recipe下发标准化消息，外部协议先经网关驱动归一化后进入模拟适配器 | 已落地，模拟适配 + 影子协议入口；失败入站保留诊断证据并写失败审计 |
| EAP网关连接 | 支持网关注册/更新、心跳、连接状态、消息入站履历、处理成功/失败状态和失败降级留痕；SECS/GEM、OPC UA、厂商HTTP默认以 `SHADOW` 模式接入 | 已落地，影子协议网关；消息详情可回看原始快照、归一化快照、适配器响应和诊断建议 |
| EAP协议驱动配置 | 支持模拟HTTP、厂商HTTP、SECS/GEM、OPC UA驱动能力列表、协议帧必填校验、驱动配置快照和消息归一化快照 | 已落地，影子协议驱动 |
| EAP网关健康检查 | 支持手动健康检查、PASS/WARN/FAIL履历、延迟与错误说明、状态联动和审计留痕；`SHADOW` 返回待真机握手的 WARN，`EXTERNAL` 未配置真实链路返回 FAIL | 已落地 |
| Track Out | 记录参数快照、人员、设备、结果并推动 Lot 流转 | 已落地 |
| 质量异常 | NG/关键参数超限生成质检、缺陷、异常并 Hold Lot | 已落地 |
| Hold/Release | 记录原因、处置结论、责任角色、人员和时间，支持单 Lot 与批量处置 | 已落地，支持 MRB 结论联动和 Lot 批量 Hold/Release 汇总审计 |
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
| Docker Compose | PostgreSQL、后端、前端三服务配置可解析并可容器级启动 | 已通过；`smartdisplay-mes-postgres` healthy，后端 `8080`、前端 `8888` 已启动；本轮 Flyway 静态验收已升级到 `V1.50`，容器库已迁移到 `1.50` |
| 后端构建 | `mvn.cmd -DskipTests package` 生成 `*-exec.jar` | 已通过 |
| API文档合同 | Swagger/OpenAPI 必须提供 `/api/v3/api-docs/pilot-v1` 分组，声明 `/api/v1/**`、JWT Bearer、统一响应、分页模型和标准错误响应 | 已落地，`OpenApiConfigTest` 5 项通过；Docker 运行态已验证 `Result/PageResult`、400/401/403/500、登录免 Bearer 和业务接口 Bearer 安全要求 |
| 前端构建 | `npm.cmd run build` 通过 | 已通过，有第三方 warning |
| 前端契约验收 | 路由、API 封装、请求拦截、RBAC 菜单/按钮权限、关键页面接线、Lot/Recipe 二级工作台、Lot 批量 Hold/Release、WMS 多库位拆批、WMS 库位任务复核、WMS 库位任务复核结果、WMS 库位任务复核驳回处置、WMS 库位任务 SLA、审计分页筛选和生产 mock fallback 禁用可自动检查 | 已通过 `npm.cmd run verify:frontend-contract`，420 项检查；已覆盖工单释放预校验、Track In 预校验 API 接线、Lot 批量处置接线、WMS 拆批、复核通过/驳回、复核结论、复核驳回处置、SLA 任务入口、EAP 消息详情诊断接线、系统审计快照查看入口、审计分页筛选、上下文导出和禁止静态校验通过数 |
| 前端视觉冒烟 | 浅色 Codex app 风格、低饱和按钮、紧凑工作台；关键页面无横向溢出、按钮文字溢出、文本裁切和控制台错误 | 已通过 `/login`、`/overview`、`/material`、`/equipment`、`/system` 视觉检查；本轮补充 `material-codex-style-desktop.png`、`material-codex-style-suppliers.png` |
| 前端 mock fallback | 开发环境可保留样例 fallback，生产环境接口失败时不静默展示样例生产数据 | 已落地，关键页面统一使用编译期 `__DEV_MOCK_FALLBACK__` 与 `src/utils/devFallback.js` |
| 前端生产包样例标识 | 默认生产构建不携带典型 mock/fallback 样例 Lot、工单、设备、Recipe、SOP、COA 编号 | 已通过 `npm.cmd run verify:production-bundle`，扫描 14 个 JS 产物 |
| 前端浏览器 E2E | 覆盖登录、导航权限、工单页 UI 下发 ERP 工单并释放、Lot 管理 Hold/Release/Rework/Scrap、Recipe 管理、Lot 过站、Track In 预校验矩阵、QMS/WMS Adapter 页面操作、物料库位任务、供应商到期复审生成审计、设备 EAP 参数/网关健康检查、EAP 失败消息诊断抽屉、质量证据、追溯、AI 报告、系统审计入口和操作员越权拒绝 | 已通过 `npm.cmd run e2e:browser`，20 步通过，Console/Network 错误数为 0，最新报告 `SmartDisplay-MES-browser-e2e-20260610-182527.md` |
| CI 浏览器 E2E 门禁 | CI 必须可启动 Docker Compose 三服务，并在真实浏览器中执行端到端闭环 | 已接入 `.github/workflows/ci.yml` 的 `Docker browser E2E` job，报告作为 Actions artifact 上传 |
| Flyway | `db/migration/V1.1-V1.50` 打包并自动迁移 | 已落地 |
| Flyway验收 | 迁移静态验收脚本、全新库迁移演练、备份恢复校验、回滚策略和变更审批清单 | 已落地；全新库演练报告生成于 `V1.38`，当前 `V1.50` 静态验收通过 |
| 真实数据库 API 闭环 | 在 Docker Compose PostgreSQL 上完成登录、工单创建/释放、Lot Track In/Out、NG 自动 Hold、Release、追溯、看板、AI 报告和审计落库校验 | 已通过 `tools\run-real-db-api-flow.ps1`，报告 `SmartDisplay-MES-real-db-api-flow-20260608-060901.md` |
| README | 启动、账号、API 示例、Docker 说明齐全 | 已更新 |
| 演示脚本 | 5分钟和15分钟脚本 | 已新增 |
| ER/流程图 | Mermaid 文档可审阅 | 已新增 |
| 测试报告 | 记录单元测试、服务级闭环、构建、迁移、Docker 启动和真实数据库 API 闭环实测结果 | 已新增 `SmartDisplay-MES测试报告.md` |
| 性能冒烟脚本 | 提供登录、1000 工单导入、核心列表、良率看板、Lot 追溯 P95 采集、阈值判定、Markdown/JSON 报告和失败退出码 | 已通过一轮容器环境实测；订单列表 P95 15.67ms、Lot 列表 P95 13.72ms、良率看板 P95 17.28ms、Lot 追溯 P95 60.08ms |
| 多轮性能基线 | 基于单轮性能冒烟脚本连续执行多轮采样，汇总 P95、标准差、漂移比例、稳定性告警和 Markdown/JSON 报告 | 已通过 `tools\run-pilot-performance-baseline.ps1`；3 轮各导入 1000 条工单，订单/Lot/良率/追溯最大 P95 分别为 8.59ms、7.32ms、13.25ms、20.01ms，报告 `SmartDisplay-MES-performance-baseline-20260608-061856.md` |
| CI 手动性能基线 | 交付复验时可在 CI 中启动 Docker Compose 并执行多轮性能基线 | 已接入 `Manual Docker performance baseline` job，通过 `workflow_dispatch` 手动触发，支持 rounds、samples、import count 参数 |

## 未完成的生产级增强

- 替代料验证报告附件、BOM/ECO跨部门会签、供应商绩效评分/趋势、供应商准入/复审/8D整改、供应商复审自动提醒、库位策略和库位任务已具备首版能力，多库位拆批任务、完成后复核通过/驳回留痕和复核驳回处置已落地。
- 后续可扩展供应商门户协同，以及 WMS 任务异步队列、抽检复核、复核驳回后的异常/MRB 编排和多角色审批；首版显式库存调整已落地，仍可继续细化库存差异责任归因。
- 真实 SECS/GEM、OPC UA 或厂商 HTTP 协议驱动真机联调和毫秒级设备状态采集。
- 真实 pgvector 向量检索、真实外部模型联调和引用召回率评估。
- 真实数据库 API 闭环集成验证已补；Flyway 全新库迁移演练、前端静态契约验收、Codex app 风格视觉冒烟、真实浏览器 E2E、生产 mock fallback 收口、生产包样例标识扫描、Docker Compose 容器级启动复验和一轮性能冒烟实测已补。
- 性能验收已完成一轮冒烟实测和三轮稳定基线；CI 已提供手动性能基线门禁，后续仍建议在固定硬件和更接近试点数据规模下持续积累趋势。
# 2026-06-08 补充验收：Lot 页 Rework/Scrap 前端闭环

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| Lot 页 Rework 操作 | `HOLD` Lot 可在前端选择返工 Route、返工起始工序、原因与操作人，并调用 `POST /api/v1/lots/{lotNo}/rework` | 已落地，受 `lot:rework` 按钮权限控制 |
| Lot 页 Scrap 操作 | `HOLD` Lot 可在前端填写原因、责任模块、审批人、操作人，并输入 `SCRAP:{lotNo}` 二次确认后调用 `POST /api/v1/lots/{lotNo}/scrap` | 已落地，受 `lot:scrap` 按钮权限控制 |
| Lot 页 API 口径 | Lot 列表、Track In/Out、Hold/Release、批量 Hold/Release、Rework/Scrap 使用 `/api/v1/lots` 试点接口 | 已统一 |
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
| E2E 稳定性 | Track In 后等待后端状态和页面行状态都刷新为 `PROCESSING` 后再执行 Track Out | 已落地，并随当前 14 步 E2E 继续通过 |

## 2026-06-09 补充验收：Lot/Recipe 二级工作台浏览器 E2E

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| Lot 管理页运行级覆盖 | 浏览器脚本必须直接访问 `/lot`，校验 Lot 队列、Track In/Out、Rework、Scrap 入口，并按本轮释放 Lot 查询到真实记录 | 已通过，`LOTE2E*` Lot 可在 Lot 管理页查到 |
| Lot 管理页 Hold/Release | 浏览器脚本必须从 Lot 管理页打开 Hold 弹窗，填写 Hold 原因，确认二次弹窗，等待 Lot 进入 `HOLD/holdFlag=1`，再从页面执行 Release 并恢复 `READY/holdFlag=0` | 已通过，Hold/Release 后同一 Lot 可继续 Track In/Out |
| Lot 管理页 Rework | 浏览器脚本必须创建独立 Hold Lot，从 Lot 管理页打开 Rework 弹窗，提交返工原因和操作人，等待 Lot 进入 `REWORK/holdFlag=0` 并保留返工起始工序 | 已通过，`LOTRWK*` Lot 进入 `REWORK` |
| Lot 管理页 Scrap | 浏览器脚本必须创建独立 Hold Lot，从 Lot 管理页打开 Scrap 弹窗，填写原因、审批人、操作人，并输入 `SCRAP:{lotNo}` 二次确认，等待 Lot 进入 `SCRAP/holdFlag=0` | 已通过，`LOTSCP*` Lot 进入 `SCRAP` |
| Recipe 管理页运行级覆盖 | 浏览器脚本必须直接访问 `/recipe`，校验 Recipe 版本池、参数详情、发布入口，并打开参数详情抽屉看到参数上下限和执行约束 | 已通过，Recipe 详情抽屉可见 |
| E2E 覆盖范围 | Lot/Recipe 二级页面不得只依赖静态契约或路由检查，必须纳入真实浏览器回归 | 已落地，并随当前 17 步 E2E 继续通过 |

## 2026-06-09 补充验收：RBAC权限口径与操作员越权

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| PE/EE 菜单口径 | 后端登录返回的权限快照必须与前端权限矩阵一致，工艺/设备工程师可看到与自身处置相关的质量协同页面 | 已落地，`RolePermissionServiceTest` 覆盖 |
| 操作员菜单收敛 | 操作员只显示生产总览、生产执行和追溯入口，不显示计划工单和系统管理入口 | 已落地，浏览器 E2E 覆盖 |
| 操作员越权拒绝 | 操作员直接访问 `/order` 应被路由守卫重定向，直接调用工单释放接口应返回 403 | 已落地，浏览器 E2E 覆盖 |
| Docker 运行态 | 当前 Docker 容器应使用本轮权限代码，登录和越权拒绝可经前端 Nginx 反代验证 | 已通过：EE 菜单含 `quality`，操作员越权释放返回 `403` |
| 回归验证 | 权限口径、前端契约和真实浏览器用例均需通过 | 已通过：RBAC 单测 12 项、前端契约 383 项、浏览器 E2E 当前 20 步 |

## 2026-06-09 补充验收：设备页 EAP 运行级闭环

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| EAP 参数页面上报 | 设备页必须能从 UI 提交参数样本，并通过 `/api/v1/equipment/parameters` 查询到真实记录 | 已落地，浏览器 E2E 覆盖唯一 `EAP_E2E_*` 参数编码 |
| EAP 网关健康检查 | 设备页必须能从 UI 触发网关健康检查，并写入 `MANUAL` 检查履历 | 已落地，浏览器 E2E 覆盖 |
| 页面布局回归 | 设备页新增写操作覆盖时仍必须通过无横向溢出、按钮文字溢出和标题裁切检查 | 已通过 `assertLayoutClean('equipment-eap')` |

## 2026-06-09 补充验收：供应商到期复审自动提醒

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| 到期复审生成 | 系统可扫描 `nextAuditDue` 到期窗口内供应商，自动生成周期复审任务，并跳过已有 OPEN 复审的供应商 | 已落地，`MaterialServiceTest` 覆盖创建与跳过 |
| 失败审计映射 | 到期复审生成接口失败时必须映射为可查询的失败审计动作 | 已落地，映射 `SUPPLIER_QUALIFICATION_REVIEW_GENERATE` |
| 前端入口 | 物料页供应商准入复审卡片提供权限控制的“生成到期复审”入口 | 已落地，受 `material:supplier-manage` 控制 |
| 浏览器 E2E | 页面点击生成到期复审后必须能在系统审计中查到批处理审计 | 已通过，浏览器 E2E 当前 20 步 |

## 2026-06-09 补充验收：工单页 ERP Adapter UI 闭环

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| ERP 下发页面回执 | 工单页点击“下发 ERP 工单”后必须展示 Adapter 批次、接收/创建/跳过/失败数量、样例工单和 `ERP_ORDER_IMPORT` 审计动作 | 已落地 |
| ERP 下发审计查询 | 浏览器 E2E 必须按页面返回批次号查询系统审计，确认 `ERP_ORDER_IMPORT` 已写入 | 已通过 |
| ERP 下发后释放 | 浏览器 E2E 必须释放页面下发的样例工单，并查询到生成的 Lot | 已通过 |
| 前端契约 | 工单页 ERP 导入回执由 `verify:frontend-contract` 自动检查，防止页面退回只显示静态按钮 | 已通过，383 项检查 |

## 2026-06-08 补充验收：多入口追溯搜索

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| Lot 追溯 | 输入 Lot 后返回工单、Route、过站、Hold、质检、异常、物料消耗和审计链路 | 已落地，复用 `traceLot` 全链路 |
| SN 追溯 | 输入 `LOT...-SN...` 可解析到绑定 Lot，并返回 SN 信息和 Lot 全链路 | 已落地，`LOT202406001-SN001` Docker 探活通过 |
| 工单追溯 | 输入工单号可归集该工单下受影响 Lot 列表，并展开首个 Lot 证据链 | 已落地于 `/api/v1/trace/search?type=ORDER` |
| 设备追溯 | 输入设备号可基于过站记录和当前设备反查受影响 Lot | 已落地，`COATER_01` Docker 探活命中 5 个 Lot |
| 物料批次追溯 | 输入物料批次可基于 `material_consumption` 反查受影响 Lot | 已落地于 `/api/v1/trace/search?type=MATERIAL_BATCH` |
| 缺陷代码追溯 | 输入缺陷代码可基于质检记录反查受影响 Lot | 已落地于 `/api/v1/trace/search?type=DEFECT_CODE` |
| 影响范围摘要 | 返回命中 Lot 数、Hold Lot 数、NG 检验数、物料批次数、缺陷代码数和设备数 | 已落地，返回 `impactSummary` |
| 相关维度聚合 | 返回工单、设备、物料批次和缺陷代码维度，便于反向排查 | 已落地，返回 `relatedDimensions` |
| 数据范围控制 | 多入口候选 Lot 必须经当前角色数据范围过滤 | 已落地，候选行最终通过 `findLot` 校验 |
| 前端追溯页 | 追溯页以接口数据驱动，支持查询类型选择、关键字输入、影响 Lot 列表和证据卡片 | 已落地，浏览器 E2E 通过 |
| 生产包收口 | 默认生产包不应携带典型 mock/fallback Lot 或工单编号 | 已通过 `verify:production-bundle`，12 个 JS 产物 clean |
## 2026-06-08 补充验收：载具绑定追溯与 Hybrid Local RAG

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| 载具绑定/解绑 | 支持 `/api/v1/carriers/{carrierNo}/bind` 和 `/api/v1/carriers/{carrierNo}/unbind`，校验 Lot 权限、载具占用状态，并写成功审计 | 已落地 |
| 载具追溯证据 | Lot 追溯返回 `carriers`，并在 `impactSummary` / `relatedDimensions` 中提供载具数量和载具号 | 已落地，Docker 探活 `carrierCount=1` |
| 载具前端入口 | 物料页提供 Carrier No、Lot No、Step、Equipment 的绑定/解绑操作条，受 `material:wms` 权限控制 | 已落地 |
| 本地混合 RAG 索引 | 知识库索引任务支持 `HYBRID_LOCAL`，切片标记为 `LOCAL_VECTOR_INDEXED`，不调用外部模型 | 已落地 |
| RAG 证据评分 | SOP 问答返回引用来源、检索策略、关键词分、向量分、证据等级和证据不足标识 | 已落地 |
| AI 配置迁移 | Flyway `V1.43` 激活 `LOCAL_RAG_HYBRID`，`SOP_QA` 默认使用 `HYBRID_LOCAL` | 已落地，Docker 运行库已到 `v1.43` |
| 前端 AI 入口 | AI 页提供 `Hybrid Local` 索引按钮，并纳入前端契约检查 | 已落地，`verify:frontend-contract` 328 项通过 |
| 回归验证 | 后端全量、前端契约、生产构建、生产包扫描、Docker 探活和浏览器 E2E 均通过 | 已通过 |

## 2026-06-10 补充验收：MES 手工质检录入

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| MES 手工质检写入口 | `POST /api/v1/quality/inspections` 必须创建真实 `quality_inspection` 记录，而不是返回静态或查询占位数据 | 已落地，HTTP 冒烟返回 `messageType=MANUAL_INSPECTION` |
| NG 自动闭环 | 手工质检 NG 必须生成缺陷、异常事件并自动 Hold Lot | 已落地，`QualityServiceTest` 覆盖 |
| OK 审计留痕 | 手工质检 OK 必须写检验记录和 `QUALITY_INSPECTION` 审计，不触发 Hold | 已落地，Docker 冒烟返回 `holdApplied=false` |
| 权限边界 | QE 可录入 MES 手工质检，PE/OPERATOR 不可越权调用该写入口 | 已落地，`quality:inspection-create` 权限和 `RolePermissionServiceTest` 覆盖 |
| 前端双来源 | 质量页必须支持 `MES 手工录入` 与 `QMS Adapter` 两种来源，并分别调用独立 API | 已落地，前端契约 401 项通过 |
| Docker 运行态 | 当前 Docker 页面和接口必须能看到本轮质量页与手工质检写接口 | 已通过覆盖部署；完整镜像重建待本机 Docker 代理恢复后复跑 |

## 2026-06-10 补充验收：AI 设备异常分析

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| 设备异常分析入口 | AI 页必须提供设备异常分析工作区，并可调用 `POST /api/v1/ai/equipment/analyze` | 已落地，前端契约 404 项覆盖 |
| MES 证据快照 | AI 设备分析必须留存设备事件、关联 Lot、近期缺陷、良率看板、模型配置和 SOP 证据输入快照 | 已落地，`PilotMesServiceTest` 覆盖 |
| 输出结构 | 返回风险等级、事件数、Lot 数、缺陷数、可能原因、排查步骤、引用来源和 `writeActionAllowed=false` | 已落地 |
| 权限边界 | QE/EE 可执行设备异常分析，OPERATOR 不可调用 AI 写入口 | 已落地，后端和前端权限矩阵已对齐 |
| AI 安全边界 | AI 结果只做辅助分析，不自动 Hold、Release、停机或派工 | 已落地，输出和留痕均标记 `writeActionAllowed=false` |
| 回归验证 | 后端全量、前端契约、生产构建和生产包扫描必须通过 | 已通过：后端 226 项、前端契约 404 项、生产包 14 个 JS 产物 clean |
| Docker 冒烟 | Docker 运行态必须能经前端反代生成设备异常分析并查询到 AI 留痕 | 已通过：`EVAP_01` 返回 `P1/HIGH`，留痕 1 条 |

## 2026-06-10 补充验收：Recipe 发布审计

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| Recipe 创建审计 | 创建 Recipe 草稿必须写 `RECIPE_CREATE`，并保存参数数量、产品、工序、设备和版本快照 | 已落地，`RecipeServiceTest` 覆盖 |
| Recipe 发布审计 | `POST /api/v1/recipes/{id}/publish` 成功必须写 `RECIPE_PUBLISH`，不能只复用泛化激活动作 | 已落地，V1 接口已切到 `publishRecipe` |
| 结构化快照 | 发布、激活、停用审计必须包含 `before/after/changedFields/request`，可看出 `DRAFT -> ACTIVE` 或 `ACTIVE -> INACTIVE` | 已落地，单元测试断言快照 |
| 失败审计口径 | Recipe 发布失败必须归类到 `RECIPE_PUBLISH`，与成功审计动作一致 | 已落地，`AuditFailureResolverTest` 覆盖 |
| 回归验证 | Recipe 定向、失败审计定向和后端全量测试必须通过 | 已通过：定向 43 项、后端全量 227 项 |
| Docker 冒烟 | Docker 运行态必须能创建 DRAFT Recipe、发布并查询到 `RECIPE_PUBLISH` 审计 | 已通过：`RCP_AUDIT_20260610121152` 发布后管理员查询到 1 条 `RECIPE_PUBLISH`，快照含 `DRAFT -> ACTIVE` |

## 2026-06-10 补充验收：Recipe 单一生效版本治理

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| 单一 ACTIVE 业务规则 | 同一产品、工序、设备下任意时刻只能有一个 `ACTIVE` Recipe | 已落地，发布/激活服务层自动停用旧 `ACTIVE`，数据库部分唯一索引兜底 |
| 自动停用审计 | 新版本发布导致旧版本失效时，必须能追溯旧版本从 `ACTIVE -> INACTIVE` 的原因和触发版本 | 已落地，写 `RECIPE_AUTO_DEACTIVATE`，快照包含触发 Recipe 和单一生效上下文 |
| 发布替换快照 | `RECIPE_PUBLISH` 审计必须能看出本次发布替换了多少旧版本、替换了哪些 Recipe | 已落地，快照包含 `singleActiveContext`、`replacedActiveCount`、`replacedActiveRecipes` |
| 数据库一致性 | 即使绕过服务层，也不能在未删除数据中产生同上下文多条 `ACTIVE` | 已落地，`V1.44__Enforce_Single_Active_Recipe.sql` 创建 `uk_recipe_single_active_context` |
| 回归验证 | Recipe 定向和后端全量测试必须通过 | 已通过：Recipe 定向 12 项、后端全量 229 项 |
| Docker 冒烟 | Docker 运行态必须完成 V1.44 迁移，并验证两个同上下文版本发布后的状态和审计 | 已通过：`RCP_SINGLE_20260610124831_V1` 自动失效，`RCP_SINGLE_20260610124831_V2` 生效；发布和自动停用审计各 1 条 |

## 2026-06-10 补充验收：Route 单一生效版本治理

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| 单一 ACTIVE 业务规则 | 同一产品任意时刻只能有一个 `ACTIVE` Route，工单释放和执行校验不能依赖歧义路线 | 已落地，服务层发现多条 `ACTIVE` 时明确拒绝 |
| 数据库一致性 | 即使绕过服务层，也不能在未删除数据中产生同产品多条 `ACTIVE` Route | 已落地，`V1.45__Enforce_Single_Active_Route.sql` 创建 `uk_route_single_active_product` |
| 历史数据收敛 | 对已有多条 `ACTIVE` Route 的库，迁移必须先保留最新一条并停用其他版本，避免建索引失败 | 已落地，迁移按更新时间、生效时间、版本和 ID 排序收敛 |
| 回归验证 | Route 定向和后端全量测试必须通过 | 已通过：Route 定向 9 项、后端全量 230 项 |
| Docker 冒烟 | Docker 运行态必须完成 V1.45 迁移，并验证 API 与唯一索引 | 已通过：`/api/v1/routes` 每产品仅返回一条 ACTIVE；插入同产品第二条 ACTIVE 被唯一索引拒绝 |

## 2026-06-10 补充验收：BOM 单一生效版本治理

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| 单一 ACTIVE 业务规则 | 同一产品任意时刻只能有一个 `ACTIVE` BOM，工单释放和物料齐套校验不能依赖歧义 BOM | 已落地，服务层发现多条 `ACTIVE` 时明确拒绝 |
| 发布自动停用旧版 | 新 BOM 发布后必须自动停用同产品旧 `ACTIVE` BOM，避免人工遗漏导致多版本并存 | 已落地，`publishBomChange` 自动置旧版为 `INACTIVE` |
| 自动停用审计 | 自动停用旧版 BOM 必须能追溯触发变更单、触发 BOM 和单一生效上下文 | 已落地，写 `BOM_AUTO_DEACTIVATE`，快照包含触发 BOM 和 `productCode` |
| 发布替换快照 | `BOM_PUBLISH` 审计必须能看出本次发布替换了多少旧版本、替换了哪些 BOM | 已落地，快照包含 `singleActiveContext`、`replacedActiveCount`、`replacedActiveBoms` |
| 数据库一致性 | 即使绕过服务层，也不能在未删除数据中产生同产品多条 `ACTIVE` BOM | 已落地，`V1.46__Enforce_Single_Active_Bom.sql` 创建 `uk_bom_single_active_product` |
| 历史数据收敛 | 对已有多条 `ACTIVE` BOM 的库，迁移必须先保留最新一条并停用其他版本，避免建索引失败 | 已落地，迁移按更新时间、生效时间、版本和 ID 排序收敛 |
| 回归验证 | Material 定向和后端全量测试必须通过 | 已通过：Material 定向 44 项、后端全量 231 项 |
| Docker 冒烟 | Docker 运行态必须完成 V1.46 迁移，并验证数据库唯一索引 | 已通过：`flyway_schema_history` 记录 `version=1.46`；插入同产品第二条 ACTIVE BOM 被唯一索引拒绝 |

## 2026-06-10 补充验收：后端试点 fallback 生产默认关闭

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| 显式 fallback 开关 | 生产默认不得静默返回试点样例数据，演示降级必须显式开启配置 | 已落地，`mes.pilot.fallback-enabled=false` 为默认值 |
| 空结果语义 | 正式表查询成功但无数据时，应返回真实空结果，不得补充样例列表 | 已落地，BOM、Route、审计、缺陷 TopN 等聚合查询已收口 |
| 失败语义 | 正式表读取失败且未启用 fallback 时，应返回明确业务异常，不能用样例数据掩盖问题 | 已落地，异常提示包含“未启用试点fallback” |
| 覆盖范围 | 试点聚合接口中的 BOM、Route、设备、物料、质量、异常、审计、看板等 fallback 路径必须受统一开关控制 | 已落地，`PilotMesService` 已统一辅助方法收口 |
| 回归验证 | 聚合服务定向和后端全量测试必须通过 | 已通过：`PilotMesServiceTest` 37 项、后端全量 236 项 |
| Docker 冒烟 | Docker 运行态查询不存在审计对象时必须返回空数组，而不是演示审计样例 | 已通过：`NO_SUCH_AUDIT_OBJECT_20260610` 返回 `data=[]` |

## 2026-06-10 补充验收：追溯 Route 证据按 Lot 产品匹配

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| Route 证据准确性 | Lot 追溯返回的 Route 必须按当前 Lot 产品读取，不能从全局生效 Route 列表取第一条 | 已落地，`traceLot` 调用 `findActiveRoute(lot.productCode)` |
| 工序序列证据 | 追溯返回 Route 时必须包含当前产品生效工序序列，便于审计防跳站和返工路径 | 已落地，返回 `route.steps` |
| 主数据缺失语义 | 当前产品缺失生效 Route 时，追溯不得抛下标异常，应返回可读的缺失证据 | 已落地，返回 `route.status=MISSING` 和错误说明 |
| 回归验证 | 追溯定向和后端全量测试必须通过 | 已通过：追溯定向 38 项、后端全量 236 项 |
| Docker 冒烟 | Docker 运行态 Lot 追溯中 `lot.productCode` 与 `route.productCode` 必须一致 | 已通过：`LOTSCP20260610010414-001` 返回 `AMOLED_65 / RTE_G6_AMOLED65_V08 / AMOLED_65` |

## 2026-06-10 补充验收：EAP 消息失败留痕与诊断详情

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| 失败消息不丢失 | EAP 入站失败时必须保留 `equipment_gateway_message` 的 `FAILED` 记录、错误信息和失败响应快照 | 已落地，`EapGatewayServiceTest` 覆盖 |
| 失败审计 | EAP 入站失败必须写 `EAP_GATEWAY_MESSAGE_FAILED`，审计结果为 `FAIL` | 已落地，单元测试断言 `recordFailure` |
| 消息详情接口 | `/api/v1/equipment/gateway-messages/{messageNo}` 必须返回原始快照、归一化快照、响应快照和诊断建议 | 已落地，详情测试覆盖 |
| 前端诊断入口 | 设备页消息履历必须可打开诊断抽屉，展示失败分类、处置建议和三类快照 | 已落地，前端契约当前 413 项和浏览器 E2E 覆盖 |
| 回归验证 | EAP 定向、前端契约、生产构建、生产包扫描和浏览器 E2E 必须通过 | 已通过：EAP 15 项、前端契约当前 413 项、生产包 14 个 JS 产物 clean、浏览器 E2E 20 步 |
| Docker 冒烟 | Docker 运行态必须能提交一条失败 EAP 入站、查询消息详情和失败审计 | 已通过：`EGM-DIAG-20260610181059` 返回 `FAILED/PROTOCOL_FRAME`，审计 `EAP_GATEWAY_MESSAGE_FAILED/FAIL` 命中 1 条 |

## 2026-06-10 补充验收：Lot 批量 Hold/Release

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| 批量 Hold 接口 | `POST /api/v1/lots/batch-hold` 必须支持最多 50 个 Lot，逐 Lot 执行既有 Hold 状态机，并返回每个 Lot 的成功/失败原因 | 已落地，`PilotMesServiceTest` 覆盖 |
| 批量 Release 接口 | `POST /api/v1/lots/batch-release` 必须支持最多 50 个 Lot，逐 Lot 执行既有 Release 状态机，并返回每个 Lot 的成功/失败原因 | 已落地，`PilotMesServiceTest` 覆盖 |
| 汇总审计 | 批量处置必须额外写 `LOT_BATCH_HOLD` / `LOT_BATCH_RELEASE`，快照包含请求、批次号、总数、成功数、失败数和逐 Lot 明细 | 已落地，单元测试断言汇总快照 |
| 权限边界 | 批量 Hold/Release 必须分别受 `lot:hold` / `lot:release` 控制，操作员不可越权执行批量质量处置 | 已落地，`RolePermissionServiceTest` 覆盖 |
| 失败审计映射 | 批量路径发生业务异常、参数异常或系统异常时必须归类到对应批量动作 | 已落地，`AuditFailureResolverTest` 覆盖 |
| 前端批量处置入口 | Lot 管理页必须提供多选、当前页全选、可 Hold/可放行统计、批量按钮和批量结果回显 | 已落地，前端契约 413 项覆盖 |
| 回归验证 | 后端批量处置定向、前端契约和前端生产构建必须通过 | 已通过：后端定向 86 项、前端契约 413 项、前端构建通过 |

## 2026-06-10 补充验收：WMS 多库位拆批任务

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| 拆批任务创建 | `POST /api/v1/material/location-tasks` 支持 `taskType=SPLIT`，创建阶段只生成任务单和审计，不改变母批库存、子批或库存事务 | 已落地，`MaterialServiceTest` 覆盖 |
| 拆批任务完成 | `POST /api/v1/material/location-tasks/{taskNo}/complete` 完成时扣减母批可用库存和总量，生成子批，更新源/目标库位占用 | 已落地，`MaterialServiceTest` 覆盖 |
| 库存事务 | 拆批完成必须写 `SPLIT_OUT` 和 `SPLIT_IN` 两条库存事务，保留母批、子批和任务快照 | 已落地 |
| 数量边界 | 拆批只能使用母批 `availableQty`，不得拆 `reservedQty` 或 `frozenQty`，且拆批数量必须小于母批可用库存 | 已落地，超出可用库存用例已覆盖 |
| 子批证据 | 子批号可手工输入或自动生成，创建任务写入快照，完成任务复用同一子批号，任务列表返回 `childBatchNo` | 已落地 |
| 权限边界 | 拆批复用 `material:wms` 权限，默认 QE 不可越权创建/完成库位任务，应用 WMS 权限快照后才允许 | 已落地，`RolePermissionServiceTest` 覆盖 |
| 前端入口 | 物料页库位任务操作台必须提供“拆批”类型、母批选择、目标库位、拆出数量、子批号输入和任务表子批证据 | 已落地，前端契约覆盖 |
| 回归验证 | 后端拆批定向、前端契约和前端生产构建必须通过 | 已通过：后端定向 59 项、前端契约 414 项、前端构建通过 |

## 2026-06-10 补充验收：WMS 库位任务复核结果留痕

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| 复核结果字段 | `material_location_task` 必须保存 `review_result` 和 `review_conclusion`，历史已复核任务回填为通过 | 已落地，`V1.49__Add_Material_Location_Task_Review_Result.sql` 覆盖 |
| 通过/驳回语义 | 复核接口必须支持 `APPROVED/REJECTED`，驳回时记录结论和异常原因，但不自动修改已完成库存事务 | 已落地，驳回任务状态保持 `DONE` |
| 审计快照 | 复核动作必须写 `MATERIAL_LOCATION_TASK_REVIEW`，快照包含复核结果、结论、异常原因和字段差异 | 已落地，`MaterialServiceTest` 覆盖 |
| 前端入口 | 物料页最近库位任务表必须提供“通过/驳回”两个复核动作，并展示复核结论 | 已落地，前端契约覆盖 |
| 回归验证 | 后端复核结果定向、前端契约、前端生产构建、生产包扫描和 Flyway 静态验收必须通过 | 已通过：后端定向 98 项、前端契约 419 项、生产包 14 个 JS 产物 clean、Flyway 49 个迁移 |

## 2026-06-10 补充验收：WMS 库位任务复核驳回处置

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| 处置字段 | `material_location_task` 必须保存 `disposition_status/result/conclusion/by/time`，历史驳回复核任务回填为待处置，历史通过任务回填为关闭 | 已落地，`V1.50__Add_Material_Location_Task_Disposition.sql` 覆盖 |
| 处置准入 | 仅 `DONE + reviewResult=REJECTED + dispositionStatus=PENDING` 的库位任务允许处置，未完成、非驳回和已处置任务必须拒绝 | 已落地，`MaterialServiceTest` 覆盖 |
| 让步接收 | `ACCEPT_DEVIATION` 只关闭差异并写审计，不得修改批次库存、库位占用或库存事务 | 已落地，单元测试断言不调用库存事务 |
| 显式调库 | `ADJUST_INVENTORY` 必须显式提供 `countedAvailableQty` 或 `actualQty`，并复用盘点逻辑写 `COUNT` 库存事务 | 已落地，调库测试覆盖可用量、总量、库位占用和库存事务 |
| 升级处置 | `ESCALATE` 将处置状态置为 `ESCALATED`，留给后续异常/MRB 编排，不自动执行生产动作 | 已落地，处置结果归一支持 |
| 审计快照 | 处置动作必须写 `MATERIAL_LOCATION_TASK_DISPOSITION`，快照包含处置结果、结论、处置人、请求和调库批次证据 | 已落地，成功审计和失败审计映射均覆盖 |
| 前端入口 | 物料页最近库位任务表必须展示处置状态和结论，并为待处置驳回任务提供“接收差异”和“调库”入口 | 已落地，前端契约覆盖 |
| 回归验证 | 后端处置定向、前端契约、前端生产构建、生产包扫描和 Flyway 静态验收必须通过 | 已通过：后端定向 102 项、前端契约 420 项、生产包 14 个 JS 产物 clean、Flyway 50 个迁移 |

## 2026-06-11 补充验收：WMS 复核差异待处置队列

| 验收项 | 标准 | 当前状态 |
| --- | --- | --- |
| 服务端待处置筛选 | `GET /api/v1/material/location-tasks?pendingDispositionOnly=true` 必须只返回 `DONE + REJECTED + PENDING/NULL` 的复核驳回待处置任务 | 已落地，Docker 运行态接口返回业务码 200，演示任务命中筛选 |
| 扩展筛选参数 | 库位任务查询必须支持 `reviewResult` 和 `dispositionStatus`，便于后续异常队列、WMS 班组待办和审计筛查复用 | 已落地，控制器、聚合服务和物料服务签名已统一 |
| 处置状态展示字段 | API 行数据必须返回 `dispositionText` 和 `dispositionType`，前端不应重复硬编码复杂状态映射 | 已落地，覆盖待处置、已升级、已调库、已接收和已关闭 |
| 过滤空结果语义 | 带筛选条件的真实查询为空时必须返回空数组，不得用演示 fallback 样例覆盖真实生产语义 | 已落地，`PilotMesService` 已按过滤条件收口 |
| 前端独立待办队列 | 物料页必须提供“复核差异待处置”队列，展示待处置数量、批次数、高优先级数量和任务明细 | 已落地，前端契约 423 项覆盖 |
| 前端处置入口 | 待处置队列中的任务必须可直接执行“接收差异”和“调库”，并复用正式处置接口 | 已落地，复用 `dispositionLocationTask` 和 `dispositionMaterialLocationTask` |
| 视觉一致性 | 队列必须沿用浅色 Codex app 工作台风格，使用低饱和按钮、细边框和紧凑任务行，移动端不溢出 | 已落地，新增队列样式和移动端单列布局 |
| Docker 可看效果 | 当前 Docker 环境必须部署本轮前后端产物，并至少有一条待处置驳回任务可在物料页查看 | 已落地，`MLT-20260611090846806-0001` 已进入 `pendingCount=1` |
| 回归验证 | 后端定向、前端契约、前端生产构建、生产包扫描和空白检查必须通过 | 已通过：后端定向 103 项、前端契约 423 项、生产包 14 个 JS 产物 clean、`diff --check` 无错误 |
