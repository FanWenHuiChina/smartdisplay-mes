# SmartDisplay MES 测试报告

更新时间：2026-06-10

## 结论

当前后端单元/服务级闭环测试、OpenAPI/Swagger 合同回归、Track In 预校验矩阵、Lot 批量 Hold/Release、WMS 多库位拆批任务、WMS 库位任务复核闭环、WMS 库位任务复核通过/驳回结论留痕、WMS 库位任务复核驳回处置、WMS 库位任务 SLA 与优先级、系统审计分页筛选、EAP 影子协议驱动、EAP 消息失败留痕与诊断详情、Flyway 迁移静态校验、Flyway 全新库迁移演练、后端打包、前端生产构建、前端契约验收、前端生产 mock fallback 收口、生产包样例业务标识扫描、Codex app 风格视觉冒烟、真实浏览器 E2E、Docker Compose 容器级启动、HTTP 冒烟、性能冒烟、三轮性能基线、BOM/ECO 跨部门会签 API 冒烟、供应商准入/8D整改、供应商月度评分趋势和供应商准入周期复审任务均已通过相应验证。2026-06-10 已完成 EAP `SHADOW` 模式协议帧校验、消息详情诊断接口、Docker `V1.50` 迁移、SECS/GEM 影子入站接口冒烟、设备页 EAP 失败消息诊断抽屉浏览器 E2E、`pilot-v1` OpenAPI 分组文档生产化、Lot 批量 Hold/Release 汇总审计接线、WMS 多库位拆批任务接线、WMS 库位任务完成后复核接线、复核通过/驳回结论留痕、复核驳回后让步接收/显式调库/升级处置和 WMS 库位任务 SLA 待办排序；当前迁移静态验收已升级到 `V1.50`。此前使用临时 PostgreSQL 容器完成全新数据库迁移演练，后端重新打包后自动迁移到 `V1.38`，并完成 `pg_dump/pg_restore` 恢复校验。

## 测试环境

- 工作目录：`D:\workspace\mes`
- 后端：Spring Boot 3.1.5、JDK 17 编译目标
- 前端：Vue 3、Vite 8
- Docker Compose：`v2.37.1-desktop.1`
- 数据库容器目标：PostgreSQL 16 Alpine

## 验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端全量测试 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` | 通过 | `Tests run: 242, Failures: 0, Errors: 0, Skipped: 0` |
| EAP影子协议与消息诊断回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=EapGatewayServiceTest" test` | 通过 | `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`；覆盖 `SHADOW/EXTERNAL` 健康检查、SECS/GEM 帧校验、OPC UA 数据变化归一化、厂商 HTTP 元数据保留、失败消息留痕和消息详情诊断 |
| V1.31 AI定向回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=AiKnowledgeServiceTest,AiModelConfigServiceTest,AiRecordServiceTest,PilotMesServiceTest,PilotMesFlowIntegrationTest" test` | 通过 | `Tests run: 20, Failures: 0, Errors: 0, Skipped: 0` |
| V1.32 AI留痕查询回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=AiRecordServiceTest,PilotMesServiceTest" test` | 通过 | `Tests run: 16, Failures: 0, Errors: 0, Skipped: 0` |
| V1.33 知识库索引任务回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=AiKbIndexServiceTest,AiKnowledgeServiceTest,RolePermissionServiceTest,PilotMesServiceTest" test` | 通过 | `Tests run: 31, Failures: 0, Errors: 0, Skipped: 0` |
| ERP导入与性能脚本回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=ErpOrderAdapterServiceTest,PilotMesServiceTest,PilotMesFlowIntegrationTest,RolePermissionServiceTest,AuditFailureResolverTest" test` | 通过 | `Tests run: 50, Failures: 0, Errors: 0, Skipped: 0` |
| MRB纪要版本管理回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=QualityServiceTest,RolePermissionServiceTest,AuditFailureResolverTest,PilotMesFlowIntegrationTest" test` | 通过 | `Tests run: 45, Failures: 0, Errors: 0, Skipped: 0` |
| MRB会签SLA升级回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=QualityServiceTest,RolePermissionServiceTest,AuditFailureResolverTest" test` | 通过 | `Tests run: 47, Failures: 0, Errors: 0, Skipped: 0` |
| 物料/BOM/供应商趋势/复审回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=MaterialServiceTest" test` | 通过 | `Tests run: 43, Failures: 0, Errors: 0, Skipped: 0`；覆盖供应商到期复审生成和 WMS 库位任务创建/领取/完成/取消审计快照 |
| WMS库位任务审计可见性回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=MaterialServiceTest,PilotMesServiceTest" test` | 通过 | `Tests run: 71, Failures: 0, Errors: 0, Skipped: 0`；覆盖库位任务结构化快照落库和 `/system/audit-logs` 返回 `requestSnapshot` |
| WMS多库位拆批任务回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=MaterialServiceTest,RolePermissionServiceTest" test` | 通过 | `Tests run: 59, Failures: 0, Errors: 0, Skipped: 0`；覆盖拆批任务创建不改库存、完成任务生成子批并写 `SPLIT_OUT/SPLIT_IN`、拆批数量不得超过可用库存、`material:wms` 权限边界 |
| WMS库位任务复核回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=MaterialServiceTest,RolePermissionServiceTest,AuditFailureResolverTest" test` | 通过 | `Tests run: 96, Failures: 0, Errors: 0, Skipped: 0`；覆盖 `DONE` 任务复核、非完成/重复复核拒绝、复核审计快照、失败审计映射和 `material:wms` 权限边界 |
| WMS库位任务复核结果回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=MaterialServiceTest,RolePermissionServiceTest,AuditFailureResolverTest" test` | 通过 | `Tests run: 98, Failures: 0, Errors: 0, Skipped: 0`；覆盖复核通过、复核驳回、驳回后状态仍为 `DONE`、结论/异常原因留痕、审计快照和 `material:wms` 权限边界 |
| WMS库位任务复核驳回处置回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=MaterialServiceTest,RolePermissionServiceTest,AuditFailureResolverTest" test` | 通过 | `Tests run: 102, Failures: 0, Errors: 0, Skipped: 0`；覆盖驳回复核待处置、让步接收不改库存、显式调库写 `COUNT` 库存事务、重复/非驳回/缺少实盘数量拒绝、失败审计映射和 `material:wms` 权限边界 |
| WMS库位任务SLA回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=MaterialServiceTest,RolePermissionServiceTest,AuditFailureResolverTest" test` | 通过 | `Tests run: 97, Failures: 0, Errors: 0, Skipped: 0`；覆盖库位任务默认/显式优先级、SLA到期时间、逾期状态输出、任务复核和 `material:wms` 权限边界 |
| 系统审计分页筛选回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=AuditLogServiceTest,PilotMesServiceTest" test` | 通过 | `Tests run: 34, Failures: 0, Errors: 0, Skipped: 0`；覆盖审计分页、业务对象、动作分组、结果、来源、操作人、日期范围筛选，请求上下文字段映射和非法日期拒绝 |
| 工单释放预校验回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" -Dtest=PilotMesServiceTest test` | 通过 | `Tests run: 27, Failures: 0, Errors: 0, Skipped: 0`；覆盖释放前 Route/BOM/设备/Recipe/Lot 拆分校验和缺少生效 BOM 阻断 |
| Track In预校验回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" -Dtest=TrackInServiceTest test` | 通过 | `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`；覆盖无副作用校验矩阵、设备能力失败阻断、正式 Track In 写接口复用同一套阻断逻辑 |
| 核心执行审计差异快照回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=PilotMesServiceTest" test` | 通过 | `Tests run: 26, Failures: 0, Errors: 0, Skipped: 0`；验证审计快照包含 `before/after/changedFields/request`，并覆盖 Rework Route/起始工序校验、Scrap 二次确认和 Recipe 筛选 |
| RBAC权限口径回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=RolePermissionServiceTest" test` | 通过 | `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`；验证 PE/EE 质量菜单口径、操作员 Track In/Out 写权限和跨域写操作拒绝 |
| Lot批量处置回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=PilotMesServiceTest,RolePermissionServiceTest,AuditFailureResolverTest" test` | 通过 | `Tests run: 86, Failures: 0, Errors: 0, Skipped: 0`；覆盖批量 Hold/Release 逐 Lot 结果、汇总审计、RBAC 权限和失败审计映射 |
| 执行闭环与审计上下文回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=PilotMesFlowIntegrationTest,AuditLogServiceTest" test` | 通过 | `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0` |
| OpenAPI/Swagger合同回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=OpenApiConfigTest" test` | 通过 | `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`；覆盖 `pilot-v1` 分组、`/api` 服务器路径、JWT Bearer、统一 `Result/PageResult` schema、标准 400/401/403/500 响应和登录接口免 Bearer |
| 后端打包 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-DskipTests" "-Dspring-boot.repackage.skip=true" package` | 通过 | 生成普通 jar，Spring Boot repackage 阶段通过 |
| 前端契约验收 | `npm.cmd run verify:frontend-contract` | 通过 | 静态覆盖关键路由、`/api/v1` API 封装、请求拦截、RBAC 菜单/按钮权限、页面接线、Lot/Recipe 二级工作台、Lot 批量 Hold/Release 处置入口、WMS 多库位拆批任务入口、WMS 库位任务复核入口、WMS 库位任务复核结果、WMS 库位任务复核驳回处置、WMS 库位任务 SLA、V1.38 库位任务、V1.41 供应商准入/复审/8D、供应商月度评分趋势、供应商到期复审生成接口、工单页 ERP Adapter 审计回执、工单释放预校验接线、Track In 预校验接线、EAP 消息详情诊断接线、系统审计快照查看入口、审计分页筛选与上下文导出、执行页禁止静态校验数和生产环境 mock fallback 禁用约束，共 420 项检查 |
| 前端生产构建 | `npm.cmd run build` | 通过 | 仅存在第三方 pure annotation 和 chunk size 警告 |
| 前端生产包样例标识扫描 | `npm.cmd run verify:production-bundle` | 通过 | 扫描 `dist/assets/*.js` 共 14 个产物，未发现典型 mock/fallback 样例 Lot、工单、设备、Recipe、SOP、COA 编号 |
| 前端视觉冒烟 | `smartdisplay-mes-ui/visual-check/visual-check-summary.json` | 通过 | `/login`、`/overview`、`/material`、`/equipment`、`/system` 无横向溢出、按钮文字溢出、文本裁切和控制台错误；视觉基线为浅色 Codex app 风格 |
| 前端真实浏览器 E2E | `npm.cmd run e2e:browser` | 通过 | 20 步通过；覆盖登录、导航权限、工单页 UI 调用 ERP Adapter 下发/审计/释放并生成 Lot、Lot 管理二级工作台 Hold/Release/Rework/Scrap、Recipe 管理二级工作台与参数详情、UI Track In/Out 及 Track In 预校验矩阵、QMS Adapter 上报、WMS Adapter 齐套/入库事务、物料 V1.38 库位任务操作台和状态流、供应商到期准入复审生成审计、设备页 EAP 参数上报、网关健康检查、EAP 失败消息留存与诊断抽屉、质量 MRB/缺陷证据、主流程 Lot 追溯查询、AI 报告生成留痕、系统审计入口、操作员菜单收敛与越权工单释放 403；Console/Network 错误数为 0，最新报告见 `docs/SmartDisplay-MES-browser-e2e-20260610-182527.md` |
| CI 浏览器 E2E 门禁 | `.github/workflows/ci.yml` 的 `Docker browser E2E` job | 已接入 | 在 `ubuntu-latest` 中启动 Docker Compose 三服务，等待前端和后端代理就绪后运行 `node scripts/run-browser-e2e.mjs`，并上传浏览器 E2E 报告 |
| Flyway 静态验收 | `powershell -ExecutionPolicy Bypass -File tools\verify-flyway-migrations.ps1` | 通过 | 识别 `V1.1-V1.50` 共 50 个迁移文件 |
| Flyway 全新库迁移演练 | `powershell -ExecutionPolicy Bypass -File tools\run-flyway-rehearsal.ps1 -StartupTimeoutSec 180` | 通过 | 临时 PostgreSQL 容器全新库迁移到 `V1.38`，应用启动成功；52 张 public 表、7 个种子用户、16 条 Route Step；`pg_dump/pg_restore` 恢复库最新版本仍为 `V1.38`，报告见 `docs/SmartDisplay-MES-flyway-rehearsal-20260608-052419.md` |
| 性能冒烟脚本语法 | PowerShell Parser 解析 `tools\run-pilot-performance-smoke.ps1` | 通过 | 脚本支持阈值参数、Markdown/JSON 报告输出和失败退出码 |
| 性能冒烟实测 | `powershell -ExecutionPolicy Bypass -File tools\run-pilot-performance-smoke.ps1 -BaseUrl http://127.0.0.1:8888/api -Username planner -Password 123456 -ImportCount 1000 -Samples 20` | 通过 | 经前端反代导入 1000 条模拟工单成功；订单列表 P95 15.67ms、Lot 列表 P95 13.72ms、良率看板 P95 17.28ms、Lot 追溯 P95 60.08ms；报告见 `docs/SmartDisplay-MES-performance-smoke-20260608-030053.md` |
| 多轮性能基线脚本语法 | PowerShell Parser 解析 `tools\run-pilot-performance-baseline.ps1` | 通过 | 脚本复用单轮冒烟脚本，汇总多轮 P95、标准差、漂移比例、稳定性告警和 Markdown/JSON 报告 |
| 三轮稳定性能基线 | `powershell -ExecutionPolicy Bypass -File tools\run-pilot-performance-baseline.ps1` | 通过 | 3 轮，每轮导入 1000 条模拟工单、每项 20 次采样；订单列表最大 P95 8.59ms、Lot 列表最大 P95 7.32ms、良率看板最大 P95 13.25ms、Lot 追溯最大 P95 20.01ms，全部为 `STABLE`；报告见 `docs/SmartDisplay-MES-performance-baseline-20260608-061856.md` |
| CI 手动性能基线门禁 | `.github/workflows/ci.yml` 的 `Manual Docker performance baseline` job | 已接入 | 通过 `workflow_dispatch` 手动触发，启动 Docker Compose 三服务后运行 `tools/run-pilot-performance-baseline.ps1`，支持配置 rounds、samples、import count，并上传 Markdown/JSON 报告 |
| Docker Compose 配置 | `docker compose config` | 通过 | 根目录 Compose 入口可解析 PostgreSQL、后端、前端服务和网络/卷配置；Docker 客户端有用户级 `config.json` 权限警告，不影响配置解析 |
| Docker Compose 子目录配置 | `docker compose -f smartdisplay-mes-api\docker-compose.yml config` | 通过 | 兼容旧入口，三服务配置可解析 |
| Docker Compose 状态 | `docker compose -f smartdisplay-mes-api\docker-compose.yml ps` | 通过 | `smartdisplay-mes-postgres` healthy，`smartdisplay-mes-api` 监听 `8080`，`smartdisplay-mes-ui` 监听 `8888` |
| Docker Compose 启动 | `docker compose -f smartdisplay-mes-api\docker-compose.yml up -d --build` | 通过 | 三服务构建并启动成功，后端启动时执行 Flyway 自动迁移 |
| Docker Flyway 迁移 | `docker compose -f smartdisplay-mes-api\docker-compose.yml exec -T postgres psql -U postgres -d smartdisplay_mes -c "select version, description, success from flyway_schema_history order by installed_rank desc limit 5;"` | 通过 | 最新迁移为 `1.50 Add Material Location Task Disposition`，`success=t`；后端日志显示从 `1.47` 连续迁移到 `1.48/1.49/1.50` |
| Docker OpenAPI运行态冒烟 | `Invoke-RestMethod http://127.0.0.1:8080/api/v3/api-docs/pilot-v1` | 通过 | 分组文档返回 `SmartDisplay MES API`、server `/api`、JWT Bearer、`Result/PageResult` schema、标准错误响应、`/v1/lots` 路径；登录接口 `security=0`，Lot 列表接口 `security=1` |
| EAP影子协议Docker冒烟 | `Invoke-RestMethod` 调用网关驱动、网关健康检查和 `/adapters/eap/messages` | 通过 | 前端反代返回 `SECS_GEM driverMode=SHADOW`、`protocolFrameValidation=true`；`GW-SECSGEM-SHADOW` 健康检查 `WARN`；SECS/GEM `S6F11` 入站归一为 `STATUS` 并 `PROCESSED` |
| EAP失败消息诊断Docker冒烟 | `Invoke-RestMethod` 经前端反代提交缺少 SECS/GEM 帧标识的 `/adapters/eap/messages` 并查询消息详情/审计 | 通过 | `EGM-DIAG-20260610181059` 返回 `accepted=false`、消息状态 `FAILED`、详情诊断 `PROTOCOL_FRAME`、快照存在，审计 `EAP_GATEWAY_MESSAGE_FAILED/FAIL` 命中 1 条 |
| HTTP 冒烟 | `Invoke-RestMethod` 调用登录、Dashboard、库位任务和分页审计接口 | 通过 | 前端反代登录、`/dashboard/overview`、`/material/location-tasks` 均返回业务码 200；`/system/audit-logs?current=1&size=5&action=WMS&result=SUCCESS` 返回 `total=360`、本页 5 条且包含请求上下文字段 |
| V1.38 库位任务状态流 | 经 `http://127.0.0.1:8888/api` 创建、领取、完成、取消库位任务 | 通过 | 盘点任务验证 `CREATED -> ASSIGNED -> DONE` 和 `CREATED -> CANCELLED`；完成任务 `MLT-20260608040941126-0001`，取消任务 `MLT-20260608040941417-0003` |
| V1.39 BOM/ECO 会签状态流 | 经 `http://127.0.0.1:8080/api` 提交 BOM 变更、查询 ECO 会签任务、逐个会签通过、发布 BOM | 通过 | 变更单 `BCR-20260608064003841-0001` 生成 3 个任务并全部 `APPROVED`，发布后数据库为 `PUBLISHED\|APPROVED\|PE,QE,PLANNER`、任务统计 `3\|3` |

## 已覆盖测试范围

- RBAC 角色策略、菜单/按钮权限和数据范围 SQL。
- 权限变更申请、审批、运行期权限快照、启动恢复、手动重载和审计。
- 审计请求上下文：请求方法、URI、客户端IP、User-Agent 自动落库；覆盖代理IP解析、字段截断和 ThreadLocal 清理。
- 系统审计查询与导出：`/api/v1/system/audit-logs` 支持分页，以及业务对象、动作分组、结果、来源、操作人和日期范围筛选；系统页使用接口分页结果并导出请求方法、URI、客户端IP、User-Agent、业务类型和快照字段。
- 关键写接口失败审计：覆盖失败路径动作解析、`result=FAIL` 写入、异常处理器触发和非关键读请求忽略。
- 核心执行审计差异快照：工单创建/释放、Track In/Out、Hold/Release、Rework/Scrap 写入 `before/after/changedFields/request` 结构化快照；系统审计页可查看接口返回的 `requestSnapshot`。
- Route 生效工序、防跳站和 Route 驱动 Lot 推进。
- Recipe 唯一性、创建、发布、停用和生效 Recipe 查询。
- Track In 状态、Route、设备、Recipe、Hold、班次和物料齐套校验；`GET /api/v1/lots/{lotNo}/track-in-checks` 返回无副作用校验矩阵，正式 Track In 写接口复用同一套阻断逻辑。
- Track Out 过站记录、质量判定、物料消耗。
- WMS 入库、冻结、解冻、退料、盘点、库存事务履历、批次行锁、库位状态/容量/类别/单位校验、库位占用更新、库位上架/整批移库/拆批/盘点任务、创建/领取/完成/取消审计差异快照和物料写接口权限。
- 来料 IQC 判定、COA/检验附件元数据留痕、批次质量状态联动、IQC 写接口权限和失败审计映射。
- 供应商绩效评分与趋势：基于物料批次、来料 IQC 和8D整改记录聚合 PASS/HOLD/NG、通过率、风险批次、评分、月度趋势和风险等级，覆盖高风险排序、超期8D扣分且不额外读取 COA 附件。
- 供应商准入复审任务：覆盖复审任务创建、重复 OPEN 任务拦截、复审通过/驳回决策、供应商状态回写、前端物料页复审工作区和失败审计映射。
- 设备事件创建/关闭、EAP 参数越限自动事件、Recipe下发/回读不一致自动事件、设备状态联动、状态历史落库、EAP节拍采样、PM任务完成、OEE停机原因聚合、OEE性能率优先按标准/实际节拍采样计算、设备写接口权限和失败审计映射。
- EAP 统一适配器与影子协议入口：标准化消息入口、状态/节拍/参数/Recipe下发分发、`equipment:eap-ingest` 权限、失败消息不回滚留痕和 `EAP_GATEWAY_MESSAGE_FAILED` 审计映射。
- 标准节拍主数据：产品+工序+设备+Recipe+版本发布、旧ACTIVE版本失效、节拍样本自动匹配标准秒、`EQUIPMENT_STANDARD_CYCLE_PUBLISH` 审计和失败审计映射。
- EAP 网关连接：网关注册/更新、心跳、健康检查、入站消息先落履历、成功 `PROCESSED`、失败 `FAILED` 并降级网关、消息详情返回原始快照/归一化快照/适配器响应/诊断建议、`equipment:eap-gateway` 权限和网关失败审计映射。
- EAP 网关健康检查：覆盖模拟 HTTP `PASS`、影子协议 `WARN`、外部真实链路未配置 `FAIL`、检查履历落库、网关状态联动和 `EQUIPMENT_GATEWAY_HEALTH_CHECK` 审计。
- EAP 协议驱动：驱动能力列表、驱动配置快照、SECS/GEM `stream/function` 帧校验与消息归一化、OPC UA 数据变化归一化、厂商 HTTP 请求元数据保留、归一化 payload 快照和驱动编码留痕。
- BOM 变更提交、审批驳回、发布生效、旧版本失效、替代料自动选择、替代料验证报告附件元数据、ECO 包快照、风险等级、跨部门会签任务、会签通过前禁止发布、会签驳回阻断发布和 BOM 写接口失败审计。
- 质量 NG/参数超限自动异常、缺陷记录和自动 Hold。
- Hold/Release、批量 Hold/Release、Rework/Scrap 基础状态转换。
- MRB 复判/关闭履历、会议号、参与人、审批状态、附件元数据、会议纪要版本、会签待办、审批通过/驳回、按风险/角色/处置动作计算 SLA、逾期升级和关闭前会签校验。
- Lot 数据范围、质量/异常/物料/载具列表数据范围。
- 基地、产线、班次主数据查询和前端主数据页接口化。
- ERP模拟工单导入：覆盖 1000 条导入不失败、重复工单跳过、超过 1000 条拒绝、`order:create` 权限和失败审计映射。
- 工单释放 -> Track In -> NG 自动 Hold -> Release -> 复测 OK -> 推进下一站 -> 追溯链路的服务级闭环。
- AI SOP 知识库基础检索、知识库导入切片、知识库索引任务履历、关键词索引重建、pgvector-ready 待联调标记、模型运行配置、RAG证据等级、依据不足判断、AI 报告元数据留痕和留痕查询。
- 前端契约验收：路由、登录守卫、请求拦截、核心 `/api/v1` 封装、RBAC 菜单/按钮权限、工单/执行/主数据/质量/设备/物料/追溯/AI/系统页面的 API 与权限接线，以及 mock fallback 编译期开关和生产环境禁用约束。
- 前端生产包扫描：默认生产构建不携带典型 mock/fallback 样例业务字符串；离线演示需显式设置 `VITE_ENABLE_MOCK_FALLBACK=true` 后重新构建。

## Docker 容器级复验详情

2026-06-08 本地代理恢复后，已重新执行 `docker compose -f smartdisplay-mes-api\docker-compose.yml up -d --build` 并完成容器级复验：

```text
smartdisplay-mes-postgres   Up (healthy)   0.0.0.0:5433->5432/tcp
smartdisplay-mes-api        Up             0.0.0.0:8080->8080/tcp
smartdisplay-mes-ui         Up             0.0.0.0:8888->80/tcp
```

数据库迁移复验结果：

```text
1.39 | Add Bom Eco Approval Tasks          | t
1.38 | Add Material Location Task Workflow | t
1.37 | Add Material Location Tasks         | t
1.36 | Add Material Location Strategy      | t
```

HTTP 冒烟覆盖：

- `POST http://127.0.0.1:8080/api/v1/auth/login` 返回业务码 `200`。
- `GET http://127.0.0.1:8888/` 返回 HTTP `200`。
- `GET http://127.0.0.1:8080/api/swagger-ui.html` 返回 HTTP `200`。
- `POST http://127.0.0.1:8888/api/v1/auth/login` 经前端 Nginx 反代返回业务码 `200`。
- 带 Token 调用 `GET http://127.0.0.1:8888/api/v1/dashboard/overview` 返回业务码 `200`。
- 带 Token 调用 `GET http://127.0.0.1:8888/api/v1/material/location-tasks` 返回业务码 `200`，当前返回 2 条库位任务。
- 带 Token 创建盘点任务后，`assign` 返回 `ASSIGNED`，`complete` 返回 `DONE`；另一个盘点任务 `cancel` 返回 `CANCELLED`。

性能冒烟报告已生成：

- Markdown：`docs/SmartDisplay-MES-performance-smoke-20260608-030053.md`
- JSON：`docs/SmartDisplay-MES-performance-smoke-20260608-030053.json`

多轮性能基线报告已生成：

- Markdown：`docs/SmartDisplay-MES-performance-baseline-20260608-061856.md`
- JSON：`docs/SmartDisplay-MES-performance-baseline-20260608-061856.json`
- 轮次明细：`docs/performance-baseline-rounds/SmartDisplay-MES-performance-smoke-20260608-061856-round01.md`、`round02.md`、`round03.md`

## BOM/ECO 跨部门会签实测

2026-06-08 已在 Docker Compose 后端与 PostgreSQL 上完成 V1.39 BOM/ECO 会签 API 冒烟：

- 数据库 Flyway 最新版本：`1.39 Add Bom Eco Approval Tasks`。
- 使用 `admin` 登录，经 `/api/v1/boms/change-requests` 提交 ECO 变更单 `BCR-20260608064003841-0001`。
- 变更单生成 3 个会签任务，角色为 `PE,QE,PLANNER`。
- 经 `/api/v1/boms/eco-approvals/{taskNo}/decision` 逐个会签通过。
- 经 `/api/v1/boms/change-requests/{changeNo}/publish` 发布目标 BOM。
- PostgreSQL 复核结果：`md_bom_change_request` 为 `PUBLISHED|APPROVED|PE,QE,PLANNER`；`md_bom_eco_approval_task` 为 `3|3`。

## 真实数据库 API 闭环复验

2026-06-08 已新增并执行 `tools\run-real-db-api-flow.ps1`，在当前 Docker Compose PostgreSQL 与后端 API 上完成真实数据库集成验证。

执行命令：

```powershell
powershell -ExecutionPolicy Bypass -File tools\run-real-db-api-flow.ps1
```

最新通过报告：

- Markdown：`docs/SmartDisplay-MES-real-db-api-flow-20260608-060901.md`
- JSON：`docs/SmartDisplay-MES-real-db-api-flow-20260608-060901.json`

本轮真实库闭环证据：

- 登录 `admin` 并确认 PostgreSQL 容器 `smartdisplay-mes-postgres` 可连接。
- 该轮真实数据库闭环校验时 Flyway 最新版本为 `V1.38`；后续 Docker 容器数据库已通过 V1.39 迁移和 BOM/ECO 会签 API 冒烟复验。
- 创建真实工单 `MOINT20260608060901707`，并直接查询 `prod_order` 确认落库。
- 释放工单生成 `LOTINT20260608060901707-001`，并直接查询 `prod_lot` 确认 `READY|CLEAN`。
- 对 `CLEAN/CLEANER_01` 执行 Track In/Out，确认 `prod_lot_step_record` 记录从打开到 `OK` 出站，并推进到 `COATING`。
- 对 `COATING/COATER_01` 执行关键参数超限出站，确认返回 `NG`，Lot 自动进入 `HOLD`。
- 通过 API 与 PostgreSQL 双重校验 `quality_inspection`、`exception_event`、`lot_hold_record` 证据：`inspection|exception|openHold=4|1|1`。
- 执行 Release，确认 Lot 恢复 `READY`，并查询 `lot_hold_record` 中存在 `RELEASED` 记录。
- 追溯接口返回完整链路：`steps=2, quality=4, holds=1`。
- 看板接口读取当前数据库：`overviewMetrics=4, yieldTrend=7`。
- 生成 AI 良率报告 `AIR-YIELD-1780870145728`，并直接查询 `ai_report_record` 确认落库。
- 审计接口与 PostgreSQL 双重校验关键动作，`sys_audit_log` 中匹配 `TRACK_IN`、`TRACK_OUT`、`LOT_RELEASE`、`QUALITY_INSPECTION` 等记录共 7 条。

## 仍需补齐

- 性能验收已完成一轮冒烟实测和三轮稳定基线；后续仍建议在更接近试点数据规模、固定硬件和 CI 环境下持续采集趋势。
- 真实 pgvector 向量召回、外部模型联调和引用召回率评估。

## 2026-06-09 RBAC权限口径与操作员越权复验

本轮修正后端 PE/EE 默认菜单与前端权限矩阵不一致的问题，并把操作员越权用例纳入真实浏览器 E2E。

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| RBAC 权限口径回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=RolePermissionServiceTest" test` | 通过，12 项测试 |
| 前端契约验证 | `npm.cmd run verify:frontend-contract` | 通过，383 项检查 |
| Docker 重建 | `docker compose -f smartdisplay-mes-api\docker-compose.yml up -d --build` | 通过，后端、前端、PostgreSQL 均启动 |
| Docker 权限探活 | 经 `http://127.0.0.1:8888/api` 登录 EE/操作员并调用越权释放 | 通过，EE 菜单包含 `quality`；操作员为 `SELF_SHIFT` 且越权释放返回 `403` |
| 浏览器 E2E | `npm.cmd run e2e:browser` | 通过，19 步，最新报告 `SmartDisplay-MES-browser-e2e-20260609-142348.md` |

覆盖点：操作员登录后仅拥有 `dashboard/execution/trace` 菜单和 `lot:track-in/lot:track-out` 按钮权限；直接访问 `/order` 会回到首个可访问页面；直接调用 `POST /api/v1/orders/{orderNo}/release` 返回业务码 `403`。

## 2026-06-09 设备页EAP运行级复验

本轮将设备页纳入真实浏览器 E2E 页面级写操作覆盖：从设备工作台提交唯一 EAP 参数编码，并触发 EAP 网关健康检查。

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 前端契约验证 | `npm.cmd run verify:frontend-contract` | 通过，383 项检查 |
| 浏览器 E2E | `npm.cmd run e2e:browser` | 通过，19 步，最新报告 `SmartDisplay-MES-browser-e2e-20260609-142348.md` |

覆盖点：设备页必须显示 `EAP 参数上报`、`EAP 网关连接`、`EAP 网关健康检查履历`；页面提交的 `EAP_E2E_*` 参数样本可从 `/api/v1/equipment/parameters` 查询到 `OK` 结果；网关健康检查会生成 `MANUAL` 检查履历。

## 2026-06-09 供应商到期复审生成复验

本轮新增供应商准入复审到期生成能力：后端扫描 `nextAuditDue` 到期窗口内供应商，跳过已有 OPEN 周期复审任务的供应商，创建周期复审任务并写批处理审计；前端物料页提供“生成到期复审”按钮。

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 后端定向测试 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=MaterialServiceTest,AuditFailureResolverTest" test` | 通过，76 项 |
| 前端契约验证 | `npm.cmd run verify:frontend-contract` | 通过，383 项检查 |
| 前端生产构建 | `npm.cmd run build` | 通过，仅有既有第三方 warning |
| 生产包样例扫描 | `npm.cmd run verify:production-bundle` | 通过，14 个 JS 产物 |
| Docker 重建 | `docker compose -f smartdisplay-mes-api\docker-compose.yml up -d --build` | 通过，前后端容器已重建 |
| 浏览器 E2E | `npm.cmd run e2e:browser` | 通过，19 步，最新报告 `SmartDisplay-MES-browser-e2e-20260609-142348.md` |

覆盖点：`POST /api/v1/material/suppliers/qualification-reviews/generate-due` 受供应商管理权限控制；失败审计映射为 `SUPPLIER_QUALIFICATION_REVIEW_GENERATE`；浏览器 E2E 从物料页点击“生成到期复审”，并通过系统审计查询确认批处理审计已写入。

## 2026-06-09 工单页 ERP Adapter UI 闭环复验

本轮将计划与工单页的 ERP 下发入口从“有 API 封装”推进到页面级验收：页面展示 Adapter 批次、创建/跳过数量、样例工单和 `ERP_ORDER_IMPORT` 审计动作，浏览器 E2E 从页面点击“下发 ERP 工单”，校验审计落库后再释放该工单生成 Lot。

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 前端契约验证 | `npm.cmd run verify:frontend-contract` | 通过，383 项检查 |
| 前端生产构建 | `npm.cmd run build` | 通过，仅有既有第三方 warning |
| 生产包样例扫描 | `npm.cmd run verify:production-bundle` | 通过，14 个 JS 产物 |
| Docker 重建 | `docker compose -f smartdisplay-mes-api\docker-compose.yml up -d --build` | 通过，前端容器已使用本轮工单页代码 |
| 浏览器 E2E | `npm.cmd run e2e:browser` | 通过，19 步，最新报告 `SmartDisplay-MES-browser-e2e-20260609-142348.md` |

## 2026-06-08 V1.40 供应商准入与8D整改复验

本轮新增供应商准入主数据、8D整改单、IQC NG/HOLD 自动开8D、供应商准入降级、前端物料页供应商工作区和 Codex app 风格收敛后，已完成以下复验：

| 验证项 | 命令/方式 | 结果 |
| --- | --- | --- |
| 后端定向测试 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=MaterialServiceTest,RolePermissionServiceTest,AuditFailureResolverTest" test` | 73 通过，0 失败 |
| 后端全量测试 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` | 183 通过，0 失败 |
| Flyway静态验收 | `powershell -ExecutionPolicy Bypass -File tools\verify-flyway-migrations.ps1` | V1.1-V1.40 共 40 个迁移文件通过 |
| 前端契约 | `npm.cmd run verify:frontend-contract` | 294 项检查通过 |
| 前端生产构建 | `npm.cmd run build` | 通过，仅有既有第三方 pure annotation 和 chunk size warning |
| 生产包样例扫描 | `npm.cmd run verify:production-bundle` | 14 个 JS 产物通过 |
| 浏览器E2E | `npm.cmd run e2e:browser` | 10 步通过，报告 `SmartDisplay-MES-browser-e2e-20260608-072928.md` |
| 视觉冒烟 | Chrome/CDP 截图 `material-codex-style-desktop.png`、`material-codex-style-suppliers.png` | 物料页无页面级横向溢出、无按钮文字溢出，供应商区已渲染 |

## 2026-06-08 供应商月度评分趋势复验

| 验收项 | 命令 | 结果 |
| --- | --- | --- |
| 后端定向测试 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=MaterialServiceTest" test` | 36 通过，0 失败 |
| 后端全量测试 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` | 184 通过，0 失败 |
| 前端契约 | `npm.cmd run verify:frontend-contract` | 296 项检查通过 |
| 前端构建 | `npm.cmd run build` | 通过，仅第三方 pure annotation 和 chunk size warning |
| 生产包扫描 | `npm.cmd run verify:production-bundle` | 14 个 JS 产物通过 |

构建警告说明：`npm.cmd run build` 中 Rolldown 对 `@vueuse/core` 的 `/* #__PURE__ */` 注释位置提示和大 chunk 提示仍为第三方/既有打包警告，本轮未引入新的构建失败。

## 2026-06-08 V1.41 供应商准入周期复审任务复验

本轮新增 `supplier_qualification_review_task`、供应商复审任务查询/创建/决策接口、前端物料页复审工作区和失败审计映射，已完成以下复验：

| 验收项 | 命令 | 结果 |
| --- | --- | --- |
| 后端定向测试 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=MaterialServiceTest" test` | 38 通过，0 失败 |
| 后端全量测试 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` | 191 通过，0 失败 |
| 前端契约 | `npm.cmd run verify:frontend-contract` | 302 项检查通过 |
| 前端构建 | `npm.cmd run build` | 通过，仅第三方 pure annotation 和 chunk size warning |
| 生产包扫描 | `npm.cmd run verify:production-bundle` | 14 个 JS 产物通过 |
| Flyway静态验收 | `powershell -ExecutionPolicy Bypass -File tools\verify-flyway-migrations.ps1` | V1.1-V1.41 共 41 个迁移文件通过 |
# 2026-06-08 Lot 页 Rework/Scrap 前端补齐复验

本轮补齐 Lot 管理页 Rework/Scrap 操作入口，并将旧 Lot 页 API 统一到 `/api/v1/lots` 试点接口；Track In 弹窗的工序与设备选项同步改为读取 `/api/v1/master/**`。

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 前端契约验证 | `npm.cmd run verify:frontend-contract` | 通过，302 项检查 |
| 前端生产构建 | `npm.cmd run build` | 通过，仅有既有第三方 pure annotation 与 chunk size warning |
| 前端生产包扫描 | `npm.cmd run verify:production-bundle` | 通过，14 个 JS 产物未发现典型 mock/fallback 样例业务标识 |

# 2026-06-08 质量页 MRB 报废入口复验

本轮补齐质量管理页 MRB 待处置卡片的 `SCRAP` 复判入口，并将复判与关闭按钮拆分为 `quality:mrb-review`、`quality:exception-close` 两类权限。

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 前端契约验证 | `npm.cmd run verify:frontend-contract` | 通过，304 项检查 |
| 前端生产构建 | `npm.cmd run build` | 通过，仅有既有第三方 pure annotation 与 chunk size warning |
| 前端生产包扫描 | `npm.cmd run verify:production-bundle` | 通过，14 个 JS 产物未发现典型 mock/fallback 样例业务标识 |

# 2026-06-08 Rework Lot 重新进站复验

本轮修正返工闭环：Track In 状态校验允许 `READY/REWORK`，前端 Lot 页和生产执行台同步允许 `REWORK` Lot 重新进站。

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 后端 Track In 定向测试 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=TrackInServiceTest" test` | 通过，10 项测试 |
| 后端执行闭环回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=TrackInServiceTest,PilotMesFlowIntegrationTest" test` | 通过，11 项测试 |
| 前端契约验证 | `npm.cmd run verify:frontend-contract` | 通过，306 项检查 |
| 前端生产构建 | `npm.cmd run build` | 通过，仅有既有第三方 pure annotation 与 chunk size warning |
| 前端生产包扫描 | `npm.cmd run verify:production-bundle` | 通过，14 个 JS 产物未发现典型 mock/fallback 样例业务标识 |

# 2026-06-08 Rework/Scrap 释放 Hold 记录复验

本轮修正 HOLD Lot 执行 Rework/Scrap 后原 Hold 记录仍打开的问题，确保处置后 Lot 状态、`holdFlag`、`lot_hold_record` 与追溯状态一致。

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| Rework/Scrap 服务层回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=PilotMesServiceTest,TrackInServiceTest,PilotMesFlowIntegrationTest" test` | 通过，27 项测试 |
| HOLD Lot Rework | `PilotMesServiceTest.reworkShouldMoveLotToReworkStepAndWriteAudit` | 验证最终状态 `REWORK`、`holdFlag=0`、HoldRecord `RELEASED`、处置结论包含返工 Route/Step |
| HOLD Lot Scrap | `PilotMesServiceTest.scrapShouldMoveLotToScrapAndWriteAudit` | 验证最终状态 `SCRAP`、`holdFlag=0`、HoldRecord `RELEASED`、处置结论等于报废原因 |
| 返工进站校验链 | `TrackInServiceTest` | 保持通过，确认 `REWORK` Lot 进站仍执行原完整校验链 |

# 2026-06-08 AI 知识库索引失败审计复验

本轮补齐 `POST /api/v1/ai/kb/index-jobs` 的失败审计映射，保证知识库索引任务在业务异常或参数异常时也能形成 `AI_KB_INDEX / SOP_KB` 失败审计。

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 审计失败映射回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=AuditFailureResolverTest,AuditFailureServiceTest,AuditLogServiceTest,GlobalExceptionHandlerTest" test` | 通过，36 项测试 |
| 后端全量回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` | 通过，194 项测试 |
| AI 索引失败解析 | `AuditFailureResolverTest.resolveShouldMapAiKnowledgeIndexFailure` | `POST /api/v1/ai/kb/index-jobs` 解析为 `AI_KB_INDEX / SOP_KB` |
| AI 索引失败写入 | `AuditFailureServiceTest.recordShouldWriteFailureAuditForAiKnowledgeIndexJob` | 失败消息会进入 `recordFailure` 快照，便于审计追溯 |

# 2026-06-08 系统页权限快照重载复验

本轮将系统管理页顶部占位“新建角色”入口替换为真实“重载权限”操作，接入 `POST /api/v1/system/permissions/reload`，并纳入按钮权限与契约检查。

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 前端契约验证 | `npm.cmd run verify:frontend-contract` | 通过，308 项检查 |
| 前端生产构建 | `npm.cmd run build` | 通过，仅有既有第三方 pure annotation 与 chunk size warning |
| 前端生产包扫描 | `npm.cmd run verify:production-bundle` | 通过，14 个 JS 产物未发现典型 mock/fallback 样例业务标识 |

# 2026-06-08 权限变更差异对比复验

本轮补齐系统页权限变更单的差异对比和驳回审批入口，审批人可先查看 `beforeSnapshot/afterSnapshot` 差异，再执行通过或驳回。

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 前端契约验证 | `npm.cmd run verify:frontend-contract` | 通过，310 项检查 |
| 前端生产构建 | `npm.cmd run build` | 通过，仅有既有第三方 pure annotation 与 chunk size warning |
| 前端生产包扫描 | `npm.cmd run verify:production-bundle` | 通过，14 个 JS 产物未发现典型 mock/fallback 样例业务标识 |

# 2026-06-08 系统页角色矩阵接口驱动复验

本轮将系统管理页角色与权限矩阵接入 `GET /api/v1/system/summary`，角色、权限点、敏感权限和规则复核数量均优先由接口数据派生。

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 前端契约验证 | `npm.cmd run verify:frontend-contract` | 通过，312 项检查 |
| 前端生产构建 | `npm.cmd run build` | 通过，仅有既有第三方 pure annotation 与 chunk size warning |
| 前端生产包扫描 | `npm.cmd run verify:production-bundle` | 通过，14 个 JS 产物未发现典型 mock/fallback 样例业务标识 |

# 2026-06-08 总览导航徽标接口驱动复验

本轮将左侧生产总览导航的徽标接入 `GET /api/v1/dashboard/overview`，并删除未被路由使用的旧静态 Dashboard 页。

| 验证项 | 命令 | 结果 |
| --- | --- | --- |
| 前端契约验证 | `npm.cmd run verify:frontend-contract` | 通过，315 项检查 |
| 前端生产构建 | `npm.cmd run build` | 通过，仅有既有第三方 pure annotation 与 chunk size warning |
| 前端生产包扫描 | `npm.cmd run verify:production-bundle` | 通过，12 个 JS 产物未发现典型 mock/fallback 样例业务标识 |
## 2026-06-08 QMS/WMS 模拟适配器增量验证

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| QMS/WMS adapter 后端回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=QualityServiceTest,PilotMesServiceTest,RolePermissionServiceTest,AuditFailureResolverTest" test` | 通过 | `Tests run: 77, Failures: 0, Errors: 0, Skipped: 0`；覆盖 QMS NG 自动缺陷/异常/Hold、QMS/WMS adapter 服务委托、RBAC 写权限和失败审计映射 |
| 前端契约回归 | `npm.cmd run verify:frontend-contract` | 通过 | `Frontend contract passed: 318 checks`；新增 QMS/WMS adapter API 封装检查 |
| 前端生产构建 | `npm.cmd run build` | 通过 | 仅有既有 `@vueuse/core` pure annotation 和 chunk size 警告，不影响构建产物 |

## 2026-06-08 QMS/WMS Adapter 前端演示入口复验

本轮将 QMS/WMS 模拟适配器从 API 封装推进到前端页面可操作入口：质量页提供 QMS 检验上报表单，物料页提供 WMS Adapter 齐套与库存事务操作条。该入口仍属于模拟外部集成，不表示真实 QMS/WMS 已联调。

| 验证项 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 前端契约回归 | `npm.cmd run verify:frontend-contract` | 通过 | `Frontend contract passed: 321 checks`；页面级检查已覆盖 `ingestQmsInspection`、`checkWmsMaterialReadiness`、`ingestWmsInventoryTransaction` |
| 前端生产构建 | `npm.cmd run build` | 通过 | 仅有既有 `@vueuse/core` pure annotation 和 chunk size 警告，不影响构建产物 |
| Docker 镜像重建 | `docker compose -f smartdisplay-mes-api\docker-compose.yml up -d --build` | 通过 | 前端镜像构建成功；随后使用 `--force-recreate backend frontend` 强制替换运行容器 |
| Docker 容器状态 | `docker compose -f smartdisplay-mes-api\docker-compose.yml ps` | 通过 | `smartdisplay-mes-postgres` healthy，`smartdisplay-mes-api` 监听 `8080`，`smartdisplay-mes-ui` 监听 `8888` |
| 前端反代 HTTP 冒烟 | `POST http://localhost:8888/api/v1/auth/login`、`GET /dashboard/overview`、`POST /adapters/wms/material-readiness`、`POST /adapters/qms/inspections` | 通过 | 登录 `200`、Dashboard `200`、WMS 齐套 `200/PASS_WITH_WARNING`、QMS OK 上报 `200/OK` |

说明：内置浏览器连接在当前 Windows 沙箱中两次启动失败，因此本轮未生成浏览器截图；运行态验证以 Docker 容器状态、前端静态资源 `200` 和经 Nginx 反代的业务接口冒烟为准。

## 2026-06-08 QMS/WMS Adapter 浏览器 E2E 复验

本轮将 QMS/WMS Adapter 前端入口纳入真实浏览器 E2E。脚本仍使用本机 Chrome/Edge + CDP，不新增前端测试依赖。

| 验证项 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 浏览器 E2E | `npm.cmd run e2e:browser` | 通过 | 12 步全部 PASS，Console/Network 错误数为 0；报告 `docs/SmartDisplay-MES-browser-e2e-20260608-161858.md` |
| QMS Adapter 页面操作 | 质量页填写当前 E2E Lot 的 QMS OK 上报并点击“提交 QMS 上报” | 通过 | 通过 `/api/v1/quality/inspections?lotNo=...` 校验 `QMS_E2E_*` 检验项已落库 |
| WMS Adapter 页面操作 | 物料页点击“Adapter 齐套”，再切换“入库”并点击“Adapter 事务” | 通过 | 页面返回 `RECEIVE ACCEPTED`，并通过 `/api/v1/material/batches` 校验 `WMSE2E*` 批次已落库 |
| Track In/Out E2E 稳定性 | 点击 Track In 后等待后端状态与页面表格行均变为 `PROCESSING` 再点击 Track Out | 通过 | 避免 Vue 异步刷新未完成时出站操作未对准当前 E2E Lot |
## 2026-06-08 多入口追溯搜索复验

本轮将追溯能力从单 Lot 查询扩展为多入口搜索聚合，覆盖 Lot、SN、工单、设备、物料批次和缺陷代码入口。后端新增 `GET /api/v1/trace/search`，前端追溯页改为查询类型和关键字驱动，并展示影响范围、首个 Lot 全链路证据、质检/物料/审计摘要。

| 验证项 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端追溯服务回归 | `mvn.cmd -s D:\workspace\mes\.m2\settings.xml -Dtest=PilotMesServiceTest test` | 通过 | `Tests run: 21, Failures: 0, Errors: 0, Skipped: 0`，覆盖设备反查和 SN 自动解析 |
| 后端全量回归 | `mvn.cmd -s D:\workspace\mes\.m2\settings.xml test` | 通过 | `Tests run: 203, Failures: 0, Errors: 0, Skipped: 0` |
| 前端契约 | `npm.cmd run verify:frontend-contract` | 通过 | `Frontend contract passed: 322 checks`，追溯页已要求 `searchTrace` 接线 |
| 前端生产构建 | `npm.cmd run build` | 通过 | 仅保留既有第三方 pure annotation 和 chunk size warning |
| 生产包样例扫描 | `npm.cmd run verify:production-bundle` | 通过 | `Production bundle clean: 12 JS assets checked`，追溯页生产包不携带样例 Lot/工单编号 |
| Docker 重建 | `docker compose -f smartdisplay-mes-api\docker-compose.yml up -d --build` | 通过 | PostgreSQL healthy，后端 `8080`，前端 `8888` |
| Docker 追溯接口探活 | 经 `http://localhost:8888/api` 登录后调用 `/v1/trace/search` | 通过 | `LOT202406001-SN001` 解析为 `SN -> LOT202406001`；`COATER_01` 设备追溯命中 5 个 Lot |
| 浏览器 E2E | `npm.cmd run e2e:browser` | 通过 | 12 步通过，报告 `docs/SmartDisplay-MES-browser-e2e-20260608-170328.md` |
## 2026-06-08 载具绑定追溯与 Hybrid Local RAG 复验

本轮补齐载具绑定/解绑动作、Lot 追溯载具证据链，并将 AI SOP 问答从关键词 fallback 推进到本地混合检索。`HYBRID_LOCAL` 仍属于试点本地确定性能力，不调用外部模型，也不自动执行生产动作。

| 验证项 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| AI 目标回归 | `mvn.cmd -s D:\workspace\mes\.m2\settings.xml "-Dtest=AiKnowledgeServiceTest,AiKbIndexServiceTest,AiModelConfigServiceTest" test` | 通过 | 10 项测试覆盖 Hybrid Local 索引、RAG 评分和 SOP_QA fallback 配置 |
| 后端全量回归 | `mvn.cmd -s D:\workspace\mes\.m2\settings.xml test` | 通过 | 213 项测试通过 |
| 前端契约 | `npm.cmd run verify:frontend-contract` | 通过 | 328 项检查，包含 AI 页 `HYBRID_LOCAL` 入口和载具绑定 API |
| 前端生产构建 | `npm.cmd run build` | 通过 | 仅保留既有 `@vueuse/core` pure annotation 和 chunk size warning |
| 生产包扫描 | `npm.cmd run verify:production-bundle` | 通过 | `Production bundle clean: 12 JS assets checked` |
| Docker 重建 | `docker compose -f smartdisplay-mes-api\docker-compose.yml up -d --build` | 通过 | backend、frontend、postgres 均启动，Flyway 从 `1.42` 迁移到 `1.43` |
| 载具追溯探活 | 登录后绑定 `CST-260606-002` 到演示 Lot 并查询 `/api/v1/trace/lots/{lotNo}` | 通过 | 追溯摘要返回 `carrierCount=1` 和载具号 |
| Hybrid RAG 探活 | 登录后调用 `/api/v1/ai/kb/index-jobs` 和 `/api/v1/ai/kb/ask` | 通过 | `LOCAL_RAG_HYBRID` 激活；索引 7 个切片；问答返回 `HYBRID_LOCAL`、`HIGH`、3 条引用 |
| 浏览器 E2E | `npm.cmd run e2e:browser` | 通过 | 12 步通过，报告 `docs/SmartDisplay-MES-browser-e2e-20260608-183556.md` |

## 2026-06-10 MES 手工质检录入复验

本轮将 `POST /api/v1/quality/inspections` 从查询占位升级为 MES 手工质检写入口，并保留 QMS Adapter 独立上报入口。质量页同步改为“MES 手工录入 / QMS Adapter”双来源，新增 `quality:inspection-create` 按钮权限，QE 可录入，PE/OPERATOR 不具备该写权限。

| 验证项 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端质量定向回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=QualityServiceTest,PilotMesServiceTest,RolePermissionServiceTest" test` | 通过 | 58 项通过；覆盖手工质检落库、NG 缺陷/异常/Hold、服务委托和 RBAC 权限 |
| 后端全量回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` | 通过 | 225 项通过 |
| 前端契约回归 | `npm.cmd run verify:frontend-contract` | 通过 | `Frontend contract passed: 401 checks`；覆盖 `createQualityInspection`、`quality:inspection-create` 和质量页手工录入路径 |
| 前端生产构建 | `npm.cmd run build` | 通过 | 仅保留既有 `@vueuse/core` pure annotation 和 chunk size warning |
| 前端生产包扫描 | `npm.cmd run verify:production-bundle` | 通过 | `Production bundle clean: 14 JS assets checked` |
| Docker 镜像重建 | `docker compose -f smartdisplay-mes-api\docker-compose.yml up -d --build` | 受阻 | 本机 Docker 仍强制访问 `127.0.0.1:7897` 代理且端口拒绝连接，基础镜像元数据无法解析 |
| Docker 覆盖部署 | 本地 `mvn package` + `npm run build` 后将 jar/dist 覆盖进现有 `smartdisplay-mes-api`、`smartdisplay-mes-ui` 容器 | 通过 | 后端重启成功，前端容器已替换为本轮 `quality-*` 产物 |
| 前端反代 HTTP 冒烟 | 经 `http://127.0.0.1:8888/api` 登录 QE 后调用 `POST /api/v1/quality/inspections` | 通过 | 返回 `messageType=MANUAL_INSPECTION`、`inspectionCount=1`、`defectCount=0`、`holdApplied=false`，返回项为 `MANUAL_SMOKE_20260610104448` |

说明：本轮 Docker 可运行态已经更新到当前代码，但不是通过完整镜像重建完成；待本机 Docker 代理恢复后，需要重新执行一次 `docker compose -f smartdisplay-mes-api\docker-compose.yml up -d --build` 固化镜像路径。

## 2026-06-10 AI 设备异常分析工作台复验

本轮将第 7 周 AI 设备异常分析从“有 API 封装但页面未接线”推进到可操作工作台：AI 页新增设备异常分析卡片，输入设备号和可选 Lot 后调用 `POST /api/v1/ai/equipment/analyze`，展示风险等级、事件数、关联 Lot、近期缺陷、排查步骤和 SOP/RAG 证据；后端输入快照同步包含目标设备事件、Lot 上下文、近期缺陷 TopN、良率看板和模型配置，AI 仍只做辅助分析，不自动执行生产动作。

| 验证项 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端 AI/RBAC 定向回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=PilotMesServiceTest,RolePermissionServiceTest" test` | 通过 | 44 项通过；覆盖设备异常分析证据快照、AI 留痕、审计和 QE/EE 权限 |
| 后端全量回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` | 通过 | 226 项通过 |
| 前端契约回归 | `npm.cmd run verify:frontend-contract` | 通过 | `Frontend contract passed: 404 checks`；新增 AI 页设备异常分析工作台接线检查 |
| 前端生产构建 | `npm.cmd run build` | 通过 | 仅保留既有 `@vueuse/core` pure annotation 和 chunk size warning |
| 前端生产包扫描 | `npm.cmd run verify:production-bundle` | 通过 | `Production bundle clean: 14 JS assets checked` |
| Docker 运行态 | 本地 jar/dist 覆盖现有 `smartdisplay-mes-api`、`smartdisplay-mes-ui` 容器 | 通过 | 三服务运行正常；完整镜像重建仍待本机 Docker 代理恢复 |
| 前端反代 HTTP 冒烟 | 经 `http://127.0.0.1:8888/api` 登录 QE 后调用 `POST /api/v1/ai/equipment/analyze` | 通过 | `EVAP_01` 返回 `EQUIPMENT_ANALYSIS`、`riskLevel=P1`、`eventCount=2`、`lotCount=2`、`defectCount=7`、`writeActionAllowed=false`、`evidenceLevel=HIGH`，AI 留痕查询返回 1 条 |

## 2026-06-10 Recipe 发布结构化审计复验

本轮补齐计划中“Recipe 发布必须写审计”的硬性要求。V1 发布接口现在调用 `publishRecipe`，成功动作写 `RECIPE_PUBLISH`，并保存 `before/after/changedFields/request` 结构化快照；旧激活/停用接口保留 `RECIPE_ACTIVATE` 与 `RECIPE_DEACTIVATE` 审计。

| 验证项 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端 Recipe/审计定向回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=RecipeServiceTest,AuditFailureResolverTest" test` | 通过 | 43 项通过；覆盖 Recipe 创建、发布、激活、停用成功审计和发布失败审计归类 |
| 后端全量回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` | 通过 | 227 项通过 |
| 后端打包 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" -DskipTests package` | 通过 | 已生成 `smartdisplay-mes-api-1.0.0-SNAPSHOT-exec.jar` |
| Docker 运行态 | 本地 jar 覆盖现有 `smartdisplay-mes-api` 容器并重启 | 通过 | 后端容器重启成功，Flyway 当前版本 `1.43`，前端容器无需替换 |
| 前端反代 HTTP 冒烟 | 经 `http://127.0.0.1:8888/api` 创建 DRAFT Recipe、发布并查询审计 | 通过 | 创建 `RCP_AUDIT_20260610121152` 后发布成功；管理员查询 `RECIPE_PUBLISH` 返回 1 条，审计操作人为 `pe`，快照包含 `before/after`、`DRAFT -> ACTIVE` 和 `changedFields=["status","updatedBy"]` |

## 2026-06-10 Recipe 单一生效版本治理复验

本轮补齐 Recipe 发布/激活时的生产级版本治理：同一 `productCode + stepCode + equipmentCode` 只能保留一个 `ACTIVE` Recipe。发布或激活新版本时，服务层会自动停用同上下文旧版 `ACTIVE`，写 `RECIPE_AUTO_DEACTIVATE` 审计；发布审计 `RECIPE_PUBLISH` 同时记录 `singleActiveContext`、`replacedActiveCount` 和 `replacedActiveRecipes`。数据库侧新增 `V1.44__Enforce_Single_Active_Recipe.sql`，先收敛历史重复生效数据，再创建部分唯一索引 `uk_recipe_single_active_context` 兜底并发一致性。

| 验证项 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端 Recipe 定向回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=RecipeServiceTest" test` | 通过 | 12 项通过；覆盖发布新版本自动停用旧 `ACTIVE`、旧激活接口复用同一治理链、自动停用审计和发布替换快照 |
| 后端全量回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` | 通过 | 229 项通过 |
| 后端打包 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" -DskipTests package` | 通过 | 已生成并覆盖 Docker 后端容器中的 `app.jar` |
| Docker Flyway 迁移 | 重启 `smartdisplay-mes-api` 并查询 `flyway_schema_history` | 通过 | 日志显示从 `1.43` 迁移到 `1.44 - Enforce Single Active Recipe`；数据库记录 `version=1.44, success=t` |
| 前端反代 HTTP 冒烟 | 经 `http://127.0.0.1:8888/api` 创建并发布两个同上下文不同版本 Recipe | 通过 | `RCP_SINGLE_20260610124831_V1` 自动变 `INACTIVE`，`RCP_SINGLE_20260610124831_V2` 为 `ACTIVE`；`RECIPE_PUBLISH` 和 `RECIPE_AUTO_DEACTIVATE` 审计各 1 条，发布快照含 `replacedActiveCount=1` |

## 2026-06-10 Route 单一生效版本治理复验

本轮补齐 Route 版本确定性：同一产品只能存在一条 `ACTIVE` 工艺路线。服务层读取生效 Route 时，如果发现同产品多条 `ACTIVE`，不再按版本排序静默取第一条，而是明确返回业务异常，避免工单释放、Rework 和 Track In 防跳站依赖歧义路线。数据库侧新增 `V1.45__Enforce_Single_Active_Route.sql`，先收敛历史重复生效 Route，再创建部分唯一索引 `uk_route_single_active_product`。

| 验证项 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端 Route 定向回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=RouteServiceTest" test` | 通过 | 9 项通过；新增覆盖同产品多条 `ACTIVE` Route 时拒绝并提示需版本治理 |
| 后端全量回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` | 通过 | 230 项通过 |
| 后端打包 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" -DskipTests package` | 通过 | 已生成并覆盖 Docker 后端容器中的 `app.jar` |
| Docker Flyway 迁移 | 重启 `smartdisplay-mes-api` 并查询 `flyway_schema_history` | 通过 | 日志显示从 `1.44` 迁移到 `1.45 - Enforce Single Active Route`；数据库记录 `version=1.45, success=t` |
| Route API 冒烟 | 经 `http://127.0.0.1:8888/api/v1/routes` 查询生效路线 | 通过 | 返回 `AMOLED_65/RTE_G6_AMOLED65_V08` 和 `AMOLED_67/RTE_G6_AMOLED67_V05`，每个产品仅一条 `ACTIVE` |
| 数据库约束冒烟 | 在事务中尝试插入 `AMOLED_65` 第二条 `ACTIVE` Route | 通过 | PostgreSQL 返回 `duplicate key value violates unique constraint "uk_route_single_active_product"`，证明数据库侧兜底生效 |

## 2026-06-10 BOM 单一生效版本治理复验

本轮补齐 BOM 版本确定性：同一产品只能存在一条 `ACTIVE` BOM。物料齐套校验读取生效 BOM 时，如果发现同产品多条 `ACTIVE`，不再按时间排序静默取第一条，而是明确返回业务异常，避免工单释放和 Track In 物料锁定依赖歧义 BOM。BOM 发布目标版本时会自动停用同产品旧 `ACTIVE` BOM，并写 `BOM_AUTO_DEACTIVATE` 审计；发布审计 `BOM_PUBLISH` 同时记录 `singleActiveContext`、`replacedActiveCount` 和 `replacedActiveBoms`。数据库侧新增 `V1.46__Enforce_Single_Active_Bom.sql`，先收敛历史重复生效 BOM，再创建部分唯一索引 `uk_bom_single_active_product`。

| 验证项 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端 Material 定向回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=MaterialServiceTest" test` | 通过 | 44 项通过；新增覆盖多条 `ACTIVE` BOM 拒绝、发布新版本自动停用旧 BOM 和发布替换快照 |
| 后端全量回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` | 通过 | 231 项通过 |
| 后端打包 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" -DskipTests package` | 通过 | 已生成并覆盖 Docker 后端容器中的 `app.jar` |
| Docker Flyway 迁移 | 重启 `smartdisplay-mes-api` 并查询 `flyway_schema_history` | 通过 | 日志显示从 `1.45` 迁移到 `1.46 - Enforce Single Active Bom`；数据库记录 `version=1.46, success=t` |
| 数据库状态检查 | 查询 `pg_indexes` 和重复 `ACTIVE` BOM 分组 | 通过 | `uk_bom_single_active_product` 已存在；当前数据库不存在同产品多条 `ACTIVE` BOM |
| 数据库约束冒烟 | 在异常捕获块中尝试插入 `AMOLED_65` 第二条 `ACTIVE` BOM | 通过 | PostgreSQL 返回 `duplicate key value violates unique constraint "uk_bom_single_active_product"`，证明数据库侧兜底生效 |

## 2026-06-10 后端试点 fallback 生产默认关闭复验

本轮将 `PilotMesService` 中用于演示的试点样例 fallback 改为显式开关控制：默认配置 `mes.pilot.fallback-enabled=false`，生产运行态不再在正式表查询为空或失败时静默补充样例 BOM、Route、设备事件、物料、质量、异常、审计、看板缺陷 TopN 等数据。演示环境如需降级，可显式将该开关置为 `true`。

| 验证项 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端聚合服务定向回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=PilotMesServiceTest" test` | 通过 | 37 项通过；覆盖 fallback 开启的演示路径，以及 fallback 关闭时 BOM、设备事件、系统审计失败拒绝和空结果不补样例 |
| 后端全量回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` | 通过 | 236 项通过 |
| 后端打包 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" -DskipTests package` | 通过 | 已生成 `smartdisplay-mes-api-1.0.0-SNAPSHOT-exec.jar` |
| Docker 运行态部署 | `docker compose -f smartdisplay-mes-api\docker-compose.yml cp ... backend:/app/app.jar` 后重启后端 | 通过 | 后端容器启动成功，Flyway 当前版本 `1.46`，迁移无需更新 |
| 审计 fallback 冒烟 | 登录后查询 `GET /api/v1/system/audit-logs?bizNo=NO_SUCH_AUDIT_OBJECT_20260610` | 通过 | 返回业务码 `200` 且 `data=[]`，没有返回 `Hold Release 审批`、`Recipe 参数变更` 等试点样例审计 |

## 2026-06-10 追溯 Route 证据按 Lot 产品匹配复验

本轮修正 Lot/SN/多入口追溯中的 Route 证据来源：`traceLot` 不再调用 `/routes` 汇总列表并取第一条生效路线，而是按当前 Lot 的 `productCode` 读取唯一生效 Route 和工序序列。这样在多产品、多 Route 并存时，追溯链路不会把其他产品的工艺路线挂到当前 Lot 上；如果主数据缺失，追溯返回 `route.status=MISSING` 和错误说明，避免运行时下标异常。

| 验证项 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 追溯定向回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=PilotMesServiceTest,PilotMesFlowIntegrationTest" test` | 通过 | 38 项通过；覆盖 `traceLot` 按 Lot 产品读取 `RTE_OLED_V1`，以及完整执行闭环追溯 |
| 后端全量回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` | 通过 | 236 项通过 |
| 后端打包与部署 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" -DskipTests package` 后覆盖 Docker 后端 jar 并重启 | 通过 | 后端容器启动成功，Flyway 当前版本仍为 `1.46` |
| Docker 追溯冒烟 | 登录后查询 `GET /api/v1/trace/lots/LOTSCP20260610010414-001` | 通过 | `lot.productCode=AMOLED_65`，`route.routeCode=RTE_G6_AMOLED65_V08`，`route.productCode=AMOLED_65`，产品匹配结果为 `true` |

## 2026-06-10 Lot 批量 Hold/Release 复验

本轮新增 `POST /api/v1/lots/batch-hold` 和 `POST /api/v1/lots/batch-release`，用于质量工程师在 Lot 管理页对当前筛选结果中的多选 Lot 执行批量处置。批量接口逐 Lot 复用既有单 Lot `hold` / `release` 状态机和审计链，单个 Lot 失败不会拖垮整批；接口返回每个 Lot 的成功/失败原因，并额外写入 `LOT_BATCH_HOLD` / `LOT_BATCH_RELEASE` 汇总审计，便于审计整批操作的总数、成功数、失败数和失败明细。

| 验证项 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端批量处置定向回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=PilotMesServiceTest,RolePermissionServiceTest,AuditFailureResolverTest" test` | 通过 | 86 项通过；覆盖批量 Hold/Release 逐 Lot 汇总、空选择拒绝、汇总审计、RBAC 权限和失败审计映射 |
| 前端契约回归 | `npm.cmd run verify:frontend-contract` | 通过 | 413 项通过；覆盖 `batchHoldLots`、`batchReleaseLots` API 封装和 Lot 页多选批量处置接线 |
| 前端生产构建 | `npm.cmd run build` | 通过 | 仅保留第三方 `@vueuse/core` pure annotation 和 chunk size warning |

## 2026-06-10 WMS 库位任务复核结果留痕复验

本轮将 WMS 库位任务完成后的复核从“记录复核人/时间”升级为“复核通过/驳回 + 结论 + 异常原因 + 审计快照”。复核驳回不自动冲正库存，也不把已完成任务改成 `REJECTED`，而是在任务仍为 `DONE` 的前提下留下 `reviewResult=REJECTED` 和 `exceptionReason`，交给后续异常处置或库存冲正流程承接。

| 验证项 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端 WMS 复核结果定向回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=MaterialServiceTest,RolePermissionServiceTest,AuditFailureResolverTest" test` | 通过 | 98 项通过；覆盖 `APPROVED/REJECTED` 归一、驳回结论、异常原因、审计快照和权限边界 |
| 前端契约回归 | `npm.cmd run verify:frontend-contract` | 通过 | 419 项通过；覆盖 `reviewResult`、`reviewConclusion`、复核通过/驳回按钮和物料页接线 |
| 前端生产构建 | `npm.cmd run build` | 通过 | 仅保留第三方 `@vueuse/core` pure annotation 和 chunk size warning |
| 前端生产包扫描 | `npm.cmd run verify:production-bundle` | 通过 | `Production bundle clean: 14 JS assets checked` |
| Flyway 静态验收 | `powershell -ExecutionPolicy Bypass -File tools\verify-flyway-migrations.ps1` | 通过 | 识别 `V1.1-V1.49` 共 49 个迁移文件 |

## 2026-06-10 WMS 库位任务复核驳回处置复验

本轮将 WMS 库位任务复核驳回后的状态从“只留痕”推进到“可处置”。处置只允许 `DONE + reviewResult=REJECTED + dispositionStatus=PENDING` 的任务进入，支持让步接收、显式调库和升级处理三种结果。关键边界是：复核驳回本身不自动冲正库存；只有用户明确选择 `ADJUST_INVENTORY` 并输入实盘可用数量时，才复用盘点逻辑写 `COUNT` 库存事务。

| 验证项 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端 WMS 复核驳回处置定向回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=MaterialServiceTest,RolePermissionServiceTest,AuditFailureResolverTest" test` | 通过 | 102 项通过；覆盖待处置状态、让步接收关闭差异不改库存、显式调库写库存事务、非驳回/已处置/缺少实盘数量拒绝、失败审计映射和 `material:wms` 权限边界 |
| 前端契约回归 | `npm.cmd run verify:frontend-contract` | 通过 | 420 项通过；覆盖 `dispositionMaterialLocationTask` API、`dispositionStatus`/`dispositionResult` 展示、`canDisposition`、接收差异和调库按钮 |
| 前端生产构建 | `npm.cmd run build` | 通过 | 仅保留第三方 `@vueuse/core` pure annotation 和 chunk size warning |
| 前端生产包扫描 | `npm.cmd run verify:production-bundle` | 通过 | `Production bundle clean: 14 JS assets checked` |
| Flyway 静态验收 | `powershell -ExecutionPolicy Bypass -File tools\verify-flyway-migrations.ps1` | 通过 | 识别 `V1.1-V1.50` 共 50 个迁移文件 |

## 2026-06-11 WMS 复核差异待处置队列复验

本轮将 WMS 复核驳回后的处置入口从“最近任务表内动作”提升为独立待办队列。后端提供 `pendingDispositionOnly=true` 服务端筛选，前端物料页按该筛选拉取真实待处置数据，避免在全量任务表中人工寻找复核驳回项。

| 验证项 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端 WMS 待处置筛选定向回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=MaterialServiceTest,RolePermissionServiceTest,AuditFailureResolverTest" test` | 通过 | 103 项通过；覆盖库位任务复核、驳回处置、权限和失败审计映射，并验证新增查询口径不破坏既有状态机 |
| 前端契约回归 | `npm.cmd run verify:frontend-contract` | 通过 | 423 项通过；覆盖 `dispositionMaterialLocationTask` API、`pendingDispositionTasks`、`pendingDispositionSummary`、`pendingDispositionOnly` 和物料页待处置队列接线 |
| 前端生产构建 | `npm.cmd run build` | 通过 | 仅保留第三方 `@vueuse/core` pure annotation 和 chunk size warning |
| 前端生产包扫描 | `npm.cmd run verify:production-bundle` | 通过 | `Production bundle clean: 14 JS assets checked` |
| 空白检查 | `git -c safe.directory=D:/workspace/mes diff --check` | 通过 | 无 whitespace error，仅有既有 LF/CRLF 提示 |
| Docker 前端静态资源覆盖 | 容器内检查 `/usr/share/nginx/html` | 通过 | `index.html` 和 `assets` 为 2026-06-11 00:32 本轮构建产物，包含 `material-DiQyXzfe.js` 和 `material-dRw9Q9vE.css` |
| Docker HTTP 冒烟 | `Invoke-WebRequest http://127.0.0.1:8888/`，登录后查询 `GET /api/v1/material/location-tasks?pendingDispositionOnly=true` | 通过 | 首页 HTTP 200；登录业务码 200；筛选接口业务码 200 |
| Docker 演示数据 | 经前端反代 API 创建、领取、完成、复核驳回 `COUNT` 库位任务 | 通过 | 生成 `MLT-20260611090846806-0001`，状态 `DONE`，`reviewResult=REJECTED`，`dispositionStatus=PENDING`，待处置筛选返回 `pendingCount=1` |

说明：Codex app 内置浏览器本轮受 Windows 沙箱权限限制，连接时报 `CreateProcessAsUserW failed: 5`，未作为通过证据；本轮以 Docker 运行态 HTTP/API、容器静态资源和自动化契约/构建扫描作为验收依据。

## 2026-06-11 WMS 复核差异升级处置入口复验

本轮补齐复核驳回处置中的第三个分支：升级处置。页面可从待处置队列或最近库位任务表将差异升级为 `ESCALATED`，让 WMS 班组把无法现场接收或调库关闭的问题留给异常/MRB 后续处理。当前升级动作只留痕和改变处置状态，不自动修改库存、不自动关闭异常。

| 验证项 | 命令/方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后端升级处置定向回归 | `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=MaterialServiceTest,RolePermissionServiceTest,AuditFailureResolverTest" test` | 通过 | 104 项通过；新增覆盖 `ESCALATE -> ESCALATED`、不改库存、不写库存事务、返回 `已升级/red` 和处置审计快照 |
| 前端契约回归 | `npm.cmd run verify:frontend-contract` | 通过 | 423 项通过；WMS 复核驳回处置检查已包含 `ESCALATE` 和“升级”入口 |
| 前端生产构建 | `npm.cmd run build` | 通过 | 仅保留第三方 `@vueuse/core` pure annotation 和 chunk size warning |
| 前端生产包扫描 | `npm.cmd run verify:production-bundle` | 通过 | `Production bundle clean: 14 JS assets checked` |
| 空白检查 | `git -c safe.directory=D:/workspace/mes diff --check` | 通过 | 无 whitespace error，仅有既有 LF/CRLF 提示 |
| Docker 前端覆盖 | 覆盖 `smartdisplay-mes-ui:/usr/share/nginx/html/` 后检查首页和 bundle | 通过 | 首页 HTTP 200；待处置筛选 `pendingCount=1`；容器内 `material-CJbEAJQd.js` 包含 `ESCALATE` |
