# SmartDisplay MES 生产级试点

SmartDisplay MES 是面向显示行业通用制造执行场景的生产级试点系统。项目目标是打通单基地、单产线、模拟外部集成的 MES 闭环，不声明复刻任何企业内部系统。

## 试点闭环

工单创建 -> 工单释放并生成 Lot -> Track In 校验 Route/设备/Recipe/Hold/班次/物料 -> Track Out 记录参数 -> 质量判定 -> NG/参数超限自动 Hold -> Release/Rework/Scrap -> Lot/SN 追溯 -> 良率/异常看板 -> AI 辅助报告。

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

GitHub Actions CI 已纳入后端测试/打包、前端契约/构建/生产包扫描、Flyway 静态校验、PowerShell 交付脚本语法检查和 Docker Compose 配置解析。门禁说明见 `docs/SmartDisplay-MES-CI门禁说明.md`。

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

可运行试点演示脚本用于复演 5 分钟/15 分钟演示闭环：

```bash
powershell -ExecutionPolicy Bypass -File tools\run-pilot-demo-script.ps1 -Mode Short
powershell -ExecutionPolicy Bypass -File tools\run-pilot-demo-script.ps1 -Mode Full
```

`Short` 覆盖登录、看板、工单释放、Lot Track In/Out、追溯和 AI 良率日报；`Full` 继续覆盖自动 Hold、Release、Rework、Scrap、WMS、载具、EAP、Hybrid RAG、AI 设备分析和审计。复验证据见 `docs/SmartDisplay-MES可运行演示脚本复验.md`。

脚本会校验工单、Lot 过站、质量 Hold、Release、追溯、看板、AI 报告和审计真实落库。最新通过报告见 `docs/SmartDisplay-MES-real-db-api-flow-20260608-221738.md`。

性能验收脚本需要后端和 PostgreSQL 已启动：

```bash
powershell -ExecutionPolicy Bypass -File tools\run-pilot-performance-smoke.ps1
powershell -ExecutionPolicy Bypass -File tools\run-pilot-performance-baseline.ps1
```

单轮脚本会按订单列表 P95 < 500ms、Lot 列表 P95 < 500ms、良率看板 < 2000ms、Lot 追溯 < 1000ms 做判定，并输出 Markdown/JSON 报告。多轮基线脚本会连续执行多轮单轮冒烟，汇总 P95、标准差、漂移比例和稳定性告警；最新通过报告见 `docs/SmartDisplay-MES-performance-baseline-20260608-061856.md`。

## 监控、健康检查与日志

### Spring Boot Actuator

引入 `spring-boot-starter-actuator`，暴露 `health`、`info`、`metrics`：

- `GET /api/actuator/health`：聚合存活/就绪探针与数据库、磁盘健康，正常返回 `{"status":"UP"}`，对匿名开放，供 Docker/K8s liveness/readiness 与监控直接探测。
- `GET /api/actuator/info`：展示应用与构建信息（由 `spring-boot-maven-plugin` 的 `build-info` 目标生成 `build-info.properties`，含构建时间/版本）。
- `GET /api/actuator/metrics`：运行指标，**需携带 JWT** 才能访问。

> Spring MVC 的 `HandlerInterceptor` 不作用于 Actuator 独立的 endpoint handler mapping，因此 actuator 的访问控制由专门的 servlet 过滤器 `ActuatorAccessFilter` 负责：`health/info` 匿名放行（探针/监控），其余端点需有效 JWT，避免运行指标对外匿名泄露。

`docker-compose.yml` 后端服务据此配置了 `healthcheck`（探测 `/api/actuator/health`），前端 `depends_on` 升级为 `condition: service_healthy`，实现"后端就绪后再启动前端"。

### 日志

日志由 `logback-spring.xml` 统一管理：控制台 + 滚动文件（按天 + 50MB 切分、gzip 历史、保留 14 天/总量上限 1GB），并按 Spring profile 区分（`default` 本地 DEBUG，`docker/prod` INFO 并降噪 SQL 日志）。

每个请求由 `AuditRequestContextFilter` 生成或透传 `requestId`（优先沿用上游 `X-Request-Id`），写入 SLF4J MDC 并回写响应头 `X-Request-Id`，所有日志行均带 `[requestId]`，便于全链路关联。

接口文档：`http://localhost:8080/api/swagger-ui.html`（Knife4j/Swagger），`/api/v1` 端点已按认证、工单、Lot 执行、质量与 MRB、物料与 WMS、设备、追溯、看板、AI 等领域 `@Operation` 标签分组并附中文摘要。

## 交付文档

- [落地进度](docs/SmartDisplay-MES生产级试点落地进度.md)
- [验收清单](docs/SmartDisplay-MES验收清单.md)
- [演示脚本](docs/SmartDisplay-MES演示脚本.md)
- [流程图与 ER 图](docs/SmartDisplay-MES流程图与ER图.md)
- [Flyway 迁移验收与回滚策略](docs/Flyway迁移验收与回滚策略.md)
- [测试报告](docs/SmartDisplay-MES测试报告.md)
