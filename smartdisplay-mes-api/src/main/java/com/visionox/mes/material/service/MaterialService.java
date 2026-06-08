package com.visionox.mes.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.visionox.mes.auth.security.AuthContext;
import com.visionox.mes.auth.security.RolePermissionService;
import com.visionox.mes.common.BusinessException;
import com.visionox.mes.lot.entity.Lot;
import com.visionox.mes.lot.entity.LotStepRecord;
import com.visionox.mes.material.entity.Bom;
import com.visionox.mes.material.entity.BomChangeAttachment;
import com.visionox.mes.material.entity.BomChangeRequest;
import com.visionox.mes.material.entity.BomEcoApprovalTask;
import com.visionox.mes.material.entity.BomItem;
import com.visionox.mes.material.entity.Carrier;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * BOM、物料批次、上料锁定和消耗追溯服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialService {

    private static final DateTimeFormatter NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final AtomicLong NO_COUNTER = new AtomicLong();

    private final BomMapper bomMapper;
    private final BomItemMapper bomItemMapper;
    private final BomChangeRequestMapper bomChangeRequestMapper;
    private final BomChangeAttachmentMapper bomChangeAttachmentMapper;
    private final BomEcoApprovalTaskMapper bomEcoApprovalTaskMapper;
    private final MaterialBatchMapper batchMapper;
    private final MaterialLoadingMapper loadingMapper;
    private final MaterialConsumptionMapper consumptionMapper;
    private final MaterialInventoryTxnMapper inventoryTxnMapper;
    private final MaterialIncomingInspectionMapper incomingInspectionMapper;
    private final MaterialCoaAttachmentMapper coaAttachmentMapper;
    private final MaterialLocationMapper materialLocationMapper;
    private final MaterialLocationTaskMapper materialLocationTaskMapper;
    private final CarrierMapper carrierMapper;
    private final SupplierMapper supplierMapper;
    private final SupplierCorrectiveActionMapper supplierCorrectiveActionMapper;
    private final SupplierQualificationReviewTaskMapper supplierQualificationReviewTaskMapper;
    private final AuditLogService auditLogService;
    private final RolePermissionService rolePermissionService;

    /**
     * Track In 前校验当前工序关键物料是否齐套。
     */
    public void validateReadiness(Lot lot, String stepCode) {
        List<MaterialRequirement> requirements = keyRequirements(lot.getProductCode(), stepCode);
        for (MaterialRequirement requirement : requirements) {
            MaterialPlan plan = selectMaterialPlan(requirement, lot, false);
            if (plan == null) {
                throw new BusinessException(String.format(
                        "物料齐套校验失败: lot=%s, step=%s, substituteGroup=%s, candidates=%s",
                        lot.getLotNo(), stepCode, requirement.groupKey(), candidateRequirementText(requirement, lot)
                ));
            }
        }
    }

    /**
     * Track In 时锁定本工序关键物料批次，避免后续 Lot 抢占同一批可用量。
     */
    @Transactional(rollbackFor = Exception.class)
    public void lockForTrackIn(Lot lot, String stepCode, String equipmentCode, String operator) {
        Long exists = loadingMapper.selectCount(new LambdaQueryWrapper<MaterialLoading>()
                .eq(MaterialLoading::getLotNo, lot.getLotNo())
                .eq(MaterialLoading::getStepCode, stepCode)
                .eq(MaterialLoading::getStatus, "LOADED"));
        if (exists != null && exists > 0) {
            return;
        }

        List<MaterialRequirement> requirements = keyRequirements(lot.getProductCode(), stepCode);
        int lockedItems = 0;
        for (MaterialRequirement requirement : requirements) {
            MaterialPlan plan = selectMaterialPlan(requirement, lot, true);
            if (plan == null) {
                throw new BusinessException("物料锁定失败，替代料组可用量不足: "
                        + requirement.groupKey() + " / " + requirement.materialCodes());
            }
            BomItem item = plan.item();
            MaterialBatch batch = plan.batch();
            BigDecimal requiredQty = plan.requiredQty();
            if (requiredQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            batch.setAvailableQty(nvl(batch.getAvailableQty()).subtract(requiredQty));
            batch.setReservedQty(nvl(batch.getReservedQty()).add(requiredQty));
            batch.setStatus(batch.getAvailableQty().compareTo(BigDecimal.ZERO) <= 0 ? "RESERVED" : "AVAILABLE");
            batch.setStockVersion(nvl(batch.getStockVersion()) + 1);
            batch.setUpdatedTime(LocalDateTime.now());
            batchMapper.updateById(batch);

            MaterialLoading loading = new MaterialLoading();
            loading.setLoadingNo(nextNo("ML"));
            loading.setLotNo(lot.getLotNo());
            loading.setOrderNo(lot.getOrderNo());
            loading.setProductCode(lot.getProductCode());
            loading.setStepCode(stepCode);
            loading.setEquipmentCode(equipmentCode);
            loading.setMaterialCode(item.getMaterialCode());
            loading.setMaterialName(item.getMaterialName());
            loading.setBatchNo(batch.getBatchNo());
            loading.setRequiredQty(requiredQty);
            loading.setLoadedQty(requiredQty);
            loading.setUnit(item.getUnit());
            loading.setStatus("LOADED");
            loading.setOperator(valueOr(operator, "system"));
            loading.setLoadedTime(LocalDateTime.now());
            loading.setRemark("Track In 自动锁定关键物料，替代料组=" + requirement.groupKey());
            loadingMapper.insert(loading);
            lockedItems++;
        }
        audit("MATERIAL_LOAD", lot.getLotNo(), "LOT",
                "Track In 上料锁定 step=" + stepCode + ", items=" + lockedItems, valueOr(operator, "system"));
    }

    /**
     * Track Out 时把已锁定物料转为消耗履历。
     */
    @Transactional(rollbackFor = Exception.class)
    public void consumeForTrackOut(Lot lot, LotStepRecord record) {
        List<MaterialLoading> loadings = loadingMapper.selectList(new LambdaQueryWrapper<MaterialLoading>()
                .eq(MaterialLoading::getLotNo, lot.getLotNo())
                .eq(MaterialLoading::getStepCode, record.getStepCode())
                .eq(MaterialLoading::getStatus, "LOADED")
                .orderByAsc(MaterialLoading::getLoadedTime));
        for (MaterialLoading loading : loadings) {
            MaterialConsumption consumption = new MaterialConsumption();
            consumption.setConsumptionNo(nextNo("MC"));
            consumption.setLotNo(lot.getLotNo());
            consumption.setOrderNo(lot.getOrderNo());
            consumption.setProductCode(lot.getProductCode());
            consumption.setStepCode(record.getStepCode());
            consumption.setEquipmentCode(record.getEquipmentCode());
            consumption.setMaterialCode(loading.getMaterialCode());
            consumption.setMaterialName(loading.getMaterialName());
            consumption.setBatchNo(loading.getBatchNo());
            consumption.setConsumedQty(loading.getLoadedQty());
            consumption.setUnit(loading.getUnit());
            consumption.setOperator(valueOr(record.getOperator(), "system"));
            consumption.setConsumeTime(LocalDateTime.now());
            consumption.setStepRecordId(record.getId());
            consumption.setTraceStatus("TRACEABLE");
            consumptionMapper.insert(consumption);

            loading.setStatus("CONSUMED");
            loading.setConsumedTime(consumption.getConsumeTime());
            loadingMapper.updateById(loading);

            MaterialBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<MaterialBatch>()
                    .eq(MaterialBatch::getBatchNo, loading.getBatchNo())
                    .last("LIMIT 1"));
            if (batch != null) {
                BigDecimal qty = nvl(loading.getLoadedQty());
                batch.setReservedQty(maxZero(nvl(batch.getReservedQty()).subtract(qty)));
                batch.setConsumedQty(nvl(batch.getConsumedQty()).add(qty));
                if (nvl(batch.getAvailableQty()).compareTo(BigDecimal.ZERO) <= 0
                        && nvl(batch.getReservedQty()).compareTo(BigDecimal.ZERO) <= 0) {
                    batch.setStatus("CONSUMED");
                } else if ("RESERVED".equals(batch.getStatus())) {
                    batch.setStatus("AVAILABLE");
                }
                batch.setUpdatedTime(LocalDateTime.now());
                batchMapper.updateById(batch);
                adjustLocationUsage(batch.getLocation(), qty.negate());
            }
        }
        if (!loadings.isEmpty()) {
            audit("MATERIAL_CONSUME", lot.getLotNo(), "LOT",
                    "Track Out 物料消耗 step=" + record.getStepCode() + ", items=" + loadings.size(),
                    valueOr(record.getOperator(), "system"));
        }
    }

    public List<Map<String, Object>> boms() {
        return bomMapper.selectList(new LambdaQueryWrapper<Bom>()
                        .orderByDesc(Bom::getEffectiveTime)
                        .orderByDesc(Bom::getId))
                .stream()
                .map(this::bomRow)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> bomChangeRequests(String status) {
        LambdaQueryWrapper<BomChangeRequest> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(BomChangeRequest::getStatus, status);
        }
        wrapper.orderByDesc(BomChangeRequest::getRequestedTime)
                .orderByDesc(BomChangeRequest::getId)
                .last("LIMIT 100");
        return bomChangeRequestMapper.selectList(wrapper)
                .stream()
                .map(this::bomChangeRow)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> bomEcoApprovalTasks(String changeNo, String status) {
        LambdaQueryWrapper<BomEcoApprovalTask> wrapper = new LambdaQueryWrapper<>();
        if (changeNo != null && !changeNo.isBlank()) {
            wrapper.eq(BomEcoApprovalTask::getChangeNo, changeNo.trim());
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(BomEcoApprovalTask::getApprovalStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        wrapper.orderByAsc(BomEcoApprovalTask::getDueTime)
                .orderByDesc(BomEcoApprovalTask::getCreatedTime)
                .last("LIMIT 100");
        List<BomEcoApprovalTask> tasks = bomEcoApprovalTaskMapper.selectList(wrapper);
        return (tasks == null ? List.<BomEcoApprovalTask>of() : tasks)
                .stream()
                .map(this::bomEcoApprovalTaskRow)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public BomChangeRequest submitBomChange(Map<String, Object> request) {
        Bom sourceBom = sourceBom(request);
        String productCode = sourceBom != null ? sourceBom.getProductCode() : requiredText(request, "productCode");
        String targetVersion = requiredText(request, "targetVersion");
        String targetBomCode = text(request, "targetBomCode", "BOM_" + productCode + "_" + targetVersion);

        Long duplicatedCode = bomMapper.selectCount(new LambdaQueryWrapper<Bom>().eq(Bom::getBomCode, targetBomCode));
        if (duplicatedCode != null && duplicatedCode > 0) {
            throw new BusinessException("目标BOM编码已存在: " + targetBomCode);
        }
        Long duplicatedVersion = bomMapper.selectCount(new LambdaQueryWrapper<Bom>()
                .eq(Bom::getProductCode, productCode)
                .eq(Bom::getBomVersion, targetVersion));
        if (duplicatedVersion != null && duplicatedVersion > 0) {
            throw new BusinessException("产品BOM版本已存在: " + productCode + "/" + targetVersion);
        }

        Bom targetBom = new Bom();
        targetBom.setBomCode(targetBomCode);
        targetBom.setBomName(text(request, "bomName", productCode + " BOM " + targetVersion));
        targetBom.setProductCode(productCode);
        targetBom.setBomVersion(targetVersion);
        targetBom.setStatus("DRAFT");
        targetBom.setCreatedBy(text(request, "operator", AuthContext.username()));
        targetBom.setCreatedTime(LocalDateTime.now());
        targetBom.setUpdatedTime(targetBom.getCreatedTime());
        bomMapper.insert(targetBom);

        List<BomItem> createdItems = createTargetBomItems(sourceBom, targetBom, request);
        BomChangeRequest change = new BomChangeRequest();
        change.setChangeNo(nextNo("BCR"));
        change.setChangeType(text(request, "changeType", "VERSION_RELEASE"));
        change.setProductCode(productCode);
        change.setSourceBomCode(sourceBom == null ? null : sourceBom.getBomCode());
        change.setTargetBomCode(targetBomCode);
        change.setTargetBomId(targetBom.getId());
        change.setTargetVersion(targetVersion);
        change.setStatus("SUBMITTED");
        change.setReason(text(request, "reason", "BOM版本变更"));
        change.setBeforeSnapshot(sourceBom == null ? "{}" : bomRow(sourceBom).toString());
        change.setAfterSnapshot(targetBomSnapshot(targetBom, createdItems).toString());
        change.setSubstitutePolicySnapshot(substitutePolicySnapshot(createdItems));
        List<String> approvalRoles = ecoApprovalRoles(request);
        change.setEcoNo(text(request, "ecoNo", change.getChangeNo()));
        change.setEcoRiskLevel(ecoRiskLevel(request));
        change.setEcoPackageSnapshot(ecoPackageSnapshot(change, request, createdItems, approvalRoles));
        change.setEcoApprovalStatus("PENDING");
        change.setEcoRequiredRoles(String.join(",", approvalRoles));
        change.setRequestedBy(text(request, "operator", AuthContext.username()));
        change.setRequestedTime(LocalDateTime.now());
        change.setCreatedTime(change.getRequestedTime());
        change.setUpdatedTime(change.getRequestedTime());
        bomChangeRequestMapper.insert(change);
        List<BomEcoApprovalTask> approvalTasks = createBomEcoApprovalTasks(change, approvalRoles,
                change.getRequestedBy(), change.getRequestedTime());
        List<BomChangeAttachment> attachments = createBomChangeAttachments(change, request,
                change.getRequestedBy(), change.getRequestedTime());

        audit("BOM_CHANGE_SUBMIT", change.getChangeNo(), "BOM_CHANGE",
                "提交BOM变更 target=" + targetBomCode + ", items=" + createdItems.size()
                        + ", attachments=" + attachments.size()
                        + ", ecoApprovals=" + approvalTasks.size(), change.getRequestedBy());
        return change;
    }

    @Transactional(rollbackFor = Exception.class)
    public BomEcoApprovalTask decideBomEcoApprovalTask(String taskNo, Map<String, Object> request) {
        BomEcoApprovalTask task = lockedBomEcoApprovalTask(taskNo);
        if (!"PENDING".equals(task.getApprovalStatus())) {
            throw new BusinessException("BOM/ECO会签任务状态不可处理: " + task.getApprovalStatus());
        }
        String decision = normalizeEcoDecision(text(request, "decision", text(request, "action", "APPROVE")));
        boolean approved = "APPROVE".equals(decision);
        LocalDateTime now = LocalDateTime.now();
        task.setApprovalStatus(approved ? "APPROVED" : "REJECTED");
        task.setDecision(decision);
        task.setApprover(text(request, "approver", AuthContext.username()));
        task.setOpinion(text(request, "opinion", text(request, "comment", approved ? "会签通过" : "会签驳回")));
        task.setActionTime(now);
        task.setUpdatedTime(now);
        bomEcoApprovalTaskMapper.updateById(task);

        refreshBomEcoApprovalStatus(task.getChangeNo(), task.getApprover(), task.getOpinion());

        audit(approved ? "BOM_ECO_APPROVAL_APPROVE" : "BOM_ECO_APPROVAL_REJECT",
                task.getTaskNo(), "BOM_ECO_APPROVAL",
                "changeNo=" + task.getChangeNo() + ", role=" + task.getApprovalRole()
                        + ", opinion=" + task.getOpinion(), task.getApprover());
        return task;
    }

    @Transactional(rollbackFor = Exception.class)
    public BomChangeRequest reviewBomChange(String changeNo, Map<String, Object> request) {
        BomChangeRequest change = lockedBomChange(changeNo);
        if (!"SUBMITTED".equals(change.getStatus())) {
            throw new BusinessException("BOM变更单状态不可审批: " + change.getStatus());
        }
        String decision = text(request, "decision", text(request, "result", "APPROVED"))
                .trim()
                .toUpperCase(Locale.ROOT);
        boolean rejected = decision.contains("REJECT");
        if (!rejected) {
            assertBomEcoApprovalsReady(changeNo);
        }
        change.setStatus(rejected ? "REJECTED" : "APPROVED");
        change.setEcoApprovalStatus(rejected ? "REJECTED" : "APPROVED");
        change.setReviewedBy(text(request, "reviewer", AuthContext.username()));
        change.setReviewedTime(LocalDateTime.now());
        change.setReviewComment(text(request, "comment", rejected ? "驳回BOM变更" : "审批通过"));
        change.setUpdatedTime(change.getReviewedTime());
        bomChangeRequestMapper.updateById(change);
        if (rejected) {
            rejectPendingBomEcoApprovals(change, change.getReviewedBy(), change.getReviewComment());
        }
        List<BomChangeAttachment> attachments = createBomChangeAttachments(change, request,
                change.getReviewedBy(), change.getReviewedTime());

        audit(rejected ? "BOM_CHANGE_REJECT" : "BOM_CHANGE_APPROVE", changeNo, "BOM_CHANGE",
                change.getReviewComment() + ", attachments=" + attachments.size(), change.getReviewedBy());
        return change;
    }

    @Transactional(rollbackFor = Exception.class)
    public BomChangeRequest publishBomChange(String changeNo, Map<String, Object> request) {
        BomChangeRequest change = lockedBomChange(changeNo);
        if (!"APPROVED".equals(change.getStatus())) {
            throw new BusinessException("BOM变更单必须审批通过后才能发布: " + change.getStatus());
        }
        assertBomEcoApprovalsReady(changeNo);
        Bom targetBom = bomMapper.selectOne(new LambdaQueryWrapper<Bom>()
                .eq(Bom::getBomCode, change.getTargetBomCode())
                .last("LIMIT 1"));
        if (targetBom == null) {
            throw new BusinessException("目标BOM不存在: " + change.getTargetBomCode());
        }

        List<Bom> activeBoms = bomMapper.selectList(new LambdaQueryWrapper<Bom>()
                .eq(Bom::getProductCode, targetBom.getProductCode())
                .eq(Bom::getStatus, "ACTIVE"));
        for (Bom active : activeBoms) {
            if (active.getId() != null && active.getId().equals(targetBom.getId())) {
                continue;
            }
            active.setStatus("INACTIVE");
            active.setUpdatedTime(LocalDateTime.now());
            bomMapper.updateById(active);
        }

        targetBom.setStatus("ACTIVE");
        targetBom.setEffectiveTime(LocalDateTime.now());
        targetBom.setUpdatedTime(targetBom.getEffectiveTime());
        bomMapper.updateById(targetBom);

        change.setStatus("PUBLISHED");
        change.setPublishedBy(text(request, "publisher", AuthContext.username()));
        change.setPublishedTime(LocalDateTime.now());
        change.setUpdatedTime(change.getPublishedTime());
        bomChangeRequestMapper.updateById(change);

        audit("BOM_PUBLISH", targetBom.getBomCode(), "BOM",
                "BOM发布 changeNo=" + changeNo + ", product=" + targetBom.getProductCode(), change.getPublishedBy());
        return change;
    }

    public Map<String, Object> materialReadiness() {
        List<Map<String, Object>> batchRows = batchMapper.selectList(new LambdaQueryWrapper<MaterialBatch>()
                        .orderByAsc(MaterialBatch::getFifoSeq)
                        .orderByDesc(MaterialBatch::getReceivedTime)
                        .last("LIMIT 100"))
                .stream()
                .map(this::batchRow)
                .collect(Collectors.toList());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("batches", batchRows);
        data.put("readiness", readiness(batchRows));
        data.put("checks", readinessChecks(batchRows));
        return data;
    }

    public List<Map<String, Object>> supplierPerformance() {
        Map<String, SupplierScore> suppliers = new LinkedHashMap<>();

        List<MaterialBatch> batches = batchMapper.selectList(new LambdaQueryWrapper<MaterialBatch>()
                .orderByDesc(MaterialBatch::getReceivedTime)
                .orderByDesc(MaterialBatch::getId)
                .last("LIMIT 200"));
        for (MaterialBatch batch : batches) {
            SupplierScore score = suppliers.computeIfAbsent(supplierKey(batch.getSupplierCode()), SupplierScore::new);
            score.batchCount++;
            score.availableQty = score.availableQty.add(nvl(batch.getAvailableQty()));
            if ("AVAILABLE".equals(batch.getStatus()) && "PASS".equals(batch.getQualityStatus())) {
                score.availableBatchCount++;
            }
            if (riskBatch(batch)) {
                score.riskBatchCount++;
            }
            score.addMaterial(batch.getMaterialCode());
            if (batch.getReceivedTime() != null
                    && (score.lastBatchTime == null || batch.getReceivedTime().isAfter(score.lastBatchTime))) {
                score.lastBatchTime = batch.getReceivedTime();
            }
        }

        List<MaterialIncomingInspection> inspections = incomingInspectionMapper.selectList(
                new LambdaQueryWrapper<MaterialIncomingInspection>()
                        .orderByDesc(MaterialIncomingInspection::getInspectionTime)
                        .orderByDesc(MaterialIncomingInspection::getId)
                        .last("LIMIT 200"));
        for (MaterialIncomingInspection inspection : inspections) {
            SupplierScore score = suppliers.computeIfAbsent(supplierKey(inspection.getSupplierCode()), SupplierScore::new);
            score.inspectionCount++;
            switch (incomingResult(inspection.getResult())) {
                case "PASS" -> score.passCount++;
                case "NG" -> score.ngCount++;
                default -> score.holdCount++;
            }
            score.addMaterial(inspection.getMaterialCode());
            if (inspection.getInspectionTime() != null
                    && (score.lastInspectionTime == null || inspection.getInspectionTime().isAfter(score.lastInspectionTime))) {
                score.lastInspectionTime = inspection.getInspectionTime();
            }
        }

        return suppliers.values().stream()
                .map(this::supplierPerformanceRow)
                .sorted(Comparator
                        .comparingDouble((Map<String, Object> row) -> ((Number) row.get("score")).doubleValue())
                        .thenComparing(row -> String.valueOf(row.get("supplierCode"))))
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> supplierScoreTrends(int months) {
        int window = months <= 0 ? 6 : Math.min(months, 12);
        YearMonth startPeriod = YearMonth.now().minusMonths(window - 1L);
        YearMonth endPeriod = YearMonth.now();
        Map<String, SupplierTrend> trends = new LinkedHashMap<>();

        supplierMapper.selectList(new LambdaQueryWrapper<Supplier>()
                        .orderByAsc(Supplier::getSupplierCode)
                        .last("LIMIT 200"))
                .forEach(supplier -> supplierTrend(trends, supplierKey(supplier.getSupplierCode()))
                        .applySupplier(supplier));

        List<MaterialBatch> batches = batchMapper.selectList(new LambdaQueryWrapper<MaterialBatch>()
                .orderByDesc(MaterialBatch::getReceivedTime)
                .orderByDesc(MaterialBatch::getId)
                .last("LIMIT 500"));
        for (MaterialBatch batch : batches) {
            YearMonth period = periodOf(batch.getReceivedTime());
            if (!inPeriodWindow(period, startPeriod, endPeriod)) {
                continue;
            }
            SupplierTrendPoint point = supplierTrend(trends, supplierKey(batch.getSupplierCode())).point(period);
            point.batchCount++;
            if (riskBatch(batch)) {
                point.riskBatchCount++;
            }
            point.addMaterial(batch.getMaterialCode());
        }

        List<MaterialIncomingInspection> inspections = incomingInspectionMapper.selectList(
                new LambdaQueryWrapper<MaterialIncomingInspection>()
                        .orderByDesc(MaterialIncomingInspection::getInspectionTime)
                        .orderByDesc(MaterialIncomingInspection::getId)
                        .last("LIMIT 500"));
        for (MaterialIncomingInspection inspection : inspections) {
            YearMonth period = periodOf(inspection.getInspectionTime());
            if (!inPeriodWindow(period, startPeriod, endPeriod)) {
                continue;
            }
            SupplierTrendPoint point = supplierTrend(trends, supplierKey(inspection.getSupplierCode())).point(period);
            point.inspectionCount++;
            switch (incomingResult(inspection.getResult())) {
                case "PASS" -> point.passCount++;
                case "NG" -> point.ngCount++;
                default -> point.holdCount++;
            }
            point.addMaterial(inspection.getMaterialCode());
        }

        List<SupplierCorrectiveAction> actions = supplierCorrectiveActionMapper.selectList(
                new LambdaQueryWrapper<SupplierCorrectiveAction>()
                        .orderByDesc(SupplierCorrectiveAction::getCreatedTime)
                        .orderByDesc(SupplierCorrectiveAction::getId)
                        .last("LIMIT 500"));
        for (SupplierCorrectiveAction action : actions) {
            YearMonth period = periodOf(action.getCreatedTime() == null ? action.getDueTime() : action.getCreatedTime());
            if (!inPeriodWindow(period, startPeriod, endPeriod)) {
                continue;
            }
            SupplierTrendPoint point = supplierTrend(trends, supplierKey(action.getSupplierCode())).point(period);
            point.actionCount++;
            boolean open = !"CLOSED".equals(valueOr(action.getStatus(), "").toUpperCase(Locale.ROOT));
            if (open) {
                point.openActionCount++;
                if (action.getDueTime() != null && action.getDueTime().isBefore(LocalDateTime.now())) {
                    point.overdueActionCount++;
                }
                if (List.of("HIGH", "CRITICAL").contains(normalizeSupplierActionSeverity(action.getSeverity()))) {
                    point.highOpenActionCount++;
                }
            }
        }

        return trends.values().stream()
                .filter(SupplierTrend::hasActivity)
                .map(trend -> supplierTrendRow(trend, startPeriod, window))
                .sorted(Comparator
                        .comparingDouble((Map<String, Object> row) -> ((Number) row.get("latestScore")).doubleValue())
                        .thenComparing(row -> String.valueOf(row.get("supplierCode"))))
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> suppliers() {
        Map<String, Map<String, Object>> performance = supplierPerformance().stream()
                .collect(Collectors.toMap(
                        row -> supplierKey(String.valueOf(row.get("supplierCode"))),
                        row -> row,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<SupplierCorrectiveAction> actions = supplierCorrectiveActionMapper.selectList(
                new LambdaQueryWrapper<SupplierCorrectiveAction>()
                        .orderByDesc(SupplierCorrectiveAction::getCreatedTime)
                        .last("LIMIT 300"));
        Map<String, List<SupplierCorrectiveAction>> actionsBySupplier = actions.stream()
                .collect(Collectors.groupingBy(action -> supplierKey(action.getSupplierCode()), LinkedHashMap::new, Collectors.toList()));

        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        List<Supplier> suppliers = supplierMapper.selectList(new LambdaQueryWrapper<Supplier>()
                .orderByAsc(Supplier::getSupplierCode)
                .last("LIMIT 200"));
        for (Supplier supplier : suppliers) {
            String key = supplierKey(supplier.getSupplierCode());
            rows.put(key, supplierRow(supplier, performance.get(key), actionsBySupplier.getOrDefault(key, List.of())));
        }
        for (Map.Entry<String, Map<String, Object>> entry : performance.entrySet()) {
            rows.putIfAbsent(entry.getKey(), supplierRow(null, entry.getValue(), actionsBySupplier.getOrDefault(entry.getKey(), List.of())));
        }
        return rows.values().stream()
                .sorted(Comparator
                        .comparing((Map<String, Object> row) -> qualificationRank(String.valueOf(row.get("qualificationStatus"))))
                        .thenComparing(row -> String.valueOf(row.get("supplierCode"))))
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> supplierCorrectiveActions(String supplierCode, String status) {
        LambdaQueryWrapper<SupplierCorrectiveAction> wrapper = new LambdaQueryWrapper<>();
        if (supplierCode != null && !supplierCode.isBlank()) {
            wrapper.eq(SupplierCorrectiveAction::getSupplierCode, supplierKey(supplierCode));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(SupplierCorrectiveAction::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        wrapper.orderByAsc(SupplierCorrectiveAction::getDueTime)
                .orderByDesc(SupplierCorrectiveAction::getCreatedTime)
                .last("LIMIT 100");
        return supplierCorrectiveActionMapper.selectList(wrapper).stream()
                .map(this::supplierCorrectiveActionRow)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> supplierQualificationReviewTasks(String supplierCode, String status) {
        LambdaQueryWrapper<SupplierQualificationReviewTask> wrapper = new LambdaQueryWrapper<>();
        if (supplierCode != null && !supplierCode.isBlank()) {
            wrapper.eq(SupplierQualificationReviewTask::getSupplierCode, supplierKey(supplierCode));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(SupplierQualificationReviewTask::getReviewStatus, normalizeReviewStatus(status));
        }
        wrapper.orderByAsc(SupplierQualificationReviewTask::getDueTime)
                .orderByDesc(SupplierQualificationReviewTask::getCreatedTime)
                .last("LIMIT 100");
        return supplierQualificationReviewTaskMapper.selectList(wrapper).stream()
                .map(this::supplierQualificationReviewTaskRow)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createSupplierQualificationReviewTask(String supplierCode, Map<String, Object> request) {
        Supplier supplier = findSupplier(supplierCode);
        SupplierQualificationReviewTask task = createSupplierQualificationReviewTaskInternal(supplier, request, true);
        return supplierQualificationReviewTaskRow(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> decideSupplierQualificationReviewTask(String taskNo, Map<String, Object> request) {
        SupplierQualificationReviewTask task = supplierQualificationReviewTaskMapper.selectOne(
                new LambdaQueryWrapper<SupplierQualificationReviewTask>()
                        .eq(SupplierQualificationReviewTask::getTaskNo, taskNo)
                        .last("LIMIT 1"));
        if (task == null) {
            throw new BusinessException("供应商准入复审任务不存在: " + taskNo);
        }
        if (!"OPEN".equals(valueOr(task.getReviewStatus(), "").toUpperCase(Locale.ROOT))) {
            throw new BusinessException("供应商准入复审任务已处理: " + taskNo);
        }
        String decision = normalizeReviewDecision(text(request, "decision", "APPROVE"));
        String operator = text(request, "operator", AuthContext.username());
        task.setDecision(decision);
        task.setDecisionComment(text(request, "decisionComment", text(request, "remark", "")));
        task.setReviewer(operator);
        task.setReviewTime(LocalDateTime.now());
        task.setReviewStatus("APPROVE".equals(decision) ? "APPROVED" : "REJECTED");
        task.setRequestSnapshot(request == null ? "{}" : request.toString());
        task.setUpdatedTime(LocalDateTime.now());

        Map<String, Object> supplierAfter = Map.of();
        if ("APPROVE".equals(decision)) {
            Supplier supplier = findSupplier(task.getSupplierCode());
            String targetQualification = normalizeQualificationStatus(
                    text(request, "qualificationStatus", task.getSuggestedQualification()));
            String targetRisk = normalizeRisk(text(request, "riskLevel", task.getSuggestedRisk()));
            supplier.setQualificationStatus(targetQualification);
            supplier.setRiskLevel(targetRisk);
            supplier.setLastAuditTime(LocalDateTime.now());
            supplier.setNextAuditDue(LocalDateTime.now().plusDays(nextAuditDays(targetQualification, targetRisk)));
            supplier.setRemark(valueOr(task.getDecisionComment(), "供应商准入复审通过"));
            supplier.setUpdatedTime(LocalDateTime.now());
            supplierMapper.updateById(supplier);
            supplierAfter = supplierRow(supplier, null, supplierCorrectiveActionMapper.selectList(
                    new LambdaQueryWrapper<SupplierCorrectiveAction>()
                            .eq(SupplierCorrectiveAction::getSupplierCode, supplier.getSupplierCode())
                            .orderByDesc(SupplierCorrectiveAction::getCreatedTime)
                            .last("LIMIT 100")));
        }

        supplierQualificationReviewTaskMapper.updateById(task);
        audit("SUPPLIER_QUALIFICATION_REVIEW_DECIDE", task.getTaskNo(), "SUPPLIER_REVIEW",
                "供应商准入复审决策 supplier=" + task.getSupplierCode() + ", decision=" + decision,
                operator);

        Map<String, Object> row = supplierQualificationReviewTaskRow(task);
        if (!supplierAfter.isEmpty()) {
            row.put("supplier", supplierAfter);
        }
        return row;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> evaluateSupplierQualification(String supplierCode, Map<String, Object> request) {
        Supplier supplier = findSupplier(supplierCode);
        String key = supplierKey(supplierCode);
        Map<String, Object> performance = supplierPerformance().stream()
                .filter(row -> key.equals(supplierKey(String.valueOf(row.get("supplierCode")))))
                .findFirst()
                .orElse(Map.of("supplierCode", key, "score", 0.0, "passRate", 0.0,
                        "riskLevel", "MEDIUM", "riskBatchCount", 0, "ngCount", 0, "holdCount", 0));
        List<SupplierCorrectiveAction> actions = supplierCorrectiveActionMapper.selectList(
                new LambdaQueryWrapper<SupplierCorrectiveAction>()
                        .eq(SupplierCorrectiveAction::getSupplierCode, key)
                        .orderByDesc(SupplierCorrectiveAction::getCreatedTime));
        QualificationDecision decision = qualificationDecision(performance, actions);
        LocalDateTime now = LocalDateTime.now();
        supplier.setScore(BigDecimal.valueOf(decision.score()).setScale(2, RoundingMode.HALF_UP));
        supplier.setPassRate(BigDecimal.valueOf(decision.passRate()).setScale(2, RoundingMode.HALF_UP));
        supplier.setRiskLevel(decision.riskLevel());
        supplier.setQualificationStatus(decision.status());
        supplier.setLastAuditTime(now);
        supplier.setNextAuditDue(now.plusDays(decision.nextAuditDays()));
        supplier.setRemark(text(request, "remark", decision.reason()));
        supplier.setUpdatedTime(now);
        supplierMapper.updateById(supplier);
        audit("SUPPLIER_QUALIFICATION_EVALUATE", key, "SUPPLIER",
                "供应商准入评估 status=" + decision.status() + ", score=" + decision.score(),
                text(request, "operator", AuthContext.username()));
        return supplierRow(supplier, performance, actions);
    }

    @Transactional(rollbackFor = Exception.class)
    public SupplierCorrectiveAction createSupplierCorrectiveAction(Map<String, Object> request) {
        SupplierCorrectiveAction action = createSupplierCorrectiveActionInternal(
                requiredText(request, "supplierCode"),
                text(request, "sourceType", "MANUAL"),
                text(request, "sourceNo", ""),
                requiredText(request, "issueSummary"),
                text(request, "severity", "MEDIUM"),
                text(request, "owner", text(request, "operator", AuthContext.username())),
                request,
                true
        );
        downgradeSupplierForAction(action.getSupplierCode(), action.getSeverity(), text(request, "operator", AuthContext.username()));
        return action;
    }

    @Transactional(rollbackFor = Exception.class)
    public SupplierCorrectiveAction closeSupplierCorrectiveAction(String actionNo, Map<String, Object> request) {
        SupplierCorrectiveAction action = supplierCorrectiveActionMapper.selectOne(
                new LambdaQueryWrapper<SupplierCorrectiveAction>()
                        .eq(SupplierCorrectiveAction::getActionNo, actionNo)
                        .last("LIMIT 1"));
        if (action == null) {
            throw new BusinessException("供应商8D整改单不存在: " + actionNo);
        }
        if ("CLOSED".equals(action.getStatus())) {
            throw new BusinessException("供应商8D整改单已关闭: " + actionNo);
        }
        action.setRootCause(text(request, "rootCause", action.getRootCause()));
        action.setContainmentAction(text(request, "containmentAction", action.getContainmentAction()));
        action.setCorrectiveAction(text(request, "correctiveAction", action.getCorrectiveAction()));
        action.setPreventiveAction(text(request, "preventiveAction", action.getPreventiveAction()));
        action.setVerificationResult(requiredText(request, "verificationResult"));
        action.setStatus("CLOSED");
        action.setClosedTime(LocalDateTime.now());
        action.setUpdatedTime(LocalDateTime.now());
        action.setRequestSnapshot(request == null ? "{}" : request.toString());
        supplierCorrectiveActionMapper.updateById(action);
        audit("SUPPLIER_8D_CLOSE", action.getActionNo(), "SUPPLIER_8D",
                "关闭供应商8D整改 supplier=" + action.getSupplierCode(),
                text(request, "operator", AuthContext.username()));
        return action;
    }

    public List<Map<String, Object>> materialLocations() {
        return materialLocationMapper.selectList(new LambdaQueryWrapper<MaterialLocation>()
                        .orderByAsc(MaterialLocation::getStrategyPriority)
                        .orderByAsc(MaterialLocation::getLocationCode)
                        .last("LIMIT 100"))
                .stream()
                .map(this::materialLocationRow)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> materialLocationTasks(String status, String batchNo) {
        LambdaQueryWrapper<MaterialLocationTask> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(MaterialLocationTask::getStatus, status);
        }
        if (batchNo != null && !batchNo.isBlank()) {
            wrapper.eq(MaterialLocationTask::getBatchNo, batchNo);
        }
        wrapper.orderByDesc(MaterialLocationTask::getExecutedTime)
                .orderByDesc(MaterialLocationTask::getId)
                .last("LIMIT 100");
        return materialLocationTaskMapper.selectList(wrapper).stream()
                .map(this::locationTaskRow)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> carriers() {
        LambdaQueryWrapper<Carrier> wrapper = new LambdaQueryWrapper<>();
        applyLineDataScope(wrapper);
        wrapper.orderByDesc(Carrier::getBindTime)
                .orderByDesc(Carrier::getId)
                .last("LIMIT 100");
        return carrierMapper.selectList(wrapper)
                .stream()
                .map(this::carrierRow)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> materialConsumptions(String lotNo) {
        LambdaQueryWrapper<MaterialConsumption> wrapper = new LambdaQueryWrapper<>();
        applyLotDataScope(wrapper);
        if (lotNo != null && !lotNo.isBlank()) {
            wrapper.eq(MaterialConsumption::getLotNo, lotNo);
        }
        wrapper.orderByDesc(MaterialConsumption::getConsumeTime).last("LIMIT 100");
        return consumptionMapper.selectList(wrapper).stream()
                .map(this::consumptionRow)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public MaterialBatch receiveMaterial(Map<String, Object> request) {
        BigDecimal qty = positiveQty(request, "qty");
        String batchNo = text(request, "batchNo", nextNo("MB"));
        String materialCode = requiredText(request, "materialCode");
        String unit = text(request, "unit", "EA");
        MaterialLocation location = resolveReceivingLocation(request, materialCode, unit, qty);
        Long exists = batchMapper.selectCount(new LambdaQueryWrapper<MaterialBatch>().eq(MaterialBatch::getBatchNo, batchNo));
        if (exists != null && exists > 0) {
            throw new BusinessException("物料批次已存在: " + batchNo);
        }

        MaterialBatch batch = new MaterialBatch();
        batch.setMaterialCode(materialCode);
        batch.setMaterialName(text(request, "materialName", batch.getMaterialCode()));
        batch.setBatchNo(batchNo);
        batch.setSupplierCode(text(request, "supplierCode", "WMS-SUP"));
        batch.setTotalQty(qty);
        batch.setAvailableQty(qty);
        batch.setReservedQty(BigDecimal.ZERO);
        batch.setConsumedQty(BigDecimal.ZERO);
        batch.setFrozenQty(BigDecimal.ZERO);
        batch.setReturnedQty(BigDecimal.ZERO);
        batch.setUnit(unit);
        batch.setQualityStatus(text(request, "qualityStatus", "PASS"));
        batch.setStatus("PASS".equals(batch.getQualityStatus()) ? "AVAILABLE" : "HOLD");
        batch.setReceivedTime(LocalDateTime.now());
        batch.setLocation(location == null ? text(request, "location", "WMS-IN") : location.getLocationCode());
        batch.setFifoSeq(intValue(value(request, "fifoSeq"), 100));
        batch.setCreatedBy(text(request, "operator", AuthContext.username()));
        batch.setStockVersion(1L);
        batch.setCreatedTime(LocalDateTime.now());
        batch.setUpdatedTime(batch.getCreatedTime());
        batchMapper.insert(batch);
        increaseLocationUsage(location, qty);

        insertTxn("RECEIVE", batch, BigDecimal.ZERO, batch.getAvailableQty(),
                BigDecimal.ZERO, batch.getFrozenQty(), BigDecimal.ZERO, batch.getReservedQty(),
                qty, null, text(request, "reason", "WMS receive"), text(request, "operator", AuthContext.username()), request);
        audit("MATERIAL_RECEIVE", batch.getBatchNo(), "MATERIAL_BATCH",
                "WMS入库 qty=" + qty.stripTrailingZeros().toPlainString() + ", location=" + batch.getLocation(),
                text(request, "operator", AuthContext.username()));
        return batch;
    }

    @Transactional(rollbackFor = Exception.class)
    public MaterialBatch freezeMaterial(String batchNo, Map<String, Object> request) {
        MaterialBatch batch = lockedBatch(batchNo);
        BigDecimal qty = positiveQty(request, "qty");
        if (nvl(batch.getAvailableQty()).compareTo(qty) < 0) {
            throw new BusinessException("冻结失败，可用库存不足: " + batchNo);
        }
        BigDecimal beforeAvailable = nvl(batch.getAvailableQty());
        BigDecimal beforeFrozen = nvl(batch.getFrozenQty());
        BigDecimal beforeReserved = nvl(batch.getReservedQty());

        batch.setAvailableQty(beforeAvailable.subtract(qty));
        batch.setFrozenQty(beforeFrozen.add(qty));
        batch.setStatus(batch.getAvailableQty().compareTo(BigDecimal.ZERO) <= 0 ? "FROZEN" : "AVAILABLE");
        touchStock(batch);
        batchMapper.updateById(batch);

        insertTxn("FREEZE", batch, beforeAvailable, batch.getAvailableQty(),
                beforeFrozen, batch.getFrozenQty(), beforeReserved, batch.getReservedQty(),
                qty.negate(), null, text(request, "reason", "WMS freeze"), text(request, "operator", AuthContext.username()), request);
        audit("MATERIAL_FREEZE", batchNo, "MATERIAL_BATCH",
                "WMS冻结 qty=" + qty.stripTrailingZeros().toPlainString(), text(request, "operator", AuthContext.username()));
        return batch;
    }

    @Transactional(rollbackFor = Exception.class)
    public MaterialBatch unfreezeMaterial(String batchNo, Map<String, Object> request) {
        MaterialBatch batch = lockedBatch(batchNo);
        BigDecimal qty = positiveQty(request, "qty");
        if (nvl(batch.getFrozenQty()).compareTo(qty) < 0) {
            throw new BusinessException("解冻失败，冻结库存不足: " + batchNo);
        }
        BigDecimal beforeAvailable = nvl(batch.getAvailableQty());
        BigDecimal beforeFrozen = nvl(batch.getFrozenQty());
        BigDecimal beforeReserved = nvl(batch.getReservedQty());

        batch.setFrozenQty(beforeFrozen.subtract(qty));
        batch.setAvailableQty(beforeAvailable.add(qty));
        if ("PASS".equals(batch.getQualityStatus())) {
            batch.setStatus("AVAILABLE");
        }
        touchStock(batch);
        batchMapper.updateById(batch);

        insertTxn("UNFREEZE", batch, beforeAvailable, batch.getAvailableQty(),
                beforeFrozen, batch.getFrozenQty(), beforeReserved, batch.getReservedQty(),
                qty, null, text(request, "reason", "WMS unfreeze"), text(request, "operator", AuthContext.username()), request);
        audit("MATERIAL_UNFREEZE", batchNo, "MATERIAL_BATCH",
                "WMS解冻 qty=" + qty.stripTrailingZeros().toPlainString(), text(request, "operator", AuthContext.username()));
        return batch;
    }

    @Transactional(rollbackFor = Exception.class)
    public MaterialBatch returnMaterial(String batchNo, Map<String, Object> request) {
        MaterialBatch batch = lockedBatch(batchNo);
        BigDecimal qty = positiveQty(request, "qty");
        BigDecimal beforeAvailable = nvl(batch.getAvailableQty());
        BigDecimal beforeFrozen = nvl(batch.getFrozenQty());
        BigDecimal beforeReserved = nvl(batch.getReservedQty());

        batch.setAvailableQty(beforeAvailable.add(qty));
        batch.setTotalQty(nvl(batch.getTotalQty()).add(qty));
        batch.setReturnedQty(nvl(batch.getReturnedQty()).add(qty));
        if ("PASS".equals(batch.getQualityStatus())) {
            batch.setStatus("AVAILABLE");
        }
        touchStock(batch);
        batchMapper.updateById(batch);
        adjustLocationUsage(batch.getLocation(), qty);

        insertTxn("RETURN", batch, beforeAvailable, batch.getAvailableQty(),
                beforeFrozen, batch.getFrozenQty(), beforeReserved, batch.getReservedQty(),
                qty, null, text(request, "reason", "WMS return"), text(request, "operator", AuthContext.username()), request);
        audit("MATERIAL_RETURN", batchNo, "MATERIAL_BATCH",
                "WMS退料 qty=" + qty.stripTrailingZeros().toPlainString(), text(request, "operator", AuthContext.username()));
        return batch;
    }

    @Transactional(rollbackFor = Exception.class)
    public MaterialBatch inventoryCount(String batchNo, Map<String, Object> request) {
        MaterialBatch batch = lockedBatch(batchNo);
        return applyInventoryCount(batch, request);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createLocationTask(Map<String, Object> request) {
        String taskType = normalizeLocationTaskType(text(request, "taskType", text(request, "type", "MOVE")));
        return switch (taskType) {
            case "MOVE", "PUTAWAY" -> createMoveLikeLocationTask(taskType, request);
            case "COUNT" -> createCountLocationTask(request);
            default -> throw new BusinessException("库位任务类型不支持: " + taskType);
        };
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> assignLocationTask(String taskNo, Map<String, Object> request) {
        MaterialLocationTask task = lockedLocationTask(taskNo);
        if (!List.of("CREATED", "ASSIGNED").contains(valueOr(task.getStatus(), ""))) {
            throw new BusinessException("当前库位任务状态不允许领取: " + task.getStatus());
        }
        String assignee = text(request, "assignedTo", text(request, "operator", AuthContext.username()));
        if (assignee.isBlank()) {
            throw new BusinessException("库位任务领取人不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        task.setStatus("ASSIGNED");
        task.setAssignedTo(assignee);
        task.setAssignedTime(now);
        task.setOperator(assignee);
        task.setUpdatedTime(now);
        materialLocationTaskMapper.updateById(task);
        audit("MATERIAL_LOCATION_TASK_ASSIGN", task.getTaskNo(), "MATERIAL_LOCATION_TASK",
                "领取库位任务: " + task.getTaskType() + ", batch=" + task.getBatchNo(), assignee);
        return Map.of("task", locationTaskRow(task));
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> completeLocationTask(String taskNo, Map<String, Object> request) {
        MaterialLocationTask task = lockedLocationTask(taskNo);
        if (!List.of("CREATED", "ASSIGNED", "EXECUTING").contains(valueOr(task.getStatus(), ""))) {
            throw new BusinessException("当前库位任务状态不允许完成: " + task.getStatus());
        }
        String operator = text(request, "operator", text(request, "executor", AuthContext.username()));
        if (operator.isBlank()) {
            operator = valueOr(task.getAssignedTo(), AuthContext.username());
        }
        task.setStatus("EXECUTING");
        task.setOperator(operator);
        task.setUpdatedTime(LocalDateTime.now());
        materialLocationTaskMapper.updateById(task);

        MaterialBatch batch = switch (normalizeLocationTaskType(task.getTaskType())) {
            case "MOVE", "PUTAWAY" -> completeMoveLikeLocationTask(task, request, operator);
            case "COUNT" -> completeCountLocationTask(task, request, operator);
            default -> throw new BusinessException("库位任务类型不支持: " + task.getTaskType());
        };

        LocalDateTime now = LocalDateTime.now();
        task.setStatus("DONE");
        task.setOperator(operator);
        task.setExecutedTime(now);
        task.setCompletedTime(now);
        task.setReviewer(text(request, "reviewer", text(request, "reviewedBy", "")));
        if (!valueOr(task.getReviewer(), "").isBlank()) {
            task.setReviewedTime(now);
        }
        task.setExceptionReason(text(request, "exceptionReason", valueOr(task.getExceptionReason(), "")));
        task.setUpdatedTime(now);
        materialLocationTaskMapper.updateById(task);
        audit("MATERIAL_LOCATION_TASK_COMPLETE", task.getTaskNo(), "MATERIAL_LOCATION_TASK",
                "完成库位任务: " + task.getTaskType() + ", batch=" + task.getBatchNo()
                        + ", actualQty=" + nvl(task.getActualQty()).stripTrailingZeros().toPlainString(),
                operator);
        return locationTaskResult(task, batch);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cancelLocationTask(String taskNo, Map<String, Object> request) {
        MaterialLocationTask task = lockedLocationTask(taskNo);
        if (!List.of("CREATED", "ASSIGNED").contains(valueOr(task.getStatus(), ""))) {
            throw new BusinessException("当前库位任务状态不允许取消: " + task.getStatus());
        }
        String operator = text(request, "operator", AuthContext.username());
        String cancelReason = text(request, "cancelReason", text(request, "reason", "WMS task cancelled"));
        String targetStatus = normalizeLocationTaskCancelStatus(text(request, "status",
                boolValue(request, "reject", false) ? "REJECTED" : "CANCELLED"));
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(targetStatus);
        task.setCancelledBy(operator);
        task.setCancelledTime(now);
        task.setCancelReason(cancelReason);
        task.setExceptionReason(text(request, "exceptionReason", valueOr(task.getExceptionReason(), "")));
        task.setUpdatedTime(now);
        materialLocationTaskMapper.updateById(task);
        audit("MATERIAL_LOCATION_TASK_CANCEL", task.getTaskNo(), "MATERIAL_LOCATION_TASK",
                targetStatus + " 库位任务: " + cancelReason, operator);
        return Map.of("task", locationTaskRow(task));
    }

    private MaterialBatch applyInventoryCount(MaterialBatch batch, Map<String, Object> request) {
        BigDecimal countedAvailable = nonNegativeQty(request, "countedAvailableQty");
        BigDecimal beforeAvailable = nvl(batch.getAvailableQty());
        BigDecimal beforeFrozen = nvl(batch.getFrozenQty());
        BigDecimal beforeReserved = nvl(batch.getReservedQty());
        BigDecimal delta = countedAvailable.subtract(beforeAvailable);

        batch.setAvailableQty(countedAvailable);
        batch.setTotalQty(countedAvailable
                .add(nvl(batch.getReservedQty()))
                .add(nvl(batch.getFrozenQty()))
                .add(nvl(batch.getConsumedQty())));
        if (countedAvailable.compareTo(BigDecimal.ZERO) <= 0 && nvl(batch.getFrozenQty()).compareTo(BigDecimal.ZERO) > 0) {
            batch.setStatus("FROZEN");
        } else if ("PASS".equals(batch.getQualityStatus())) {
            batch.setStatus("AVAILABLE");
        }
        batch.setLastCountTime(LocalDateTime.now());
        touchStock(batch);
        batchMapper.updateById(batch);
        adjustLocationUsage(batch.getLocation(), delta);

        insertTxn("COUNT", batch, beforeAvailable, batch.getAvailableQty(),
                beforeFrozen, batch.getFrozenQty(), beforeReserved, batch.getReservedQty(),
                delta, countedAvailable, text(request, "reason", "WMS inventory count"), text(request, "operator", AuthContext.username()), request);
        audit("MATERIAL_COUNT", batch.getBatchNo(), "MATERIAL_BATCH",
                "WMS盘点 countedAvailable=" + countedAvailable.stripTrailingZeros().toPlainString(), text(request, "operator", AuthContext.username()));
        return batch;
    }

    private Map<String, Object> createMoveLikeLocationTask(String taskType, Map<String, Object> request) {
        String batchNo = requiredText(request, "batchNo");
        String targetLocationCode = requiredText(request, "targetLocation");
        String operator = text(request, "operator", AuthContext.username());
        String reason = text(request, "reason", defaultLocationTaskReason(taskType));

        MaterialBatch batch = lockedBatch(batchNo);
        String sourceLocation = valueOr(batch.getLocation(), "");
        if (!sourceLocation.isBlank() && sourceLocation.equals(targetLocationCode)) {
            throw new BusinessException("目标库位不能与当前库位相同: " + targetLocationCode);
        }

        BigDecimal physicalQty = physicalStockQty(batch);
        BigDecimal moveQty = locationMoveQty(request, physicalQty);
        if (moveQty.compareTo(physicalQty) != 0) {
            throw new BusinessException("首版库位移库按批次整批执行，计划数量必须等于在库数量: "
                    + physicalQty.stripTrailingZeros().toPlainString());
        }
        MaterialLocation targetLocation = materialLocationMapper.selectByLocationCodeForUpdate(targetLocationCode);
        if (targetLocation == null) {
            throw new BusinessException("目标库位不存在或未维护: " + targetLocationCode);
        }
        validateReceivingLocation(targetLocation, inferMaterialClass(batch.getMaterialCode()), batch.getUnit(), moveQty);

        MaterialLocationTask task = createLocationTaskRecord(taskType, batch, sourceLocation,
                targetLocation.getLocationCode(), moveQty, BigDecimal.ZERO, "CREATED", reason, operator, request);
        audit("MATERIAL_LOCATION_TASK_CREATE", task.getTaskNo(), "MATERIAL_LOCATION_TASK",
                "创建库位任务: " + taskType + " batch=" + batchNo + ", from=" + sourceLocation + ", to="
                        + targetLocation.getLocationCode() + ", qty=" + moveQty.stripTrailingZeros().toPlainString(),
                operator);
        if (boolValue(request, "executeNow", false)) {
            return completeLocationTask(task.getTaskNo(), request);
        }
        return locationTaskResult(task, batch);
    }

    private Map<String, Object> createCountLocationTask(Map<String, Object> request) {
        String batchNo = requiredText(request, "batchNo");
        String operator = text(request, "operator", AuthContext.username());
        String reason = text(request, "reason", defaultLocationTaskReason("COUNT"));
        MaterialBatch batch = lockedBatch(batchNo);
        BigDecimal beforeAvailable = nvl(batch.getAvailableQty());
        BigDecimal countedAvailable = optionalLocationCountQty(request);

        MaterialLocationTask task = createLocationTaskRecord("COUNT", batch, batch.getLocation(),
                batch.getLocation(), beforeAvailable, countedAvailable, "CREATED", reason, operator, request);
        audit("MATERIAL_LOCATION_TASK_CREATE", task.getTaskNo(), "MATERIAL_LOCATION_TASK",
                "创建盘点任务: batch=" + batchNo + ", plannedAvailable="
                        + beforeAvailable.stripTrailingZeros().toPlainString(), operator);
        if (boolValue(request, "executeNow", false)) {
            return completeLocationTask(task.getTaskNo(), request);
        }
        return locationTaskResult(task, batch);
    }

    private MaterialBatch completeMoveLikeLocationTask(MaterialLocationTask task, Map<String, Object> request, String operator) {
        MaterialBatch batch = lockedBatch(task.getBatchNo());
        String currentLocation = valueOr(batch.getLocation(), "");
        String taskSourceLocation = valueOr(task.getSourceLocation(), "");
        if (!taskSourceLocation.isBlank() && !taskSourceLocation.equals(currentLocation)) {
            throw new BusinessException("批次当前库位与任务源库位不一致: task="
                    + taskSourceLocation + ", current=" + currentLocation);
        }
        String targetLocationCode = valueOr(task.getTargetLocation(), "");
        if (targetLocationCode.isBlank()) {
            targetLocationCode = requiredText(request, "targetLocation");
        }
        if (!currentLocation.isBlank() && currentLocation.equals(targetLocationCode)) {
            throw new BusinessException("目标库位不能与当前库位相同: " + targetLocationCode);
        }

        BigDecimal physicalQty = physicalStockQty(batch);
        BigDecimal actualQty = locationMoveQty(request, nvl(task.getPlannedQty()).compareTo(BigDecimal.ZERO) > 0
                ? nvl(task.getPlannedQty()) : physicalQty);
        if (actualQty.compareTo(physicalQty) != 0) {
            throw new BusinessException("库位移库按批次整批执行，实绩数量必须等于当前在库数量: "
                    + physicalQty.stripTrailingZeros().toPlainString());
        }

        MaterialLocation targetLocation = materialLocationMapper.selectByLocationCodeForUpdate(targetLocationCode);
        if (targetLocation == null) {
            throw new BusinessException("目标库位不存在或未维护: " + targetLocationCode);
        }
        validateReceivingLocation(targetLocation, inferMaterialClass(batch.getMaterialCode()), batch.getUnit(), actualQty);

        BigDecimal beforeAvailable = nvl(batch.getAvailableQty());
        BigDecimal beforeFrozen = nvl(batch.getFrozenQty());
        BigDecimal beforeReserved = nvl(batch.getReservedQty());

        adjustLocationUsage(currentLocation, actualQty.negate());
        increaseLocationUsage(targetLocation, actualQty);
        batch.setLocation(targetLocation.getLocationCode());
        touchStock(batch);
        batchMapper.updateById(batch);

        insertTxn(normalizeLocationTaskType(task.getTaskType()), batch, beforeAvailable, batch.getAvailableQty(),
                beforeFrozen, batch.getFrozenQty(), beforeReserved, batch.getReservedQty(),
                BigDecimal.ZERO, null, valueOr(task.getReason(), defaultLocationTaskReason(task.getTaskType())),
                operator, request);

        task.setSourceLocation(currentLocation);
        task.setTargetLocation(targetLocation.getLocationCode());
        task.setActualQty(actualQty);
        return batch;
    }

    private MaterialBatch completeCountLocationTask(MaterialLocationTask task, Map<String, Object> request, String operator) {
        MaterialBatch batch = lockedBatch(task.getBatchNo());
        BigDecimal countedAvailable = locationCountQty(request, task);
        Map<String, Object> countRequest = new LinkedHashMap<>(request == null ? Map.of() : request);
        countRequest.put("countedAvailableQty", countedAvailable);
        countRequest.put("reason", valueOr(task.getReason(), defaultLocationTaskReason("COUNT")));
        countRequest.put("operator", operator);
        MaterialBatch updated = applyInventoryCount(batch, countRequest);
        task.setSourceLocation(updated.getLocation());
        task.setTargetLocation(updated.getLocation());
        task.setActualQty(countedAvailable);
        return updated;
    }

    public List<Map<String, Object>> inventoryTransactions(String batchNo) {
        LambdaQueryWrapper<MaterialInventoryTxn> wrapper = new LambdaQueryWrapper<>();
        if (batchNo != null && !batchNo.isBlank()) {
            wrapper.eq(MaterialInventoryTxn::getBatchNo, batchNo);
        }
        wrapper.orderByDesc(MaterialInventoryTxn::getTxnTime).last("LIMIT 100");
        return inventoryTxnMapper.selectList(wrapper).stream()
                .map(this::txnRow)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> incomingInspections(String batchNo) {
        LambdaQueryWrapper<MaterialIncomingInspection> wrapper = new LambdaQueryWrapper<>();
        if (batchNo != null && !batchNo.isBlank()) {
            wrapper.eq(MaterialIncomingInspection::getBatchNo, batchNo);
        }
        wrapper.orderByDesc(MaterialIncomingInspection::getInspectionTime)
                .orderByDesc(MaterialIncomingInspection::getId)
                .last("LIMIT 100");
        return incomingInspectionMapper.selectList(wrapper).stream()
                .map(this::incomingInspectionRow)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createIncomingInspection(String batchNo, Map<String, Object> request) {
        MaterialBatch batch = lockedBatch(batchNo);
        String result = normalizeIncomingResult(text(request, "result", text(request, "qualityStatus", "PASS")));
        String inspector = text(request, "inspector", text(request, "operator", AuthContext.username()));
        LocalDateTime now = LocalDateTime.now();

        MaterialIncomingInspection inspection = new MaterialIncomingInspection();
        inspection.setInspectionNo(nextNo("MIQC"));
        inspection.setBatchNo(batch.getBatchNo());
        inspection.setMaterialCode(batch.getMaterialCode());
        inspection.setMaterialName(batch.getMaterialName());
        inspection.setSupplierCode(batch.getSupplierCode());
        inspection.setResult(result);
        inspection.setInspectedQty(nonNegativeQty(request, "inspectedQty"));
        inspection.setSampleQty(nonNegativeQty(request, "sampleQty"));
        inspection.setUnit(text(request, "unit", batch.getUnit()));
        inspection.setDefectCode(text(request, "defectCode", ""));
        inspection.setDefectDescription(text(request, "defectDescription", text(request, "defectDesc", "")));
        inspection.setCoaNo(text(request, "coaNo", ""));
        inspection.setConclusion(text(request, "conclusion", defaultIncomingConclusion(result)));
        inspection.setInspector(inspector);
        inspection.setInspectionTime(now);
        inspection.setSourceSystem(text(request, "sourceSystem", "qms-adapter"));
        inspection.setRequestSnapshot(request == null ? "{}" : request.toString());
        inspection.setCreatedTime(now);
        inspection.setUpdatedTime(now);
        incomingInspectionMapper.insert(inspection);

        List<MaterialCoaAttachment> attachments = createCoaAttachments(inspection, request, inspector, now);
        applyIncomingResult(batch, result);
        touchStock(batch);
        batchMapper.updateById(batch);

        SupplierCorrectiveAction supplierAction = null;
        if (!"PASS".equals(result)) {
            supplierAction = createSupplierCorrectiveActionInternal(
                    batch.getSupplierCode(),
                    "IQC",
                    inspection.getInspectionNo(),
                    "来料质检" + result + ": " + valueOr(inspection.getDefectDescription(), valueOr(inspection.getDefectCode(), "待补充缺陷原因")),
                    "NG".equals(result) ? "HIGH" : "MEDIUM",
                    inspector,
                    Map.of(
                            "batchNo", batch.getBatchNo(),
                            "inspectionNo", inspection.getInspectionNo(),
                            "result", result,
                            "defectCode", valueOr(inspection.getDefectCode(), ""),
                            "operator", inspector
                    ),
                    true
            );
            downgradeSupplierForAction(supplierAction.getSupplierCode(), supplierAction.getSeverity(), inspector);
        }

        audit("MATERIAL_IQC", batch.getBatchNo(), "MATERIAL_BATCH",
                "来料质检 result=" + result + ", attachments=" + attachments.size(), inspector);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("inspection", incomingInspectionRow(inspection, attachments));
        data.put("attachments", attachments.stream().map(this::coaAttachmentRow).collect(Collectors.toList()));
        data.put("batch", batchRow(batch));
        if (supplierAction != null) {
            data.put("supplierCorrectiveAction", supplierCorrectiveActionRow(supplierAction));
        }
        return data;
    }

    private Bom sourceBom(Map<String, Object> request) {
        String sourceBomCode = text(request, "sourceBomCode", "");
        if (!sourceBomCode.isBlank()) {
            Bom source = bomMapper.selectOne(new LambdaQueryWrapper<Bom>()
                    .eq(Bom::getBomCode, sourceBomCode)
                    .last("LIMIT 1"));
            if (source == null) {
                throw new BusinessException("源BOM不存在: " + sourceBomCode);
            }
            return source;
        }
        String productCode = requiredText(request, "productCode");
        Bom source = activeBom(productCode);
        if (source == null) {
            throw new BusinessException("未找到可复制的生效BOM: product=" + productCode);
        }
        return source;
    }

    private List<BomItem> createTargetBomItems(Bom sourceBom, Bom targetBom, Map<String, Object> request) {
        List<BomItem> items = itemRequestRows(request);
        if (items.isEmpty() && sourceBom != null) {
            items = bomItemMapper.selectList(new LambdaQueryWrapper<BomItem>()
                    .eq(BomItem::getBomId, sourceBom.getId())
                    .orderByAsc(BomItem::getStepCode)
                    .orderByAsc(BomItem::getSubstituteGroup)
                    .orderByAsc(BomItem::getSubstitutePriority)
                    .orderByAsc(BomItem::getId));
        }
        if (items.isEmpty()) {
            throw new BusinessException("BOM变更至少需要一条物料明细");
        }
        List<BomItem> created = new ArrayList<>();
        for (BomItem source : items) {
            BomItem item = copyItemForTarget(source, targetBom.getId());
            bomItemMapper.insert(item);
            created.add(item);
        }
        return created;
    }

    @SuppressWarnings("unchecked")
    private List<BomItem> itemRequestRows(Map<String, Object> request) {
        Object value = value(request, "items");
        if (!(value instanceof List<?> rows)) {
            return List.of();
        }
        List<BomItem> items = new ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> source)) {
                continue;
            }
            Map<String, Object> itemMap = (Map<String, Object>) source;
            BomItem item = new BomItem();
            item.setMaterialCode(requiredText(itemMap, "materialCode"));
            item.setMaterialName(text(itemMap, "materialName", item.getMaterialCode()));
            item.setStepCode(requiredText(itemMap, "stepCode"));
            item.setRequiredQty(positiveQty(itemMap, "requiredQty"));
            item.setUnit(text(itemMap, "unit", "EA"));
            item.setIsKeyMaterial(intValue(value(itemMap, "isKeyMaterial"), 1));
            item.setSubstituteGroup(text(itemMap, "substituteGroup", item.getMaterialCode()));
            item.setSubstitutePriority(intValue(value(itemMap, "substitutePriority"), 1));
            item.setSubstituteEnabled(intValue(value(itemMap, "substituteEnabled"), 1));
            items.add(item);
        }
        return items;
    }

    private BomItem copyItemForTarget(BomItem source, Long targetBomId) {
        BomItem item = new BomItem();
        item.setBomId(targetBomId);
        item.setMaterialCode(source.getMaterialCode());
        item.setMaterialName(source.getMaterialName());
        item.setStepCode(source.getStepCode());
        item.setRequiredQty(source.getRequiredQty());
        item.setUnit(source.getUnit());
        item.setIsKeyMaterial(source.getIsKeyMaterial() == null ? 1 : source.getIsKeyMaterial());
        item.setSubstituteGroup(valueOr(source.getSubstituteGroup(), source.getMaterialCode()));
        item.setSubstitutePriority(source.getSubstitutePriority() == null ? 1 : source.getSubstitutePriority());
        item.setSubstituteEnabled(source.getSubstituteEnabled() == null ? 1 : source.getSubstituteEnabled());
        item.setCreatedTime(LocalDateTime.now());
        return item;
    }

    private BomChangeRequest lockedBomChange(String changeNo) {
        if (changeNo == null || changeNo.isBlank()) {
            throw new BusinessException("BOM变更单号不能为空");
        }
        BomChangeRequest change = bomChangeRequestMapper.selectOne(new LambdaQueryWrapper<BomChangeRequest>()
                .eq(BomChangeRequest::getChangeNo, changeNo)
                .last("LIMIT 1"));
        if (change == null) {
            throw new BusinessException("BOM变更单不存在: " + changeNo);
        }
        return change;
    }

    private BomEcoApprovalTask lockedBomEcoApprovalTask(String taskNo) {
        if (taskNo == null || taskNo.isBlank()) {
            throw new BusinessException("BOM/ECO会签任务号不能为空");
        }
        BomEcoApprovalTask task = bomEcoApprovalTaskMapper.selectOne(new LambdaQueryWrapper<BomEcoApprovalTask>()
                .eq(BomEcoApprovalTask::getTaskNo, taskNo)
                .last("LIMIT 1"));
        if (task == null) {
            throw new BusinessException("BOM/ECO会签任务不存在: " + taskNo);
        }
        return task;
    }

    private List<BomEcoApprovalTask> bomEcoTasksByChange(String changeNo) {
        List<BomEcoApprovalTask> tasks = bomEcoApprovalTaskMapper.selectList(new LambdaQueryWrapper<BomEcoApprovalTask>()
                .eq(BomEcoApprovalTask::getChangeNo, changeNo));
        return tasks == null ? List.of() : tasks;
    }

    private List<BomEcoApprovalTask> createBomEcoApprovalTasks(BomChangeRequest change,
                                                               List<String> approvalRoles,
                                                               String operator,
                                                               LocalDateTime now) {
        List<BomEcoApprovalTask> tasks = new ArrayList<>();
        String slaLevel = ecoSlaLevel(change.getEcoRiskLevel());
        int slaHours = ecoSlaHours(change.getEcoRiskLevel());
        for (String role : approvalRoles) {
            BomEcoApprovalTask task = new BomEcoApprovalTask();
            task.setTaskNo(nextNo("BEA"));
            task.setChangeNo(change.getChangeNo());
            task.setEcoNo(change.getEcoNo());
            task.setProductCode(change.getProductCode());
            task.setTargetBomCode(change.getTargetBomCode());
            task.setApprovalRole(role);
            task.setApprovalStatus("PENDING");
            task.setSlaLevel(slaLevel);
            task.setSlaHours(slaHours);
            task.setDueTime(now.plusHours(slaHours));
            task.setCreatedBy(operator);
            task.setCreatedTime(now);
            task.setUpdatedTime(now);
            bomEcoApprovalTaskMapper.insert(task);
            tasks.add(task);
        }
        return tasks;
    }

    private void refreshBomEcoApprovalStatus(String changeNo, String operator, String opinion) {
        BomChangeRequest change = lockedBomChange(changeNo);
        List<BomEcoApprovalTask> tasks = bomEcoTasksByChange(changeNo);
        if (tasks.isEmpty()) {
            change.setEcoApprovalStatus("APPROVED");
            change.setStatus("APPROVED");
            change.setReviewedBy(operator);
            change.setReviewedTime(LocalDateTime.now());
            change.setReviewComment(valueOr(opinion, "无会签任务，自动通过"));
        } else if (tasks.stream().anyMatch(task -> "REJECTED".equals(task.getApprovalStatus()))) {
            change.setEcoApprovalStatus("REJECTED");
            change.setStatus("REJECTED");
            change.setReviewedBy(operator);
            change.setReviewedTime(LocalDateTime.now());
            change.setReviewComment(valueOr(opinion, "ECO会签驳回"));
        } else if (tasks.stream().allMatch(task -> "APPROVED".equals(task.getApprovalStatus()))) {
            change.setEcoApprovalStatus("APPROVED");
            change.setStatus("APPROVED");
            change.setReviewedBy(operator);
            change.setReviewedTime(LocalDateTime.now());
            change.setReviewComment(valueOr(opinion, "ECO跨部门会签全部通过"));
        } else {
            change.setEcoApprovalStatus("PENDING");
            change.setStatus("SUBMITTED");
        }
        change.setUpdatedTime(LocalDateTime.now());
        bomChangeRequestMapper.updateById(change);
    }

    private void rejectPendingBomEcoApprovals(BomChangeRequest change, String operator, String opinion) {
        List<BomEcoApprovalTask> tasks = bomEcoApprovalTaskMapper.selectList(new LambdaQueryWrapper<BomEcoApprovalTask>()
                .eq(BomEcoApprovalTask::getChangeNo, change.getChangeNo())
                .eq(BomEcoApprovalTask::getApprovalStatus, "PENDING"));
        if (tasks == null) {
            tasks = List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        for (BomEcoApprovalTask task : tasks) {
            task.setApprovalStatus("REJECTED");
            task.setDecision("REJECT");
            task.setApprover(operator);
            task.setOpinion(valueOr(opinion, "BOM变更单被驳回，同步关闭会签任务"));
            task.setActionTime(now);
            task.setUpdatedTime(now);
            bomEcoApprovalTaskMapper.updateById(task);
        }
    }

    private void assertBomEcoApprovalsReady(String changeNo) {
        List<BomEcoApprovalTask> tasks = bomEcoTasksByChange(changeNo);
        if (tasks.isEmpty()) {
            return;
        }
        List<String> pendingRoles = tasks.stream()
                .filter(task -> !"APPROVED".equals(task.getApprovalStatus()))
                .map(task -> task.getApprovalRole() + ":" + task.getApprovalStatus())
                .collect(Collectors.toList());
        if (!pendingRoles.isEmpty()) {
            throw new BusinessException("BOM/ECO会签未全部通过: " + String.join(",", pendingRoles));
        }
    }

    private List<MaterialRequirement> keyRequirements(String productCode, String stepCode) {
        Bom bom = activeBom(productCode);
        if (bom == null) {
            throw new BusinessException("未找到生效BOM: product=" + productCode);
        }
        List<BomItem> items = bomItemMapper.selectList(new LambdaQueryWrapper<BomItem>()
                .eq(BomItem::getBomId, bom.getId())
                .eq(BomItem::getStepCode, stepCode)
                .eq(BomItem::getIsKeyMaterial, 1)
                .orderByAsc(BomItem::getSubstituteGroup)
                .orderByAsc(BomItem::getSubstitutePriority)
                .orderByAsc(BomItem::getId));
        Map<String, List<BomItem>> groups = new LinkedHashMap<>();
        for (BomItem item : items) {
            if (Integer.valueOf(0).equals(item.getSubstituteEnabled())) {
                continue;
            }
            String groupKey = valueOr(item.getSubstituteGroup(), item.getMaterialCode());
            groups.computeIfAbsent(groupKey, ignored -> new ArrayList<>()).add(item);
        }
        return groups.entrySet().stream()
                .map(entry -> new MaterialRequirement(entry.getKey(), entry.getValue().stream()
                        .sorted(Comparator.comparing((BomItem item) -> item.getSubstitutePriority() == null ? 1 : item.getSubstitutePriority())
                                .thenComparing(BomItem::getMaterialCode))
                        .collect(Collectors.toList())))
                .collect(Collectors.toList());
    }

    private Bom activeBom(String productCode) {
        return bomMapper.selectOne(new LambdaQueryWrapper<Bom>()
                .eq(Bom::getProductCode, productCode)
                .eq(Bom::getStatus, "ACTIVE")
                .orderByDesc(Bom::getEffectiveTime)
                .orderByDesc(Bom::getId)
                .last("LIMIT 1"));
    }

    private MaterialBatch findAvailableBatch(String materialCode, BigDecimal requiredQty) {
        List<MaterialBatch> batches = batchMapper.selectList(new LambdaQueryWrapper<MaterialBatch>()
                .eq(MaterialBatch::getMaterialCode, materialCode)
                .eq(MaterialBatch::getStatus, "AVAILABLE")
                .eq(MaterialBatch::getQualityStatus, "PASS")
                .ge(MaterialBatch::getAvailableQty, requiredQty)
                .and(wrapper -> wrapper.isNull(MaterialBatch::getExpireTime)
                        .or()
                        .gt(MaterialBatch::getExpireTime, LocalDateTime.now()))
                .orderByAsc(MaterialBatch::getFifoSeq)
                .orderByAsc(MaterialBatch::getReceivedTime)
                .orderByAsc(MaterialBatch::getId)
                .last("LIMIT 1"));
        return batches.isEmpty() ? null : batches.get(0);
    }

    private MaterialBatch findAvailableBatchForUpdate(String materialCode, BigDecimal requiredQty) {
        return batchMapper.selectAvailableBatchForUpdate(materialCode, requiredQty, LocalDateTime.now());
    }

    private MaterialPlan selectMaterialPlan(MaterialRequirement requirement, Lot lot, boolean forUpdate) {
        for (BomItem candidate : requirement.candidates()) {
            BigDecimal requiredQty = requiredQty(candidate, lot);
            if (requiredQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            MaterialBatch batch = forUpdate
                    ? findAvailableBatchForUpdate(candidate.getMaterialCode(), requiredQty)
                    : findAvailableBatch(candidate.getMaterialCode(), requiredQty);
            if (batch != null) {
                return new MaterialPlan(candidate, batch, requiredQty);
            }
        }
        return null;
    }

    private BigDecimal requiredQty(BomItem item, Lot lot) {
        return nvl(item.getRequiredQty()).multiply(BigDecimal.valueOf(lot.getQty() == null ? 0L : lot.getQty()));
    }

    private String candidateRequirementText(MaterialRequirement requirement, Lot lot) {
        return requirement.candidates().stream()
                .map(item -> item.getMaterialCode() + "="
                        + requiredQty(item, lot).stripTrailingZeros().toPlainString()
                        + valueOr(item.getUnit(), ""))
                .collect(Collectors.joining("/"));
    }

    private Map<String, Object> bomRow(Bom bom) {
        List<BomItem> items = bomItemMapper.selectList(new LambdaQueryWrapper<BomItem>().eq(BomItem::getBomId, bom.getId()));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bomCode", bom.getBomCode());
        row.put("bomName", bom.getBomName());
        row.put("productCode", bom.getProductCode());
        row.put("bomVersion", bom.getBomVersion());
        row.put("status", bom.getStatus());
        row.put("effectiveTime", bom.getEffectiveTime());
        row.put("items", items.size());
        row.put("keyItems", items.stream().filter(item -> Integer.valueOf(1).equals(item.getIsKeyMaterial())).count());
        row.put("steps", items.stream().map(BomItem::getStepCode).distinct().collect(Collectors.toList()));
        row.put("substituteGroups", items.stream()
                .map(item -> valueOr(item.getSubstituteGroup(), item.getMaterialCode()))
                .distinct()
                .count());
        return row;
    }

    private Map<String, Object> bomChangeRow(BomChangeRequest change) {
        List<BomChangeAttachment> attachments = bomChangeAttachmentMapper.selectList(
                new LambdaQueryWrapper<BomChangeAttachment>()
                        .eq(BomChangeAttachment::getChangeNo, change.getChangeNo())
                        .orderByDesc(BomChangeAttachment::getUploadedTime)
                        .orderByDesc(BomChangeAttachment::getId));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("changeNo", change.getChangeNo());
        row.put("changeType", change.getChangeType());
        row.put("productCode", change.getProductCode());
        row.put("sourceBomCode", change.getSourceBomCode());
        row.put("targetBomCode", change.getTargetBomCode());
        row.put("targetVersion", change.getTargetVersion());
        row.put("status", change.getStatus());
        row.put("reason", change.getReason());
        row.put("requestedBy", change.getRequestedBy());
        row.put("requestedTime", change.getRequestedTime());
        row.put("reviewedBy", change.getReviewedBy());
        row.put("reviewedTime", change.getReviewedTime());
        row.put("reviewComment", change.getReviewComment());
        row.put("publishedBy", change.getPublishedBy());
        row.put("publishedTime", change.getPublishedTime());
        row.put("substitutePolicySnapshot", change.getSubstitutePolicySnapshot());
        row.put("ecoNo", change.getEcoNo());
        row.put("ecoRiskLevel", change.getEcoRiskLevel());
        row.put("ecoApprovalStatus", valueOr(change.getEcoApprovalStatus(), "PENDING"));
        row.put("ecoRequiredRoles", valueOr(change.getEcoRequiredRoles(), ""));
        row.put("ecoPackageSnapshot", change.getEcoPackageSnapshot());
        row.put("attachmentCount", attachments == null ? 0 : attachments.size());
        row.put("attachments", attachments == null
                ? List.of()
                : attachments.stream().map(this::bomChangeAttachmentRow).collect(Collectors.toList()));
        List<BomEcoApprovalTask> approvalTasks = bomEcoApprovalTaskMapper.selectList(
                new LambdaQueryWrapper<BomEcoApprovalTask>()
                        .eq(BomEcoApprovalTask::getChangeNo, change.getChangeNo())
                        .orderByAsc(BomEcoApprovalTask::getDueTime)
                        .orderByAsc(BomEcoApprovalTask::getId));
        if (approvalTasks == null) {
            approvalTasks = List.of();
        }
        row.put("ecoApprovalTaskCount", approvalTasks == null ? 0 : approvalTasks.size());
        row.put("ecoApprovalTasks", approvalTasks == null
                ? List.of()
                : approvalTasks.stream().map(this::bomEcoApprovalTaskRow).collect(Collectors.toList()));
        row.put("type", statusType(change.getStatus()));
        return row;
    }

    private Map<String, Object> bomEcoApprovalTaskRow(BomEcoApprovalTask task) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("taskNo", task.getTaskNo());
        row.put("changeNo", task.getChangeNo());
        row.put("ecoNo", task.getEcoNo());
        row.put("productCode", task.getProductCode());
        row.put("targetBomCode", task.getTargetBomCode());
        row.put("approvalRole", task.getApprovalRole());
        row.put("approver", task.getApprover());
        row.put("approvalStatus", task.getApprovalStatus());
        row.put("decision", task.getDecision());
        row.put("opinion", task.getOpinion());
        row.put("slaLevel", task.getSlaLevel());
        row.put("slaHours", task.getSlaHours());
        row.put("dueTime", task.getDueTime());
        row.put("actionTime", task.getActionTime());
        row.put("createdBy", task.getCreatedBy());
        row.put("createdTime", task.getCreatedTime());
        row.put("type", statusType(task.getApprovalStatus()));
        return row;
    }

    private Map<String, Object> bomChangeAttachmentRow(BomChangeAttachment attachment) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("attachmentNo", attachment.getAttachmentNo());
        row.put("changeNo", attachment.getChangeNo());
        row.put("productCode", attachment.getProductCode());
        row.put("targetBomCode", attachment.getTargetBomCode());
        row.put("fileName", attachment.getFileName());
        row.put("fileUrl", attachment.getFileUrl());
        row.put("fileHash", attachment.getFileHash());
        row.put("fileType", attachment.getFileType());
        row.put("attachmentRole", attachment.getAttachmentRole());
        row.put("uploadedBy", attachment.getUploadedBy());
        row.put("uploadedTime", attachment.getUploadedTime());
        return row;
    }

    private Map<String, Object> targetBomSnapshot(Bom bom, List<BomItem> items) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bomCode", bom.getBomCode());
        row.put("productCode", bom.getProductCode());
        row.put("bomVersion", bom.getBomVersion());
        row.put("status", bom.getStatus());
        row.put("items", items.stream().map(this::bomItemSnapshot).collect(Collectors.toList()));
        return row;
    }

    private Map<String, Object> bomItemSnapshot(BomItem item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("materialCode", item.getMaterialCode());
        row.put("materialName", item.getMaterialName());
        row.put("stepCode", item.getStepCode());
        row.put("requiredQty", item.getRequiredQty());
        row.put("unit", item.getUnit());
        row.put("isKeyMaterial", item.getIsKeyMaterial());
        row.put("substituteGroup", item.getSubstituteGroup());
        row.put("substitutePriority", item.getSubstitutePriority());
        row.put("substituteEnabled", item.getSubstituteEnabled());
        return row;
    }

    private String substitutePolicySnapshot(List<BomItem> items) {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (BomItem item : items) {
            if (Integer.valueOf(0).equals(item.getSubstituteEnabled())) {
                continue;
            }
            String group = valueOr(item.getSubstituteGroup(), item.getMaterialCode());
            groups.computeIfAbsent(group, ignored -> new ArrayList<>())
                    .add(item.getMaterialCode() + "#" + (item.getSubstitutePriority() == null ? 1 : item.getSubstitutePriority()));
        }
        return groups.toString();
    }

    private Map<String, Object> supplierPerformanceRow(SupplierScore supplier) {
        double inspectionBase = supplier.inspectionCount <= 0 ? 0.0 : supplier.inspectionCount;
        double batchBase = supplier.batchCount <= 0 ? 0.0 : supplier.batchCount;
        double passRate = inspectionBase > 0
                ? supplier.passCount * 100.0 / inspectionBase
                : batchBase > 0 ? (supplier.batchCount - supplier.riskBatchCount) * 100.0 / batchBase : 0.0;
        double ngRate = inspectionBase > 0 ? supplier.ngCount / inspectionBase : 0.0;
        double holdRate = inspectionBase > 0 ? supplier.holdCount / inspectionBase : 0.0;
        double riskBatchRate = batchBase > 0 ? supplier.riskBatchCount / batchBase : 0.0;
        double score = clampScore(100.0 - ngRate * 45.0 - holdRate * 25.0 - riskBatchRate * 20.0);
        String riskLevel = supplierRiskLevel(score, ngRate, riskBatchRate, supplier.holdCount);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("supplierCode", supplier.supplierCode);
        row.put("batchCount", supplier.batchCount);
        row.put("availableBatchCount", supplier.availableBatchCount);
        row.put("availableQty", supplier.availableQty);
        row.put("materialCount", supplier.materialCodes.size());
        row.put("materialCodes", supplier.materialCodes);
        row.put("inspectionCount", supplier.inspectionCount);
        row.put("passCount", supplier.passCount);
        row.put("holdCount", supplier.holdCount);
        row.put("ngCount", supplier.ngCount);
        row.put("passRate", round1(passRate));
        row.put("passRateText", formatPercent(passRate));
        row.put("riskBatchCount", supplier.riskBatchCount);
        row.put("score", round1(score));
        row.put("scoreText", String.format(Locale.ROOT, "%.1f", round1(score)));
        row.put("riskLevel", riskLevel);
        row.put("lastBatchTime", supplier.lastBatchTime);
        row.put("lastInspectionTime", supplier.lastInspectionTime);
        row.put("type", switch (riskLevel) {
            case "HIGH" -> "red";
            case "MEDIUM" -> "amber";
            default -> "green";
        });
        return row;
    }

    private Map<String, Object> supplierTrendRow(SupplierTrend supplier, YearMonth startPeriod, int months) {
        List<Map<String, Object>> points = new ArrayList<>();
        Map<String, Object> latestPoint = null;
        Map<String, Object> latestActivePoint = null;
        for (int index = 0; index < months; index++) {
            YearMonth period = startPeriod.plusMonths(index);
            Map<String, Object> point = supplierTrendPointRow(period, supplier.points.getOrDefault(period, new SupplierTrendPoint()));
            points.add(point);
            latestPoint = point;
            if (((Number) point.get("activityCount")).intValue() > 0) {
                latestActivePoint = point;
            }
        }
        Map<String, Object> latest = latestActivePoint == null ? latestPoint : latestActivePoint;
        if (latest == null) {
            latest = supplierTrendPointRow(startPeriod, new SupplierTrendPoint());
        }
        long actionWindowCount = points.stream()
                .mapToLong(point -> ((Number) point.get("actionCount")).longValue())
                .sum();
        long overdueWindowCount = points.stream()
                .mapToLong(point -> ((Number) point.get("overdueActionCount")).longValue())
                .sum();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("supplierCode", supplier.supplierCode);
        row.put("supplierName", supplier.supplierName);
        row.put("materialClass", supplier.materialClass);
        row.put("periodStart", startPeriod.toString());
        row.put("periodEnd", startPeriod.plusMonths(months - 1L).toString());
        row.put("latestPeriod", latest.get("period"));
        row.put("latestScore", latest.get("score"));
        row.put("latestScoreText", latest.get("scoreText"));
        row.put("latestPassRate", latest.get("passRate"));
        row.put("latestPassRateText", latest.get("passRateText"));
        row.put("latestRiskLevel", latest.get("riskLevel"));
        row.put("actionWindowCount", actionWindowCount);
        row.put("overdueWindowCount", overdueWindowCount);
        row.put("trend", points);
        row.put("summary", supplierTrendSummary(supplier.supplierCode, latest, actionWindowCount, overdueWindowCount));
        row.put("type", latest.get("type"));
        return row;
    }

    private Map<String, Object> supplierTrendPointRow(YearMonth period, SupplierTrendPoint point) {
        double inspectionBase = point.inspectionCount <= 0 ? 0.0 : point.inspectionCount;
        double batchBase = point.batchCount <= 0 ? 0.0 : point.batchCount;
        double passRate = inspectionBase > 0
                ? point.passCount * 100.0 / inspectionBase
                : batchBase > 0 ? (point.batchCount - point.riskBatchCount) * 100.0 / batchBase : 0.0;
        double ngRate = inspectionBase > 0 ? point.ngCount / inspectionBase : 0.0;
        double holdRate = inspectionBase > 0 ? point.holdCount / inspectionBase : 0.0;
        double riskBatchRate = batchBase > 0 ? point.riskBatchCount / batchBase : 0.0;
        double actionPenalty = point.openActionCount * 8.0 + point.overdueActionCount * 12.0;
        double score = clampScore(100.0 - ngRate * 45.0 - holdRate * 25.0 - riskBatchRate * 20.0 - actionPenalty);
        String actionRisk = point.overdueActionCount > 0 || point.highOpenActionCount > 0
                ? "HIGH"
                : point.openActionCount > 0 ? "MEDIUM" : "LOW";
        String riskLevel = worseRisk(supplierRiskLevel(score, ngRate, riskBatchRate, point.holdCount), actionRisk);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("period", period.toString());
        row.put("batchCount", point.batchCount);
        row.put("riskBatchCount", point.riskBatchCount);
        row.put("inspectionCount", point.inspectionCount);
        row.put("passCount", point.passCount);
        row.put("holdCount", point.holdCount);
        row.put("ngCount", point.ngCount);
        row.put("actionCount", point.actionCount);
        row.put("openActionCount", point.openActionCount);
        row.put("overdueActionCount", point.overdueActionCount);
        row.put("activityCount", point.batchCount + point.inspectionCount + point.actionCount);
        row.put("materialCount", point.materialCodes.size());
        row.put("materialCodes", point.materialCodes);
        row.put("passRate", round1(passRate));
        row.put("passRateText", formatPercent(passRate));
        row.put("score", round1(score));
        row.put("scoreText", String.format(Locale.ROOT, "%.1f", round1(score)));
        row.put("riskLevel", riskLevel);
        row.put("type", switch (riskLevel) {
            case "HIGH" -> "red";
            case "MEDIUM" -> "amber";
            default -> "green";
        });
        return row;
    }

    private String supplierTrendSummary(String supplierCode,
                                        Map<String, Object> latest,
                                        long actionWindowCount,
                                        long overdueWindowCount) {
        String riskLevel = String.valueOf(latest.getOrDefault("riskLevel", "LOW"));
        String scoreText = String.valueOf(latest.getOrDefault("scoreText", "0.0"));
        if (overdueWindowCount > 0) {
            return supplierCode + " 近周期存在超期8D，最新评分 " + scoreText + "，需供应商质量复审。";
        }
        if ("HIGH".equals(riskLevel)) {
            return supplierCode + " 最新评分 " + scoreText + "，批次/IQC/8D 风险处于高位。";
        }
        if (actionWindowCount > 0 || "MEDIUM".equals(riskLevel)) {
            return supplierCode + " 最新评分 " + scoreText + "，建议维持条件准入和月度趋势复核。";
        }
        return supplierCode + " 最新评分 " + scoreText + "，近周期供应商表现稳定。";
    }

    private Map<String, Object> supplierRow(Supplier supplier,
                                            Map<String, Object> performance,
                                            List<SupplierCorrectiveAction> actions) {
        Map<String, Object> sourcePerformance = performance == null ? Map.of() : performance;
        List<SupplierCorrectiveAction> sourceActions = actions == null ? List.of() : actions;
        String supplierCode = supplier != null
                ? supplierKey(supplier.getSupplierCode())
                : supplierKey(String.valueOf(sourcePerformance.get("supplierCode")));
        double performanceScore = rowDouble(sourcePerformance, "score", -1.0);
        double storedScore = supplier == null || supplier.getScore() == null ? -1.0 : supplier.getScore().doubleValue();
        double score = performanceScore >= 0 ? performanceScore : Math.max(storedScore, 0.0);
        double performancePassRate = rowDouble(sourcePerformance, "passRate", -1.0);
        double storedPassRate = supplier == null || supplier.getPassRate() == null ? -1.0 : supplier.getPassRate().doubleValue();
        double passRate = performancePassRate >= 0 ? performancePassRate : Math.max(storedPassRate, 0.0);
        String performanceRisk = String.valueOf(sourcePerformance.getOrDefault("riskLevel", ""));
        String supplierRisk = supplier == null ? "" : supplier.getRiskLevel();
        String riskLevel = worseRisk(valueOr(performanceRisk, "LOW"), valueOr(supplierRisk, "LOW"));
        long openActionCount = sourceActions.stream()
                .filter(action -> !"CLOSED".equals(valueOr(action.getStatus(), "").toUpperCase(Locale.ROOT)))
                .count();
        long overdueActionCount = sourceActions.stream()
                .filter(action -> !"CLOSED".equals(valueOr(action.getStatus(), "").toUpperCase(Locale.ROOT)))
                .filter(action -> action.getDueTime() != null && action.getDueTime().isBefore(LocalDateTime.now()))
                .count();
        SupplierCorrectiveAction latestAction = sourceActions.stream()
                .max(Comparator.comparing(action -> action.getCreatedTime() == null ? LocalDateTime.MIN : action.getCreatedTime()))
                .orElse(null);
        String qualificationStatus = supplier == null
                ? (openActionCount > 0 || "HIGH".equals(riskLevel) ? "CONDITIONAL" : "PENDING")
                : valueOr(supplier.getQualificationStatus(), "PENDING");

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("supplierCode", supplierCode);
        row.put("supplierName", supplier == null ? supplierCode : supplier.getSupplierName());
        row.put("supplierType", supplier == null ? "MATERIAL" : valueOr(supplier.getSupplierType(), "MATERIAL"));
        row.put("materialClass", supplier == null ? "GENERAL" : valueOr(supplier.getMaterialClass(), "GENERAL"));
        row.put("qualificationStatus", qualificationStatus);
        row.put("riskLevel", riskLevel);
        row.put("score", round1(score));
        row.put("scoreText", String.format(Locale.ROOT, "%.1f", round1(score)));
        row.put("passRate", round1(passRate));
        row.put("passRateText", formatPercent(passRate));
        row.put("owner", supplier == null ? "" : valueOr(supplier.getOwner(), ""));
        row.put("status", supplier == null ? "ACTIVE" : valueOr(supplier.getStatus(), "ACTIVE"));
        row.put("lastAuditTime", supplier == null ? null : supplier.getLastAuditTime());
        row.put("nextAuditDue", supplier == null ? null : supplier.getNextAuditDue());
        row.put("remark", supplier == null ? "" : valueOr(supplier.getRemark(), ""));
        row.put("batchCount", rowInt(sourcePerformance, "batchCount", 0));
        row.put("availableBatchCount", rowInt(sourcePerformance, "availableBatchCount", 0));
        row.put("inspectionCount", rowInt(sourcePerformance, "inspectionCount", 0));
        row.put("passCount", rowInt(sourcePerformance, "passCount", 0));
        row.put("holdCount", rowInt(sourcePerformance, "holdCount", 0));
        row.put("ngCount", rowInt(sourcePerformance, "ngCount", 0));
        row.put("riskBatchCount", rowInt(sourcePerformance, "riskBatchCount", 0));
        row.put("materialCodes", sourcePerformance.getOrDefault("materialCodes", List.of()));
        row.put("openActionCount", openActionCount);
        row.put("overdueActionCount", overdueActionCount);
        row.put("latestActionNo", latestAction == null ? "" : latestAction.getActionNo());
        row.put("latestActionStatus", latestAction == null ? "" : latestAction.getStatus());
        row.put("type", qualificationType(qualificationStatus, riskLevel, openActionCount, overdueActionCount));
        return row;
    }

    private Map<String, Object> supplierCorrectiveActionRow(SupplierCorrectiveAction action) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("actionNo", action.getActionNo());
        row.put("supplierCode", action.getSupplierCode());
        row.put("sourceType", action.getSourceType());
        row.put("sourceNo", action.getSourceNo());
        row.put("issueSummary", action.getIssueSummary());
        row.put("rootCause", action.getRootCause());
        row.put("containmentAction", action.getContainmentAction());
        row.put("correctiveAction", action.getCorrectiveAction());
        row.put("preventiveAction", action.getPreventiveAction());
        row.put("owner", action.getOwner());
        row.put("severity", action.getSeverity());
        row.put("status", action.getStatus());
        row.put("dueTime", action.getDueTime());
        row.put("closedTime", action.getClosedTime());
        row.put("verificationResult", action.getVerificationResult());
        row.put("createdTime", action.getCreatedTime());
        row.put("updatedTime", action.getUpdatedTime());
        row.put("overdue", action.getDueTime() != null
                && !"CLOSED".equals(valueOr(action.getStatus(), "").toUpperCase(Locale.ROOT))
                && action.getDueTime().isBefore(LocalDateTime.now()));
        row.put("type", supplierActionType(action));
        return row;
    }

    private Map<String, Object> supplierQualificationReviewTaskRow(SupplierQualificationReviewTask task) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("taskNo", task.getTaskNo());
        row.put("supplierCode", task.getSupplierCode());
        row.put("reviewType", task.getReviewType());
        row.put("sourceNo", task.getSourceNo());
        row.put("triggerReason", task.getTriggerReason());
        row.put("qualificationBefore", task.getQualificationBefore());
        row.put("riskBefore", task.getRiskBefore());
        row.put("suggestedQualification", task.getSuggestedQualification());
        row.put("suggestedRisk", task.getSuggestedRisk());
        row.put("reviewStatus", task.getReviewStatus());
        row.put("dueTime", task.getDueTime());
        row.put("reviewer", task.getReviewer());
        row.put("reviewTime", task.getReviewTime());
        row.put("decision", task.getDecision());
        row.put("decisionComment", task.getDecisionComment());
        row.put("createdBy", task.getCreatedBy());
        row.put("createdTime", task.getCreatedTime());
        row.put("updatedTime", task.getUpdatedTime());
        row.put("overdue", task.getDueTime() != null
                && "OPEN".equals(valueOr(task.getReviewStatus(), "").toUpperCase(Locale.ROOT))
                && task.getDueTime().isBefore(LocalDateTime.now()));
        row.put("canDecide", "OPEN".equals(valueOr(task.getReviewStatus(), "").toUpperCase(Locale.ROOT)));
        row.put("type", supplierReviewTaskType(task));
        return row;
    }

    private Supplier findSupplier(String supplierCode) {
        String key = supplierKey(supplierCode);
        Supplier supplier = supplierMapper.selectOne(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getSupplierCode, key)
                .last("LIMIT 1"));
        if (supplier == null) {
            throw new BusinessException("供应商主数据不存在: " + key);
        }
        return supplier;
    }

    private QualificationDecision qualificationDecision(Map<String, Object> performance,
                                                        List<SupplierCorrectiveAction> actions) {
        Map<String, Object> sourcePerformance = performance == null ? Map.of() : performance;
        List<SupplierCorrectiveAction> sourceActions = actions == null ? List.of() : actions;
        double score = rowDouble(sourcePerformance, "score", 0.0);
        double passRate = rowDouble(sourcePerformance, "passRate", 0.0);
        int batchCount = rowInt(sourcePerformance, "batchCount", 0);
        int inspectionCount = rowInt(sourcePerformance, "inspectionCount", 0);
        int ngCount = rowInt(sourcePerformance, "ngCount", 0);
        int holdCount = rowInt(sourcePerformance, "holdCount", 0);
        int riskBatchCount = rowInt(sourcePerformance, "riskBatchCount", 0);
        String riskLevel = valueOr(String.valueOf(sourcePerformance.get("riskLevel")), "MEDIUM").toUpperCase(Locale.ROOT);
        long openActions = sourceActions.stream()
                .filter(action -> !"CLOSED".equals(valueOr(action.getStatus(), "").toUpperCase(Locale.ROOT)))
                .count();
        boolean hasHighOpenAction = sourceActions.stream()
                .filter(action -> !"CLOSED".equals(valueOr(action.getStatus(), "").toUpperCase(Locale.ROOT)))
                .anyMatch(action -> List.of("HIGH", "CRITICAL").contains(valueOr(action.getSeverity(), "").toUpperCase(Locale.ROOT)));

        if (batchCount == 0 && inspectionCount == 0) {
            return new QualificationDecision("PENDING", "MEDIUM", score, passRate, 30,
                    "供应商暂无足够批次与来料质检样本，保持待准入。");
        }
        if (score < 60.0 || openActions >= 3 || ("HIGH".equals(riskLevel) && hasHighOpenAction)) {
            return new QualificationDecision("BLOCKED", "HIGH", score, passRate, 7,
                    "供应商评分或未关闭8D风险过高，准入阻断并要求MRB/供应商质量复核。");
        }
        if (score < 90.0 || !"LOW".equals(riskLevel) || openActions > 0 || ngCount > 0 || holdCount > 0 || riskBatchCount > 0) {
            return new QualificationDecision("CONDITIONAL", worseRisk(riskLevel, hasHighOpenAction ? "HIGH" : "MEDIUM"),
                    score, passRate, 30, "供应商存在批次、质检或8D风险，维持条件准入并缩短复核周期。");
        }
        return new QualificationDecision("QUALIFIED", "LOW", score, passRate, 90,
                "供应商批次、质检与8D状态满足试点准入要求。");
    }

    private SupplierQualificationReviewTask createSupplierQualificationReviewTaskInternal(Supplier supplier,
                                                                                          Map<String, Object> request,
                                                                                          boolean recordAudit) {
        String key = supplierKey(supplier.getSupplierCode());
        String reviewType = normalizeReviewType(text(request, "reviewType", "PERIODIC"));
        Long openExists = supplierQualificationReviewTaskMapper.selectCount(
                new LambdaQueryWrapper<SupplierQualificationReviewTask>()
                        .eq(SupplierQualificationReviewTask::getSupplierCode, key)
                        .eq(SupplierQualificationReviewTask::getReviewType, reviewType)
                        .eq(SupplierQualificationReviewTask::getReviewStatus, "OPEN"));
        if (openExists != null && openExists > 0) {
            throw new BusinessException("供应商已存在未关闭准入复审任务: " + key);
        }

        Map<String, Object> performance = supplierPerformance().stream()
                .filter(row -> key.equals(supplierKey(String.valueOf(row.get("supplierCode")))))
                .findFirst()
                .orElse(Map.of("supplierCode", key, "score", 0.0, "passRate", 0.0,
                        "riskLevel", "MEDIUM", "riskBatchCount", 0, "ngCount", 0, "holdCount", 0));
        List<SupplierCorrectiveAction> actions = supplierCorrectiveActionMapper.selectList(
                new LambdaQueryWrapper<SupplierCorrectiveAction>()
                        .eq(SupplierCorrectiveAction::getSupplierCode, key)
                        .orderByDesc(SupplierCorrectiveAction::getCreatedTime)
                        .last("LIMIT 100"));
        QualificationDecision decision = qualificationDecision(performance, actions);
        LocalDateTime now = LocalDateTime.now();

        SupplierQualificationReviewTask task = new SupplierQualificationReviewTask();
        task.setTaskNo(nextNo("SQR"));
        task.setSupplierCode(key);
        task.setReviewType(reviewType);
        task.setSourceNo(text(request, "sourceNo", String.valueOf(performance.getOrDefault("latestActionNo", ""))));
        task.setTriggerReason(text(request, "triggerReason", decision.reason()));
        task.setQualificationBefore(valueOr(supplier.getQualificationStatus(), "PENDING"));
        task.setRiskBefore(valueOr(supplier.getRiskLevel(), "MEDIUM"));
        task.setSuggestedQualification(decision.status());
        task.setSuggestedRisk(decision.riskLevel());
        task.setReviewStatus("OPEN");
        task.setDueTime(supplierReviewDueTime(request, decision.riskLevel()));
        task.setPerformanceSnapshot(performance.toString());
        task.setRequestSnapshot(request == null ? "{}" : request.toString());
        task.setCreatedBy(text(request, "operator", AuthContext.username()));
        task.setCreatedTime(now);
        task.setUpdatedTime(now);
        supplierQualificationReviewTaskMapper.insert(task);
        if (recordAudit) {
            audit("SUPPLIER_QUALIFICATION_REVIEW_CREATE", task.getTaskNo(), "SUPPLIER_REVIEW",
                    "创建供应商准入复审 supplier=" + key + ", suggested=" + decision.status(),
                    task.getCreatedBy());
        }
        return task;
    }

    private SupplierCorrectiveAction createSupplierCorrectiveActionInternal(String supplierCode,
                                                                            String sourceType,
                                                                            String sourceNo,
                                                                            String issueSummary,
                                                                            String severity,
                                                                            String owner,
                                                                            Map<String, Object> request,
                                                                            boolean recordAudit) {
        String key = supplierKey(supplierCode);
        String normalizedSeverity = normalizeSupplierActionSeverity(severity);
        LocalDateTime now = LocalDateTime.now();
        SupplierCorrectiveAction action = new SupplierCorrectiveAction();
        action.setActionNo(nextNo("SCA"));
        action.setSupplierCode(key);
        action.setSourceType(valueOr(sourceType, "MANUAL").toUpperCase(Locale.ROOT));
        action.setSourceNo(valueOr(sourceNo, ""));
        action.setIssueSummary(valueOr(issueSummary, "供应商质量/交付异常待补充"));
        action.setRootCause(text(request, "rootCause", ""));
        action.setContainmentAction(text(request, "containmentAction", text(request, "containment", "")));
        action.setCorrectiveAction(text(request, "correctiveAction", ""));
        action.setPreventiveAction(text(request, "preventiveAction", ""));
        action.setOwner(valueOr(owner, text(request, "operator", AuthContext.username())));
        action.setSeverity(normalizedSeverity);
        action.setStatus("OPEN");
        action.setDueTime(supplierActionDueTime(request, normalizedSeverity));
        action.setRequestSnapshot(request == null ? "{}" : request.toString());
        action.setCreatedTime(now);
        action.setUpdatedTime(now);
        supplierCorrectiveActionMapper.insert(action);
        if (recordAudit) {
            audit("SUPPLIER_8D_CREATE", action.getActionNo(), "SUPPLIER_8D",
                    "创建供应商8D整改 supplier=" + key + ", severity=" + normalizedSeverity,
                    text(request, "operator", AuthContext.username()));
        }
        return action;
    }

    private void downgradeSupplierForAction(String supplierCode, String severity, String operator) {
        String key = supplierKey(supplierCode);
        Supplier supplier = supplierMapper.selectOne(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getSupplierCode, key)
                .last("LIMIT 1"));
        if (supplier == null) {
            return;
        }
        String normalizedSeverity = normalizeSupplierActionSeverity(severity);
        String currentStatus = valueOr(supplier.getQualificationStatus(), "PENDING").toUpperCase(Locale.ROOT);
        String nextStatus = switch (normalizedSeverity) {
            case "CRITICAL" -> "BLOCKED";
            case "HIGH", "MEDIUM" -> "BLOCKED".equals(currentStatus) ? "BLOCKED" : "CONDITIONAL";
            default -> currentStatus;
        };
        supplier.setQualificationStatus(nextStatus);
        supplier.setRiskLevel(worseRisk(valueOr(supplier.getRiskLevel(), "MEDIUM"), supplierActionRisk(normalizedSeverity)));
        supplier.setNextAuditDue(LocalDateTime.now().plusDays("BLOCKED".equals(nextStatus) ? 7 : 30));
        supplier.setRemark("供应商8D整改触发准入复核，severity=" + normalizedSeverity);
        supplier.setUpdatedTime(LocalDateTime.now());
        supplierMapper.updateById(supplier);
        audit("SUPPLIER_QUALIFICATION_DOWNGRADE", key, "SUPPLIER",
                "供应商因8D整改降级 status=" + nextStatus + ", severity=" + normalizedSeverity, operator);
    }

    private LocalDateTime supplierActionDueTime(Map<String, Object> request, String severity) {
        Object rawDueTime = value(request, "dueTime");
        if (rawDueTime instanceof LocalDateTime time) {
            return time;
        }
        if (rawDueTime != null && !String.valueOf(rawDueTime).isBlank()) {
            try {
                return LocalDateTime.parse(String.valueOf(rawDueTime));
            } catch (Exception ignored) {
                // 日期格式不合法时使用标准SLA，避免创建8D被非关键字段阻断。
            }
        }
        int defaultDays = switch (normalizeSupplierActionSeverity(severity)) {
            case "CRITICAL" -> 3;
            case "HIGH" -> 5;
            case "LOW" -> 14;
            default -> 7;
        };
        return LocalDateTime.now().plusDays(Math.max(1, intValue(value(request, "dueDays"), defaultDays)));
    }

    private LocalDateTime supplierReviewDueTime(Map<String, Object> request, String riskLevel) {
        Object rawDueTime = value(request, "dueTime");
        if (rawDueTime instanceof LocalDateTime time) {
            return time;
        }
        if (rawDueTime != null && !String.valueOf(rawDueTime).isBlank()) {
            try {
                return LocalDateTime.parse(String.valueOf(rawDueTime));
            } catch (Exception ignored) {
                // 日期格式不合法时使用标准SLA，避免复审任务被非关键字段阻断。
            }
        }
        int defaultDays = switch (normalizeRisk(riskLevel)) {
            case "HIGH" -> 7;
            case "MEDIUM" -> 14;
            default -> 30;
        };
        return LocalDateTime.now().plusDays(Math.max(1, intValue(value(request, "dueDays"), defaultDays)));
    }

    private String normalizeReviewType(String reviewType) {
        return switch (valueOr(reviewType, "PERIODIC").trim().toUpperCase(Locale.ROOT)) {
            case "RISK", "RISK_TRIGGERED" -> "RISK";
            case "MANUAL" -> "MANUAL";
            default -> "PERIODIC";
        };
    }

    private String normalizeReviewStatus(String status) {
        return switch (valueOr(status, "OPEN").trim().toUpperCase(Locale.ROOT)) {
            case "APPROVE", "APPROVED", "PASS" -> "APPROVED";
            case "REJECT", "REJECTED", "FAIL" -> "REJECTED";
            default -> "OPEN";
        };
    }

    private String normalizeReviewDecision(String decision) {
        String normalized = valueOr(decision, "APPROVE").trim().toUpperCase(Locale.ROOT);
        if (List.of("APPROVE", "APPROVED", "PASS").contains(normalized)) {
            return "APPROVE";
        }
        if (List.of("REJECT", "REJECTED", "FAIL").contains(normalized)) {
            return "REJECT";
        }
        throw new BusinessException("复审决策仅支持 APPROVE/REJECT");
    }

    private String normalizeQualificationStatus(String status) {
        return switch (valueOr(status, "PENDING").trim().toUpperCase(Locale.ROOT)) {
            case "QUALIFIED", "PASS" -> "QUALIFIED";
            case "CONDITIONAL", "LIMITED" -> "CONDITIONAL";
            case "BLOCKED", "DISQUALIFIED" -> "BLOCKED";
            default -> "PENDING";
        };
    }

    private int nextAuditDays(String qualificationStatus, String riskLevel) {
        String status = normalizeQualificationStatus(qualificationStatus);
        if ("BLOCKED".equals(status) || "HIGH".equals(normalizeRisk(riskLevel))) {
            return 7;
        }
        if ("CONDITIONAL".equals(status)) {
            return 30;
        }
        if ("QUALIFIED".equals(status)) {
            return 90;
        }
        return 30;
    }

    private int qualificationRank(String status) {
        return switch (valueOr(status, "PENDING").toUpperCase(Locale.ROOT)) {
            case "BLOCKED" -> 0;
            case "CONDITIONAL" -> 1;
            case "PENDING" -> 2;
            case "QUALIFIED" -> 3;
            default -> 4;
        };
    }

    private String qualificationType(String status, String riskLevel, long openActionCount, long overdueActionCount) {
        if (overdueActionCount > 0 || "BLOCKED".equals(valueOr(status, "").toUpperCase(Locale.ROOT))) {
            return "red";
        }
        if (openActionCount > 0
                || "CONDITIONAL".equals(valueOr(status, "").toUpperCase(Locale.ROOT))
                || "HIGH".equals(valueOr(riskLevel, "").toUpperCase(Locale.ROOT))
                || "MEDIUM".equals(valueOr(riskLevel, "").toUpperCase(Locale.ROOT))) {
            return "amber";
        }
        if ("QUALIFIED".equals(valueOr(status, "").toUpperCase(Locale.ROOT))) {
            return "green";
        }
        return "blue";
    }

    private String supplierActionType(SupplierCorrectiveAction action) {
        String status = valueOr(action.getStatus(), "").toUpperCase(Locale.ROOT);
        if ("CLOSED".equals(status)) {
            return "green";
        }
        if (action.getDueTime() != null && action.getDueTime().isBefore(LocalDateTime.now())) {
            return "red";
        }
        return switch (normalizeSupplierActionSeverity(action.getSeverity())) {
            case "CRITICAL", "HIGH" -> "red";
            case "MEDIUM" -> "amber";
            default -> "blue";
        };
    }

    private String supplierReviewTaskType(SupplierQualificationReviewTask task) {
        String status = valueOr(task.getReviewStatus(), "").toUpperCase(Locale.ROOT);
        if ("APPROVED".equals(status)) {
            return "green";
        }
        if ("REJECTED".equals(status)) {
            return "red";
        }
        if (task.getDueTime() != null && task.getDueTime().isBefore(LocalDateTime.now())) {
            return "red";
        }
        return switch (normalizeRisk(task.getSuggestedRisk())) {
            case "HIGH" -> "red";
            case "MEDIUM" -> "amber";
            default -> "blue";
        };
    }

    private String normalizeSupplierActionSeverity(String severity) {
        return switch (valueOr(severity, "MEDIUM").trim().toUpperCase(Locale.ROOT)) {
            case "P0", "CRITICAL", "BLOCKER" -> "CRITICAL";
            case "P1", "HIGH" -> "HIGH";
            case "P3", "LOW" -> "LOW";
            default -> "MEDIUM";
        };
    }

    private String supplierActionRisk(String severity) {
        return switch (normalizeSupplierActionSeverity(severity)) {
            case "CRITICAL", "HIGH" -> "HIGH";
            case "LOW" -> "LOW";
            default -> "MEDIUM";
        };
    }

    private String worseRisk(String left, String right) {
        return riskRank(left) <= riskRank(right) ? normalizeRisk(left) : normalizeRisk(right);
    }

    private int riskRank(String riskLevel) {
        return switch (normalizeRisk(riskLevel)) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }

    private String normalizeRisk(String riskLevel) {
        return switch (valueOr(riskLevel, "LOW").trim().toUpperCase(Locale.ROOT)) {
            case "HIGH", "P1", "CRITICAL" -> "HIGH";
            case "MEDIUM", "P2" -> "MEDIUM";
            default -> "LOW";
        };
    }

    private double rowDouble(Map<String, Object> row, String key, double defaultValue) {
        if (row == null || row.get(key) == null) {
            return defaultValue;
        }
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private int rowInt(Map<String, Object> row, String key, int defaultValue) {
        if (row == null || row.get(key) == null) {
            return defaultValue;
        }
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private SupplierTrend supplierTrend(Map<String, SupplierTrend> trends, String supplierCode) {
        return trends.computeIfAbsent(supplierCode, SupplierTrend::new);
    }

    private boolean inPeriodWindow(YearMonth period, YearMonth startPeriod, YearMonth endPeriod) {
        return period != null && !period.isBefore(startPeriod) && !period.isAfter(endPeriod);
    }

    private YearMonth periodOf(LocalDateTime time) {
        return time == null ? null : YearMonth.from(time);
    }

    private boolean riskBatch(MaterialBatch batch) {
        String qualityStatus = valueOr(batch.getQualityStatus(), "").toUpperCase(Locale.ROOT);
        String stockStatus = valueOr(batch.getStatus(), "").toUpperCase(Locale.ROOT);
        return !"PASS".equals(qualityStatus)
                || List.of("HOLD", "BLOCKED", "NG", "LOCKED").contains(stockStatus)
                || (batch.getExpireTime() != null && batch.getExpireTime().isBefore(LocalDateTime.now()));
    }

    private String supplierKey(String supplierCode) {
        String key = valueOr(supplierCode, "").trim();
        return key.isBlank() ? "UNKNOWN" : key.toUpperCase(Locale.ROOT);
    }

    private String incomingResult(String result) {
        return switch (valueOr(result, "HOLD").trim().toUpperCase(Locale.ROOT)) {
            case "OK", "PASS", "ACCEPT" -> "PASS";
            case "NG", "FAIL", "FAILED", "REJECT", "REJECTED" -> "NG";
            default -> "HOLD";
        };
    }

    private String supplierRiskLevel(double score, double ngRate, double riskBatchRate, int holdCount) {
        if (score < 70.0 || ngRate >= 0.2 || riskBatchRate >= 0.3) {
            return "HIGH";
        }
        if (score < 90.0 || holdCount > 0 || riskBatchRate > 0.0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private double clampScore(double score) {
        return Math.max(0.0, Math.min(100.0, score));
    }

    private double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private String formatPercent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", round1(value));
    }

    private Map<String, Object> materialLocationRow(MaterialLocation location) {
        BigDecimal capacity = nvl(location.getCapacityQty());
        BigDecimal used = nvl(location.getUsedQty());
        BigDecimal available = maxZero(capacity.subtract(used));
        double utilization = capacity.compareTo(BigDecimal.ZERO) <= 0
                ? 0.0
                : used.multiply(BigDecimal.valueOf(100))
                .divide(capacity, 1, RoundingMode.HALF_UP)
                .doubleValue();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("locationCode", location.getLocationCode());
        row.put("zoneCode", location.getZoneCode());
        row.put("areaCode", location.getAreaCode());
        row.put("storageType", location.getStorageType());
        row.put("materialClass", location.getMaterialClass());
        row.put("status", location.getStatus());
        row.put("capacityQty", capacity);
        row.put("usedQty", used);
        row.put("availableQty", available);
        row.put("utilizationRate", utilization);
        row.put("utilizationText", String.format(Locale.ROOT, "%.1f%%", utilization));
        row.put("unit", location.getUnit());
        row.put("temperatureWindow", formatWindow(location.getTemperatureMin(), location.getTemperatureMax(), "℃"));
        row.put("humidityWindow", formatWindow(location.getHumidityMin(), location.getHumidityMax(), "%RH"));
        row.put("strategyPriority", location.getStrategyPriority());
        row.put("remark", location.getRemark());
        row.put("type", locationType(location.getStatus(), utilization));
        return row;
    }

    private Map<String, Object> locationTaskRow(MaterialLocationTask task) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("taskNo", task.getTaskNo());
        row.put("taskType", task.getTaskType());
        row.put("batchNo", task.getBatchNo());
        row.put("materialCode", task.getMaterialCode());
        row.put("materialName", task.getMaterialName());
        row.put("sourceLocation", task.getSourceLocation());
        row.put("targetLocation", task.getTargetLocation());
        row.put("plannedQty", nvl(task.getPlannedQty()));
        row.put("actualQty", nvl(task.getActualQty()));
        row.put("unit", task.getUnit());
        row.put("status", task.getStatus());
        row.put("reason", task.getReason());
        row.put("operator", task.getOperator());
        row.put("assignedTo", task.getAssignedTo());
        row.put("assignedTime", task.getAssignedTime());
        row.put("reviewer", task.getReviewer());
        row.put("reviewedTime", task.getReviewedTime());
        row.put("cancelledBy", task.getCancelledBy());
        row.put("cancelledTime", task.getCancelledTime());
        row.put("cancelReason", task.getCancelReason());
        row.put("exceptionReason", task.getExceptionReason());
        row.put("taskSource", valueOr(task.getTaskSource(), "MANUAL"));
        row.put("executedTime", task.getExecutedTime());
        row.put("completedTime", task.getCompletedTime());
        row.put("createdTime", task.getCreatedTime());
        row.put("type", statusType(task.getStatus()));
        return row;
    }

    private Map<String, Object> locationTaskResult(MaterialLocationTask task, MaterialBatch batch) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task", locationTaskRow(task));
        result.put("batch", batchRow(batch));
        return result;
    }

    private MaterialLocationTask createLocationTaskRecord(String taskType,
                                                          MaterialBatch batch,
                                                          String sourceLocation,
                                                          String targetLocation,
                                                          BigDecimal plannedQty,
                                                          BigDecimal actualQty,
                                                          String status,
                                                          String reason,
                                                          String operator,
                                                          Map<String, Object> request) {
        LocalDateTime now = LocalDateTime.now();
        MaterialLocationTask task = new MaterialLocationTask();
        task.setTaskNo(nextNo("MLT"));
        task.setTaskType(taskType);
        task.setBatchNo(batch.getBatchNo());
        task.setMaterialCode(batch.getMaterialCode());
        task.setMaterialName(batch.getMaterialName());
        task.setSourceLocation(sourceLocation);
        task.setTargetLocation(targetLocation);
        task.setPlannedQty(plannedQty);
        task.setActualQty(actualQty);
        task.setUnit(batch.getUnit());
        task.setStatus(status);
        task.setReason(reason);
        task.setOperator(operator);
        task.setTaskSource(text(request, "taskSource", "MANUAL"));
        task.setExecutedTime("DONE".equals(status) ? now : null);
        task.setCompletedTime("DONE".equals(status) ? now : null);
        task.setRequestSnapshot(request == null ? "{}" : request.toString());
        task.setCreatedTime(now);
        task.setUpdatedTime(now);
        materialLocationTaskMapper.insert(task);
        return task;
    }

    private String normalizeLocationTaskType(String value) {
        String taskType = valueOr(value, "MOVE").trim().toUpperCase(Locale.ROOT);
        return switch (taskType) {
            case "MOVE", "移库" -> "MOVE";
            case "PUTAWAY", "上架" -> "PUTAWAY";
            case "COUNT", "盘点" -> "COUNT";
            default -> taskType;
        };
    }

    private String defaultLocationTaskReason(String taskType) {
        return switch (taskType) {
            case "PUTAWAY" -> "WMS putaway";
            case "COUNT" -> "WMS inventory count task";
            default -> "WMS location move";
        };
    }

    private BigDecimal physicalStockQty(MaterialBatch batch) {
        return nvl(batch.getAvailableQty())
                .add(nvl(batch.getReservedQty()))
                .add(nvl(batch.getFrozenQty()));
    }

    private BigDecimal locationMoveQty(Map<String, Object> request, BigDecimal fallbackQty) {
        Object raw = firstPresent(request, "actualQty", "plannedQty", "qty");
        BigDecimal qty = raw == null || String.valueOf(raw).isBlank() ? fallbackQty : decimalValue(raw);
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("库位任务数量必须大于0");
        }
        return qty;
    }

    private BigDecimal locationCountQty(Map<String, Object> request) {
        Object raw = firstPresent(request, "countedAvailableQty", "actualQty", "qty");
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw new BusinessException("盘点任务必须填写实盘可用数量");
        }
        BigDecimal qty = decimalValue(raw);
        if (qty.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("盘点数量不能小于0");
        }
        return qty;
    }

    private BigDecimal optionalLocationCountQty(Map<String, Object> request) {
        Object raw = firstPresent(request, "countedAvailableQty", "actualQty", "qty");
        if (raw == null || String.valueOf(raw).isBlank()) {
            return null;
        }
        BigDecimal qty = decimalValue(raw);
        if (qty.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("盘点数量不能小于0");
        }
        return qty;
    }

    private BigDecimal locationCountQty(Map<String, Object> request, MaterialLocationTask task) {
        Object raw = firstPresent(request, "countedAvailableQty", "actualQty", "qty");
        if (raw == null || String.valueOf(raw).isBlank()) {
            raw = task.getActualQty();
        }
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw new BusinessException("盘点任务必须填写实盘可用数量");
        }
        BigDecimal qty = raw instanceof BigDecimal decimal ? decimal : decimalValue(raw);
        if (qty.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("盘点数量不能小于0");
        }
        return qty;
    }

    private String normalizeLocationTaskCancelStatus(String value) {
        String status = valueOr(value, "CANCELLED").trim().toUpperCase(Locale.ROOT);
        return switch (status) {
            case "CANCEL", "CANCELLED", "取消" -> "CANCELLED";
            case "REJECT", "REJECTED", "驳回" -> "REJECTED";
            default -> throw new BusinessException("库位任务取消状态不支持: " + value);
        };
    }

    private MaterialLocationTask lockedLocationTask(String taskNo) {
        if (taskNo == null || taskNo.isBlank()) {
            throw new BusinessException("库位任务号不能为空");
        }
        MaterialLocationTask task = materialLocationTaskMapper.selectByTaskNoForUpdate(taskNo);
        if (task == null) {
            throw new BusinessException("库位任务不存在: " + taskNo);
        }
        return task;
    }

    private Object firstPresent(Map<String, Object> request, String... keys) {
        if (request == null) {
            return null;
        }
        for (String key : keys) {
            Object raw = request.get(key);
            if (raw != null && !String.valueOf(raw).isBlank()) {
                return raw;
            }
        }
        return null;
    }

    private MaterialLocation resolveReceivingLocation(Map<String, Object> request,
                                                      String materialCode,
                                                      String unit,
                                                      BigDecimal qty) {
        String materialClass = text(request, "materialClass", inferMaterialClass(materialCode));
        String requestedLocation = text(request, "location", "");
        MaterialLocation location = requestedLocation.isBlank()
                ? materialLocationMapper.selectAvailableLocationForUpdate(materialClass, unit, qty)
                : materialLocationMapper.selectByLocationCodeForUpdate(requestedLocation);
        if (location == null) {
            if (requestedLocation.isBlank()) {
                return null;
            }
            throw new BusinessException("库位不存在或未维护: " + requestedLocation);
        }
        validateReceivingLocation(location, materialClass, unit, qty);
        return location;
    }

    private void validateReceivingLocation(MaterialLocation location, String materialClass, String unit, BigDecimal qty) {
        if (!"ACTIVE".equals(location.getStatus())) {
            throw new BusinessException("库位不可入库: " + location.getLocationCode() + "/" + location.getStatus());
        }
        String locationClass = valueOr(location.getMaterialClass(), "ANY");
        if (!List.of("ANY", materialClass).contains(locationClass)) {
            throw new BusinessException("库位物料类别不匹配: " + location.getLocationCode()
                    + ", required=" + materialClass + ", actual=" + locationClass);
        }
        String locationUnit = valueOr(location.getUnit(), "");
        if (!locationUnit.isBlank() && !locationUnit.equals(unit)) {
            throw new BusinessException("库位计量单位不匹配: " + location.getLocationCode()
                    + ", required=" + unit + ", actual=" + locationUnit);
        }
        BigDecimal capacity = location.getCapacityQty();
        if (capacity != null && capacity.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal available = capacity.subtract(nvl(location.getUsedQty()));
            if (available.compareTo(qty) < 0) {
                throw new BusinessException("库位容量不足: " + location.getLocationCode()
                        + ", available=" + available.stripTrailingZeros().toPlainString());
            }
        }
    }

    private void increaseLocationUsage(MaterialLocation location, BigDecimal qty) {
        if (location == null) {
            return;
        }
        location.setUsedQty(nvl(location.getUsedQty()).add(qty));
        if (location.getCapacityQty() != null
                && location.getCapacityQty().compareTo(BigDecimal.ZERO) > 0
                && nvl(location.getUsedQty()).compareTo(location.getCapacityQty()) >= 0) {
            location.setStatus("FULL");
        }
        location.setUpdatedTime(LocalDateTime.now());
        materialLocationMapper.updateById(location);
    }

    private void adjustLocationUsage(String locationCode, BigDecimal delta) {
        if (locationCode == null || locationCode.isBlank() || delta.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        MaterialLocation location = materialLocationMapper.selectByLocationCodeForUpdate(locationCode);
        if (location == null) {
            return;
        }
        location.setUsedQty(maxZero(nvl(location.getUsedQty()).add(delta)));
        if ("FULL".equals(location.getStatus())
                && (location.getCapacityQty() == null
                || location.getCapacityQty().compareTo(BigDecimal.ZERO) <= 0
                || nvl(location.getUsedQty()).compareTo(location.getCapacityQty()) < 0)) {
            location.setStatus("ACTIVE");
        }
        location.setUpdatedTime(LocalDateTime.now());
        materialLocationMapper.updateById(location);
    }

    private String inferMaterialClass(String materialCode) {
        String code = valueOr(materialCode, "").toUpperCase(Locale.ROOT);
        if (code.contains("OLED")) {
            return "ORGANIC";
        }
        if (code.contains("PI") || code.contains("ENCAP") || code.contains("OCA") || code.contains("GLUE")) {
            return "CHEMICAL";
        }
        if (code.contains("FPC") || code.contains("IC")) {
            return "ELECTRONIC";
        }
        return "GENERAL";
    }

    private String formatWindow(BigDecimal min, BigDecimal max, String unit) {
        if (min == null && max == null) {
            return "-";
        }
        String left = min == null ? "*" : min.stripTrailingZeros().toPlainString();
        String right = max == null ? "*" : max.stripTrailingZeros().toPlainString();
        return left + " ~ " + right + unit;
    }

    private String locationType(String status, double utilization) {
        if (!"ACTIVE".equals(status)) {
            return "LOCKED".equals(status) || "FULL".equals(status) ? "red" : "gray";
        }
        if (utilization >= 90.0) {
            return "amber";
        }
        return "green";
    }

    private Map<String, Object> batchRow(MaterialBatch batch) {
        int remainPercent = remainPercent(batch);
        String rowStatus = batchStatus(batch, remainPercent);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("materialCode", batch.getMaterialCode());
        row.put("materialName", batch.getMaterialName());
        row.put("batchNo", batch.getBatchNo());
        row.put("supplierCode", batch.getSupplierCode());
        row.put("totalQty", batch.getTotalQty());
        row.put("availableQty", batch.getAvailableQty());
        row.put("reservedQty", batch.getReservedQty());
        row.put("consumedQty", batch.getConsumedQty());
        row.put("frozenQty", nvl(batch.getFrozenQty()));
        row.put("returnedQty", nvl(batch.getReturnedQty()));
        row.put("unit", batch.getUnit());
        row.put("remainPercent", remainPercent);
        row.put("status", rowStatus);
        row.put("stockStatus", batch.getStatus());
        row.put("qualityStatus", batch.getQualityStatus());
        row.put("expireTime", batch.getExpireTime());
        row.put("lastCountTime", batch.getLastCountTime());
        row.put("stockVersion", nvl(batch.getStockVersion()));
        row.put("location", batch.getLocation());
        row.put("type", statusType(rowStatus));
        return row;
    }

    private Map<String, Object> incomingInspectionRow(MaterialIncomingInspection inspection) {
        List<MaterialCoaAttachment> attachments = coaAttachmentMapper.selectList(
                new LambdaQueryWrapper<MaterialCoaAttachment>()
                        .eq(MaterialCoaAttachment::getInspectionNo, inspection.getInspectionNo())
                        .orderByDesc(MaterialCoaAttachment::getUploadedTime)
                        .orderByDesc(MaterialCoaAttachment::getId));
        return incomingInspectionRow(inspection, attachments);
    }

    private Map<String, Object> incomingInspectionRow(MaterialIncomingInspection inspection,
                                                      List<MaterialCoaAttachment> attachments) {
        List<MaterialCoaAttachment> safeAttachments = attachments == null ? List.of() : attachments;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("inspectionNo", inspection.getInspectionNo());
        row.put("batchNo", inspection.getBatchNo());
        row.put("materialCode", inspection.getMaterialCode());
        row.put("materialName", inspection.getMaterialName());
        row.put("supplierCode", inspection.getSupplierCode());
        row.put("result", inspection.getResult());
        row.put("inspectedQty", inspection.getInspectedQty());
        row.put("sampleQty", inspection.getSampleQty());
        row.put("unit", inspection.getUnit());
        row.put("defectCode", inspection.getDefectCode());
        row.put("defectDescription", inspection.getDefectDescription());
        row.put("coaNo", inspection.getCoaNo());
        row.put("conclusion", inspection.getConclusion());
        row.put("inspector", inspection.getInspector());
        row.put("inspectionTime", inspection.getInspectionTime());
        row.put("sourceSystem", inspection.getSourceSystem());
        row.put("attachmentCount", safeAttachments.size());
        row.put("attachments", safeAttachments.stream().map(this::coaAttachmentRow).collect(Collectors.toList()));
        row.put("status", inspection.getResult());
        row.put("type", statusType(inspection.getResult()));
        return row;
    }

    private Map<String, Object> coaAttachmentRow(MaterialCoaAttachment attachment) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("attachmentNo", attachment.getAttachmentNo());
        row.put("inspectionNo", attachment.getInspectionNo());
        row.put("batchNo", attachment.getBatchNo());
        row.put("fileName", attachment.getFileName());
        row.put("fileUrl", attachment.getFileUrl());
        row.put("fileHash", attachment.getFileHash());
        row.put("fileType", attachment.getFileType());
        row.put("uploadedBy", attachment.getUploadedBy());
        row.put("uploadedTime", attachment.getUploadedTime());
        return row;
    }

    private Map<String, Object> txnRow(MaterialInventoryTxn txn) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("txnNo", txn.getTxnNo());
        row.put("txnType", txn.getTxnType());
        row.put("materialCode", txn.getMaterialCode());
        row.put("materialName", txn.getMaterialName());
        row.put("batchNo", txn.getBatchNo());
        row.put("qtyDelta", txn.getQtyDelta());
        row.put("availableBefore", txn.getAvailableBefore());
        row.put("availableAfter", txn.getAvailableAfter());
        row.put("frozenBefore", txn.getFrozenBefore());
        row.put("frozenAfter", txn.getFrozenAfter());
        row.put("reservedBefore", txn.getReservedBefore());
        row.put("reservedAfter", txn.getReservedAfter());
        row.put("countedQty", txn.getCountedQty());
        row.put("unit", txn.getUnit());
        row.put("reason", txn.getReason());
        row.put("operator", txn.getOperator());
        row.put("sourceSystem", txn.getSourceSystem());
        row.put("txnTime", txn.getTxnTime());
        return row;
    }

    private Map<String, Object> carrierRow(Carrier carrier) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("carrierNo", carrier.getCarrierNo());
        row.put("code", carrier.getCarrierNo());
        row.put("carrierType", carrier.getCarrierType());
        row.put("type", statusType(carrier.getStatus()));
        row.put("status", carrier.getStatus());
        row.put("lotNo", valueOr(carrier.getLotNo(), ""));
        row.put("lineCode", carrier.getLineCode());
        row.put("lot", valueOr(carrier.getLotNo(), "-"));
        row.put("productCode", carrier.getProductCode());
        row.put("stepCode", carrier.getStepCode());
        row.put("step", valueOr(carrier.getStepCode(), "-"));
        row.put("equipmentCode", carrier.getEquipmentCode());
        row.put("bindTime", carrier.getBindTime());
        row.put("location", carrier.getLocation());
        return row;
    }

    private Map<String, Object> consumptionRow(MaterialConsumption consumption) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("consumptionNo", consumption.getConsumptionNo());
        row.put("lotNo", consumption.getLotNo());
        row.put("lot", consumption.getLotNo());
        row.put("orderNo", consumption.getOrderNo());
        row.put("productCode", consumption.getProductCode());
        row.put("stepCode", consumption.getStepCode());
        row.put("step", consumption.getStepCode());
        row.put("equipmentCode", consumption.getEquipmentCode());
        row.put("materialCode", consumption.getMaterialCode());
        row.put("materialName", consumption.getMaterialName());
        row.put("batchNo", consumption.getBatchNo());
        row.put("batch", consumption.getBatchNo());
        row.put("consumedQty", consumption.getConsumedQty());
        row.put("qty", nvl(consumption.getConsumedQty()).stripTrailingZeros().toPlainString() + valueOr(consumption.getUnit(), ""));
        row.put("unit", consumption.getUnit());
        row.put("operator", consumption.getOperator());
        row.put("consumeTime", consumption.getConsumeTime());
        row.put("time", consumption.getConsumeTime() == null ? "" : consumption.getConsumeTime().toLocalTime().withNano(0).toString());
        row.put("traceStatus", consumption.getTraceStatus());
        row.put("status", consumption.getTraceStatus());
        row.put("type", "TRACEABLE".equals(consumption.getTraceStatus()) ? "green" : "amber");
        return row;
    }

    private List<Map<String, Object>> readinessChecks(List<Map<String, Object>> batchRows) {
        long ok = batchRows.stream().filter(row -> "OK".equals(row.get("status"))).count();
        long warning = batchRows.stream().filter(row -> "WARNING".equals(row.get("status"))).count();
        long blocked = batchRows.stream().filter(row -> "BLOCKED".equals(row.get("status"))).count();
        return List.of(
                check("BOM关键物料", batchRows.isEmpty() ? "未配置" : "已配置 " + batchRows.size() + " 批", batchRows.isEmpty() ? "red" : "green"),
                check("批次质量", blocked > 0 ? "存在不可用批次" : "来料质量 PASS", blocked > 0 ? "red" : "green"),
                check("FIFO库存", warning > 0 ? "低库存 " + warning + " 批" : "可用量充足", warning > 0 ? "amber" : "green"),
                check("齐套结果", blocked > 0 ? "BLOCKED" : ok > 0 ? "PASS" : "待确认", blocked > 0 ? "red" : "green")
        );
    }

    private Map<String, Object> check(String title, String text, String type) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("title", title);
        row.put("text", text);
        row.put("type", type);
        return row;
    }

    private String readiness(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return "NO_DATA";
        }
        if (rows.stream().anyMatch(row -> "BLOCKED".equals(row.get("status")))) {
            return "BLOCKED";
        }
        if (rows.stream().anyMatch(row -> "WARNING".equals(row.get("status")))) {
            return "PASS_WITH_WARNING";
        }
        return "PASS";
    }

    private int remainPercent(MaterialBatch batch) {
        BigDecimal total = nvl(batch.getTotalQty());
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        return nvl(batch.getAvailableQty())
                .multiply(BigDecimal.valueOf(100))
                .divide(total, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    private String batchStatus(MaterialBatch batch, int remainPercent) {
        if (!"AVAILABLE".equals(batch.getStatus()) || !"PASS".equals(batch.getQualityStatus())) {
            return "BLOCKED";
        }
        if (batch.getExpireTime() != null && batch.getExpireTime().isBefore(LocalDateTime.now())) {
            return "BLOCKED";
        }
        if (remainPercent < 20) {
            return "WARNING";
        }
        return "OK";
    }

    private String statusType(String status) {
        return switch (valueOr(status, "")) {
            case "OK", "PASS", "AVAILABLE", "BOUND", "LOADED", "TRACEABLE", "APPROVED", "PUBLISHED", "DONE" -> "green";
            case "WARNING", "RESERVED", "CLEANING", "WAIT_MRB", "FROZEN", "SUBMITTED", "PENDING", "CREATED", "ASSIGNED" -> "amber";
            case "BLOCKED", "HOLD", "LOCKED", "NG", "REJECTED", "CANCELLED" -> "red";
            case "IDLE", "EXECUTING" -> "blue";
            default -> "gray";
        };
    }

    private List<MaterialCoaAttachment> createCoaAttachments(MaterialIncomingInspection inspection,
                                                             Map<String, Object> request,
                                                             String operator,
                                                             LocalDateTime now) {
        List<MaterialCoaAttachment> attachments = new ArrayList<>();
        Object rawAttachments = value(request, "attachments");
        if (rawAttachments instanceof Collection<?> collection) {
            for (Object item : collection) {
                appendCoaAttachment(attachments, inspection, objectMap(item), operator, now);
            }
        }

        Map<String, Object> direct = new LinkedHashMap<>();
        direct.put("fileName", text(request, "coaFileName", text(request, "fileName", "")));
        direct.put("fileUrl", text(request, "coaFileUrl", text(request, "fileUrl", "")));
        direct.put("fileHash", text(request, "coaFileHash", text(request, "fileHash", "")));
        direct.put("fileType", text(request, "fileType", "COA"));
        appendCoaAttachment(attachments, inspection, direct, operator, now);
        return attachments;
    }

    private void appendCoaAttachment(List<MaterialCoaAttachment> attachments,
                                     MaterialIncomingInspection inspection,
                                     Map<String, Object> source,
                                     String operator,
                                     LocalDateTime now) {
        String fileName = text(source, "fileName", text(source, "name", ""));
        String fileUrl = text(source, "fileUrl", text(source, "url", ""));
        String fileHash = text(source, "fileHash", text(source, "hash", ""));
        if (fileName.isBlank() && fileUrl.isBlank() && fileHash.isBlank()) {
            return;
        }
        if (fileName.isBlank()) {
            fileName = !fileUrl.isBlank() ? fileUrl : fileHash;
        }

        MaterialCoaAttachment attachment = new MaterialCoaAttachment();
        attachment.setAttachmentNo(nextNo("MCA"));
        attachment.setInspectionNo(inspection.getInspectionNo());
        attachment.setBatchNo(inspection.getBatchNo());
        attachment.setFileName(fileName);
        attachment.setFileUrl(fileUrl);
        attachment.setFileHash(fileHash);
        attachment.setFileType(text(source, "fileType", text(source, "type", "COA")));
        attachment.setUploadedBy(text(source, "uploadedBy", operator));
        attachment.setUploadedTime(now);
        attachment.setCreatedTime(now);
        coaAttachmentMapper.insert(attachment);
        attachments.add(attachment);
    }

    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> target = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                target.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return target;
    }

    private void applyIncomingResult(MaterialBatch batch, String result) {
        batch.setQualityStatus(result);
        if (!"PASS".equals(result)) {
            batch.setStatus("HOLD");
            return;
        }
        if (nvl(batch.getAvailableQty()).compareTo(BigDecimal.ZERO) > 0) {
            batch.setStatus("AVAILABLE");
        } else if (nvl(batch.getFrozenQty()).compareTo(BigDecimal.ZERO) > 0) {
            batch.setStatus("FROZEN");
        } else if (nvl(batch.getReservedQty()).compareTo(BigDecimal.ZERO) > 0) {
            batch.setStatus("RESERVED");
        } else if (nvl(batch.getConsumedQty()).compareTo(BigDecimal.ZERO) > 0) {
            batch.setStatus("CONSUMED");
        } else {
            batch.setStatus("AVAILABLE");
        }
    }

    private String normalizeIncomingResult(String value) {
        String result = valueOr(value, "PASS").trim().toUpperCase(Locale.ROOT);
        return switch (result) {
            case "OK", "PASS", "ACCEPT" -> "PASS";
            case "NG", "FAIL", "FAILED", "REJECT", "REJECTED" -> "NG";
            case "HOLD", "WAIT", "PENDING", "WAIT_MRB" -> "HOLD";
            default -> throw new BusinessException("来料质检结果不支持: " + value);
        };
    }

    private String defaultIncomingConclusion(String result) {
        return switch (result) {
            case "PASS" -> "来料检验通过，批次允许投料。";
            case "NG" -> "来料检验不合格，批次禁止投料并等待处置。";
            default -> "来料检验待处置，批次暂挂。";
        };
    }

    private List<BomChangeAttachment> createBomChangeAttachments(BomChangeRequest change,
                                                                 Map<String, Object> request,
                                                                 String operator,
                                                                 LocalDateTime now) {
        List<BomChangeAttachment> attachments = new ArrayList<>();
        Object rawAttachments = value(request, "attachments");
        if (rawAttachments instanceof Collection<?> collection) {
            for (Object item : collection) {
                appendBomChangeAttachment(attachments, change, objectMap(item), operator, now);
            }
        }

        Map<String, Object> direct = new LinkedHashMap<>();
        direct.put("fileName", text(request, "validationFileName", text(request, "fileName", "")));
        direct.put("fileUrl", text(request, "validationFileUrl", text(request, "fileUrl", "")));
        direct.put("fileHash", text(request, "validationFileHash", text(request, "fileHash", "")));
        direct.put("fileType", text(request, "validationFileType", text(request, "fileType", "SUBSTITUTE_VALIDATION")));
        direct.put("attachmentRole", text(request, "attachmentRole", "SUBSTITUTE_VALIDATION"));
        appendBomChangeAttachment(attachments, change, direct, operator, now);
        return attachments;
    }

    private void appendBomChangeAttachment(List<BomChangeAttachment> attachments,
                                           BomChangeRequest change,
                                           Map<String, Object> source,
                                           String operator,
                                           LocalDateTime now) {
        String fileName = text(source, "fileName", text(source, "name", ""));
        String fileUrl = text(source, "fileUrl", text(source, "url", ""));
        String fileHash = text(source, "fileHash", text(source, "hash", ""));
        if (fileName.isBlank() && fileUrl.isBlank() && fileHash.isBlank()) {
            return;
        }
        if (fileName.isBlank()) {
            fileName = !fileUrl.isBlank() ? fileUrl : fileHash;
        }

        BomChangeAttachment attachment = new BomChangeAttachment();
        attachment.setAttachmentNo(nextNo("BCA"));
        attachment.setChangeNo(change.getChangeNo());
        attachment.setProductCode(change.getProductCode());
        attachment.setTargetBomCode(change.getTargetBomCode());
        attachment.setFileName(fileName);
        attachment.setFileUrl(fileUrl);
        attachment.setFileHash(fileHash);
        attachment.setFileType(text(source, "fileType", text(source, "type", "SUBSTITUTE_VALIDATION")));
        attachment.setAttachmentRole(text(source, "attachmentRole", text(source, "role", "SUBSTITUTE_VALIDATION")));
        attachment.setUploadedBy(text(source, "uploadedBy", operator));
        attachment.setUploadedTime(now);
        attachment.setCreatedTime(now);
        bomChangeAttachmentMapper.insert(attachment);
        attachments.add(attachment);
    }

    private List<String> ecoApprovalRoles(Map<String, Object> request) {
        List<String> roles = new ArrayList<>();
        Object rawRoles = value(request, "approvalRoles");
        if (rawRoles instanceof Collection<?> collection) {
            collection.forEach(role -> addEcoApprovalRole(roles, role));
        } else if (rawRoles != null && !String.valueOf(rawRoles).isBlank()) {
            for (String role : String.valueOf(rawRoles).split(",")) {
                addEcoApprovalRole(roles, role);
            }
        }
        if (roles.isEmpty()) {
            addEcoApprovalRole(roles, "PE");
            if (boolValue(request, "qualityImpact", true) || boolValue(request, "substituteMaterial", true)) {
                addEcoApprovalRole(roles, "QE");
            }
            if (boolValue(request, "materialImpact", true)) {
                addEcoApprovalRole(roles, "PLANNER");
            }
            if (boolValue(request, "equipmentImpact", false)) {
                addEcoApprovalRole(roles, "EE");
            }
        }
        return roles;
    }

    private void addEcoApprovalRole(List<String> roles, Object value) {
        String role = String.valueOf(value == null ? "" : value).trim().toUpperCase(Locale.ROOT);
        if (role.isBlank()) {
            return;
        }
        role = switch (role) {
            case "PROCESS", "PROCESS_ENGINEER" -> "PE";
            case "QUALITY", "QUALITY_ENGINEER" -> "QE";
            case "PLAN", "PMC", "WMS", "MATERIAL", "MATERIAL_PLANNER" -> "PLANNER";
            case "EQUIPMENT", "EQUIPMENT_ENGINEER" -> "EE";
            default -> role;
        };
        if (!List.of("PE", "QE", "PLANNER", "EE").contains(role)) {
            throw new BusinessException("BOM/ECO会签角色不支持: " + value);
        }
        if (!roles.contains(role)) {
            roles.add(role);
        }
    }

    private String ecoRiskLevel(Map<String, Object> request) {
        String risk = text(request, "ecoRiskLevel", text(request, "riskLevel", "MEDIUM"))
                .trim()
                .toUpperCase(Locale.ROOT);
        return switch (risk) {
            case "P1", "HIGH", "CRITICAL" -> "HIGH";
            case "P3", "LOW" -> "LOW";
            default -> "MEDIUM";
        };
    }

    private int ecoSlaHours(String riskLevel) {
        return switch (valueOr(riskLevel, "MEDIUM")) {
            case "HIGH" -> 12;
            case "LOW" -> 72;
            default -> 24;
        };
    }

    private String ecoSlaLevel(String riskLevel) {
        return switch (valueOr(riskLevel, "MEDIUM")) {
            case "HIGH" -> "P1";
            case "LOW" -> "P3";
            default -> "P2";
        };
    }

    private String ecoPackageSnapshot(BomChangeRequest change,
                                      Map<String, Object> request,
                                      List<BomItem> createdItems,
                                      List<String> approvalRoles) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("ecoNo", change.getEcoNo());
        snapshot.put("changeNo", change.getChangeNo());
        snapshot.put("changeType", change.getChangeType());
        snapshot.put("productCode", change.getProductCode());
        snapshot.put("sourceBomCode", change.getSourceBomCode());
        snapshot.put("targetBomCode", change.getTargetBomCode());
        snapshot.put("targetVersion", change.getTargetVersion());
        snapshot.put("riskLevel", change.getEcoRiskLevel());
        snapshot.put("approvalRoles", approvalRoles);
        snapshot.put("reason", change.getReason());
        snapshot.put("validationPlan", text(request, "validationPlan", "替代料验证报告、试产批次和质量判定标准齐套后发布"));
        snapshot.put("rollbackPlan", text(request, "rollbackPlan", "发布异常时回退到上一版ACTIVE BOM并冻结目标版本"));
        snapshot.put("impactScope", text(request, "impactScope", "单基地/单产线试点产品"));
        snapshot.put("itemCount", createdItems.size());
        snapshot.put("keyMaterialCount", createdItems.stream()
                .filter(item -> Integer.valueOf(1).equals(item.getIsKeyMaterial()))
                .count());
        snapshot.put("substituteGroups", createdItems.stream()
                .map(item -> valueOr(item.getSubstituteGroup(), item.getMaterialCode()))
                .distinct()
                .collect(Collectors.toList()));
        return snapshot.toString();
    }

    private String normalizeEcoDecision(String decision) {
        String value = valueOr(decision, "APPROVE").trim().toUpperCase(Locale.ROOT);
        if (value.contains("REJECT") || value.contains("NG")) {
            return "REJECT";
        }
        return "APPROVE";
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private long nvl(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal maxZero(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : value;
    }

    private <T> void applyLotDataScope(LambdaQueryWrapper<T> wrapper) {
        RolePermissionService.DataScopeCondition condition = dataScopeCondition();
        if (condition != null && !condition.unrestricted()) {
            wrapper.apply("lot_no IN (SELECT lot_no FROM prod_lot WHERE " + condition.sql() + ")",
                    condition.parameters().toArray());
        }
    }

    private <T> void applyLineDataScope(LambdaQueryWrapper<T> wrapper) {
        RolePermissionService.DataScopeCondition condition = dataScopeCondition();
        if (condition != null && !condition.unrestricted()) {
            wrapper.apply(condition.sql(), condition.parameters().toArray());
        }
    }

    private RolePermissionService.DataScopeCondition dataScopeCondition() {
        return rolePermissionService.dataScopeCondition(AuthContext.role(), AuthContext.username(), "",
                "line_code", null, null);
    }

    private String nextNo(String prefix) {
        long seq = NO_COUNTER.updateAndGet(value -> value >= 9999 ? 1 : value + 1);
        return prefix + "-" + NO_TIME.format(LocalDateTime.now()) + "-" + String.format("%04d", seq);
    }

    private MaterialBatch lockedBatch(String batchNo) {
        if (batchNo == null || batchNo.isBlank()) {
            throw new BusinessException("物料批次号不能为空");
        }
        MaterialBatch batch = batchMapper.selectByBatchNoForUpdate(batchNo);
        if (batch == null) {
            throw new BusinessException("物料批次不存在: " + batchNo);
        }
        return batch;
    }

    private void touchStock(MaterialBatch batch) {
        batch.setStockVersion(nvl(batch.getStockVersion()) + 1);
        batch.setUpdatedTime(LocalDateTime.now());
    }

    private void insertTxn(String txnType,
                           MaterialBatch batch,
                           BigDecimal availableBefore,
                           BigDecimal availableAfter,
                           BigDecimal frozenBefore,
                           BigDecimal frozenAfter,
                           BigDecimal reservedBefore,
                           BigDecimal reservedAfter,
                           BigDecimal qtyDelta,
                           BigDecimal countedQty,
                           String reason,
                           String operator,
                           Map<String, Object> request) {
        MaterialInventoryTxn txn = new MaterialInventoryTxn();
        txn.setTxnNo(nextNo("MIT"));
        txn.setTxnType(txnType);
        txn.setMaterialCode(batch.getMaterialCode());
        txn.setMaterialName(batch.getMaterialName());
        txn.setBatchNo(batch.getBatchNo());
        txn.setSupplierCode(batch.getSupplierCode());
        txn.setQtyDelta(qtyDelta);
        txn.setAvailableBefore(availableBefore);
        txn.setAvailableAfter(availableAfter);
        txn.setFrozenBefore(frozenBefore);
        txn.setFrozenAfter(frozenAfter);
        txn.setReservedBefore(reservedBefore);
        txn.setReservedAfter(reservedAfter);
        txn.setCountedQty(countedQty);
        txn.setUnit(batch.getUnit());
        txn.setReason(reason);
        txn.setSourceSystem(text(request, "sourceSystem", "wms-adapter"));
        txn.setOperator(operator);
        txn.setTxnTime(LocalDateTime.now());
        txn.setRequestSnapshot(request == null ? "{}" : request.toString());
        txn.setCreatedTime(txn.getTxnTime());
        inventoryTxnMapper.insert(txn);
    }

    private void audit(String action, String bizNo, String bizType, String description, String operator) {
        try {
            auditLogService.record(action, bizNo, bizType, description, operator, "material-service", null);
        } catch (Exception e) {
            log.warn("物料审计日志写入失败，已降级不阻断主流程: action={}, bizNo={}, reason={}", action, bizNo, e.getMessage());
        }
    }

    private String requiredText(Map<String, Object> request, String key) {
        String value = text(request, key, "");
        if (value.isBlank()) {
            throw new BusinessException(key + "不能为空");
        }
        return value;
    }

    private String text(Map<String, Object> request, String key, String defaultValue) {
        Object value = value(request, key);
        return value == null || String.valueOf(value).isBlank() ? defaultValue : String.valueOf(value);
    }

    private Object value(Map<String, Object> request, String key) {
        return request == null ? null : request.get(key);
    }

    private boolean boolValue(Map<String, Object> request, String key, boolean defaultValue) {
        Object value = value(request, key);
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return switch (text) {
            case "true", "1", "yes", "y", "on" -> true;
            case "false", "0", "no", "n", "off" -> false;
            default -> defaultValue;
        };
    }

    private int intValue(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private BigDecimal positiveQty(Map<String, Object> request, String key) {
        BigDecimal qty = decimalValue(value(request, key));
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(key + "必须大于0");
        }
        return qty;
    }

    private BigDecimal nonNegativeQty(Map<String, Object> request, String key) {
        BigDecimal qty = decimalValue(value(request, key));
        if (qty.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(key + "不能小于0");
        }
        return qty;
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new BusinessException("数量格式不正确: " + value);
        }
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static class SupplierScore {
        private final String supplierCode;
        private final List<String> materialCodes = new ArrayList<>();
        private BigDecimal availableQty = BigDecimal.ZERO;
        private int batchCount;
        private int availableBatchCount;
        private int riskBatchCount;
        private int inspectionCount;
        private int passCount;
        private int holdCount;
        private int ngCount;
        private LocalDateTime lastBatchTime;
        private LocalDateTime lastInspectionTime;

        private SupplierScore(String supplierCode) {
            this.supplierCode = supplierCode;
        }

        private void addMaterial(String materialCode) {
            if (materialCode != null && !materialCode.isBlank() && !materialCodes.contains(materialCode)) {
                materialCodes.add(materialCode);
            }
        }
    }

    private static class SupplierTrend {
        private final String supplierCode;
        private final Map<YearMonth, SupplierTrendPoint> points = new LinkedHashMap<>();
        private String supplierName;
        private String materialClass = "GENERAL";

        private SupplierTrend(String supplierCode) {
            this.supplierCode = supplierCode;
            this.supplierName = supplierCode;
        }

        private SupplierTrendPoint point(YearMonth period) {
            return points.computeIfAbsent(period, ignored -> new SupplierTrendPoint());
        }

        private void applySupplier(Supplier supplier) {
            this.supplierName = supplier.getSupplierName() == null || supplier.getSupplierName().isBlank()
                    ? supplierCode
                    : supplier.getSupplierName();
            this.materialClass = supplier.getMaterialClass() == null || supplier.getMaterialClass().isBlank()
                    ? "GENERAL"
                    : supplier.getMaterialClass();
        }

        private boolean hasActivity() {
            return points.values().stream().anyMatch(SupplierTrendPoint::hasActivity);
        }
    }

    private static class SupplierTrendPoint {
        private final List<String> materialCodes = new ArrayList<>();
        private int batchCount;
        private int riskBatchCount;
        private int inspectionCount;
        private int passCount;
        private int holdCount;
        private int ngCount;
        private int actionCount;
        private int openActionCount;
        private int overdueActionCount;
        private int highOpenActionCount;

        private void addMaterial(String materialCode) {
            if (materialCode != null && !materialCode.isBlank() && !materialCodes.contains(materialCode)) {
                materialCodes.add(materialCode);
            }
        }

        private boolean hasActivity() {
            return batchCount + inspectionCount + actionCount > 0;
        }
    }

    private record MaterialRequirement(String groupKey, List<BomItem> candidates) {
        String materialCodes() {
            return candidates.stream()
                    .map(BomItem::getMaterialCode)
                    .collect(Collectors.joining("/"));
        }
    }

    private record MaterialPlan(BomItem item, MaterialBatch batch, BigDecimal requiredQty) {
    }

    private record QualificationDecision(String status, String riskLevel, double score, double passRate,
                                         int nextAuditDays, String reason) {
    }
}
