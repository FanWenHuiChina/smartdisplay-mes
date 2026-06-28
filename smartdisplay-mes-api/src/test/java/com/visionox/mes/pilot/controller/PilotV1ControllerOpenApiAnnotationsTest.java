package com.visionox.mes.pilot.controller;

import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the OpenAPI documentation policy for the pilot v1 API: every HTTP endpoint must carry an
 * {@code @Operation} with at least one (non-blank) tag so Knife4j/Swagger groups them by domain, and
 * the demo-backbone endpoints must additionally carry a human-readable Chinese summary.
 *
 * <p>Reflection-based and Spring-context-free, matching the project's fast unit-test style.</p>
 */
class PilotV1ControllerOpenApiAnnotationsTest {

    private static final List<Class<? extends Annotation>> MAPPINGS = List.of(
            GetMapping.class, PostMapping.class, PutMapping.class, DeleteMapping.class, PatchMapping.class);

    @Test
    void everyEndpointHasOperationWithTag() {
        for (Method method : PilotV1Controller.class.getDeclaredMethods()) {
            if (!isEndpoint(method)) {
                continue;
            }
            Operation operation = method.getAnnotation(Operation.class);
            assertThat(operation).as("@Operation missing on %s", method.getName()).isNotNull();
            assertThat(operation.tags()).as("tag missing on %s", method.getName()).isNotEmpty();
            assertThat(operation.tags()[0]).as("blank tag on %s", method.getName()).isNotBlank();
        }
    }

    @Test
    void backboneEndpointsHaveReadableSummaries() {
        assertThat(summaryOf("login")).contains("登录");
        assertThat(summaryOf("releaseOrder")).contains("释放");
        assertThat(summaryOf("trackIn")).contains("进站");
        assertThat(summaryOf("trackOut")).contains("出站");
        assertThat(summaryOf("traceLot")).contains("追溯");
        assertThat(summaryOf("aiYieldReport")).contains("良率");
        assertThat(summaryOf("claimMaterialLocationTask")).contains("认领");
    }

    private String summaryOf(String methodName) {
        for (Method method : PilotV1Controller.class.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                Operation operation = method.getAnnotation(Operation.class);
                assertThat(operation).as("@Operation missing on %s", methodName).isNotNull();
                return operation.summary();
            }
        }
        throw new AssertionError("endpoint method not found: " + methodName);
    }

    private boolean isEndpoint(Method method) {
        return MAPPINGS.stream().anyMatch(method::isAnnotationPresent);
    }
}
