package com.visionox.mes.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 试点版角色权限规则。
 */
@Component
public class RolePermissionService {

    private static final Set<String> SYSTEM_ROLES = Set.of("ADMIN");
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Map<String, String> ROLE_LINE_CODES = Map.of(
            "PLANNER", "LINE_01",
            "OPERATOR", "LINE_01",
            "QE", "LINE_01",
            "PE", "LINE_01",
            "EE", "LINE_01"
    );
    private static final List<String> ALL_MENUS = List.of(
            "dashboard", "order", "execution", "quality", "material", "trace", "master", "recipe", "equipment", "ai", "system"
    );
    private static final List<String> ALL_BUTTONS = List.of(
            "order:create", "order:release",
            "lot:track-in", "lot:track-out", "lot:hold", "lot:release", "lot:rework", "lot:scrap",
            "quality:mrb-review", "quality:mrb-approve", "quality:mrb-escalate", "quality:exception-close",
            "material:wms", "material:iqc", "material:supplier-manage",
            "bom:change", "bom:eco-approve",
            "recipe:publish", "equipment:event-create", "equipment:eap-ingest", "equipment:eap-gateway",
            "ai:yield-report", "ai:equipment-analyze", "ai:kb-ask", "ai:kb-import", "ai:kb-index",
            "system:user-read", "system:audit-read", "system:permission-change"
    );

    private final Map<String, PermissionSnapshot> runtimeOverrides = new ConcurrentHashMap<>();

    public String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "OPERATOR";
        }
        return switch (role.trim().toUpperCase(Locale.ROOT)) {
            case "ADMIN", "ADMINISTRATOR" -> "ADMIN";
            case "PLANNER", "PLAN" -> "PLANNER";
            case "OPERATOR", "OP" -> "OPERATOR";
            case "QE", "QUALITY", "QUALITY_ENGINEER" -> "QE";
            case "PE", "PROCESS", "PROCESS_ENGINEER", "ENGINEER" -> "PE";
            case "EE", "EQUIPMENT", "EQUIPMENT_ENGINEER" -> "EE";
            default -> role.trim().toUpperCase(Locale.ROOT);
        };
    }

    public String normalizeDataScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return "SELF";
        }
        return switch (scope.trim().toUpperCase(Locale.ROOT)) {
            case "ALL", "LINE", "SELF_SHIFT", "SELF" -> scope.trim().toUpperCase(Locale.ROOT);
            default -> "SELF";
        };
    }

    public boolean canAccess(String role, HttpServletRequest request) {
        String normalizedRole = normalizeRole(role);
        if ("ADMIN".equals(normalizedRole)) {
            return true;
        }
        String path = normalizedPath(request);
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        Set<String> buttons = new LinkedHashSet<>(permissionSnapshot(normalizedRole).buttons());

        if (path.startsWith("/v1/system/me/permissions") || path.startsWith("/system/me/permissions")) {
            return true;
        }
        if (path.startsWith("/v1/system") || path.startsWith("/system")) {
            return SYSTEM_ROLES.contains(normalizedRole);
        }
        if (isReadOnly(request)) {
            return true;
        }
        if (path.startsWith("/v1/orders") || path.startsWith("/orders")) {
            return path.contains("/release") ? buttons.contains("order:release") : buttons.contains("order:create");
        }
        if (path.contains("/track-in")) {
            return buttons.contains("lot:track-in");
        }
        if (path.contains("/track-out")) {
            return buttons.contains("lot:track-out");
        }
        if (path.contains("/hold")) {
            return buttons.contains("lot:hold");
        }
        if (path.contains("/release")) {
            return buttons.contains("lot:release");
        }
        if (path.contains("/rework")) {
            return buttons.contains("lot:rework");
        }
        if (path.contains("/scrap")) {
            return buttons.contains("lot:scrap");
        }
        if (path.contains("/mrb-review")) {
            return buttons.contains("quality:mrb-review");
        }
        if (path.contains("/mrb-records") && path.contains("/minutes")) {
            return buttons.contains("quality:mrb-review")
                    || buttons.contains("quality:mrb-approve")
                    || buttons.contains("quality:exception-close");
        }
        if (path.contains("/mrb-approvals/refresh-sla")) {
            return buttons.contains("quality:mrb-escalate");
        }
        if (path.contains("/mrb-approvals")) {
            return buttons.contains("quality:mrb-approve");
        }
        if (path.contains("/quality") && path.contains("/close")) {
            return buttons.contains("quality:exception-close");
        }
        if (path.startsWith("/v1/quality") || path.startsWith("/quality")) {
            return buttons.contains("quality:mrb-review")
                    || buttons.contains("quality:mrb-approve")
                    || buttons.contains("quality:exception-close");
        }
        if (path.startsWith("/v1/recipes") || path.startsWith("/recipes")) {
            return buttons.contains("recipe:publish");
        }
        if (path.startsWith("/v1/boms/eco-approvals") || path.startsWith("/boms/eco-approvals")) {
            return buttons.contains("bom:eco-approve");
        }
        if (path.startsWith("/v1/boms") || path.startsWith("/boms")) {
            return buttons.contains("bom:change");
        }
        if (path.startsWith("/v1/adapters/erp") || path.startsWith("/adapters/erp")) {
            return buttons.contains("order:create");
        }
        if (path.startsWith("/v1/adapters/eap") || path.startsWith("/adapters/eap")) {
            return buttons.contains("equipment:eap-ingest");
        }
        if (path.startsWith("/v1/equipment/gateways") || path.startsWith("/equipment/gateways")) {
            return buttons.contains("equipment:eap-gateway");
        }
        if (path.startsWith("/v1/equipment") || path.startsWith("/equipment")) {
            return buttons.contains("equipment:event-create");
        }
        if (path.startsWith("/v1/material") || path.startsWith("/material")) {
            if (path.contains("/suppliers")) {
                return buttons.contains("material:supplier-manage") || buttons.contains("material:iqc");
            }
            if (path.contains("/incoming-inspection")) {
                return buttons.contains("material:iqc");
            }
            return buttons.contains("material:wms");
        }
        if (path.startsWith("/v1/ai/reports/yield") || path.startsWith("/ai/reports/yield")) {
            return buttons.contains("ai:yield-report");
        }
        if (path.startsWith("/v1/ai/equipment/analyze") || path.startsWith("/ai/equipment/analyze")) {
            return buttons.contains("ai:equipment-analyze");
        }
        if (path.startsWith("/v1/ai/kb/ask") || path.startsWith("/ai/kb/ask")) {
            return buttons.contains("ai:kb-ask");
        }
        if (path.startsWith("/v1/ai/kb/import") || path.startsWith("/ai/kb/import")) {
            return buttons.contains("ai:kb-import");
        }
        if (path.startsWith("/v1/ai/kb/index-jobs") || path.startsWith("/ai/kb/index-jobs")) {
            return buttons.contains("ai:kb-index");
        }
        return List.of("POST", "PUT", "PATCH", "DELETE").contains(method)
                && "ADMIN".equals(normalizedRole);
    }

    public Map<String, Object> permissions(String role) {
        return permissions(role, AuthContext.username());
    }

    public Map<String, Object> permissions(String role, String username) {
        PermissionSnapshot snapshot = permissionSnapshot(role);
        Map<String, Object> data = snapshot.toMap();
        data.put("dataScopeSql", dataScopeCondition(snapshot.role(), username,
                "t", "line_code", "created_by", "created_time").toMap());
        return data;
    }

    public PermissionSnapshot permissionSnapshot(String role) {
        String normalizedRole = normalizeRole(role);
        PermissionSnapshot override = runtimeOverrides.get(normalizedRole);
        return override == null ? defaultSnapshot(normalizedRole) : override;
    }

    public PermissionSnapshot defaultSnapshot(String role) {
        String normalizedRole = normalizeRole(role);
        return new PermissionSnapshot(
                normalizedRole,
                defaultMenus(normalizedRole),
                defaultButtons(normalizedRole),
                defaultDataScope(normalizedRole),
                defaultDomains(normalizedRole)
        );
    }

    public void applyPermissionSnapshot(String role, Map<String, Object> snapshot) {
        String normalizedRole = normalizeRole(role);
        runtimeOverrides.put(normalizedRole, snapshotFromMap(normalizedRole, snapshot));
    }

    public void clearPermissionSnapshot(String role) {
        runtimeOverrides.remove(normalizeRole(role));
    }

    public DataScopeCondition dataScopeCondition(String role, String username, String tableAlias,
                                                 String lineCodeColumn, String ownerColumn,
                                                 String createdTimeColumn) {
        PermissionSnapshot snapshot = permissionSnapshot(role);
        String scope = normalizeDataScope(snapshot.dataScope());
        if ("ALL".equals(scope)) {
            return DataScopeCondition.unrestricted(scope);
        }
        if ("LINE".equals(scope)) {
            return lineScope(scope, snapshot.role(), tableAlias, lineCodeColumn);
        }
        if ("SELF_SHIFT".equals(scope)) {
            if (hasColumn(ownerColumn) && hasColumn(createdTimeColumn)) {
                return new DataScopeCondition(scope,
                        qualifiedColumn(tableAlias, ownerColumn) + " = {0} AND "
                                + qualifiedColumn(tableAlias, createdTimeColumn) + " >= {1}",
                        List.of(valueOr(username, "system"), LocalDate.now().atStartOfDay()));
            }
            if (hasColumn(lineCodeColumn)) {
                return lineScope(scope, snapshot.role(), tableAlias, lineCodeColumn);
            }
            return DataScopeCondition.deny(scope);
        }
        if (hasColumn(ownerColumn)) {
            return new DataScopeCondition(scope,
                    qualifiedColumn(tableAlias, ownerColumn) + " = {0}",
                    List.of(valueOr(username, "system")));
        }
        if (hasColumn(lineCodeColumn)) {
            return lineScope(scope, snapshot.role(), tableAlias, lineCodeColumn);
        }
        return DataScopeCondition.deny(scope);
    }

    private DataScopeCondition lineScope(String scope, String role, String tableAlias, String lineCodeColumn) {
        if (!hasColumn(lineCodeColumn)) {
            return DataScopeCondition.deny(scope);
        }
        String lineCode = ROLE_LINE_CODES.getOrDefault(normalizeRole(role), "LINE_01");
        return new DataScopeCondition(scope,
                qualifiedColumn(tableAlias, lineCodeColumn) + " = {0}",
                List.of(lineCode));
    }

    private PermissionSnapshot snapshotFromMap(String role, Map<String, Object> snapshot) {
        PermissionSnapshot defaults = defaultSnapshot(role);
        Map<String, Object> source = snapshot == null ? Map.of() : snapshot;
        return new PermissionSnapshot(
                normalizeRole(text(source.get("role"), role)),
                stringList(source.get("menus"), defaults.menus()),
                stringList(source.get("buttons"), defaults.buttons()),
                normalizeDataScope(text(source.get("dataScope"), defaults.dataScope())),
                stringList(source.get("domains"), defaults.domains())
        );
    }

    private List<String> defaultMenus(String role) {
        return switch (role) {
            case "ADMIN" -> ALL_MENUS;
            case "PLANNER" -> List.of("dashboard", "order", "trace");
            case "OPERATOR" -> List.of("dashboard", "execution", "trace");
            case "QE" -> List.of("dashboard", "quality", "material", "trace", "ai");
            case "PE" -> List.of("dashboard", "master", "recipe", "ai");
            case "EE" -> List.of("dashboard", "equipment", "trace", "ai");
            default -> List.of("dashboard", "execution");
        };
    }

    private List<String> defaultButtons(String role) {
        return switch (role) {
            case "ADMIN" -> ALL_BUTTONS;
            case "PLANNER" -> List.of("order:create", "order:release", "bom:eco-approve");
            case "OPERATOR" -> List.of("lot:track-in", "lot:track-out");
            case "QE" -> List.of(
                    "lot:hold", "lot:release", "lot:rework", "lot:scrap",
                    "quality:mrb-review", "quality:mrb-approve", "quality:mrb-escalate", "quality:exception-close", "material:iqc", "material:supplier-manage", "bom:eco-approve",
                    "ai:yield-report", "ai:kb-ask", "ai:kb-import", "ai:kb-index"
            );
            case "PE" -> List.of("quality:mrb-approve", "quality:mrb-escalate", "recipe:publish", "bom:change", "bom:eco-approve", "ai:yield-report", "ai:kb-ask", "ai:kb-import", "ai:kb-index");
            case "EE" -> List.of("quality:mrb-approve", "quality:mrb-escalate", "bom:eco-approve", "equipment:event-create", "equipment:eap-ingest", "equipment:eap-gateway", "ai:equipment-analyze", "ai:kb-ask", "ai:kb-import", "ai:kb-index");
            default -> List.of();
        };
    }

    private String defaultDataScope(String role) {
        return switch (role) {
            case "ADMIN" -> "ALL";
            case "PLANNER", "QE", "PE", "EE" -> "LINE";
            case "OPERATOR" -> "SELF_SHIFT";
            default -> "SELF";
        };
    }

    private List<String> defaultDomains(String role) {
        return switch (role) {
            case "ADMIN" -> List.of("ORDER", "EXECUTION", "QUALITY", "MATERIAL", "RECIPE", "EQUIPMENT", "AI", "SYSTEM");
            case "PLANNER" -> List.of("ORDER");
            case "OPERATOR" -> List.of("EXECUTION");
            case "QE" -> List.of("QUALITY", "MATERIAL", "AI");
            case "PE" -> List.of("RECIPE", "AI");
            case "EE" -> List.of("EQUIPMENT", "AI");
            default -> List.of("EXECUTION");
        };
    }

    private boolean isReadOnly(HttpServletRequest request) {
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
    }

    private String normalizedPath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String uri = request.getRequestURI();
        if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private String qualifiedColumn(String tableAlias, String column) {
        String safeColumn = safeIdentifier(column);
        if (tableAlias == null || tableAlias.isBlank()) {
            return safeColumn;
        }
        return safeIdentifier(tableAlias) + "." + safeColumn;
    }

    private String safeIdentifier(String value) {
        if (value == null || !SQL_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("非法SQL标识符: " + value);
        }
        return value;
    }

    private boolean hasColumn(String column) {
        return column != null && !column.isBlank();
    }

    private static List<String> stringList(Object value, List<String> fallback) {
        List<String> items = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> addString(items, item));
        } else if (value instanceof String text && !text.isBlank()) {
            for (String item : text.split("[,;\\s]+")) {
                addString(items, item);
            }
        }
        if (items.isEmpty() && fallback != null) {
            items.addAll(fallback);
        }
        return immutableDistinct(items);
    }

    private static void addString(List<String> items, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (!text.isBlank()) {
            items.add(text);
        }
    }

    private static List<String> immutableDistinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values == null ? List.of() : values));
    }

    private String text(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record PermissionSnapshot(String role, List<String> menus, List<String> buttons,
                                     String dataScope, List<String> domains) {

        public PermissionSnapshot {
            menus = immutableDistinct(menus);
            buttons = immutableDistinct(buttons);
            dataScope = dataScope == null || dataScope.isBlank() ? "SELF" : dataScope;
            domains = immutableDistinct(domains);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("role", role);
            data.put("menus", menus);
            data.put("buttons", buttons);
            data.put("dataScope", dataScope);
            data.put("domains", domains);
            return data;
        }
    }

    public record DataScopeCondition(String scope, String sql, List<Object> parameters) {

        public DataScopeCondition {
            sql = sql == null ? "" : sql;
            parameters = List.copyOf(parameters == null ? List.of() : parameters);
        }

        static DataScopeCondition unrestricted(String scope) {
            return new DataScopeCondition(scope, "", List.of());
        }

        static DataScopeCondition deny(String scope) {
            return new DataScopeCondition(scope, "1 = 0", List.of());
        }

        public boolean unrestricted() {
            return sql.isBlank();
        }

        public Map<String, Object> toMap() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("scope", scope);
            data.put("sql", sql);
            data.put("parameters", parameters);
            data.put("unrestricted", unrestricted());
            return data;
        }
    }
}
