# SmartDisplay MES 面试就绪度完善计划

生成时间：2026-06-27

## 背景与定位

本项目用于**面试展示**，目标是呈现"贴近真实生产落地"的工程完整度与专业判断力，而非真机投产。因此：

- **移出计划**：真实 SECS/GEM、OPC UA 真机联调（无真机，已用 Mock 模拟器 + 影子驱动覆盖到可验证边界）；真实 pgvector 向量库与外部大模型联调（无凭据，已用 SHADOW/SIMULATED 边界占位）。这两项在面试中作为"已预留扩展点、边界清晰"来讲，而不是未完成的坑。
- **聚焦**：补齐"面试官一眼会看、一问会追"的生产级工程要素，把现有扎实的业务深度配上同等专业的工程外观。

## 工程现状盘点（通读结论）

扎实的部分（面试加分项，已具备）：

- 后端 142 个 Java 文件、27 个测试类、52 个 Flyway 版本化迁移、13 个 Vue 视图。
- 安全：JWT + 轻量 RBAC（菜单/按钮/数据范围权限）+ 数据范围 SQL 自动拼接 + 权限变更审批闭环。
- 健壮性：`@RestControllerAdvice` 全局异常处理覆盖 10 类异常；统一 `Result/PageResult`。
- 可信度：关键写动作结构化审计快照（before/after/changedFields/request）+ 失败审计映射。
- 测试：服务级单测 + H2 内存库的跨层集成测试（ERP 导入 → 释放 → Lot → 追溯）。
- 交付：Docker Compose（PostgreSQL + 后端 + 前端 Nginx 反代）+ 前端契约门禁 + 浏览器 E2E 门禁。

## 待完善项（按面试性价比排序）

### 梯队 A：低风险、高展示价值、能立刻看到效果

1. **健康检查与监控端点（Spring Boot Actuator）**
   - 现状：`pom.xml` 无 actuator，没有 `/health`、`/info`、`/metrics`。
   - 面试理由：几乎必问"你的服务怎么做健康检查、就绪探针、监控"。Docker/K8s liveness/readiness 也依赖它。
   - 动作：引入 `spring-boot-starter-actuator`，暴露 `health/info/metrics`，`docker-compose.yml` 后端加 healthcheck，`info` 端点注入 git/构建信息。
   - 验收：`GET /api/actuator/health` 返回 UP；补一条 ActuatorTest 或集成断言。

2. **OpenAPI/Swagger 注解补全**
   - 现状：全工程仅 4 个文件有 `@Operation`，`PilotV1Controller`（1879 行、上百端点）几乎裸奔，Knife4j 页面分组有了但每个接口缺摘要/参数/响应说明。
   - 面试理由：打开接口文档是面试演示的高频动作，空文档显得不专业。
   - 动作：给 `PilotV1Controller` 和 `AuthController` 的核心端点加 `@Operation(summary/description)`、`@Parameter`、`@Tag` 分组；至少覆盖登录、工单释放、Track In/Out、Hold/Release、MRB、库位任务、AI 报告等主干。
   - 验收：`OpenApiConfigTest` 扩展断言关键路径带 summary；Knife4j 页面分组清晰。

3. **生产级日志配置（logback-spring.xml）**
   - 现状：无 `logback-spring.xml`，只有 application.yml 里的 root: INFO，无文件输出、无滚动、无 traceId。
   - 面试理由：生产日志切割、分级、关联请求是落地标配。
   - 动作：加 `logback-spring.xml`（控制台 + 滚动文件、按环境 profile 区分、可选 MDC requestId）；与已有的 `AuditRequestContextFilter` 联动注入 requestId 到 MDC。
   - 验收：启动后生成滚动日志文件；日志含 requestId。

4. **README 快速开始与架构说明补全**
   - 现状：README "快速开始"只一句"见 docs/"。
   - 面试理由：README 是面试官看代码的第一入口。
   - 动作：补本地启动（mvn + npm）、Docker 一键启动、默认账号表、架构图引用、接口文档地址、测试运行命令。

### 梯队 B：中等价值、需谨慎、体现架构判断力

5. **上帝类拆分（PilotMesService 6913 行 / MaterialService 4504 行）**
   - 现状：`PilotMesService` 6913 行聚合了工单/Lot/执行/追溯/看板/AI 等多个领域；`MaterialService` 4504 行。
   - 面试理由：面试官一看行数就会问"为什么一个 Service 六千行、怎么拆"。能讲清拆分思路本身就是加分；动手拆出 1-2 个内聚子域（如 Lot 执行、追溯、看板）更有说服力。
   - 风险：大重构有回归风险，必须每步全测试通过。建议按"提取委托"渐进式拆分，保持对外方法签名不变。
   - 动作（建议分多次小步）：先拆 `PilotMesService` 中追溯/看板这类相对独立的读模型到独立 Service，主类委托调用；保持 281 项测试全绿。
   - 验收：每次拆分后端全量测试通过，无行为变化。

6. **主数据读缓存（可选，量力而行）**
   - 现状：产品/工序/设备/缺陷代码等读多写少主数据每次查库。
   - 面试理由：可借此聊缓存策略、失效、一致性。
   - 动作：引入 Spring Cache（`ConcurrentMapCacheManager` 即可，不必上 Redis），给主数据查询加 `@Cacheable`，写动作 `@CacheEvict`。
   - 取舍：若时间有限，作为"可扩展点"在 README/面试话术里讲清即可，不必实做。

### 梯队 C：锦上添花

7. **接口限流 / 防刷（登录接口）**：登录加简单的失败计数或限流，体现安全意识。
8. **API 版本与弃用策略说明**：文档化 `/api/v1` 版本治理。
9. **CI 增强**：GitHub Actions 跑后端测试 + 前端契约（现有 CI 门禁基础上补单测覆盖率徽章）。

## 推进原则

- 每个增量保持项目既有纪律：改完即跑对应测试 + 后端全量回归 + 前端契约/构建，全绿才记进度。
- 优先做梯队 A（4 项），它们风险最低、面试展示收益最高，且互相独立可快速交付。
- 梯队 B 的上帝类拆分单独评估，小步渐进，每步可回退。
- 编辑含中文的已有文件时用 Python 按字节操作（本环境 Edit/Read 对多字节 UTF-8 不稳定，详见 [[mes-utf8-edit-hazard]]）。

## 已移出计划（面试定位下不再追）

- 真实 SECS/GEM / OPC UA 真机联调、毫秒级设备采集 —— 无真机；已有 Mock 模拟器 + 影子驱动 + 联调单测覆盖可验证边界，作为"清晰预留的扩展点"讲解。
- 真实 pgvector 向量检索、外部大模型联调 —— 无向量库与模型凭据；已有 SHADOW/SIMULATED 边界与 RAG 关键词回退，作为扩展点讲解。
