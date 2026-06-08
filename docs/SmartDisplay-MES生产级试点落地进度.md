# SmartDisplay MES 生产级试点落地进度

更新时间：2026-06-08

## 当前定位

本项目定位为单基地、单产线、模拟外部集成的生产级试点系统。当前实现目标是先打通“工单释放 -> Lot流转 -> Track In/Out -> Hold/Release -> 追溯 -> 看板 -> AI报告”的可运行主干，不声明复刻任何企业内部MES。

## 本次已落地

### 后端 API v1 主干

- 新增 `com.visionox.mes.pilot.controller.PilotV1Controller`，统一暴露 `/api/v1/**` 试点接口。
- 新增/接入工单实体与 Mapper：`ProductionOrder`、`ProductionOrderMapper`。
- `PilotMesService` 聚合已有真实表和模拟适配器数据：
  - 真实表：`prod_order`、`prod_lot`、`prod_lot_step_record`、`lot_hold_record`、`quality_inspection`、`quality_defect_record`、`exception_event`、`quality_mrb_record`、`quality_mrb_attachment`、`quality_mrb_approval_task`、`quality_mrb_minutes`、`md_recipe`、`md_process_step`、`md_equipment`、`equipment_event`、`equipment_pm_task`、`equipment_parameter_sample`、`equipment_recipe_command`、`equipment_status_history`、`equipment_cycle_sample`、`equipment_standard_cycle`、`equipment_gateway_connection`、`equipment_gateway_message`、`equipment_gateway_health_check`、`md_route`、`md_route_step`、`md_bom`、`md_bom_item`、`md_bom_change_request`、`md_bom_change_attachment`、`md_bom_eco_approval_task`、`md_material_location`、`material_location_task`、`material_batch`、`material_loading`、`material_consumption`、`material_inventory_txn`、`material_incoming_inspection`、`material_coa_attachment`、`material_carrier`、`md_supplier`、`supplier_corrective_action`、`supplier_qualification_review_task`、`sys_user`、`sys_audit_log`、`sys_permission_change_request`、`ai_report_record`、`ai_model_config`、`ai_kb_document`、`ai_kb_chunk`、`ai_kb_index_job`。
  - 模拟适配器：产品字典、ERP工单下发、外部 EAP/QMS/WMS 数据来源、AI报告和RAG问答的外部模型调用。
- 覆盖首批计划接口：
  - Auth/System：`POST /api/v1/auth/login`、`GET /api/v1/system/users`、`GET /api/v1/system/audit-logs`。
  - Master：`/api/v1/master/products`、`/api/v1/master/process-steps`、`/api/v1/master/equipments`、`/api/v1/master/defect-codes`。
  - Route/BOM/Recipe：`/api/v1/routes`、`/api/v1/boms`、`/api/v1/boms/change-requests`、`/api/v1/boms/eco-approvals`、`POST /api/v1/boms/eco-approvals/{taskNo}/decision`、`POST /api/v1/boms/change-requests/{changeNo}/review`、`POST /api/v1/boms/change-requests/{changeNo}/publish`、`/api/v1/recipes`、`POST /api/v1/recipes/{id}/publish`。
  - Order/Lot/Execution：`/api/v1/orders`、`POST /api/v1/orders/{orderNo}/release`、`/api/v1/lots`、Track In/Out、Hold、Release、Rework、Scrap。
  - ERP Adapter：`POST /api/v1/adapters/erp/orders`，支持模拟 ERP 工单数组下发和 `count=1000` 批量生成试点工单。
  - Quality/Exception：`/api/v1/quality/inspections`、`/api/v1/quality/exceptions`、`/api/v1/quality/exceptions/{eventNo}/mrb-records`、`/api/v1/quality/mrb-records/{mrbNo}/minutes`、`/api/v1/quality/mrb-approvals`、`POST /api/v1/quality/mrb-approvals/refresh-sla`。
  - Equipment/EAP：`/api/v1/equipment/events`、`POST /api/v1/equipment/events`、`POST /api/v1/equipment/events/{eventNo}/close`、`/api/v1/equipment/oee`、`/api/v1/equipment/status-history`、`POST /api/v1/equipment/status/report`、`/api/v1/equipment/cycle-samples`、`POST /api/v1/equipment/cycle-samples/report`、`/api/v1/equipment/standard-cycles`、`POST /api/v1/equipment/standard-cycles`、`/api/v1/equipment/gateways`、`POST /api/v1/equipment/gateways`、`POST /api/v1/equipment/gateways/{gatewayCode}/heartbeat`、`POST /api/v1/equipment/gateways/{gatewayCode}/health-check`、`/api/v1/equipment/gateway-health-checks`、`/api/v1/equipment/gateway-drivers`、`/api/v1/equipment/gateway-messages`、`/api/v1/equipment/parameters`、`POST /api/v1/equipment/parameters/report`、`/api/v1/equipment/pm-tasks`、`POST /api/v1/equipment/pm-tasks/{taskNo}/complete`、`/api/v1/equipment/recipe-downloads`、`POST /api/v1/equipment/recipe-downloads`、`POST /api/v1/adapters/eap/messages`。
  - Material/IQC：`/api/v1/material/batches`、`/api/v1/material/locations`、`/api/v1/material/location-tasks`、`/api/v1/material/inventory-transactions`、`/api/v1/material/incoming-inspections`、`/api/v1/material/suppliers`、`/api/v1/material/suppliers/trends`、`/api/v1/material/suppliers/qualification-reviews`、供应商8D接口、`POST /api/v1/material/location-tasks`、`POST /api/v1/material/batches/{batchNo}/incoming-inspection`。
  - Trace/Dashboard/AI：Lot追溯、良率看板、生产总览、AI良率日报、设备异常分析、SOP问答、AI模型配置、AI报告留痕查询、知识库索引任务履历。
- 修正项目启动类和 Track In 注释中的合规表述，统一为公开行业模型和通用MES实践。
- 新增 `sys_audit_log`，工单创建/释放、Track In/Out、Hold/Release、Rework、Scrap、AI良率日报、AI设备异常分析、AI SOP问答已写入审计；审计记录已自动补充请求方法、URI、客户端IP和User-Agent，并支持关键写接口失败留痕。
- 新增 `md_route`、`md_route_step`，Track In 第二层已改为 Route 防跳站强校验。
- 新增 `quality_inspection`、`quality_defect_record`、`exception_event`，Track Out 会按 Recipe 参数上下限生成质检记录；显式 NG 或关键参数超限会创建异常事件并自动 Hold Lot。
- 新增 `md_bom`、`md_bom_item`、`md_bom_change_request`、`md_bom_change_attachment`、`md_bom_eco_approval_task`、`md_material_location`、`material_location_task`、`material_batch`、`material_loading`、`material_consumption`、`material_inventory_txn`、`material_incoming_inspection`、`material_coa_attachment`、`material_carrier`、`md_supplier`、`supplier_corrective_action`、`supplier_qualification_review_task`，Track In 已接入关键物料齐套校验与批次锁定，Track Out 已生成物料消耗追溯；BOM 变更已支持替代料验证报告附件元数据、ECO 包快照、风险等级和跨部门会签；WMS 入库会校验库位状态、物料类别、单位和容量，冻结、解冻、退料、盘点会写库存事务履历和审计；库位任务已支持上架、整批移库、盘点任务单；来料 IQC 判定会写检验记录、COA/附件元数据、审计并联动批次质量状态；供应商准入、8D整改、周期复审任务和月度评分趋势已基于批次、IQC 与8D记录聚合。
- 新增 `equipment_event`、`equipment_pm_task`、`equipment_parameter_sample`、`equipment_recipe_command`，设备事件、EAP 参数采样、PM 任务和 Recipe 下发/回读已从静态数据升级为正式表；EAP 参数越限或 Recipe 回读不一致会自动生成设备事件并更新设备状态，设备事件创建、参数上报、PM 完成和 Recipe 下发均写审计。
- `equipment_event` 已扩展停机原因、计划/非计划、开始/结束时间、持续分钟和影响等级；新增 `/api/v1/equipment/oee` 按近 24 小时聚合设备 OEE、可用率、性能率、质量率、计划/非计划停机和停机原因 TopN；事件关闭会回填结束时间、持续分钟并写 `EQUIPMENT_EVENT_CLOSE` 审计。
- 新增 `equipment_status_history` 和 `equipment_cycle_sample`，EAP 状态上报、设备状态变化历史、标准节拍/实际节拍采样、良品/产出数量已落正式表；OEE 性能率优先使用节拍样本计算，缺少样本时才回退到设备状态估算。
- 新增 `equipment_standard_cycle`，标准节拍主数据按产品、工序、设备、Recipe和版本治理；EAP节拍样本未上报标准秒时会自动匹配 ACTIVE 标准节拍，匹配不到才拒绝。
- 新增 `EapAdapter` 和 `SimulatedEapAdapter`，状态、节拍、参数和 Recipe 下发写动作已收口到统一 EAP 适配器边界；新增 `/api/v1/adapters/eap/messages` 标准化消息入口，为真实设备协议驱动替换预留扩展点。
- 新增 `equipment_gateway_connection` 和 `equipment_gateway_message`，支持 EAP 网关连接配置、心跳状态、消息入站履历、处理成功/失败状态和错误留痕；统一消息入口已先写网关消息履历再调用模拟适配器。
- 新增 `EapProtocolDriver`、`EapProtocolDriverRegistry`、模拟 HTTP、厂商 HTTP、SECS/GEM、OPC UA 四类协议驱动边界；网关连接保存驱动编码、模式、TLS、连接/读取超时和配置快照，入站消息保存原始快照与归一化快照。
- 新增 `equipment_gateway_health_check`，支持手动网关健康检查、协议驱动健康结果、PASS/WARN/FAIL 履历、网关状态联动和审计留痕；真实 SECS/GEM、OPC UA、厂商 HTTP 当前明确返回待真机联调的 WARN 口径。
- 新增 JWT 拦截器和轻量级 RBAC：除登录、Swagger、API Docs 外，`/api/**` 默认要求 Bearer Token；写操作按管理员、计划员、操作员、质量工程师、工艺工程师、设备工程师做角色控制。
- 新增 `ErpOrderAdapterService`，模拟 ERP 下发工单并落 `prod_order`；接口支持单批最多 1000 条、批量查重、重复工单跳过、导入汇总审计 `ERP_ORDER_IMPORT` 和失败审计映射，默认只有具备 `order:create` 权限的角色可调用。
- 新增 `ai_report_record`，AI良率日报、设备异常分析、SOP问答都会保存输入快照、Prompt模板版本、模型、输出JSON、创建人和创建时间；V1.31 已扩展模型供应方、模型模式、配置编码、检索策略、证据数量、最高证据分、证据等级和依据不足标志。
- 新增 `ai_model_config`，保存良率日报、设备异常分析、SOP问答的模型运行配置，区分 `SIMULATED`、`SHADOW` 等模式，并内置 OpenAI 兼容接口影子配置占位但默认禁用。
- 新增 `ai_kb_document`、`ai_kb_chunk`，内置 Hold/Release、蒸镀报警、Mura判定、关键物料批次追溯等 SOP/手册/质量标准切片；SOP问答会从正式切片检索引用，依据不足时明确提示；V1.31 已为切片增加检索策略、embedding状态和向量索引预留字段。
- 已启用 Flyway 版本化迁移，新增 `V1.1__Create_Core_Production_Tables.sql`、`V1.2__Seed_Core_Production_Data.sql`，并补齐 `V1.3-V1.10` 后续迁移；覆盖核心生产表、质量检验、缺陷、异常事件、典型 Hold、BOM、物料批次、载具、消耗履历、6 类试点角色账号、AI 留痕和 SOP 知识库场景。`init.sql` 保留作一次性初始化参考，新环境默认由后端启动时执行 `db/migration`。
- 已新增后端 `Dockerfile`、前端 `Dockerfile` 与 Nginx 配置，并将 `docker-compose.yml` 升级为 PostgreSQL、后端、前端三服务；前端容器通过 Nginx 将 `/api` 反向代理到后端容器。
- 已新增第8周交付文档：`SmartDisplay-MES演示脚本.md`、`SmartDisplay-MES流程图与ER图.md`、`SmartDisplay-MES验收清单.md`。
- 已新增 MRB 复判与异常关闭接口：`POST /api/v1/quality/exceptions/{eventNo}/mrb-review`、`POST /api/v1/quality/exceptions/{eventNo}/close`，异常事件会记录复判动作、复判意见、处置动作、根因、关闭结论并同步缺陷处置状态；MRB 复判/关闭会额外写入 `quality_mrb_record`、`quality_mrb_attachment` 和 `quality_mrb_minutes`，保留会议号、参与人、审批状态、附件元数据和会议纪要正文版本；高风险处置生成 `quality_mrb_approval_task` 会签待办，按风险/角色/处置动作计算 SLA，逾期可升级到责任主管，未完成、升级中或驳回时不能关闭异常。
- 已扩展 RBAC 能力模型：登录响应和 `/api/v1/system/me/permissions` 返回菜单、按钮、数据范围权限和数据范围 SQL 模板；补充越权、数据范围与权限能力单元测试。
- 前端已接入菜单级权限和按钮级权限：主导航、侧边导航、路由守卫按 `permissions.menus` 裁剪；工单释放、Track In/Out、Hold、MRB、Recipe发布、设备写动作、AI报告和知识库导入按 `permissions.buttons` 隐藏并在方法内兜底拦截。
- 已新增权限变更审计闭环：`sys_permission_change_request` 保存变更前后权限快照，`POST /api/v1/system/permission-change-requests` 提交申请，`POST /api/v1/system/permission-change-requests/{changeNo}/review` 审批通过/驳回并写入 `sys_audit_log`；审批通过后会应用运行期权限快照。
- 已新增数据范围 SQL 自动拼接能力：`RolePermissionService` 可按 `ALL`、`LINE`、`SELF_SHIFT`、`SELF` 生成安全 SQL 条件；工单和设备列表已接入数据范围过滤。
- 已补充 Flyway 迁移静态验收脚本 `tools/verify-flyway-migrations.ps1` 和回滚/生产变更审批文档 `docs/Flyway迁移验收与回滚策略.md`。
- 已新增 SOP 知识库导入闭环：`POST /api/v1/ai/kb/import` 支持导入文本/Markdown 内容并自动切片落 `ai_kb_document`、`ai_kb_chunk`；`GET /api/v1/ai/kb/documents` 返回文档、切片数量和索引状态，AI 页面已提供文件读取、内容预览、导入入口和索引入口。
- 新增 `ai_kb_index_job`，支持 `KEYWORD_FALLBACK` 关键词索引重建、`PGVECTOR_READY` 向量待联调状态标记、索引任务履历、切片 embedding 状态更新和 `AI_KB_INDEX` 审计；当前明确不生成真实 embedding，不执行 pgvector 相似度检索。

### 前端接口化

- 新增 `smartdisplay-mes-ui/src/api/pilot.js`，封装 `/api/v1` 试点接口。
- `request.js` 增加 `Authorization: Bearer <token>` 请求头，并处理业务 `401/403`：登录过期自动清理本地身份并回到登录页，无权限操作明确提示。
- 登录页已切换到 `/api/v1/auth/login`，展示管理员、计划员、操作员、质量工程师、工艺工程师、设备工程师 6 类试点账号。
- 关键页面已从纯静态改为“接口优先 + 开发 fallback”：
  - 生产总览：接入 `/v1/dashboard/overview`。
  - 工单页面：接入 `/v1/orders`、`/v1/orders/{orderNo}/release`、`/v1/lots`。
  - 生产执行：接入 `/v1/lots`、Track In、Track Out、Hold。
  - 质量管理：接入 `/v1/quality/inspections`、`/v1/quality/exceptions`、`/v1/quality/exceptions/{eventNo}/mrb-records`、`/v1/quality/mrb-approvals`、`/v1/quality/mrb-approvals/refresh-sla`、`/v1/dashboard/yield`。
  - 物料与载具：接入 `/v1/material/batches`、`/v1/material/consumptions`、`/v1/material/inventory-transactions`、`/v1/material/incoming-inspections`、`/v1/material/location-tasks`、`/v1/material/suppliers`、`/v1/material/suppliers/trends`、`/v1/material/suppliers/qualification-reviews`、WMS 入库/冻结/解冻/退料/盘点、库位上架/整批移库/盘点任务、来料 IQC/COA、供应商准入/复审/8D和 `/v1/carriers`。
  - 设备与自动化：接入 `/v1/master/equipments`、`/v1/equipment/events`、`/v1/equipment/events/{eventNo}/close`、`/v1/equipment/oee`、`/v1/equipment/status-history`、`/v1/equipment/status/report`、`/v1/equipment/cycle-samples`、`/v1/equipment/cycle-samples/report`、`/v1/equipment/standard-cycles`、`/v1/equipment/gateways`、`/v1/equipment/gateway-drivers`、`/v1/equipment/gateway-health-checks`、`/v1/equipment/gateway-messages`、`/v1/equipment/parameters`、`/v1/equipment/pm-tasks`、`/v1/equipment/recipe-downloads`、`/v1/adapters/eap/messages`、设备事件创建/关闭、OEE拆解、停机原因TopN、EAP状态上报、节拍采样、标准节拍主数据、网关连接配置、驱动配置、网关心跳、健康检查、消息履历、参数上报、PM完成、Recipe下发/回读和统一EAP消息入口。
  - 追溯分析：接入 `/v1/trace/lots/{lotNo}`。
  - AI页面：接入 `/v1/dashboard/yield`、`/v1/ai/reports/yield`、`/v1/ai/kb/ask`、`/v1/ai/model-configs`、`/v1/ai/report-records`、`/v1/ai/kb/index-jobs`，展示模型模式、检索策略、证据等级、最高证据分、真实报告留痕、知识库索引状态和索引任务履历。

## 验证结果

- 前端：`npm.cmd run build` 通过。
  - 仅有第三方 `@vueuse/core` pure annotation 和 chunk size 警告，不是本次代码错误。
- 前端契约验收：`npm.cmd run verify:frontend-contract` 通过，静态覆盖路由、请求拦截、`/api/v1` 封装、RBAC 菜单/按钮权限、关键页面接线、V1.38 库位任务、V1.41 供应商准入/复审/8D、供应商月度评分趋势接口和生产 mock fallback 禁用约束，共 302 项检查；`npm.cmd run verify:production-bundle` 通过，生产包 14 个 JS 产物未发现典型 mock/fallback 样例业务标识。
- 前端视觉冒烟：当前 UI 已调整为参考 Codex app 的浅色、中性灰、轻边框、低阴影和低饱和按钮风格；`/login`、`/overview`、`/material`、`/equipment`、`/system` 已完成截图检查，无横向溢出、按钮文字溢出、文本裁切和控制台错误。
- 前端真实浏览器 E2E：`npm.cmd run e2e:browser` 通过，10 步覆盖登录、导航权限、工单创建/释放并生成 Lot、UI Track In/Out、质量 MRB/缺陷证据、物料 V1.38 库位任务操作台和状态流、追溯查询、AI 报告生成留痕、系统审计入口；Console/Network 错误数为 0，最新报告见 `docs/SmartDisplay-MES-browser-e2e-20260608-072928.md`。
- 后端单元测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，`Tests run: 191, Failures: 0, Errors: 0, Skipped: 0`。
- 后端打包：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-DskipTests" "-Dspring-boot.repackage.skip=true" package` 通过。
  - 普通 jar、源码编译和 Spring Boot repackage 均已通过。
- Flyway 静态验收：`powershell -ExecutionPolicy Bypass -File tools\verify-flyway-migrations.ps1` 通过，识别 `V1.1-V1.41` 共 41 个迁移文件。
- Flyway 全新库迁移演练：`powershell -ExecutionPolicy Bypass -File tools\run-flyway-rehearsal.ps1 -StartupTimeoutSec 180` 通过；该演练报告生成于 `V1.38 Add Material Location Task Workflow`，后续 V1.39/V1.40/V1.41 已补充静态验收，V1.39 已完成 Docker 容器数据库迁移复验；报告见 `docs/SmartDisplay-MES-flyway-rehearsal-20260608-052419.md`。
- Docker交付配置：`docker compose config` 通过；根目录已新增 `docker-compose.yml` 作为交付入口；后端可执行包 `mvn.cmd "-DskipTests" package` 通过并生成 `target/smartdisplay-mes-api-1.0.0-SNAPSHOT-exec.jar`。
- Docker容器级复验：`docker compose -f smartdisplay-mes-api\docker-compose.yml up -d --build` 通过；`smartdisplay-mes-postgres` healthy，`smartdisplay-mes-api` 暴露 `8080`，`smartdisplay-mes-ui` 暴露 `8888`；容器数据库 Flyway 已迁移到 `V1.39 Add Bom Eco Approval Tasks`。
- HTTP状态流冒烟：经 `http://127.0.0.1:8888/api` 反代登录、总览和库位任务查询均返回业务码 200；V1.38 盘点任务已验证 `CREATED -> ASSIGNED -> DONE` 和 `CREATED -> CANCELLED`。
- BOM/ECO 会签 API 冒烟：经 `http://127.0.0.1:8080/api` 提交 BOM 变更、查询 ECO 会签任务、逐个会签通过并发布目标 BOM；变更单 `BCR-20260608064003841-0001` 生成 3 个任务并全部 `APPROVED`，发布后数据库为 `PUBLISHED|APPROVED|PE,QE,PLANNER`、任务统计 `3|3`。
- HTTP冒烟：后端直连登录、前端首页、Swagger、前端 Nginx `/api` 反代登录、Dashboard 和库位任务接口均返回 200；`GET /api/v1/material/location-tasks` 当前返回 2 条记录。
- 性能冒烟实测：经 `http://127.0.0.1:8888/api` 反代运行 `tools/run-pilot-performance-smoke.ps1` 通过，导入 1000 条模拟工单成功；订单列表 P95 15.67ms、Lot 列表 P95 13.72ms、良率看板 P95 17.28ms、Lot 追溯 P95 60.08ms；报告见 `docs/SmartDisplay-MES-performance-smoke-20260608-030053.md`。
- 多轮稳定性能基线：运行 `tools/run-pilot-performance-baseline.ps1` 通过，3 轮各导入 1000 条模拟工单、每项 20 次采样；订单列表最大 P95 8.59ms、Lot 列表最大 P95 7.32ms、良率看板最大 P95 13.25ms、Lot 追溯最大 P95 20.01ms，均低于阈值并标记 `STABLE`；报告见 `docs/SmartDisplay-MES-performance-baseline-20260608-061856.md`。

## 仍未达到生产级落地标准的缺口

- 审计：关键动作已落 `sys_audit_log`，请求上下文、IP和调用端标识已自动解析并落库；关键写接口业务异常、参数校验异常和系统异常已写失败审计；工单创建/释放、Track In/Out、Hold/Release、Rework/Scrap 已写入 `before/after/changedFields/request` 结构化差异快照；批量操作差异快照和新增写接口审计映射仍需持续治理。
- 质量：基础检验、缺陷、异常事件、NG/参数超限自动 Hold、MRB复判、异常关闭、结构化处置结论、MRB履历、会议号、参与人、审批状态、附件元数据、会议纪要正文版本管理、多角色会签待办、按角色/风险/处置动作的审批 SLA、逾期升级策略和关闭前会签校验已落地。
- 物料：BOM、BOM变更附件、物料批次、库位策略、库位上架/整批移库/盘点任务、上料锁定、消耗履历、载具绑定、WMS 入库/冻结/解冻/退料/盘点、库存事务履历、来料 IQC、COA/检验附件元数据、基于批次与 IQC 的供应商绩效评分、准入/复审/8D整改、月度评分趋势和 `FOR UPDATE` 批次锁已落地；后续可继续扩展异步领取、复核和多库位拆批任务。
- 设备：设备主数据、能力矩阵、事件队列、EAP 参数采样、参数越限自动设备事件、PM任务、Recipe下发/回读命令履历、事件关闭、OEE拆解、停机原因TopN、设备状态历史、标准/实际节拍采样、标准节拍主数据、EAP 统一适配器、网关连接配置、协议驱动抽象、网关心跳、健康检查和消息履历已落地；仍缺真实 SECS/GEM、OPC UA、厂商 HTTP 驱动真机联调和毫秒级设备状态采集。
- Route/BOM：Route正式表、生效状态、Track In防跳站、BOM正式表、关键物料齐套明细、BOM变更审批、替代料策略、替代料验证报告附件、ECO 包快照、风险等级、跨部门会签和版本发布审批已落地。
- 权限：JWT 登录、接口鉴权拦截、角色级写权限、菜单/按钮/数据范围权限能力模型、越权自动化测试、前端菜单裁剪、按钮级隐藏、权限变更申请/审批/审计闭环、启动恢复、手动重载和数据范围 SQL 自动拼接已落地；Lot、质量、异常、物料消耗和载具列表已接入数据范围；组织/产线/班次主数据已补。
- AI：三类 AI 调用已落 `ai_report_record` 并记录输入快照、Prompt模板版本、模型、输出JSON、模型配置快照和证据质量；`ai_model_config` 已提供试点模型运行配置与外部模型影子配置边界；SOP知识库文档/切片表、种子切片、文件导入、自动切片、关键词引用返回、证据等级、依据不足提示、索引任务履历和 pgvector-ready 边界标记已落地；仍缺真实 pgvector 向量检索、真实外部模型联调和引用召回率评估。
- Flyway：已引入依赖并启用 `classpath:db/migration` 自动迁移；已补迁移静态验收脚本、全新库迁移演练、备份恢复校验、回滚策略和生产环境变更审批流程。
- 测试：Track In校验链（含班次窗口）、Lot状态机、工单释放、ERP 1000 条模拟工单导入、质量异常自动Hold、Release后继续执行、追溯链路、供应商月度评分趋势、供应商准入复审任务、V1.38 库位任务状态流和 V1.39 BOM/ECO 会签状态流已具备服务级/接口级验证；正式测试报告、性能冒烟脚本、前端静态契约验收、Codex app 风格视觉冒烟、真实浏览器 E2E、生产 mock fallback 收口、生产包样例标识扫描、一轮容器环境性能实测、三轮稳定性能基线和真实数据库 API 闭环复验已补。
- Docker交付：PostgreSQL、后端、前端三服务 Compose 已整合，根目录 Compose 入口、演示脚本、ER图、业务流程图、验收清单和测试报告已补；2026-06-08 复验已完成三服务容器级启动、前端反代、Swagger、Dashboard、库位任务接口、V1.38 库位任务状态流、V1.39 BOM/ECO 会签状态流和 Flyway V1.39 容器数据库迁移验证。

## 下一步建议

1. 将当前零依赖浏览器 E2E 接入后续 CI 或交付脚本，并在范围扩大时补充异常处置、Rework/Scrap 和多角色权限用例。
2. 将 `tools/run-pilot-performance-baseline.ps1` 接入后续 CI 或交付复验，在固定硬件和更接近试点数据规模下持续采集趋势。
3. 继续推进真实 SECS/GEM、OPC UA 或厂商 HTTP 协议驱动适配、真机联调和毫秒级设备状态采集。
4. 继续推进真实 pgvector 向量检索、真实外部模型联调、引用召回率评估和 AI 安全评审，形成生产级试点验收报告。
5. 继续推进供应商门户协同、多库位拆批任务、供应商复审自动提醒和更严格的批量操作差异快照治理。

## 2026-06-08 增量：核心执行审计差异快照
- `PilotMesService` 已为工单创建、工单释放、Track In、Track Out、Hold、Release、Rework 和 Scrap 生成结构化审计快照，统一写入 `sys_audit_log.request_snapshot`。
- 快照结构固定为 `before`、`after`、`changedFields`、`request`，其中 `changedFields` 自动列出本次动作改变的业务字段，便于审计导出、问题复盘和接口回放。
- 工单释放快照额外记录生成的 Lot 编号列表和数量；Track Out 快照额外记录最终判定结果和参数快照。
- 已验证 `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=PilotMesServiceTest" test` 通过，`Tests run: 14, Failures: 0, Errors: 0, Skipped: 0`。
- 已验证 `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=PilotMesFlowIntegrationTest,AuditLogServiceTest" test` 通过，`Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`。
- 本轮已将 Rework/Scrap 从“可执行动作”补强为生产动作强校验：Rework 必须提交 `reworkRouteCode` 和 `reworkStepCode`，且起始工序必须属于当前产品生效 Route 并允许返工；Scrap 必须提交 `scrapConfirmed=true`、`confirmText=SCRAP:{lotNo}`、原因、责任模块和审批人，否则拒绝落库。
- 已重新验证 `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=PilotMesServiceTest" test` 通过，`Tests run: 16, Failures: 0, Errors: 0, Skipped: 0`；当前后端全量测试已更新为 `Tests run: 191, Failures: 0, Errors: 0, Skipped: 0`。
- 本轮已补齐 Track In 班次校验：服务会按 Lot 产线查询 ACTIVE 班次，支持跨天班次窗口；没有产线、没有 ACTIVE 班次或当前时间不在班次窗口时拒绝进站，且不会继续锁料。
- 已重新验证 `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=TrackInServiceTest,PilotMesFlowIntegrationTest" test` 通过，`Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`；已重新运行后端全量测试，`Tests run: 191, Failures: 0, Errors: 0, Skipped: 0`。

## 2026-06-08 增量：WMS库位任务分步工作流
- 新增 `V1.38__Add_Material_Location_Task_Workflow.sql`，为 `material_location_task` 补充领取人、领取时间、复核人、复核时间、取消人、取消时间、取消原因、异常原因、任务来源和完成时间字段。
- `MaterialService` 已将库位任务创建改为默认 `CREATED`，支持 `executeNow=true` 兼容立即执行；新增领取、完成、取消三类服务方法，完成时才真正更新库存/库位和库存事务。
- 新增接口：`POST /api/v1/material/location-tasks/{taskNo}/assign`、`POST /api/v1/material/location-tasks/{taskNo}/complete`、`POST /api/v1/material/location-tasks/{taskNo}/cancel`，失败审计映射已覆盖。
- 前端物料页库位任务操作台已接入领取、完成、取消按钮，保持浅色 Codex app 风格。
- 已验证 `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=MaterialServiceTest" test` 通过，`Tests run: 29, Failures: 0, Errors: 0, Skipped: 0`；Flyway 静态验收通过，识别 `V1.1-V1.38` 共 38 个迁移文件；Docker 容器数据库已迁移到 `V1.38`；接口冒烟通过 `CREATED -> ASSIGNED -> DONE` 和 `CREATED -> CANCELLED`。

## 2026-06-08 增量：BOM/ECO 跨部门会签
- 新增 `V1.39__Add_Bom_Eco_Approval_Tasks.sql` 和 `md_bom_eco_approval_task` 正式表，记录 ECO 会签任务号、变更单号、产品、目标 BOM、审批角色、审批人、状态、决策意见、SLA、风险等级、ECO 包快照和时间戳。
- `BomChangeRequest` 已扩展 `ecoNo`、`ecoRiskLevel`、`ecoPackageSnapshot`、`ecoApprovalStatus` 和 `ecoRequiredRoles`，BOM 变更提交时生成 ECO 包快照并创建 PE/QE/计划等跨部门会签任务。
- `MaterialService` 已实现 `/api/v1/boms/eco-approvals` 查询和 `POST /api/v1/boms/eco-approvals/{taskNo}/decision` 决策；全部会签通过才允许发布，任一驳回会阻断发布并回写变更单状态。
- RBAC 新增 `bom:eco-approve` 按钮权限，`AuditFailureResolver` 已覆盖 `BOM_ECO_APPROVAL_DECISION` 失败审计映射；前端主数据页已展示 ECO 状态、风险等级、会签角色和会签操作入口。
- 已验证 `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=MaterialServiceTest,RolePermissionServiceTest,AuditFailureResolverTest" test` 通过，`Tests run: 67, Failures: 0, Errors: 0, Skipped: 0`；后端全量测试通过，`Tests run: 177, Failures: 0, Errors: 0, Skipped: 0`。
- 已验证 Flyway 静态验收通过，识别 `V1.1-V1.39` 共 39 个迁移文件；Docker 容器数据库已迁移到 `V1.39 Add Bom Eco Approval Tasks`。
- 已完成 API 冒烟：变更单 `BCR-20260608064003841-0001` 生成 3 个 ECO 会签任务，全部 `APPROVED` 后发布成功；数据库复核为 `PUBLISHED|APPROVED|PE,QE,PLANNER` 和任务统计 `3|3`。

## 2026-06-08 增量：前端 Codex app 风格一致性修复
- 全局视觉基线调整为浅色、中性灰、轻边框、低阴影和低饱和按钮；状态色仅用于业务状态，不再使用深色侧栏和高饱和主按钮。
- 登录、总览、设备、质量、物料、Recipe、Dashboard、Lot 弹窗和主布局已统一按钮、字体、卡片、表格、标签和焦点态样式。
- 已生成视觉检查截图和 `smartdisplay-mes-ui/visual-check/visual-check-summary.json`，覆盖 `/login`、`/overview`、`/material`、`/equipment`、`/system`，检查结果为无横向溢出、无按钮文字溢出、无文本裁切、无控制台错误。

## 2026-06-08 增量：供应商绩效评分

- 新增 `/api/v1/material/suppliers/performance`，基于 `material_batch` 与 `material_incoming_inspection` 聚合供应商批次数、可用批次数、IQC PASS/HOLD/NG、通过率、风险批次数、评分和风险等级。
- 物料页新增“供应商绩效评分”只读看板，展示评分条、通过率、NG/HOLD、风险批次和 HIGH/MEDIUM/LOW 等级；开发态 fallback 仍受 `__DEV_MOCK_FALLBACK__` 编译期开关保护。
- 新增 `MaterialServiceTest.supplierPerformanceShouldAggregateBatchAndInspectionRisk`，覆盖批次 + IQC 聚合、NG/HOLD 降分、高风险排序和不读取 COA 附件表。
- 已验证 `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=MaterialServiceTest" test` 通过，`Tests run: 18, Failures: 0, Errors: 0, Skipped: 0`；当时 `npm.cmd run verify:frontend-contract` 通过，共 268 项检查。

## 2026-06-08 增量：BOM替代料验证报告附件

- 新增 `md_bom_change_attachment` 正式表和 `V1.35__Add_Bom_Change_Attachments.sql`，保存 BOM/ECO 附件编号、变更单号、产品、目标BOM、文件名、地址、校验摘要、文件类型、附件角色、上传人和上传时间。
- `MaterialService.submitBomChange`、`reviewBomChange` 已支持 `attachments` 数组和 `validationFileName/validationFileUrl/validationFileHash` 直接字段，提交/审批 BOM 变更时可写入替代料验证报告附件元数据；`bomChangeRequests` 返回 `attachmentCount` 和附件列表。
- 前端主数据页的 BOM 变更表新增附件数量列，提交变更草稿时随单发送替代料验证报告元数据；前端契约脚本新增对验证报告字段和附件数量显示的检查。
- 已验证 `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=MaterialServiceTest" test` 通过，`Tests run: 19, Failures: 0, Errors: 0, Skipped: 0`；`npm.cmd run verify:frontend-contract` 通过，共 270 项检查；Flyway 静态验收通过，识别 `V1.1-V1.35` 共 35 个迁移文件。

## 2026-06-08 增量：WMS库位策略

- 新增 `md_material_location` 正式表和 `V1.36__Add_Material_Location_Strategy.sql`，保存库位编码、区域、存储类型、物料类别、状态、容量、已用量、环境窗口和策略优先级。
- `MaterialService.receiveMaterial` 已接入库位策略：入库时校验库位状态、物料类别、计量单位和容量；未指定库位时按物料类别、单位、容量和优先级自动选择；入库、退料、盘点和 Track Out 消耗会同步调整库位占用。
- 新增 `/api/v1/material/locations`，物料页新增“库位策略 / 容量管控”只读看板，展示库位类型、物料类、占用、环境窗口、优先级和锁定状态；WMS入库库位输入升级为库位下拉选择。
- 已验证 `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=MaterialServiceTest" test` 通过，`Tests run: 22, Failures: 0, Errors: 0, Skipped: 0`；`npm.cmd run verify:frontend-contract` 通过，共 272 项检查；Flyway 静态验收通过，识别 `V1.1-V1.36` 共 36 个迁移文件。

## 2026-06-08 增量：WMS库位任务

- 新增 `material_location_task` 正式表和 `V1.37__Add_Material_Location_Tasks.sql`，保存任务号、任务类型、批次、源/目标库位、计划/实绩数量、状态、原因、执行人、执行时间和请求快照。
- 新增 `GET /api/v1/material/location-tasks` 与 `POST /api/v1/material/location-tasks`；首版 `MOVE`/`PUTAWAY` 按批次整批转移，校验目标库位状态、容量、物料类别和单位，更新批次当前库位、源/目标库位占用、库存事务和审计；`COUNT` 复用既有盘点库存逻辑并补任务留痕。
- 前端物料页新增“库位任务 / 上架移库盘点”操作台，支持任务类型切换、批次选择、目标库位、任务数量/实盘可用、原因、操作员和最近任务表；开发态 fallback 仍受 `__DEV_MOCK_FALLBACK__` 编译期开关保护。
- 已验证 `mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=MaterialServiceTest" test` 通过，`Tests run: 26, Failures: 0, Errors: 0, Skipped: 0`；该轮 `npm.cmd run verify:frontend-contract` 通过，共 276 项检查；`npm.cmd run build` 和 `npm.cmd run verify:production-bundle` 通过；该轮 Flyway 静态验收通过，识别 `V1.1-V1.37` 共 37 个迁移文件。当前最新验收已升级到 `V1.41`。

## 2026-06-07 增量：核心规则测试补强

- 已新增 `RouteServiceTest`，覆盖生效 Route 缺失、Route 工序缺失、工序顺序返回、Track In 空工序、Lot 当前工序为空、防跳站失败、Route 未配置请求工序、合法进站工序通过。
- 已新增 `RecipeServiceTest`，覆盖 Recipe 编码唯一性、产品 + 工序 + 设备 + 版本唯一性、创建草稿 Recipe 与参数、无 ACTIVE Recipe 拒绝、按版本查询返回最新生效 Recipe、Recipe 发布与停用状态转换。
- 已新增 `PilotMesServiceTest`，覆盖工单创建、工单释放拆分 Lot、缺失工单拒绝、Rework 返工状态转换、Scrap 报废状态转换和对应审计写入。
- 已重新运行后端全量单元测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，当前 `Tests run: 59, Failures: 0, Errors: 0, Skipped: 0`。
- 测试覆盖状态更新：RBAC、权限变更、工单创建/释放、Lot 拆分、Route 防跳站、Recipe 匹配、Track In 校验链、Track Out、Hold/Release、Rework/Scrap、质量 NG 自动 Hold、MRB 复判/关闭、物料齐套/锁定/消耗、AI SOP 知识库基础逻辑已具备单元级验证。
- 后续增量已补齐服务级执行闭环测试、正式测试报告、Docker Compose 容器级启动复验、真实浏览器前端 E2E、Flyway 全新库迁移演练、真实数据库 API 闭环复验、一轮性能冒烟实测和三轮稳定性能基线。

## 2026-06-07 增量：Route 驱动流转与 Lot 数据范围

- `PilotMesService.releaseOrder` 已从固定首站升级为读取产品生效 Route 首站；释放工单生成 Lot 时同步写入 `lineCode`，用于后续执行域权限控制。
- `PilotMesService.trackOut` 已从固定默认路线推进升级为读取产品生效 Route 下一站；当前工序为末站时自动将 Lot 置为 `COMPLETED`。
- Track In 默认设备选择已按当前工序匹配设备能力，不再固定使用 `COATER_01`。
- 新增 `V1.13__Seed_Route_Dispatch_Master_Data.sql`，补齐 `CLEAN`、`EXPOSURE`、`ETCH`、`ENCAPSULATION`、`AGING` 等试点 Route 调度所需设备和 Recipe 参数。
- 新增 `V1.14__Add_Lot_Line_Code_Data_Scope.sql`，为 `prod_lot` 增加 `line_code`、回填历史 Lot 产线并建立索引。
- Lot 列表、生产总览 WIP、追溯入口以及 Track In/Out、Hold、Release、Rework、Scrap 等 Lot 敏感动作已接入 `RolePermissionService` 数据范围过滤；无权限访问的 Lot 会被拦截为“Lot不存在或无权限访问”。
- 新增测试侧 Mockito MockMaker 配置，规避当前 JDK 21 环境下 inline ByteBuddy attach 不稳定问题。
- `PilotMesServiceTest` 已扩展到 9 个用例，新增 Route 首站/下一站/末站完成、Lot 产线继承、Lot 列表数据范围和追溯越权拦截验证。
- 已重新运行后端全量单元测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，当前 `Tests run: 63, Failures: 0, Errors: 0, Skipped: 0`。
- Flyway 静态验收：`powershell -ExecutionPolicy Bypass -File tools\verify-flyway-migrations.ps1` 通过，识别 `V1.1-V1.14` 共 14 个迁移文件。
- 后端打包：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-DskipTests" "-Dspring-boot.repackage.skip=true" package` 通过。

## 2026-06-07 增量：质量与物料跨域数据范围

- `QualityService.inspectionRows`、`exceptionRows`、`defectTopN` 已按 `prod_lot.line_code` 子查询接入数据范围，避免质量看板和异常列表跨产线透出。
- `QualityService.reviewException`、`closeException` 查找异常事件时同步应用数据范围，防止用户拿到事件号后跨产线复判或关闭异常。
- `MaterialService.materialConsumptions` 已按关联 Lot 的 `line_code` 过滤，物料消耗追溯列表不再返回无权限 Lot 的消耗记录。
- `material_carrier` 新增 `line_code` 执行域字段，`MaterialService.carriers` 已按载具产线过滤；空闲载具也能按产线展示，不依赖绑定 Lot。
- 质量检验、异常列表、物料消耗、缺陷 TopN 和告警队列已调整为“正式表查询成功即返回真实结果”；只有数据库异常时才降级 fallback，避免数据范围过滤后的空结果被演示假数据覆盖。
- 新增 `V1.15__Add_Carrier_Line_Code_Data_Scope.sql`，为载具回填绑定 Lot 产线或默认试点产线并建立索引。
- `QualityServiceTest` 新增质量检验和异常列表数据范围测试；`MaterialServiceTest` 新增物料消耗和载具数据范围测试；`PilotMesServiceTest` 新增正式查询空结果不 fallback 的权限边界测试。
- 已重新运行后端全量单元测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，当前 `Tests run: 70, Failures: 0, Errors: 0, Skipped: 0`。
- Flyway 静态验收：`powershell -ExecutionPolicy Bypass -File tools\verify-flyway-migrations.ps1` 通过，识别 `V1.1-V1.15` 共 15 个迁移文件。
- 后端打包：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-DskipTests" "-Dspring-boot.repackage.skip=true" package` 通过。

## 2026-06-07 增量：权限变更持久化热加载

- `PermissionChangeService` 新增应用启动后自动恢复逻辑：读取最近审批通过的权限变更单，按角色应用最新 `afterSnapshot` 到 `RolePermissionService` 运行期权限快照。
- `PermissionChangeService.reloadApprovedPermissionSnapshots()` 支持运行期手动恢复，避免后端重启或内存快照丢失后权限审批结果失效。
- `PilotV1Controller` 新增管理员接口 `POST /api/v1/system/permissions/reload`，返回本次恢复的角色数量。
- `PermissionChangeServiceTest` 新增最新审批快照恢复测试，验证同一角色多条已通过记录时只应用最新一条。
- 已重新运行后端全量单元测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，当前 `Tests run: 76, Failures: 0, Errors: 0, Skipped: 0`。
- Flyway 静态验收：`powershell -ExecutionPolicy Bypass -File tools\verify-flyway-migrations.ps1` 通过，识别 `V1.1-V1.16` 共 16 个迁移文件。
- 后端打包：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-DskipTests" "-Dspring-boot.repackage.skip=true" package` 通过。

## 2026-06-07 增量：组织、产线与班次主数据

- 新增 `md_site`、`md_production_line`、`md_work_shift` 三类正式主数据表，覆盖单基地、试点产线、预留产线和白/夜班配置。
- 新增 `V1.16__Create_Org_Line_Shift_Master_Data.sql`，提供 `SITE_HF_01`、`LINE_01`、`LINE_02`、`SHIFT_D_LINE_01`、`SHIFT_N_LINE_01` 等种子数据。
- 新增 `Site`、`ProductionLine`、`WorkShift` 实体和 Mapper，`MasterDataService` 支持基地、产线、班次查询及 `siteCode`、`lineCode`、`status` 筛选。
- `PilotV1Controller` 新增 `/api/v1/master/sites`、`/api/v1/master/production-lines`、`/api/v1/master/shifts`；旧 `/api/master-data/**` 同步补齐兼容接口。
- 前端主数据页已接入基地、产线、班次接口，展示组织层级、产线状态和班次时段；后端不可用时保留开发 fallback。
- 已新增 `MasterDataServiceTest`，覆盖基地、产线和班次查询服务。
- 已重新运行后端全量单元测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，当前 `Tests run: 75, Failures: 0, Errors: 0, Skipped: 0`。
- Flyway 静态验收：`powershell -ExecutionPolicy Bypass -File tools\verify-flyway-migrations.ps1` 通过，识别 `V1.1-V1.16` 共 16 个迁移文件。
- 前端生产构建：`npm.cmd run build` 通过，仍仅有第三方 pure annotation 和 chunk size 警告。
- 后端打包：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-DskipTests" "-Dspring-boot.repackage.skip=true" package` 通过。

## 2026-06-07 增量：审计请求上下文

- 新增 `AuditRequestContext` 和 `AuditRequestContextFilter`，为所有 Web 请求绑定请求方法、URI、客户端IP和User-Agent，并在请求结束后清理 ThreadLocal，避免跨请求串用。
- `AuditLogService.record(...)` 保持原有签名不变，自动读取当前请求上下文并写入 `sys_audit_log`，不影响工单、Lot、质量、权限变更和AI等既有审计调用点。
- 新增 `V1.17__Add_Audit_Request_Context.sql`，为 `sys_audit_log` 补充 `request_method`、`request_uri`、`client_ip`、`user_agent` 字段和 URI 索引。
- 新增 `AuditLogServiceTest` 与 `AuditRequestContextFilterTest`，覆盖有/无请求上下文写审计、代理IP解析、长User-Agent截断和 ThreadLocal 清理。
- 已重新运行后端全量单元测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，当前 `Tests run: 80, Failures: 0, Errors: 0, Skipped: 0`。
- Flyway 静态验收：`powershell -ExecutionPolicy Bypass -File tools\verify-flyway-migrations.ps1` 通过，识别 `V1.1-V1.17` 共 17 个迁移文件。

## 2026-06-07 增量：关键写接口失败审计

- 新增 `AuditFailureResolver`，将 `/api/v1` 和兼容旧入口中的关键写请求失败归类为可查询的业务动作，覆盖登录、工单、Recipe、Lot执行、质量处置、权限变更、设备事件和AI写接口。
- 新增 `AuditFailureService`，在统一异常处理层捕获业务异常、参数校验异常和系统异常后，以 `result=FAIL` 写入 `sys_audit_log`；审计写入失败只记录警告，不改变原错误响应。
- `AuditLogService` 新增 `recordFailure(...)`，与成功审计共用动作、业务编号、业务类型、请求上下文和输入快照字段，方便按 `action + result` 追溯成功/失败尝试。
- `GlobalExceptionHandler` 补齐 `MethodArgumentNotValidException`，使 JSON 请求体参数校验失败也进入统一响应和失败审计链路。
- 新增 `AuditFailureResolverTest`、`AuditFailureServiceTest` 和 `GlobalExceptionHandlerTest`，并扩展 `AuditLogServiceTest` 覆盖失败结果。
- 已重新运行后端全量单元测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，当前 `Tests run: 88, Failures: 0, Errors: 0, Skipped: 0`。

## 2026-06-07 增量：执行闭环集成测试

- 修复 `PilotMesService.trackOut` 生产执行规则：只有 `OK` 出站才推进 Route 下一站；`NG` 或参数超限由质量服务自动 Hold 后保持当前工序，避免异常 Lot 被错误推进。
- 新增 `PilotMesFlowIntegrationTest`，用真实 `PilotMesService`、`TrackInService`、`QualityService`、`HoldService` 串联，Mapper 使用状态化 mock，覆盖：
  - 工单释放生成 Lot，并从生效 Route 首站 `COATING` 开始。
  - Track In 校验 Route、设备、Recipe、物料齐套并创建过站记录。
  - Track Out 参数超限触发质量 NG、异常事件、缺陷记录和自动 Hold。
  - Release 关闭 Hold 后 Lot 恢复 `READY`，仍停留原工序等待复测。
  - 复测 OK 后 Lot 推进到下一工序 `EXPOSURE`。
  - Lot 追溯返回工单、过站、Hold、质检、异常和物料消耗链路。
- `PilotMesServiceTest` 新增 `NG` 出站不推进 Route 的单元规则测试，防止回归。
- 已重新运行后端全量单元测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，当前 `Tests run: 72, Failures: 0, Errors: 0, Skipped: 0`。
- Flyway 静态验收：`powershell -ExecutionPolicy Bypass -File tools\verify-flyway-migrations.ps1` 通过，识别 `V1.1-V1.15` 共 15 个迁移文件。
- 后端打包：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-DskipTests" "-Dspring-boot.repackage.skip=true" package` 通过。

## 2026-06-07 增量：WMS库存事务与前端操作台

- 新增 `material_inventory_txn` 正式表和 `V1.18__Add_Wms_Inventory_Transactions.sql`，`material_batch` 补充 `frozen_qty`、`returned_qty`、`last_count_time`、`stock_version` 字段。
- `MaterialBatchMapper` 增加 `selectByBatchNoForUpdate` 和 `selectAvailableBatchForUpdate`，Track In 物料锁定和 WMS 写操作均通过数据库行锁保护关键批次库存。
- `MaterialService` 已落地 WMS 入库、冻结、解冻、退料、盘点和库存事务查询；所有库存写操作同步写 `material_inventory_txn` 和 `sys_audit_log`。
- `PilotV1Controller` 新增 `/api/v1/material/receive`、`/api/v1/material/batches/{batchNo}/freeze`、`/unfreeze`、`/return`、`/inventory-count` 和 `/api/v1/material/inventory-transactions`。
- `AuditFailureResolver` 已覆盖 WMS 关键写接口失败审计；`RolePermissionService` 新增 `material:wms` 按钮权限，物料写接口不再依赖默认兜底规则。
- 前端物料页新增 WMS 库存事务操作台，支持入库、冻结、解冻、退料、盘点提交，并展示库存事务履历；保留开发环境 fallback 数据。
- 已重新运行定向测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=RolePermissionServiceTest,MaterialServiceTest,AuditFailureResolverTest" test` 通过，当前 `Tests run: 28, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行后端全量测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，当前 `Tests run: 99, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行前端生产构建：`npm.cmd run build` 通过，仍仅有第三方 pure annotation 和 chunk size 警告。
- Flyway 静态验收通过，当前识别 `V1.1-V1.19` 共 19 个迁移文件。

## 2026-06-07 增量：BOM变更审批与替代料策略

- 新增 `md_bom_change_request` 正式表和 `V1.19__Add_Bom_Change_Approval.sql`，`md_bom_item` 补充 `substitute_priority`、`substitute_enabled` 字段，并增加 PI 替代料种子数据。
- `MaterialService` 新增 BOM 变更提交、审批通过/驳回、发布生效能力；发布时会将同产品旧 ACTIVE BOM 置为 INACTIVE，并写入审计。
- Track In 物料齐套和锁定逻辑已从“逐物料强匹配”升级为“按 `substitute_group` 聚合，按 `substitute_priority` 选择可用主料/替代料”，主料不可用时可自动落到同组替代料。
- `PilotV1Controller` 新增 `/api/v1/boms/change-requests`、`/review`、`/publish` 接口；`AuditFailureResolver` 已覆盖 BOM 变更提交、审批和发布失败审计。
- `RolePermissionService` 新增 `bom:change` 按钮权限，工艺工程师默认具备 BOM 变更能力；前端权限枚举同步补齐。
- 前端主数据页新增 BOM 变更审批区，支持提交示例变更草稿、审批通过和发布生效，并展示变更单状态。
- 已重新运行定向测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=MaterialServiceTest,RolePermissionServiceTest,AuditFailureResolverTest" test` 通过，当前 `Tests run: 33, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行后端全量测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，当前 `Tests run: 99, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行前端生产构建：`npm.cmd run build` 通过，仍仅有第三方 pure annotation 和 chunk size 警告。
- Flyway 静态验收通过，当前识别 `V1.1-V1.19` 共 19 个迁移文件。

## 2026-06-07 增量：来料IQC与COA附件留痕

- 新增 `material_incoming_inspection`、`material_coa_attachment` 正式表和 `V1.20__Add_Material_Iqc_Coa.sql`，提供来料检验记录、COA编号、附件名、附件地址、校验摘要、上传人和上传时间字段。
- `MaterialService` 新增来料 IQC 查询与提交能力；PASS 会将批次 `quality_status` 恢复为 `PASS` 并按库存状态恢复可用，NG/HOLD 会将批次置为 `HOLD`，从而被 Track In 物料齐套校验自动拦截。
- `PilotV1Controller` 新增 `GET /api/v1/material/incoming-inspections` 和 `POST /api/v1/material/batches/{batchNo}/incoming-inspection`。
- `AuditFailureResolver` 已覆盖来料 IQC 失败审计；成功提交会写 `MATERIAL_IQC` 审计。
- `RolePermissionService` 新增 `material:iqc` 按钮权限，QE 默认具备来料判定能力且可进入物料页；WMS 库存事务仍由 `material:wms` 单独控制。
- 前端物料页新增“来料质检 / COA 留痕”操作区，支持批次选择、PASS/HOLD/NG 判定、检验数量、抽检数量、COA编号、附件元数据、缺陷代码和处置结论提交，并展示最近 IQC 记录。
- 已重新运行定向测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=MaterialServiceTest,RolePermissionServiceTest,AuditFailureResolverTest" test` 通过，当前 `Tests run: 36, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行后端全量测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，当前 `Tests run: 102, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行前端生产构建：`npm.cmd run build` 通过，仍仅有第三方 pure annotation 和 chunk size 警告。
- Flyway 静态验收通过，当前识别 `V1.1-V1.20` 共 20 个迁移文件。

## 2026-06-07 增量：MRB履历与附件元数据

- 新增 `quality_mrb_record`、`quality_mrb_attachment` 正式表和 `V1.21__Add_Mrb_Record_Attachments.sql`，保存每次 MRB 复判/关闭的会议号、参与人、风险等级、审批状态、处置意见和附件元数据。
- `QualityService.reviewException`、`closeException` 已从“只覆盖 exception_event 当前字段”升级为“更新当前状态 + 写不可覆盖 MRB 履历”；附件元数据支持文件名、地址、校验摘要、类型、上传人和上传时间。
- `PilotV1Controller` 新增 `GET /api/v1/quality/exceptions/{eventNo}/mrb-records`，按现有 Lot 数据范围校验事件访问权限后返回 MRB 履历和附件列表。
- 前端质量页新增“MRB 处置履历 / 附件”区域，选中异常后展示复判/关闭记录，并支持在复判操作中提交会议号、参与人、风险等级和附件元数据。
- 已重新运行定向测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=QualityServiceTest,AuditFailureResolverTest,RolePermissionServiceTest" test` 通过，当前 `Tests run: 26, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行后端全量测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，当前 `Tests run: 103, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行前端生产构建：`npm.cmd run build` 通过，仍仅有第三方 pure annotation 和 chunk size 警告。
- 后端打包：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-DskipTests" "-Dspring-boot.repackage.skip=true" package` 通过。
- Flyway 静态验收通过，当前识别 `V1.1-V1.21` 共 21 个迁移文件。

## 2026-06-07 增量：MRB多角色会签任务

- 新增 `quality_mrb_approval_task` 正式表和 `V1.22__Add_Mrb_Approval_Tasks.sql`，保存 MRB 会签任务号、角色、审批人、状态、结论、意见、到期时间和处理时间。
- `QualityService.reviewException` 在 P1、返工、报废或显式 `approvalRequired=true` 时生成 QE/PE/EE 会签待办，并将 MRB 记录置为 `PENDING`、异常事件置为 `MRB_PENDING`。
- 新增 `GET /api/v1/quality/mrb-approvals`、`POST /api/v1/quality/mrb-approvals/{taskNo}/approve`、`/reject`；审批完成会汇总更新 MRB 记录和异常事件状态。
- `QualityService.closeException` 新增关闭前校验：存在 `PENDING` 会签时禁止关闭，存在 `REJECTED` 会签时禁止关闭。
- `RolePermissionService` 新增 `quality:mrb-approve` 按钮权限，QE/PE/EE 默认具备会签审批能力，PE/EE 不能直接执行 MRB 复判或异常关闭。
- 前端质量页新增“会签待办”列表，支持按选中异常查看待办并执行通过/驳回。
- 已重新运行定向测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=QualityServiceTest,RolePermissionServiceTest,AuditFailureResolverTest" test` 通过，当前 `Tests run: 29, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行后端全量测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，该增量验证 `Tests run: 106, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行前端生产构建：`npm.cmd run build` 通过，仍仅有第三方 pure annotation 和 chunk size 警告。
- 后端打包：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-DskipTests" "-Dspring-boot.repackage.skip=true" package` 通过。
- Flyway 静态验收通过，该增量验证识别 `V1.1-V1.22` 共 22 个迁移文件。

## 2026-06-07 增量：设备事件、PM与EAP参数落库

- 新增 `equipment_event`、`equipment_pm_task`、`equipment_parameter_sample` 正式表和 `V1.23__Add_Equipment_Events_Pm_Eap.sql`，覆盖设备报警/参数事件、PM计划任务和EAP参数采样履历。
- 新增 `EquipmentService`、实体和 Mapper，支持设备事件查询/创建、EAP参数上报、参数越限自动生成设备事件、设备状态联动、PM任务查询和完成。
- `PilotV1Controller` 新增 `/api/v1/equipment/events`、`/api/v1/equipment/parameters`、`/api/v1/equipment/parameters/report`、`/api/v1/equipment/pm-tasks`、`/api/v1/equipment/pm-tasks/{taskNo}/complete`。
- `AuditFailureResolver` 已覆盖设备事件、EAP参数上报和PM完成失败审计；成功路径写 `EQUIPMENT_EVENT`、`EAP_PARAMETER_REPORT`、`EQUIPMENT_PM_COMPLETE` 审计。
- 前端设备页已从纯静态升级为接口驱动，展示设备状态矩阵、事件队列、EAP参数采样履历和PM待办，并支持模拟事件创建、参数上报和PM完成。
- 已重新运行定向测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=EquipmentServiceTest,RolePermissionServiceTest,AuditFailureResolverTest" test` 通过，当前 `Tests run: 25, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行后端全量测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，该增量验证 `Tests run: 111, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行前端生产构建：`npm.cmd run build` 通过，仍仅有第三方 pure annotation 和 chunk size 警告。
- Flyway 静态验收通过，该增量验证识别 `V1.1-V1.23` 共 23 个迁移文件。

## 2026-06-07 增量：Recipe下发与EAP回读确认

- 新增 `equipment_recipe_command` 正式表和 `V1.24__Add_Equipment_Recipe_Downloads.sql`，保存 Recipe 下发命令号、设备、Lot、工序、产品、Recipe版本、EAP ACK、回读状态、参数快照和不一致明细。
- `EquipmentService.downloadRecipe` 支持校验 ACTIVE Recipe 与目标设备/工序/产品匹配，保存期望参数快照与 EAP 回读快照；回读 `MISMATCH` 时自动生成 `RECIPE` 类型设备事件并将设备状态置为 `ALARM`。
- `PilotV1Controller` 新增 `GET /api/v1/equipment/recipe-downloads` 和 `POST /api/v1/equipment/recipe-downloads`；`AuditFailureResolver` 已覆盖 `EQUIPMENT_RECIPE_DOWNLOAD` 失败审计。
- 前端设备页新增“Recipe 下发 / 回读确认”和“Recipe 下发履历”面板，支持模拟 MATCH/MISMATCH 回读结果并刷新履历。
- 已重新运行定向测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=EquipmentServiceTest,RolePermissionServiceTest,AuditFailureResolverTest" test` 通过，当前 `Tests run: 28, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行后端全量测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，当前 `Tests run: 114, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行前端生产构建：`npm.cmd run build` 通过，仍仅有第三方 pure annotation 和 chunk size 警告。
- Flyway 静态验收通过，当前识别 `V1.1-V1.24` 共 24 个迁移文件。

## 2026-06-07 增量：设备停机原因与OEE试点口径

- 新增 `V1.25__Add_Equipment_Downtime_Oee.sql`，为 `equipment_event` 增加 `reason_code`、`reason_name`、`downtime_category`、`downtime_type`、`started_time`、`ended_time`、`duration_minutes`、`impact_level`，并补充计划 PM、非计划报警和开放停机种子事件。
- `EquipmentService` 新增 `oeeSummary` 和 `closeEvent`：按近 24 小时聚合计划停机、非计划停机、可用率、性能率、质量率、OEE、停机原因 TopN 和设备级可用率；事件关闭会回填结束时间、持续分钟、关闭结论并可将设备恢复到 `IDLE`。
- `PilotV1Controller` 新增 `GET /api/v1/equipment/oee` 与 `POST /api/v1/equipment/events/{eventNo}/close`；`AuditFailureResolver` 已覆盖 `EQUIPMENT_EVENT_CLOSE` 失败审计。
- 生产总览页新增设备 OEE 拆解卡片和停机原因 TopN；设备页新增 OEE 拆解、停机原因 TopN、事件关闭按钮和 `DOWN` 事件类型。
- 已重新运行定向测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=EquipmentServiceTest,AuditFailureResolverTest" test` 通过，当前 `Tests run: 19, Failures: 0, Errors: 0, Skipped: 0`。
- 已完成该增量后端全量测试；最新全量测试统计见上方“验证结果”和下一节 V1.26 增量。
- 已重新运行前端生产构建：`npm.cmd run build` 通过，仍仅有第三方 pure annotation 和 chunk size 警告。
- 后端打包：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-DskipTests" "-Dspring-boot.repackage.skip=true" package` 通过。
- 已完成该增量 Flyway 静态验收；最新迁移范围见上方“验证结果”和下一节 V1.26 增量。

## 2026-06-07 增量：设备状态历史与节拍采样

- 新增 `V1.26__Add_Equipment_Status_Cycle_Samples.sql`，建立 `equipment_status_history` 和 `equipment_cycle_sample`，保存 EAP 状态上报、设备状态变化来源、标准节拍、实际节拍、产出数量和良品数量。
- `EquipmentService` 新增状态历史、节拍采样查询和模拟上报能力；设备状态变更统一写入状态历史，参数越限、Recipe 回读失败、PM 完成、事件关闭和显式 EAP 状态上报均可追溯。
- OEE 计算已从单纯事件估算升级为“状态历史 + 节拍采样”口径：性能率优先按 `standard_cycle_seconds / actual_cycle_seconds` 采样计算，质量率优先按样本 `goodQty / outputQty` 计算，缺少样本时才回退到设备状态估算。
- `PilotV1Controller` 新增 `GET /api/v1/equipment/status-history`、`POST /api/v1/equipment/status/report`、`GET /api/v1/equipment/cycle-samples`、`POST /api/v1/equipment/cycle-samples/report`；成功审计动作包含 `EAP_STATUS_REPORT` 和 `EAP_CYCLE_REPORT`，失败审计已同步映射。
- 前端设备页新增“EAP 状态上报 / 状态历史”和“EAP 标准节拍 / 实际节拍”面板；生产总览页会显示 OEE 性能率样本数。
- 已重新运行定向测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=EquipmentServiceTest,RolePermissionServiceTest,AuditFailureResolverTest" test` 通过，当前 `Tests run: 36, Failures: 0, Errors: 0, Skipped: 0`。
- 已完成该增量后端全量测试；最新全量测试统计见上方“验证结果”和下一节 V1.27 增量。
- 已重新运行前端生产构建：`npm.cmd run build` 通过，仍仅有第三方 pure annotation 和 chunk size 警告。
- 后端打包：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-DskipTests" "-Dspring-boot.repackage.skip=true" package` 通过。
- 已完成该增量 Flyway 静态验收；最新迁移范围见上方“验证结果”和下一节 V1.27 增量。

## 2026-06-07 增量：EAP 统一适配器占位

- 新增 `EapAdapter` 接口和 `SimulatedEapAdapter` 默认实现，将 EAP 状态、节拍、参数和 Recipe 下发统一收口到可替换适配器边界。
- 新增 `POST /api/v1/adapters/eap/messages` 标准化消息入口，支持 `STATUS`、`CYCLE`、`PARAMETER`、`RECIPE_DOWNLOAD` 消息类型和常用别名。
- 消息 envelope 会补充 `operator`、`correlationId`、`gatewayId`、`protocol`、`sourceSystem`、`adapterCode` 到业务快照，便于后续真实 EAP 网关审计与排障。
- `PilotMesService` 的 EAP 写动作改为经 `EapAdapter` 调用，保留原有设备页面接口不变，同时为后续替换 SECS/GEM、OPC UA 或厂商 HTTP 网关驱动预留扩展点。
- RBAC 新增 `equipment:eap-ingest` 按钮权限，默认仅管理员和设备工程师可调用统一 EAP 消息入口；失败审计新增 `EAP_ADAPTER_MESSAGE` 映射。
- 前端 API 封装新增 `ingestEapMessage`，可用于后续设备页或演示脚本直接调用统一入口。
- 已重新运行定向测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=SimulatedEapAdapterTest,RolePermissionServiceTest,AuditFailureResolverTest,PilotMesServiceTest,PilotMesFlowIntegrationTest" test` 通过，当前 `Tests run: 44, Failures: 0, Errors: 0, Skipped: 0`。

## 2026-06-07 增量：标准节拍主数据治理

- 新增 `V1.27__Add_Equipment_Standard_Cycle.sql` 和 `equipment_standard_cycle`，以产品、工序、设备、Recipe和版本管理标准节拍、上下限窗口、生效状态和审计快照。
- `EquipmentService.publishStandardCycle` 支持发布 ACTIVE 标准节拍，自动将同产品/工序/设备/Recipe 的旧 ACTIVE 版本置为 `INACTIVE` 并写 `EQUIPMENT_STANDARD_CYCLE_PUBLISH` 审计。
- EAP 节拍样本上报支持不传 `standardCycleSeconds`：后端会按产品/工序/设备/Recipe 匹配 ACTIVE 标准节拍，匹配成功后写入样本标准秒和 `standardCycleNo` 快照，匹配不到才拒绝。
- 新增 `GET /api/v1/equipment/standard-cycles` 和 `POST /api/v1/equipment/standard-cycles`；失败审计已覆盖标准节拍发布接口。
- 前端设备页新增“标准节拍主数据”面板，可查询 ACTIVE 标准节拍、发布新版本，并支持节拍样本留空标准秒后由后端匹配主数据。
- 已重新运行定向测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=EquipmentServiceTest,RolePermissionServiceTest,AuditFailureResolverTest,SimulatedEapAdapterTest" test` 通过，当前 `Tests run: 43, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行前端生产构建：`npm.cmd run build` 通过，仍仅有第三方 pure annotation 和 chunk size 警告。

## 2026-06-07 增量：EAP 设备网关连接与消息履历

- 新增 `V1.28__Add_Equipment_Gateway_Connections.sql`，建立 `equipment_gateway_connection` 和 `equipment_gateway_message`，种子包含模拟 HTTP、SECS/GEM 预留和 OPC UA 预留三类网关。
- 新增 `EapGatewayService`，支持网关查询、注册/更新、心跳、消息履历查询和统一入站消息处理；入站消息先写 `RECEIVED`，调用适配器成功后置为 `PROCESSED`，失败置为 `FAILED` 并将网关状态标记为 `DEGRADED`。
- `PilotV1Controller` 新增 `GET /api/v1/equipment/gateways`、`POST /api/v1/equipment/gateways`、`POST /api/v1/equipment/gateways/{gatewayCode}/heartbeat`、`GET /api/v1/equipment/gateway-messages`。
- RBAC 新增 `equipment:eap-gateway`，默认管理员和设备工程师可维护网关；失败审计已覆盖网关注册和心跳。
- `POST /api/v1/adapters/eap/messages` 已改为经网关服务记录消息履历后再调用 `SimulatedEapAdapter`，为后续替换真实 SECS/GEM、OPC UA 或厂商 HTTP 驱动留下边界。
- 前端设备页新增 “EAP 网关连接” 和 “EAP 网关消息履历” 面板，支持网关保存、心跳/降级模拟、消息履历刷新和 `STATUS/CYCLE` 模拟入站。
- 已重新运行定向测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=EapGatewayServiceTest,RolePermissionServiceTest,AuditFailureResolverTest,PilotMesServiceTest,PilotMesFlowIntegrationTest" test` 通过，当前 `Tests run: 48, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行后端全量测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，当前 `Tests run: 135, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行 Flyway 静态验收：`powershell -ExecutionPolicy Bypass -File tools\verify-flyway-migrations.ps1` 通过，识别 `V1.1-V1.28` 共 28 个迁移文件。
- 已重新运行前端生产构建和后端打包：`npm.cmd run build`、`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-DskipTests" "-Dspring-boot.repackage.skip=true" package` 均通过。

## 2026-06-07 增量：EAP 协议驱动抽象与配置快照

- 新增 `V1.29__Add_Eap_Protocol_Driver_Config.sql`，为 `equipment_gateway_connection` 增加驱动编码、驱动模式、TLS开关、连接超时、读取超时和驱动配置快照，为 `equipment_gateway_message` 增加驱动编码和归一化消息快照。
- 新增 `EapProtocolDriver`、`EapProtocolDriverRegistry` 以及 `SimulatedHttpProtocolDriver`、`VendorHttpProtocolDriver`、`SecsGemProtocolDriver`、`OpcUaProtocolDriver` 四类驱动边界。
- `EapGatewayService.ingestMessage` 已升级为“原始消息落库 -> 选择协议驱动 -> 归一化协议帧 -> 调用业务适配器 -> 保存处理结果”，消息履历同时保留原始快照和归一化快照。
- 新增 `GET /api/v1/equipment/gateway-drivers` 返回当前驱动能力，前端设备页可显示驱动数量、驱动编码、驱动模式、TLS和连接/读取超时配置。
- SECS/GEM 入站支持按 `stream/function` 或 `secsMessage` 推导标准 `messageType`，例如 `S6F11` 可归一为 `STATUS`；OPC UA 与厂商 HTTP 驱动保留节点、质量码、签名、厂商消息号等协议字段。
- 已重新运行定向测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=EapGatewayServiceTest,RolePermissionServiceTest,AuditFailureResolverTest,PilotMesServiceTest" test` 通过，当前 `Tests run: 49, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行后端全量测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，当前 `Tests run: 137, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行 Flyway 静态验收：`powershell -ExecutionPolicy Bypass -File tools\verify-flyway-migrations.ps1` 通过，识别 `V1.1-V1.29` 共 29 个迁移文件。
- 已重新运行前端生产构建和后端打包：`npm.cmd run build`、`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-DskipTests" "-Dspring-boot.repackage.skip=true" package` 均通过。

## 2026-06-07 增量：EAP 网关健康检查与运维履历
- 新增 `V1.30__Add_Equipment_Gateway_Health_Checks.sql`，建立 `equipment_gateway_health_check`，保存检查单号、网关、协议、驱动、Endpoint、检查类型、PASS/WARN/FAIL 结果、延迟、错误说明、请求/响应快照和检查人。
- `EapProtocolDriver` 新增 `checkHealth` 边界，模拟 HTTP 返回可达检查，SECS/GEM、OPC UA、厂商 HTTP 在未真机联调时明确返回 WARN，不伪装真实设备已接通。
- `EapGatewayService` 新增健康检查执行与履历查询，检查结果会联动网关状态：PASS -> `CONNECTED`，WARN -> `DEGRADED`，FAIL -> `DISCONNECTED`，并写 `EQUIPMENT_GATEWAY_HEALTH_CHECK` 审计。
- 新增 `POST /api/v1/equipment/gateways/{gatewayCode}/health-check` 和 `GET /api/v1/equipment/gateway-health-checks`；RBAC 沿用 `equipment:eap-gateway`，失败审计已覆盖健康检查写接口。
- 前端设备页新增“EAP 网关健康检查履历”面板和“健康检查”按钮，支持按当前网关刷新检查履历，显示结果、延迟和错误说明。
- 已重新运行定向测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=EapGatewayServiceTest,RolePermissionServiceTest,AuditFailureResolverTest" test` 通过，当前 `Tests run: 40, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行后端全量测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，当前 `Tests run: 141, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行 Flyway 静态验收：`powershell -ExecutionPolicy Bypass -File tools\verify-flyway-migrations.ps1` 通过，识别 `V1.1-V1.30` 共 30 个迁移文件。
- 已重新运行前端生产构建：`npm.cmd run build` 通过，仍仅有既有第三方 pure annotation 和 chunk size warning。

## 2026-06-07 增量：AI模型配置与RAG证据可信度
- 新增 `V1.31__Add_Ai_Model_Config_And_Rag_Evidence.sql`，建立 `ai_model_config`，并扩展 `ai_report_record` 的模型供应方、模型模式、配置编码、检索策略、证据数量、最高证据分、证据等级、依据不足标志和模型配置快照字段。
- `ai_kb_chunk` 新增检索策略、embedding状态、embedding模型、embedding引用和索引时间字段，为后续 pgvector 或外部向量服务接入预留边界。
- 新增 `AiModelConfigService`、`AiModelConfig`、`AiModelConfigMapper`，提供良率日报、设备异常分析、SOP问答的试点模型配置读取；数据库不可用时返回本地 fallback 配置，不阻塞演示链路。
- `AiKnowledgeService.ask` 已从“有引用即可信”升级为“引用数量 + 最高证据分 + 证据等级 + 依据不足判断”：低于 0.65 的弱命中会返回引用但标记 `INSUFFICIENT`，避免泛泛关键词被当成可靠答案。
- `PilotMesService` 的 AI 良率日报、设备异常分析和 SOP 问答已写入模型配置快照和证据质量元数据；AI 输出继续保持只读辅助建议，不自动派工、停机或放行。
- 新增 `GET /api/v1/ai/model-configs`，前端 AI 页面展示模型配置、模式、检索策略、RAG证据等级、引用数量和最高证据分。
- 已重新运行定向测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=AiKnowledgeServiceTest,AiModelConfigServiceTest,AiRecordServiceTest,PilotMesServiceTest,PilotMesFlowIntegrationTest" test` 通过，当前 `Tests run: 20, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行后端全量测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，当前 `Tests run: 145, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行 Flyway 静态验收：`powershell -ExecutionPolicy Bypass -File tools\verify-flyway-migrations.ps1` 通过，识别 `V1.1-V1.31` 共 31 个迁移文件。
- 已重新运行前端生产构建：`npm.cmd run build` 通过，仍仅有既有第三方 pure annotation 和 chunk size warning。
- 已重新运行后端打包：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-DskipTests" "-Dspring-boot.repackage.skip=true" package` 通过。

## 2026-06-07 增量：AI报告留痕查询闭环
- `AiRecordService` 新增 AI 报告留痕列表和详情查询能力，列表返回报告编号、业务范围、模型模式、检索策略、证据等级、依据不足状态、创建人和时间；详情接口返回解析后的输入快照、输出JSON和模型配置快照。
- 新增 `GET /api/v1/ai/report-records` 和 `GET /api/v1/ai/report-records/{reportNo}`，支持按报告类型、业务编号、证据等级和依据不足标志过滤，默认按创建时间倒序返回最近 80 条。
- 前端 AI 页面“AI 报告留痕”表已从静态数据升级为真实接口数据；生成良率日报或 SOP 问答后会刷新 `ai_report_record` 查询结果，保留开发 fallback 仅用于接口不可用场景。
- 已重新运行定向测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=AiRecordServiceTest,PilotMesServiceTest" test` 通过，当前 `Tests run: 16, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行后端全量测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，当前 `Tests run: 147, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行 Flyway 静态验收：`powershell -ExecutionPolicy Bypass -File tools\verify-flyway-migrations.ps1` 通过，识别 `V1.1-V1.31` 共 31 个迁移文件。
- 已重新运行前端生产构建：`npm.cmd run build` 通过，仍仅有既有第三方 pure annotation 和 chunk size warning。
- 已重新运行后端打包：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-DskipTests" "-Dspring-boot.repackage.skip=true" package` 通过。

## 2026-06-07 增量：AI知识库索引任务履历
- 新增 `V1.32__Add_Ai_Kb_Index_Jobs.sql`，建立 `ai_kb_index_job`，保存索引任务号、范围文档、检索策略、embedding模型、目标/完成切片数、边界说明、触发人和开始/结束时间。
- 新增 `AiKbIndexService`、`AiKbIndexJob`、`AiKbIndexJobMapper`，支持同步触发 `KEYWORD_FALLBACK` 重建和 `PGVECTOR_READY` 元数据标记；后者只把切片置为 `VECTOR_PENDING`，明确不生成真实 embedding、不执行 pgvector 相似度查询。
- `AiKnowledgeService.documents` 已返回文档级索引状态、关键词已索引切片数、向量待联调切片数、未索引切片数和最近索引时间，便于前端和验收查看。
- 新增 `GET /api/v1/ai/kb/index-jobs` 和 `POST /api/v1/ai/kb/index-jobs`；写操作纳入 `ai:kb-index` 权限，默认管理员、质量工程师、工艺工程师和设备工程师可触发，并写 `AI_KB_INDEX` 审计。
- 前端 AI 页面新增知识库索引状态、重建关键词索引、标记向量待联调和索引任务履历表；接口不可用时仍保留开发 fallback。
- 已重新运行定向测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=AiKbIndexServiceTest,AiKnowledgeServiceTest,RolePermissionServiceTest,PilotMesServiceTest" test` 通过，当前 `Tests run: 31, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行后端全量测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，当前 `Tests run: 150, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行 Flyway 静态验收：`powershell -ExecutionPolicy Bypass -File tools\verify-flyway-migrations.ps1` 通过，识别 `V1.1-V1.32` 共 32 个迁移文件。
- 已重新运行前端生产构建：`npm.cmd run build` 通过，仍仅有既有第三方 pure annotation 和 chunk size warning。
- 已重新运行后端打包：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-DskipTests" "-Dspring-boot.repackage.skip=true" package` 通过。

## 2026-06-07 增量：ERP模拟工单下发与性能冒烟脚本
- 新增 `ErpOrderAdapterService`，模拟 ERP 工单下发到 `prod_order`，支持显式 `orders` 数组和 `count` 自动生成两种模式；单批最多 1000 条，先批量查重再写入，重复工单跳过。
- 新增 `POST /api/v1/adapters/erp/orders`，默认创建 `CREATED` 工单，不自动释放 Lot；成功写 `ERP_ORDER_IMPORT` 审计，失败路径纳入 `AuditFailureResolver`。
- RBAC 已把 ERP 工单导入映射到 `order:create` 权限：计划员可调用，操作员不可调用。
- 前端 API 封装新增 `importErpOrders`，后续可接入工单导入页面或演示工具。
- 新增 `tools/run-pilot-performance-smoke.ps1`，用于运行中环境的性能冒烟：登录、ERP 1000 条工单导入、工单列表 P95、Lot 列表 P95、良率看板 P95、Lot 追溯 P95。
- 新增 `ErpOrderAdapterServiceTest`，覆盖 1000 条模拟工单导入不失败、重复工单跳过和超过 1000 条拒绝；同步补充 Pilot 聚合、RBAC 和失败审计映射测试。
- 已重新运行定向测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=ErpOrderAdapterServiceTest,PilotMesServiceTest,PilotMesFlowIntegrationTest,RolePermissionServiceTest,AuditFailureResolverTest" test` 通过，当前 `Tests run: 50, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行后端全量测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，当前 `Tests run: 155, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行 Flyway 静态验收：`powershell -ExecutionPolicy Bypass -File tools\verify-flyway-migrations.ps1` 通过，识别 `V1.1-V1.32` 共 32 个迁移文件。
- 已重新运行前端生产构建：`npm.cmd run build` 通过，仍仅有既有第三方 pure annotation 和 chunk size warning。
- 已重新运行后端打包：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-DskipTests" "-Dspring-boot.repackage.skip=true" package` 通过。
- 已完成性能冒烟脚本语法校验；未在本轮执行 P95 实测，因为需要运行中的后端服务和数据库。

## 2026-06-07 增量：MRB会议纪要版本管理
- 新增 `V1.33__Add_Mrb_Minutes_Versions.sql` 和 `quality_mrb_minutes`，按 `mrb_no + version_no` 管理会议纪要正文、摘要、行动项、风险说明、编辑人、编辑时间、变更原因和请求快照。
- 新增 `GET /api/v1/quality/mrb-records/{mrbNo}/minutes` 和 `POST /api/v1/quality/mrb-records/{mrbNo}/minutes`；手动追加纪要会生成下一版本并写 `MRB_MINUTES_CREATE` 审计。
- `QualityService.reviewException` 和 `closeException` 已支持在请求中携带 `minutesContent`、`meetingMinutes`、`minutes` 或 `content` 自动生成纪要版本；MRB记录返回体补充 `minutesVersionCount` 和 `latestMinutes`。
- 前端质量页新增 MRB会议纪要输入区，复判/关闭动作会随请求提交纪要正文；异常卡片显示纪要版本数。
- RBAC 已明确 MRB纪要写接口权限，QE/PE/EE 等 MRB参与角色可写，操作员不可写；失败审计新增 `MRB_MINUTES_CREATE` 映射。
- 已重新运行定向测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=QualityServiceTest,RolePermissionServiceTest,AuditFailureResolverTest,PilotMesFlowIntegrationTest" test` 通过，当前 `Tests run: 45, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行后端全量测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，当前 `Tests run: 158, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行 Flyway 静态验收：`powershell -ExecutionPolicy Bypass -File tools\verify-flyway-migrations.ps1` 通过，识别 `V1.1-V1.33` 共 33 个迁移文件。
- 已重新运行前端生产构建：`npm.cmd run build` 通过，仍仅有既有第三方 pure annotation 和 chunk size warning。

## 2026-06-07 增量：MRB会签SLA与升级策略
- 新增 `V1.34__Add_Mrb_Approval_Sla_Escalation.sql`，为 `quality_mrb_approval_task` 增加 `sla_level`、`sla_hours`、`escalation_role`、`escalated_to`、`escalated_time`、`escalation_reason`、`escalation_count` 和 SLA/升级索引。
- `QualityService` 已按处置动作、风险等级和审批角色计算会签 SLA：P1/报废走更短 SLA，QE/PE/EE 可形成不同到期时间；`ESCALATED` 仍视为未完成会签，关闭异常时会被拦截。
- 新增 `POST /api/v1/quality/mrb-approvals/refresh-sla`，可按 MRB 单号或异常单号刷新逾期会签并升级到质量/工艺/设备主管，写入 `MRB_APPROVAL_ESCALATE` 审计。
- RBAC 新增 `quality:mrb-escalate` 按钮权限，QE/PE/EE 默认具备 SLA 升级能力，操作员不可执行；失败审计新增 SLA 升级接口映射。
- 前端质量页展示会签 SLA 等级、剩余时间/逾期状态、升级对象，并提供“刷新 SLA”操作按钮，保留浅色工作台风格。
- 已重新运行定向测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=QualityServiceTest,RolePermissionServiceTest,AuditFailureResolverTest" test` 通过，当前 `Tests run: 47, Failures: 0, Errors: 0, Skipped: 0`。
- 已重新运行后端全量测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，该轮 `Tests run: 161, Failures: 0, Errors: 0, Skipped: 0`；当前最新全量测试已升级到 188 项通过。
- 已重新运行前端生产构建：`npm.cmd run build` 通过，仅有既有第三方 pure annotation 和 chunk size 警告。
- 该轮已重新运行 Flyway 静态验收：`powershell -ExecutionPolicy Bypass -File tools\verify-flyway-migrations.ps1` 通过，当时识别 `V1.1-V1.34` 共 34 个迁移文件；当前最新验收已升级到 `V1.41`。

## 2026-06-07 增量：前端验收契约与关键 API 接线
- 新增 `smartdisplay-mes-ui/scripts/verify-frontend-contract.mjs`，不引入新依赖，静态检查关键路由、登录守卫、请求拦截、`/api/v1` API 封装、RBAC 菜单/按钮权限和关键页面的 API/权限接线。
- 前端 `package.json` 新增 `verify:frontend-contract` 命令，作为浏览器 E2E 依赖未安装前的可重复前端验收门槛；该脚本不替代真实浏览器 E2E。
- 前端 API 封装补齐 `getSystemUsers` 和 `publishRecipe`，覆盖计划中的 `/api/v1/system/users` 与 `POST /api/v1/recipes/{id}/publish`；主数据页 Recipe 发布按钮已接入真实接口，系统页已读取用户列表作为系统指标来源。
- 已运行 `npm.cmd run verify:frontend-contract` 通过，当前 `Frontend contract passed: 238 checks`。
- 已重新运行前端生产构建：`npm.cmd run build` 通过，仍仅有既有第三方 pure annotation 和 chunk size 警告。

## 2026-06-08 增量：前端生产 mock fallback 收口
- 新增 `smartdisplay-mes-ui/src/utils/devFallback.js`，并由 `vite.config.js` 的 `__DEV_MOCK_FALLBACK__` 编译期常量统一控制开发样例数据开关：默认开发模式启用、生产模式关闭，生产环境如需临时演示必须显式设置 `VITE_ENABLE_MOCK_FALLBACK=true` 后重新构建。
- 工单、执行、总览、主数据、质量、设备、物料、追溯、AI、系统页面的初始 mock 数据和接口失败 fallback 已改为开发环境限定；生产环境接口失败时不再静默展示 `fallback*` 样例生产数据。
- 主数据页避免在无真实 BOM 时使用 fallback BOM 发起变更；物料页避免在生产环境默认选中 fallback 批次号；设备页避免默认选中 fallback 设备和 EAP 网关。
- `verify-frontend-contract.mjs` 已增加 fallback 约束：有 fallback 的关键页面必须导入统一工具、必须走 `__DEV_MOCK_FALLBACK__`、`devFallback` 或 `isDevFallbackEnabled`，禁止直接 `ref(fallback*)` 初始化，禁止 raw fallback warning。
- 已运行 `npm.cmd run verify:frontend-contract` 通过，当前 `Frontend contract passed: 266 checks`。
- 已重新运行前端生产构建：`npm.cmd run build` 通过，仍仅有既有第三方 pure annotation 和 chunk size 警告。
- 本轮曾受 Windows `Path/PATH` 重复环境变量和浏览器沙箱刷新异常影响，真实浏览器 E2E 当时作为后续缺口保留；该缺口已在后续零依赖 CDP 脚本中补齐。

## 2026-06-08 增量：前端生产包样例标识剥离
- `vite.config.js` 新增 `__DEV_MOCK_FALLBACK__` 编译期常量：默认开发模式启用，生产模式关闭；离线演示需显式设置 `VITE_ENABLE_MOCK_FALLBACK=true` 后重新构建。
- `src/utils/devFallback.js` 改为读取编译期常量，关键页面的初始 fallback 数据改为 `__DEV_MOCK_FALLBACK__ ? ... : ...` 分支，使生产构建可裁剪样例数组和样例业务字符串。
- 清理执行、订单、主数据、设备、物料、追溯和系统页面中会进入生产包的演示默认值；生产环境不再默认携带样例 Lot、工单、设备、Recipe、SOP、COA 编号。
- 新增 `smartdisplay-mes-ui/scripts/verify-production-bundle-clean.mjs` 和 `npm.cmd run verify:production-bundle`，扫描 `dist/assets/*.js` 中典型 mock/fallback 样例业务标识，作为生产构建后的自动验收门槛。
- 已运行 `npm.cmd run verify:frontend-contract` 通过，当前 `Frontend contract passed: 266 checks`。
- 已运行 `npm.cmd run build` 通过，仍仅有既有第三方 pure annotation 和 chunk size warning。
- 已运行 `npm.cmd run verify:production-bundle` 通过，扫描 14 个 JS 产物未发现典型 mock/fallback 样例业务标识。
- 真实浏览器 E2E 已在后续零依赖 CDP 脚本中补齐，最新报告见 `docs/SmartDisplay-MES-browser-e2e-20260608-055759.md`。

## 2026-06-08 增量：真实浏览器前端 E2E
- 新增 `smartdisplay-mes-ui/scripts/run-browser-e2e.mjs` 和 `npm.cmd run e2e:browser`，使用本机 Chrome/Edge + CDP + Node 原生 WebSocket 运行，不新增 Playwright/Cypress 依赖。
- E2E 覆盖登录、顶部导航和权限菜单、工单创建、工单释放并生成 Lot、执行页选中 Lot 后通过 UI 完成 Track In/Out、质量 MRB/缺陷证据、物料 V1.38 库位任务操作台、库位任务 `CREATED -> ASSIGNED -> DONE` 与 `CREATED -> CANCELLED` 状态流、Lot 追溯查询、AI 良率报告生成留痕和系统审计入口。
- 修正执行页选中 Lot 的 UI 交互：表格行点击会稳定选中目标 Lot，Track In 使用当前 Lot 的 Route 工序与设备能力映射，Track Out 优先处理被选中的 `PROCESSING` Lot。
- 修正 E2E 操作定位：Track In/Out 改为明确点击按钮，避免误点侧边栏 `Track In / Out` 导航；物料页库位任务操作入口改为等待异步列表渲染后再断言。
- 已运行 `npm.cmd run e2e:browser` 通过，10 步全部 PASS，Console/Network 错误数为 0；最新报告写入 `docs/SmartDisplay-MES-browser-e2e-20260608-055759.md/json`。

## 2026-06-08 增量：Flyway 全新库迁移演练
- 新增 `tools/run-flyway-rehearsal.ps1`，用于生产变更前的数据库迁移演练：先执行迁移静态验收，再重新打包后端 exec jar，启动临时 PostgreSQL 容器，从全新空库运行 Spring Boot + Flyway 自动迁移。
- 演练脚本会校验最新迁移版本、应用启动状态、public 表数量、关键种子用户、Route Step 种子数据，并执行 `pg_dump/pg_restore` 到恢复库，验证备份恢复后的 `flyway_schema_history` 仍为最新版本。
- 正式演练已运行 `powershell -ExecutionPolicy Bypass -File tools\run-flyway-rehearsal.ps1 -StartupTimeoutSec 180` 通过：迁移文件 `V1.1-V1.38` 共 38 个，临时库最新版本 `V1.38 Add Material Location Task Workflow`，52 张 public 表、7 个种子用户、16 条 Route Step，恢复库最新版本仍为 `V1.38`；后续 V1.39/V1.40/V1.41 已通过迁移静态验收，V1.39 已完成 Docker 容器数据库复验。
- 演练报告写入 `docs/SmartDisplay-MES-flyway-rehearsal-20260608-052419.md/json`；脚本失败时会保留 Flyway 历史尾部和 Java 启动日志尾部，便于定位迁移失败点。

## 2026-06-08 增量：性能验收脚本工程化
- `tools/run-pilot-performance-smoke.ps1` 已从控制台采集脚本升级为可交付验收脚本：支持订单列表、Lot 列表、良率看板、Lot 追溯四类 P95 阈值参数，默认对应计划中的 500ms、500ms、2000ms、1000ms。
- 脚本新增 Min/Avg/P95/Max 统计、ERP 导入汇总校验、Markdown 报告输出、JSON 报告输出和失败退出码；默认报告写入 `docs/SmartDisplay-MES-performance-smoke-*.md/json`，也可通过 `-ReportPath`、`-JsonPath` 指定。
- 脚本保持 ASCII，避免 Windows PowerShell 5.1 在无 BOM UTF-8 场景下解析失败；中文验收说明保留在项目文档中。
- 已运行 PowerShell Parser 静态解析通过：`tools\run-pilot-performance-smoke.ps1`。
- 2026-06-08 本地代理恢复后已执行真实 P95 冒烟实测：经 `http://127.0.0.1:8888/api` 反代导入 1000 条模拟工单成功，订单列表 P95 15.67ms、Lot 列表 P95 13.72ms、良率看板 P95 17.28ms、Lot 追溯 P95 60.08ms；Markdown/JSON 报告已写入 `docs/SmartDisplay-MES-performance-smoke-20260608-030053.*`。

## 2026-06-08 增量：多轮稳定性能基线
- 新增 `tools/run-pilot-performance-baseline.ps1`，复用单轮性能冒烟脚本连续执行多轮采样，自动汇总各检查项的平均 P95、最小/最大 P95、标准差、P95 漂移比例、稳定性告警和失败退出码。
- 脚本支持 `-Rounds`、`-Samples`、`-ImportCount`、四类接口阈值、`-MaxP95DriftPercent` 和 `-FailOnStabilityWarning`，默认每轮导入 1000 条 ERP 模拟工单并对订单、Lot、良率看板和 Lot 追溯做 20 次采样。
- 已运行 PowerShell Parser 静态解析通过：`tools\run-pilot-performance-baseline.ps1`。
- 已执行默认三轮基线：`powershell -ExecutionPolicy Bypass -File tools\run-pilot-performance-baseline.ps1` 通过；3 轮各导入 1000 条模拟工单，订单列表最大 P95 8.59ms、Lot 列表最大 P95 7.32ms、良率看板最大 P95 13.25ms、Lot 追溯最大 P95 20.01ms，全部低于验收阈值并标记 `STABLE`。
- 汇总报告写入 `docs/SmartDisplay-MES-performance-baseline-20260608-061856.md/json`，单轮明细写入 `docs/performance-baseline-rounds/SmartDisplay-MES-performance-smoke-20260608-061856-round01..03.*`。

## 2026-06-08 增量：真实数据库 API 闭环复验
- 新增 `tools/run-real-db-api-flow.ps1`，在运行中的 Docker Compose PostgreSQL 与后端 API 上执行端到端闭环，不只验证 HTTP 返回，也通过容器内 PostgreSQL 查询校验关键数据真实落库。
- 脚本覆盖登录、Flyway 最新版本、工单创建与落库、工单释放生成 Lot、CLEAN Track In/Out、COATING 参数超限 NG 自动 Hold、质量/异常/Hold 落库、Release、追溯、看板、AI 良率报告落库，以及审计 API 与数据库双重校验。
- 已运行 `powershell -ExecutionPolicy Bypass -File tools\run-real-db-api-flow.ps1` 通过；本轮业务号为工单 `MOINT20260608060901707`、Lot `LOTINT20260608060901707-001`、AI 报告 `AIR-YIELD-1780870145728`。
- 复验报告写入 `docs/SmartDisplay-MES-real-db-api-flow-20260608-060901.md/json`；该报告已同步纳入 `SmartDisplay-MES测试报告.md` 和 `SmartDisplay-MES验收清单.md`。

## 2026-06-08 增量：供应商准入与8D整改闭环
- 新增 `V1.40__Add_Supplier_Qualification_8d.sql`，包含 `md_supplier` 供应商准入主数据、`supplier_corrective_action` 8D整改单、供应商准入索引、8D来源索引和首版试点种子数据。
- 物料服务新增供应商列表、准入评估、8D创建、8D关闭能力；供应商准入状态由主数据、批次绩效、IQC PASS/HOLD/NG、风险批次和未关闭8D共同决定。
- 来料IQC判定为 `NG` 或 `HOLD` 时自动创建供应商8D整改单，并按严重度将供应商准入状态降级到 `CONDITIONAL/BLOCKED`；PASS 批次保持原有放行逻辑。
- 新增 `/api/v1/material/suppliers`、`/api/v1/material/suppliers/{supplierCode}/qualification/evaluate`、`/api/v1/material/suppliers/corrective-actions`、`/api/v1/material/suppliers/corrective-actions/{actionNo}/close` 等接口。
- RBAC 新增 `material:supplier-manage` 按钮权限，QE/ADMIN 可执行供应商准入和8D处置；失败审计新增 `SUPPLIER_QUALIFICATION_EVALUATE`、`SUPPLIER_8D_CREATE`、`SUPPLIER_8D_CLOSE` 映射。
- 前端物料页新增“供应商准入/绩效”和“供应商8D整改”工作区，接入真实接口并保留开发 fallback；全局按钮、导航和物料页局部表格继续收敛到 Codex app 风格的浅色、低饱和、细边框工作台。
- 已运行定向后端测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=MaterialServiceTest,RolePermissionServiceTest,AuditFailureResolverTest" test` 通过，`Tests run: 73, Failures: 0, Errors: 0, Skipped: 0`。
- 已运行后端全量测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，`Tests run: 183, Failures: 0, Errors: 0, Skipped: 0`。
- 已运行 Flyway 静态验收：`powershell -ExecutionPolicy Bypass -File tools\verify-flyway-migrations.ps1` 通过，识别 `V1.1-V1.40` 共 40 个迁移文件。
- 已运行前端验收：`npm.cmd run verify:frontend-contract` 通过，`Frontend contract passed: 294 checks`；`npm.cmd run build` 通过；`npm.cmd run verify:production-bundle` 通过，扫描 14 个 JS 产物。
- 已运行浏览器 E2E：`npm.cmd run e2e:browser` 通过，10 步全部 PASS，报告 `docs/SmartDisplay-MES-browser-e2e-20260608-072928.md`。
- 已生成物料页视觉验证截图：`docs/material-codex-style-desktop.png` 和 `docs/material-codex-style-suppliers.png`；供应商区无页面级横向溢出、无按钮文字溢出。

## 2026-06-08 增量：供应商月度评分趋势
- 新增 `GET /api/v1/material/suppliers/trends`，基于 `material_batch.received_time`、`material_incoming_inspection.inspection_time` 和 `supplier_corrective_action.created_time/due_time` 聚合最近 6 个月供应商评分趋势。
- 评分口径复用供应商绩效基础公式，并额外纳入未关闭 8D、超期 8D 和 HIGH/CRITICAL 8D 风险；接口返回供应商、月份、批次、IQC PASS/HOLD/NG、8D 数量、评分、风险等级和结构化趋势点。
- 前端物料页新增“供应商月度评分趋势”工作区，使用浅色细边框和低饱和柱形展示最近 6 个月趋势，接入真实接口并保留开发 fallback。
- 新增 `MaterialServiceTest.supplierScoreTrendsShouldAggregateMonthlyIqcAnd8dRisk`，覆盖批次风险、IQC NG/HOLD、未关闭超期 8D 对趋势评分和风险等级的影响。
- 已运行后端定向测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=MaterialServiceTest" test` 通过，`Tests run: 36, Failures: 0, Errors: 0, Skipped: 0`。
- 已运行后端全量测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，`Tests run: 184, Failures: 0, Errors: 0, Skipped: 0`。
- 已运行前端验收：`npm.cmd run verify:frontend-contract` 通过，`Frontend contract passed: 296 checks`；`npm.cmd run build` 通过；`npm.cmd run verify:production-bundle` 通过，扫描 14 个 JS 产物。

## 2026-06-08 增量：供应商准入周期复审任务
- 新增 `V1.41__Add_Supplier_Qualification_Review_Tasks.sql`，包含 `supplier_qualification_review_task`、供应商+状态+到期时间索引、到期任务索引和首版高风险供应商复审种子数据。
- 物料服务新增供应商准入复审任务列表、创建和决策能力；同一供应商同一复审类型存在未关闭任务时会阻止重复创建，决策通过后可按建议准入状态和风险等级回写供应商主数据。
- 新增 `GET /api/v1/material/suppliers/qualification-reviews`、`POST /api/v1/material/suppliers/{supplierCode}/qualification-reviews`、`POST /api/v1/material/suppliers/qualification-reviews/{taskNo}/decision`。
- 前端物料页新增“供应商准入复审”工作区，展示复审任务、建议准入、风险等级、到期时间，并支持创建、通过和驳回复审任务。
- RBAC 复用 `material:supplier-manage` 权限，失败审计新增 `SUPPLIER_REVIEW_CREATE` 和 `SUPPLIER_REVIEW_DECIDE` 映射。
- 已运行后端定向测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-Dtest=MaterialServiceTest" test` 通过，`Tests run: 38, Failures: 0, Errors: 0, Skipped: 0`。
- 已运行后端全量测试：`mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test` 通过，`Tests run: 188, Failures: 0, Errors: 0, Skipped: 0`。
- 已运行前端验收：`npm.cmd run verify:frontend-contract` 通过，`Frontend contract passed: 302 checks`；`npm.cmd run build` 通过；`npm.cmd run verify:production-bundle` 通过，扫描 14 个 JS 产物。
- 已运行 Flyway 静态验收：`powershell -ExecutionPolicy Bypass -File tools\verify-flyway-migrations.ps1` 通过，识别 `V1.1-V1.41` 共 41 个迁移文件。
# 2026-06-08 增量：Lot 页 Rework/Scrap 操作入口

- Lot 管理页新增 Rework 与 Scrap 操作按钮，按 `lot:rework`、`lot:scrap` 按钮权限控制，并仅允许在 `HOLD` 且 `holdFlag=1` 的 Lot 上触发。
- 新增 Rework 弹窗：选择有效返工 Route 和返工起始工序，填写原因与操作人，提交到 `POST /api/v1/lots/{lotNo}/rework`。
- 新增 Scrap 弹窗：填写报废原因、责任模块、审批人与操作人，并要求输入 `SCRAP:{lotNo}` 完成二次确认，提交到 `POST /api/v1/lots/{lotNo}/scrap`。
- 旧 Lot 页 API 封装统一切换到 `/api/v1/lots` 试点接口，Track In 弹窗的工序与设备选项也统一读取 `/api/v1/master/**`。
- 前端 `.gitignore` 增加 `.vite`，避免本地构建缓存进入仓库。
- 已复验：`npm.cmd run verify:frontend-contract` 通过 302 项检查；`npm.cmd run build` 通过，仅保留既有第三方 pure annotation 与 chunk size warning；`npm.cmd run verify:production-bundle` 通过，扫描 14 个 JS 产物。
