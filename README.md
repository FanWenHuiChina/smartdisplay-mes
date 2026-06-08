# SmartDisplay MES 生产级试点

SmartDisplay MES 是面向显示行业通用制造执行场景的生产级试点系统。项目目标是打通单基地、单产线、模拟外部集成的 MES 闭环，不声明复刻任何企业内部系统。

## 试点闭环

工单创建 -> 工单释放并生成 Lot -> Track In 校验 Route/设备/Recipe/Hold/物料 -> Track Out 记录参数 -> 质量判定 -> NG/参数超限自动 Hold -> Release/Rework/Scrap -> Lot/SN 追溯 -> 良率/异常看板 -> AI 辅助报告。

物料域已扩展到 WMS 库位任务、来料 IQC/COA、供应商绩效评分、供应商月度评分趋势、供应商准入评估、供应商准入周期复审任务和 8D 整改闭环；IQC NG/HOLD 会自动生成供应商 8D 并触发准入风险降级。

## 技术栈

- 后端：Spring Boot 3、MyBatis-Plus、PostgreSQL、Flyway
- 前端：Vue 3、Element Plus、Vite、Nginx
- 交付：Docker Compose 编排 PostgreSQL、后端 API、前端工作台

## 快速启动

```bash
docker compose up -d --build
```

启动后访问：

- 前端工作台：http://localhost:8888
- 后端 Swagger：http://localhost:8080/api/swagger-ui.html
- PostgreSQL：localhost:5433

默认试点账号密码均为 `123456`：

- `admin` 管理员
- `planner` 计划员
- `operator` 操作员
- `qe` 质量工程师
- `pe` 工艺工程师
- `ee` 设备工程师

## 本地验证

后端：

```bash
cd smartdisplay-mes-api
mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" test
mvn.cmd "-Dmaven.repo.local=D:\workspace\mes\.m2" "-DskipTests" "-Dspring-boot.repackage.skip=true" package
```

前端：

```bash
cd smartdisplay-mes-ui
npm.cmd run verify:frontend-contract
npm.cmd run build
npm.cmd run verify:production-bundle
```

前端 mock fallback 默认只在开发环境启用；生产构建接口失败时不会静默展示样例生产数据，默认生产包也会通过 `verify:production-bundle` 检查典型 mock/fallback 样例业务标识。离线演示如需临时启用，显式设置 `VITE_ENABLE_MOCK_FALLBACK=true` 后重新构建。

Flyway 迁移静态验收：

```bash
powershell -ExecutionPolicy Bypass -File tools\verify-flyway-migrations.ps1
```

当前最新迁移为 `V1.41 Add Supplier Qualification Review Tasks`，供应商准入主数据、周期复审任务、8D整改闭环、IQC NG/HOLD 自动开8D和供应商月度评分趋势已通过后端、前端和 Flyway 静态验收；BOM/ECO 跨部门会签状态流已在 Docker Compose 后端和 PostgreSQL 上完成 API 冒烟。

真实数据库 API 闭环复验需要 Docker Compose 后端和 PostgreSQL 已启动：

```bash
powershell -ExecutionPolicy Bypass -File tools\run-real-db-api-flow.ps1
```

脚本会校验工单、Lot 过站、质量 Hold、Release、追溯、看板、AI 报告和审计真实落库。最新通过报告见 `docs/SmartDisplay-MES-real-db-api-flow-20260608-060901.md`。

性能验收脚本需要后端和 PostgreSQL 已启动：

```bash
powershell -ExecutionPolicy Bypass -File tools\run-pilot-performance-smoke.ps1
powershell -ExecutionPolicy Bypass -File tools\run-pilot-performance-baseline.ps1
```

单轮脚本会按订单列表 P95 < 500ms、Lot 列表 P95 < 500ms、良率看板 < 2000ms、Lot 追溯 < 1000ms 做判定，并输出 Markdown/JSON 报告。多轮基线脚本会连续执行多轮单轮冒烟，汇总 P95、标准差、漂移比例和稳定性告警；最新通过报告见 `docs/SmartDisplay-MES-performance-baseline-20260608-061856.md`。

## 交付文档

- [落地进度](docs/SmartDisplay-MES生产级试点落地进度.md)
- [验收清单](docs/SmartDisplay-MES验收清单.md)
- [演示脚本](docs/SmartDisplay-MES演示脚本.md)
- [流程图与 ER 图](docs/SmartDisplay-MES流程图与ER图.md)
- [Flyway 迁移验收与回滚策略](docs/Flyway迁移验收与回滚策略.md)
- [测试报告](docs/SmartDisplay-MES测试报告.md)
