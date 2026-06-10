package com.visionox.mes.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void smartDisplayMesOpenApiShouldExposePilotMetadataAndJwtScheme() {
        OpenAPI openAPI = config.smartDisplayMesOpenApi();

        assertThat(openAPI.getInfo().getTitle()).isEqualTo("SmartDisplay MES API");
        assertThat(openAPI.getInfo().getDescription())
                .contains("单基地")
                .contains("模拟 ERP/EAP/QMS/WMS")
                .contains("不声称复刻任何企业内部系统");
        assertThat(openAPI.getServers()).singleElement()
                .satisfies(server -> assertThat(server.getUrl()).isEqualTo("/api"));

        SecurityScheme bearer = openAPI.getComponents().getSecuritySchemes().get(OpenApiConfig.BEARER_AUTH);
        assertThat(bearer.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(bearer.getScheme()).isEqualTo("bearer");
        assertThat(bearer.getBearerFormat()).isEqualTo("JWT");
        assertThat(openAPI.getSecurity()).singleElement()
                .satisfies(requirement -> assertThat(requirement).containsKey(OpenApiConfig.BEARER_AUTH));

        assertThat(openAPI.getComponents().getSchemas())
                .containsKeys("Result", "PageResult");
        assertThat(openAPI.getComponents().getResponses())
                .containsKeys("BadRequest", "Unauthorized", "Forbidden", "ServerError");
    }

    @Test
    void pilotV1OpenApiShouldOnlyGroupV1Paths() {
        GroupedOpenApi groupedOpenApi = config.pilotV1OpenApi(config.pilotOpenApiCustomizer(), config.pilotOperationCustomizer());

        assertThat(groupedOpenApi.getGroup()).isEqualTo("pilot-v1");
        assertThat(groupedOpenApi.getDisplayName()).isEqualTo("SmartDisplay MES 生产级试点 API v1");
        assertThat(groupedOpenApi.getPathsToMatch()).containsExactly("/v1/**");
        assertThat(groupedOpenApi.getOpenApiCustomizers()).hasSize(1);
        assertThat(groupedOpenApi.getOperationCustomizers()).hasSize(1);
    }

    @Test
    void pilotOpenApiCustomizerShouldBackfillComponentsForGroupedDocs() {
        OpenAPI groupedDoc = new OpenAPI();

        config.customizePilotOpenApi(groupedDoc);

        assertThat(groupedDoc.getInfo().getTitle()).isEqualTo("SmartDisplay MES API");
        assertThat(groupedDoc.getServers()).singleElement()
                .satisfies(server -> assertThat(server.getUrl()).isEqualTo("/api"));
        assertThat(groupedDoc.getComponents().getSecuritySchemes())
                .containsKey(OpenApiConfig.BEARER_AUTH);
        assertThat(groupedDoc.getComponents().getSchemas())
                .containsKeys("Result", "PageResult");
        Schema<?> resultSchema = groupedDoc.getComponents().getSchemas().get("Result");
        assertThat(resultSchema.getDescription()).contains("统一响应包装");
        assertThat(groupedDoc.getComponents().getResponses())
                .containsKeys("BadRequest", "Unauthorized", "Forbidden", "ServerError");
    }

    @Test
    void pilotOperationCustomizerShouldAddStandardResponsesAndBearerSecurity() throws Exception {
        Operation operation = new Operation().summary("查询Lot");

        Operation customized = config.customizePilotOperation(operation, handlerMethod("lots"));

        assertThat(customized.getExtensions())
                .containsEntry("x-mes-response-wrapper", "Result")
                .containsEntry("x-mes-pilot-scope", "single-site-single-line")
                .containsEntry("x-mes-integration-mode", "simulated-adapters");
        assertThat(customized.getSecurity()).singleElement()
                .satisfies(requirement -> assertThat(requirement).containsKey(OpenApiConfig.BEARER_AUTH));
        assertStandardErrorResponses(customized.getResponses());
    }

    @Test
    void pilotOperationCustomizerShouldNotRequireBearerForLogin() throws Exception {
        Operation operation = new Operation().summary("登录");
        operation.addSecurityItem(new SecurityRequirement().addList(OpenApiConfig.BEARER_AUTH));

        Operation customized = config.customizePilotOperation(operation, handlerMethod("login"));

        assertThat(customized.getSecurity()).isEmpty();
        assertStandardErrorResponses(customized.getResponses());
    }

    private void assertStandardErrorResponses(ApiResponses responses) {
        assertThat(responses).containsKeys("400", "401", "403", "500");
        assertThat(responses.get("400").get$ref()).isEqualTo("#/components/responses/BadRequest");
        assertThat(responses.get("401").get$ref()).isEqualTo("#/components/responses/Unauthorized");
        assertThat(responses.get("403").get$ref()).isEqualTo("#/components/responses/Forbidden");
        assertThat(responses.get("500").get$ref()).isEqualTo("#/components/responses/ServerError");
    }

    private HandlerMethod handlerMethod(String methodName) throws NoSuchMethodException {
        Method method = StubController.class.getDeclaredMethod(methodName);
        return new HandlerMethod(new StubController(), method);
    }

    static class StubController {
        void login() {
        }

        void lots() {
        }
    }
}
