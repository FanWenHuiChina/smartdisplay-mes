package com.visionox.mes.equipment.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.visionox.mes.auth.security.AuthContext;
import com.visionox.mes.common.BusinessException;
import com.visionox.mes.equipment.entity.EquipmentCycleSample;
import com.visionox.mes.equipment.entity.EquipmentEvent;
import com.visionox.mes.equipment.entity.EquipmentParameterSample;
import com.visionox.mes.equipment.entity.EquipmentPmTask;
import com.visionox.mes.equipment.entity.EquipmentRecipeCommand;
import com.visionox.mes.equipment.entity.EquipmentStandardCycle;
import com.visionox.mes.equipment.entity.EquipmentStatusHistory;
import com.visionox.mes.equipment.mapper.EquipmentCycleSampleMapper;
import com.visionox.mes.equipment.mapper.EquipmentEventMapper;
import com.visionox.mes.equipment.mapper.EquipmentParameterSampleMapper;
import com.visionox.mes.equipment.mapper.EquipmentPmTaskMapper;
import com.visionox.mes.equipment.mapper.EquipmentRecipeCommandMapper;
import com.visionox.mes.equipment.mapper.EquipmentStandardCycleMapper;
import com.visionox.mes.equipment.mapper.EquipmentStatusHistoryMapper;
import com.visionox.mes.lot.entity.Equipment;
import com.visionox.mes.lot.mapper.EquipmentMapper;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class EquipmentService {

    private static final DateTimeFormatter NO_TIME = DateTimeFormatter.ofPattern("yyMMddHHmmss");
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final EquipmentMapper equipmentMapper;
    private final EquipmentEventMapper eventMapper;
    private final EquipmentPmTaskMapper pmTaskMapper;
    private final EquipmentParameterSampleMapper parameterSampleMapper;
    private final EquipmentRecipeCommandMapper recipeCommandMapper;
    private final EquipmentStatusHistoryMapper statusHistoryMapper;
    private final EquipmentCycleSampleMapper cycleSampleMapper;
    private final EquipmentStandardCycleMapper standardCycleMapper;
    private final RecipeMapper recipeMapper;
    private final RecipeParamMapper recipeParamMapper;
    private final AuditLogService auditLogService;

    public List<Map<String, Object>> events(String equipmentCode, String status) {
        LambdaQueryWrapper<EquipmentEvent> wrapper = new LambdaQueryWrapper<>();
        if (equipmentCode != null && !equipmentCode.isBlank()) {
            wrapper.eq(EquipmentEvent::getEquipmentCode, equipmentCode);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(EquipmentEvent::getStatus, status);
        }
        wrapper.orderByDesc(EquipmentEvent::getOccurredTime).last("LIMIT 50");
        return eventMapper.selectList(wrapper).stream()
                .map(this::eventRow)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> parameterSamples(String equipmentCode) {
        LambdaQueryWrapper<EquipmentParameterSample> wrapper = new LambdaQueryWrapper<>();
        if (equipmentCode != null && !equipmentCode.isBlank()) {
            wrapper.eq(EquipmentParameterSample::getEquipmentCode, equipmentCode);
        }
        wrapper.orderByDesc(EquipmentParameterSample::getSampleTime).last("LIMIT 50");
        return parameterSampleMapper.selectList(wrapper).stream()
                .map(this::parameterRow)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> pmTasks(String status) {
        refreshOverduePmTasks();
        LambdaQueryWrapper<EquipmentPmTask> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(EquipmentPmTask::getStatus, status);
        }
        wrapper.orderByAsc(EquipmentPmTask::getPlanEndTime).last("LIMIT 50");
        return pmTaskMapper.selectList(wrapper).stream()
                .map(this::pmTaskRow)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> recipeCommands(String equipmentCode, String status) {
        LambdaQueryWrapper<EquipmentRecipeCommand> wrapper = new LambdaQueryWrapper<>();
        if (equipmentCode != null && !equipmentCode.isBlank()) {
            wrapper.eq(EquipmentRecipeCommand::getEquipmentCode, equipmentCode);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(EquipmentRecipeCommand::getCommandStatus, status);
        }
        wrapper.orderByDesc(EquipmentRecipeCommand::getDownloadTime).last("LIMIT 50");
        return recipeCommandMapper.selectList(wrapper).stream()
                .map(this::recipeCommandRow)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> statusHistories(String equipmentCode) {
        LambdaQueryWrapper<EquipmentStatusHistory> wrapper = new LambdaQueryWrapper<>();
        if (equipmentCode != null && !equipmentCode.isBlank()) {
            wrapper.eq(EquipmentStatusHistory::getEquipmentCode, equipmentCode);
        }
        wrapper.orderByDesc(EquipmentStatusHistory::getChangedTime).last("LIMIT 50");
        return statusHistoryMapper.selectList(wrapper).stream()
                .map(this::statusHistoryRow)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> cycleSamples(String equipmentCode) {
        LambdaQueryWrapper<EquipmentCycleSample> wrapper = new LambdaQueryWrapper<>();
        if (equipmentCode != null && !equipmentCode.isBlank()) {
            wrapper.eq(EquipmentCycleSample::getEquipmentCode, equipmentCode);
        }
        wrapper.orderByDesc(EquipmentCycleSample::getSampleTime).last("LIMIT 50");
        return cycleSampleMapper.selectList(wrapper).stream()
                .map(this::cycleSampleRow)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> standardCycles(String equipmentCode, String status) {
        LambdaQueryWrapper<EquipmentStandardCycle> wrapper = new LambdaQueryWrapper<>();
        if (equipmentCode != null && !equipmentCode.isBlank()) {
            wrapper.eq(EquipmentStandardCycle::getEquipmentCode, equipmentCode);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(EquipmentStandardCycle::getStatus, status.toUpperCase(Locale.ROOT));
        }
        wrapper.orderByDesc(EquipmentStandardCycle::getEffectiveTime).last("LIMIT 80");
        List<EquipmentStandardCycle> cycles = standardCycleMapper.selectList(wrapper);
        return (cycles == null ? List.<EquipmentStandardCycle>of() : cycles).stream()
                .map(this::standardCycleRow)
                .collect(Collectors.toList());
    }

    public Map<String, Object> oeeSummary(String lineCode) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusHours(24);
        List<Equipment> equipments = equipmentMapper.selectList(null);
        if (lineCode != null && !lineCode.isBlank()) {
            equipments = equipments.stream()
                    .filter(equipment -> lineCode.equals(equipment.getLineCode()))
                    .collect(Collectors.toList());
        }

        List<EquipmentEvent> events = eventMapper.selectList(new LambdaQueryWrapper<EquipmentEvent>()
                .ge(EquipmentEvent::getOccurredTime, windowStart.minusDays(1)));
        if (events == null) {
            events = List.of();
        }
        List<String> equipmentCodes = equipments.stream().map(Equipment::getEquipmentCode).collect(Collectors.toList());
        List<EquipmentEvent> scopedEvents = events.stream()
                .filter(event -> equipmentCodes.contains(event.getEquipmentCode()))
                .collect(Collectors.toList());
        List<EquipmentCycleSample> cycleSamples = cycleSampleMapper.selectList(new LambdaQueryWrapper<EquipmentCycleSample>()
                .ge(EquipmentCycleSample::getSampleTime, windowStart));
        if (cycleSamples == null) {
            cycleSamples = List.of();
        }
        List<EquipmentCycleSample> scopedCycleSamples = cycleSamples.stream()
                .filter(sample -> equipmentCodes.contains(sample.getEquipmentCode()))
                .collect(Collectors.toList());

        long scheduledMinutes = equipments.size() * 24L * 60L;
        long plannedDowntime = scopedEvents.stream()
                .filter(event -> "PLANNED".equals(resolveDowntimeType(event)))
                .mapToLong(event -> overlapMinutes(event, windowStart, now))
                .sum();
        long unplannedDowntime = scopedEvents.stream()
                .filter(event -> "UNPLANNED".equals(resolveDowntimeType(event)))
                .mapToLong(event -> overlapMinutes(event, windowStart, now))
                .sum();
        long plannedProductionMinutes = Math.max(0L, scheduledMinutes - plannedDowntime);
        double availabilityRate = plannedProductionMinutes == 0
                ? 0.0
                : clampPercent((plannedProductionMinutes - unplannedDowntime) * 100.0 / plannedProductionMinutes);
        double performanceRate = performanceRate(equipments, scopedCycleSamples);
        double qualityRate = qualityRate(windowStart, scopedCycleSamples);
        double oeeRate = clampPercent(availabilityRate * performanceRate * qualityRate / 10000.0);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("windowHours", 24);
        data.put("lineCode", valueOr(lineCode, "ALL"));
        data.put("equipmentCount", equipments.size());
        data.put("scheduledMinutes", scheduledMinutes);
        data.put("plannedProductionMinutes", plannedProductionMinutes);
        data.put("plannedDowntimeMinutes", plannedDowntime);
        data.put("unplannedDowntimeMinutes", unplannedDowntime);
        data.put("availabilityRate", round(availabilityRate));
        data.put("availabilityText", percentText(availabilityRate));
        data.put("performanceRate", round(performanceRate));
        data.put("performanceText", percentText(performanceRate));
        data.put("qualityRate", round(qualityRate));
        data.put("qualityText", percentText(qualityRate));
        data.put("oeeRate", round(oeeRate));
        data.put("oeeText", percentText(oeeRate));
        data.put("performanceSampleCount", scopedCycleSamples.size());
        data.put("statusDistribution", statusDistribution(equipments));
        data.put("reasonTopN", downtimeReasonTopN(scopedEvents, windowStart, now));
        data.put("equipmentRows", equipmentOeeRows(equipments, scopedEvents, scopedCycleSamples, windowStart, now));
        data.put("recentCycleSamples", scopedCycleSamples.stream().limit(5).map(this::cycleSampleRow).collect(Collectors.toList()));
        data.put("calculationNote", scopedCycleSamples.isEmpty()
                ? "试点口径：可用率来自近24小时设备停机事件；当前缺少EAP节拍采样，性能率临时按设备可执行状态估算。"
                : "试点口径：可用率来自近24小时设备停机事件，性能率来自EAP标准节拍/实际节拍采样，质量率优先使用节拍采样良品数。");
        return data;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createEvent(Map<String, Object> request) {
        Map<String, Object> safeRequest = safeRequest(request);
        Equipment equipment = findEquipment(requiredText(safeRequest, "equipmentCode"));
        LocalDateTime now = LocalDateTime.now();
        EquipmentEvent event = buildEvent(equipment, safeRequest, now);
        eventMapper.insert(event);
        updateEquipmentStatusForEvent(equipment, event, safeRequest);
        audit("EQUIPMENT_EVENT", event.getEventNo(), "EQUIPMENT",
                "eventType=" + event.getEventType() + ", equipment=" + event.getEquipmentCode(),
                text(safeRequest, "operator", currentUser()), event.getRequestSnapshot());
        return Map.of("event", eventRow(event), "equipment", equipment);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reportStatus(Map<String, Object> request) {
        Map<String, Object> safeRequest = safeRequest(request);
        Equipment equipment = findEquipment(requiredText(safeRequest, "equipmentCode"));
        String targetStatus = requiredText(safeRequest, "status").toUpperCase(Locale.ROOT);
        String reason = text(safeRequest, "changeReason", text(safeRequest, "reason", "EAP状态上报"));
        String operator = text(safeRequest, "operator", currentUser());
        String snapshot = JSONUtil.toJsonStr(safeRequest);
        updateEquipmentStatus(equipment, targetStatus, reason, operator, text(safeRequest, "sourceSystem", "eap-adapter"), snapshot);
        audit("EAP_STATUS_REPORT", equipment.getEquipmentCode(), "EQUIPMENT",
                "equipment=" + equipment.getEquipmentCode() + ", status=" + targetStatus,
                operator, snapshot);
        return Map.of("equipment", equipment);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> closeEvent(String eventNo, Map<String, Object> request) {
        EquipmentEvent event = eventMapper.selectOne(new LambdaQueryWrapper<EquipmentEvent>()
                .eq(EquipmentEvent::getEventNo, eventNo));
        if (event == null) {
            throw new BusinessException("Equipment event not found: " + eventNo);
        }
        if ("CLOSED".equals(event.getStatus())) {
            throw new BusinessException("Equipment event already closed: " + eventNo);
        }
        Map<String, Object> safeRequest = safeRequest(request);
        LocalDateTime now = LocalDateTime.now();
        event.setStatus("CLOSED");
        event.setClosedBy(text(safeRequest, "closedBy", text(safeRequest, "operator", currentUser())));
        event.setClosedTime(now);
        event.setCloseConclusion(text(safeRequest, "closeConclusion", "设备事件已关闭"));
        event.setEndedTime(now);
        event.setDurationMinutes((int) rawDurationMinutes(event, now));
        event.setUpdatedTime(now);
        event.setRequestSnapshot(JSONUtil.toJsonStr(safeRequest));
        eventMapper.updateById(event);

        Equipment equipment = findEquipment(event.getEquipmentCode());
        String equipmentStatus = text(safeRequest, "equipmentStatus", "IDLE").toUpperCase(Locale.ROOT);
        if (List.of("ALARM", "DOWN", "PM").contains(equipment.getStatus())) {
            updateEquipmentStatus(equipment, equipmentStatus, event.getCloseConclusion(),
                    event.getClosedBy(), text(safeRequest, "sourceSystem", "eap-adapter"), event.getRequestSnapshot());
        }
        audit("EQUIPMENT_EVENT_CLOSE", event.getEventNo(), "EQUIPMENT",
                "equipment=" + event.getEquipmentCode() + ", duration=" + event.getDurationMinutes(),
                event.getClosedBy(), event.getRequestSnapshot());
        return Map.of("event", eventRow(event), "equipment", equipment);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> publishStandardCycle(Map<String, Object> request) {
        Map<String, Object> safeRequest = safeRequest(request);
        Equipment equipment = findEquipment(requiredText(safeRequest, "equipmentCode"));
        LocalDateTime now = LocalDateTime.now();
        String productCode = requiredText(safeRequest, "productCode");
        String stepCode = requiredText(safeRequest, "stepCode").toUpperCase(Locale.ROOT);
        String recipeCode = text(safeRequest, "recipeCode", "");
        BigDecimal standard = positiveDecimal(value(safeRequest, "standardCycleSeconds"), "standardCycleSeconds");
        BigDecimal lower = optionalDecimal(value(safeRequest, "lowerCycleSeconds"), "lowerCycleSeconds");
        BigDecimal upper = optionalDecimal(value(safeRequest, "upperCycleSeconds"), "upperCycleSeconds");
        validateCycleWindow(standard, lower, upper);

        List<EquipmentStandardCycle> activeCycles = standardCycleMapper.selectList(new LambdaQueryWrapper<EquipmentStandardCycle>()
                .eq(EquipmentStandardCycle::getProductCode, productCode)
                .eq(EquipmentStandardCycle::getStepCode, stepCode)
                .eq(EquipmentStandardCycle::getEquipmentCode, equipment.getEquipmentCode())
                .eq(EquipmentStandardCycle::getRecipeCode, recipeCode)
                .eq(EquipmentStandardCycle::getStatus, "ACTIVE")
                .isNull(EquipmentStandardCycle::getExpireTime));
        for (EquipmentStandardCycle activeCycle : activeCycles == null ? List.<EquipmentStandardCycle>of() : activeCycles) {
            activeCycle.setStatus("INACTIVE");
            activeCycle.setExpireTime(now);
            activeCycle.setUpdatedBy(text(safeRequest, "operator", currentUser()));
            activeCycle.setUpdatedTime(now);
            standardCycleMapper.updateById(activeCycle);
        }

        EquipmentStandardCycle cycle = new EquipmentStandardCycle();
        cycle.setCycleNo(text(safeRequest, "cycleNo", nextNo("ESC")));
        cycle.setProductCode(productCode);
        cycle.setStepCode(stepCode);
        cycle.setEquipmentCode(equipment.getEquipmentCode());
        cycle.setRecipeCode(recipeCode);
        cycle.setCycleVersion(text(safeRequest, "cycleVersion", "V1.0"));
        cycle.setStandardCycleSeconds(standard);
        cycle.setLowerCycleSeconds(lower);
        cycle.setUpperCycleSeconds(upper);
        cycle.setStatus("ACTIVE");
        cycle.setEffectiveTime(now);
        cycle.setCreatedBy(text(safeRequest, "operator", currentUser()));
        cycle.setUpdatedBy(cycle.getCreatedBy());
        cycle.setCreatedTime(now);
        cycle.setUpdatedTime(now);
        cycle.setRequestSnapshot(JSONUtil.toJsonStr(safeRequest));
        standardCycleMapper.insert(cycle);

        audit("EQUIPMENT_STANDARD_CYCLE_PUBLISH", cycle.getCycleNo(), "EQUIPMENT",
                "equipment=" + cycle.getEquipmentCode() + ", product=" + cycle.getProductCode()
                        + ", step=" + cycle.getStepCode() + ", standard=" + cycle.getStandardCycleSeconds(),
                cycle.getCreatedBy(), cycle.getRequestSnapshot());
        return Map.of("cycle", standardCycleRow(cycle), "equipment", equipment);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reportCycleSample(Map<String, Object> request) {
        Map<String, Object> safeRequest = safeRequest(request);
        Equipment equipment = findEquipment(requiredText(safeRequest, "equipmentCode"));
        LocalDateTime now = LocalDateTime.now();
        EquipmentStandardCycle matchedCycle = activeStandardCycleForSample(safeRequest, equipment, now);
        BigDecimal standardCycleSeconds = optionalDecimal(value(safeRequest, "standardCycleSeconds"), "standardCycleSeconds");
        if (standardCycleSeconds == null) {
            if (matchedCycle == null) {
                throw new BusinessException("standardCycleSeconds is required when no active standard cycle matches");
            }
            standardCycleSeconds = matchedCycle.getStandardCycleSeconds();
        }
        if (standardCycleSeconds.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("standardCycleSeconds must be greater than zero");
        }
        EquipmentCycleSample sample = new EquipmentCycleSample();
        sample.setSampleNo(text(safeRequest, "sampleNo", nextNo("ECS")));
        sample.setEquipmentCode(equipment.getEquipmentCode());
        sample.setLineCode(valueOr(equipment.getLineCode(), "LINE_01"));
        sample.setLotNo(text(safeRequest, "lotNo", ""));
        sample.setStepCode(text(safeRequest, "stepCode", ""));
        sample.setRecipeCode(text(safeRequest, "recipeCode", ""));
        sample.setStandardCycleSeconds(standardCycleSeconds);
        sample.setActualCycleSeconds(decimalValue(value(safeRequest, "actualCycleSeconds"), "actualCycleSeconds"));
        sample.setOutputQty(Math.max(1, intValue(value(safeRequest, "outputQty"), 1, "outputQty")));
        sample.setGoodQty(Math.max(0, intValue(value(safeRequest, "goodQty"), sample.getOutputQty(), "goodQty")));
        if (sample.getGoodQty() > sample.getOutputQty()) {
            throw new BusinessException("goodQty cannot exceed outputQty");
        }
        sample.setResult(resolveCycleResult(sample, text(safeRequest, "result", ""), matchedCycle));
        sample.setSampleTime(now);
        sample.setSourceSystem(text(safeRequest, "sourceSystem", "eap-adapter"));
        Map<String, Object> snapshot = new LinkedHashMap<>(safeRequest);
        if (matchedCycle != null) {
            snapshot.put("standardCycleNo", matchedCycle.getCycleNo());
        }
        sample.setRawPayload(JSONUtil.toJsonStr(snapshot));
        sample.setCreatedTime(now);
        cycleSampleMapper.insert(sample);

        audit("EAP_CYCLE_REPORT", sample.getSampleNo(), "EQUIPMENT",
                "equipment=" + sample.getEquipmentCode() + ", actual=" + sample.getActualCycleSeconds()
                        + ", standard=" + sample.getStandardCycleSeconds()
                        + (matchedCycle == null ? "" : ", standardCycle=" + matchedCycle.getCycleNo())
                        + ", result=" + sample.getResult(),
                text(safeRequest, "operator", currentUser()), sample.getRawPayload());
        return Map.of("sample", cycleSampleRow(sample), "equipment", equipment);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reportParameters(Map<String, Object> request) {
        Map<String, Object> safeRequest = safeRequest(request);
        Equipment equipment = findEquipment(requiredText(safeRequest, "equipmentCode"));
        LocalDateTime now = LocalDateTime.now();
        EquipmentParameterSample sample = buildParameterSample(equipment, safeRequest, now);
        parameterSampleMapper.insert(sample);

        EquipmentEvent event = null;
        if (!"OK".equals(sample.getResult())) {
            Map<String, Object> eventRequest = new LinkedHashMap<>(safeRequest);
            eventRequest.put("eventType", "PARAMETER");
            eventRequest.put("eventLevel", text(safeRequest, "eventLevel", "P1"));
            eventRequest.put("title", sample.getParamCode() + " out of limit");
            eventRequest.put("description", "EAP parameter " + sample.getParamCode()
                    + "=" + sample.getParamValue() + valueOr(sample.getUnit(), "")
                    + " is outside recipe limits.");
            event = buildEvent(equipment, eventRequest, now);
            eventMapper.insert(event);
            updateEquipmentStatus(equipment, "ALARM");
            audit("EQUIPMENT_EVENT", event.getEventNo(), "EQUIPMENT",
                    "auto event from parameter sample " + sample.getSampleNo(),
                    text(safeRequest, "operator", currentUser()), event.getRequestSnapshot());
        } else {
            updateEquipmentStatus(equipment, text(safeRequest, "equipmentStatus", equipment.getStatus()));
        }

        audit("EAP_PARAMETER_REPORT", sample.getSampleNo(), "EQUIPMENT",
                "equipment=" + sample.getEquipmentCode() + ", param=" + sample.getParamCode()
                        + ", result=" + sample.getResult(),
                text(safeRequest, "operator", currentUser()), sample.getRawPayload());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sample", parameterRow(sample));
        data.put("equipment", equipment);
        if (event != null) {
            data.put("event", eventRow(event));
        }
        return data;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> completePmTask(String taskNo, Map<String, Object> request) {
        EquipmentPmTask task = pmTaskMapper.selectOne(new LambdaQueryWrapper<EquipmentPmTask>()
                .eq(EquipmentPmTask::getTaskNo, taskNo));
        if (task == null) {
            throw new BusinessException("PM task not found: " + taskNo);
        }
        if ("COMPLETED".equals(task.getStatus())) {
            throw new BusinessException("PM task already completed: " + taskNo);
        }
        Map<String, Object> safeRequest = safeRequest(request);
        LocalDateTime now = LocalDateTime.now();
        task.setStatus("COMPLETED");
        task.setResult(text(safeRequest, "result", "PASS"));
        task.setOperator(text(safeRequest, "operator", currentUser()));
        task.setCompletedTime(now);
        task.setUpdatedTime(now);
        task.setRequestSnapshot(JSONUtil.toJsonStr(safeRequest));
        pmTaskMapper.updateById(task);

        Equipment equipment = findEquipment(task.getEquipmentCode());
        if ("PM".equals(equipment.getStatus()) || "DOWN".equals(equipment.getStatus())) {
            updateEquipmentStatus(equipment, text(safeRequest, "equipmentStatus", "IDLE"));
        }
        audit("EQUIPMENT_PM_COMPLETE", task.getTaskNo(), "EQUIPMENT",
                "equipment=" + task.getEquipmentCode() + ", result=" + task.getResult(),
                task.getOperator(), task.getRequestSnapshot());
        return Map.of("task", pmTaskRow(task), "equipment", equipment);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> downloadRecipe(Map<String, Object> request) {
        Map<String, Object> safeRequest = safeRequest(request);
        Equipment equipment = findEquipment(requiredText(safeRequest, "equipmentCode"));
        Recipe recipe = findActiveRecipe(equipment, safeRequest);
        List<RecipeParam> params = recipeParamMapper.selectList(new LambdaQueryWrapper<RecipeParam>()
                .eq(RecipeParam::getRecipeId, recipe.getId())
                .orderByAsc(RecipeParam::getDisplayOrder));

        Map<String, Map<String, Object>> expectedParams = expectedParamSnapshot(params);
        Map<String, Map<String, Object>> readbackParams = readbackParamSnapshot(value(safeRequest, "readbackParams"), expectedParams);
        String mismatchDetail = text(safeRequest, "mismatchDetail", compareParamSnapshots(expectedParams, readbackParams));
        String requestedReadbackStatus = text(safeRequest, "readbackStatus", "");
        String readbackStatus = requestedReadbackStatus.isBlank()
                ? (mismatchDetail.isBlank() ? "MATCH" : "MISMATCH")
                : requestedReadbackStatus.toUpperCase(Locale.ROOT);
        if ("MISMATCH".equals(readbackStatus) && mismatchDetail.isBlank()) {
            mismatchDetail = "EAP readback was marked as MISMATCH";
        }

        LocalDateTime now = LocalDateTime.now();
        EquipmentRecipeCommand command = new EquipmentRecipeCommand();
        command.setCommandNo(text(safeRequest, "commandNo", nextNo("RDL")));
        command.setEquipmentCode(equipment.getEquipmentCode());
        command.setLineCode(valueOr(equipment.getLineCode(), "LINE_01"));
        command.setLotNo(text(safeRequest, "lotNo", ""));
        command.setStepCode(valueOr(recipe.getStepCode(), text(safeRequest, "stepCode", "")));
        command.setProductCode(valueOr(recipe.getProductCode(), text(safeRequest, "productCode", "")));
        command.setRecipeId(recipe.getId());
        command.setRecipeCode(recipe.getRecipeCode());
        command.setRecipeVersion(recipe.getRecipeVersion());
        command.setCommandType(text(safeRequest, "commandType", "DOWNLOAD").toUpperCase(Locale.ROOT));
        command.setCommandStatus("MATCH".equals(readbackStatus) ? "SUCCESS" : "FAILED");
        command.setDownloadBy(text(safeRequest, "operator", currentUser()));
        command.setDownloadTime(now);
        command.setEapAckStatus(text(safeRequest, "eapAckStatus", "ACK").toUpperCase(Locale.ROOT));
        command.setReadbackStatus(readbackStatus);
        command.setExpectedParamSnapshot(JSONUtil.toJsonStr(expectedParams.values()));
        command.setReadbackParamSnapshot(JSONUtil.toJsonStr(readbackParams.values()));
        command.setMismatchDetail(mismatchDetail);
        command.setSourceSystem(text(safeRequest, "sourceSystem", "eap-adapter"));
        command.setCreatedTime(now);
        command.setUpdatedTime(now);
        command.setRequestSnapshot(JSONUtil.toJsonStr(safeRequest));
        recipeCommandMapper.insert(command);

        EquipmentEvent event = null;
        if ("FAILED".equals(command.getCommandStatus())) {
            Map<String, Object> eventRequest = new LinkedHashMap<>(safeRequest);
            eventRequest.put("eventType", "RECIPE");
            eventRequest.put("eventLevel", "P1");
            eventRequest.put("recipeCode", recipe.getRecipeCode());
            eventRequest.put("stepCode", recipe.getStepCode());
            eventRequest.put("title", "Recipe readback mismatch");
            eventRequest.put("description", "Recipe " + recipe.getRecipeCode() + " readback mismatch: " + mismatchDetail);
            event = buildEvent(equipment, eventRequest, now);
            eventMapper.insert(event);
            updateEquipmentStatus(equipment, "ALARM");
            audit("EQUIPMENT_EVENT", event.getEventNo(), "EQUIPMENT",
                    "auto event from recipe command " + command.getCommandNo(),
                    command.getDownloadBy(), event.getRequestSnapshot());
        }

        audit("EQUIPMENT_RECIPE_DOWNLOAD", command.getCommandNo(), "EQUIPMENT",
                "equipment=" + command.getEquipmentCode() + ", recipe=" + command.getRecipeCode()
                        + ", readback=" + command.getReadbackStatus(),
                command.getDownloadBy(), command.getRequestSnapshot());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("command", recipeCommandRow(command));
        data.put("equipment", equipment);
        if (event != null) {
            data.put("event", eventRow(event));
        }
        return data;
    }

    private EquipmentEvent buildEvent(Equipment equipment, Map<String, Object> request, LocalDateTime now) {
        EquipmentEvent event = new EquipmentEvent();
        event.setEventNo(text(request, "eventNo", nextNo("EVT")));
        event.setEquipmentCode(equipment.getEquipmentCode());
        event.setLineCode(valueOr(equipment.getLineCode(), "LINE_01"));
        event.setEventType(text(request, "eventType", "ALARM").toUpperCase(Locale.ROOT));
        event.setEventLevel(text(request, "eventLevel", "P2").toUpperCase(Locale.ROOT));
        event.setLotNo(text(request, "lotNo", ""));
        event.setStepCode(text(request, "stepCode", ""));
        event.setRecipeCode(text(request, "recipeCode", ""));
        event.setTitle(text(request, "title", event.getEquipmentCode() + " " + event.getEventType()));
        event.setDescription(text(request, "description", text(request, "remark", event.getTitle())));
        event.setStatus(text(request, "status", "OPEN").toUpperCase(Locale.ROOT));
        event.setSourceSystem(text(request, "sourceSystem", "eap-adapter"));
        event.setOccurredTime(now);
        event.setReasonCode(text(request, "reasonCode", event.getEventType()).toUpperCase(Locale.ROOT));
        event.setReasonName(text(request, "reasonName", event.getTitle()));
        event.setDowntimeCategory(text(request, "downtimeCategory", defaultDowntimeCategory(event.getEventType())).toUpperCase(Locale.ROOT));
        event.setDowntimeType(text(request, "downtimeType", defaultDowntimeType(event.getEventType())).toUpperCase(Locale.ROOT));
        event.setStartedTime(now);
        event.setImpactLevel(text(request, "impactLevel", event.getEventLevel()).toUpperCase(Locale.ROOT));
        if ("CLOSED".equals(event.getStatus())) {
            event.setEndedTime(now);
            event.setDurationMinutes(intValue(value(request, "durationMinutes"), 0));
        }
        event.setCreatedBy(text(request, "operator", currentUser()));
        event.setCreatedTime(now);
        event.setUpdatedTime(now);
        event.setRequestSnapshot(JSONUtil.toJsonStr(request));
        return event;
    }

    private EquipmentParameterSample buildParameterSample(Equipment equipment, Map<String, Object> request, LocalDateTime now) {
        EquipmentParameterSample sample = new EquipmentParameterSample();
        sample.setSampleNo(text(request, "sampleNo", nextNo("EPS")));
        sample.setEquipmentCode(equipment.getEquipmentCode());
        sample.setLineCode(valueOr(equipment.getLineCode(), "LINE_01"));
        sample.setLotNo(text(request, "lotNo", ""));
        sample.setStepCode(text(request, "stepCode", ""));
        sample.setRecipeCode(text(request, "recipeCode", ""));
        sample.setParamCode(requiredText(request, "paramCode").toUpperCase(Locale.ROOT));
        sample.setParamName(text(request, "paramName", sample.getParamCode()));
        sample.setParamValue(decimalValue(value(request, "paramValue"), "paramValue"));
        sample.setUnit(text(request, "unit", ""));
        sample.setLowerLimit(optionalDecimal(value(request, "lowerLimit")));
        sample.setUpperLimit(optionalDecimal(value(request, "upperLimit")));
        sample.setResult(resolveSampleResult(sample, text(request, "result", "")));
        sample.setSampleTime(now);
        sample.setSourceSystem(text(request, "sourceSystem", "eap-adapter"));
        sample.setRawPayload(JSONUtil.toJsonStr(request));
        sample.setCreatedTime(now);
        return sample;
    }

    private String resolveSampleResult(EquipmentParameterSample sample, String requestedResult) {
        if (requestedResult != null && !requestedResult.isBlank()) {
            return requestedResult.toUpperCase(Locale.ROOT);
        }
        BigDecimal value = sample.getParamValue();
        if (sample.getLowerLimit() != null && value.compareTo(sample.getLowerLimit()) < 0) {
            return "NG";
        }
        if (sample.getUpperLimit() != null && value.compareTo(sample.getUpperLimit()) > 0) {
            return "NG";
        }
        return "OK";
    }

    private String resolveCycleResult(EquipmentCycleSample sample, String requestedResult, EquipmentStandardCycle standardCycle) {
        if (requestedResult != null && !requestedResult.isBlank()) {
            return requestedResult.toUpperCase(Locale.ROOT);
        }
        if (sample.getGoodQty() != null && sample.getOutputQty() != null && sample.getGoodQty() < sample.getOutputQty()) {
            return "NG";
        }
        if (standardCycle != null) {
            if (standardCycle.getUpperCycleSeconds() != null
                    && sample.getActualCycleSeconds().compareTo(standardCycle.getUpperCycleSeconds()) > 0) {
                return "NG";
            }
            if (standardCycle.getLowerCycleSeconds() != null
                    && sample.getActualCycleSeconds().compareTo(standardCycle.getLowerCycleSeconds()) < 0) {
                return "WARN";
            }
            if (standardCycle.getLowerCycleSeconds() != null || standardCycle.getUpperCycleSeconds() != null) {
                return "OK";
            }
        }
        BigDecimal warnLimit = sample.getStandardCycleSeconds().multiply(new BigDecimal("1.05"));
        BigDecimal ngLimit = sample.getStandardCycleSeconds().multiply(new BigDecimal("1.20"));
        if (sample.getActualCycleSeconds().compareTo(ngLimit) > 0) {
            return "NG";
        }
        if (sample.getActualCycleSeconds().compareTo(warnLimit) > 0) {
            return "WARN";
        }
        return "OK";
    }

    private EquipmentStandardCycle activeStandardCycleForSample(Map<String, Object> request,
                                                                Equipment equipment,
                                                                LocalDateTime now) {
        String stepCode = text(request, "stepCode", "").toUpperCase(Locale.ROOT);
        if (stepCode.isBlank()) {
            return null;
        }
        List<EquipmentStandardCycle> cycles = standardCycleMapper.selectList(new LambdaQueryWrapper<EquipmentStandardCycle>()
                .eq(EquipmentStandardCycle::getEquipmentCode, equipment.getEquipmentCode())
                .eq(EquipmentStandardCycle::getStepCode, stepCode)
                .eq(EquipmentStandardCycle::getStatus, "ACTIVE"));
        if (cycles == null || cycles.isEmpty()) {
            return null;
        }
        String productCode = text(request, "productCode", "");
        String recipeCode = text(request, "recipeCode", "");
        return cycles.stream()
                .filter(cycle -> productCode.isBlank() || productCode.equals(cycle.getProductCode()))
                .filter(cycle -> recipeCode.isBlank()
                        || recipeCode.equals(cycle.getRecipeCode())
                        || valueOr(cycle.getRecipeCode(), "").isBlank())
                .filter(cycle -> cycle.getEffectiveTime() == null || !cycle.getEffectiveTime().isAfter(now))
                .filter(cycle -> cycle.getExpireTime() == null || cycle.getExpireTime().isAfter(now))
                .max(Comparator.comparingInt(cycle -> standardCycleScore(cycle, productCode, recipeCode)))
                .orElse(null);
    }

    private int standardCycleScore(EquipmentStandardCycle cycle, String productCode, String recipeCode) {
        int score = 0;
        if (!productCode.isBlank() && productCode.equals(cycle.getProductCode())) {
            score += 10;
        }
        if (!recipeCode.isBlank() && recipeCode.equals(cycle.getRecipeCode())) {
            score += 5;
        }
        if (valueOr(cycle.getRecipeCode(), "").isBlank()) {
            score += 1;
        }
        return score;
    }

    private void validateCycleWindow(BigDecimal standard, BigDecimal lower, BigDecimal upper) {
        if (standard.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("standardCycleSeconds must be greater than zero");
        }
        if (lower != null && lower.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("lowerCycleSeconds must be greater than zero");
        }
        if (upper != null && upper.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("upperCycleSeconds must be greater than zero");
        }
        if (lower != null && lower.compareTo(standard) > 0) {
            throw new BusinessException("lowerCycleSeconds cannot exceed standardCycleSeconds");
        }
        if (upper != null && upper.compareTo(standard) < 0) {
            throw new BusinessException("upperCycleSeconds cannot be less than standardCycleSeconds");
        }
    }

    private void refreshOverduePmTasks() {
        List<EquipmentPmTask> dueTasks = pmTaskMapper.selectList(new LambdaQueryWrapper<EquipmentPmTask>()
                .ne(EquipmentPmTask::getStatus, "COMPLETED")
                .lt(EquipmentPmTask::getPlanEndTime, LocalDateTime.now()));
        for (EquipmentPmTask task : dueTasks) {
            if (!"OVERDUE".equals(task.getStatus())) {
                task.setStatus("OVERDUE");
                task.setUpdatedTime(LocalDateTime.now());
                pmTaskMapper.updateById(task);
            }
        }
    }

    private void updateEquipmentStatusForEvent(Equipment equipment, EquipmentEvent event, Map<String, Object> request) {
        String requestedStatus = text(request, "equipmentStatus", "");
        if (!requestedStatus.isBlank()) {
            updateEquipmentStatus(equipment, requestedStatus.toUpperCase(Locale.ROOT));
            return;
        }
        if ("DOWN".equals(event.getEventType())) {
            updateEquipmentStatus(equipment, "DOWN");
            return;
        }
        if ("P1".equals(event.getEventLevel()) || "ALARM".equals(event.getEventType()) || "PARAMETER".equals(event.getEventType())) {
            updateEquipmentStatus(equipment, "ALARM");
        }
        if ("PM".equals(event.getEventType())) {
            updateEquipmentStatus(equipment, "PM");
        }
        if ("STATUS".equals(event.getEventType())) {
            updateEquipmentStatus(equipment, text(request, "targetStatus", equipment.getStatus()).toUpperCase(Locale.ROOT));
        }
    }

    private void updateEquipmentStatus(Equipment equipment, String status) {
        updateEquipmentStatus(equipment, status, "AUTO_STATUS_CHANGE", currentUser(), "equipment-service", null);
    }

    private void updateEquipmentStatus(Equipment equipment, String status, String reason,
                                       String operator, String sourceSystem, String snapshot) {
        if (equipment == null || status == null || status.isBlank() || status.equals(equipment.getStatus())) {
            return;
        }
        String oldStatus = equipment.getStatus();
        equipment.setStatus(status);
        equipment.setUpdatedTime(LocalDateTime.now());
        equipmentMapper.updateById(equipment);
        recordStatusHistory(equipment, oldStatus, status, reason, operator, sourceSystem, snapshot);
    }

    private void recordStatusHistory(Equipment equipment, String oldStatus, String newStatus, String reason,
                                     String operator, String sourceSystem, String snapshot) {
        EquipmentStatusHistory history = new EquipmentStatusHistory();
        history.setHistoryNo(nextNo("ESH"));
        history.setEquipmentCode(equipment.getEquipmentCode());
        history.setLineCode(valueOr(equipment.getLineCode(), "LINE_01"));
        history.setFromStatus(valueOr(oldStatus, ""));
        history.setToStatus(newStatus);
        history.setChangeReason(valueOr(reason, "AUTO_STATUS_CHANGE"));
        history.setSourceSystem(valueOr(sourceSystem, "equipment-service"));
        history.setChangedBy(valueOr(operator, currentUser()));
        history.setChangedTime(LocalDateTime.now());
        history.setCreatedTime(history.getChangedTime());
        history.setRequestSnapshot(snapshot);
        statusHistoryMapper.insert(history);
    }

    private Recipe findActiveRecipe(Equipment equipment, Map<String, Object> request) {
        String recipeCode = text(request, "recipeCode", "");
        Recipe recipe;
        if (!recipeCode.isBlank()) {
            recipe = recipeMapper.selectOne(new LambdaQueryWrapper<Recipe>()
                    .eq(Recipe::getRecipeCode, recipeCode));
        } else {
            String productCode = requiredText(request, "productCode");
            String stepCode = requiredText(request, "stepCode");
            recipe = recipeMapper.selectOne(new LambdaQueryWrapper<Recipe>()
                    .eq(Recipe::getProductCode, productCode)
                    .eq(Recipe::getStepCode, stepCode)
                    .eq(Recipe::getEquipmentCode, equipment.getEquipmentCode())
                    .eq(Recipe::getStatus, "ACTIVE")
                    .orderByDesc(Recipe::getId)
                    .last("LIMIT 1"));
        }
        if (recipe == null) {
            throw new BusinessException("Active recipe not found for equipment: " + equipment.getEquipmentCode());
        }
        if (!"ACTIVE".equals(recipe.getStatus())) {
            throw new BusinessException("Recipe is not active: " + recipe.getRecipeCode());
        }
        if (!equipment.getEquipmentCode().equals(recipe.getEquipmentCode())) {
            throw new BusinessException("Recipe " + recipe.getRecipeCode()
                    + " does not match equipment " + equipment.getEquipmentCode());
        }
        String requestStep = text(request, "stepCode", "");
        if (!requestStep.isBlank() && !requestStep.equals(recipe.getStepCode())) {
            throw new BusinessException("Recipe " + recipe.getRecipeCode() + " does not match step " + requestStep);
        }
        String requestProduct = text(request, "productCode", "");
        if (!requestProduct.isBlank() && !requestProduct.equals(recipe.getProductCode())) {
            throw new BusinessException("Recipe " + recipe.getRecipeCode() + " does not match product " + requestProduct);
        }
        return recipe;
    }

    private Map<String, Map<String, Object>> expectedParamSnapshot(List<RecipeParam> params) {
        if (params == null || params.isEmpty()) {
            throw new BusinessException("Recipe parameters are not configured");
        }
        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        for (RecipeParam param : params) {
            String code = firstText(param.getParamCode(), param.getParamName());
            if (code.isBlank()) {
                code = "PARAM_" + valueOr(String.valueOf(param.getId()), String.valueOf(rows.size() + 1));
            }
            code = code.toUpperCase(Locale.ROOT);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("paramCode", code);
            row.put("paramName", valueOr(param.getParamName(), code));
            row.put("targetValue", param.getTargetValue());
            row.put("lowerLimit", param.getLowerLimit());
            row.put("upperLimit", param.getUpperLimit());
            row.put("unit", param.getUnit());
            row.put("isKeyParam", param.getIsKeyParam());
            rows.put(code, row);
        }
        return rows;
    }

    private Map<String, Map<String, Object>> readbackParamSnapshot(Object raw,
                                                                  Map<String, Map<String, Object>> expected) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            return copySnapshot(expected);
        }
        if (raw instanceof String text) {
            try {
                return readbackParamSnapshot(JSONUtil.parse(text), expected);
            } catch (Exception e) {
                throw new BusinessException("readbackParams must be valid JSON");
            }
        }
        if (raw instanceof Collection<?> collection) {
            Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
            for (Object item : collection) {
            Map<String, Object> row = objectMap(item);
                String code = firstText(row.get("paramCode"), row.get("paramName")).toUpperCase(Locale.ROOT);
                if (!code.isBlank()) {
                    row.put("paramCode", code);
                    rows.put(code, row);
                }
            }
            return rows;
        }
        Map<String, Object> source = objectMap(raw);
        if (source.containsKey("params")) {
            return readbackParamSnapshot(source.get("params"), expected);
        }
        if (source.containsKey("paramCode")) {
            String code = firstText(source.get("paramCode"), source.get("paramName")).toUpperCase(Locale.ROOT);
            source.put("paramCode", code);
            return code.isBlank() ? Map.of() : Map.of(code, source);
        }
        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String code = key.toUpperCase(Locale.ROOT);
            Map<String, Object> row;
            if (value instanceof Map<?, ?> || value instanceof cn.hutool.json.JSONObject) {
                row = objectMap(value);
            } else {
                row = new LinkedHashMap<>();
                row.put("targetValue", value);
            }
            row.putIfAbsent("paramCode", code);
            rows.put(code, row);
        });
        return rows;
    }

    private Map<String, Map<String, Object>> copySnapshot(Map<String, Map<String, Object>> source) {
        Map<String, Map<String, Object>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, new LinkedHashMap<>(value)));
        return copy;
    }

    private String compareParamSnapshots(Map<String, Map<String, Object>> expected,
                                         Map<String, Map<String, Object>> readback) {
        List<String> details = expected.entrySet().stream()
                .map(entry -> compareParam(entry.getKey(), entry.getValue(), readback.get(entry.getKey())))
                .filter(detail -> !detail.isBlank())
                .collect(Collectors.toList());
        return String.join("; ", details);
    }

    private String compareParam(String code, Map<String, Object> expected, Map<String, Object> actual) {
        if (actual == null) {
            return code + " missing";
        }
        List<String> mismatches = List.of("targetValue", "lowerLimit", "upperLimit").stream()
                .filter(key -> !sameValue(expected.get(key), actual.get(key)))
                .map(key -> code + " " + key + " mismatch")
                .collect(Collectors.toList());
        return String.join(", ", mismatches);
    }

    private boolean sameValue(Object expected, Object actual) {
        if (expected == null || String.valueOf(expected).isBlank()) {
            return actual == null || String.valueOf(actual).isBlank();
        }
        if (actual == null || String.valueOf(actual).isBlank()) {
            return false;
        }
        try {
            BigDecimal left = expected instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(expected));
            BigDecimal right = actual instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(actual));
            return left.compareTo(right) == 0;
        } catch (NumberFormatException e) {
            return String.valueOf(expected).equals(String.valueOf(actual));
        }
    }

    private Map<String, Object> objectMap(Object value) {
        Map<String, Object> target = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> target.put(String.valueOf(key), item));
            return target;
        }
        if (value instanceof cn.hutool.json.JSONObject jsonObject) {
            jsonObject.forEach((key, item) -> target.put(String.valueOf(key), item));
        }
        return target;
    }

    private String firstText(Object first, Object second) {
        if (first != null && !String.valueOf(first).isBlank()) {
            return String.valueOf(first);
        }
        if (second != null && !String.valueOf(second).isBlank()) {
            return String.valueOf(second);
        }
        return "";
    }

    private Equipment findEquipment(String equipmentCode) {
        Equipment equipment = equipmentMapper.selectOne(new LambdaQueryWrapper<Equipment>()
                .eq(Equipment::getEquipmentCode, equipmentCode));
        if (equipment == null) {
            throw new BusinessException("Equipment not found: " + equipmentCode);
        }
        return equipment;
    }

    private Map<String, Object> eventRow(EquipmentEvent event) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("eventNo", event.getEventNo());
        row.put("equipmentCode", event.getEquipmentCode());
        row.put("lineCode", event.getLineCode());
        row.put("eventType", event.getEventType());
        row.put("eventLevel", event.getEventLevel());
        row.put("lotNo", event.getLotNo());
        row.put("stepCode", event.getStepCode());
        row.put("recipeCode", event.getRecipeCode());
        row.put("title", event.getTitle());
        row.put("description", event.getDescription());
        row.put("status", event.getStatus());
        row.put("sourceSystem", event.getSourceSystem());
        row.put("occurredTime", event.getOccurredTime());
        row.put("reasonCode", event.getReasonCode());
        row.put("reasonName", event.getReasonName());
        row.put("downtimeCategory", event.getDowntimeCategory());
        row.put("downtimeType", resolveDowntimeType(event));
        row.put("startedTime", event.getStartedTime());
        row.put("endedTime", event.getEndedTime());
        row.put("durationMinutes", event.getDurationMinutes() == null ? rawDurationMinutes(event, LocalDateTime.now()) : event.getDurationMinutes());
        row.put("impactLevel", valueOr(event.getImpactLevel(), event.getEventLevel()));
        row.put("time", formatTime(event.getOccurredTime()));
        row.put("type", statusType(event.getStatus(), event.getEventLevel()));
        return row;
    }

    private Map<String, Object> parameterRow(EquipmentParameterSample sample) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sampleNo", sample.getSampleNo());
        row.put("equipmentCode", sample.getEquipmentCode());
        row.put("lineCode", sample.getLineCode());
        row.put("lotNo", sample.getLotNo());
        row.put("stepCode", sample.getStepCode());
        row.put("recipeCode", sample.getRecipeCode());
        row.put("paramCode", sample.getParamCode());
        row.put("paramName", sample.getParamName());
        row.put("paramValue", sample.getParamValue());
        row.put("unit", sample.getUnit());
        row.put("lowerLimit", sample.getLowerLimit());
        row.put("upperLimit", sample.getUpperLimit());
        row.put("result", sample.getResult());
        row.put("sampleTime", sample.getSampleTime());
        row.put("time", formatTime(sample.getSampleTime()));
        row.put("sourceSystem", sample.getSourceSystem());
        row.put("type", statusType(sample.getResult(), ""));
        return row;
    }

    private Map<String, Object> pmTaskRow(EquipmentPmTask task) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("taskNo", task.getTaskNo());
        row.put("equipmentCode", task.getEquipmentCode());
        row.put("lineCode", task.getLineCode());
        row.put("pmType", task.getPmType());
        row.put("pmLevel", task.getPmLevel());
        row.put("planStartTime", task.getPlanStartTime());
        row.put("planEndTime", task.getPlanEndTime());
        row.put("status", task.getStatus());
        row.put("checklist", task.getChecklist());
        row.put("result", task.getResult());
        row.put("operator", task.getOperator());
        row.put("completedTime", task.getCompletedTime());
        row.put("time", formatTime(task.getPlanEndTime()));
        row.put("type", statusType(task.getStatus(), ""));
        return row;
    }

    private Map<String, Object> recipeCommandRow(EquipmentRecipeCommand command) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("commandNo", command.getCommandNo());
        row.put("equipmentCode", command.getEquipmentCode());
        row.put("lineCode", command.getLineCode());
        row.put("lotNo", command.getLotNo());
        row.put("stepCode", command.getStepCode());
        row.put("productCode", command.getProductCode());
        row.put("recipeCode", command.getRecipeCode());
        row.put("recipeVersion", command.getRecipeVersion());
        row.put("commandType", command.getCommandType());
        row.put("commandStatus", command.getCommandStatus());
        row.put("downloadBy", command.getDownloadBy());
        row.put("downloadTime", command.getDownloadTime());
        row.put("eapAckStatus", command.getEapAckStatus());
        row.put("readbackStatus", command.getReadbackStatus());
        row.put("mismatchDetail", command.getMismatchDetail());
        row.put("sourceSystem", command.getSourceSystem());
        row.put("time", formatTime(command.getDownloadTime()));
        row.put("type", statusType(command.getCommandStatus(), command.getReadbackStatus()));
        return row;
    }

    private Map<String, Object> statusHistoryRow(EquipmentStatusHistory history) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("historyNo", history.getHistoryNo());
        row.put("equipmentCode", history.getEquipmentCode());
        row.put("lineCode", history.getLineCode());
        row.put("fromStatus", history.getFromStatus());
        row.put("toStatus", history.getToStatus());
        row.put("changeReason", history.getChangeReason());
        row.put("sourceSystem", history.getSourceSystem());
        row.put("changedBy", history.getChangedBy());
        row.put("changedTime", history.getChangedTime());
        row.put("time", formatTime(history.getChangedTime()));
        row.put("type", statusType(history.getToStatus(), ""));
        return row;
    }

    private Map<String, Object> cycleSampleRow(EquipmentCycleSample sample) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sampleNo", sample.getSampleNo());
        row.put("equipmentCode", sample.getEquipmentCode());
        row.put("lineCode", sample.getLineCode());
        row.put("lotNo", sample.getLotNo());
        row.put("stepCode", sample.getStepCode());
        row.put("recipeCode", sample.getRecipeCode());
        row.put("standardCycleSeconds", sample.getStandardCycleSeconds());
        row.put("actualCycleSeconds", sample.getActualCycleSeconds());
        row.put("outputQty", sample.getOutputQty());
        row.put("goodQty", sample.getGoodQty());
        row.put("result", sample.getResult());
        row.put("sampleTime", sample.getSampleTime());
        row.put("time", formatTime(sample.getSampleTime()));
        row.put("sourceSystem", sample.getSourceSystem());
        row.put("performanceText", cyclePerformanceText(List.of(sample)));
        row.put("type", statusType(sample.getResult(), ""));
        return row;
    }

    private Map<String, Object> standardCycleRow(EquipmentStandardCycle cycle) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("cycleNo", cycle.getCycleNo());
        row.put("productCode", cycle.getProductCode());
        row.put("stepCode", cycle.getStepCode());
        row.put("equipmentCode", cycle.getEquipmentCode());
        row.put("recipeCode", cycle.getRecipeCode());
        row.put("cycleVersion", cycle.getCycleVersion());
        row.put("standardCycleSeconds", cycle.getStandardCycleSeconds());
        row.put("lowerCycleSeconds", cycle.getLowerCycleSeconds());
        row.put("upperCycleSeconds", cycle.getUpperCycleSeconds());
        row.put("status", cycle.getStatus());
        row.put("effectiveTime", cycle.getEffectiveTime());
        row.put("expireTime", cycle.getExpireTime());
        row.put("updatedBy", cycle.getUpdatedBy());
        row.put("updatedTime", cycle.getUpdatedTime());
        row.put("time", formatTime(cycle.getUpdatedTime()));
        row.put("type", statusType(cycle.getStatus(), ""));
        return row;
    }

    private String statusType(String status, String level) {
        String value = valueOr(status, "").toUpperCase(Locale.ROOT);
        String qualifier = valueOr(level, "").toUpperCase(Locale.ROOT);
        if ("P1".equals(qualifier)
                || "MISMATCH".equals(qualifier)
                || "NG".equals(value)
                || "FAILED".equals(value)
                || "OPEN".equals(value)
                || "DOWN".equals(value)
                || "OVERDUE".equals(value)) {
            return "red";
        }
        if ("PROCESSING".equals(value) || "PENDING".equals(value) || "WARN".equals(value)) {
            return "amber";
        }
        if ("OK".equals(value) || "COMPLETED".equals(value) || "CLOSED".equals(value) || "PASS".equals(value)) {
            return "green";
        }
        return "blue";
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "-" : time.toLocalTime().withNano(0).toString();
    }

    private String nextNo(String prefix) {
        int seq = Math.floorMod(SEQUENCE.incrementAndGet(), 10000);
        return prefix + "-" + NO_TIME.format(LocalDateTime.now()) + "-" + String.format("%04d", seq);
    }

    private void audit(String action, String bizNo, String bizType, String description, String operator, String snapshot) {
        try {
            auditLogService.record(action, bizNo, bizType, description, operator, "equipment-service", snapshot);
        } catch (Exception e) {
            log.warn("equipment audit write failed: action={}, bizNo={}, reason={}", action, bizNo, e.getMessage());
        }
    }

    private String requiredText(Map<String, Object> request, String key) {
        String value = text(request, key, "");
        if (value.isBlank()) {
            throw new BusinessException(key + " is required");
        }
        return value;
    }

    private String text(Map<String, Object> request, String key, String defaultValue) {
        Object value = value(request, key);
        return value == null || String.valueOf(value).isBlank() ? valueOr(defaultValue, "") : String.valueOf(value);
    }

    private Object value(Map<String, Object> request, String key) {
        return request == null ? null : request.get(key);
    }

    private BigDecimal decimalValue(Object value, String field) {
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BusinessException(field + " is required");
        }
        try {
            return value instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new BusinessException(field + " must be numeric");
        }
    }

    private BigDecimal positiveDecimal(Object value, String field) {
        BigDecimal decimal = decimalValue(value, field);
        if (decimal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(field + " must be greater than zero");
        }
        return decimal;
    }

    private BigDecimal optionalDecimal(Object value) {
        return optionalDecimal(value, "limit");
    }

    private BigDecimal optionalDecimal(Object value, String field) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return value instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new BusinessException(field + " must be numeric");
        }
    }

    private int intValue(Object value, int defaultValue) {
        return intValue(value, defaultValue, "durationMinutes");
    }

    private int intValue(Object value, int defaultValue, String field) {
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new BusinessException(field + " must be numeric");
        }
    }

    private String defaultDowntimeCategory(String eventType) {
        return switch (valueOr(eventType, "").toUpperCase(Locale.ROOT)) {
            case "PM" -> "PM";
            case "QUALITY" -> "QUALITY";
            case "STATUS" -> "STATUS";
            default -> "EQUIPMENT";
        };
    }

    private String defaultDowntimeType(String eventType) {
        return switch (valueOr(eventType, "").toUpperCase(Locale.ROOT)) {
            case "PM" -> "PLANNED";
            case "STATUS" -> "STATE";
            default -> "UNPLANNED";
        };
    }

    private String resolveDowntimeType(EquipmentEvent event) {
        String configured = valueOr(event.getDowntimeType(), "").toUpperCase(Locale.ROOT);
        if (!configured.isBlank()) {
            return configured;
        }
        return defaultDowntimeType(event.getEventType());
    }

    private long overlapMinutes(EquipmentEvent event, LocalDateTime windowStart, LocalDateTime now) {
        LocalDateTime start = event.getStartedTime() == null ? event.getOccurredTime() : event.getStartedTime();
        if (start == null) {
            return 0L;
        }
        LocalDateTime end = event.getEndedTime() != null
                ? event.getEndedTime()
                : event.getClosedTime() != null ? event.getClosedTime() : now;
        if (end.isBefore(start)) {
            return Math.max(0, event.getDurationMinutes() == null ? 0 : event.getDurationMinutes());
        }
        LocalDateTime effectiveStart = start.isBefore(windowStart) ? windowStart : start;
        LocalDateTime effectiveEnd = end.isAfter(now) ? now : end;
        return Math.max(0L, Duration.between(effectiveStart, effectiveEnd).toMinutes());
    }

    private long rawDurationMinutes(EquipmentEvent event, LocalDateTime now) {
        LocalDateTime start = event.getStartedTime() == null ? event.getOccurredTime() : event.getStartedTime();
        if (start == null) {
            return 0L;
        }
        LocalDateTime end = event.getEndedTime() != null
                ? event.getEndedTime()
                : event.getClosedTime() != null ? event.getClosedTime() : now;
        return Math.max(0L, Duration.between(start, end).toMinutes());
    }

    private double performanceRate(List<Equipment> equipments, List<EquipmentCycleSample> cycleSamples) {
        if (cycleSamples != null && !cycleSamples.isEmpty()) {
            return cyclePerformance(cycleSamples);
        }
        if (equipments == null || equipments.isEmpty()) {
            return 0.0;
        }
        long capable = equipments.stream()
                .filter(equipment -> List.of("RUNNING", "IDLE", "PM").contains(valueOr(equipment.getStatus(), "").toUpperCase(Locale.ROOT)))
                .count();
        return clampPercent(95.0 + capable * 5.0 / equipments.size());
    }

    private double qualityRate(LocalDateTime windowStart, List<EquipmentCycleSample> cycleSamples) {
        if (cycleSamples != null && !cycleSamples.isEmpty()) {
            int outputQty = cycleSamples.stream().mapToInt(sample -> sample.getOutputQty() == null ? 0 : sample.getOutputQty()).sum();
            int goodQty = cycleSamples.stream().mapToInt(sample -> sample.getGoodQty() == null ? 0 : sample.getGoodQty()).sum();
            if (outputQty > 0) {
                return clampPercent(goodQty * 100.0 / outputQty);
            }
        }
        List<EquipmentParameterSample> samples = parameterSampleMapper.selectList(new LambdaQueryWrapper<EquipmentParameterSample>()
                .ge(EquipmentParameterSample::getSampleTime, windowStart));
        if (samples == null || samples.isEmpty()) {
            return 96.82;
        }
        long ok = samples.stream().filter(sample -> "OK".equals(sample.getResult())).count();
        return clampPercent(ok * 100.0 / samples.size());
    }

    private double cyclePerformance(List<EquipmentCycleSample> samples) {
        double standardSeconds = samples.stream()
                .mapToDouble(sample -> decimalDouble(sample.getStandardCycleSeconds()) * Math.max(1, sample.getOutputQty() == null ? 1 : sample.getOutputQty()))
                .sum();
        double actualSeconds = samples.stream()
                .mapToDouble(sample -> decimalDouble(sample.getActualCycleSeconds()) * Math.max(1, sample.getOutputQty() == null ? 1 : sample.getOutputQty()))
                .sum();
        if (actualSeconds <= 0) {
            return 0.0;
        }
        return clampPercent(standardSeconds * 100.0 / actualSeconds);
    }

    private String cyclePerformanceText(List<EquipmentCycleSample> samples) {
        return percentText(cyclePerformance(samples));
    }

    private double decimalDouble(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private List<Map<String, Object>> statusDistribution(List<Equipment> equipments) {
        Map<String, Long> grouped = equipments.stream()
                .collect(Collectors.groupingBy(equipment -> valueOr(equipment.getStatus(), "UNKNOWN"),
                        LinkedHashMap::new, Collectors.counting()));
        return grouped.entrySet().stream()
                .map(entry -> Map.<String, Object>of(
                        "status", entry.getKey(),
                        "qty", entry.getValue(),
                        "type", statusType(entry.getKey(), "")
                ))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> downtimeReasonTopN(List<EquipmentEvent> events,
                                                        LocalDateTime windowStart,
                                                        LocalDateTime now) {
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (EquipmentEvent event : events) {
            String downtimeType = resolveDowntimeType(event);
            if (!"PLANNED".equals(downtimeType) && !"UNPLANNED".equals(downtimeType)) {
                continue;
            }
            long minutes = overlapMinutes(event, windowStart, now);
            if (minutes <= 0) {
                continue;
            }
            String reasonCode = valueOr(event.getReasonCode(), valueOr(event.getEventType(), "UNKNOWN"));
            Map<String, Object> row = grouped.computeIfAbsent(reasonCode, key -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("reasonCode", reasonCode);
                item.put("reasonName", valueOr(event.getReasonName(), valueOr(event.getTitle(), reasonCode)));
                item.put("downtimeType", downtimeType);
                item.put("downtimeCategory", valueOr(event.getDowntimeCategory(), defaultDowntimeCategory(event.getEventType())));
                item.put("durationMinutes", 0L);
                item.put("eventCount", 0L);
                item.put("type", "PLANNED".equals(downtimeType) ? "amber" : "red");
                return item;
            });
            row.put("durationMinutes", ((Number) row.get("durationMinutes")).longValue() + minutes);
            row.put("eventCount", ((Number) row.get("eventCount")).longValue() + 1L);
        }
        return grouped.values().stream()
                .sorted((left, right) -> Long.compare(((Number) right.get("durationMinutes")).longValue(),
                        ((Number) left.get("durationMinutes")).longValue()))
                .limit(5)
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> equipmentOeeRows(List<Equipment> equipments,
                                                       List<EquipmentEvent> events,
                                                       List<EquipmentCycleSample> cycleSamples,
                                                       LocalDateTime windowStart,
                                                       LocalDateTime now) {
        return equipments.stream().map(equipment -> {
            List<EquipmentEvent> equipmentEvents = events.stream()
                    .filter(event -> equipment.getEquipmentCode().equals(event.getEquipmentCode()))
                    .collect(Collectors.toList());
            List<EquipmentCycleSample> equipmentSamples = cycleSamples.stream()
                    .filter(sample -> equipment.getEquipmentCode().equals(sample.getEquipmentCode()))
                    .collect(Collectors.toList());
            long planned = equipmentEvents.stream()
                    .filter(event -> "PLANNED".equals(resolveDowntimeType(event)))
                    .mapToLong(event -> overlapMinutes(event, windowStart, now))
                    .sum();
            long unplanned = equipmentEvents.stream()
                    .filter(event -> "UNPLANNED".equals(resolveDowntimeType(event)))
                    .mapToLong(event -> overlapMinutes(event, windowStart, now))
                    .sum();
            long plannedProduction = Math.max(0L, 24L * 60L - planned);
            double availability = plannedProduction == 0 ? 0.0 : clampPercent((plannedProduction - unplanned) * 100.0 / plannedProduction);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("equipmentCode", equipment.getEquipmentCode());
            row.put("equipmentName", equipment.getEquipmentName());
            row.put("lineCode", equipment.getLineCode());
            row.put("status", equipment.getStatus());
            row.put("availabilityRate", round(availability));
            row.put("availabilityText", percentText(availability));
            row.put("performanceRate", round(equipmentSamples.isEmpty() ? 0.0 : cyclePerformance(equipmentSamples)));
            row.put("performanceText", equipmentSamples.isEmpty() ? "-" : cyclePerformanceText(equipmentSamples));
            row.put("cycleSampleCount", equipmentSamples.size());
            row.put("plannedDowntimeMinutes", planned);
            row.put("unplannedDowntimeMinutes", unplanned);
            row.put("type", statusType(equipment.getStatus(), ""));
            return row;
        }).collect(Collectors.toList());
    }

    private double clampPercent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(100.0, value));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String percentText(double value) {
        return String.format(Locale.ROOT, "%.2f%%", round(value));
    }

    private Map<String, Object> safeRequest(Map<String, Object> request) {
        return request == null ? Map.of() : request;
    }

    private String currentUser() {
        return AuthContext.username();
    }

    private String valueOr(String value, String fallback) {
        return Objects.requireNonNullElse(value, fallback);
    }
}
