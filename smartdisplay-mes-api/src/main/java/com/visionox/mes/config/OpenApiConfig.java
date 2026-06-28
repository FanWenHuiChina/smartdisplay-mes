package com.visionox.mes.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

import java.util.List;

/**
 * OpenAPI 交付配置。
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI smartDisplayMesOpenApi() {
        OpenAPI openAPI = new OpenAPI()
                .info(apiInfo())
                .servers(List.of(apiServer()))
                .externalDocs(new ExternalDocumentation()
                        .description("SmartDisplay MES 生产级试点交付文档")
                        .url("/docs/SmartDisplay-MES生产级试点落地进度.md"))
                .security(List.of(new SecurityRequirement().addList(BEARER_AUTH)));
        ensureMesComponents(openAPI);
        return openAPI;
    }

    @Bean
    public GroupedOpenApi pilotV1OpenApi(OpenApiCustomizer pilotOpenApiCustomizer,
                                         OperationCustomizer pilotOperationCustomizer) {
        return GroupedOpenApi.builder()
                .group("pilot-v1")
                .displayName("SmartDisplay MES 生产级试点 API v1")
                .pathsToMatch("/v1/**")
                .addOpenApiCustomizer(pilotOpenApiCustomizer)
                .addOperationCustomizer(pilotOperationCustomizer)
                .build();
    }

    @Bean
    public OpenApiCustomizer pilotOpenApiCustomizer() {
        return this::customizePilotOpenApi;
    }

    @Bean
    public OperationCustomizer pilotOperationCustomizer() {
        return this::customizePilotOperation;
    }

    void customizePilotOpenApi(OpenAPI openAPI) {
        if (openAPI.getInfo() == null) {
            openAPI.setInfo(apiInfo());
        }
        if (openAPI.getServers() == null || openAPI.getServers().isEmpty()) {
            openAPI.setServers(List.of(apiServer()));
        }
        ensureMesComponents(openAPI);
    }

    Operation customizePilotOperation(Operation operation, HandlerMethod handlerMethod) {
        operation.addExtension("x-mes-response-wrapper", "Result");
        operation.addExtension("x-mes-pilot-scope", "single-site-single-line");
        operation.addExtension("x-mes-integration-mode", "simulated-adapters");
        ensureStandardResponses(operation);
        if (isLoginOperation(handlerMethod)) {
            operation.setSecurity(List.of());
        } else if (operation.getSecurity() == null || operation.getSecurity().isEmpty()) {
            operation.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
        }
        return operation;
    }

    private Info apiInfo() {
        return new Info()
                .title("SmartDisplay MES API")
                .version("1.0.0")
                .description("""
                        显示行业 MES + AI 生产级试点 API。当前范围为单基地、单产线、模拟 ERP/EAP/QMS/WMS 外部集成，
                        覆盖工单释放、Lot 流转、Route/Recipe 校验、质量 Hold、设备异常、物料批次、追溯、看板和 AI 辅助报告。
                        本项目基于公开显示制造业务和通用 MES 模型构建，不声称复刻任何企业内部系统。
                        所有 /api/v1 写接口默认要求 JWT Bearer Token，并按角色、按钮权限和数据范围进行轻量 RBAC 控制。
                        """)
                .contact(new Contact()
                        .name("SmartDisplay MES Pilot")
                        .url("https://github.com/FanWenHuiChina/smartdisplay-mes"));
    }

    private Server apiServer() {
        return new Server()
                .url("/api")
                .description("Docker Compose / Nginx 反代后的统一 API 根路径");
    }

    private SecurityScheme bearerSecurityScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("登录 `/api/v1/auth/login` 后，将返回 token 以 `Authorization: Bearer <token>` 形式传入。");
    }

    private void ensureMesComponents(OpenAPI openAPI) {
        Components components = openAPI.getComponents();
        if (components == null) {
            components = new Components();
            openAPI.setComponents(components);
        }
        components.addSecuritySchemes(BEARER_AUTH, bearerSecurityScheme())
                .addSchemas("Result", resultSchema())
                .addSchemas("PageResult", pageResultSchema())
                .addResponses("BadRequest", errorResponse("400 参数校验失败"))
                .addResponses("Unauthorized", errorResponse("401 未登录或登录已过期"))
                .addResponses("Forbidden", errorResponse("403 当前角色无权执行该操作"))
                .addResponses("ServerError", errorResponse("500 系统异常"));
    }

    @SuppressWarnings("rawtypes")
    private Schema resultSchema() {
        return new Schema<>()
                .type("object")
                .description("统一响应包装。业务成功固定 `code=200`；业务异常、鉴权失败和系统异常沿用同一结构返回。")
                .addProperty("code", new Schema<>().type("integer").format("int32").example(200))
                .addProperty("message", new Schema<>().type("string").example("操作成功"))
                .addProperty("data", new Schema<>().type("object").additionalProperties(true))
                .addProperty("timestamp", new Schema<>().type("integer").format("int64").example(1780880000000L));
    }

    @SuppressWarnings("rawtypes")
    private Schema pageResultSchema() {
        return new Schema<>()
                .type("object")
                .description("MyBatis-Plus 分页对象，通常位于 `Result.data`。")
                .addProperty("records", new Schema<>().type("array").items(new Schema<>().type("object").additionalProperties(true)))
                .addProperty("total", new Schema<>().type("integer").format("int64").example(20))
                .addProperty("size", new Schema<>().type("integer").format("int64").example(20))
                .addProperty("current", new Schema<>().type("integer").format("int64").example(1))
                .addProperty("pages", new Schema<>().type("integer").format("int64").example(1));
    }

    private ApiResponse errorResponse(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/Result"))));
    }

    private void ensureStandardResponses(Operation operation) {
        ApiResponses responses = operation.getResponses();
        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }
        putIfAbsent(responses, "400", "BadRequest");
        putIfAbsent(responses, "401", "Unauthorized");
        putIfAbsent(responses, "403", "Forbidden");
        putIfAbsent(responses, "500", "ServerError");
    }

    private void putIfAbsent(ApiResponses responses, String code, String component) {
        if (!responses.containsKey(code)) {
            responses.addApiResponse(code, new ApiResponse().$ref("#/components/responses/" + component));
        }
    }

    private boolean isLoginOperation(HandlerMethod handlerMethod) {
        if (handlerMethod == null) {
            return false;
        }
        return "login".equals(handlerMethod.getMethod().getName());
    }
}
