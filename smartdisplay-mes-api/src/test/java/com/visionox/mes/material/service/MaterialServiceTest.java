package com.visionox.mes.material.service;

import com.visionox.mes.auth.security.RolePermissionService;
import com.visionox.mes.common.BusinessException;
import com.visionox.mes.lot.entity.Lot;
import com.visionox.mes.lot.entity.LotStepRecord;
import com.visionox.mes.material.entity.Bom;
import com.visionox.mes.material.entity.BomChangeAttachment;
import com.visionox.mes.material.entity.BomChangeRequest;
import com.visionox.mes.material.entity.BomEcoApprovalTask;
import com.visionox.mes.material.entity.BomItem;
import com.visionox.mes.material.entity.MaterialBatch;
import com.visionox.mes.material.entity.MaterialCoaAttachment;
import com.visionox.mes.material.entity.MaterialConsumption;
import com.visionox.mes.material.entity.MaterialIncomingInspection;
import com.visionox.mes.material.entity.MaterialInventoryTxn;
import com.visionox.mes.material.entity.MaterialLoading;
import com.visionox.mes.material.entity.MaterialLocation;
import com.visionox.mes.material.entity.MaterialLocationTask;
import com.visionox.mes.material.entity.Supplier;
import com.visionox.mes.material.entity.SupplierCorrectiveAction;
import com.visionox.mes.material.entity.SupplierQualificationReviewTask;
import com.visionox.mes.material.mapper.BomChangeAttachmentMapper;
import com.visionox.mes.material.mapper.BomChangeRequestMapper;
import com.visionox.mes.material.mapper.BomEcoApprovalTaskMapper;
import com.visionox.mes.material.mapper.BomItemMapper;
import com.visionox.mes.material.mapper.BomMapper;
import com.visionox.mes.material.mapper.CarrierMapper;
import com.visionox.mes.material.mapper.MaterialBatchMapper;
import com.visionox.mes.material.mapper.MaterialCoaAttachmentMapper;
import com.visionox.mes.material.mapper.MaterialConsumptionMapper;
import com.visionox.mes.material.mapper.MaterialIncomingInspectionMapper;
import com.visionox.mes.material.mapper.MaterialInventoryTxnMapper;
import com.visionox.mes.material.mapper.MaterialLoadingMapper;
import com.visionox.mes.material.mapper.MaterialLocationMapper;
import com.visionox.mes.material.mapper.MaterialLocationTaskMapper;
import com.visionox.mes.material.mapper.SupplierCorrectiveActionMapper;
import com.visionox.mes.material.mapper.SupplierMapper;
import com.visionox.mes.material.mapper.SupplierQualificationReviewTaskMapper;
import com.visionox.mes.system.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialServiceTest {

    @Mock
    private BomMapper bomMapper;

    @Mock
    private BomItemMapper bomItemMapper;

    @Mock
    private BomChangeRequestMapper bomChangeRequestMapper;

    @Mock
    private BomChangeAttachmentMapper bomChangeAttachmentMapper;

    @Mock
    private BomEcoApprovalTaskMapper bomEcoApprovalTaskMapper;

    @Mock
    private MaterialBatchMapper batchMapper;

    @Mock
    private MaterialLoadingMapper loadingMapper;

    @Mock
    private MaterialConsumptionMapper consumptionMapper;

    @Mock
    private MaterialInventoryTxnMapper inventoryTxnMapper;

    @Mock
    private MaterialIncomingInspectionMapper incomingInspectionMapper;

    @Mock
    private MaterialCoaAttachmentMapper coaAttachmentMapper;

    @Mock
    private MaterialLocationMapper materialLocationMapper;

    @Mock
    private MaterialLocationTaskMapper materialLocationTaskMapper;

    @Mock
    private CarrierMapper carrierMapper;

    @Mock
    private SupplierMapper supplierMapper;

    @Mock
    private SupplierCorrectiveActionMapper supplierCorrectiveActionMapper;

    @Mock
    private SupplierQualificationReviewTaskMapper supplierQualificationReviewTaskMapper;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private RolePermissionService rolePermissionService;

    @InjectMocks
    private MaterialService materialService;

    @Test
    void validateReadinessShouldRejectWhenActiveBomIsMissing() {
        Lot lot = lot();
        when(bomMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> materialService.validateReadiness(lot, "COATING"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OLED_PANEL");

        verify(bomItemMapper, never()).selectList(any());
        verify(batchMapper, never()).selectList(any());
    }

    @Test
    void validateReadinessShouldRejectWhenRequiredKeyMaterialHasNoAvailableBatch() {
        Lot lot = lot();
        when(bomMapper.selectOne(any())).thenReturn(activeBom());
        when(bomItemMapper.selectList(any())).thenReturn(List.of(bomItem("PI_INK", "PI液", "COATING", "0.5")));
        when(batchMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> materialService.validateReadiness(lot, "COATING"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("LOT001")
                .hasMessageContaining("PI_INK")
                .hasMessageContaining("50kg");
    }

    @Test
    void lockForTrackInShouldReserveAvailableBatchAndCreateLoadingRecord() {
        Lot lot = lot();
        MaterialBatch batch = batch("PI_INK_B001", "100", "0", "0", "AVAILABLE");
        when(loadingMapper.selectCount(any())).thenReturn(0L);
        when(bomMapper.selectOne(any())).thenReturn(activeBom());
        when(bomItemMapper.selectList(any())).thenReturn(List.of(bomItem("PI_INK", "PI液", "COATING", "0.5")));
        when(batchMapper.selectAvailableBatchForUpdate(eq("PI_INK"), any(), any())).thenReturn(batch);

        materialService.lockForTrackIn(lot, "COATING", "COATER_01", "op1001");

        assertThat(batch.getAvailableQty()).isEqualByComparingTo("50");
        assertThat(batch.getReservedQty()).isEqualByComparingTo("50");
        assertThat(batch.getStatus()).isEqualTo("AVAILABLE");
        assertThat(batch.getStockVersion()).isEqualTo(1L);
        assertThat(batch.getUpdatedTime()).isNotNull();
        verify(batchMapper).updateById(batch);

        ArgumentCaptor<MaterialLoading> loadingCaptor = ArgumentCaptor.forClass(MaterialLoading.class);
        verify(loadingMapper).insert(loadingCaptor.capture());
        MaterialLoading loading = loadingCaptor.getValue();
        assertThat(loading.getLotNo()).isEqualTo("LOT001");
        assertThat(loading.getOrderNo()).isEqualTo("MO001");
        assertThat(loading.getProductCode()).isEqualTo("OLED_PANEL");
        assertThat(loading.getStepCode()).isEqualTo("COATING");
        assertThat(loading.getEquipmentCode()).isEqualTo("COATER_01");
        assertThat(loading.getMaterialCode()).isEqualTo("PI_INK");
        assertThat(loading.getBatchNo()).isEqualTo("PI_INK_B001");
        assertThat(loading.getRequiredQty()).isEqualByComparingTo("50");
        assertThat(loading.getLoadedQty()).isEqualByComparingTo("50");
        assertThat(loading.getStatus()).isEqualTo("LOADED");
        assertThat(loading.getOperator()).isEqualTo("op1001");
        assertThat(loading.getLoadedTime()).isNotNull();

        verify(auditLogService).record(eq("MATERIAL_LOAD"), eq("LOT001"), eq("LOT"), any(), eq("op1001"), eq("material-service"), any());
    }

    @Test
    void lockForTrackInShouldUseSubstituteMaterialWhenPrimaryIsUnavailable() {
        Lot lot = lot();
        BomItem primary = bomItem("PI_INK", "PI液", "COATING", "0.5");
        primary.setSubstituteGroup("PI");
        primary.setSubstitutePriority(1);
        BomItem substitute = bomItem("PI_INK_ALT", "PI替代液", "COATING", "0.5");
        substitute.setSubstituteGroup("PI");
        substitute.setSubstitutePriority(2);
        MaterialBatch substituteBatch = batch("PI_ALT_B001", "100", "0", "0", "AVAILABLE");
        substituteBatch.setMaterialCode("PI_INK_ALT");
        substituteBatch.setMaterialName("PI替代液");

        when(loadingMapper.selectCount(any())).thenReturn(0L);
        when(bomMapper.selectOne(any())).thenReturn(activeBom());
        when(bomItemMapper.selectList(any())).thenReturn(List.of(primary, substitute));
        when(batchMapper.selectAvailableBatchForUpdate(eq("PI_INK"), any(), any())).thenReturn(null);
        when(batchMapper.selectAvailableBatchForUpdate(eq("PI_INK_ALT"), any(), any())).thenReturn(substituteBatch);

        materialService.lockForTrackIn(lot, "COATING", "COATER_01", "op1001");

        ArgumentCaptor<MaterialLoading> loadingCaptor = ArgumentCaptor.forClass(MaterialLoading.class);
        verify(loadingMapper).insert(loadingCaptor.capture());
        MaterialLoading loading = loadingCaptor.getValue();
        assertThat(loading.getMaterialCode()).isEqualTo("PI_INK_ALT");
        assertThat(loading.getBatchNo()).isEqualTo("PI_ALT_B001");
        assertThat(loading.getRemark()).contains("替代料组=PI");
        assertThat(substituteBatch.getAvailableQty()).isEqualByComparingTo("50");
        assertThat(substituteBatch.getReservedQty()).isEqualByComparingTo("50");
    }

    @Test
    void submitBomChangeShouldCreateDraftBomItemsChangeRequestAndAudit() {
        Bom source = activeBom();
        source.setBomVersion("V01");
        when(bomMapper.selectOne(any())).thenReturn(source);
        when(bomMapper.selectCount(any())).thenReturn(0L);
        when(bomItemMapper.selectList(any())).thenReturn(List.of(bomItem("PI_INK", "PI液", "COATING", "0.5")));

        BomChangeRequest change = materialService.submitBomChange(Map.of(
                "sourceBomCode", "BOM-OLED-01",
                "targetBomCode", "BOM-OLED-02",
                "targetVersion", "V02",
                "reason", "新增替代料策略",
                "operator", "pe1001",
                "attachments", List.of(Map.of(
                        "fileName", "substitute-validation.pdf",
                        "fileUrl", "qms://eco/substitute-validation.pdf",
                        "fileHash", "sha256:substitute",
                        "attachmentRole", "SUBSTITUTE_VALIDATION"
                ))
        ));

        ArgumentCaptor<Bom> bomCaptor = ArgumentCaptor.forClass(Bom.class);
        verify(bomMapper).insert(bomCaptor.capture());
        Bom target = bomCaptor.getValue();
        assertThat(target.getBomCode()).isEqualTo("BOM-OLED-02");
        assertThat(target.getBomVersion()).isEqualTo("V02");
        assertThat(target.getStatus()).isEqualTo("DRAFT");

        verify(bomItemMapper).insert(any(BomItem.class));
        verify(bomChangeRequestMapper).insert(change);
        assertThat(change.getStatus()).isEqualTo("SUBMITTED");
        assertThat(change.getSourceBomCode()).isEqualTo("BOM-OLED-01");
        assertThat(change.getTargetBomCode()).isEqualTo("BOM-OLED-02");
        assertThat(change.getSubstitutePolicySnapshot()).contains("PI_INK");
        assertThat(change.getEcoNo()).isEqualTo(change.getChangeNo());
        assertThat(change.getEcoApprovalStatus()).isEqualTo("PENDING");
        assertThat(change.getEcoRequiredRoles()).contains("PE", "QE", "PLANNER");
        ArgumentCaptor<BomEcoApprovalTask> taskCaptor = ArgumentCaptor.forClass(BomEcoApprovalTask.class);
        verify(bomEcoApprovalTaskMapper, times(3)).insert(taskCaptor.capture());
        assertThat(taskCaptor.getAllValues())
                .extracting(BomEcoApprovalTask::getApprovalRole)
                .containsExactly("PE", "QE", "PLANNER");
        assertThat(taskCaptor.getAllValues())
                .allSatisfy(task -> {
                    assertThat(task.getChangeNo()).isEqualTo(change.getChangeNo());
                    assertThat(task.getApprovalStatus()).isEqualTo("PENDING");
                    assertThat(task.getDueTime()).isNotNull();
                });
        ArgumentCaptor<BomChangeAttachment> attachmentCaptor = ArgumentCaptor.forClass(BomChangeAttachment.class);
        verify(bomChangeAttachmentMapper).insert(attachmentCaptor.capture());
        BomChangeAttachment attachment = attachmentCaptor.getValue();
        assertThat(attachment.getChangeNo()).isEqualTo(change.getChangeNo());
        assertThat(attachment.getTargetBomCode()).isEqualTo("BOM-OLED-02");
        assertThat(attachment.getFileName()).isEqualTo("substitute-validation.pdf");
        assertThat(attachment.getFileHash()).isEqualTo("sha256:substitute");
        assertThat(attachment.getAttachmentRole()).isEqualTo("SUBSTITUTE_VALIDATION");
        verify(auditLogService).record(eq("BOM_CHANGE_SUBMIT"), eq(change.getChangeNo()), eq("BOM_CHANGE"), any(), eq("pe1001"), eq("material-service"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void bomChangeRequestsShouldReturnValidationAttachments() {
        BomChangeRequest change = bomChange("BCR003", "SUBMITTED");
        BomChangeAttachment attachment = new BomChangeAttachment();
        attachment.setAttachmentNo("BCA001");
        attachment.setChangeNo("BCR003");
        attachment.setProductCode("OLED_PANEL");
        attachment.setTargetBomCode("BOM-OLED-03");
        attachment.setFileName("validation.xlsx");
        attachment.setFileUrl("qms://eco/validation.xlsx");
        attachment.setFileHash("sha256:validation");
        attachment.setAttachmentRole("SUBSTITUTE_VALIDATION");
        attachment.setUploadedBy("pe1001");
        attachment.setUploadedTime(LocalDateTime.now());
        when(bomChangeRequestMapper.selectList(any())).thenReturn(List.of(change));
        when(bomChangeAttachmentMapper.selectList(any())).thenReturn(List.of(attachment));

        List<Map<String, Object>> rows = materialService.bomChangeRequests(null);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("attachmentCount")).isEqualTo(1);
        List<Map<String, Object>> attachments = (List<Map<String, Object>>) rows.get(0).get("attachments");
        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).get("fileName")).isEqualTo("validation.xlsx");
        assertThat(attachments.get(0).get("attachmentRole")).isEqualTo("SUBSTITUTE_VALIDATION");
    }

    @Test
    void reviewAndPublishBomChangeShouldActivateTargetAndDisableOldActiveBom() {
        BomChangeRequest change = bomChange("BCR001", "APPROVED");
        Bom target = activeBom();
        target.setId(20L);
        target.setBomCode("BOM-OLED-02");
        target.setBomVersion("V02");
        target.setStatus("DRAFT");
        Bom oldActive = activeBom();
        oldActive.setId(10L);
        oldActive.setStatus("ACTIVE");
        when(bomChangeRequestMapper.selectOne(any())).thenReturn(change);
        when(bomMapper.selectOne(any())).thenReturn(target);
        when(bomMapper.selectList(any())).thenReturn(List.of(oldActive));

        BomChangeRequest published = materialService.publishBomChange("BCR001", Map.of("publisher", "pe1001"));

        assertThat(target.getStatus()).isEqualTo("ACTIVE");
        assertThat(target.getEffectiveTime()).isNotNull();
        assertThat(oldActive.getStatus()).isEqualTo("INACTIVE");
        assertThat(published.getStatus()).isEqualTo("PUBLISHED");
        verify(bomMapper).updateById(oldActive);
        verify(bomMapper).updateById(target);
        verify(bomChangeRequestMapper).updateById(change);
        verify(auditLogService).record(eq("BOM_PUBLISH"), eq("BOM-OLED-02"), eq("BOM"), any(), eq("pe1001"), eq("material-service"), any());
    }

    @Test
    void reviewBomChangeShouldRejectSubmittedChange() {
        BomChangeRequest change = bomChange("BCR002", "SUBMITTED");
        when(bomChangeRequestMapper.selectOne(any())).thenReturn(change);

        BomChangeRequest rejected = materialService.reviewBomChange("BCR002", Map.of(
                "decision", "REJECTED",
                "reviewer", "pe-lead",
                "comment", "缺少替代料验证报告"
        ));

        assertThat(rejected.getStatus()).isEqualTo("REJECTED");
        assertThat(rejected.getReviewComment()).isEqualTo("缺少替代料验证报告");
        verify(bomChangeRequestMapper).updateById(change);
        verify(auditLogService).record(eq("BOM_CHANGE_REJECT"), eq("BCR002"), eq("BOM_CHANGE"), any(), eq("pe-lead"), eq("material-service"), any());
    }

    @Test
    void approveBomEcoTaskShouldApproveChangeWhenAllRolesPassed() {
        BomChangeRequest change = bomChange("BCR004", "SUBMITTED");
        BomEcoApprovalTask peTask = bomEcoTask("BEA001", "BCR004", "PE", "PENDING");
        BomEcoApprovalTask qeTask = bomEcoTask("BEA002", "BCR004", "QE", "APPROVED");
        when(bomEcoApprovalTaskMapper.selectOne(any())).thenReturn(peTask);
        when(bomChangeRequestMapper.selectOne(any())).thenReturn(change);
        when(bomEcoApprovalTaskMapper.selectList(any())).thenReturn(List.of(peTask, qeTask));

        BomEcoApprovalTask approved = materialService.decideBomEcoApprovalTask("BEA001", Map.of(
                "decision", "APPROVE",
                "approver", "pe-lead",
                "opinion", "工艺验证通过"
        ));

        assertThat(approved.getApprovalStatus()).isEqualTo("APPROVED");
        assertThat(change.getStatus()).isEqualTo("APPROVED");
        assertThat(change.getEcoApprovalStatus()).isEqualTo("APPROVED");
        assertThat(change.getReviewComment()).isEqualTo("工艺验证通过");
        verify(bomEcoApprovalTaskMapper).updateById(peTask);
        verify(bomChangeRequestMapper).updateById(change);
        verify(auditLogService).record(eq("BOM_ECO_APPROVAL_APPROVE"), eq("BEA001"), eq("BOM_ECO_APPROVAL"), any(), eq("pe-lead"), eq("material-service"), any());
    }

    @Test
    void rejectBomEcoTaskShouldRejectChange() {
        BomChangeRequest change = bomChange("BCR005", "SUBMITTED");
        BomEcoApprovalTask task = bomEcoTask("BEA003", "BCR005", "QE", "PENDING");
        when(bomEcoApprovalTaskMapper.selectOne(any())).thenReturn(task);
        when(bomChangeRequestMapper.selectOne(any())).thenReturn(change);
        when(bomEcoApprovalTaskMapper.selectList(any())).thenReturn(List.of(task));

        BomEcoApprovalTask rejected = materialService.decideBomEcoApprovalTask("BEA003", Map.of(
                "decision", "REJECT",
                "approver", "qe-lead",
                "opinion", "缺少试产良率证据"
        ));

        assertThat(rejected.getApprovalStatus()).isEqualTo("REJECTED");
        assertThat(change.getStatus()).isEqualTo("REJECTED");
        assertThat(change.getEcoApprovalStatus()).isEqualTo("REJECTED");
        assertThat(change.getReviewComment()).isEqualTo("缺少试产良率证据");
        verify(auditLogService).record(eq("BOM_ECO_APPROVAL_REJECT"), eq("BEA003"), eq("BOM_ECO_APPROVAL"), any(), eq("qe-lead"), eq("material-service"), any());
    }

    @Test
    void publishBomChangeShouldRejectWhenEcoApprovalsArePending() {
        BomChangeRequest change = bomChange("BCR006", "APPROVED");
        BomEcoApprovalTask task = bomEcoTask("BEA004", "BCR006", "PLANNER", "PENDING");
        when(bomChangeRequestMapper.selectOne(any())).thenReturn(change);
        when(bomEcoApprovalTaskMapper.selectList(any())).thenReturn(List.of(task));

        assertThatThrownBy(() -> materialService.publishBomChange("BCR006", Map.of("publisher", "pe1001")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("BOM/ECO会签未全部通过");

        verify(bomMapper, never()).selectOne(any());
        verify(auditLogService, never()).record(eq("BOM_PUBLISH"), any(), any(), any(), any(), any(), any());
    }

    @Test
    void receiveMaterialShouldCreateBatchInventoryTxnAndAudit() {
        when(batchMapper.selectCount(any())).thenReturn(0L);

        MaterialBatch batch = materialService.receiveMaterial(Map.of(
                "materialCode", "PI_INK",
                "materialName", "PI Ink",
                "batchNo", "PI_INK_B002",
                "qty", "120.5",
                "unit", "kg",
                "operator", "wms1001"
        ));

        assertThat(batch.getBatchNo()).isEqualTo("PI_INK_B002");
        assertThat(batch.getTotalQty()).isEqualByComparingTo("120.5");
        assertThat(batch.getAvailableQty()).isEqualByComparingTo("120.5");
        assertThat(batch.getFrozenQty()).isEqualByComparingTo("0");
        assertThat(batch.getStockVersion()).isEqualTo(1L);
        verify(batchMapper).insert(batch);

        ArgumentCaptor<MaterialInventoryTxn> txnCaptor = ArgumentCaptor.forClass(MaterialInventoryTxn.class);
        verify(inventoryTxnMapper).insert(txnCaptor.capture());
        MaterialInventoryTxn txn = txnCaptor.getValue();
        assertThat(txn.getTxnType()).isEqualTo("RECEIVE");
        assertThat(txn.getBatchNo()).isEqualTo("PI_INK_B002");
        assertThat(txn.getQtyDelta()).isEqualByComparingTo("120.5");
        assertThat(txn.getAvailableBefore()).isEqualByComparingTo("0");
        assertThat(txn.getAvailableAfter()).isEqualByComparingTo("120.5");
        verify(auditLogService).record(eq("MATERIAL_RECEIVE"), eq("PI_INK_B002"), eq("MATERIAL_BATCH"), any(), eq("wms1001"), eq("material-service"), any());
    }

    @Test
    void receiveMaterialShouldValidateLocationAndUpdateUsage() {
        MaterialLocation location = materialLocation("WMS-A01", "CHEMICAL", "ACTIVE", "100", "40", "kg");
        when(batchMapper.selectCount(any())).thenReturn(0L);
        when(materialLocationMapper.selectByLocationCodeForUpdate("WMS-A01")).thenReturn(location);

        MaterialBatch batch = materialService.receiveMaterial(Map.of(
                "materialCode", "PI_INK",
                "materialName", "PI Ink",
                "batchNo", "PI_INK_B005",
                "qty", "50",
                "unit", "kg",
                "location", "WMS-A01",
                "operator", "wms1001"
        ));

        assertThat(batch.getLocation()).isEqualTo("WMS-A01");
        assertThat(location.getUsedQty()).isEqualByComparingTo("90");
        verify(materialLocationMapper).updateById(location);
        verify(batchMapper).insert(batch);
    }

    @Test
    void receiveMaterialShouldRejectWhenLocationCapacityIsInsufficient() {
        MaterialLocation location = materialLocation("WMS-A01", "CHEMICAL", "ACTIVE", "60", "20", "kg");
        when(materialLocationMapper.selectByLocationCodeForUpdate("WMS-A01")).thenReturn(location);

        assertThatThrownBy(() -> materialService.receiveMaterial(Map.of(
                "materialCode", "PI_INK",
                "batchNo", "PI_INK_B006",
                "qty", "50",
                "unit", "kg",
                "location", "WMS-A01",
                "operator", "wms1001"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("库位容量不足");

        verify(batchMapper, never()).insert(any());
        verify(materialLocationMapper, never()).updateById(any());
    }

    @Test
    void materialLocationsShouldExposeCapacityAndEnvironmentWindow() {
        MaterialLocation location = materialLocation("COLD-02", "ORGANIC", "ACTIVE", "200", "80", "g");
        location.setStorageType("COLD");
        location.setTemperatureMin(new BigDecimal("2"));
        location.setTemperatureMax(new BigDecimal("8"));
        location.setHumidityMin(new BigDecimal("20"));
        location.setHumidityMax(new BigDecimal("45"));
        when(materialLocationMapper.selectList(any())).thenReturn(List.of(location));

        List<Map<String, Object>> rows = materialService.materialLocations();

        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("locationCode")).isEqualTo("COLD-02");
        assertThat(row.get("availableQty")).isEqualTo(new BigDecimal("120"));
        assertThat(row.get("utilizationText")).isEqualTo("40.0%");
        assertThat(row.get("temperatureWindow")).isEqualTo("2 ~ 8℃");
        assertThat(row.get("humidityWindow")).isEqualTo("20 ~ 45%RH");
    }

    @Test
    void createMoveLocationTaskShouldCreatePendingTaskAndWriteAudit() {
        MaterialBatch batch = batch("PI_INK_B007", "70", "20", "0", "AVAILABLE");
        batch.setFrozenQty(new BigDecimal("10"));
        batch.setLocation("WMS-IN");
        MaterialLocation target = materialLocation("WMS-A01", "CHEMICAL", "ACTIVE", "300", "20", "kg");
        when(batchMapper.selectByBatchNoForUpdate("PI_INK_B007")).thenReturn(batch);
        when(materialLocationMapper.selectByLocationCodeForUpdate("WMS-A01")).thenReturn(target);

        Map<String, Object> result = materialService.createLocationTask(Map.of(
                "taskType", "MOVE",
                "batchNo", "PI_INK_B007",
                "targetLocation", "WMS-A01",
                "operator", "wms1001",
                "reason", "产线补料前移库"
        ));

        assertThat(batch.getLocation()).isEqualTo("WMS-IN");
        assertThat(target.getUsedQty()).isEqualByComparingTo("20");
        assertThat(result.get("task")).isInstanceOf(Map.class);
        verify(batchMapper, never()).updateById(batch);
        verify(materialLocationMapper, never()).updateById(target);
        verify(inventoryTxnMapper, never()).insert(any(MaterialInventoryTxn.class));

        ArgumentCaptor<MaterialLocationTask> taskCaptor = ArgumentCaptor.forClass(MaterialLocationTask.class);
        verify(materialLocationTaskMapper).insert(taskCaptor.capture());
        MaterialLocationTask task = taskCaptor.getValue();
        assertThat(task.getTaskType()).isEqualTo("MOVE");
        assertThat(task.getBatchNo()).isEqualTo("PI_INK_B007");
        assertThat(task.getSourceLocation()).isEqualTo("WMS-IN");
        assertThat(task.getTargetLocation()).isEqualTo("WMS-A01");
        assertThat(task.getPlannedQty()).isEqualByComparingTo("100");
        assertThat(task.getActualQty()).isEqualByComparingTo("0");
        assertThat(task.getStatus()).isEqualTo("CREATED");
        verify(auditLogService).record(eq("MATERIAL_LOCATION_TASK_CREATE"), any(), eq("MATERIAL_LOCATION_TASK"), any(), eq("wms1001"), eq("material-service"), any());
    }

    @Test
    void completeMoveLocationTaskShouldMoveWholeBatchAndWriteTaskAudit() {
        MaterialLocationTask task = locationTask("MLT-001", "MOVE", "PI_INK_B007");
        task.setStatus("ASSIGNED");
        task.setActualQty(BigDecimal.ZERO);
        MaterialBatch batch = batch("PI_INK_B007", "70", "20", "0", "AVAILABLE");
        batch.setFrozenQty(new BigDecimal("10"));
        batch.setLocation("WMS-IN");
        MaterialLocation source = materialLocation("WMS-IN", "ANY", "ACTIVE", "500", "100", "kg");
        MaterialLocation target = materialLocation("WMS-A01", "CHEMICAL", "ACTIVE", "300", "20", "kg");
        when(materialLocationTaskMapper.selectByTaskNoForUpdate("MLT-001")).thenReturn(task);
        when(batchMapper.selectByBatchNoForUpdate("PI_INK_B007")).thenReturn(batch);
        when(materialLocationMapper.selectByLocationCodeForUpdate("WMS-A01")).thenReturn(target);
        when(materialLocationMapper.selectByLocationCodeForUpdate("WMS-IN")).thenReturn(source);

        Map<String, Object> result = materialService.completeLocationTask("MLT-001", Map.of("operator", "wms1001"));

        assertThat(batch.getLocation()).isEqualTo("WMS-A01");
        assertThat(source.getUsedQty()).isEqualByComparingTo("0");
        assertThat(target.getUsedQty()).isEqualByComparingTo("120");
        assertThat(task.getActualQty()).isEqualByComparingTo("100");
        assertThat(task.getStatus()).isEqualTo("DONE");
        assertThat(result.get("batch")).isInstanceOf(Map.class);
        verify(batchMapper).updateById(batch);
        verify(materialLocationMapper).updateById(source);
        verify(materialLocationMapper).updateById(target);
        verify(inventoryTxnMapper).insert(any(MaterialInventoryTxn.class));
        verify(materialLocationTaskMapper, times(2)).updateById(task);
        verify(auditLogService).record(eq("MATERIAL_LOCATION_TASK_COMPLETE"), eq("MLT-001"), eq("MATERIAL_LOCATION_TASK"), any(), eq("wms1001"), eq("material-service"), any());
    }

    @Test
    void createMoveLocationTaskShouldRejectLockedTargetLocation() {
        MaterialBatch batch = batch("PI_INK_B008", "50", "0", "0", "AVAILABLE");
        batch.setLocation("WMS-IN");
        MaterialLocation target = materialLocation("WMS-HOLD-01", "ANY", "LOCKED", "300", "20", "kg");
        when(batchMapper.selectByBatchNoForUpdate("PI_INK_B008")).thenReturn(batch);
        when(materialLocationMapper.selectByLocationCodeForUpdate("WMS-HOLD-01")).thenReturn(target);

        assertThatThrownBy(() -> materialService.createLocationTask(Map.of(
                "taskType", "MOVE",
                "batchNo", "PI_INK_B008",
                "targetLocation", "WMS-HOLD-01",
                "operator", "wms1001"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("库位不可入库");

        verify(batchMapper, never()).updateById(any());
        verify(materialLocationTaskMapper, never()).insert(any());
    }

    @Test
    void createCountLocationTaskShouldCreatePendingTask() {
        MaterialBatch batch = batch("PI_INK_B009", "70", "20", "10", "AVAILABLE");
        batch.setFrozenQty(new BigDecimal("5"));
        batch.setLocation("WMS-A01");
        when(batchMapper.selectByBatchNoForUpdate("PI_INK_B009")).thenReturn(batch);

        Map<String, Object> result = materialService.createLocationTask(Map.of(
                "taskType", "COUNT",
                "batchNo", "PI_INK_B009",
                "actualQty", "68",
                "operator", "wms1001"
        ));

        assertThat(batch.getAvailableQty()).isEqualByComparingTo("70");
        assertThat(batch.getTotalQty()).isEqualByComparingTo("100");
        assertThat(result.get("task")).isInstanceOf(Map.class);
        verify(inventoryTxnMapper, never()).insert(any(MaterialInventoryTxn.class));

        ArgumentCaptor<MaterialLocationTask> taskCaptor = ArgumentCaptor.forClass(MaterialLocationTask.class);
        verify(materialLocationTaskMapper).insert(taskCaptor.capture());
        MaterialLocationTask task = taskCaptor.getValue();
        assertThat(task.getTaskType()).isEqualTo("COUNT");
        assertThat(task.getPlannedQty()).isEqualByComparingTo("70");
        assertThat(task.getActualQty()).isEqualByComparingTo("68");
        assertThat(task.getStatus()).isEqualTo("CREATED");
        verify(auditLogService).record(eq("MATERIAL_LOCATION_TASK_CREATE"), any(), eq("MATERIAL_LOCATION_TASK"), any(), eq("wms1001"), eq("material-service"), any());
    }

    @Test
    void completeCountLocationTaskShouldReuseInventoryCountAndWriteTask() {
        MaterialLocationTask task = locationTask("MLT-COUNT-001", "COUNT", "PI_INK_B009");
        task.setStatus("CREATED");
        task.setSourceLocation("WMS-A01");
        task.setTargetLocation("WMS-A01");
        task.setPlannedQty(new BigDecimal("70"));
        task.setActualQty(new BigDecimal("68"));
        MaterialBatch batch = batch("PI_INK_B009", "70", "20", "10", "AVAILABLE");
        batch.setFrozenQty(new BigDecimal("5"));
        batch.setLocation("WMS-A01");
        MaterialLocation location = materialLocation("WMS-A01", "CHEMICAL", "ACTIVE", "300", "95", "kg");
        when(materialLocationTaskMapper.selectByTaskNoForUpdate("MLT-COUNT-001")).thenReturn(task);
        when(batchMapper.selectByBatchNoForUpdate("PI_INK_B009")).thenReturn(batch);
        when(materialLocationMapper.selectByLocationCodeForUpdate("WMS-A01")).thenReturn(location);

        Map<String, Object> result = materialService.completeLocationTask("MLT-COUNT-001", Map.of("operator", "wms1001"));

        assertThat(batch.getAvailableQty()).isEqualByComparingTo("68");
        assertThat(batch.getTotalQty()).isEqualByComparingTo("103");
        assertThat(location.getUsedQty()).isEqualByComparingTo("93");
        assertThat(task.getStatus()).isEqualTo("DONE");
        assertThat(result.get("batch")).isInstanceOf(Map.class);
        verify(inventoryTxnMapper).insert(any(MaterialInventoryTxn.class));
        verify(materialLocationTaskMapper, times(2)).updateById(task);
        verify(auditLogService).record(eq("MATERIAL_COUNT"), eq("PI_INK_B009"), eq("MATERIAL_BATCH"), any(), eq("wms1001"), eq("material-service"), any());
        verify(auditLogService).record(eq("MATERIAL_LOCATION_TASK_COMPLETE"), eq("MLT-COUNT-001"), eq("MATERIAL_LOCATION_TASK"), any(), eq("wms1001"), eq("material-service"), any());
    }

    @Test
    void assignAndCancelLocationTaskShouldUpdateWorkflowFields() {
        MaterialLocationTask task = locationTask("MLT-CANCEL-001", "MOVE", "PI_INK_B010");
        task.setStatus("CREATED");
        when(materialLocationTaskMapper.selectByTaskNoForUpdate("MLT-CANCEL-001")).thenReturn(task);

        Map<String, Object> assigned = materialService.assignLocationTask("MLT-CANCEL-001", Map.of("assignedTo", "wms1002"));

        assertThat(task.getStatus()).isEqualTo("ASSIGNED");
        assertThat(task.getAssignedTo()).isEqualTo("wms1002");
        assertThat(assigned.get("task")).isInstanceOf(Map.class);

        Map<String, Object> cancelled = materialService.cancelLocationTask("MLT-CANCEL-001", Map.of(
                "operator", "wms1001",
                "cancelReason", "目标库位临时锁定"
        ));

        assertThat(task.getStatus()).isEqualTo("CANCELLED");
        assertThat(task.getCancelledBy()).isEqualTo("wms1001");
        assertThat(task.getCancelReason()).isEqualTo("目标库位临时锁定");
        assertThat(cancelled.get("task")).isInstanceOf(Map.class);
        verify(materialLocationTaskMapper, times(2)).updateById(task);
        verify(auditLogService).record(eq("MATERIAL_LOCATION_TASK_ASSIGN"), eq("MLT-CANCEL-001"), eq("MATERIAL_LOCATION_TASK"), any(), eq("wms1002"), eq("material-service"), any());
        verify(auditLogService).record(eq("MATERIAL_LOCATION_TASK_CANCEL"), eq("MLT-CANCEL-001"), eq("MATERIAL_LOCATION_TASK"), any(), eq("wms1001"), eq("material-service"), any());
    }

    @Test
    void materialLocationTasksShouldExposeTaskRows() {
        MaterialLocationTask task = locationTask("MLT-001", "MOVE", "PI_INK_B007");
        when(materialLocationTaskMapper.selectList(any())).thenReturn(List.of(task));

        List<Map<String, Object>> rows = materialService.materialLocationTasks("DONE", "PI_INK_B007");

        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("taskNo")).isEqualTo("MLT-001");
        assertThat(row.get("taskType")).isEqualTo("MOVE");
        assertThat(row.get("batchNo")).isEqualTo("PI_INK_B007");
        assertThat(row.get("actualQty")).isEqualTo(new BigDecimal("100"));
        assertThat(row.get("status")).isEqualTo("DONE");
    }

    @Test
    void freezeMaterialShouldLockBatchForUpdateAndMoveAvailableToFrozen() {
        MaterialBatch batch = batch("PI_INK_B001", "100", "0", "0", "AVAILABLE");
        when(batchMapper.selectByBatchNoForUpdate("PI_INK_B001")).thenReturn(batch);

        MaterialBatch updated = materialService.freezeMaterial("PI_INK_B001", Map.of("qty", "30", "operator", "wms1001"));

        assertThat(updated.getAvailableQty()).isEqualByComparingTo("70");
        assertThat(updated.getFrozenQty()).isEqualByComparingTo("30");
        assertThat(updated.getStatus()).isEqualTo("AVAILABLE");
        assertThat(updated.getStockVersion()).isEqualTo(1L);
        verify(batchMapper).updateById(batch);
        verify(inventoryTxnMapper).insert(any(MaterialInventoryTxn.class));
        verify(auditLogService).record(eq("MATERIAL_FREEZE"), eq("PI_INK_B001"), eq("MATERIAL_BATCH"), any(), eq("wms1001"), eq("material-service"), any());
    }

    @Test
    void returnMaterialShouldIncreaseAvailableTotalAndReturnedQty() {
        MaterialBatch batch = batch("PI_INK_B001", "70", "0", "0", "AVAILABLE");
        when(batchMapper.selectByBatchNoForUpdate("PI_INK_B001")).thenReturn(batch);

        MaterialBatch updated = materialService.returnMaterial("PI_INK_B001", Map.of("qty", "5", "operator", "op1001"));

        assertThat(updated.getAvailableQty()).isEqualByComparingTo("75");
        assertThat(updated.getTotalQty()).isEqualByComparingTo("105");
        assertThat(updated.getReturnedQty()).isEqualByComparingTo("5");
        assertThat(updated.getStockVersion()).isEqualTo(1L);
        verify(inventoryTxnMapper).insert(any(MaterialInventoryTxn.class));
        verify(auditLogService).record(eq("MATERIAL_RETURN"), eq("PI_INK_B001"), eq("MATERIAL_BATCH"), any(), eq("op1001"), eq("material-service"), any());
    }

    @Test
    void inventoryCountShouldAdjustAvailableAndTotalByCountedQty() {
        MaterialBatch batch = batch("PI_INK_B001", "70", "20", "10", "AVAILABLE");
        batch.setFrozenQty(new BigDecimal("5"));
        when(batchMapper.selectByBatchNoForUpdate("PI_INK_B001")).thenReturn(batch);

        MaterialBatch updated = materialService.inventoryCount("PI_INK_B001", Map.of("countedAvailableQty", "68", "operator", "wms1001"));

        assertThat(updated.getAvailableQty()).isEqualByComparingTo("68");
        assertThat(updated.getTotalQty()).isEqualByComparingTo("103");
        assertThat(updated.getLastCountTime()).isNotNull();
        assertThat(updated.getStockVersion()).isEqualTo(1L);
        verify(inventoryTxnMapper).insert(any(MaterialInventoryTxn.class));
        verify(auditLogService).record(eq("MATERIAL_COUNT"), eq("PI_INK_B001"), eq("MATERIAL_BATCH"), any(), eq("wms1001"), eq("material-service"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void createIncomingInspectionShouldPassBatchStoreCoaAttachmentAndAudit() {
        MaterialBatch batch = batch("PI_INK_B003", "100", "0", "0", "HOLD");
        batch.setQualityStatus("HOLD");
        when(batchMapper.selectByBatchNoForUpdate("PI_INK_B003")).thenReturn(batch);

        Map<String, Object> result = materialService.createIncomingInspection("PI_INK_B003", Map.of(
                "result", "PASS",
                "inspectedQty", "12.5",
                "sampleQty", "3",
                "coaNo", "COA-PI-003",
                "inspector", "qe1001",
                "attachments", List.of(Map.of(
                        "fileName", "COA-PI-003.pdf",
                        "fileUrl", "qms://coa/COA-PI-003.pdf",
                        "fileHash", "sha256:abc"
                ))
        ));

        assertThat(batch.getQualityStatus()).isEqualTo("PASS");
        assertThat(batch.getStatus()).isEqualTo("AVAILABLE");
        assertThat(batch.getStockVersion()).isEqualTo(1L);
        assertThat(batch.getUpdatedTime()).isNotNull();
        verify(batchMapper).updateById(batch);

        ArgumentCaptor<MaterialIncomingInspection> inspectionCaptor = ArgumentCaptor.forClass(MaterialIncomingInspection.class);
        verify(incomingInspectionMapper).insert(inspectionCaptor.capture());
        MaterialIncomingInspection inspection = inspectionCaptor.getValue();
        assertThat(inspection.getBatchNo()).isEqualTo("PI_INK_B003");
        assertThat(inspection.getResult()).isEqualTo("PASS");
        assertThat(inspection.getInspectedQty()).isEqualByComparingTo("12.5");
        assertThat(inspection.getSampleQty()).isEqualByComparingTo("3");
        assertThat(inspection.getCoaNo()).isEqualTo("COA-PI-003");
        assertThat(inspection.getInspector()).isEqualTo("qe1001");

        ArgumentCaptor<MaterialCoaAttachment> attachmentCaptor = ArgumentCaptor.forClass(MaterialCoaAttachment.class);
        verify(coaAttachmentMapper).insert(attachmentCaptor.capture());
        MaterialCoaAttachment attachment = attachmentCaptor.getValue();
        assertThat(attachment.getInspectionNo()).isEqualTo(inspection.getInspectionNo());
        assertThat(attachment.getBatchNo()).isEqualTo("PI_INK_B003");
        assertThat(attachment.getFileName()).isEqualTo("COA-PI-003.pdf");
        assertThat(attachment.getFileHash()).isEqualTo("sha256:abc");

        Map<String, Object> inspectionRow = (Map<String, Object>) result.get("inspection");
        assertThat(inspectionRow.get("attachmentCount")).isEqualTo(1);
        verify(auditLogService).record(eq("MATERIAL_IQC"), eq("PI_INK_B003"), eq("MATERIAL_BATCH"), any(), eq("qe1001"), eq("material-service"), any());
    }

    @Test
    void createIncomingInspectionShouldHoldBatchWhenResultIsNg() {
        MaterialBatch batch = batch("PI_INK_B004", "100", "0", "0", "AVAILABLE");
        batch.setSupplierCode("SUP-PI-01");
        Supplier supplier = supplier("SUP-PI-01", "QUALIFIED", "LOW");
        when(batchMapper.selectByBatchNoForUpdate("PI_INK_B004")).thenReturn(batch);
        when(supplierMapper.selectOne(any())).thenReturn(supplier);

        Map<String, Object> result = materialService.createIncomingInspection("PI_INK_B004", Map.of(
                "result", "NG",
                "defectCode", "D-PARTICLE",
                "defectDescription", "来料颗粒超标",
                "inspector", "qe1001"
        ));

        assertThat(batch.getQualityStatus()).isEqualTo("NG");
        assertThat(batch.getStatus()).isEqualTo("HOLD");
        verify(batchMapper).updateById(batch);
        verify(coaAttachmentMapper, never()).insert(any());

        ArgumentCaptor<MaterialIncomingInspection> inspectionCaptor = ArgumentCaptor.forClass(MaterialIncomingInspection.class);
        verify(incomingInspectionMapper).insert(inspectionCaptor.capture());
        assertThat(inspectionCaptor.getValue().getResult()).isEqualTo("NG");
        assertThat(inspectionCaptor.getValue().getDefectCode()).isEqualTo("D-PARTICLE");

        ArgumentCaptor<SupplierCorrectiveAction> actionCaptor = ArgumentCaptor.forClass(SupplierCorrectiveAction.class);
        verify(supplierCorrectiveActionMapper).insert(actionCaptor.capture());
        SupplierCorrectiveAction action = actionCaptor.getValue();
        assertThat(action.getSupplierCode()).isEqualTo("SUP-PI-01");
        assertThat(action.getSourceType()).isEqualTo("IQC");
        assertThat(action.getSourceNo()).isEqualTo(inspectionCaptor.getValue().getInspectionNo());
        assertThat(action.getSeverity()).isEqualTo("HIGH");
        assertThat(action.getStatus()).isEqualTo("OPEN");
        assertThat(action.getDueTime()).isNotNull();
        assertThat(result).containsKey("supplierCorrectiveAction");

        verify(supplierMapper).updateById(supplier);
        assertThat(supplier.getQualificationStatus()).isEqualTo("CONDITIONAL");
        assertThat(supplier.getRiskLevel()).isEqualTo("HIGH");
    }

    @Test
    void supplierPerformanceShouldAggregateBatchAndInspectionRisk() {
        MaterialBatch lowRiskBatch = batch("PI_INK_A001", "90", "0", "0", "AVAILABLE");
        lowRiskBatch.setSupplierCode("SUP-A");
        lowRiskBatch.setReceivedTime(LocalDateTime.now().minusDays(1));

        MaterialBatch highRiskBatch = batch("PI_INK_B001", "40", "0", "0", "HOLD");
        highRiskBatch.setSupplierCode("SUP-B");
        highRiskBatch.setQualityStatus("NG");
        highRiskBatch.setReceivedTime(LocalDateTime.now().minusHours(12));

        when(batchMapper.selectList(any())).thenReturn(List.of(lowRiskBatch, highRiskBatch));
        when(incomingInspectionMapper.selectList(any())).thenReturn(List.of(
                incomingInspection("MIQC-A001", "PI_INK_A001", "SUP-A", "PASS"),
                incomingInspection("MIQC-B001", "PI_INK_B001", "SUP-B", "NG"),
                incomingInspection("MIQC-B002", "PI_INK_B002", "SUP-B", "HOLD")
        ));

        List<Map<String, Object>> rows = materialService.supplierPerformance();

        assertThat(rows).hasSize(2);
        Map<String, Object> highRisk = rows.get(0);
        Map<String, Object> lowRisk = rows.get(1);
        assertThat(highRisk.get("supplierCode")).isEqualTo("SUP-B");
        assertThat(highRisk.get("riskLevel")).isEqualTo("HIGH");
        assertThat(highRisk.get("type")).isEqualTo("red");
        assertThat(highRisk.get("batchCount")).isEqualTo(1);
        assertThat(highRisk.get("inspectionCount")).isEqualTo(2);
        assertThat(highRisk.get("ngCount")).isEqualTo(1);
        assertThat(highRisk.get("holdCount")).isEqualTo(1);
        assertThat(highRisk.get("riskBatchCount")).isEqualTo(1);
        assertThat(((Number) highRisk.get("score")).doubleValue())
                .isLessThan(((Number) lowRisk.get("score")).doubleValue());
        assertThat(lowRisk.get("supplierCode")).isEqualTo("SUP-A");
        assertThat(lowRisk.get("riskLevel")).isEqualTo("LOW");
        assertThat(lowRisk.get("passRateText")).isEqualTo("100.0%");
        verify(coaAttachmentMapper, never()).selectList(any());
    }

    @Test
    void supplierScoreTrendsShouldAggregateMonthlyIqcAnd8dRisk() {
        MaterialBatch lowRiskBatch = batch("PI_INK_A001", "90", "0", "0", "AVAILABLE");
        lowRiskBatch.setSupplierCode("SUP-A");
        lowRiskBatch.setReceivedTime(LocalDateTime.now().minusMonths(1));

        MaterialBatch highRiskBatch = batch("PI_INK_B001", "40", "0", "0", "HOLD");
        highRiskBatch.setSupplierCode("SUP-B");
        highRiskBatch.setQualityStatus("NG");
        highRiskBatch.setReceivedTime(LocalDateTime.now().minusDays(1));

        SupplierCorrectiveAction action = correctiveAction("SCA-001", "SUP-B", "OPEN", "HIGH");
        action.setCreatedTime(LocalDateTime.now().minusDays(1));
        action.setDueTime(LocalDateTime.now().minusHours(2));

        when(supplierMapper.selectList(any())).thenReturn(List.of(
                supplier("SUP-A", "QUALIFIED", "LOW"),
                supplier("SUP-B", "CONDITIONAL", "HIGH")
        ));
        when(batchMapper.selectList(any())).thenReturn(List.of(lowRiskBatch, highRiskBatch));
        when(incomingInspectionMapper.selectList(any())).thenReturn(List.of(
                incomingInspection("MIQC-A001", "PI_INK_A001", "SUP-A", "PASS"),
                incomingInspection("MIQC-B001", "PI_INK_B001", "SUP-B", "NG"),
                incomingInspection("MIQC-B002", "PI_INK_B002", "SUP-B", "HOLD")
        ));
        when(supplierCorrectiveActionMapper.selectList(any())).thenReturn(List.of(action));

        List<Map<String, Object>> rows = materialService.supplierScoreTrends(6);

        assertThat(rows).hasSize(2);
        Map<String, Object> highRisk = rows.get(0);
        Map<String, Object> lowRisk = rows.get(1);
        assertThat(highRisk.get("supplierCode")).isEqualTo("SUP-B");
        assertThat(highRisk.get("latestRiskLevel")).isEqualTo("HIGH");
        assertThat(highRisk.get("actionWindowCount")).isEqualTo(1L);
        assertThat(highRisk.get("overdueWindowCount")).isEqualTo(1L);
        assertThat(highRisk.get("summary")).asString().contains("超期8D");
        assertThat(((Number) highRisk.get("latestScore")).doubleValue())
                .isLessThan(((Number) lowRisk.get("latestScore")).doubleValue());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> trend = (List<Map<String, Object>>) highRisk.get("trend");
        assertThat(trend).hasSize(6);
        Map<String, Object> latestPoint = trend.get(trend.size() - 1);
        assertThat(latestPoint.get("ngCount")).isEqualTo(1);
        assertThat(latestPoint.get("holdCount")).isEqualTo(1);
        assertThat(latestPoint.get("openActionCount")).isEqualTo(1);
        assertThat(latestPoint.get("overdueActionCount")).isEqualTo(1);
    }

    @Test
    void createSupplierQualificationReviewShouldSnapshotSuggestedDecisionAndAudit() {
        MaterialBatch batch = batch("PI_INK_A001", "90", "0", "0", "AVAILABLE");
        batch.setSupplierCode("SUP-A");
        Supplier supplier = supplier("SUP-A", "PENDING", "MEDIUM");

        when(supplierMapper.selectOne(any())).thenReturn(supplier);
        when(supplierQualificationReviewTaskMapper.selectCount(any())).thenReturn(0L);
        when(batchMapper.selectList(any())).thenReturn(List.of(batch));
        when(incomingInspectionMapper.selectList(any())).thenReturn(List.of(
                incomingInspection("MIQC-A001", "PI_INK_A001", "SUP-A", "PASS")
        ));
        when(supplierCorrectiveActionMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> row = materialService.createSupplierQualificationReviewTask("SUP-A", Map.of(
                "operator", "qe1003",
                "reviewType", "PERIODIC",
                "triggerReason", "周期复审到期"
        ));

        ArgumentCaptor<SupplierQualificationReviewTask> taskCaptor = ArgumentCaptor.forClass(SupplierQualificationReviewTask.class);
        verify(supplierQualificationReviewTaskMapper).insert(taskCaptor.capture());
        SupplierQualificationReviewTask task = taskCaptor.getValue();
        assertThat(task.getTaskNo()).startsWith("SQR-");
        assertThat(task.getSupplierCode()).isEqualTo("SUP-A");
        assertThat(task.getReviewType()).isEqualTo("PERIODIC");
        assertThat(task.getQualificationBefore()).isEqualTo("PENDING");
        assertThat(task.getSuggestedQualification()).isEqualTo("QUALIFIED");
        assertThat(task.getSuggestedRisk()).isEqualTo("LOW");
        assertThat(task.getReviewStatus()).isEqualTo("OPEN");
        assertThat(task.getDueTime()).isAfter(LocalDateTime.now().plusDays(20));
        assertThat(task.getPerformanceSnapshot()).contains("score");
        assertThat(row.get("suggestedQualification")).isEqualTo("QUALIFIED");
        verify(auditLogService).record(eq("SUPPLIER_QUALIFICATION_REVIEW_CREATE"), eq(task.getTaskNo()), eq("SUPPLIER_REVIEW"),
                any(), eq("qe1003"), eq("material-service"), any());
    }

    @Test
    void decideSupplierQualificationReviewShouldApplySuggestedStatusAndAudit() {
        SupplierQualificationReviewTask task = reviewTask("SQR-001", "SUP-A", "OPEN");
        Supplier supplier = supplier("SUP-A", "CONDITIONAL", "MEDIUM");

        when(supplierQualificationReviewTaskMapper.selectOne(any())).thenReturn(task);
        when(supplierMapper.selectOne(any())).thenReturn(supplier);
        when(supplierCorrectiveActionMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> row = materialService.decideSupplierQualificationReviewTask("SQR-001", Map.of(
                "decision", "APPROVE",
                "qualificationStatus", "QUALIFIED",
                "riskLevel", "LOW",
                "decisionComment", "复审通过，恢复正式准入",
                "operator", "qe1003"
        ));

        assertThat(task.getReviewStatus()).isEqualTo("APPROVED");
        assertThat(task.getDecision()).isEqualTo("APPROVE");
        assertThat(task.getReviewTime()).isNotNull();
        assertThat(supplier.getQualificationStatus()).isEqualTo("QUALIFIED");
        assertThat(supplier.getRiskLevel()).isEqualTo("LOW");
        assertThat(supplier.getNextAuditDue()).isAfter(LocalDateTime.now().plusDays(80));
        assertThat(row.get("reviewStatus")).isEqualTo("APPROVED");
        assertThat(row).containsKey("supplier");
        verify(supplierMapper).updateById(supplier);
        verify(supplierQualificationReviewTaskMapper).updateById(task);
        verify(auditLogService).record(eq("SUPPLIER_QUALIFICATION_REVIEW_DECIDE"), eq("SQR-001"), eq("SUPPLIER_REVIEW"),
                any(), eq("qe1003"), eq("material-service"), any());
    }

    @Test
    void suppliersShouldMergeMasterDataPerformanceAndOpen8d() {
        MaterialBatch batch = batch("PI_INK_A001", "90", "0", "0", "AVAILABLE");
        batch.setSupplierCode("SUP-A");
        Supplier supplier = supplier("SUP-A", "QUALIFIED", "LOW");
        SupplierCorrectiveAction action = correctiveAction("SCA-001", "SUP-A", "OPEN", "HIGH");
        action.setDueTime(LocalDateTime.now().minusDays(1));

        when(batchMapper.selectList(any())).thenReturn(List.of(batch));
        when(incomingInspectionMapper.selectList(any())).thenReturn(List.of(
                incomingInspection("MIQC-A001", "PI_INK_A001", "SUP-A", "PASS")
        ));
        when(supplierCorrectiveActionMapper.selectList(any())).thenReturn(List.of(action));
        when(supplierMapper.selectList(any())).thenReturn(List.of(supplier));

        List<Map<String, Object>> rows = materialService.suppliers();

        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("supplierCode")).isEqualTo("SUP-A");
        assertThat(row.get("qualificationStatus")).isEqualTo("QUALIFIED");
        assertThat(row.get("openActionCount")).isEqualTo(1L);
        assertThat(row.get("overdueActionCount")).isEqualTo(1L);
        assertThat(row.get("latestActionNo")).isEqualTo("SCA-001");
        assertThat(row.get("type")).isEqualTo("red");
    }

    @Test
    void evaluateSupplierQualificationShouldUpdateQualifiedStatus() {
        MaterialBatch batch = batch("PI_INK_A001", "90", "0", "0", "AVAILABLE");
        batch.setSupplierCode("SUP-A");
        Supplier supplier = supplier("SUP-A", "PENDING", "MEDIUM");

        when(supplierMapper.selectOne(any())).thenReturn(supplier);
        when(batchMapper.selectList(any())).thenReturn(List.of(batch));
        when(incomingInspectionMapper.selectList(any())).thenReturn(List.of(
                incomingInspection("MIQC-A001", "PI_INK_A001", "SUP-A", "PASS")
        ));
        when(supplierCorrectiveActionMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> row = materialService.evaluateSupplierQualification("SUP-A", Map.of("operator", "qe1003"));

        assertThat(supplier.getQualificationStatus()).isEqualTo("QUALIFIED");
        assertThat(supplier.getRiskLevel()).isEqualTo("LOW");
        assertThat(supplier.getNextAuditDue()).isAfter(LocalDateTime.now().plusDays(80));
        assertThat(row.get("qualificationStatus")).isEqualTo("QUALIFIED");
        verify(supplierMapper).updateById(supplier);
        verify(auditLogService).record(eq("SUPPLIER_QUALIFICATION_EVALUATE"), eq("SUP-A"), eq("SUPPLIER"), any(), eq("qe1003"), eq("material-service"), any());
    }

    @Test
    void closeSupplierCorrectiveActionShouldCloseAndAudit() {
        SupplierCorrectiveAction action = correctiveAction("SCA-001", "SUP-A", "OPEN", "MEDIUM");
        when(supplierCorrectiveActionMapper.selectOne(any())).thenReturn(action);

        SupplierCorrectiveAction closed = materialService.closeSupplierCorrectiveAction("SCA-001", Map.of(
                "rootCause", "COA参数漂移",
                "correctiveAction", "供应商复核关键参数控制图",
                "preventiveAction", "连续三批加严抽检",
                "verificationResult", "复验通过",
                "operator", "qe1003"
        ));

        assertThat(closed.getStatus()).isEqualTo("CLOSED");
        assertThat(closed.getClosedTime()).isNotNull();
        assertThat(closed.getVerificationResult()).isEqualTo("复验通过");
        verify(supplierCorrectiveActionMapper).updateById(action);
        verify(auditLogService).record(eq("SUPPLIER_8D_CLOSE"), eq("SCA-001"), eq("SUPPLIER_8D"), any(), eq("qe1003"), eq("material-service"), any());
    }

    @Test
    void lockForTrackInShouldSkipWhenLotAlreadyHasLoadedMaterialForStep() {
        when(loadingMapper.selectCount(any())).thenReturn(1L);

        materialService.lockForTrackIn(lot(), "COATING", "COATER_01", "op1001");

        verify(bomMapper, never()).selectOne(any());
        verify(batchMapper, never()).updateById(any());
        verify(loadingMapper, never()).insert(any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void consumeForTrackOutShouldCreateTraceableConsumptionAndMoveBatchQuantities() {
        Lot lot = lot();
        LotStepRecord stepRecord = stepRecord();
        MaterialLoading loading = loading();
        MaterialBatch batch = batch("PI_INK_B001", "50", "50", "0", "AVAILABLE");
        when(loadingMapper.selectList(any())).thenReturn(List.of(loading));
        when(batchMapper.selectOne(any())).thenReturn(batch);

        materialService.consumeForTrackOut(lot, stepRecord);

        ArgumentCaptor<MaterialConsumption> consumptionCaptor = ArgumentCaptor.forClass(MaterialConsumption.class);
        verify(consumptionMapper).insert(consumptionCaptor.capture());
        MaterialConsumption consumption = consumptionCaptor.getValue();
        assertThat(consumption.getLotNo()).isEqualTo("LOT001");
        assertThat(consumption.getStepCode()).isEqualTo("COATING");
        assertThat(consumption.getEquipmentCode()).isEqualTo("COATER_01");
        assertThat(consumption.getMaterialCode()).isEqualTo("PI_INK");
        assertThat(consumption.getBatchNo()).isEqualTo("PI_INK_B001");
        assertThat(consumption.getConsumedQty()).isEqualByComparingTo("50");
        assertThat(consumption.getOperator()).isEqualTo("op1001");
        assertThat(consumption.getStepRecordId()).isEqualTo(1001L);
        assertThat(consumption.getTraceStatus()).isEqualTo("TRACEABLE");
        assertThat(consumption.getConsumeTime()).isNotNull();

        assertThat(loading.getStatus()).isEqualTo("CONSUMED");
        assertThat(loading.getConsumedTime()).isEqualTo(consumption.getConsumeTime());
        verify(loadingMapper).updateById(loading);

        assertThat(batch.getReservedQty()).isEqualByComparingTo("0");
        assertThat(batch.getConsumedQty()).isEqualByComparingTo("50");
        assertThat(batch.getStatus()).isEqualTo("AVAILABLE");
        assertThat(batch.getUpdatedTime()).isNotNull();
        verify(batchMapper).updateById(batch);

        verify(auditLogService).record(eq("MATERIAL_CONSUME"), eq("LOT001"), eq("LOT"), any(), eq("op1001"), eq("material-service"), any());
    }

    @Test
    void materialConsumptionsShouldApplyLotDataScope() {
        when(rolePermissionService.dataScopeCondition(any(), any(), any(), any(), any(), any()))
                .thenReturn(new RolePermissionService.DataScopeCondition("LINE", "line_code = {0}", List.of("LINE_01")));
        when(consumptionMapper.selectList(any())).thenReturn(List.of());

        assertThat(materialService.materialConsumptions(null)).isEmpty();

        verify(rolePermissionService).dataScopeCondition(any(), any(), eq(""), eq("line_code"), isNull(), isNull());
        verify(consumptionMapper).selectList(any());
    }

    @Test
    void carriersShouldApplyLineDataScope() {
        when(rolePermissionService.dataScopeCondition(any(), any(), any(), any(), any(), any()))
                .thenReturn(new RolePermissionService.DataScopeCondition("LINE", "line_code = {0}", List.of("LINE_01")));
        when(carrierMapper.selectList(any())).thenReturn(List.of());

        assertThat(materialService.carriers()).isEmpty();

        verify(rolePermissionService).dataScopeCondition(any(), any(), eq(""), eq("line_code"), isNull(), isNull());
        verify(carrierMapper).selectList(any());
    }

    private Lot lot() {
        Lot lot = new Lot();
        lot.setLotNo("LOT001");
        lot.setOrderNo("MO001");
        lot.setProductCode("OLED_PANEL");
        lot.setQty(100);
        return lot;
    }

    private LotStepRecord stepRecord() {
        LotStepRecord record = new LotStepRecord();
        record.setId(1001L);
        record.setLotNo("LOT001");
        record.setStepCode("COATING");
        record.setEquipmentCode("COATER_01");
        record.setOperator("op1001");
        return record;
    }

    private Bom activeBom() {
        Bom bom = new Bom();
        bom.setId(10L);
        bom.setBomCode("BOM-OLED-01");
        bom.setProductCode("OLED_PANEL");
        bom.setStatus("ACTIVE");
        return bom;
    }

    private BomItem bomItem(String materialCode, String materialName, String stepCode, String requiredQty) {
        BomItem item = new BomItem();
        item.setId(20L);
        item.setBomId(10L);
        item.setMaterialCode(materialCode);
        item.setMaterialName(materialName);
        item.setStepCode(stepCode);
        item.setRequiredQty(new BigDecimal(requiredQty));
        item.setUnit("kg");
        item.setIsKeyMaterial(1);
        item.setSubstituteGroup("PI");
        item.setSubstitutePriority(1);
        item.setSubstituteEnabled(1);
        return item;
    }

    private BomChangeRequest bomChange(String changeNo, String status) {
        BomChangeRequest change = new BomChangeRequest();
        change.setId(50L);
        change.setChangeNo(changeNo);
        change.setEcoNo("ECO-" + changeNo);
        change.setEcoRiskLevel("MEDIUM");
        change.setEcoApprovalStatus("PENDING");
        change.setEcoRequiredRoles("PE,QE,PLANNER");
        change.setProductCode("OLED_PANEL");
        change.setSourceBomCode("BOM-OLED-01");
        change.setTargetBomCode("BOM-OLED-02");
        change.setTargetVersion("V02");
        change.setStatus(status);
        change.setRequestedBy("pe1001");
        change.setRequestedTime(LocalDateTime.now().minusHours(1));
        return change;
    }

    private BomEcoApprovalTask bomEcoTask(String taskNo, String changeNo, String role, String status) {
        BomEcoApprovalTask task = new BomEcoApprovalTask();
        task.setId(60L);
        task.setTaskNo(taskNo);
        task.setChangeNo(changeNo);
        task.setEcoNo("ECO-" + changeNo);
        task.setProductCode("OLED_PANEL");
        task.setTargetBomCode("BOM-OLED-02");
        task.setApprovalRole(role);
        task.setApprovalStatus(status);
        task.setSlaLevel("P2");
        task.setSlaHours(24);
        task.setDueTime(LocalDateTime.now().plusHours(24));
        task.setCreatedBy("pe1001");
        task.setCreatedTime(LocalDateTime.now().minusHours(1));
        return task;
    }

    private MaterialBatch batch(String batchNo, String availableQty, String reservedQty, String consumedQty, String status) {
        MaterialBatch batch = new MaterialBatch();
        batch.setId(30L);
        batch.setMaterialCode("PI_INK");
        batch.setMaterialName("PI液");
        batch.setBatchNo(batchNo);
        batch.setTotalQty(new BigDecimal("100"));
        batch.setAvailableQty(new BigDecimal(availableQty));
        batch.setReservedQty(new BigDecimal(reservedQty));
        batch.setConsumedQty(new BigDecimal(consumedQty));
        batch.setFrozenQty(BigDecimal.ZERO);
        batch.setReturnedQty(BigDecimal.ZERO);
        batch.setUnit("kg");
        batch.setStatus(status);
        batch.setQualityStatus("PASS");
        batch.setStockVersion(0L);
        batch.setExpireTime(LocalDateTime.now().plusDays(30));
        return batch;
    }

    private MaterialIncomingInspection incomingInspection(String inspectionNo, String batchNo,
                                                          String supplierCode, String result) {
        MaterialIncomingInspection inspection = new MaterialIncomingInspection();
        inspection.setInspectionNo(inspectionNo);
        inspection.setBatchNo(batchNo);
        inspection.setMaterialCode("PI_INK");
        inspection.setMaterialName("PI液");
        inspection.setSupplierCode(supplierCode);
        inspection.setResult(result);
        inspection.setInspectionTime(LocalDateTime.now().minusHours(1));
        return inspection;
    }

    private Supplier supplier(String supplierCode, String qualificationStatus, String riskLevel) {
        Supplier supplier = new Supplier();
        supplier.setId(80L);
        supplier.setSupplierCode(supplierCode);
        supplier.setSupplierName(supplierCode + " 供应商");
        supplier.setSupplierType("MATERIAL");
        supplier.setMaterialClass("CHEMICAL");
        supplier.setQualificationStatus(qualificationStatus);
        supplier.setRiskLevel(riskLevel);
        supplier.setScore(new BigDecimal("95.0"));
        supplier.setPassRate(new BigDecimal("100.0"));
        supplier.setOwner("qe1003");
        supplier.setStatus("ACTIVE");
        supplier.setCreatedTime(LocalDateTime.now().minusDays(30));
        supplier.setUpdatedTime(LocalDateTime.now().minusDays(1));
        return supplier;
    }

    private SupplierCorrectiveAction correctiveAction(String actionNo, String supplierCode, String status, String severity) {
        SupplierCorrectiveAction action = new SupplierCorrectiveAction();
        action.setId(90L);
        action.setActionNo(actionNo);
        action.setSupplierCode(supplierCode);
        action.setSourceType("IQC");
        action.setSourceNo("MIQC-001");
        action.setIssueSummary("来料质检异常");
        action.setOwner("qe1003");
        action.setSeverity(severity);
        action.setStatus(status);
        action.setDueTime(LocalDateTime.now().plusDays(5));
        action.setCreatedTime(LocalDateTime.now().minusDays(1));
        action.setUpdatedTime(LocalDateTime.now().minusDays(1));
        return action;
    }

    private SupplierQualificationReviewTask reviewTask(String taskNo, String supplierCode, String status) {
        SupplierQualificationReviewTask task = new SupplierQualificationReviewTask();
        task.setId(91L);
        task.setTaskNo(taskNo);
        task.setSupplierCode(supplierCode);
        task.setReviewType("PERIODIC");
        task.setTriggerReason("周期复审");
        task.setQualificationBefore("CONDITIONAL");
        task.setRiskBefore("MEDIUM");
        task.setSuggestedQualification("QUALIFIED");
        task.setSuggestedRisk("LOW");
        task.setReviewStatus(status);
        task.setDueTime(LocalDateTime.now().plusDays(7));
        task.setCreatedBy("qe1003");
        task.setCreatedTime(LocalDateTime.now().minusDays(1));
        task.setUpdatedTime(LocalDateTime.now().minusDays(1));
        return task;
    }

    private MaterialLocation materialLocation(String locationCode, String materialClass, String status,
                                              String capacityQty, String usedQty, String unit) {
        MaterialLocation location = new MaterialLocation();
        location.setId(60L);
        location.setLocationCode(locationCode);
        location.setZoneCode("ZONE-A");
        location.setAreaCode("WMS");
        location.setStorageType("NORMAL");
        location.setMaterialClass(materialClass);
        location.setStatus(status);
        location.setCapacityQty(new BigDecimal(capacityQty));
        location.setUsedQty(new BigDecimal(usedQty));
        location.setUnit(unit);
        location.setStrategyPriority(10);
        return location;
    }

    private MaterialLocationTask locationTask(String taskNo, String taskType, String batchNo) {
        MaterialLocationTask task = new MaterialLocationTask();
        task.setId(70L);
        task.setTaskNo(taskNo);
        task.setTaskType(taskType);
        task.setBatchNo(batchNo);
        task.setMaterialCode("PI_INK");
        task.setMaterialName("PI胶");
        task.setSourceLocation("WMS-IN");
        task.setTargetLocation("WMS-A01");
        task.setPlannedQty(new BigDecimal("100"));
        task.setActualQty(new BigDecimal("100"));
        task.setUnit("kg");
        task.setStatus("DONE");
        task.setReason("产线补料前移库");
        task.setOperator("wms1001");
        task.setExecutedTime(LocalDateTime.now());
        task.setCreatedTime(LocalDateTime.now());
        return task;
    }

    private MaterialLoading loading() {
        MaterialLoading loading = new MaterialLoading();
        loading.setId(40L);
        loading.setLotNo("LOT001");
        loading.setOrderNo("MO001");
        loading.setProductCode("OLED_PANEL");
        loading.setStepCode("COATING");
        loading.setEquipmentCode("COATER_01");
        loading.setMaterialCode("PI_INK");
        loading.setMaterialName("PI液");
        loading.setBatchNo("PI_INK_B001");
        loading.setLoadedQty(new BigDecimal("50"));
        loading.setUnit("kg");
        loading.setStatus("LOADED");
        loading.setOperator("op1001");
        loading.setLoadedTime(LocalDateTime.now().minusMinutes(30));
        return loading;
    }
}
