# PilotMesService 拆分设计说明（梯队B-5）

生成时间：2026-06-27

## 1. 现状与问题

`PilotMesService` 是前端工作台首批闭环接口的聚合层，实测 **3112 行、123 个 public 方法、25 个注入依赖**（`PilotV1Controller` 几乎所有 `/v1` 端点都委托到它）。它是典型的"上帝类/聚合服务"：一个类同时承载工单、Lot 执行、追溯、看板、AI 报告、设备、物料转发、系统/审计等多个领域。

> 注：早期文档曾按 6913 行描述，实测已是 3112 行（部分领域逻辑此前已下沉到 `QualityService`/`MaterialService`/`EquipmentService` 等领域服务，`PilotMesService` 大量方法其实是对它们的**薄委托**）。

## 2. 成因（为什么会长这样）

- **接口优先、单一聚合入口**：为快速打通"工单→Lot→质检→追溯→看板→AI"闭环，控制器统一指向一个 service，迭代中各领域不断往里加方法。
- **读模型与写流程混居**：既有重写流程（`releaseOrder` ~280 行、`trackOut`、Hold/Release/Rework/Scrap），又有重读模型（`traceLot`、`overview`、`dashboardYield`、`aiYieldReport`）。
- **共享私有 helper 沉淀**：`audit`/`currentUser`/`text`/`safeRequest`/`valueOr`/`objectText`/`findLot`/`fallbackListOnFailure` 等跨领域工具方法集中在本类，进一步把各领域绑在一起。

## 3. 耦合现实（决定拆分难度）

通读后确认两条硬约束，任何"零行为变化"的拆分都要先处理它们：

1. **共享 helper 网**：候选子域都依赖同一批私有工具方法。直接搬走某域会断掉其它域。
2. **跨域互相调用**：`aiYieldReport → dashboardYield → equipmentOeeSummary`；`traceLot → qualityInspections / qualityExceptions / materialConsumptions / auditLogs`。若简单地"主类委托新服务、新服务回调主类"，会形成构造器循环依赖。
3. **测试覆盖不均**（决定先拆谁最安全）：`traceLot` 有 2 个测试文件覆盖（含跨层集成测试）；而 `overview`/`aiYieldReport`/`ragAsk`/`systemSummary` **无直接测试**——这些属于"盲改高风险区"，不优先动。

## 4. 目标分解

按"内聚读模型优先、写流程后置"拆成若干领域子服务，`PilotMesService` 收敛为**编排/委托门面**：

| 子服务 | 承载方法 | 依赖 | 备注 |
|---|---|---|---|
| `LotTraceService` | `traceLot/traceSn/traceSearch` + 追溯私有 helper | 各 Mapper + routeService/qualityService/materialService/auditLogService | **首个落地**，测试覆盖最好 |
| `DashboardQueryService` | `overview/dashboardYield/yieldTrend` | qualityService/equipmentService + lot 读 | 先补测试再拆 |
| `AiReportService` | `aiYieldReport/aiEquipmentAnalyze/ragAsk` + AI 私有 helper | 4 个 Ai* 服务 + DashboardQueryService | 依赖 Dashboard，破除 AI→Dashboard 环 |
| （保留）`PilotMesService` | 工单/Lot 写流程编排 + 跨域委托 | 上述子服务 + 领域服务 | 仍是控制器入口，方法签名不变 |

共享工具下沉到无状态支撑组件（`PilotServiceSupport`/静态工具），供主类与子服务共用，消除"搬走即断"。

## 5. 安全拆分策略（每步可回退、零行为变化）

1. **签名不变**：对外（控制器）调用的 `pilotMesService.xxx()` 方法签名全部保留，方法体改为**一行委托**到子服务。控制器与前端零改动。
2. **单向依赖、破环**：子服务**不回调** `PilotMesService`；它自带所需 Mapper/领域服务，必要的跨域读方法随之**迁移到子服务**（主类改为委托），从而 `PilotMesService → 子服务` 单向、无环。无法迁移的极小工具方法就近**复制**（纯函数、无副作用，复制无风险）。
3. **测试门禁**：每抽取一步，跑后端全量回归（当前 311 项）必须全绿；优先抽取有测试覆盖的子域（trace）。
4. **小步多次**：一次只抽一个子域，独立提交，可单独回退。

## 6. 首个落地：LotTraceService

选 trace 作为第一刀，因为：① `traceLot` 有跨层集成测试 + 服务级测试双覆盖，是最强的回归安全网；② trace 是纯只读读模型，无写副作用；③ 其私有 helper（`traceRoute`/`serialNumberSummary`/`carrierTraceRows`/`traceImpactSummary` 等）多为 trace 专用，迁移干净。

落地形态：新建 `LotTraceService` 自带所需 Mapper 与领域服务（无 `PilotMesService` 回调，无环）；`PilotMesService` 的 `traceLot/traceSn/traceSearch` 及其专属读方法改为委托。验收：`PilotMesServiceTest` + `PilotMesFlowIntegrationTest` 的 trace 断言与全量 311 项保持全绿。

## 7. 风险与回退

- **主要风险**：迁移/复制 helper 时行为漂移、或漏改一处调用点。由全量测试（尤其 trace 双覆盖）兜底；每步独立提交，异常即 `git revert` 单个提交。
- **未覆盖区不强拆**：`overview/aiYieldReport/ragAsk/systemSummary` 在补齐测试前，仅作为后续规划项，不在无测试网下动刀。
