# SmartDisplay MES 代码审查问题清单

> 审查日期: 2026-06-27 | 项目版本: 1.0.0-SNAPSHOT

---

## 一、安全类问题 (严重)

### 1.1 数据库密码硬编码

**文件**: `docker-compose.yml:8,33` | `smartdisplay-mes-api/src/main/resources/application.yml:9`

```
POSTGRES_PASSWORD: mes123456
password: ${SPRING_DATASOURCE_PASSWORD:mes123456}
```

数据库密码 `mes123456` 在 application.yml 和 docker-compose.yml 中均以明文硬编码作为默认值。生产环境必须全部通过环境变量注入，移除默认值。

### 1.2 JWT 签名密钥硬编码

**文件**: `smartdisplay-mes-api/src/main/resources/application.yml:59`

```
secret: ${MES_JWT_SECRET:smartdisplay-mes-jwt-secret-key-2024-very-long-key-for-hs256}
```

JWT 签名密钥有硬编码默认值。只要未设置 `MES_JWT_SECRET` 环境变量，任何人拿到此仓库就能伪造 JWT Token。生产环境必须强制要求提供环境变量，建议去掉默认值或改用启动时检测。

### 1.3 明文密码回退逻辑

**文件**: `smartdisplay-mes-api/src/main/java/com/visionox/mes/auth/service/AuthService.java:74`

```java
return storedPassword.equals(rawPassword);
```

当存储的密码不是 `$2a$/$2b$/$2y$` 前缀的 BCrypt 格式时，直接进行明文比较。这意味着如果数据库中某用户的密码字段存的是明文，系统将接受明文登录。建议去掉此回退分支，仅支持 BCrypt 验证。

### 1.4 种子用户共用同一个密码哈希

**文件**: `smartdisplay-mes-api/src/main/resources/db/migration/V1.8__Seed_RBAC_Users.sql:1-15`

7 个种子用户 (admin, planner, operator, qe, pe, ee, engineer) 使用完全相同的 BCrypt 哈希值。虽然这是种子数据，但如果这个哈希对应的明文弱密码被猜到，所有用户的账户将同时暴露。建议至少为 admin 使用独立密码。

### 1.5 SQL 注入风险 - LIMIT 拼接

**文件**: `smartdisplay-mes-api/src/main/java/com/visionox/mes/system/service/AuditLogService.java:62`

```java
wrapper.orderByDesc(AuditLog::getCreatedTime).last("LIMIT " + Math.max(1, limit));
```

`Math.max(1, limit)` 仅防止负数，但 `limit` 来自方法参数，代码中 `limit` 值硬编码为 50，当前不存在注入风险。但该写法不符合安全最佳实践，如果未来 `limit` 来自外部输入则存在风险。

**其他出现位置**:
- `PilotMesService.java:1679` — `.last("LIMIT 5")`
- `PilotMesService.java:2004` — `.last("LIMIT 50")`
- `PilotMesService.java:2015` — `.last("LIMIT 100")`
- `PilotMesService.java:2023` — `.last("LIMIT 50")`

上述 4 处 `LIMIT` 值均为硬编码常量，暂无注入风险，但建议统一使用 MyBatis-Plus 的 `Page` 分页替代原始 SQL 拼接。

### 1.6 前端 Token 明文存 localStorage (XSS 风险)

**文件**: `smartdisplay-mes-ui/src/router/index.js:105-110` | `smartdisplay-mes-ui/src/api/request.js:41`

```javascript
localStorage.setItem('token', 'dev-preview-token')
const token = localStorage.getItem('token')
```

JWT Token 存储在 localStorage 中，任何 XSS 漏洞都可轻易窃取。建议改用 httpOnly Cookie 传递身份凭证，或使用 sessionStorage + 短有效期策略。同时 dev preview token 在生产构建中不应存在 (`import.meta.env.DEV` 仅在 Vite dev mode 生效，但仍需注意此路径)。

### 1.7 Swagger UI 生产环境暴露

**文件**: `smartdisplay-mes-api/src/main/resources/application.yml:42-49`

```yaml
springdoc:
  api-docs:
    enabled: true
  swagger-ui:
    enabled: true
```

生产环境下 Swagger UI 和 OpenAPI 文档直接对外暴露，可被攻击者用于全面了解 API 接口结构。建议添加 profile 条件控制，仅在 dev/staging 环境启用。

### 1.8 Actuator info 端点匿名可访问

**文件**: `smartdisplay-mes-api/src/main/resources/application.yml:81` | `smartdisplay-mes-api/src/main/java/com/visionox/mes/auth/security/ActuatorAccessFilter.java:32`

```java
static final List<String> ANONYMOUS_ACTUATOR_PREFIXES = List.of("/actuator/health", "/actuator/info");
```

`/actuator/info` 端点对匿名开放，暴露应用名称、描述、版本等信息。若 `show-details: when-authorized` 未正确限制，更多信息可能泄露。建议对 info 端点也要求认证或关闭 `env.enabled`。

### 1.9 全局异常处理器泄露内部信息

**文件**: `smartdisplay-mes-api/src/main/java/com/visionox/mes/common/GlobalExceptionHandler.java:60-65`

```java
public Result<Void> handleException(Exception e, HttpServletRequest request) {
    log.error("系统异常: ", e);
    ...
    return Result.fail("系统异常: " + e.getMessage());
}
```

未预期的异常会将 `e.getMessage()` 直接返回给客户端。生产环境下可能泄露数据库结构、SQL 错误等敏感信息。建议对非 BusinessException 使用通用错误消息，详细错误仅记录日志。

---

## 二、架构/设计问题 (重要)

### 2.1 PilotMesService 超大类 (God Object)

**文件**: `smartdisplay-mes-api/src/main/java/com/visionox/mes/pilot/service/PilotMesService.java`

- **总行数: 3029 行**
- 直接注入了 **18 个 Mapper** 和 **15 个 Service**
- 承担了工单管理、Lot 执行、设备OEE、质量、物料WMS、追溯、AI报告、系统管理等全部领域的聚合逻辑
- 包含大量硬编码 fallback 数据 (products, defectCodes, equipmentEvents, OEE 等)

**建议**: 按领域拆分为多个聚合服务 (OrderAggregateService, LotAggregateService, EquipmentAggregateService 等)，PilotMesService 仅作路由门面。

### 2.2 PilotV1Controller 超大控制器

**文件**: `smartdisplay-mes-api/src/main/java/com/visionox/mes/pilot/controller/PilotV1Controller.java`

- **总行数: 945 行**
- 包含 80+ 个端点方法
- 缺少 `@Valid` 对请求体的校验 (仅 login 方法使用了 `@Valid`)

### 2.3 RBAC 权限匹配逻辑脆弱

**文件**: `smartdisplay-mes-api/src/main/java/com/visionox/mes/auth/security/RolePermissionService.java:74-199`

`canAccess()` 方法使用了 120+ 行连续的 `if (path.startsWith(...))` 和 `if (path.contains(...))` 进行权限判断。这种字符串匹配方式:
- 新增接口时容易遗漏
- `contains` 可能误匹配 (如 `/orders/{orderNo}/release` 中的 "release" 会被 lot release 条件误判)
- 无法做静态检查

**建议**: 使用注解驱动 (如 `@PreAuthorize`) 或统一注册映射表。

### 2.4 服务层直接操作 Mapper

**文件**: `smartdisplay-mes-api/src/main/java/com/visionox/mes/pilot/service/PilotMesService.java:154-162, 191, 238, 649`

PilotMesService 在聚合层直接注入并调用 Mapper (如 `orderMapper.insert()`, `lotMapper.updateById()`, `equipmentMapper.selectList()`)，绕过了对应的领域服务层 (TrackInService, EquipmentService 等)，导致:
- 业务逻辑散落在聚合层和领域层之间
- 数据范围 (data scope) 可能不一致
- 难以进行单元测试

### 2.5 Hardcoded Fallback 数据污染生产路径

**文件**: `smartdisplay-mes-api/src/main/java/com/visionox/mes/pilot/service/PilotMesService.java:676-681, 742-749, 964-968, 971-1010`

```java
// products() 直接返回硬编码数据，不访问数据库
public List<Map<String, Object>> products() {
    return List.of(
        Map.of("productCode", "AMOLED_65", ...),
        ...
    );
}
```

多个查询方法包含硬编码的 fallback 数据。虽然由 `pilotFallbackEnabled` 控制，但 `products()` 等方法完全没有真实数据访问路径--始终返回硬编码数据，这意味着产品主数据表可能存在但从未被使用。

---

## 三、代码质量问题 (中等)

### 3.1 死代码 - validateActiveShift 方法

**文件**: `smartdisplay-mes-api/src/main/java/com/visionox/mes/lot/service/TrackInService.java:420-436`

```java
private void validateActiveShift(Lot lot) {
    // 此方法定义了但未被任何地方调用
```

`validateActiveShift()` 定义了完整的班次校验逻辑 (18行)，但在 TrackInService 中从未被调用。实际的校验流程走的是 `activeShiftCheck()` 方法 (line 397-418)，两个方法的逻辑完全重复。建议删除冗余方法。

### 3.2 Java instanceof 模式匹配兼容性

**文件**: `smartdisplay-mes-api/src/main/java/com/visionox/mes/common/GlobalExceptionHandler.java:70-71, 81-82`

```java
if (error instanceof FieldError fieldError) {
```

**文件**: `smartdisplay-mes-api/src/main/java/com/visionox/mes/pilot/service/PilotMesService.java:1017`

```java
if (batches instanceof List<?> list && !list.isEmpty()) {
```

`instanceof` 模式匹配 (Pattern Matching for instanceof) 在 Java 16 正式引入，Java 17 LTS 支持。但项目 pom.xml 中未显式配置 `--enable-preview`，且 Spring Boot 3.1.5 的工具链应能正常编译。验证通过 `mvn test` 可以确认，但若某些 IDE 或构建环境配置不当可能导致编译错误。建议在 pom.xml 的 maven-compiler-plugin 中确认 `release=17` 配置。

### 3.3 MyBatis SQL 日志输出到标准输出

**文件**: `smartdisplay-mes-api/src/main/resources/application.yml:32`

```yaml
log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
```

所有 SQL 语句打印到标准输出 (stdout)，在容器化部署时会混入应用日志流，且生产环境不该输出完整 SQL。建议移除或改用 logback profile 控制。

### 3.4 docker-compose DB 端口暴露到宿主机

**文件**: `docker-compose.yml:10-11`

```yaml
ports:
  - "5433:5432"
```

PostgreSQL 端口映射到宿主机 `5433`。生产部署不应暴露数据库端口到公共网络。建议仅保留容器间网络，去掉 ports 映射或限制 `127.0.0.1:5433:5432`。

### 3.5 前端全量注册 Element Plus 图标

**文件**: `smartdisplay-mes-ui/src/main.js:14-16`

```javascript
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
```

将 Element Plus 所有图标注册为全局组件，增加不必要的打包体积和内存占用。建议按需引入或使用 `unplugin-icons` 插件。

### 3.6 RBAC 前后端权限定义重复

**文件**: `smartdisplay-mes-api/.../RolePermissionService.java:24-45` | `smartdisplay-mes-ui/src/utils/permissions.js:1-11`

ALL_MENUS、ALL_BUTTONS、角色-按钮映射在后端 Java 和前端 JavaScript 中各维护了一份独立副本。新增一个权限按钮需要同时修改两份代码，极易产生前后端不一致的权限口径。

---

## 四、数据库迁移问题 (中等)

### 4.1 种子数据使用了过期日期

**文件**: `smartdisplay-mes-api/src/main/resources/db/migration/V1.2__Seed_Core_Production_Data.sql:48-66`

种子数据的工单编号 `MO202406001`, Lot 编号 `LOT202406001` 等使用的日期为 2024 年 6 月，距今已 2 年。虽然是演示数据，但可能引起时间相关的逻辑混乱 (如追溯查询默认时间范围)。当前代码未强制执行时间范围过滤，所以暂无功能影响，但降低了演示可信度。

### 4.2 Flyway 迁移量过多

**文件**: `smartdisplay-mes-api/src/main/resources/db/migration/` (52 个文件)

52 个迁移文件对于"试点"项目来说数量偏多。大量连续的小粒度迁移 (如 V1.30-V1.52) 表明建表策略经历过多次迭代。建议在正式发布前合并为一个基线迁移。

### 4.3 DDL 使用 IF NOT EXISTS 与 Flyway 冲突

**文件**: `smartdisplay-mes-api/src/main/resources/db/migration/V1.1__Create_Core_Production_Tables.sql:1`

```sql
CREATE TABLE IF NOT EXISTS md_recipe (...);
```

Flyway 按版本号严格顺序执行迁移，`IF NOT EXISTS` 在 Flyway 上下文中是反模式——如果某张表已存在 (不应该发生)，Flyway 不会报错，但表结构可能与预期不一致。建议去掉 `IF NOT EXISTS`。

---

## 五、测试相关问题 (中等)

### 5.1 前端缺少单元测试

**文件**: `smartdisplay-mes-ui/package.json:6-12`

前端项目没有配置任何单元测试框架 (Jest/Vitest)。只有 contract verification 和 production bundle scan 脚本。Vue 组件、Pinia store、router guards 均无自动化测试覆盖。

### 5.2 全局状态管理未充分使用

**文件**: `smartdisplay-mes-ui/src/main.js:2,11`

Pinia 已安装但几乎没有实际 store 定义——权限、用户信息通过 localStorage 直接读写 (`router/index.js:113`, `request.js:41`)。对于 MES 系统的复杂度，建议将认证状态、权限快照统一纳入 Pinia store 管理。

### 5.3 无容器资源限制

**文件**: `docker-compose.yml:1-67`

3 个容器均未配置 `mem_limit` / `cpus` 资源限制。在资源受限的生产环境中可能导致 OOM kill 或不稳定的性能表现。PostgreSQL 尤其需要合理的内存限制。

---

## 六、次要问题

### 6.1 dev preview token 硬编码

**文件**: `smartdisplay-mes-ui/src/router/index.js:105-110`

```
localStorage.setItem('token', 'dev-preview-token')
```

开发预览模式下直接注入硬编码 token 到 localStorage。虽然由 `import.meta.env.DEV` 保护 (仅 Vite dev server 生效)，但如有类似机制被误引入生产构建将导致严重安全漏洞。

### 6.2 前端无请求超时重试策略

**文件**: `smartdisplay-mes-ui/src/api/request.js:30-31`

```javascript
const request = axios.create({
  timeout: 10000,
})
```

Axios 设置了 10 秒超时，但没有配置重试逻辑。MES 场景下网络抖动可能导致操作失败，建议对幂等 GET 请求添加有限的重试策略。

### 6.3 后端无请求限流

除了 `LoginAttemptService` 限制登录频率外，其他 API 端点没有任何限流保护。对于一个暴露 80+ 端点的系统，攻击者可以轻易通过高频请求压垮服务。

### 6.4 `@RequestMapping("/v1")` 路径问题

**文件**: `smartdisplay-mes-api/src/main/java/com/visionox/mes/pilot/controller/PilotV1Controller.java:44`

Controller 上使用 `/v1`，配合 `server.servlet.context-path: /api`，最终路径为 `/api/v1/...`。但 RolePermissionService 中的路径匹配同时包含了 `/v1/...` 和 `/...` 两种前缀——说明可能存在内部重定向或双路径暴露。建议统一路径前缀策略。

### 6.5 V1.8 种子数据引用 `sys_user` 表但迁移在 V1.3

**文件**: `V1.3__Create_User_Table.sql` | `V1.8__Seed_RBAC_Users.sql`

用户表在 V1.3 创建，种子数据在 V1.8 插入。如果中间版本 (V1.4-V1.7) 对 `sys_user` 表结构有修改，V1.8 的 INSERT 可能因列不匹配而失败。建议种子数据紧跟在建表迁移之后。

---

## 总结

| 严重程度 | 数量 | 关键问题 |
|---------|------|---------|
| 严重 (安全) | 9 | 硬编码密钥/密码、明文回退、XSS风险、异常泄露 |
| 重要 (架构) | 5 | God类3000行、字符串路径RBAC、服务层直调Mapper |
| 中等 (代码质量) | 8 | 死代码、日志泄露、DB端口暴露、前后端权限双写 |
| 次要 | 5 | 限流缺失、超时重试、迁移顺序 |

**优先处理建议**: 
1. 移除所有硬编码密钥和密码 (1.1, 1.2)
2. 去掉明文密码回退逻辑 (1.3)
3. 生产环境禁用 Swagger UI (1.7)
4. 拆分 PilotMesService (2.1)
5. 重构 RBAC 为注解驱动 (2.3)
