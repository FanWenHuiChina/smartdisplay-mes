# SmartDisplay MES 演示脚本

## 演示前准备

```bash
cd smartdisplay-mes-api
docker-compose up -d
```

- 前端工作台：`http://localhost:8888`
- 后端 Swagger：`http://localhost:8080/api/swagger-ui.html`
- 测试账号：`admin/planner/operator/qe/pe/ee`，密码均为 `123456`

## 5分钟版本

1. 登录系统
   - 使用 `admin / 123456` 登录。
   - 说明系统定位：单基地、单产线、模拟外部集成的显示行业 MES 试点。

2. 看生产总览
   - 进入“生产总览”，展示 WIP、良率、Hold、设备稼动、异常队列。
   - 强调看板来自 `/api/v1/dashboard/overview` 和质量/异常真实表优先数据。

3. 工单释放
   - 进入“计划与工单”，点击“释放工单”。
   - 展示工单释放后生成 Lot，后端写 `prod_order`、`prod_lot` 和 `sys_audit_log`。

4. Track In/Out
   - 进入“生产执行”，对 READY Lot 执行 Track In。
   - 说明校验链：Lot状态、Route下一站、设备状态、设备能力、Recipe、Hold、物料齐套、权限、班次、审计。
   - 对 PROCESSING Lot 执行 Track Out，说明参数快照进入过站记录。

5. 追溯与 AI
   - 进入“追溯分析”，查询 `LOT202406001`，展示工单、Route、过站、Hold、质量、物料、审计链路。
   - 进入“报表与AI”，先查看模型配置、模型模式和RAG证据等级，再生成良率日报，并问“蒸镀真空波动如何排查？”展示回答、引用来源、最高证据分和 AI 留痕。

## 15分钟版本

1. 系统定位与架构
   - 说明合规边界：参考公开显示行业流程和通用 MES 模型，不复刻任何企业内部系统。
   - 说明技术栈：Vue 3、Element Plus、Spring Boot 3、MyBatis-Plus、PostgreSQL、Flyway、Docker Compose。

2. 角色权限演示
   - 使用 `planner` 登录，释放工单。
   - 使用 `operator` 登录，执行 Track In/Out。
   - 使用 `qe` 登录，执行 Hold/Release。
   - 切换非授权角色执行写操作，展示 `403` 角色无权限提示。

3. 工单到 Lot
   - 在“计划与工单”查看工单池与释放校验。
   - 释放工单，讲清楚 Lot 状态机：`CREATED/READY/PROCESSING/HOLD/COMPLETED/REWORK/SCRAP`。

4. 生产执行闭环
   - 在“生产执行”执行 Track In。
   - 讲校验链和事务边界：任何校验失败都返回明确原因，不写半成品流转。
   - 执行 Track Out，讲参数快照、Recipe 上下限判断和下一站推进。

5. 质量异常闭环
   - 展示质量管理里的检验记录、异常队列和不良 TopN。
   - 说明 NG 或关键参数超限会生成 `quality_inspection`、`quality_defect_record`、`exception_event` 并自动 Hold Lot。
   - 说明 Release/Rework/Scrap 会写处置原因和审计，并在 MRB 履历中保存会议号、参与人、附件元数据和审批状态；P1、返工或报废会生成 QE/PE/EE 会签待办，按角色和风险计算 SLA，逾期可升级到责任主管，未通过或升级中禁止关闭异常。

6. 物料与载具闭环
   - 进入“物料与载具”，展示 BOM、批次、齐套、载具和消耗履历。
   - 说明 Track In 锁定关键物料，Track Out 生成 `material_consumption`。
   - 展示“库位任务 / 上架移库盘点”，执行一次整批移库或盘点任务，说明任务单、库存事务和审计会同步留痕。
   - 展示“来料质检 / COA 留痕”，说明 PASS 批次允许投料，NG/HOLD 批次会进入 HOLD 并被 Track In 物料校验拦截。
   - 展示“供应商准入复审”，创建或处理一条复审任务，说明系统会基于批次、IQC、8D 风险给出建议准入状态，并把决策留痕。

7. 追溯
   - 查询 `LOT202406001`。
   - 展示从工单、Route、设备、Recipe、参数、质量、Hold、返工、物料消耗到审计日志的证据链。

8. 设备自动化与 OEE
   - 在“标准节拍主数据”中查看或发布 COATING 标准节拍，说明旧 ACTIVE 版本会失效并写审计。
   - 进入“设备与自动化”，执行一次 EAP 状态上报和一次标准/实际节拍采样。
   - 查看 “EAP 网关连接”，演示模拟 HTTP 网关、SECS/GEM 预留网关、OPC UA 预留网关的连接状态；执行一次心跳或降级模拟。
   - 查看 “EAP 网关健康检查履历”，执行一次手动健康检查，说明模拟 HTTP 可返回 `PASS`，真实协议占位在未真机联调前返回 `WARN` 并写履历和审计。
   - 展示网关驱动编码、驱动模式、TLS开关、连接/读取超时，说明当前已有协议驱动边界和配置快照。
   - 查看 “EAP 网关消息履历”，执行一次 `STATUS` 或 `CYCLE` 模拟入站，说明消息先落履历，再由模拟适配器分发到设备服务。
   - 说明 SECS/GEM、OPC UA、厂商 HTTP 当前是占位驱动：可以做协议帧归一化与审计留痕，但尚未连接真实设备。
   - 展示设备状态历史、节拍采样履历和生产总览中的 OEE 性能率样本数，说明缺少节拍样本时系统才回退到状态估算。
   - 说明同类消息也可经 `/api/v1/adapters/eap/messages` 统一入口接入，当前是模拟适配器和网关占位，后续可替换真实 SECS/GEM、OPC UA 或厂商 HTTP 驱动。

9. AI 辅助
   - 生成 AI 良率日报。
   - 执行 SOP 问答。
   - 说明 AI 只做辅助分析，不自动派工、停机或放行；所有 AI 输入快照、Prompt 版本、模型配置快照、证据等级和输出 JSON 写入 `ai_report_record`。

10. 交付能力
   - 展示 `docker-compose.yml` 三服务：PostgreSQL、后端、前端。
   - 说明 Flyway 启动迁移和 `db/migration/V1.1-V1.41`。
