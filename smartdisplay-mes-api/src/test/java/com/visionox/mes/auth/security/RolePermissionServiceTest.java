package com.visionox.mes.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RolePermissionServiceTest {

    private final RolePermissionService service = new RolePermissionService();

    @Test
    void shouldNormalizeLegacyAndPilotRoles() {
        assertThat(service.normalizeRole("admin")).isEqualTo("ADMIN");
        assertThat(service.normalizeRole("engineer")).isEqualTo("PE");
        assertThat(service.normalizeRole("op")).isEqualTo("OPERATOR");
        assertThat(service.normalizeRole("quality_engineer")).isEqualTo("QE");
        assertThat(service.normalizeRole("equipment_engineer")).isEqualTo("EE");
        assertThat(service.normalizeRole(null)).isEqualTo("OPERATOR");
    }

    @Test
    void shouldAllowAllRolesToReadBusinessData() {
        assertThat(service.canAccess("OPERATOR", request("GET", "/api/v1/lots"))).isTrue();
        assertThat(service.canAccess("PLANNER", request("GET", "/api/v1/orders"))).isTrue();
        assertThat(service.canAccess("QE", request("GET", "/api/v1/quality/inspections"))).isTrue();
    }

    @Test
    void shouldRestrictSystemManagementToAdmin() {
        assertThat(service.canAccess("ADMIN", request("GET", "/api/v1/system/users"))).isTrue();
        assertThat(service.canAccess("QE", request("GET", "/api/v1/system/users"))).isFalse();
    }

    @Test
    void shouldAllowPlannerToWriteOrdersOnly() {
        assertThat(service.canAccess("PLANNER", request("POST", "/api/v1/orders"))).isTrue();
        assertThat(service.canAccess("PLANNER", request("POST", "/api/v1/adapters/erp/orders"))).isTrue();
        assertThat(service.canAccess("PLANNER", request("POST", "/api/v1/lots/LOT001/track-in"))).isFalse();
    }

    @Test
    void shouldAllowOperatorToTrackInAndOutOnly() {
        assertThat(service.canAccess("OPERATOR", request("POST", "/api/v1/lots/LOT001/track-in"))).isTrue();
        assertThat(service.canAccess("OPERATOR", request("POST", "/api/v1/lots/LOT001/track-out"))).isTrue();
        assertThat(service.canAccess("OPERATOR", request("POST", "/api/v1/lots/LOT001/hold"))).isFalse();
        assertThat(service.canAccess("OPERATOR", request("POST", "/api/v1/adapters/erp/orders"))).isFalse();
    }

    @Test
    void shouldAllowQualityEngineerToDispositionLots() {
        assertThat(service.canAccess("QE", request("POST", "/api/v1/lots/LOT001/hold"))).isTrue();
        assertThat(service.canAccess("QE", request("POST", "/api/v1/lots/LOT001/release"))).isTrue();
        assertThat(service.canAccess("QE", request("POST", "/api/v1/lots/LOT001/scrap"))).isTrue();
        assertThat(service.canAccess("QE", request("POST", "/api/v1/quality/mrb-approvals/MRBT001/approve"))).isTrue();
        assertThat(service.canAccess("QE", request("POST", "/api/v1/quality/mrb-approvals/refresh-sla"))).isTrue();
        assertThat(service.canAccess("QE", request("POST", "/api/v1/quality/mrb-records/MRB001/minutes"))).isTrue();
        assertThat(service.canAccess("QE", request("POST", "/api/v1/orders"))).isFalse();
    }

    @Test
    void shouldAllowDomainEngineersToWriteOwnedDomains() {
        assertThat(service.canAccess("PE", request("POST", "/api/v1/recipes/1/publish"))).isTrue();
        assertThat(service.canAccess("PE", request("POST", "/api/v1/boms/change-requests"))).isTrue();
        assertThat(service.canAccess("PE", request("POST", "/api/v1/boms/eco-approvals/BEA001/decision"))).isTrue();
        assertThat(service.canAccess("QE", request("POST", "/api/v1/boms/eco-approvals/BEA001/decision"))).isTrue();
        assertThat(service.canAccess("PLANNER", request("POST", "/api/v1/boms/eco-approvals/BEA001/decision"))).isTrue();
        assertThat(service.canAccess("PE", request("POST", "/api/v1/quality/mrb-approvals/MRBT001/approve"))).isTrue();
        assertThat(service.canAccess("PE", request("POST", "/api/v1/quality/mrb-records/MRB001/minutes"))).isTrue();
        assertThat(service.canAccess("EE", request("POST", "/api/v1/equipment/events"))).isTrue();
        assertThat(service.canAccess("EE", request("POST", "/api/v1/equipment/parameters/report"))).isTrue();
        assertThat(service.canAccess("EE", request("POST", "/api/v1/equipment/pm-tasks/PM001/complete"))).isTrue();
        assertThat(service.canAccess("EE", request("POST", "/api/v1/equipment/recipe-downloads"))).isTrue();
        assertThat(service.canAccess("EE", request("POST", "/api/v1/equipment/standard-cycles"))).isTrue();
        assertThat(service.canAccess("EE", request("POST", "/api/v1/equipment/gateways"))).isTrue();
        assertThat(service.canAccess("EE", request("POST", "/api/v1/equipment/gateways/GW001/heartbeat"))).isTrue();
        assertThat(service.canAccess("EE", request("POST", "/api/v1/equipment/gateways/GW001/health-check"))).isTrue();
        assertThat(service.canAccess("EE", request("POST", "/api/v1/adapters/eap/messages"))).isTrue();
        assertThat(service.canAccess("EE", request("POST", "/api/v1/quality/mrb-approvals/MRBT001/reject"))).isTrue();
        assertThat(service.canAccess("OPERATOR", request("POST", "/api/v1/quality/mrb-records/MRB001/minutes"))).isFalse();
        assertThat(service.canAccess("PE", request("POST", "/api/v1/equipment/events"))).isFalse();
        assertThat(service.canAccess("PE", request("POST", "/api/v1/equipment/parameters/report"))).isFalse();
        assertThat(service.canAccess("PE", request("POST", "/api/v1/equipment/recipe-downloads"))).isFalse();
        assertThat(service.canAccess("PE", request("POST", "/api/v1/equipment/standard-cycles"))).isFalse();
        assertThat(service.canAccess("PE", request("POST", "/api/v1/equipment/gateways"))).isFalse();
        assertThat(service.canAccess("PE", request("POST", "/api/v1/equipment/gateways/GW001/health-check"))).isFalse();
        assertThat(service.canAccess("PE", request("POST", "/api/v1/adapters/eap/messages"))).isFalse();
        assertThat(service.canAccess("EE", request("POST", "/api/v1/recipes/1/publish"))).isFalse();
        assertThat(service.canAccess("PE", request("POST", "/api/v1/quality/exceptions/EX001/mrb-review"))).isFalse();
        assertThat(service.canAccess("OPERATOR", request("POST", "/api/v1/boms/change-requests"))).isFalse();
        assertThat(service.canAccess("OPERATOR", request("POST", "/api/v1/boms/eco-approvals/BEA001/decision"))).isFalse();
    }

    @Test
    void shouldRequireMaterialWmsButtonForMaterialWriteApis() {
        assertThat(service.canAccess("PLANNER", request("POST", "/api/v1/material/receive"))).isFalse();
        assertThat(service.canAccess("QE", request("POST", "/api/v1/material/batches/MB001/freeze"))).isFalse();
        assertThat(service.canAccess("QE", request("POST", "/api/v1/material/batches/MB001/incoming-inspection"))).isTrue();
        assertThat(service.canAccess("QE", request("POST", "/api/v1/material/suppliers/SUP-A/qualification/evaluate"))).isTrue();
        assertThat(service.canAccess("QE", request("POST", "/api/v1/material/suppliers/corrective-actions"))).isTrue();
        assertThat(service.canAccess("PE", request("POST", "/api/v1/material/batches/MB001/incoming-inspection"))).isFalse();
        assertThat(service.canAccess("PE", request("POST", "/api/v1/material/suppliers/corrective-actions"))).isFalse();

        service.applyPermissionSnapshot("QE", Map.of(
                "role", "QE",
                "menus", List.of("dashboard", "quality", "material"),
                "buttons", List.of("material:wms"),
                "dataScope", "LINE",
                "domains", List.of("QUALITY", "MATERIAL")
        ));

        assertThat(service.canAccess("QE", request("POST", "/api/v1/material/batches/MB001/freeze"))).isTrue();
        assertThat(service.canAccess("QE", request("POST", "/api/v1/material/batches/MB001/inventory-count"))).isTrue();
        assertThat(service.canAccess("QE", request("POST", "/api/v1/material/suppliers/corrective-actions"))).isFalse();
    }

    @Test
    void shouldAllowAiWritesOnlyForEngineeringAndQualityRoles() {
        assertThat(service.canAccess("QE", request("POST", "/api/v1/ai/reports/yield"))).isTrue();
        assertThat(service.canAccess("PE", request("POST", "/api/v1/ai/kb/ask"))).isTrue();
        assertThat(service.canAccess("PE", request("POST", "/api/v1/ai/kb/index-jobs"))).isTrue();
        assertThat(service.canAccess("EE", request("POST", "/api/v1/ai/equipment/analyze"))).isTrue();
        assertThat(service.canAccess("OPERATOR", request("POST", "/api/v1/ai/kb/ask"))).isFalse();
        assertThat(service.canAccess("OPERATOR", request("POST", "/api/v1/ai/kb/index-jobs"))).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExposeMenuButtonAndDataScopeCapabilities() {
        var qePermissions = service.permissions("qe");

        assertThat(qePermissions.get("role")).isEqualTo("QE");
        assertThat((Iterable<String>) qePermissions.get("menus")).contains("quality", "material", "trace", "ai");
        assertThat((Iterable<String>) qePermissions.get("buttons"))
                .contains("quality:mrb-review", "quality:mrb-approve", "quality:mrb-escalate", "quality:exception-close", "lot:release", "material:iqc", "material:supplier-manage", "ai:kb-import", "ai:kb-index");
        assertThat(qePermissions.get("dataScope")).isEqualTo("LINE");

        var operatorPermissions = service.permissions("operator");
        assertThat((Iterable<String>) operatorPermissions.get("buttons"))
                .containsExactly("lot:track-in", "lot:track-out");
        assertThat(operatorPermissions.get("dataScope")).isEqualTo("SELF_SHIFT");

        var pePermissions = service.permissions("pe");
        assertThat((Iterable<String>) pePermissions.get("buttons"))
                .contains("quality:mrb-approve", "quality:mrb-escalate", "recipe:publish", "bom:change", "bom:eco-approve");

        var eePermissions = service.permissions("ee");
        assertThat((Iterable<String>) eePermissions.get("buttons"))
                .contains("equipment:event-create", "equipment:eap-ingest", "equipment:eap-gateway");
    }

    @Test
    void shouldGenerateSafeSqlForDataScope() {
        var lineScope = service.dataScopeCondition("QE", "qe1001", "po", "line_code", "created_by", "created_time");
        assertThat(lineScope.sql()).isEqualTo("po.line_code = {0}");
        assertThat(lineScope.parameters()).containsExactly("LINE_01");

        var selfShiftScope = service.dataScopeCondition("OPERATOR", "op1001", "", "line_code", "created_by", "created_time");
        assertThat(selfShiftScope.sql()).isEqualTo("created_by = {0} AND created_time >= {1}");
        assertThat(selfShiftScope.parameters()).hasSize(2);
        assertThat(selfShiftScope.parameters().get(0)).isEqualTo("op1001");

        var adminScope = service.dataScopeCondition("ADMIN", "admin", "po", "line_code", "created_by", "created_time");
        assertThat(adminScope.unrestricted()).isTrue();
    }

    @Test
    void shouldApplyRuntimePermissionSnapshotToAccessCheck() {
        service.applyPermissionSnapshot("QE", Map.of(
                "role", "QE",
                "menus", List.of("dashboard", "quality", "ai"),
                "buttons", List.of("ai:equipment-analyze"),
                "dataScope", "LINE",
                "domains", List.of("QUALITY", "AI")
        ));

        assertThat(service.canAccess("QE", request("POST", "/api/v1/ai/equipment/analyze"))).isTrue();
        assertThat(service.canAccess("QE", request("POST", "/api/v1/quality/exceptions/EX001/mrb-review"))).isFalse();
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setContextPath("/api");
        return request;
    }
}
