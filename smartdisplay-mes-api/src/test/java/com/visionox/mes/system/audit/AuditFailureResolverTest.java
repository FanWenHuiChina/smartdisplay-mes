package com.visionox.mes.system.audit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AuditFailureResolverTest {

    private final AuditFailureResolver resolver = new AuditFailureResolver();

    @Test
    void resolveShouldMapLotTrackInFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/lots/LOT001/track-in");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("TRACK_IN");
        assertThat(target.get().bizNo()).isEqualTo("LOT001");
        assertThat(target.get().bizType()).isEqualTo("LOT");
    }

    @Test
    void resolveShouldRemoveContextPathBeforeMapping() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders/MO001/release");
        request.setContextPath("/api");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("ORDER_RELEASE");
        assertThat(target.get().bizNo()).isEqualTo("MO001");
        assertThat(target.get().bizType()).isEqualTo("ORDER");
    }

    @Test
    void resolveShouldMapLegacyRecipeActivateFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/recipes/100/activate");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("RECIPE_ACTIVATE");
        assertThat(target.get().bizNo()).isEqualTo("100");
        assertThat(target.get().bizType()).isEqualTo("RECIPE");
    }

    @Test
    void resolveShouldMapMaterialFreezeFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/material/batches/PI_INK_B001/freeze");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("MATERIAL_FREEZE");
        assertThat(target.get().bizNo()).isEqualTo("PI_INK_B001");
        assertThat(target.get().bizType()).isEqualTo("MATERIAL_BATCH");
    }

    @Test
    void resolveShouldMapMaterialIqcFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/material/batches/PI_INK_B001/incoming-inspection");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("MATERIAL_IQC");
        assertThat(target.get().bizNo()).isEqualTo("PI_INK_B001");
        assertThat(target.get().bizType()).isEqualTo("MATERIAL_BATCH");
    }

    @Test
    void resolveShouldMapSupplierQualificationEvaluateFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/material/suppliers/SUP-A/qualification/evaluate");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("SUPPLIER_QUALIFICATION_EVALUATE");
        assertThat(target.get().bizNo()).isEqualTo("SUP-A");
        assertThat(target.get().bizType()).isEqualTo("SUPPLIER");
    }

    @Test
    void resolveShouldMapSupplier8dCreateFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/material/suppliers/corrective-actions");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("SUPPLIER_8D_CREATE");
        assertThat(target.get().bizNo()).isNull();
        assertThat(target.get().bizType()).isEqualTo("SUPPLIER_8D");
    }

    @Test
    void resolveShouldMapSupplier8dCloseFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/material/suppliers/corrective-actions/SCA-001/close");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("SUPPLIER_8D_CLOSE");
        assertThat(target.get().bizNo()).isEqualTo("SCA-001");
        assertThat(target.get().bizType()).isEqualTo("SUPPLIER_8D");
    }

    @Test
    void resolveShouldMapSupplierQualificationReviewCreateFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/material/suppliers/SUP-A/qualification-reviews");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("SUPPLIER_QUALIFICATION_REVIEW_CREATE");
        assertThat(target.get().bizNo()).isEqualTo("SUP-A");
        assertThat(target.get().bizType()).isEqualTo("SUPPLIER");
    }

    @Test
    void resolveShouldMapSupplierQualificationReviewDecisionFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/material/suppliers/qualification-reviews/SQR-001/decision");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("SUPPLIER_QUALIFICATION_REVIEW_DECIDE");
        assertThat(target.get().bizNo()).isEqualTo("SQR-001");
        assertThat(target.get().bizType()).isEqualTo("SUPPLIER_REVIEW");
    }

    @Test
    void resolveShouldMapBomPublishFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/boms/change-requests/BCR001/publish");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("BOM_PUBLISH");
        assertThat(target.get().bizNo()).isEqualTo("BCR001");
        assertThat(target.get().bizType()).isEqualTo("BOM_CHANGE");
    }

    @Test
    void resolveShouldMapBomEcoApprovalDecisionFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/boms/eco-approvals/BEA001/decision");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("BOM_ECO_APPROVAL_DECISION");
        assertThat(target.get().bizNo()).isEqualTo("BEA001");
        assertThat(target.get().bizType()).isEqualTo("BOM_ECO_APPROVAL");
    }

    @Test
    void resolveShouldMapMrbApprovalFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/quality/mrb-approvals/MRBT001/approve");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("MRB_APPROVAL_APPROVE");
        assertThat(target.get().bizNo()).isEqualTo("MRBT001");
        assertThat(target.get().bizType()).isEqualTo("MRB_APPROVAL");
    }

    @Test
    void resolveShouldMapMrbApprovalSlaEscalationFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/quality/mrb-approvals/refresh-sla");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("MRB_APPROVAL_ESCALATE");
        assertThat(target.get().bizNo()).isNull();
        assertThat(target.get().bizType()).isEqualTo("MRB_APPROVAL");
    }

    @Test
    void resolveShouldMapMrbMinutesFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/quality/mrb-records/MRB001/minutes");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("MRB_MINUTES_CREATE");
        assertThat(target.get().bizNo()).isEqualTo("MRB001");
        assertThat(target.get().bizType()).isEqualTo("MRB_MINUTES");
    }

    @Test
    void resolveShouldMapEquipmentParameterReportFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/equipment/parameters/report");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("EAP_PARAMETER_REPORT");
        assertThat(target.get().bizType()).isEqualTo("EQUIPMENT");
    }

    @Test
    void resolveShouldMapEquipmentStatusReportFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/equipment/status/report");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("EAP_STATUS_REPORT");
        assertThat(target.get().bizType()).isEqualTo("EQUIPMENT");
    }

    @Test
    void resolveShouldMapEquipmentCycleReportFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/equipment/cycle-samples/report");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("EAP_CYCLE_REPORT");
        assertThat(target.get().bizType()).isEqualTo("EQUIPMENT");
    }

    @Test
    void resolveShouldMapEquipmentStandardCyclePublishFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/equipment/standard-cycles");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("EQUIPMENT_STANDARD_CYCLE_PUBLISH");
        assertThat(target.get().bizType()).isEqualTo("EQUIPMENT");
    }

    @Test
    void resolveShouldMapEquipmentGatewayRegisterFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/equipment/gateways");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("EQUIPMENT_GATEWAY_REGISTER");
        assertThat(target.get().bizType()).isEqualTo("EQUIPMENT_GATEWAY");
    }

    @Test
    void resolveShouldMapEquipmentGatewayHeartbeatFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/equipment/gateways/GW001/heartbeat");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("EQUIPMENT_GATEWAY_HEARTBEAT");
        assertThat(target.get().bizNo()).isEqualTo("GW001");
        assertThat(target.get().bizType()).isEqualTo("EQUIPMENT_GATEWAY");
    }

    @Test
    void resolveShouldMapEquipmentGatewayHealthCheckFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/equipment/gateways/GW001/health-check");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("EQUIPMENT_GATEWAY_HEALTH_CHECK");
        assertThat(target.get().bizNo()).isEqualTo("GW001");
        assertThat(target.get().bizType()).isEqualTo("EQUIPMENT_GATEWAY");
    }

    @Test
    void resolveShouldMapEquipmentPmCompleteFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/equipment/pm-tasks/PM001/complete");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("EQUIPMENT_PM_COMPLETE");
        assertThat(target.get().bizNo()).isEqualTo("PM001");
        assertThat(target.get().bizType()).isEqualTo("EQUIPMENT");
    }

    @Test
    void resolveShouldMapEquipmentRecipeDownloadFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/equipment/recipe-downloads");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("EQUIPMENT_RECIPE_DOWNLOAD");
        assertThat(target.get().bizType()).isEqualTo("EQUIPMENT");
    }

    @Test
    void resolveShouldMapEapAdapterMessageFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/adapters/eap/messages");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("EAP_ADAPTER_MESSAGE");
        assertThat(target.get().bizType()).isEqualTo("EQUIPMENT");
    }

    @Test
    void resolveShouldMapErpOrderImportFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/adapters/erp/orders");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("ERP_ORDER_IMPORT");
        assertThat(target.get().bizType()).isEqualTo("ERP_ADAPTER");
    }

    @Test
    void resolveShouldMapQmsInspectionAdapterFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/adapters/qms/inspections");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("QMS_INSPECTION_REPORT");
        assertThat(target.get().bizNo()).isNull();
        assertThat(target.get().bizType()).isEqualTo("QMS_ADAPTER");
    }

    @Test
    void resolveShouldMapWmsReadinessAdapterFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/adapters/wms/material-readiness");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("WMS_MATERIAL_READINESS");
        assertThat(target.get().bizNo()).isNull();
        assertThat(target.get().bizType()).isEqualTo("WMS_ADAPTER");
    }

    @Test
    void resolveShouldMapWmsInventoryTransactionAdapterFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/adapters/wms/inventory-transactions");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("WMS_INVENTORY_TRANSACTION");
        assertThat(target.get().bizNo()).isNull();
        assertThat(target.get().bizType()).isEqualTo("WMS_ADAPTER");
    }

    @Test
    void resolveShouldMapAiKnowledgeIndexFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/ai/kb/index-jobs");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("AI_KB_INDEX");
        assertThat(target.get().bizNo()).isNull();
        assertThat(target.get().bizType()).isEqualTo("SOP_KB");
    }

    @Test
    void resolveShouldMapEquipmentEventCloseFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/equipment/events/EVT001/close");

        Optional<AuditFailureTarget> target = resolver.resolve(request);

        assertThat(target).isPresent();
        assertThat(target.get().action()).isEqualTo("EQUIPMENT_EVENT_CLOSE");
        assertThat(target.get().bizNo()).isEqualTo("EVT001");
        assertThat(target.get().bizType()).isEqualTo("EQUIPMENT");
    }

    @Test
    void resolveShouldIgnoreReadRequests() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/lots");

        assertThat(resolver.resolve(request)).isEmpty();
    }
}
