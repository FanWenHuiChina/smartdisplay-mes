package com.visionox.mes.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionox.mes.auth.security.AuthContext;
import com.visionox.mes.auth.security.RolePermissionService;
import com.visionox.mes.common.BusinessException;
import com.visionox.mes.system.entity.PermissionChangeRequest;
import com.visionox.mes.system.mapper.PermissionChangeRequestMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 权限变更申请、审批和审计闭环服务。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionChangeService {

    private static final TypeReference<LinkedHashMap<String, Object>> SNAPSHOT_TYPE = new TypeReference<>() {
    };

    private final PermissionChangeRequestMapper permissionChangeRequestMapper;
    private final RolePermissionService rolePermissionService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @EventListener(ApplicationReadyEvent.class)
    public void loadApprovedPermissionSnapshotsOnStartup() {
        int applied = reloadApprovedPermissionSnapshots();
        log.info("已从权限变更单恢复运行期权限快照: appliedRoles={}", applied);
    }

    @Transactional(rollbackFor = Exception.class)
    public PermissionChangeRequest createChangeRequest(Map<String, Object> request) {
        Map<String, Object> safeRequest = safeRequest(request);
        String targetRole = rolePermissionService.normalizeRole(text(safeRequest.get("targetRole"), "OPERATOR"));
        Map<String, Object> beforeSnapshot = rolePermissionService.permissionSnapshot(targetRole).toMap();
        Map<String, Object> afterSnapshot = afterSnapshot(targetRole, beforeSnapshot, safeRequest);
        if (beforeSnapshot.equals(afterSnapshot)) {
            throw new BusinessException(400, "权限变更内容为空，请至少调整菜单、按钮、数据范围或领域权限");
        }

        LocalDateTime now = LocalDateTime.now();
        PermissionChangeRequest change = new PermissionChangeRequest();
        change.setChangeNo(nextChangeNo(now));
        change.setTargetRole(targetRole);
        change.setChangeType(text(safeRequest.get("changeType"), "ROLE_PERMISSION_UPDATE"));
        change.setBeforeSnapshot(toJson(beforeSnapshot));
        change.setAfterSnapshot(toJson(afterSnapshot));
        change.setReason(text(safeRequest.get("reason"), "权限矩阵调整"));
        change.setStatus("PENDING_REVIEW");
        change.setRequester(text(safeRequest.get("requester"), AuthContext.username()));
        change.setCreatedTime(now);
        change.setUpdatedTime(now);
        permissionChangeRequestMapper.insert(change);

        auditLogService.record("PERMISSION_CHANGE_SUBMIT", change.getChangeNo(), "PERMISSION_CHANGE",
                "提交权限变更: " + targetRole, change.getRequester(), "permission-service", change.getAfterSnapshot());
        return change;
    }

    @Transactional(rollbackFor = Exception.class)
    public PermissionChangeRequest reviewChangeRequest(String changeNo, Map<String, Object> request) {
        PermissionChangeRequest change = findByChangeNo(changeNo);
        if (!"PENDING_REVIEW".equals(change.getStatus())) {
            throw new BusinessException(400, "权限变更单已闭环，不能重复审批: " + changeNo);
        }
        Map<String, Object> safeRequest = safeRequest(request);
        String decision = text(safeRequest.get("decision"), "APPROVE").trim().toUpperCase(Locale.ROOT);
        String reviewer = text(safeRequest.get("reviewer"), AuthContext.username());
        String opinion = text(safeRequest.get("reviewOpinion"), text(safeRequest.get("opinion"), ""));
        LocalDateTime now = LocalDateTime.now();

        if ("APPROVE".equals(decision) || "APPROVED".equals(decision)) {
            change.setStatus("APPROVED");
            rolePermissionService.applyPermissionSnapshot(change.getTargetRole(), fromJson(change.getAfterSnapshot()));
            auditLogService.record("PERMISSION_CHANGE_APPROVE", change.getChangeNo(), "PERMISSION_CHANGE",
                    "审批通过权限变更: " + change.getTargetRole(), reviewer, "permission-service", change.getAfterSnapshot());
        } else if ("REJECT".equals(decision) || "REJECTED".equals(decision)) {
            change.setStatus("REJECTED");
            auditLogService.record("PERMISSION_CHANGE_REJECT", change.getChangeNo(), "PERMISSION_CHANGE",
                    "驳回权限变更: " + change.getTargetRole(), reviewer, "permission-service", change.getAfterSnapshot());
        } else {
            throw new BusinessException(400, "审批结论只支持 APPROVE 或 REJECT");
        }

        change.setReviewer(reviewer);
        change.setReviewOpinion(opinion);
        change.setReviewedTime(now);
        change.setUpdatedTime(now);
        permissionChangeRequestMapper.updateById(change);
        return change;
    }

    public List<PermissionChangeRequest> listChangeRequests(String status) {
        LambdaQueryWrapper<PermissionChangeRequest> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(PermissionChangeRequest::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        wrapper.orderByDesc(PermissionChangeRequest::getCreatedTime).last("LIMIT 50");
        return permissionChangeRequestMapper.selectList(wrapper);
    }

    public int reloadApprovedPermissionSnapshots() {
        List<PermissionChangeRequest> approvedChanges = permissionChangeRequestMapper.selectList(
                new LambdaQueryWrapper<PermissionChangeRequest>()
                        .eq(PermissionChangeRequest::getStatus, "APPROVED")
                        .orderByDesc(PermissionChangeRequest::getReviewedTime)
                        .orderByDesc(PermissionChangeRequest::getUpdatedTime)
                        .orderByDesc(PermissionChangeRequest::getCreatedTime)
                        .last("LIMIT 200")
        );
        LinkedHashSet<String> appliedRoles = new LinkedHashSet<>();
        for (PermissionChangeRequest change : approvedChanges) {
            String role = rolePermissionService.normalizeRole(change.getTargetRole());
            if (appliedRoles.contains(role)) {
                continue;
            }
            try {
                rolePermissionService.applyPermissionSnapshot(role, fromJson(change.getAfterSnapshot()));
                appliedRoles.add(role);
            } catch (BusinessException e) {
                log.warn("权限快照恢复失败: changeNo={}, role={}, reason={}",
                        change.getChangeNo(), role, e.getMessage());
            }
        }
        return appliedRoles.size();
    }

    public PermissionChangeRequest findByChangeNo(String changeNo) {
        PermissionChangeRequest change = permissionChangeRequestMapper.selectOne(
                new LambdaQueryWrapper<PermissionChangeRequest>().eq(PermissionChangeRequest::getChangeNo, changeNo)
        );
        if (change == null) {
            throw new BusinessException(404, "权限变更单不存在: " + changeNo);
        }
        return change;
    }

    private Map<String, Object> afterSnapshot(String targetRole, Map<String, Object> beforeSnapshot,
                                              Map<String, Object> request) {
        Map<String, Object> after = new LinkedHashMap<>(beforeSnapshot);
        after.put("role", targetRole);

        List<String> menus = stringList(request.get("menus"), castList(beforeSnapshot.get("menus")));
        List<String> buttons = stringList(request.get("buttons"), castList(beforeSnapshot.get("buttons")));
        buttons = mergeButtons(buttons, request.get("addButtons"), request.get("removeButtons"));
        List<String> domains = stringList(request.get("domains"), castList(beforeSnapshot.get("domains")));

        after.put("menus", menus);
        after.put("buttons", buttons);
        after.put("dataScope", rolePermissionService.normalizeDataScope(
                text(request.get("dataScope"), String.valueOf(beforeSnapshot.get("dataScope")))));
        after.put("domains", domains);
        return after;
    }

    private List<String> mergeButtons(List<String> baseButtons, Object addButtons, Object removeButtons) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(baseButtons);
        merged.addAll(stringList(addButtons, List.of()));
        stringList(removeButtons, List.of()).forEach(merged::remove);
        return List.copyOf(merged);
    }

    private String nextChangeNo(LocalDateTime now) {
        return "PCR" + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(now)
                + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException("权限快照序列化失败", e);
        }
    }

    private Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json, SNAPSHOT_TYPE);
        } catch (JsonProcessingException e) {
            throw new BusinessException("权限快照解析失败", e);
        }
    }

    private Map<String, Object> safeRequest(Map<String, Object> request) {
        return request == null ? Map.of() : request;
    }

    private String text(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private List<String> castList(Object value) {
        return stringList(value, List.of());
    }

    private List<String> stringList(Object value, List<String> fallback) {
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
        return List.copyOf(new LinkedHashSet<>(items));
    }

    private void addString(List<String> items, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value).trim();
        if (!text.isBlank()) {
            items.add(text);
        }
    }
}
