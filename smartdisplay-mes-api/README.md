# SmartDisplay MES API

## 项目简介

SmartDisplay MES（制造执行系统）- 参考显示行业公开资料、半导体/显示制造通用 MES 模型构建的生产级试点系统。

**核心功能：**
- ✅ Recipe配方管理（参数化管理，支持版本控制）
- ✅ Lot流转控制（Track In/Out校验）
- ✅ Hold/Release异常控制
- ✅ `/api/v1` 试点闭环接口
- ✅ ERP模拟工单下发、1000条试点工单批量导入和导入审计
- ✅ 质量检验、缺陷记录、异常事件与NG/参数超限自动Hold
- ✅ MRB复判/关闭履历、会议号、参与人、审批状态与附件元数据留痕
- ✅ MRB多角色会签待办、审批通过/驳回和关闭前会签校验
- ✅ MRB会签SLA、逾期升级、主管升级对象和审计留痕
- ✅ MRB会议纪要正文版本管理、摘要/行动项/风险说明和审计留痕
- ✅ BOM、物料批次、上料锁定、消耗追溯与载具绑定基础闭环
- ✅ WMS入库、冻结、解冻、退料、盘点、库存事务履历与并发批次锁
- ✅ WMS库位策略、容量校验、物料类别匹配、环境窗口和占用联动
- ✅ WMS库位任务：上架、整批移库、盘点任务、领取/完成/取消状态流、库存事务与审计留痕
- ✅ 来料IQC、COA/检验附件元数据留痕、供应商批次判定与批次质量状态联动
- ✅ 基于物料批次、来料IQC与8D记录的供应商绩效评分、月度评分趋势、准入评估、周期复审任务、8D整改闭环和物料页供应商工作区
- ✅ BOM变更审批、替代料策略、替代料验证报告附件、ECO跨部门会签和版本发布流程
- ✅ 设备事件、EAP参数采样、参数越限自动设备事件、PM任务和设备状态联动
- ✅ Recipe下发、EAP回读确认、Mismatch自动设备事件和命令履历
- ✅ 设备OEE拆解、计划/非计划停机原因TopN、状态历史、标准/实际节拍采样、事件关闭与时长审计
- ✅ EAP统一适配器占位和标准化消息入口（状态、节拍、参数、Recipe下发）
- ✅ 标准节拍主数据治理、ACTIVE版本发布、旧版本失效和节拍样本自动匹配
- ✅ EAP网关连接配置、心跳、消息履历、处理失败降级和真实协议驱动边界
- ✅ EAP协议驱动抽象、驱动能力列表、配置快照和协议帧归一化留痕
- ✅ EAP网关健康检查、检查履历、结果审计和连接状态联动
- ✅ JWT登录、接口鉴权和试点角色写权限控制
- ✅ 权限变更申请/审批/审计闭环、持久化热加载与数据范围 SQL 自动拼接
- ✅ 审计请求上下文留痕（请求方法、URI、客户端IP、User-Agent）
- ✅ 关键写接口失败审计与统一异常审计
- ✅ AI良率分析、设备异常分析、RAG问答结构化输出、模型配置边界、证据可信度和结果留痕
- ✅ AI报告留痕查询、输入/输出快照回看和前端审计表接口化
- ✅ AI知识库索引任务履历、关键词索引重建和 pgvector-ready 待联调边界标记
- ✅ Flyway版本化数据库迁移（`db/migration` 自动初始化）

**技术栈：**
- Spring Boot 3.1.5
- MyBatis-Plus 3.5.5
- PostgreSQL 15
- SpringDoc OpenAPI 3 (Swagger UI)

## 快速启动

### 1. 环境要求
- JDK 17+
- Maven 3.6+
- Docker & Docker Compose

### 2. Docker一键试运行
```bash
cd ..
docker compose up -d --build
```

启动后访问：
- 前端工作台：`http://localhost:8888`
- 后端Swagger：`http://localhost:8080/api/swagger-ui.html`
- PostgreSQL：`localhost:5433`

Docker Compose 会启动 PostgreSQL、后端和前端三类服务。后端启动时通过 Flyway 自动执行 `src/main/resources/db/migration` 下的版本化迁移；`init.sql` 仅作为一次性初始化参考，不再由 Docker Compose 自动挂载执行。

### 3. 本地开发运行
```bash
# 只启动数据库
docker-compose up -d postgres

# 启动后端
mvn clean install
mvn spring-boot:run
```

数据库迁移演练在根目录执行：

```bash
powershell -ExecutionPolicy Bypass -File tools/run-flyway-rehearsal.ps1 -StartupTimeoutSec 180
```

该脚本会重新打后端包，启动临时 PostgreSQL 容器，从全新空库运行 Flyway 到最新版本，并执行 `pg_dump/pg_restore` 恢复校验。最新通过报告见 `../docs/SmartDisplay-MES-flyway-rehearsal-20260608-052419.md`。

真实数据库 API 闭环复验在根目录执行：

```bash
powershell -ExecutionPolicy Bypass -File tools/run-real-db-api-flow.ps1
```

该脚本会在当前 Docker Compose PostgreSQL 和后端 API 上校验工单创建/释放、Lot Track In/Out、NG 自动 Hold、Release、追溯、看板、AI 报告和审计真实落库。最新通过报告见 `../docs/SmartDisplay-MES-real-db-api-flow-20260608-060901.md`。

性能验收和多轮基线在根目录执行：

```bash
powershell -ExecutionPolicy Bypass -File tools/run-pilot-performance-smoke.ps1
powershell -ExecutionPolicy Bypass -File tools/run-pilot-performance-baseline.ps1
```

单轮脚本验证 1000 条模拟工单导入和核心接口 P95 阈值；多轮基线脚本连续运行多轮并汇总 P95、标准差、漂移比例和稳定性告警。最新三轮基线报告见 `../docs/SmartDisplay-MES-performance-baseline-20260608-061856.md`。

前端本地开发在 `smartdisplay-mes-ui` 执行：

```bash
npm install
npm run verify:frontend-contract
npm run build
npm run verify:production-bundle
npm run e2e:browser
npm run dev
```

前端开发样例 fallback 默认仅在开发环境启用；生产构建不会静默展示 mock 生产数据，默认生产包会检查典型 mock/fallback 样例业务标识不进入 `dist/assets/*.js`。真实浏览器 E2E 默认访问 `http://127.0.0.1:8888`，需要 Docker Compose 或等价前后端服务已启动。若只为离线演示临时启用 fallback，需显式设置 `VITE_ENABLE_MOCK_FALLBACK=true` 后重新构建。

### 4. 访问Swagger UI
```
http://localhost:8080/api/swagger-ui.html
```

### 5. 登录并测试Recipe接口
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"pe","password":"123456"}' \
  | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

# 创建Recipe
curl -X POST http://localhost:8080/api/recipes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${TOKEN}" \
  -d '{
    "recipeCode": "RCP_TEST_001",
    "recipeName": "测试Recipe",
    "productCode": "AMOLED_65",
    "stepCode": "COATING",
    "equipmentCode": "COATER_01",
    "recipeVersion": "V1.0",
    "description": "测试用Recipe",
    "params": [
      {
        "paramName": "温度",
        "paramCode": "TEMP",
        "targetValue": 150,
        "upperLimit": 155,
        "lowerLimit": 145,
        "unit": "℃",
        "paramType": "TEMPERATURE",
        "isKeyParam": 1
      }
    ]
  }'

# 查询Recipe详情
curl -H "Authorization: Bearer ${TOKEN}" http://localhost:8080/api/recipes/1

# 查找有效Recipe
curl -H "Authorization: Bearer ${TOKEN}" "http://localhost:8080/api/recipes/search?productCode=AMOLED_65&stepCode=COATING&equipmentCode=COATER_01"
```

试点账号默认密码均为 `123456`：`admin` 管理员、`planner` 计划员、`operator` 操作员、`qe` 质量工程师、`pe` 工艺工程师、`ee` 设备工程师。

## 项目结构

```
smartdisplay-mes-api/
├── src/main/java/com/visionox/mes/
│   ├── SmartDisplayMesApplication.java    # 启动类
│   ├── common/                             # 通用组件
│   │   ├── Result.java                     # 统一响应
│   │   ├── BusinessException.java          # 业务异常
│   │   └── GlobalExceptionHandler.java     # 全局异常处理
│   ├── config/                             # 配置类
│   │   └── MybatisPlusConfig.java          # MyBatis-Plus配置
│   └── recipe/                             # Recipe模块
│       ├── entity/                         # 实体类
│       ├── mapper/                         # Mapper接口
│       ├── service/                        # 服务层
│       ├── controller/                     # 控制器
│       └── dto/                            # DTO
├── src/main/resources/
│   ├── application.yml                     # 应用配置
│   ├── db/migration/                       # Flyway版本化迁移
│   └── init.sql                            # 一次性初始化参考脚本
├── Dockerfile                              # 后端容器构建
└── docker-compose.yml                      # PostgreSQL/后端/前端编排
```

## 开发进度

### Week 1-2: Recipe模块 ✅
- [x] 数据库表设计
- [x] Recipe实体和Mapper
- [x] RecipeService业务逻辑
- [x] RecipeController REST接口
- [x] Swagger文档集成
- [x] Docker Compose环境

### 生产级试点主干 ✅
- [x] Lot实体设计
- [x] Track In校验逻辑（Lot状态、Route防跳站、设备状态、设备能力、Recipe、Hold、班次、物料齐套）
- [x] Track Out记录与NG自动Hold
- [x] Hold/Release机制
- [x] `/api/v1` 工单、Lot、执行、追溯、看板、AI试点接口
- [x] `/api/v1/adapters/erp/orders` ERP模拟工单下发和批量导入
- [x] Docker Compose三服务配置：PostgreSQL、后端、前端Nginx

### 下一阶段：质量与物料正式落库 🚧
- [x] quality_inspection / quality_defect_record 正式表
- [x] exception_event 异常事件表
- [x] Track Out 参数窗口判定与质量异常自动Hold
- [x] MRB复判、异常关闭、审批结论正式流程
- [x] MRB复判/关闭履历、会议号、参与人、审批状态和附件元数据
- [x] MRB多角色会签任务、审批通过/驳回和关闭前会签状态校验
- [x] MRB会签SLA、逾期升级、主管升级对象和升级审计
- [x] MRB会议纪要正文版本管理、手动追加版本和复判/关闭自动落版本
- [x] material batch / carrier / consume 正式表
- [x] Track In 关键物料齐套校验、批次锁定与 Track Out 消耗履历
- [x] WMS入库、冻结、解冻、退料、盘点、库存事务履历和并发库存锁策略
- [x] WMS库位策略：库位主数据、存储类型、物料类别、容量、环境窗口、优先级和占用联动
- [x] WMS库位任务：`MOVE`/`PUTAWAY`整批转移、`COUNT`盘点任务、`material_location_task`留痕、领取/完成/取消状态流和物料页操作台
- [x] 供应商批次来料质检、COA/检验附件元数据留痕和批次质量状态联动
- [x] 供应商绩效评分与趋势：批次、IQC PASS/HOLD/NG、8D记录、通过率、风险批次、评分、月度趋势和风险等级聚合
- [x] 供应商准入、复审与8D整改：供应商主数据、准入状态评估、周期复审任务创建/审批、IQC NG/HOLD 自动开8D、8D关闭和审计留痕
- [x] BOM替代料验证报告附件元数据：变更单、文件名、地址、校验摘要、附件角色和上传人
- [x] BOM/ECO跨部门会签：ECO包快照、风险等级、会签任务、会签通过前禁止发布和驳回阻断
- [x] sys_audit_log 审计表与关键动作落库
- [x] 审计请求上下文（请求方法、URI、客户端IP、User-Agent）
- [x] 关键写接口失败审计和统一异常审计
- [x] 工单创建/释放、Track In/Out、Hold/Release、Rework/Scrap 核心执行动作差异快照
- [ ] 批量操作差异快照和新增写接口审计映射持续维护

### 下一阶段：Route/BOM/RBAC/Flyway 🚧
- [x] Route正式表与防跳站校验
- [x] BOM正式表与物料齐套明细
- [x] BOM变更审批、替代料策略、替代料验证报告附件、ECO跨部门会签和版本发布流程
- [x] RBAC接口鉴权和试点角色写权限
- [x] 菜单级权限、按钮级权限、数据范围权限能力模型、前端裁剪和越权自动化测试
- [x] 权限变更申请/审批/审计闭环、运行期权限快照应用、启动恢复、手动重载和数据范围 SQL 自动拼接
- [x] Flyway依赖启用与迁移自动执行
- [x] Flyway迁移验收脚本、回滚策略和生产变更审批流程
- [x] V1.1核心生产表迁移脚本
- [x] V1.2核心试点种子数据
- [x] V1.6质量/异常表迁移脚本与种子数据
- [x] V1.7 BOM/物料/载具表迁移脚本与种子数据
- [x] V1.8试点角色账号种子数据
- [x] V1.11 MRB复判与异常处置字段迁移脚本
- [x] V1.12 权限变更申请单迁移脚本
- [x] V1.13-V1.15 Route调度、Lot/载具产线数据范围迁移脚本
- [x] V1.16 组织、产线和班次主数据迁移脚本
- [x] V1.17 审计请求上下文字段迁移脚本
- [x] V1.18 WMS库存事务迁移脚本
- [x] V1.19 BOM变更审批与替代料策略迁移脚本
- [x] V1.20 来料IQC与COA附件元数据迁移脚本
- [x] V1.21 MRB履历与附件元数据迁移脚本
- [x] V1.22 MRB多角色会签任务迁移脚本
- [x] V1.23 设备事件、PM与EAP参数采样迁移脚本
- [x] V1.24 Recipe下发与EAP回读确认迁移脚本
- [x] V1.25 设备停机原因与OEE字段迁移脚本
- [x] V1.26 设备状态历史与节拍采样迁移脚本
- [x] V1.27 标准节拍主数据迁移脚本
- [x] V1.28 EAP网关连接与消息履历迁移脚本
- [x] V1.29 EAP协议驱动配置与归一化快照迁移脚本
- [x] V1.30 EAP网关健康检查履历迁移脚本
- [x] V1.31 AI模型配置与RAG证据可信度迁移脚本
- [x] V1.32 AI知识库索引任务履历迁移脚本
- [x] V1.33 MRB会议纪要版本管理迁移脚本
- [x] V1.34 MRB会签SLA与升级策略迁移脚本
- [x] V1.35 BOM变更附件与替代料验证报告迁移脚本
- [x] V1.36 WMS库位策略与容量校验迁移脚本
- [x] V1.37 WMS库位任务迁移脚本
- [x] V1.38 WMS库位任务分步工作流迁移脚本
- [x] V1.39 BOM/ECO跨部门会签任务迁移脚本
- [x] V1.40 供应商准入与8D整改闭环迁移脚本
- [x] V1.41 供应商准入周期复审任务迁移脚本

### 下一阶段：设备与自动化工程化 🚧
- [x] 设备事件正式表、查询和创建接口
- [x] EAP参数采样正式表、模拟上报接口和参数越限自动事件
- [x] PM任务正式表、查询和完成接口
- [x] Recipe下发/回读命令履历和Mismatch自动报警
- [x] OEE停机原因模型、事件关闭和前端OEE拆解
- [x] EAP状态上报、设备状态历史、标准/实际节拍采样和OEE性能率样本口径
- [x] EAP统一适配器接口、模拟适配器和 `/api/v1/adapters/eap/messages` 标准化消息入口
- [x] 标准节拍主数据、ACTIVE发布、旧版本失效和节拍样本自动匹配
- [x] EAP网关连接配置、心跳、消息履历和失败降级状态
- [x] EAP协议驱动抽象、驱动能力列表、配置快照、SECS/GEM/OPC UA/厂商HTTP归一化边界
- [x] EAP网关健康检查、检查履历、WARN/FAIL留痕和状态联动
- [x] 前端设备页接口驱动
- [ ] 真实SECS/GEM、OPC UA或厂商HTTP协议驱动真机联调
- [ ] 毫秒级设备状态采集

### 下一阶段：AI工程化 🚧
- [x] ai_report_record 正式表
- [x] AI良率日报、设备异常分析、SOP问答输入快照、Prompt模板版本、模型和输出JSON留痕
- [x] ai_model_config 模型运行配置、模型模式、外部模型影子配置占位和配置快照
- [x] RAG关键词检索证据等级、最高证据分、依据不足标志和报告留痕
- [x] AI报告留痕查询接口和前端审计表真实数据接入
- [x] SOP/设备手册/质量标准文档表、切片表、种子切片、关键词检索与引用返回
- [x] SOP文件上传导入和自动切片
- [x] 知识库索引任务履历、关键词索引重建、pgvector-ready 待联调状态标记和审计
- [ ] 真实 pgvector 向量检索、真实外部模型联调和引用召回率评估

### 下一阶段：交付与验收 🚧
- [x] 后端 Dockerfile
- [x] 前端 Dockerfile + Nginx `/api` 反向代理
- [x] Docker Compose整合 PostgreSQL、后端、前端
- [x] 5分钟/15分钟端到端演示脚本：`../docs/SmartDisplay-MES演示脚本.md`
- [x] ER图、业务流程图和验收清单：`../docs/SmartDisplay-MES流程图与ER图.md`、`../docs/SmartDisplay-MES验收清单.md`
- [x] 单元测试、服务级闭环测试和 Docker Compose 配置验收报告：`../docs/SmartDisplay-MES测试报告.md`
- [x] 性能冒烟脚本：`../tools/run-pilot-performance-smoke.ps1`，支持 P95 阈值判定、Markdown/JSON 报告和失败退出码
- [x] 多轮性能基线脚本：`../tools/run-pilot-performance-baseline.ps1`，支持多轮采样、P95 漂移、稳定性告警和汇总报告
- [x] 前端静态契约验收：`npm run verify:frontend-contract`
- [x] Docker Compose容器级启动复验：PostgreSQL healthy、后端 `8080`、前端 `8888`；本轮 Flyway 静态验收已升级到 `V1.41`
- [x] Flyway 全新库迁移演练：`../docs/SmartDisplay-MES-flyway-rehearsal-20260608-052419.md`
- [x] 真实数据库 API 闭环复验：`../tools/run-real-db-api-flow.ps1`，报告 `../docs/SmartDisplay-MES-real-db-api-flow-20260608-060901.md`
- [x] V1.38 库位任务状态流接口冒烟：`CREATED -> ASSIGNED -> DONE`、`CREATED -> CANCELLED`
- [x] V1.39 BOM/ECO 会签状态流接口冒烟：3 个会签任务全部 `APPROVED` 后发布 BOM
- [x] V1.41 供应商准入周期复审任务后端/前端/Flyway 验收；供应商准入、8D整改和月度评分趋势已完成后端全量和前端契约验收
- [x] 前端 Codex app 风格视觉冒烟：关键页面无横向溢出、按钮文字溢出、文本裁切和控制台错误
- [x] 性能验收冒烟实测：1000 工单导入、订单/Lot/良率/追溯 P95 均通过阈值
- [x] 三轮稳定性能基线：`../docs/SmartDisplay-MES-performance-baseline-20260608-061856.md`
- [x] 完整浏览器前端E2E报告：`../docs/SmartDisplay-MES-browser-e2e-20260608-055759.md`

## 交付文档

- 演示脚本：`../docs/SmartDisplay-MES演示脚本.md`
- 业务流程图与ER图：`../docs/SmartDisplay-MES流程图与ER图.md`
- 生产级试点验收清单：`../docs/SmartDisplay-MES验收清单.md`
- 落地进度：`../docs/SmartDisplay-MES生产级试点落地进度.md`
- Flyway迁移验收与回滚策略：`../docs/Flyway迁移验收与回滚策略.md`

## 业务逻辑说明

### Recipe管理
Recipe是OLED生产的核心，包含设备加工的所有参数（温度、压力、时间、速度等）。

**业务规则：**
1. Recipe由产品型号+工序+设备+版本唯一确定
2. Track In时必须校验Recipe存在且状态为ACTIVE
3. Recipe参数包含目标值、上限、下限，用于Track Out时校验
4. 关键参数超限会自动触发Hold

**状态流转：**
- DRAFT（草稿）→ ACTIVE（生效）→ INACTIVE（失效）

## 面试准备

### 项目亮点
1. **业务深度**：围绕显示行业公开流程和通用MES模型，覆盖工单、Lot、Route、Recipe、Hold、追溯闭环
2. **技术栈现代化**：Spring Boot 3.2、Java 17、MyBatis-Plus
3. **工程规范**：统一异常处理、参数校验、日志规范、API文档
4. **Docker化部署**：一键启动，环境隔离

### 常见问题
**Q: Recipe为什么重要？**
> Recipe是设备加工参数包，OLED生产对参数控制要求极高，错误Recipe直接影响良率。Track In时强制校验Recipe，确保每次加工参数正确。

**Q: 为什么用PostgreSQL？**
> 后续需要集成PGVector扩展，用于AI知识库的向量检索。PostgreSQL性能稳定，适合企业级应用。

**Q: 项目是真实上线的吗？**
> 这是个人学习和求职展示项目，参考公开的显示行业资料、半导体/显示制造通用MES模型和开源技术栈实现，不复刻任何企业内部系统。

## 许可证

本项目仅用于学习研究和求职展示，业务规则参考公开行业资料和通用MES模型，不包含任何企业内部系统、内部流程或商业秘密。

## 联系方式

- 项目作者：SmartDisplay MES
- 创建时间：2024-06
