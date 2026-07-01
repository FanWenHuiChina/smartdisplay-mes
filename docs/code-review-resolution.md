# SmartDisplay MES 代码审查处置说明

> 针对《SmartDisplay MES 代码审查问题清单》(code-review-issues.md) 的逐条处置。
> 处置日期: 2026-06-27 | 分支: `fix/code-review-hardening`

原则：**干净、安全、不破坏一键演示**的真问题逐条修复；架构级重构、与试点演示强绑定的取舍、以及非真问题，标注理由并留作后续/扩展点。后端全量回归保持全绿。

## 一、安全类

| 编号 | 处置 | 说明 |
|---|---|---|
| 1.1 DB 口令默认值 | ✅ 修复（prod 加固） | 保留默认值便于一键演示；新增 `prod` profile 启动校验 `ProductionSecurityGuard`：prod 下使用内置默认 DB 口令即 fail-fast。 |
| 1.2 JWT 密钥默认值 | ✅ 修复（prod 加固） | 同上，prod 下使用内置默认 JWT 密钥即 fail-fast，强制 `MES_JWT_SECRET` 注入。 |
| 1.3 明文密码回退 | ✅ 修复 | `AuthService.matchesPassword` 去掉 `equals` 明文回退，仅 BCrypt 校验（种子用户均为 BCrypt，登录不受影响）。 |
| 1.4 种子用户同哈希 | ⏸ 不改 | 演示约定所有账号口令均为 `123456`；改独立口令会破坏文档与演示。生产由独立账号体系托管。 |
| 1.5 LIMIT 拼接 | ⏸ 不改 | 审查亦确认均为硬编码常量、无注入风险；统一改 Page 属较大改动，列为后续。 |
| 1.6 Token 存 localStorage | ⏸ 不改 | SPA + 无状态 JWT 的常见取舍；httpOnly Cookie 是架构级改动，留作扩展点。 |
| 1.7 Swagger 生产暴露 | ✅ 修复（prod 加固） | 新增 `application-prod.yml` 在 prod 关闭 `springdoc`/`swagger-ui`；docker 演示 profile 仍保留 Swagger。 |
| 1.8 Actuator /info 匿名 | ⏸ 不改 | `/info` 仅暴露应用名/描述/版本与构建信息，非敏感；`metrics` 已由 `ActuatorAccessFilter` 要求鉴权。 |
| 1.9 异常泄露 e.getMessage | ✅ 修复 | `GlobalExceptionHandler` 兜底分支改为通用文案，`e.getMessage()` 仅入日志与审计，不回传客户端。 |

## 二、架构/设计

| 编号 | 处置 | 说明 |
|---|---|---|
| 2.1 God 类 | 🔄 进行中（已出设计+首刀） | 见《PilotMesService拆分设计说明》；已抽出 `LotTraceAssembler`，其余子域按"补测试后再拆"推进。 |
| 2.2 超大控制器 / 缺 @Valid | ⏸ 不改 | 端点多以 `Map<String,Object>` 入参（无 DTO 可校验）；引入 DTO+@Valid 为较大重构，列为后续。 |
| 2.3 RBAC 字符串匹配 | ⏸ 不改 | `/orders/.../release` 已由"orders 分支先返回"规避误匹配；注解驱动 RBAC 为架构级改造，留作后续。 |
| 2.4 服务层直调 Mapper | ⏸ 不改 | 与 2.1 同源，随拆分逐步收敛。 |
| 2.5 硬编码 fallback 数据 | ⏸ 不改 | 试点 mock/降级数据，由 `pilotFallbackEnabled` 控制，是演示边界的刻意设计。 |

## 三、代码质量

| 编号 | 处置 | 说明 |
|---|---|---|
| 3.1 死代码 validateActiveShift | ✅ 修复 | 删除未被调用、与 `activeShiftCheck` 重复的方法。 |
| 3.2 instanceof 模式匹配 | ⏸ 不改 | Java 17 正式支持，`mvn test` 正常编译，非真问题。 |
| 3.3 MyBatis SQL 打 stdout | ✅ 修复 | `log-impl` 改 `Slf4jImpl`，SQL 走 logback，docker/prod profile 自动降噪（INFO/WARN）。 |
| 3.4 DB 端口暴露宿主机 | ✅ 修复 | docker-compose 端口绑定 `127.0.0.1:5433:5432`，仅本机可达。 |
| 3.5 前端全量注册图标 | ⏸ 不改 | 前端打包体积优化，留作扩展点。 |
| 3.6 RBAC 前后端双写 | ⏸ 不改 | 单一事实源需架构改造（后端下发权限清单），留作后续。 |

## 四、数据库迁移

| 编号 | 处置 | 说明 |
|---|---|---|
| 4.1 种子过期日期 | ⏸ 不改 | 演示数据，当前无时间范围过滤强约束；改动涉及多处引用。 |
| 4.2 迁移过多 | ⏸ 不改 | 迁移即历史；正式发布前合并基线属发布动作，不在本次范围。 |
| 4.3 IF NOT EXISTS | ⏸ 不改 | 去掉有破坏已落库环境的风险；试点环境无重复执行问题。 |

## 五、测试

| 编号 | 处置 | 说明 |
|---|---|---|
| 5.1 前端无单测 | ⏸ 不改 | 引入 Vitest 为独立工作项，列为后续（后端已有 322 项 + JaCoCo 覆盖率）。 |
| 5.2 Pinia 未充分使用 | ⏸ 不改 | 前端状态管理重构，留作后续。 |
| 5.3 容器无资源限制 | ✅ 修复 | docker-compose 三服务加 `mem_limit`/`cpus`（pg 512m、backend 1g、frontend 128m）。 |

## 六、次要

| 编号 | 处置 | 说明 |
|---|---|---|
| 6.1 dev preview token | ⏸ 不改 | 仅 `import.meta.env.DEV` 生效，生产包已由 `verify:production-bundle` 扫描拦截。 |
| 6.2 前端无重试 | ⏸ 不改 | 可选增强，留作扩展点。 |
| 6.3 无全局限流 | ⏸ 部分 | 登录限流已由 `LoginAttemptService` 覆盖；全局网关限流为较大改动，留作后续。 |
| 6.4 /v1 双前缀匹配 | ⏸ 不改 | `canAccess` 同时匹配带/不带 context-path 的路径是防御性写法，非双路径暴露。 |
| 6.5 V1.8 引用 V1.3 表 | ⏸ 不改 | V1.4–V1.7 未改 `sys_user` 结构，Flyway 顺序执行通过，无实际失败。 |

## 本次实修汇总

- 安全：1.1/1.2/1.7（prod profile 加固 + 启动校验）、1.3（BCrypt-only）、1.9（异常不泄露）
- 质量/运维：3.1（删死代码）、3.3（SQL 走 SLF4J）、3.4（DB 端口绑本机）、5.3（容器资源限制）
- 验证：后端全量 322 项通过（新增 `ProductionSecurityGuardTest` 3 项），`docker compose config` 校验通过。
