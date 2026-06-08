package com.visionox.mes.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visionox.mes.auth.security.RolePermissionService;
import com.visionox.mes.common.BusinessException;
import com.visionox.mes.system.entity.PermissionChangeRequest;
import com.visionox.mes.system.mapper.PermissionChangeRequestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionChangeServiceTest {

    @Mock
    private PermissionChangeRequestMapper permissionChangeRequestMapper;

    @Mock
    private AuditLogService auditLogService;

    private RolePermissionService rolePermissionService;
    private PermissionChangeService permissionChangeService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        rolePermissionService = new RolePermissionService();
        objectMapper = new ObjectMapper();
        permissionChangeService = new PermissionChangeService(
                permissionChangeRequestMapper,
                rolePermissionService,
                auditLogService,
                objectMapper
        );
    }

    @Test
    void createChangeRequestShouldPersistSnapshotsAndAuditSubmitAction() {
        PermissionChangeRequest change = permissionChangeService.createChangeRequest(Map.of(
                "targetRole", "QE",
                "addButtons", List.of("ai:equipment-analyze"),
                "reason", "质量工程师需要联动设备异常分析"
        ));

        ArgumentCaptor<PermissionChangeRequest> captor = ArgumentCaptor.forClass(PermissionChangeRequest.class);
        verify(permissionChangeRequestMapper).insert(captor.capture());
        PermissionChangeRequest saved = captor.getValue();

        assertThat(change.getChangeNo()).startsWith("PCR");
        assertThat(saved.getTargetRole()).isEqualTo("QE");
        assertThat(saved.getStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(saved.getBeforeSnapshot()).contains("quality:mrb-review");
        assertThat(saved.getAfterSnapshot()).contains("ai:equipment-analyze");
        verify(auditLogService).record(eq("PERMISSION_CHANGE_SUBMIT"), eq(saved.getChangeNo()),
                eq("PERMISSION_CHANGE"), any(), eq(saved.getRequester()), eq("permission-service"), eq(saved.getAfterSnapshot()));
    }

    @Test
    void createChangeRequestShouldRejectEmptyDiff() {
        assertThatThrownBy(() -> permissionChangeService.createChangeRequest(Map.of("targetRole", "QE")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("权限变更内容为空");
    }

    @Test
    void reviewChangeRequestShouldApproveAndApplyRuntimeSnapshot() throws Exception {
        PermissionChangeRequest pending = new PermissionChangeRequest();
        pending.setChangeNo("PCR001");
        pending.setTargetRole("QE");
        pending.setStatus("PENDING_REVIEW");
        pending.setRequester("admin");
        pending.setAfterSnapshot(objectMapper.writeValueAsString(Map.of(
                "role", "QE",
                "menus", List.of("dashboard", "quality", "ai"),
                "buttons", List.of("ai:equipment-analyze"),
                "dataScope", "LINE",
                "domains", List.of("QUALITY", "AI")
        )));
        when(permissionChangeRequestMapper.selectOne(any())).thenReturn(pending);

        PermissionChangeRequest reviewed = permissionChangeService.reviewChangeRequest("PCR001", Map.of(
                "decision", "APPROVE",
                "reviewer", "admin",
                "reviewOpinion", "试点通过"
        ));

        assertThat(reviewed.getStatus()).isEqualTo("APPROVED");
        assertThat(reviewed.getReviewer()).isEqualTo("admin");
        assertThat(rolePermissionService.permissions("QE").get("buttons").toString()).contains("ai:equipment-analyze");
        verify(permissionChangeRequestMapper).updateById(pending);
        verify(auditLogService).record(eq("PERMISSION_CHANGE_APPROVE"), eq("PCR001"),
                eq("PERMISSION_CHANGE"), any(), eq("admin"), eq("permission-service"), eq(pending.getAfterSnapshot()));
    }

    @Test
    void reloadApprovedPermissionSnapshotsShouldApplyLatestSnapshotPerRole() throws Exception {
        PermissionChangeRequest qeLatest = approvedChange("PCR003", "QE", List.of("quality:mrb-review", "ai:equipment-analyze"), LocalDateTime.now());
        PermissionChangeRequest qeOld = approvedChange("PCR002", "QE", List.of("quality:mrb-review"), LocalDateTime.now().minusDays(1));
        PermissionChangeRequest eeLatest = approvedChange("PCR004", "EE", List.of("equipment:event-create", "ai:equipment-analyze"), LocalDateTime.now());
        when(permissionChangeRequestMapper.selectList(any())).thenReturn(List.of(qeLatest, qeOld, eeLatest));

        int applied = permissionChangeService.reloadApprovedPermissionSnapshots();

        assertThat(applied).isEqualTo(2);
        assertThat(rolePermissionService.permissions("QE").get("buttons").toString()).contains("ai:equipment-analyze");
        assertThat(rolePermissionService.permissions("EE").get("buttons").toString()).contains("equipment:event-create");
        verify(permissionChangeRequestMapper).selectList(any());
    }

    private PermissionChangeRequest approvedChange(String changeNo, String role, List<String> buttons,
                                                   LocalDateTime reviewedTime) throws Exception {
        PermissionChangeRequest change = new PermissionChangeRequest();
        change.setChangeNo(changeNo);
        change.setTargetRole(role);
        change.setStatus("APPROVED");
        change.setReviewedTime(reviewedTime);
        change.setUpdatedTime(reviewedTime);
        change.setAfterSnapshot(objectMapper.writeValueAsString(Map.of(
                "role", role,
                "menus", List.of("dashboard"),
                "buttons", buttons,
                "dataScope", "LINE",
                "domains", List.of("QUALITY", "AI")
        )));
        return change;
    }
}
