package com.visionox.mes.system.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 将失败的写请求归类到可查询的业务审计动作。
 */
@Component
public class AuditFailureResolver {

    public Optional<AuditFailureTarget> resolve(HttpServletRequest request) {
        if (request == null || !isWriteMethod(request.getMethod())) {
            return Optional.empty();
        }

        String path = normalizedPath(request);
        String[] parts = path.substring(1).split("/");
        if (parts.length == 0) {
            return Optional.empty();
        }

        int offset = "v1".equals(parts[0]) ? 1 : 0;
        if (parts.length <= offset) {
            return Optional.empty();
        }

        return resolveV1(parts, offset)
                .or(() -> resolveLegacy(parts, offset));
    }

    private Optional<AuditFailureTarget> resolveV1(String[] parts, int offset) {
        String domain = parts[offset];
        if ("auth".equals(domain) && matches(parts, offset, "auth", "login")) {
            return target("AUTH_LOGIN", null, "AUTH");
        }
        if ("orders".equals(domain)) {
            if (parts.length == offset + 1) {
                return target("ORDER_CREATE", null, "ORDER");
            }
            if (matches(parts, offset, "orders", "*", "release")) {
                return target("ORDER_RELEASE", parts[offset + 1], "ORDER");
            }
        }
        if ("lots".equals(domain) && parts.length >= offset + 3) {
            String lotNo = parts[offset + 1];
            return switch (parts[offset + 2]) {
                case "track-in" -> target("TRACK_IN", lotNo, "LOT");
                case "track-out" -> target("TRACK_OUT", lotNo, "LOT");
                case "hold" -> target("LOT_HOLD", lotNo, "LOT");
                case "release" -> target("LOT_RELEASE", lotNo, "LOT");
                case "rework" -> target("LOT_REWORK", lotNo, "LOT");
                case "scrap" -> target("LOT_SCRAP", lotNo, "LOT");
                default -> Optional.empty();
            };
        }
        if ("recipes".equals(domain) && matches(parts, offset, "recipes", "*", "publish")) {
            return target("RECIPE_PUBLISH", parts[offset + 1], "RECIPE");
        }
        if ("boms".equals(domain)) {
            return resolveBom(parts, offset);
        }
        if ("system".equals(domain)) {
            return resolveSystem(parts, offset);
        }
        if ("quality".equals(domain)) {
            return resolveQuality(parts, offset);
        }
        if ("equipment".equals(domain)) {
            return resolveEquipment(parts, offset);
        }
        if ("adapters".equals(domain)) {
            return resolveAdapters(parts, offset);
        }
        if ("material".equals(domain)) {
            return resolveMaterial(parts, offset);
        }
        if ("ai".equals(domain)) {
            return resolveAi(parts, offset);
        }
        return Optional.empty();
    }

    private Optional<AuditFailureTarget> resolveLegacy(String[] parts, int offset) {
        String domain = parts[offset];
        if ("recipes".equals(domain)) {
            if (parts.length == offset + 1) {
                return target("RECIPE_CREATE", null, "RECIPE");
            }
            if (matches(parts, offset, "recipes", "*", "activate")) {
                return target("RECIPE_ACTIVATE", parts[offset + 1], "RECIPE");
            }
            if (matches(parts, offset, "recipes", "*", "deactivate")) {
                return target("RECIPE_DEACTIVATE", parts[offset + 1], "RECIPE");
            }
        }
        if ("lots".equals(domain) && parts.length >= offset + 3) {
            String lotNo = parts[offset + 1];
            return switch (parts[offset + 2]) {
                case "track-in" -> target("TRACK_IN", lotNo, "LOT");
                case "track-out" -> target("TRACK_OUT", lotNo, "LOT");
                case "hold" -> target("LOT_HOLD", lotNo, "LOT");
                case "release" -> target("LOT_RELEASE", lotNo, "LOT");
                default -> Optional.empty();
            };
        }
        return Optional.empty();
    }

    private Optional<AuditFailureTarget> resolveSystem(String[] parts, int offset) {
        if (matches(parts, offset, "system", "permission-change-requests")) {
            return target("PERMISSION_CHANGE_SUBMIT", null, "PERMISSION_CHANGE");
        }
        if (matches(parts, offset, "system", "permission-change-requests", "*", "review")) {
            return target("PERMISSION_CHANGE_REVIEW", parts[offset + 2], "PERMISSION_CHANGE");
        }
        if (matches(parts, offset, "system", "permissions", "reload")) {
            return target("PERMISSION_RELOAD", null, "PERMISSION");
        }
        return Optional.empty();
    }

    private Optional<AuditFailureTarget> resolveQuality(String[] parts, int offset) {
        if (matches(parts, offset, "quality", "inspections")) {
            return target("QUALITY_INSPECTION", null, "LOT");
        }
        if (matches(parts, offset, "quality", "exceptions", "*", "mrb-review")) {
            return target("MRB_REVIEW", parts[offset + 2], "EXCEPTION");
        }
        if (matches(parts, offset, "quality", "exceptions", "*", "close")) {
            return target("EXCEPTION_CLOSE", parts[offset + 2], "EXCEPTION");
        }
        if (matches(parts, offset, "quality", "mrb-records", "*", "minutes")) {
            return target("MRB_MINUTES_CREATE", parts[offset + 2], "MRB_MINUTES");
        }
        if (matches(parts, offset, "quality", "mrb-approvals", "refresh-sla")) {
            return target("MRB_APPROVAL_ESCALATE", null, "MRB_APPROVAL");
        }
        if (matches(parts, offset, "quality", "mrb-approvals", "*", "approve")) {
            return target("MRB_APPROVAL_APPROVE", parts[offset + 2], "MRB_APPROVAL");
        }
        if (matches(parts, offset, "quality", "mrb-approvals", "*", "reject")) {
            return target("MRB_APPROVAL_REJECT", parts[offset + 2], "MRB_APPROVAL");
        }
        return Optional.empty();
    }

    private Optional<AuditFailureTarget> resolveBom(String[] parts, int offset) {
        if (matches(parts, offset, "boms", "eco-approvals", "*", "decision")) {
            return target("BOM_ECO_APPROVAL_DECISION", parts[offset + 2], "BOM_ECO_APPROVAL");
        }
        if (matches(parts, offset, "boms", "change-requests")) {
            return target("BOM_CHANGE_SUBMIT", null, "BOM_CHANGE");
        }
        if (matches(parts, offset, "boms", "change-requests", "*", "review")) {
            return target("BOM_CHANGE_REVIEW", parts[offset + 2], "BOM_CHANGE");
        }
        if (matches(parts, offset, "boms", "change-requests", "*", "publish")) {
            return target("BOM_PUBLISH", parts[offset + 2], "BOM_CHANGE");
        }
        return Optional.empty();
    }

    private Optional<AuditFailureTarget> resolveAi(String[] parts, int offset) {
        if (matches(parts, offset, "ai", "reports", "yield")) {
            return target("AI_YIELD_REPORT", null, "AI_REPORT");
        }
        if (matches(parts, offset, "ai", "equipment", "analyze")) {
            return target("AI_EQUIPMENT_ANALYZE", null, "EQUIPMENT");
        }
        if (matches(parts, offset, "ai", "kb", "ask")) {
            return target("AI_KB_ASK", null, "SOP_KB");
        }
        if (matches(parts, offset, "ai", "kb", "import")) {
            return target("AI_KB_IMPORT", null, "SOP_KB");
        }
        if (matches(parts, offset, "ai", "kb", "index-jobs")) {
            return target("AI_KB_INDEX", null, "SOP_KB");
        }
        return Optional.empty();
    }

    private Optional<AuditFailureTarget> resolveMaterial(String[] parts, int offset) {
        if (matches(parts, offset, "material", "receive")) {
            return target("MATERIAL_RECEIVE", null, "MATERIAL_BATCH");
        }
        if (matches(parts, offset, "material", "batches", "*", "freeze")) {
            return target("MATERIAL_FREEZE", parts[offset + 2], "MATERIAL_BATCH");
        }
        if (matches(parts, offset, "material", "batches", "*", "unfreeze")) {
            return target("MATERIAL_UNFREEZE", parts[offset + 2], "MATERIAL_BATCH");
        }
        if (matches(parts, offset, "material", "batches", "*", "return")) {
            return target("MATERIAL_RETURN", parts[offset + 2], "MATERIAL_BATCH");
        }
        if (matches(parts, offset, "material", "batches", "*", "inventory-count")) {
            return target("MATERIAL_COUNT", parts[offset + 2], "MATERIAL_BATCH");
        }
        if (matches(parts, offset, "material", "batches", "*", "incoming-inspection")) {
            return target("MATERIAL_IQC", parts[offset + 2], "MATERIAL_BATCH");
        }
        if (matches(parts, offset, "material", "suppliers", "*", "qualification", "evaluate")) {
            return target("SUPPLIER_QUALIFICATION_EVALUATE", parts[offset + 2], "SUPPLIER");
        }
        if (matches(parts, offset, "material", "suppliers", "*", "qualification-reviews")) {
            return target("SUPPLIER_QUALIFICATION_REVIEW_CREATE", parts[offset + 2], "SUPPLIER");
        }
        if (matches(parts, offset, "material", "suppliers", "qualification-reviews", "*", "decision")) {
            return target("SUPPLIER_QUALIFICATION_REVIEW_DECIDE", parts[offset + 3], "SUPPLIER_REVIEW");
        }
        if (matches(parts, offset, "material", "suppliers", "corrective-actions")) {
            return target("SUPPLIER_8D_CREATE", null, "SUPPLIER_8D");
        }
        if (matches(parts, offset, "material", "suppliers", "corrective-actions", "*", "close")) {
            return target("SUPPLIER_8D_CLOSE", parts[offset + 3], "SUPPLIER_8D");
        }
        if (matches(parts, offset, "material", "location-tasks")) {
            return target("MATERIAL_LOCATION_TASK_CREATE", null, "MATERIAL_LOCATION_TASK");
        }
        if (matches(parts, offset, "material", "location-tasks", "*", "assign")) {
            return target("MATERIAL_LOCATION_TASK_ASSIGN", parts[offset + 2], "MATERIAL_LOCATION_TASK");
        }
        if (matches(parts, offset, "material", "location-tasks", "*", "complete")) {
            return target("MATERIAL_LOCATION_TASK_COMPLETE", parts[offset + 2], "MATERIAL_LOCATION_TASK");
        }
        if (matches(parts, offset, "material", "location-tasks", "*", "cancel")) {
            return target("MATERIAL_LOCATION_TASK_CANCEL", parts[offset + 2], "MATERIAL_LOCATION_TASK");
        }
        return Optional.empty();
    }

    private Optional<AuditFailureTarget> resolveEquipment(String[] parts, int offset) {
        if (matches(parts, offset, "equipment", "events")) {
            return target("EQUIPMENT_EVENT", null, "EQUIPMENT");
        }
        if (matches(parts, offset, "equipment", "events", "*", "close")) {
            return target("EQUIPMENT_EVENT_CLOSE", parts[offset + 2], "EQUIPMENT");
        }
        if (matches(parts, offset, "equipment", "gateways")) {
            return target("EQUIPMENT_GATEWAY_REGISTER", null, "EQUIPMENT_GATEWAY");
        }
        if (matches(parts, offset, "equipment", "gateways", "*", "heartbeat")) {
            return target("EQUIPMENT_GATEWAY_HEARTBEAT", parts[offset + 2], "EQUIPMENT_GATEWAY");
        }
        if (matches(parts, offset, "equipment", "gateways", "*", "health-check")) {
            return target("EQUIPMENT_GATEWAY_HEALTH_CHECK", parts[offset + 2], "EQUIPMENT_GATEWAY");
        }
        if (matches(parts, offset, "equipment", "parameters", "report")) {
            return target("EAP_PARAMETER_REPORT", null, "EQUIPMENT");
        }
        if (matches(parts, offset, "equipment", "status", "report")) {
            return target("EAP_STATUS_REPORT", null, "EQUIPMENT");
        }
        if (matches(parts, offset, "equipment", "cycle-samples", "report")) {
            return target("EAP_CYCLE_REPORT", null, "EQUIPMENT");
        }
        if (matches(parts, offset, "equipment", "standard-cycles")) {
            return target("EQUIPMENT_STANDARD_CYCLE_PUBLISH", null, "EQUIPMENT");
        }
        if (matches(parts, offset, "equipment", "pm-tasks", "*", "complete")) {
            return target("EQUIPMENT_PM_COMPLETE", parts[offset + 2], "EQUIPMENT");
        }
        if (matches(parts, offset, "equipment", "recipe-downloads")) {
            return target("EQUIPMENT_RECIPE_DOWNLOAD", null, "EQUIPMENT");
        }
        return Optional.empty();
    }

    private Optional<AuditFailureTarget> resolveAdapters(String[] parts, int offset) {
        if (matches(parts, offset, "adapters", "erp", "orders")) {
            return target("ERP_ORDER_IMPORT", null, "ERP_ADAPTER");
        }
        if (matches(parts, offset, "adapters", "eap", "messages")) {
            return target("EAP_ADAPTER_MESSAGE", null, "EQUIPMENT");
        }
        if (matches(parts, offset, "adapters", "qms", "inspections")) {
            return target("QMS_INSPECTION_REPORT", null, "QMS_ADAPTER");
        }
        if (matches(parts, offset, "adapters", "wms", "material-readiness")) {
            return target("WMS_MATERIAL_READINESS", null, "WMS_ADAPTER");
        }
        if (matches(parts, offset, "adapters", "wms", "inventory-transactions")) {
            return target("WMS_INVENTORY_TRANSACTION", null, "WMS_ADAPTER");
        }
        return Optional.empty();
    }

    private boolean matches(String[] parts, int offset, String... pattern) {
        if (parts.length != offset + pattern.length) {
            return false;
        }
        for (int i = 0; i < pattern.length; i++) {
            String expected = pattern[i];
            if (!"*".equals(expected) && !expected.equals(parts[offset + i])) {
                return false;
            }
        }
        return true;
    }

    private Optional<AuditFailureTarget> target(String action, String bizNo, String bizType) {
        return Optional.of(new AuditFailureTarget(action, bizNo, bizType));
    }

    private boolean isWriteMethod(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
    }

    private String normalizedPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        } else if (path.startsWith("/api/")) {
            path = path.substring("/api".length());
        }
        if (path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
