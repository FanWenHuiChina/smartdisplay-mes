package com.visionox.mes.quality.service;

import com.visionox.mes.auth.security.RolePermissionService;
import com.visionox.mes.lot.dto.TrackOutRequest;
import com.visionox.mes.lot.entity.HoldRecord;
import com.visionox.mes.lot.entity.Lot;
import com.visionox.mes.lot.entity.LotStepRecord;
import com.visionox.mes.lot.mapper.HoldRecordMapper;
import com.visionox.mes.lot.mapper.LotMapper;
import com.visionox.mes.quality.entity.ExceptionEvent;
import com.visionox.mes.quality.entity.QualityDefectRecord;
import com.visionox.mes.quality.entity.QualityInspection;
import com.visionox.mes.quality.entity.QualityMrbApprovalTask;
import com.visionox.mes.quality.entity.QualityMrbAttachment;
import com.visionox.mes.quality.entity.QualityMrbMinutes;
import com.visionox.mes.quality.entity.QualityMrbRecord;
import com.visionox.mes.quality.mapper.ExceptionEventMapper;
import com.visionox.mes.quality.mapper.QualityDefectRecordMapper;
import com.visionox.mes.quality.mapper.QualityInspectionMapper;
import com.visionox.mes.quality.mapper.QualityMrbApprovalTaskMapper;
import com.visionox.mes.quality.mapper.QualityMrbAttachmentMapper;
import com.visionox.mes.quality.mapper.QualityMrbMinutesMapper;
import com.visionox.mes.quality.mapper.QualityMrbRecordMapper;
import com.visionox.mes.recipe.mapper.RecipeMapper;
import com.visionox.mes.recipe.mapper.RecipeParamMapper;
import com.visionox.mes.system.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QualityServiceTest {

    @Mock
    private QualityInspectionMapper inspectionMapper;

    @Mock
    private QualityDefectRecordMapper defectRecordMapper;

    @Mock
    private ExceptionEventMapper exceptionEventMapper;

    @Mock
    private QualityMrbRecordMapper mrbRecordMapper;

    @Mock
    private QualityMrbAttachmentMapper mrbAttachmentMapper;

    @Mock
    private QualityMrbMinutesMapper mrbMinutesMapper;

    @Mock
    private QualityMrbApprovalTaskMapper mrbApprovalTaskMapper;

    @Mock
    private RecipeMapper recipeMapper;

    @Mock
    private RecipeParamMapper recipeParamMapper;

    @Mock
    private LotMapper lotMapper;

    @Mock
    private HoldRecordMapper holdRecordMapper;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private RolePermissionService rolePermissionService;

    @InjectMocks
    private QualityService qualityService;

    @Test
    void evaluateTrackOutShouldCreateExceptionAndAutoHoldWhenManualResultIsNg() {
        Lot lot = lot("LOT001");
        LotStepRecord stepRecord = stepRecord();
        when(recipeMapper.selectOne(any())).thenReturn(null);
        when(holdRecordMapper.selectCount(any())).thenReturn(0L);
        when(lotMapper.selectOne(any())).thenReturn(lot);

        String result = qualityService.evaluateTrackOut(lot, stepRecord, ngRequest());

        assertThat(result).isEqualTo("NG");

        ArgumentCaptor<QualityInspection> inspectionCaptor = ArgumentCaptor.forClass(QualityInspection.class);
        verify(inspectionMapper).insert(inspectionCaptor.capture());
        QualityInspection inspection = inspectionCaptor.getValue();
        assertThat(inspection.getLotNo()).isEqualTo("LOT001");
        assertThat(inspection.getResult()).isEqualTo("NG");
        assertThat(inspection.getItemCode()).isEqualTo("PROCESS_RESULT");
        assertThat(inspection.getRemark()).isEqualTo("涂布厚度不足");

        ArgumentCaptor<QualityDefectRecord> defectCaptor = ArgumentCaptor.forClass(QualityDefectRecord.class);
        verify(defectRecordMapper).insert(defectCaptor.capture());
        QualityDefectRecord defect = defectCaptor.getValue();
        assertThat(defect.getLotNo()).isEqualTo("LOT001");
        assertThat(defect.getDefectCode()).isEqualTo("D-PROCESS-NG");
        assertThat(defect.getDisposition()).isEqualTo("WAIT_MRB");

        ArgumentCaptor<ExceptionEvent> eventCaptor = ArgumentCaptor.forClass(ExceptionEvent.class);
        verify(exceptionEventMapper).insert(eventCaptor.capture());
        ExceptionEvent event = eventCaptor.getValue();
        assertThat(event.getLotNo()).isEqualTo("LOT001");
        assertThat(event.getStatus()).isEqualTo("OPEN");
        assertThat(event.getOwnerRole()).isEqualTo("QE");

        assertThat(lot.getStatus()).isEqualTo("HOLD");
        assertThat(lot.getHoldFlag()).isEqualTo(1);
        verify(lotMapper).updateById(lot);

        ArgumentCaptor<HoldRecord> holdCaptor = ArgumentCaptor.forClass(HoldRecord.class);
        verify(holdRecordMapper).insert(holdCaptor.capture());
        HoldRecord hold = holdCaptor.getValue();
        assertThat(hold.getLotNo()).isEqualTo("LOT001");
        assertThat(hold.getHoldType()).isEqualTo("QUALITY");
        assertThat(hold.getHoldBy()).isEqualTo("system");
        assertThat(hold.getStatus()).isEqualTo("HOLD");

        verify(auditLogService).record(eq("QUALITY_INSPECTION"), eq("LOT001"), eq("LOT"), any(), eq("system"), eq("quality-service"), any());
        verify(auditLogService).record(eq("EXCEPTION_CREATE"), any(), eq("EXCEPTION"), any(), eq("system"), eq("quality-service"), any());
        verify(auditLogService).record(eq("LOT_HOLD"), eq("LOT001"), eq("LOT"), any(), eq("system"), eq("quality-service"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void reportQmsInspectionShouldCreateDefectExceptionAndHoldWhenNg() {
        Lot lot = lot("LOT001");
        lot.setCurrentStepCode("COATING");
        lot.setCurrentEquipmentCode("COATER_01");
        when(lotMapper.selectOne(any())).thenReturn(lot);
        when(holdRecordMapper.selectCount(any())).thenReturn(0L);

        Map<String, Object> result = qualityService.reportQmsInspection(Map.of(
                "lotNo", "LOT001",
                "sourceSystem", "qms-adapter",
                "operator", "qe1001",
                "items", List.of(
                        Map.of("itemCode", "APPEARANCE", "itemName", "外观", "result", "OK"),
                        Map.of("itemCode", "MURA", "itemName", "Mura", "result", "FAIL", "defectCode", "D-MURA")
                )
        ));

        assertThat(result.get("result")).isEqualTo("NG");
        assertThat(result.get("inspectionCount")).isEqualTo(2);
        assertThat(result.get("defectCount")).isEqualTo(1);
        assertThat(result.get("holdApplied")).isEqualTo(true);
        assertThat((List<Map<String, Object>>) result.get("inspections")).hasSize(2);
        assertThat(result.get("exceptionEvent")).isNotNull();

        ArgumentCaptor<QualityInspection> inspectionCaptor = ArgumentCaptor.forClass(QualityInspection.class);
        verify(inspectionMapper, org.mockito.Mockito.times(2)).insert(inspectionCaptor.capture());
        assertThat(inspectionCaptor.getAllValues()).extracting(QualityInspection::getResult)
                .containsExactly("OK", "NG");

        ArgumentCaptor<QualityDefectRecord> defectCaptor = ArgumentCaptor.forClass(QualityDefectRecord.class);
        verify(defectRecordMapper).insert(defectCaptor.capture());
        assertThat(defectCaptor.getValue().getDefectCode()).isEqualTo("D-MURA");

        ArgumentCaptor<ExceptionEvent> eventCaptor = ArgumentCaptor.forClass(ExceptionEvent.class);
        verify(exceptionEventMapper).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getLotNo()).isEqualTo("LOT001");
        assertThat(eventCaptor.getValue().getOwnerRole()).isEqualTo("QE");

        assertThat(lot.getStatus()).isEqualTo("HOLD");
        assertThat(lot.getHoldFlag()).isEqualTo(1);
        verify(lotMapper).updateById(lot);
        verify(holdRecordMapper).insert(any(HoldRecord.class));
        verify(auditLogService).record(eq("QMS_INSPECTION_REPORT"), eq("LOT001"), eq("LOT"), any(), eq("qe1001"), eq("quality-service"), any());
        verify(auditLogService).record(eq("EXCEPTION_CREATE"), any(), eq("EXCEPTION"), any(), eq("system"), eq("quality-service"), isNull());
        verify(auditLogService).record(eq("LOT_HOLD"), eq("LOT001"), eq("LOT"), any(), eq("qe1001"), eq("quality-service"), isNull());
    }

    @Test
    void reviewExceptionShouldRecordMrbDecisionAndUpdateDefectDisposition() {
        ExceptionEvent event = exceptionEvent("EX001", "OPEN");
        QualityDefectRecord defect = new QualityDefectRecord();
        defect.setLotNo("LOT001");
        defect.setStatus("OPEN");
        defect.setDisposition("WAIT_MRB");
        when(exceptionEventMapper.selectOne(any())).thenReturn(event);
        when(defectRecordMapper.selectList(any())).thenReturn(List.of(defect));

        Map<String, Object> row = qualityService.reviewException("EX001", Map.of(
                "dispositionAction", "REWORK",
                "mrbOpinion", "涂胶厚度超限，回 COATING 返工",
                "reviewer", "qe1001",
                "meetingNo", "MRB-001",
                "participants", "qe1001,pe1002",
                "attachments", List.of(Map.of(
                        "fileName", "recheck.xlsx",
                        "fileUrl", "qms://mrb/recheck.xlsx",
                        "fileHash", "sha256:recheck"
                ))
        ));

        assertThat(row.get("status")).isEqualTo("MRB_PENDING");
        assertThat(row.get("mrbResult")).isEqualTo("REWORK");
        assertThat(row.get("mrbReviewer")).isEqualTo("qe1001");
        assertThat(event.getMrbTime()).isNotNull();
        assertThat(defect.getStatus()).isEqualTo("PROCESSING");
        assertThat(defect.getDisposition()).isEqualTo("REWORK");
        verify(exceptionEventMapper).updateById(event);
        verify(defectRecordMapper).updateById(defect);
        ArgumentCaptor<QualityMrbRecord> mrbCaptor = ArgumentCaptor.forClass(QualityMrbRecord.class);
        verify(mrbRecordMapper).insert(mrbCaptor.capture());
        QualityMrbRecord mrb = mrbCaptor.getValue();
        assertThat(mrb.getEventNo()).isEqualTo("EX001");
        assertThat(mrb.getReviewType()).isEqualTo("REVIEW");
        assertThat(mrb.getDispositionAction()).isEqualTo("REWORK");
        assertThat(mrb.getApprovalStatus()).isEqualTo("PENDING");
        assertThat(mrb.getMeetingNo()).isEqualTo("MRB-001");
        assertThat(mrb.getParticipants()).isEqualTo("qe1001,pe1002");

        ArgumentCaptor<QualityMrbAttachment> attachmentCaptor = ArgumentCaptor.forClass(QualityMrbAttachment.class);
        verify(mrbAttachmentMapper).insert(attachmentCaptor.capture());
        QualityMrbAttachment attachment = attachmentCaptor.getValue();
        assertThat(attachment.getMrbNo()).isEqualTo(mrb.getMrbNo());
        assertThat(attachment.getEventNo()).isEqualTo("EX001");
        assertThat(attachment.getFileName()).isEqualTo("recheck.xlsx");
        assertThat(attachment.getFileHash()).isEqualTo("sha256:recheck");
        verify(mrbRecordMapper).updateById(mrb);
        ArgumentCaptor<QualityMrbApprovalTask> approvalCaptor = ArgumentCaptor.forClass(QualityMrbApprovalTask.class);
        verify(mrbApprovalTaskMapper, org.mockito.Mockito.atLeastOnce()).insert(approvalCaptor.capture());
        assertThat(approvalCaptor.getAllValues()).extracting(QualityMrbApprovalTask::getApprovalRole)
                .contains("QE", "PE", "EE");
        assertThat(approvalCaptor.getAllValues()).allSatisfy(task -> {
            assertThat(task.getSlaLevel()).isEqualTo("CRITICAL");
            assertThat(task.getDueTime()).isNotNull();
            assertThat(task.getEscalationRole()).isNotBlank();
        });
        assertThat(approvalCaptor.getAllValues()).filteredOn(task -> "QE".equals(task.getApprovalRole()))
                .singleElement()
                .extracting(QualityMrbApprovalTask::getSlaHours)
                .isEqualTo(2);
        verify(auditLogService).record(eq("MRB_REVIEW"), eq("EX001"), eq("EXCEPTION"), any(), eq("qe1001"), eq("quality-service"), any());
    }

    @Test
    void reviewExceptionShouldCreateInitialMrbMinutesWhenRequestContainsMinutes() {
        ExceptionEvent event = exceptionEvent("EX001", "OPEN");
        when(exceptionEventMapper.selectOne(any())).thenReturn(event);
        when(defectRecordMapper.selectList(any())).thenReturn(List.of());
        when(mrbMinutesMapper.selectList(any())).thenReturn(List.of());

        qualityService.reviewException("EX001", Map.of(
                "dispositionAction", "RELEASE",
                "riskLevel", "P2",
                "reviewer", "qe1001",
                "meetingMinutes", "会议结论：复测通过，允许解除 Hold。",
                "minutesSummary", "复测通过"
        ));

        ArgumentCaptor<QualityMrbMinutes> minutesCaptor = ArgumentCaptor.forClass(QualityMrbMinutes.class);
        verify(mrbMinutesMapper).insert(minutesCaptor.capture());
        QualityMrbMinutes minutes = minutesCaptor.getValue();
        assertThat(minutes.getMrbNo()).startsWith("MRB-");
        assertThat(minutes.getEventNo()).isEqualTo("EX001");
        assertThat(minutes.getLotNo()).isEqualTo("LOT001");
        assertThat(minutes.getVersionNo()).isEqualTo(1);
        assertThat(minutes.getMinutesContent()).isEqualTo("会议结论：复测通过，允许解除 Hold。");
        assertThat(minutes.getSummary()).isEqualTo("复测通过");
        assertThat(minutes.getSourceAction()).isEqualTo("REVIEW");
        verify(auditLogService).record(eq("MRB_MINUTES_CREATE"), eq(minutes.getMinutesNo()), eq("MRB_MINUTES"),
                any(), eq("qe1001"), eq("quality-service"), any());
    }

    @Test
    void createMrbMinutesShouldAppendNextVersion() {
        QualityMrbRecord record = mrbRecord("MRB001", "EX001");
        QualityMrbMinutes existing = mrbMinutes("MRBM001", "MRB001", 1);
        when(mrbRecordMapper.selectOne(any())).thenReturn(record);
        when(exceptionEventMapper.selectOne(any())).thenReturn(exceptionEvent("EX001", "MRB_REVIEWED"));
        when(mrbMinutesMapper.selectList(any())).thenReturn(List.of(existing));

        Map<String, Object> row = qualityService.createMrbMinutes("MRB001", Map.of(
                "minutesContent", "第二版：补充 PE 参数窗口确认意见。",
                "summary", "补充 PE 意见",
                "actionItems", "PE确认参数窗口；QE复核放行条件",
                "editor", "qe1001",
                "changeReason", "补充会议结论"
        ));

        ArgumentCaptor<QualityMrbMinutes> minutesCaptor = ArgumentCaptor.forClass(QualityMrbMinutes.class);
        verify(mrbMinutesMapper).insert(minutesCaptor.capture());
        QualityMrbMinutes minutes = minutesCaptor.getValue();
        assertThat(minutes.getMrbNo()).isEqualTo("MRB001");
        assertThat(minutes.getVersionNo()).isEqualTo(2);
        assertThat(minutes.getMinutesContent()).isEqualTo("第二版：补充 PE 参数窗口确认意见。");
        assertThat(minutes.getActionItems()).contains("PE");
        assertThat(minutes.getSourceAction()).isEqualTo("MANUAL");
        assertThat(row.get("versionNo")).isEqualTo(2);
        verify(auditLogService).record(eq("MRB_MINUTES_CREATE"), eq(minutes.getMinutesNo()), eq("MRB_MINUTES"),
                any(), eq("qe1001"), eq("quality-service"), any());
    }

    @Test
    void inspectionRowsShouldApplyLotDataScope() {
        when(rolePermissionService.dataScopeCondition(any(), any(), any(), any(), any(), any()))
                .thenReturn(new RolePermissionService.DataScopeCondition("LINE", "line_code = {0}", List.of("LINE_01")));
        when(inspectionMapper.selectList(any())).thenReturn(List.of());

        assertThat(qualityService.inspectionRows(null)).isEmpty();

        verify(rolePermissionService).dataScopeCondition(any(), any(), eq(""), eq("line_code"), isNull(), isNull());
        verify(inspectionMapper).selectList(any());
    }

    @Test
    void exceptionRowsShouldApplyLotDataScope() {
        when(rolePermissionService.dataScopeCondition(any(), any(), any(), any(), any(), any()))
                .thenReturn(new RolePermissionService.DataScopeCondition("LINE", "line_code = {0}", List.of("LINE_01")));
        when(exceptionEventMapper.selectList(any())).thenReturn(List.of());

        assertThat(qualityService.exceptionRows(null)).isEmpty();

        verify(rolePermissionService).dataScopeCondition(any(), any(), eq(""), eq("line_code"), isNull(), isNull());
        verify(exceptionEventMapper).selectList(any());
    }

    @Test
    void closeExceptionShouldRequireConclusion() {
        when(exceptionEventMapper.selectOne(any())).thenReturn(exceptionEvent("EX001", "MRB_REVIEWED"));

        assertThatThrownBy(() -> qualityService.closeException("EX001", Map.of("dispositionAction", "RELEASE")))
                .hasMessageContaining("处置结论");
    }

    @Test
    void closeExceptionShouldRejectWhenApprovalTaskIsPending() {
        ExceptionEvent event = exceptionEvent("EX001", "MRB_PENDING");
        QualityMrbApprovalTask task = approvalTask("MRBT001", "MRB001", "PENDING");
        when(exceptionEventMapper.selectOne(any())).thenReturn(event);
        when(mrbApprovalTaskMapper.selectList(any())).thenReturn(List.of(task));

        assertThatThrownBy(() -> qualityService.closeException("EX001", Map.of(
                "dispositionAction", "RELEASE",
                "closeConclusion", "等待关闭",
                "closedBy", "qe1001"
        ))).hasMessageContaining("会签未完成");
    }

    @Test
    void closeExceptionShouldRejectWhenApprovalTaskIsEscalated() {
        ExceptionEvent event = exceptionEvent("EX001", "MRB_PENDING");
        QualityMrbApprovalTask task = approvalTask("MRBT001", "MRB001", "ESCALATED");
        when(exceptionEventMapper.selectOne(any())).thenReturn(event);
        when(mrbApprovalTaskMapper.selectList(any())).thenReturn(List.of(task));

        assertThatThrownBy(() -> qualityService.closeException("EX001", Map.of(
                "dispositionAction", "RELEASE",
                "closeConclusion", "等待主管升级会签",
                "closedBy", "qe1001"
        ))).hasMessageContaining("会签未完成");
    }

    @Test
    void closeExceptionShouldCloseEventAndDefects() {
        ExceptionEvent event = exceptionEvent("EX001", "MRB_REVIEWED");
        event.setMrbResult("RELEASE");
        QualityDefectRecord defect = new QualityDefectRecord();
        defect.setLotNo("LOT001");
        defect.setStatus("PROCESSING");
        when(exceptionEventMapper.selectOne(any())).thenReturn(event);
        when(defectRecordMapper.selectList(any())).thenReturn(List.of(defect));
        when(mrbApprovalTaskMapper.selectList(any())).thenReturn(List.of(approvalTask("MRBT001", "MRB001", "APPROVED")));

        Map<String, Object> row = qualityService.closeException("EX001", Map.of(
                "dispositionAction", "RELEASE",
                "closeConclusion", "复判 OK，解除 Hold 后继续流转",
                "closedBy", "qe1001",
                "rootCause", "涂胶头短时波动"
        ));

        assertThat(row.get("status")).isEqualTo("CLOSED");
        assertThat(row.get("closeConclusion")).isEqualTo("复判 OK，解除 Hold 后继续流转");
        assertThat(row.get("rootCause")).isEqualTo("涂胶头短时波动");
        assertThat(event.getClosedTime()).isNotNull();
        assertThat(defect.getStatus()).isEqualTo("CLOSED");
        assertThat(defect.getDisposition()).isEqualTo("RELEASE");
        verify(exceptionEventMapper).updateById(event);
        verify(defectRecordMapper).updateById(defect);
        ArgumentCaptor<QualityMrbRecord> mrbCaptor = ArgumentCaptor.forClass(QualityMrbRecord.class);
        verify(mrbRecordMapper).insert(mrbCaptor.capture());
        QualityMrbRecord mrb = mrbCaptor.getValue();
        assertThat(mrb.getReviewType()).isEqualTo("CLOSE");
        assertThat(mrb.getDispositionAction()).isEqualTo("RELEASE");
        assertThat(mrb.getOpinion()).isEqualTo("复判 OK，解除 Hold 后继续流转");
        verify(auditLogService).record(eq("EXCEPTION_CLOSE"), eq("EX001"), eq("EXCEPTION"), any(), eq("qe1001"), eq("quality-service"), any());
    }

    @Test
    void decideMrbApprovalTaskShouldApproveAndRefreshMrbStatus() {
        QualityMrbApprovalTask task = approvalTask("MRBT001", "MRB001", "PENDING");
        QualityMrbRecord record = new QualityMrbRecord();
        record.setMrbNo("MRB001");
        record.setEventNo("EX001");
        record.setApprovalStatus("PENDING");
        ExceptionEvent event = exceptionEvent("EX001", "MRB_PENDING");
        when(mrbApprovalTaskMapper.selectOne(any())).thenReturn(task);
        when(mrbRecordMapper.selectOne(any())).thenReturn(record);
        when(mrbApprovalTaskMapper.selectList(any())).thenReturn(List.of(task));
        when(exceptionEventMapper.selectOne(any())).thenReturn(event);

        Map<String, Object> row = qualityService.decideMrbApprovalTask("MRBT001", Map.of(
                "decision", "APPROVE",
                "approver", "pe1001",
                "opinion", "工艺确认通过"
        ));

        assertThat(row.get("approvalStatus")).isEqualTo("APPROVED");
        assertThat(task.getApprover()).isEqualTo("pe1001");
        assertThat(task.getDecision()).isEqualTo("APPROVE");
        assertThat(record.getApprovalStatus()).isEqualTo("APPROVED");
        assertThat(event.getStatus()).isEqualTo("MRB_REVIEWED");
        verify(mrbApprovalTaskMapper).updateById(task);
        verify(mrbRecordMapper).updateById(record);
        verify(exceptionEventMapper).updateById(event);
        verify(auditLogService).record(eq("MRB_APPROVAL_APPROVE"), eq("MRBT001"), eq("MRB_APPROVAL"), any(), eq("pe1001"), eq("quality-service"), any());
    }

    @Test
    void refreshMrbApprovalSlaShouldEscalateOverduePendingTasks() {
        QualityMrbApprovalTask task = approvalTask("MRBT001", "MRB001", "PENDING");
        task.setApprovalRole("PE");
        task.setDueTime(LocalDateTime.now().minusMinutes(10));
        QualityMrbRecord record = new QualityMrbRecord();
        record.setMrbNo("MRB001");
        record.setEventNo("EX001");
        record.setApprovalStatus("PENDING");
        when(mrbApprovalTaskMapper.selectList(any())).thenReturn(List.of(task));
        when(mrbRecordMapper.selectOne(any())).thenReturn(record);
        when(exceptionEventMapper.selectOne(any())).thenReturn(exceptionEvent("EX001", "MRB_PENDING"));

        Map<String, Object> row = qualityService.refreshMrbApprovalSla(Map.of(
                "mrbNo", "MRB001",
                "operator", "qe1001",
                "escalationReason", "超过SLA未会签"
        ));

        assertThat(row.get("escalatedCount")).isEqualTo(1);
        assertThat(task.getApprovalStatus()).isEqualTo("ESCALATED");
        assertThat(task.getEscalationRole()).isEqualTo("PROCESS_MANAGER");
        assertThat(task.getEscalatedTo()).isEqualTo("pm1001");
        assertThat(task.getEscalatedTime()).isNotNull();
        assertThat(task.getEscalationCount()).isEqualTo(1);
        assertThat(record.getApprovalStatus()).isEqualTo("PENDING");
        verify(mrbApprovalTaskMapper).updateById(task);
        verify(mrbRecordMapper).updateById(record);
        verify(auditLogService).record(eq("MRB_APPROVAL_ESCALATE"), eq("MRBT001"), eq("MRB_APPROVAL"),
                any(), eq("qe1001"), eq("quality-service"), any());
    }

    @Test
    void mrbRecordsShouldReturnAttachmentsAfterDataScopeCheck() {
        ExceptionEvent event = exceptionEvent("EX001", "MRB_REVIEWED");
        QualityMrbRecord record = new QualityMrbRecord();
        record.setMrbNo("MRB001");
        record.setEventNo("EX001");
        record.setLotNo("LOT001");
        record.setReviewType("REVIEW");
        record.setDispositionAction("RELEASE");
        record.setApprovalStatus("APPROVED");
        record.setAttachmentCount(1);
        QualityMrbAttachment attachment = new QualityMrbAttachment();
        attachment.setAttachmentNo("MRBA001");
        attachment.setMrbNo("MRB001");
        attachment.setEventNo("EX001");
        attachment.setFileName("mrb.pdf");
        when(exceptionEventMapper.selectOne(any())).thenReturn(event);
        when(mrbRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(mrbAttachmentMapper.selectList(any())).thenReturn(List.of(attachment));
        when(mrbApprovalTaskMapper.selectList(any())).thenReturn(List.of(approvalTask("MRBT001", "MRB001", "APPROVED")));

        List<Map<String, Object>> rows = qualityService.mrbRecords("EX001");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("mrbNo")).isEqualTo("MRB001");
        assertThat(rows.get(0).get("attachmentCount")).isEqualTo(1);
        assertThat((List<?>) rows.get(0).get("attachments")).hasSize(1);
        assertThat((List<?>) rows.get(0).get("approvalTasks")).hasSize(1);
    }

    private QualityMrbApprovalTask approvalTask(String taskNo, String mrbNo, String status) {
        QualityMrbApprovalTask task = new QualityMrbApprovalTask();
        task.setTaskNo(taskNo);
        task.setMrbNo(mrbNo);
        task.setEventNo("EX001");
        task.setLotNo("LOT001");
        task.setApprovalRole("PE");
        task.setApprovalStatus(status);
        return task;
    }

    private QualityMrbRecord mrbRecord(String mrbNo, String eventNo) {
        QualityMrbRecord record = new QualityMrbRecord();
        record.setMrbNo(mrbNo);
        record.setEventNo(eventNo);
        record.setLotNo("LOT001");
        record.setReviewType("REVIEW");
        record.setDispositionAction("RELEASE");
        record.setApprovalStatus("APPROVED");
        return record;
    }

    private QualityMrbMinutes mrbMinutes(String minutesNo, String mrbNo, int versionNo) {
        QualityMrbMinutes minutes = new QualityMrbMinutes();
        minutes.setMinutesNo(minutesNo);
        minutes.setMrbNo(mrbNo);
        minutes.setEventNo("EX001");
        minutes.setLotNo("LOT001");
        minutes.setVersionNo(versionNo);
        minutes.setMinutesContent("历史会议纪要");
        minutes.setEditor("qe1001");
        return minutes;
    }

    private TrackOutRequest ngRequest() {
        TrackOutRequest request = new TrackOutRequest();
        request.setLotNo("LOT001");
        request.setResult("NG");
        request.setRemark("涂布厚度不足");
        return request;
    }

    private Lot lot(String lotNo) {
        Lot lot = new Lot();
        lot.setLotNo(lotNo);
        lot.setOrderNo("MO001");
        lot.setProductCode("OLED_PANEL");
        lot.setStatus("PROCESSING");
        lot.setHoldFlag(0);
        return lot;
    }

    private LotStepRecord stepRecord() {
        LotStepRecord record = new LotStepRecord();
        record.setLotNo("LOT001");
        record.setStepCode("COATING");
        record.setEquipmentCode("COATER_01");
        record.setRecipeCode("RCP_COAT_01");
        return record;
    }

    private ExceptionEvent exceptionEvent(String eventNo, String status) {
        ExceptionEvent event = new ExceptionEvent();
        event.setEventNo(eventNo);
        event.setEventType("QUALITY");
        event.setEventLevel("P1");
        event.setLotNo("LOT001");
        event.setStepCode("COATING");
        event.setEquipmentCode("COATER_01");
        event.setTitle("涂胶厚度超限");
        event.setStatus(status);
        event.setOwnerRole("QE");
        return event;
    }
}
