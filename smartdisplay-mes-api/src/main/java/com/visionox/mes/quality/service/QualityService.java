package com.visionox.mes.quality.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.visionox.mes.auth.security.AuthContext;
import com.visionox.mes.auth.security.RolePermissionService;
import com.visionox.mes.common.BusinessException;
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
import com.visionox.mes.recipe.entity.Recipe;
import com.visionox.mes.recipe.entity.RecipeParam;
import com.visionox.mes.recipe.mapper.RecipeMapper;
import com.visionox.mes.recipe.mapper.RecipeParamMapper;
import com.visionox.mes.system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 质量检验与异常事件服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityService {

    private static final DateTimeFormatter NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final AtomicLong NO_COUNTER = new AtomicLong();
    private static final Set<String> DISPOSITION_ACTIONS = Set.of("RELEASE", "REWORK", "SCRAP", "CONTINUE_HOLD");

    private final QualityInspectionMapper inspectionMapper;
    private final QualityDefectRecordMapper defectRecordMapper;
    private final ExceptionEventMapper exceptionEventMapper;
    private final QualityMrbRecordMapper mrbRecordMapper;
    private final QualityMrbAttachmentMapper mrbAttachmentMapper;
    private final QualityMrbMinutesMapper mrbMinutesMapper;
    private final QualityMrbApprovalTaskMapper mrbApprovalTaskMapper;
    private final RecipeMapper recipeMapper;
    private final RecipeParamMapper recipeParamMapper;
    private final LotMapper lotMapper;
    private final HoldRecordMapper holdRecordMapper;
    private final AuditLogService auditLogService;
    private final RolePermissionService rolePermissionService;

    /**
     * Track Out 后执行质量判定，返回最终出站结果。
     */
    @Transactional(rollbackFor = Exception.class)
    public String evaluateTrackOut(Lot lot, LotStepRecord stepRecord, TrackOutRequest request) {
        String requestedResult = normalizeResult(request.getResult());
        Map<String, Object> processParams = parseParams(request.getProcessParams());
        Recipe recipe = findRecipe(stepRecord.getRecipeCode());
        List<RecipeParam> recipeParams = recipe == null ? List.of() : recipeParams(recipe.getId());
        List<QualityInspection> inspections = new ArrayList<>();
        boolean hasNg = "NG".equals(requestedResult);

        for (RecipeParam recipeParam : recipeParams) {
            Object rawValue = findMeasuredValue(processParams, recipeParam);
            if (rawValue == null) {
                continue;
            }
            BigDecimal measuredValue = decimalValue(rawValue);
            String result = judgeParam(recipeParam, measuredValue);
            if ("NG".equals(result)) {
                hasNg = true;
            }
            QualityInspection inspection = buildInspection(lot, stepRecord, recipeParam, measuredValue, result);
            inspectionMapper.insert(inspection);
            inspections.add(inspection);
            if ("NG".equals(result)) {
                createDefect(inspection, defectCode(recipeParam), defectName(recipeParam), "MAJOR");
            }
        }

        if (inspections.isEmpty() || "NG".equals(requestedResult)) {
            QualityInspection inspection = buildResultInspection(lot, stepRecord, requestedResult, request.getRemark());
            inspectionMapper.insert(inspection);
            inspections.add(inspection);
            if ("NG".equals(requestedResult)) {
                createDefect(inspection, "D-PROCESS-NG", "出站判定不合格", "MAJOR");
            }
        }

        String finalResult = hasNg ? "NG" : "OK";
        audit("QUALITY_INSPECTION", lot.getLotNo(), "LOT",
                "Track Out 质检判定 result=" + finalResult + ", items=" + inspections.size(), "system",
                request.getProcessParams());

        if ("NG".equals(finalResult)) {
            ExceptionEvent event = createException(lot, stepRecord, inspections);
            autoHold(lot.getLotNo(), "QUALITY", "质量检验不合格，异常单 " + event.getEventNo() + " 自动 Hold", "system");
        }
        return finalResult;
    }

    public List<Map<String, Object>> inspectionRows(String lotNo) {
        LambdaQueryWrapper<QualityInspection> wrapper = new LambdaQueryWrapper<>();
        applyLotDataScope(wrapper);
        if (lotNo != null && !lotNo.isBlank()) {
            wrapper.eq(QualityInspection::getLotNo, lotNo);
        }
        wrapper.orderByDesc(QualityInspection::getInspectionTime).last("LIMIT 100");
        return inspectionMapper.selectList(wrapper).stream()
                .map(this::inspectionRow)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> exceptionRows(String lotNo) {
        LambdaQueryWrapper<ExceptionEvent> wrapper = new LambdaQueryWrapper<>();
        applyLotDataScope(wrapper);
        if (lotNo != null && !lotNo.isBlank()) {
            wrapper.eq(ExceptionEvent::getLotNo, lotNo);
        }
        wrapper.orderByDesc(ExceptionEvent::getOccurredTime).last("LIMIT 100");
        return exceptionEventMapper.selectList(wrapper).stream()
                .map(this::exceptionRow)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> mrbRecords(String eventNo) {
        findException(eventNo);
        return mrbRecordMapper.selectList(new LambdaQueryWrapper<QualityMrbRecord>()
                        .eq(QualityMrbRecord::getEventNo, eventNo)
                        .orderByDesc(QualityMrbRecord::getReviewTime)
                        .orderByDesc(QualityMrbRecord::getId)
                        .last("LIMIT 100"))
                .stream()
                .map(this::mrbRecordRow)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> mrbMinutes(String mrbNo) {
        QualityMrbRecord record = findMrbRecord(mrbNo);
        return mrbMinutesForRecord(record).stream()
                .map(this::mrbMinutesRow)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createMrbMinutes(String mrbNo, Map<String, Object> request) {
        QualityMrbRecord record = findMrbRecord(mrbNo);
        ExceptionEvent event = findException(record.getEventNo());
        String content = extractMrbMinutesContent(request);
        if (content.isBlank()) {
            throw new BusinessException("MRB会议纪要正文不能为空");
        }
        String editor = text(request, "editor", text(request, "operator", AuthContext.username()));
        QualityMrbMinutes minutes = createMrbMinutesVersion(record, event, content, editor, "MANUAL", request, LocalDateTime.now());
        audit("MRB_MINUTES_CREATE", minutes.getMinutesNo(), "MRB_MINUTES",
                "MRB会议纪要新增版本: mrbNo=" + mrbNo + ", version=" + minutes.getVersionNo(),
                editor, JSONUtil.toJsonStr(request));
        return mrbMinutesRow(minutes);
    }

    public List<Map<String, Object>> mrbApprovalTasks(String eventNo, String status) {
        if (eventNo != null && !eventNo.isBlank()) {
            findException(eventNo);
        }
        LambdaQueryWrapper<QualityMrbApprovalTask> wrapper = new LambdaQueryWrapper<>();
        applyApprovalTaskDataScope(wrapper);
        if (eventNo != null && !eventNo.isBlank()) {
            wrapper.eq(QualityMrbApprovalTask::getEventNo, eventNo);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(QualityMrbApprovalTask::getApprovalStatus, normalizeApprovalStatus(status));
        }
        wrapper.orderByAsc(QualityMrbApprovalTask::getDueTime)
                .orderByDesc(QualityMrbApprovalTask::getCreatedTime)
                .last("LIMIT 100");
        return mrbApprovalTaskMapper.selectList(wrapper).stream()
                .map(this::mrbApprovalTaskRow)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> refreshMrbApprovalSla(Map<String, Object> request) {
        LocalDateTime now = LocalDateTime.now();
        String eventNo = text(request, "eventNo", "");
        if (!eventNo.isBlank()) {
            findException(eventNo);
        }
        String mrbNo = text(request, "mrbNo", "");
        String operator = text(request, "operator", AuthContext.username());
        String reason = text(request, "escalationReason", "MRB approval SLA overdue");
        int limit = Math.max(1, Math.min(intValue(request == null ? null : request.get("limit"), 200), 500));

        LambdaQueryWrapper<QualityMrbApprovalTask> wrapper = new LambdaQueryWrapper<>();
        applyApprovalTaskDataScope(wrapper);
        wrapper.eq(QualityMrbApprovalTask::getApprovalStatus, "PENDING")
                .le(QualityMrbApprovalTask::getDueTime, now)
                .orderByAsc(QualityMrbApprovalTask::getDueTime)
                .last("LIMIT " + limit);
        if (!eventNo.isBlank()) {
            wrapper.eq(QualityMrbApprovalTask::getEventNo, eventNo);
        }
        if (!mrbNo.isBlank()) {
            wrapper.eq(QualityMrbApprovalTask::getMrbNo, mrbNo);
        }

        List<QualityMrbApprovalTask> tasks = mrbApprovalTaskMapper.selectList(wrapper);
        tasks = tasks == null ? List.of() : tasks;
        Set<String> affectedMrbNos = new LinkedHashSet<>();
        List<Map<String, Object>> escalatedRows = new ArrayList<>();
        for (QualityMrbApprovalTask task : tasks) {
            String escalationRole = text(request, "escalationRole", defaultEscalationRole(task.getApprovalRole()));
            String escalatedTo = text(request, "escalatedTo", defaultEscalatedTo(escalationRole));
            task.setApprovalStatus("ESCALATED");
            task.setEscalationRole(escalationRole);
            task.setEscalatedTo(escalatedTo);
            task.setEscalatedTime(now);
            task.setEscalationReason(reason);
            task.setEscalationCount((task.getEscalationCount() == null ? 0 : task.getEscalationCount()) + 1);
            task.setUpdatedTime(now);
            mrbApprovalTaskMapper.updateById(task);
            affectedMrbNos.add(task.getMrbNo());
            escalatedRows.add(mrbApprovalTaskRow(task));
            audit("MRB_APPROVAL_ESCALATE", task.getTaskNo(), "MRB_APPROVAL",
                    "MRB approval SLA escalated: mrbNo=" + task.getMrbNo()
                            + ", role=" + task.getApprovalRole()
                            + ", escalatedTo=" + escalatedTo,
                    operator, JSONUtil.toJsonStr(request));
        }
        affectedMrbNos.forEach(this::refreshMrbApprovalStatus);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("escalatedCount", escalatedRows.size());
        row.put("operator", operator);
        row.put("refreshedAt", now);
        row.put("tasks", escalatedRows);
        return row;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> decideMrbApprovalTask(String taskNo, Map<String, Object> request) {
        QualityMrbApprovalTask task = findApprovalTask(taskNo);
        if (!approvalStillOpen(task)) {
            throw new BusinessException("MRB会签任务已处理，不能重复审批: " + taskNo);
        }
        String decision = normalizeApprovalDecision(text(request, "decision", text(request, "action", "APPROVE")));
        String operator = text(request, "approver", text(request, "operator", AuthContext.username()));
        LocalDateTime now = LocalDateTime.now();
        task.setApprovalStatus("APPROVE".equals(decision) ? "APPROVED" : "REJECTED");
        task.setDecision(decision);
        task.setOpinion(text(request, "opinion", "APPROVE".equals(decision) ? "会签通过" : "会签驳回"));
        task.setApprover(operator);
        task.setActionTime(now);
        task.setUpdatedTime(now);
        mrbApprovalTaskMapper.updateById(task);

        refreshMrbApprovalStatus(task.getMrbNo());
        audit("MRB_APPROVAL_" + decision, task.getTaskNo(), "MRB_APPROVAL",
                "MRB会签 decision=" + decision + ", mrbNo=" + task.getMrbNo(),
                operator, JSONUtil.toJsonStr(request));
        return mrbApprovalTaskRow(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reviewException(String eventNo, Map<String, Object> request) {
        ExceptionEvent event = findException(eventNo);
        if ("CLOSED".equals(event.getStatus())) {
            throw new BusinessException("异常事件已关闭，不能重复复判: " + eventNo);
        }

        String action = normalizeDispositionAction(text(request, "dispositionAction", text(request, "action", "CONTINUE_HOLD")));
        String reviewer = text(request, "reviewer", text(request, "operator", AuthContext.username()));
        String opinion = text(request, "mrbOpinion", text(request, "opinion", defaultMrbOpinion(action)));
        LocalDateTime now = LocalDateTime.now();

        boolean approvalRequired = approvalRequired(event, action, request);
        event.setStatus(approvalRequired ? "MRB_PENDING" : "CONTINUE_HOLD".equals(action) ? "PROCESSING" : "MRB_REVIEWED");
        event.setOwnerUser(reviewer);
        event.setMrbResult(action);
        event.setMrbOpinion(opinion);
        event.setMrbReviewer(reviewer);
        event.setMrbTime(now);
        event.setDispositionAction(action);
        exceptionEventMapper.updateById(event);
        updateDefects(event, "PROCESSING", action);
        createMrbRecord(event, "REVIEW", action, opinion, reviewer, request, now, approvalRequired);

        audit("MRB_REVIEW", event.getEventNo(), "EXCEPTION",
                "MRB复判: action=" + action + ", opinion=" + opinion, reviewer, JSONUtil.toJsonStr(request));
        return exceptionRow(event);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> closeException(String eventNo, Map<String, Object> request) {
        ExceptionEvent event = findException(eventNo);
        if ("CLOSED".equals(event.getStatus())) {
            return exceptionRow(event);
        }

        String action = normalizeDispositionAction(text(request, "dispositionAction",
                text(request, "action", valueOr(event.getDispositionAction(), valueOr(event.getMrbResult(), "RELEASE")))));
        String operator = text(request, "closedBy", text(request, "operator", AuthContext.username()));
        String conclusion = text(request, "closeConclusion", text(request, "disposition", ""));
        if (conclusion.isBlank()) {
            throw new BusinessException("关闭异常必须填写处置结论");
        }
        assertMrbApprovalsReady(event.getEventNo());

        LocalDateTime now = LocalDateTime.now();
        event.setStatus("CLOSED");
        event.setOwnerUser(operator);
        event.setDispositionAction(action);
        event.setRootCause(text(request, "rootCause", event.getRootCause()));
        event.setClosedTime(now);
        event.setCloseConclusion(conclusion);
        if (event.getMrbResult() == null || event.getMrbResult().isBlank()) {
            event.setMrbResult(action);
            event.setMrbOpinion(conclusion);
            event.setMrbReviewer(operator);
            event.setMrbTime(now);
        }
        exceptionEventMapper.updateById(event);
        updateDefects(event, "CLOSED", action);
        createMrbRecord(event, "CLOSE", action, conclusion, operator, request, now, false);

        audit("EXCEPTION_CLOSE", event.getEventNo(), "EXCEPTION",
                "异常关闭: action=" + action + ", conclusion=" + conclusion, operator, JSONUtil.toJsonStr(request));
        return exceptionRow(event);
    }

    public List<Map<String, Object>> defectTopN(int limit) {
        LambdaQueryWrapper<QualityDefectRecord> wrapper = new LambdaQueryWrapper<>();
        applyLotDataScope(wrapper);
        wrapper.orderByDesc(QualityDefectRecord::getCreatedTime)
                .last("LIMIT 1000");
        List<QualityDefectRecord> records = defectRecordMapper.selectList(wrapper);
        Map<String, List<QualityDefectRecord>> grouped = records.stream()
                .collect(Collectors.groupingBy(record -> valueOr(record.getDefectCode(), "UNKNOWN")));
        return grouped.entrySet().stream()
                .map(entry -> {
                    int qty = entry.getValue().stream()
                            .map(QualityDefectRecord::getQty)
                            .filter(Objects::nonNull)
                            .mapToInt(Integer::intValue)
                            .sum();
                    QualityDefectRecord first = entry.getValue().get(0);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("defectCode", entry.getKey());
                    row.put("defectName", valueOr(first.getDefectName(), entry.getKey()));
                    row.put("qty", qty);
                    row.put("level", valueOr(first.getDefectLevel(), "MAJOR"));
                    return row;
                })
                .sorted(Comparator.comparing(row -> -((Integer) row.get("qty"))))
                .limit(Math.max(1, limit))
                .collect(Collectors.toList());
    }

    private Recipe findRecipe(String recipeCode) {
        if (recipeCode == null || recipeCode.isBlank()) {
            return null;
        }
        return recipeMapper.selectOne(new LambdaQueryWrapper<Recipe>().eq(Recipe::getRecipeCode, recipeCode));
    }

    private ExceptionEvent findException(String eventNo) {
        if (eventNo == null || eventNo.isBlank()) {
            throw new BusinessException("异常事件号不能为空");
        }
        LambdaQueryWrapper<ExceptionEvent> wrapper = new LambdaQueryWrapper<ExceptionEvent>()
                .eq(ExceptionEvent::getEventNo, eventNo);
        applyLotDataScope(wrapper);
        ExceptionEvent event = exceptionEventMapper.selectOne(wrapper);
        if (event == null) {
            throw new BusinessException("异常事件不存在或无权限访问: " + eventNo);
        }
        return event;
    }

    private QualityMrbRecord findMrbRecord(String mrbNo) {
        if (mrbNo == null || mrbNo.isBlank()) {
            throw new BusinessException("MRB单号不能为空");
        }
        QualityMrbRecord record = mrbRecordMapper.selectOne(new LambdaQueryWrapper<QualityMrbRecord>()
                .eq(QualityMrbRecord::getMrbNo, mrbNo)
                .last("LIMIT 1"));
        if (record == null) {
            throw new BusinessException("MRB记录不存在: " + mrbNo);
        }
        findException(record.getEventNo());
        return record;
    }

    private <T> void applyLotDataScope(LambdaQueryWrapper<T> wrapper) {
        RolePermissionService.DataScopeCondition condition = rolePermissionService.dataScopeCondition(
                AuthContext.role(), AuthContext.username(), "", "line_code", null, null);
        if (condition != null && !condition.unrestricted()) {
            wrapper.apply("lot_no IN (SELECT lot_no FROM prod_lot WHERE " + condition.sql() + ")",
                    condition.parameters().toArray());
        }
    }

    private void applyApprovalTaskDataScope(LambdaQueryWrapper<QualityMrbApprovalTask> wrapper) {
        RolePermissionService.DataScopeCondition condition = rolePermissionService.dataScopeCondition(
                AuthContext.role(), AuthContext.username(), "", "line_code", null, null);
        if (condition != null && !condition.unrestricted()) {
            wrapper.apply("lot_no IN (SELECT lot_no FROM prod_lot WHERE " + condition.sql() + ")",
                    condition.parameters().toArray());
        }
    }

    private List<RecipeParam> recipeParams(Long recipeId) {
        return recipeParamMapper.selectList(new LambdaQueryWrapper<RecipeParam>()
                .eq(RecipeParam::getRecipeId, recipeId)
                .orderByAsc(RecipeParam::getDisplayOrder));
    }

    private QualityInspection buildInspection(Lot lot, LotStepRecord stepRecord, RecipeParam param, BigDecimal measuredValue, String result) {
        QualityInspection inspection = new QualityInspection();
        inspection.setInspectionNo(nextNo("QI"));
        inspection.setLotNo(lot.getLotNo());
        inspection.setOrderNo(lot.getOrderNo());
        inspection.setProductCode(lot.getProductCode());
        inspection.setStepCode(stepRecord.getStepCode());
        inspection.setEquipmentCode(stepRecord.getEquipmentCode());
        inspection.setRecipeCode(stepRecord.getRecipeCode());
        inspection.setItemCode(valueOr(param.getParamCode(), param.getParamName()));
        inspection.setItemName(valueOr(param.getParamName(), inspection.getItemCode()));
        inspection.setMeasuredValue(measuredValue);
        inspection.setUpperLimit(param.getUpperLimit());
        inspection.setLowerLimit(param.getLowerLimit());
        inspection.setUnit(param.getUnit());
        inspection.setResult(result);
        inspection.setDefectCode("NG".equals(result) ? defectCode(param) : null);
        inspection.setDefectPosition(stepRecord.getStepCode());
        inspection.setInspector("system");
        inspection.setInspectionTime(LocalDateTime.now());
        inspection.setSource("TRACK_OUT");
        inspection.setRemark("Track Out 参数窗口判定");
        return inspection;
    }

    private QualityInspection buildResultInspection(Lot lot, LotStepRecord stepRecord, String result, String remark) {
        QualityInspection inspection = new QualityInspection();
        inspection.setInspectionNo(nextNo("QI"));
        inspection.setLotNo(lot.getLotNo());
        inspection.setOrderNo(lot.getOrderNo());
        inspection.setProductCode(lot.getProductCode());
        inspection.setStepCode(stepRecord.getStepCode());
        inspection.setEquipmentCode(stepRecord.getEquipmentCode());
        inspection.setRecipeCode(stepRecord.getRecipeCode());
        inspection.setItemCode("PROCESS_RESULT");
        inspection.setItemName("出站判定");
        inspection.setResult(result);
        inspection.setDefectCode("NG".equals(result) ? "D-PROCESS-NG" : null);
        inspection.setDefectPosition(stepRecord.getStepCode());
        inspection.setInspector("system");
        inspection.setInspectionTime(LocalDateTime.now());
        inspection.setSource("TRACK_OUT");
        inspection.setRemark(remark);
        return inspection;
    }

    private void createDefect(QualityInspection inspection, String defectCode, String defectName, String level) {
        QualityDefectRecord defect = new QualityDefectRecord();
        defect.setDefectNo(nextNo("QD"));
        defect.setInspectionNo(inspection.getInspectionNo());
        defect.setLotNo(inspection.getLotNo());
        defect.setStepCode(inspection.getStepCode());
        defect.setEquipmentCode(inspection.getEquipmentCode());
        defect.setDefectCode(defectCode);
        defect.setDefectName(defectName);
        defect.setDefectLevel(level);
        defect.setDefectPosition(inspection.getDefectPosition());
        defect.setQty(1);
        defect.setStatus("OPEN");
        defect.setDisposition("WAIT_MRB");
        defect.setCreatedBy("system");
        defectRecordMapper.insert(defect);
    }

    private ExceptionEvent createException(Lot lot, LotStepRecord stepRecord, List<QualityInspection> inspections) {
        ExceptionEvent event = new ExceptionEvent();
        event.setEventNo(nextNo("EX"));
        event.setEventType("QUALITY");
        event.setEventLevel(eventLevel(inspections));
        event.setLotNo(lot.getLotNo());
        event.setOrderNo(lot.getOrderNo());
        event.setStepCode(stepRecord.getStepCode());
        event.setEquipmentCode(stepRecord.getEquipmentCode());
        event.setSourceModule("QUALITY");
        event.setTitle("质量检验不合格");
        event.setDescription(exceptionDescription(inspections));
        event.setStatus("OPEN");
        event.setOwnerRole("QE");
        event.setOccurredTime(LocalDateTime.now());
        event.setCreatedBy("system");
        exceptionEventMapper.insert(event);
        audit("EXCEPTION_CREATE", event.getEventNo(), "EXCEPTION", event.getDescription(), "system", null);
        return event;
    }

    private void autoHold(String lotNo, String holdType, String reason, String operator) {
        Long activeHold = holdRecordMapper.selectCount(new LambdaQueryWrapper<HoldRecord>()
                .eq(HoldRecord::getLotNo, lotNo)
                .eq(HoldRecord::getStatus, "HOLD"));
        if (activeHold != null && activeHold > 0) {
            return;
        }

        Lot latestLot = lotMapper.selectOne(new LambdaQueryWrapper<Lot>().eq(Lot::getLotNo, lotNo));
        if (latestLot == null) {
            return;
        }
        latestLot.setHoldFlag(1);
        latestLot.setStatus("HOLD");
        lotMapper.updateById(latestLot);

        HoldRecord holdRecord = new HoldRecord();
        holdRecord.setLotNo(lotNo);
        holdRecord.setHoldReason(reason);
        holdRecord.setHoldType(holdType);
        holdRecord.setHoldBy(operator);
        holdRecord.setHoldTime(LocalDateTime.now());
        holdRecord.setStatus("HOLD");
        holdRecordMapper.insert(holdRecord);
        audit("LOT_HOLD", lotNo, "LOT", "质量异常自动 Hold: " + reason, operator, null);
    }

    private Map<String, Object> inspectionRow(QualityInspection inspection) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("inspectionNo", inspection.getInspectionNo());
        row.put("lotNo", inspection.getLotNo());
        row.put("orderNo", inspection.getOrderNo());
        row.put("productCode", inspection.getProductCode());
        row.put("stepCode", inspection.getStepCode());
        row.put("equipmentCode", inspection.getEquipmentCode());
        row.put("recipeCode", inspection.getRecipeCode());
        row.put("itemCode", inspection.getItemCode());
        row.put("itemName", inspection.getItemName());
        row.put("measuredValue", inspection.getMeasuredValue());
        row.put("upperLimit", inspection.getUpperLimit());
        row.put("lowerLimit", inspection.getLowerLimit());
        row.put("unit", inspection.getUnit());
        row.put("result", inspection.getResult());
        row.put("defectCode", inspection.getDefectCode());
        row.put("defectPosition", inspection.getDefectPosition());
        row.put("inspector", inspection.getInspector());
        row.put("inspectionTime", inspection.getInspectionTime());
        row.put("source", inspection.getSource());
        row.put("remark", inspection.getRemark());
        return row;
    }

    private Map<String, Object> exceptionRow(ExceptionEvent event) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("eventNo", event.getEventNo());
        row.put("eventType", event.getEventType());
        row.put("eventLevel", event.getEventLevel());
        row.put("lotNo", event.getLotNo());
        row.put("orderNo", event.getOrderNo());
        row.put("stepCode", event.getStepCode());
        row.put("equipmentCode", event.getEquipmentCode());
        row.put("sourceModule", event.getSourceModule());
        row.put("title", event.getTitle());
        row.put("description", event.getDescription());
        row.put("status", event.getStatus());
        row.put("ownerRole", event.getOwnerRole());
        row.put("ownerUser", event.getOwnerUser());
        row.put("occurredTime", event.getOccurredTime());
        row.put("mrbResult", event.getMrbResult());
        row.put("mrbOpinion", event.getMrbOpinion());
        row.put("mrbReviewer", event.getMrbReviewer());
        row.put("mrbTime", event.getMrbTime());
        row.put("dispositionAction", event.getDispositionAction());
        row.put("rootCause", event.getRootCause());
        row.put("closedTime", event.getClosedTime());
        row.put("closeConclusion", event.getCloseConclusion());
        row.put("mrbRecordCount", nvl(mrbRecordMapper.selectCount(new LambdaQueryWrapper<QualityMrbRecord>()
                .eq(QualityMrbRecord::getEventNo, event.getEventNo()))));
        row.put("mrbAttachmentCount", nvl(mrbAttachmentMapper.selectCount(new LambdaQueryWrapper<QualityMrbAttachment>()
                .eq(QualityMrbAttachment::getEventNo, event.getEventNo()))));
        row.put("mrbMinutesCount", nvl(mrbMinutesMapper.selectCount(new LambdaQueryWrapper<QualityMrbMinutes>()
                .eq(QualityMrbMinutes::getEventNo, event.getEventNo()))));
        return row;
    }

    private Map<String, Object> mrbRecordRow(QualityMrbRecord record) {
        List<QualityMrbAttachment> attachments = mrbAttachmentMapper.selectList(
                new LambdaQueryWrapper<QualityMrbAttachment>()
                        .eq(QualityMrbAttachment::getMrbNo, record.getMrbNo())
                        .orderByDesc(QualityMrbAttachment::getUploadedTime)
                        .orderByDesc(QualityMrbAttachment::getId));
        attachments = attachments == null ? List.of() : attachments;
        List<QualityMrbApprovalTask> tasks = mrbApprovalTaskMapper.selectList(
                new LambdaQueryWrapper<QualityMrbApprovalTask>()
                        .eq(QualityMrbApprovalTask::getMrbNo, record.getMrbNo())
                        .orderByAsc(QualityMrbApprovalTask::getDueTime)
                        .orderByAsc(QualityMrbApprovalTask::getId));
        tasks = tasks == null ? List.of() : tasks;
        List<QualityMrbMinutes> minutes = mrbMinutesForRecord(record);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("mrbNo", record.getMrbNo());
        row.put("eventNo", record.getEventNo());
        row.put("lotNo", record.getLotNo());
        row.put("reviewType", record.getReviewType());
        row.put("dispositionAction", record.getDispositionAction());
        row.put("opinion", record.getOpinion());
        row.put("meetingNo", record.getMeetingNo());
        row.put("participants", record.getParticipants());
        row.put("riskLevel", record.getRiskLevel());
        row.put("approvalStatus", record.getApprovalStatus());
        row.put("reviewer", record.getReviewer());
        row.put("reviewTime", record.getReviewTime());
        row.put("attachmentCount", record.getAttachmentCount() == null ? attachments.size() : record.getAttachmentCount());
        row.put("attachments", attachments.stream().map(this::mrbAttachmentRow).collect(Collectors.toList()));
        row.put("approvalTasks", tasks.stream().map(this::mrbApprovalTaskRow).collect(Collectors.toList()));
        row.put("approvalTaskCount", tasks.size());
        row.put("minutesVersionCount", minutes.size());
        row.put("latestMinutes", minutes.isEmpty() ? null : mrbMinutesRow(minutes.get(0)));
        row.put("type", statusType(record.getApprovalStatus()));
        return row;
    }

    private Map<String, Object> mrbAttachmentRow(QualityMrbAttachment attachment) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("attachmentNo", attachment.getAttachmentNo());
        row.put("mrbNo", attachment.getMrbNo());
        row.put("eventNo", attachment.getEventNo());
        row.put("lotNo", attachment.getLotNo());
        row.put("fileName", attachment.getFileName());
        row.put("fileUrl", attachment.getFileUrl());
        row.put("fileHash", attachment.getFileHash());
        row.put("fileType", attachment.getFileType());
        row.put("uploadedBy", attachment.getUploadedBy());
        row.put("uploadedTime", attachment.getUploadedTime());
        return row;
    }

    private Map<String, Object> mrbMinutesRow(QualityMrbMinutes minutes) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("minutesNo", minutes.getMinutesNo());
        row.put("mrbNo", minutes.getMrbNo());
        row.put("eventNo", minutes.getEventNo());
        row.put("lotNo", minutes.getLotNo());
        row.put("versionNo", minutes.getVersionNo());
        row.put("minutesContent", minutes.getMinutesContent());
        row.put("summary", minutes.getSummary());
        row.put("actionItems", minutes.getActionItems());
        row.put("riskNote", minutes.getRiskNote());
        row.put("editor", minutes.getEditor());
        row.put("editTime", minutes.getEditTime());
        row.put("changeReason", minutes.getChangeReason());
        row.put("sourceAction", minutes.getSourceAction());
        return row;
    }

    private Map<String, Object> mrbApprovalTaskRow(QualityMrbApprovalTask task) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("taskNo", task.getTaskNo());
        row.put("mrbNo", task.getMrbNo());
        row.put("eventNo", task.getEventNo());
        row.put("lotNo", task.getLotNo());
        row.put("approvalRole", task.getApprovalRole());
        row.put("approver", task.getApprover());
        row.put("approvalStatus", task.getApprovalStatus());
        row.put("decision", task.getDecision());
        row.put("opinion", task.getOpinion());
        row.put("dueTime", task.getDueTime());
        row.put("actionTime", task.getActionTime());
        row.put("slaLevel", valueOr(task.getSlaLevel(), "STANDARD"));
        row.put("slaHours", task.getSlaHours() == null ? 4 : task.getSlaHours());
        row.put("escalationRole", task.getEscalationRole());
        row.put("escalatedTo", task.getEscalatedTo());
        row.put("escalatedTime", task.getEscalatedTime());
        row.put("escalationReason", task.getEscalationReason());
        row.put("escalationCount", task.getEscalationCount() == null ? 0 : task.getEscalationCount());
        row.put("slaRemainingMinutes", slaRemainingMinutes(task));
        row.put("slaOverdue", slaOverdue(task, LocalDateTime.now()));
        row.put("createdBy", task.getCreatedBy());
        row.put("createdTime", task.getCreatedTime());
        row.put("type", statusType(task.getApprovalStatus()));
        return row;
    }

    private void updateDefects(ExceptionEvent event, String status, String disposition) {
        if (event.getLotNo() == null || event.getLotNo().isBlank()) {
            return;
        }
        List<QualityDefectRecord> defects = defectRecordMapper.selectList(new LambdaQueryWrapper<QualityDefectRecord>()
                .eq(QualityDefectRecord::getLotNo, event.getLotNo())
                .in(QualityDefectRecord::getStatus, List.of("OPEN", "PROCESSING")));
        for (QualityDefectRecord defect : defects) {
            defect.setStatus(status);
            defect.setDisposition(disposition);
            defectRecordMapper.updateById(defect);
        }
    }

    private void createMrbRecord(ExceptionEvent event, String reviewType, String action, String opinion,
                                 String reviewer, Map<String, Object> request, LocalDateTime now,
                                 boolean approvalRequired) {
        QualityMrbRecord record = new QualityMrbRecord();
        record.setMrbNo(nextNo("MRB"));
        record.setEventNo(event.getEventNo());
        record.setLotNo(event.getLotNo());
        record.setReviewType(reviewType);
        record.setDispositionAction(action);
        record.setOpinion(opinion);
        record.setMeetingNo(text(request, "meetingNo", ""));
        record.setParticipants(text(request, "participants", text(request, "attendees", "")));
        record.setRiskLevel(text(request, "riskLevel", valueOr(event.getEventLevel(), "P2")));
        record.setApprovalStatus(approvalRequired ? "PENDING" : normalizeApprovalStatus(text(request, "approvalStatus", "APPROVED")));
        record.setReviewer(reviewer);
        record.setReviewTime(now);
        record.setAttachmentCount(0);
        record.setRequestSnapshot(request == null ? "{}" : JSONUtil.toJsonStr(request));
        record.setCreatedTime(now);
        mrbRecordMapper.insert(record);

        List<QualityMrbAttachment> attachments = createMrbAttachments(record, event, request, reviewer, now);
        List<QualityMrbApprovalTask> tasks = approvalRequired
                ? createApprovalTasks(record, event, request, reviewer, now)
                : List.of();
        record.setAttachmentCount(attachments.size());
        if (!attachments.isEmpty() || !tasks.isEmpty()) {
            mrbRecordMapper.updateById(record);
        }
        createInitialMrbMinutesIfPresent(record, event, reviewType, reviewer, request, now);
    }

    private void createInitialMrbMinutesIfPresent(QualityMrbRecord record, ExceptionEvent event, String sourceAction,
                                                  String editor, Map<String, Object> request, LocalDateTime now) {
        String content = extractMrbMinutesContent(request);
        if (content.isBlank()) {
            return;
        }
        QualityMrbMinutes minutes = createMrbMinutesVersion(record, event, content, editor, sourceAction, request, now);
        audit("MRB_MINUTES_CREATE", minutes.getMinutesNo(), "MRB_MINUTES",
                "MRB会议纪要保存: mrbNo=" + record.getMrbNo() + ", version=" + minutes.getVersionNo(),
                editor, request == null ? "{}" : JSONUtil.toJsonStr(request));
    }

    private QualityMrbMinutes createMrbMinutesVersion(QualityMrbRecord record, ExceptionEvent event, String content,
                                                      String editor, String sourceAction, Map<String, Object> request,
                                                      LocalDateTime now) {
        int versionNo = nextMrbMinutesVersion(record.getMrbNo());
        QualityMrbMinutes minutes = new QualityMrbMinutes();
        minutes.setMinutesNo(nextNo("MRBM"));
        minutes.setMrbNo(record.getMrbNo());
        minutes.setEventNo(event.getEventNo());
        minutes.setLotNo(event.getLotNo());
        minutes.setVersionNo(versionNo);
        minutes.setMinutesContent(content);
        minutes.setSummary(text(request, "minutesSummary", text(request, "summary", "")));
        minutes.setActionItems(text(request, "actionItems", ""));
        minutes.setRiskNote(text(request, "riskNote", ""));
        minutes.setEditor(valueOr(editor, "system"));
        minutes.setEditTime(now);
        minutes.setChangeReason(text(request, "changeReason", versionNo == 1 ? "MRB处置同步生成" : "MRB纪要补充修订"));
        minutes.setSourceAction(sourceAction);
        minutes.setRequestSnapshot(request == null ? "{}" : JSONUtil.toJsonStr(request));
        minutes.setCreatedTime(now);
        mrbMinutesMapper.insert(minutes);
        return minutes;
    }

    private List<QualityMrbMinutes> mrbMinutesForRecord(QualityMrbRecord record) {
        List<QualityMrbMinutes> minutes = mrbMinutesMapper.selectList(new LambdaQueryWrapper<QualityMrbMinutes>()
                .eq(QualityMrbMinutes::getMrbNo, record.getMrbNo())
                .orderByDesc(QualityMrbMinutes::getVersionNo)
                .orderByDesc(QualityMrbMinutes::getEditTime)
                .orderByDesc(QualityMrbMinutes::getId));
        return minutes == null ? List.of() : minutes;
    }

    private int nextMrbMinutesVersion(String mrbNo) {
        List<QualityMrbMinutes> minutes = mrbMinutesMapper.selectList(new LambdaQueryWrapper<QualityMrbMinutes>()
                .eq(QualityMrbMinutes::getMrbNo, valueOr(mrbNo, ""))
                .orderByDesc(QualityMrbMinutes::getVersionNo));
        minutes = minutes == null ? List.of() : minutes;
        return minutes.stream()
                .map(QualityMrbMinutes::getVersionNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private String extractMrbMinutesContent(Map<String, Object> request) {
        String content = text(request, "minutesContent",
                text(request, "meetingMinutes",
                        text(request, "minutes", text(request, "content", ""))));
        return content.trim();
    }

    private List<QualityMrbAttachment> createMrbAttachments(QualityMrbRecord record, ExceptionEvent event,
                                                           Map<String, Object> request, String operator,
                                                           LocalDateTime now) {
        List<QualityMrbAttachment> attachments = new ArrayList<>();
        Object rawAttachments = request == null ? null : request.get("attachments");
        if (rawAttachments instanceof Collection<?> collection) {
            for (Object item : collection) {
                appendMrbAttachment(attachments, record, event, objectMap(item), operator, now);
            }
        }

        Map<String, Object> direct = new LinkedHashMap<>();
        direct.put("fileName", text(request, "fileName", text(request, "attachmentName", "")));
        direct.put("fileUrl", text(request, "fileUrl", text(request, "attachmentUrl", "")));
        direct.put("fileHash", text(request, "fileHash", text(request, "attachmentHash", "")));
        direct.put("fileType", text(request, "fileType", "MRB_EVIDENCE"));
        appendMrbAttachment(attachments, record, event, direct, operator, now);
        return attachments;
    }

    private List<QualityMrbApprovalTask> createApprovalTasks(QualityMrbRecord record, ExceptionEvent event,
                                                             Map<String, Object> request, String operator,
                                                             LocalDateTime now) {
        List<String> roles = approvalRoles(record.getDispositionAction(), record.getRiskLevel(), request);
        List<QualityMrbApprovalTask> tasks = new ArrayList<>();
        for (String role : roles) {
            int slaHours = approvalSlaHours(role, record.getDispositionAction(), record.getRiskLevel(), request);
            QualityMrbApprovalTask task = new QualityMrbApprovalTask();
            task.setTaskNo(nextNo("MRBT"));
            task.setMrbNo(record.getMrbNo());
            task.setEventNo(event.getEventNo());
            task.setLotNo(event.getLotNo());
            task.setApprovalRole(role);
            task.setApprovalStatus("PENDING");
            task.setSlaLevel(approvalSlaLevel(record.getDispositionAction(), record.getRiskLevel(), slaHours));
            task.setSlaHours(slaHours);
            task.setDueTime(now.plusHours(slaHours));
            task.setEscalationRole(defaultEscalationRole(role));
            task.setEscalationCount(0);
            task.setCreatedBy(operator);
            task.setCreatedTime(now);
            task.setUpdatedTime(now);
            mrbApprovalTaskMapper.insert(task);
            tasks.add(task);
        }
        return tasks;
    }

    private int approvalSlaHours(String role, String action, String riskLevel, Map<String, Object> request) {
        String normalizedRole = valueOr(role, "").toUpperCase(Locale.ROOT);
        Object roleOverride = request == null ? null : request.get(normalizedRole.toLowerCase(Locale.ROOT) + "SlaHours");
        Object globalOverride = request == null ? null : request.get("approvalSlaHours");
        if (roleOverride != null) {
            return Math.max(1, intValue(roleOverride, 4));
        }
        if (globalOverride != null) {
            return Math.max(1, intValue(globalOverride, 4));
        }
        String normalizedAction = valueOr(action, "").toUpperCase(Locale.ROOT);
        String normalizedRisk = valueOr(riskLevel, "P2").toUpperCase(Locale.ROOT);
        if ("SCRAP".equals(normalizedAction)) {
            return switch (normalizedRole) {
                case "QE" -> 1;
                case "PE", "EE" -> 2;
                default -> 4;
            };
        }
        if ("P1".equals(normalizedRisk)) {
            return switch (normalizedRole) {
                case "QE" -> 2;
                case "PE", "EE" -> 3;
                default -> 4;
            };
        }
        if ("REWORK".equals(normalizedAction)) {
            return switch (normalizedRole) {
                case "QE" -> 3;
                case "PE" -> 4;
                default -> 6;
            };
        }
        return 4;
    }

    private String approvalSlaLevel(String action, String riskLevel, int slaHours) {
        String normalizedAction = valueOr(action, "").toUpperCase(Locale.ROOT);
        String normalizedRisk = valueOr(riskLevel, "P2").toUpperCase(Locale.ROOT);
        if ("SCRAP".equals(normalizedAction) || "P1".equals(normalizedRisk) || slaHours <= 2) {
            return "CRITICAL";
        }
        if ("REWORK".equals(normalizedAction) || slaHours <= 4) {
            return "STANDARD";
        }
        return "NORMAL";
    }

    private String defaultEscalationRole(String approvalRole) {
        return switch (valueOr(approvalRole, "").toUpperCase(Locale.ROOT)) {
            case "QE" -> "QUALITY_MANAGER";
            case "PE" -> "PROCESS_MANAGER";
            case "EE" -> "EQUIPMENT_MANAGER";
            default -> "MRB_CHAIR";
        };
    }

    private String defaultEscalatedTo(String escalationRole) {
        return switch (valueOr(escalationRole, "").toUpperCase(Locale.ROOT)) {
            case "QUALITY_MANAGER" -> "qm1001";
            case "PROCESS_MANAGER" -> "pm1001";
            case "EQUIPMENT_MANAGER" -> "em1001";
            default -> "mrb-chair";
        };
    }

    private List<String> approvalRoles(String action, String riskLevel, Map<String, Object> request) {
        Object raw = request == null ? null : request.get("approvalRoles");
        List<String> roles = new ArrayList<>();
        if (raw instanceof Collection<?> collection) {
            collection.forEach(item -> addApprovalRole(roles, item));
        } else if (raw != null && !String.valueOf(raw).isBlank()) {
            for (String item : String.valueOf(raw).split("[,;\\s]+")) {
                addApprovalRole(roles, item);
            }
        }
        if (!roles.isEmpty()) {
            return List.copyOf(roles);
        }
        addApprovalRole(roles, "QE");
        if ("REWORK".equals(action) || "SCRAP".equals(action) || "P1".equals(riskLevel)) {
            addApprovalRole(roles, "PE");
        }
        if ("SCRAP".equals(action) || "P1".equals(riskLevel)) {
            addApprovalRole(roles, "EE");
        }
        return List.copyOf(roles);
    }

    private void addApprovalRole(List<String> roles, Object value) {
        if (value == null) {
            return;
        }
        String role = valueOr(String.valueOf(value), "").trim().toUpperCase(Locale.ROOT);
        if (!role.isBlank() && !roles.contains(role)) {
            roles.add(role);
        }
    }

    private boolean approvalRequired(ExceptionEvent event, String action, Map<String, Object> request) {
        if (booleanValue(request == null ? null : request.get("approvalRequired"))) {
            return true;
        }
        String riskLevel = text(request, "riskLevel", valueOr(event.getEventLevel(), "P2")).toUpperCase(Locale.ROOT);
        return "P1".equals(riskLevel) || "REWORK".equals(action) || "SCRAP".equals(action);
    }

    private void refreshMrbApprovalStatus(String mrbNo) {
        QualityMrbRecord record = mrbRecordMapper.selectOne(new LambdaQueryWrapper<QualityMrbRecord>()
                .eq(QualityMrbRecord::getMrbNo, mrbNo)
                .last("LIMIT 1"));
        if (record == null) {
            return;
        }
        List<QualityMrbApprovalTask> tasks = mrbApprovalTaskMapper.selectList(new LambdaQueryWrapper<QualityMrbApprovalTask>()
                .eq(QualityMrbApprovalTask::getMrbNo, mrbNo));
        tasks = tasks == null ? List.of() : tasks;
        if (tasks.isEmpty()) {
            return;
        }
        String status;
        if (tasks.stream().anyMatch(task -> "REJECTED".equals(task.getApprovalStatus()))) {
            status = "REJECTED";
        } else if (tasks.stream().anyMatch(this::approvalStillOpen)) {
            status = "PENDING";
        } else {
            status = "APPROVED";
        }
        record.setApprovalStatus(status);
        mrbRecordMapper.updateById(record);

        ExceptionEvent event = exceptionEventMapper.selectOne(new LambdaQueryWrapper<ExceptionEvent>()
                .eq(ExceptionEvent::getEventNo, record.getEventNo())
                .last("LIMIT 1"));
        if (event != null && "MRB_PENDING".equals(event.getStatus())) {
            event.setStatus("APPROVED".equals(status) ? "MRB_REVIEWED" : "REJECTED".equals(status) ? "MRB_REJECTED" : "MRB_PENDING");
            exceptionEventMapper.updateById(event);
        }
    }

    private void assertMrbApprovalsReady(String eventNo) {
        List<QualityMrbApprovalTask> tasks = mrbApprovalTaskMapper.selectList(new LambdaQueryWrapper<QualityMrbApprovalTask>()
                .eq(QualityMrbApprovalTask::getEventNo, eventNo));
        tasks = tasks == null ? List.of() : tasks;
        boolean hasPending = tasks.stream().anyMatch(this::approvalStillOpen);
        if (hasPending) {
            throw new BusinessException("MRB会签未完成，不能关闭异常: " + eventNo);
        }
        boolean hasRejected = tasks.stream().anyMatch(task -> "REJECTED".equals(task.getApprovalStatus()));
        if (hasRejected) {
            throw new BusinessException("MRB会签已驳回，不能关闭异常: " + eventNo);
        }
    }

    private void appendMrbAttachment(List<QualityMrbAttachment> attachments, QualityMrbRecord record,
                                     ExceptionEvent event, Map<String, Object> source,
                                     String operator, LocalDateTime now) {
        String fileName = text(source, "fileName", text(source, "name", ""));
        String fileUrl = text(source, "fileUrl", text(source, "url", ""));
        String fileHash = text(source, "fileHash", text(source, "hash", ""));
        if (fileName.isBlank() && fileUrl.isBlank() && fileHash.isBlank()) {
            return;
        }
        if (fileName.isBlank()) {
            fileName = !fileUrl.isBlank() ? fileUrl : fileHash;
        }

        QualityMrbAttachment attachment = new QualityMrbAttachment();
        attachment.setAttachmentNo(nextNo("MRBA"));
        attachment.setMrbNo(record.getMrbNo());
        attachment.setEventNo(event.getEventNo());
        attachment.setLotNo(event.getLotNo());
        attachment.setFileName(fileName);
        attachment.setFileUrl(fileUrl);
        attachment.setFileHash(fileHash);
        attachment.setFileType(text(source, "fileType", text(source, "type", "MRB_EVIDENCE")));
        attachment.setUploadedBy(text(source, "uploadedBy", operator));
        attachment.setUploadedTime(now);
        attachment.setCreatedTime(now);
        mrbAttachmentMapper.insert(attachment);
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

    private QualityMrbApprovalTask findApprovalTask(String taskNo) {
        if (taskNo == null || taskNo.isBlank()) {
            throw new BusinessException("MRB会签任务号不能为空");
        }
        LambdaQueryWrapper<QualityMrbApprovalTask> wrapper = new LambdaQueryWrapper<QualityMrbApprovalTask>()
                .eq(QualityMrbApprovalTask::getTaskNo, taskNo);
        applyApprovalTaskDataScope(wrapper);
        QualityMrbApprovalTask task = mrbApprovalTaskMapper.selectOne(wrapper);
        if (task == null) {
            throw new BusinessException("MRB会签任务不存在或无权限访问: " + taskNo);
        }
        return task;
    }

    private Map<String, Object> parseParams(String processParams) {
        if (processParams == null || processParams.isBlank()) {
            return Map.of();
        }
        try {
            JSONObject object = JSONUtil.parseObj(processParams);
            Map<String, Object> values = new LinkedHashMap<>();
            object.forEach(values::put);
            return values;
        } catch (Exception e) {
            log.warn("过程参数不是有效 JSON，跳过参数窗口判定: {}", processParams);
            return Map.of();
        }
    }

    private Object findMeasuredValue(Map<String, Object> values, RecipeParam param) {
        if (values.isEmpty()) {
            return null;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        values.forEach((key, value) -> normalized.put(normalizeKey(key), value));
        for (String candidate : paramCandidates(param)) {
            Object value = normalized.get(normalizeKey(candidate));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private List<String> paramCandidates(RecipeParam param) {
        List<String> candidates = new ArrayList<>();
        candidates.add(param.getParamCode());
        candidates.add(param.getParamName());
        candidates.add(param.getParamType());
        String code = valueOr(param.getParamCode(), "").toUpperCase(Locale.ROOT);
        String type = valueOr(param.getParamType(), "").toUpperCase(Locale.ROOT);
        if (code.contains("TEMP") || type.contains("TEMPERATURE")) {
            candidates.add("temperature");
            candidates.add("temp");
        }
        if (code.contains("SPEED") || type.contains("SPEED")) {
            candidates.add("speed");
        }
        if (code.contains("THICKNESS") || type.contains("DIMENSION")) {
            candidates.add("thickness");
            candidates.add("filmThickness");
        }
        if (code.contains("VACUUM") || type.contains("PRESSURE")) {
            candidates.add("vacuum");
            candidates.add("pressure");
        }
        return candidates.stream().filter(Objects::nonNull).collect(Collectors.toList());
    }

    private BigDecimal decimalValue(Object value) {
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String judgeParam(RecipeParam param, BigDecimal measuredValue) {
        if (measuredValue == null) {
            return "NG";
        }
        if (param.getUpperLimit() != null && measuredValue.compareTo(param.getUpperLimit()) > 0) {
            return "NG";
        }
        if (param.getLowerLimit() != null && measuredValue.compareTo(param.getLowerLimit()) < 0) {
            return "NG";
        }
        return "OK";
    }

    private String normalizeResult(String result) {
        return "NG".equalsIgnoreCase(valueOr(result, "OK")) ? "NG" : "OK";
    }

    private String normalizeDispositionAction(String action) {
        String normalized = valueOr(action, "CONTINUE_HOLD").trim().toUpperCase(Locale.ROOT);
        normalized = switch (normalized) {
            case "PASS", "OK", "RELEASE_LOT" -> "RELEASE";
            case "返工", "REWORK_LOT" -> "REWORK";
            case "报废", "SCRAP_LOT" -> "SCRAP";
            case "HOLD", "WAIT", "WAIT_MRB", "CONTINUE" -> "CONTINUE_HOLD";
            default -> normalized;
        };
        if (!DISPOSITION_ACTIONS.contains(normalized)) {
            throw new BusinessException("不支持的MRB处置动作: " + action);
        }
        return normalized;
    }

    private boolean approvalStillOpen(QualityMrbApprovalTask task) {
        return task != null && ("PENDING".equals(task.getApprovalStatus()) || "ESCALATED".equals(task.getApprovalStatus()));
    }

    private boolean slaOverdue(QualityMrbApprovalTask task, LocalDateTime now) {
        return approvalStillOpen(task) && task.getDueTime() != null && !task.getDueTime().isAfter(now);
    }

    private Long slaRemainingMinutes(QualityMrbApprovalTask task) {
        if (!approvalStillOpen(task) || task.getDueTime() == null) {
            return null;
        }
        return Duration.between(LocalDateTime.now(), task.getDueTime()).toMinutes();
    }

    private String normalizeApprovalStatus(String status) {
        String normalized = valueOr(status, "APPROVED").trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PASS", "OK", "APPROVE", "APPROVED" -> "APPROVED";
            case "REJECT", "REJECTED" -> "REJECTED";
            case "PENDING", "WAIT", "WAIT_APPROVAL" -> "PENDING";
            case "ESCALATE", "ESCALATED", "OVERDUE" -> "ESCALATED";
            default -> throw new BusinessException("不支持的MRB审批状态: " + status);
        };
    }

    private String normalizeApprovalDecision(String decision) {
        String normalized = valueOr(decision, "APPROVE").trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PASS", "OK", "APPROVED", "APPROVE" -> "APPROVE";
            case "NG", "REJECTED", "REJECT" -> "REJECT";
            default -> throw new BusinessException("不支持的MRB会签结论: " + decision);
        };
    }

    private String statusType(String status) {
        return switch (valueOr(status, "")) {
            case "APPROVED", "RELEASE", "CLOSED", "OK" -> "green";
            case "PENDING", "PROCESSING", "CONTINUE_HOLD", "REWORK" -> "amber";
            case "ESCALATED" -> "red";
            case "REJECTED", "SCRAP", "NG" -> "red";
            default -> "gray";
        };
    }

    private String defaultMrbOpinion(String action) {
        return switch (action) {
            case "RELEASE" -> "复判通过，允许解除 Hold 并继续流转";
            case "REWORK" -> "复判不通过，建议返工处理";
            case "SCRAP" -> "缺陷不可恢复，建议报废";
            default -> "继续 Hold，等待补充分析";
        };
    }

    private String defectCode(RecipeParam param) {
        String code = valueOr(param.getParamCode(), "").toUpperCase(Locale.ROOT);
        if (code.contains("THICKNESS")) {
            return "D-THICKNESS";
        }
        if (code.contains("VACUUM")) {
            return "D-VACUUM";
        }
        if (code.contains("TEMP")) {
            return "D-TEMP";
        }
        return "D-PARAM-OOC";
    }

    private String defectName(RecipeParam param) {
        String name = valueOr(param.getParamName(), valueOr(param.getParamCode(), "参数"));
        return name + "超限";
    }

    private String eventLevel(List<QualityInspection> inspections) {
        boolean hasNg = inspections.stream().anyMatch(inspection -> "NG".equals(inspection.getResult()));
        return hasNg ? "P1" : "P2";
    }

    private String exceptionDescription(List<QualityInspection> inspections) {
        return inspections.stream()
                .filter(inspection -> "NG".equals(inspection.getResult()))
                .findFirst()
                .map(inspection -> inspection.getLotNo() + " 在 " + inspection.getStepCode()
                        + " 工序 " + inspection.getItemName() + " 判定不合格")
                .orElse("质量检验不合格");
    }

    private String normalizeKey(String key) {
        return valueOr(key, "")
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "")
                .toLowerCase(Locale.ROOT);
    }

    private String nextNo(String prefix) {
        long seq = NO_COUNTER.updateAndGet(value -> value >= 9999 ? 1 : value + 1);
        return prefix + "-" + NO_TIME.format(LocalDateTime.now()) + "-" + String.format("%04d", seq);
    }

    private long nvl(Long value) {
        return value == null ? 0L : value;
    }

    private long longValue(Object value, long fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int intValue(Object value, int fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private boolean booleanValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return "true".equals(text) || "1".equals(text) || "yes".equals(text) || "y".equals(text);
    }

    private void audit(String action, String bizNo, String bizType, String description, String operator, String requestSnapshot) {
        try {
            auditLogService.record(action, bizNo, bizType, description, operator, "quality-service", requestSnapshot);
        } catch (Exception e) {
            log.warn("质量审计日志写入失败，已降级不阻断主流程: action={}, bizNo={}, reason={}", action, bizNo, e.getMessage());
        }
    }

    private String text(Map<String, Object> request, String key, String defaultValue) {
        Object value = request == null ? null : request.get(key);
        return value == null || String.valueOf(value).isBlank() ? valueOr(defaultValue, "") : String.valueOf(value);
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
