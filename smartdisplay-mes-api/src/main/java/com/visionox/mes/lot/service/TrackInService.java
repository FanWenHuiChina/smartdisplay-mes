package com.visionox.mes.lot.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.visionox.mes.common.BusinessException;
import com.visionox.mes.lot.dto.*;
import com.visionox.mes.lot.entity.*;
import com.visionox.mes.lot.mapper.*;
import com.visionox.mes.masterdata.entity.WorkShift;
import com.visionox.mes.masterdata.mapper.WorkShiftMapper;
import com.visionox.mes.material.service.MaterialService;
import com.visionox.mes.recipe.entity.Recipe;
import com.visionox.mes.quality.service.QualityService;
import com.visionox.mes.recipe.service.RecipeService;
import com.visionox.mes.route.service.RouteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Track In/Out服务
 *
 * 核心业务逻辑：
 * Track In 8层校验（参考显示行业通用 MES 执行控制模型）：
 * 1. Lot状态校验 - 必须是READY或REWORK状态
 * 2. Route防跳站校验 - 请求工序必须等于Lot当前待执行工序，且在产品生效Route中
 * 3. 设备状态校验 - 必须是IDLE或RUNNING
 * 4. 设备能力校验 - 设备必须支持该工序
 * 5. Recipe校验 - 必须存在有效的Recipe
 * 6. Hold状态校验 - Hold的Lot不能进站
 * 7. 班次校验 - 当前时间必须落入Lot产线的ACTIVE班次
 * 8. 物料齐套校验 - 关键物料必须齐套后才能进站
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackInService {

    private final LotMapper lotMapper;
    private final EquipmentMapper equipmentMapper;
    private final LotStepRecordMapper stepRecordMapper;
    private final HoldRecordMapper holdRecordMapper;
    private final RecipeService recipeService;
    private final RouteService routeService;
    private final QualityService qualityService;
    private final MaterialService materialService;
    private final WorkShiftMapper workShiftMapper;

    /**
     * Lot分页查询
     */
    public IPage<Lot> pageLots(Page<Lot> page, String lotNo, String status) {
        LambdaQueryWrapper<Lot> wrapper = new LambdaQueryWrapper<>();
        if (lotNo != null && !lotNo.isEmpty()) {
            wrapper.like(Lot::getLotNo, lotNo);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Lot::getStatus, status);
        }
        wrapper.orderByDesc(Lot::getCreatedTime);
        return lotMapper.selectPage(page, wrapper);
    }

    /**
     * Track In - 核心业务逻辑
     */
    @Transactional(rollbackFor = Exception.class)
    public void trackIn(TrackInRequest request) {
        log.info("Track In开始: lotNo={}, step={}, equipment={}",
                request.getLotNo(), request.getStepCode(), request.getEquipmentCode());

        TrackInEvaluation evaluation = evaluateTrackIn(request);
        assertTrackInChecksPassed(evaluation.result());
        Lot lot = evaluation.lot();
        Recipe recipe = evaluation.recipe();

        // ====== 校验通过，执行Track In ======

        // 1. 更新Lot状态
        lot.setStatus("PROCESSING");
        lot.setCurrentStepCode(request.getStepCode());
        lot.setCurrentEquipmentCode(request.getEquipmentCode());
        lotMapper.updateById(lot);

        // 2. 创建过站记录
        LotStepRecord record = new LotStepRecord();
        record.setLotNo(request.getLotNo());
        record.setStepCode(request.getStepCode());
        record.setEquipmentCode(request.getEquipmentCode());
        record.setRecipeCode(recipe.getRecipeCode());
        record.setTrackInTime(LocalDateTime.now());
        record.setOperator(request.getOperator() != null ? request.getOperator() : "system");
        stepRecordMapper.insert(record);

        // 3. 锁定本工序关键物料批次
        materialService.lockForTrackIn(lot, request.getStepCode(), request.getEquipmentCode(), record.getOperator());

        log.info("Track In成功: lotNo={}, recordId={}", request.getLotNo(), record.getId());
    }

    /**
     * Track In 进站前预校验，供前端执行台展示和写接口复用。
     */
    public Map<String, Object> trackInChecks(TrackInRequest request) {
        return evaluateTrackIn(request).result();
    }

    /**
     * Track Out
     */
    @Transactional(rollbackFor = Exception.class)
    public String trackOut(TrackOutRequest request) {
        log.info("Track Out开始: lotNo={}, result={}", request.getLotNo(), request.getResult());

        // 1. 校验Lot状态
        Lot lot = lotMapper.selectOne(
                new LambdaQueryWrapper<Lot>()
                        .eq(Lot::getLotNo, request.getLotNo())
        );
        if (lot == null) {
            throw new BusinessException("Lot不存在: " + request.getLotNo());
        }

        if (!"PROCESSING".equals(lot.getStatus())) {
            throw new BusinessException(
                    String.format("Lot状态不允许出站: 当前状态=%s, 期望状态=PROCESSING", lot.getStatus())
            );
        }

        // 2. 查找最近的Track In记录
        List<LotStepRecord> records = stepRecordMapper.selectList(
                new LambdaQueryWrapper<LotStepRecord>()
                        .eq(LotStepRecord::getLotNo, request.getLotNo())
                        .isNull(LotStepRecord::getTrackOutTime)
                        .orderByDesc(LotStepRecord::getTrackInTime)
        );

        if (records.isEmpty()) {
            throw new BusinessException("未找到对应的Track In记录: " + request.getLotNo());
        }

        LotStepRecord record = records.get(0);

        // 3. 执行质量判定并更新过站记录
        record.setTrackOutTime(LocalDateTime.now());
        record.setProcessParams(request.getProcessParams());
        record.setRemark(request.getRemark());
        String finalResult = qualityService.evaluateTrackOut(lot, record, request);
        record.setResult(finalResult);
        stepRecordMapper.updateById(record);
        materialService.consumeForTrackOut(lot, record);

        // 4. OK恢复READY；NG/超限由质量服务自动Hold
        if (!"NG".equals(finalResult)) {
            lot.setStatus("READY");
            lotMapper.updateById(lot);
        }

        log.info("Track Out成功: lotNo={}, finalResult={}", request.getLotNo(), finalResult);
        return finalResult;
    }

    /**
     * 检查设备能力（是否支持该工序）
     */
    private boolean checkEquipmentCapability(Equipment equipment, String stepCode) {
        String capabilitySteps = equipment.getCapabilitySteps();
        if (capabilitySteps == null || capabilitySteps.isEmpty()) {
            return false;
        }

        try {
            List<String> steps = JSONUtil.toList(capabilitySteps, String.class);
            return steps.contains(stepCode);
        } catch (Exception e) {
            log.warn("解析设备能力失败，已降级为不支持该工序: equipment={}, capabilitySteps={}, reason={}",
                    equipment.getEquipmentCode(), capabilitySteps, e.getMessage());
            return false;
        }
    }

    private boolean isTrackInAllowedStatus(String status) {
        return "READY".equals(status) || "REWORK".equals(status);
    }

    private TrackInEvaluation evaluateTrackIn(TrackInRequest request) {
        List<Map<String, Object>> checks = new ArrayList<>();
        Map<String, Object> result = new LinkedHashMap<>();
        String lotNo = request == null ? "" : valueOr(request.getLotNo(), "");
        String stepCode = request == null ? "" : valueOr(request.getStepCode(), "");
        String equipmentCode = request == null ? "" : valueOr(request.getEquipmentCode(), "");
        result.put("checkedTime", LocalDateTime.now());
        result.put("lotNo", lotNo);
        result.put("requestedStepCode", stepCode);
        result.put("equipmentCode", equipmentCode);

        if (request == null || lotNo.isBlank()) {
            checks.add(trackInCheck("lotStatus", "Lot状态", false, true, "red", "Lot不能为空"));
            addUnavailableTrackInChecks(checks, "缺少Lot，无法执行后续校验");
            return trackInCheckResult(new TrackInEvaluation(null, null, null, result), checks);
        }

        Lot lot = lotMapper.selectOne(new LambdaQueryWrapper<Lot>().eq(Lot::getLotNo, lotNo));
        if (lot == null) {
            checks.add(trackInCheck("lotStatus", "Lot状态", false, true, "red", "Lot不存在: " + lotNo));
            addUnavailableTrackInChecks(checks, "缺少Lot，无法执行后续校验");
            return trackInCheckResult(new TrackInEvaluation(null, null, null, result), checks);
        }
        result.put("productCode", lot.getProductCode());
        result.put("lineCode", lot.getLineCode());
        result.put("currentStepCode", lot.getCurrentStepCode());
        result.put("status", lot.getStatus());
        result.put("holdFlag", lot.getHoldFlag());

        boolean lotReady = isTrackInAllowedStatus(lot.getStatus());
        checks.add(trackInCheck("lotStatus", "Lot状态", lotReady, true, lotReady ? "green" : "red",
                lotReady ? lot.getStatus() + "，允许进站" : "当前状态 " + valueOr(lot.getStatus(), "-") + " 不允许进站"));
        if (!lotReady) {
            addUnavailableTrackInChecks(checks, "Lot状态未通过，后续校验未执行");
            return trackInCheckResult(new TrackInEvaluation(lot, null, null, result), checks);
        }

        boolean routeReady = false;
        try {
            routeService.validateTrackInStep(lot.getProductCode(), lot.getCurrentStepCode(), stepCode);
            routeReady = true;
            checks.add(trackInCheck("route", "Route下一站", true, true, "green",
                    valueOr(stepCode, "-") + " 与当前待执行工序一致"));
        } catch (Exception e) {
            checks.add(trackInCheck("route", "Route下一站", false, true, "red", e.getMessage()));
        }

        Equipment equipment = null;
        boolean equipmentUsable = false;
        if (equipmentCode.isBlank()) {
            checks.add(trackInCheck("equipmentStatus", "设备状态", false, true, "red", "设备编码不能为空"));
        } else {
            equipment = equipmentMapper.selectOne(new LambdaQueryWrapper<Equipment>()
                    .eq(Equipment::getEquipmentCode, equipmentCode));
            if (equipment == null) {
                checks.add(trackInCheck("equipmentStatus", "设备状态", false, true, "red", "设备不存在: " + equipmentCode));
            } else {
                result.put("equipmentStatus", equipment.getStatus());
                equipmentUsable = "IDLE".equals(equipment.getStatus()) || "RUNNING".equals(equipment.getStatus());
                checks.add(trackInCheck("equipmentStatus", "设备状态", equipmentUsable, true,
                        equipmentUsable ? "green" : "red",
                        equipmentCode + " / " + valueOr(equipment.getStatus(), "-")));
            }
        }

        boolean capabilityReady = equipment != null && checkEquipmentCapability(equipment, stepCode);
        checks.add(trackInCheck("equipmentCapability", "设备能力", capabilityReady, true,
                capabilityReady ? "green" : equipment == null ? "gray" : "red",
                capabilityReady ? equipmentCode + " 支持 " + stepCode
                        : equipment == null ? "缺少设备，无法校验能力" : equipmentCode + " 不支持 " + stepCode));

        Recipe recipe = null;
        if (routeReady && equipmentUsable && capabilityReady) {
            try {
                recipe = recipeService.findActiveRecipe(lot.getProductCode(), stepCode, equipmentCode);
                log.debug("找到有效Recipe: {}", recipe.getRecipeCode());
                checks.add(trackInCheck("recipe", "Recipe", true, true, "green",
                        recipe.getRecipeCode() + " 已生效"));
                result.put("recipeCode", recipe.getRecipeCode());
            } catch (BusinessException e) {
                checks.add(trackInCheck("recipe", "Recipe", false, true, "red",
                        String.format("Recipe校验失败: product=%s, step=%s, equipment=%s - %s",
                                lot.getProductCode(), stepCode, equipmentCode, e.getMessage())));
            }
        } else {
            checks.add(trackInCheck("recipe", "Recipe", false, true, "gray", "Route或设备未通过，暂不匹配Recipe"));
        }

        boolean holdReady = true;
        String holdText = "无未解除Hold";
        String holdType = "green";
        if (lot.getHoldFlag() != null && lot.getHoldFlag() == 1) {
            Long holdCount = holdRecordMapper.selectCount(new LambdaQueryWrapper<HoldRecord>()
                    .eq(HoldRecord::getLotNo, lotNo)
                    .eq(HoldRecord::getStatus, "HOLD"));
            holdReady = holdCount == null || holdCount <= 0;
            holdText = holdReady ? "Hold标识为1但无打开Hold记录" : "Lot处于Hold状态，不能进站: " + lotNo;
            holdType = holdReady ? "amber" : "red";
        }
        checks.add(trackInCheck("hold", "Hold状态", holdReady, true, holdType, holdText));

        if (!holdReady) {
            checks.add(trackInCheck("shift", "班次窗口", false, true, "gray", "Hold未解除，班次校验未执行"));
            checks.add(trackInCheck("material", "物料齐套", false, true, "gray", "Hold未解除，物料校验未执行"));
            result.put("materialReady", false);
            return trackInCheckResult(new TrackInEvaluation(lot, equipment, recipe, result), checks);
        }

        Map<String, Object> shiftCheck = activeShiftCheck(lot);
        checks.add(shiftCheck);
        boolean shiftReady = Boolean.TRUE.equals(shiftCheck.get("passed"));

        boolean materialReady = false;
        if (routeReady && equipmentUsable && capabilityReady && recipe != null && shiftReady) {
            try {
                materialService.validateReadiness(lot, stepCode);
                materialReady = true;
                checks.add(trackInCheck("material", "物料齐套", true, true, "green", "关键物料批次齐套"));
            } catch (BusinessException e) {
                checks.add(trackInCheck("material", "物料齐套", false, true, "red", e.getMessage()));
            }
        } else {
            String materialText = materialSkipText(routeReady, equipmentUsable, capabilityReady, recipe, shiftReady);
            checks.add(trackInCheck("material", "物料齐套", false, true, "gray", materialText));
        }
        result.put("materialReady", materialReady);

        return trackInCheckResult(new TrackInEvaluation(lot, equipment, recipe, result), checks);
    }

    private void assertTrackInChecksPassed(Map<String, Object> result) {
        if (Boolean.TRUE.equals(result.get("trackInReady"))) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> checks = (List<Map<String, Object>>) result.getOrDefault("checks", List.of());
        String reasons = checks.stream()
                .filter(check -> !Boolean.FALSE.equals(check.get("blocking")))
                .filter(check -> !Boolean.TRUE.equals(check.get("passed")))
                .map(check -> check.get("title") + "=" + check.get("text"))
                .collect(java.util.stream.Collectors.joining("; "));
        throw new BusinessException("Track In预校验未通过: " + reasons);
    }

    private TrackInEvaluation trackInCheckResult(TrackInEvaluation evaluation, List<Map<String, Object>> checks) {
        long passedCount = checks.stream().filter(check -> Boolean.TRUE.equals(check.get("passed"))).count();
        long blockingFailedCount = checks.stream()
                .filter(check -> !Boolean.FALSE.equals(check.get("blocking")))
                .filter(check -> !Boolean.TRUE.equals(check.get("passed")))
                .count();
        Map<String, Object> result = evaluation.result();
        result.put("checks", checks);
        result.put("passedCount", passedCount);
        result.put("total", checks.size());
        result.put("blockingFailedCount", blockingFailedCount);
        result.put("trackInReady", blockingFailedCount == 0);
        return evaluation;
    }

    private Map<String, Object> trackInCheck(String key, String title, boolean passed, boolean blocking,
                                             String type, String text) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("key", key);
        check.put("title", title);
        check.put("passed", passed);
        check.put("blocking", blocking);
        check.put("type", type);
        check.put("text", valueOr(text, "-"));
        return check;
    }

    private void addUnavailableTrackInChecks(List<Map<String, Object>> checks, String text) {
        checks.add(trackInCheck("route", "Route下一站", false, true, "gray", text));
        checks.add(trackInCheck("equipmentStatus", "设备状态", false, true, "gray", text));
        checks.add(trackInCheck("equipmentCapability", "设备能力", false, true, "gray", text));
        checks.add(trackInCheck("recipe", "Recipe", false, true, "gray", text));
        checks.add(trackInCheck("hold", "Hold状态", false, true, "gray", text));
        checks.add(trackInCheck("shift", "班次窗口", false, true, "gray", text));
        checks.add(trackInCheck("material", "物料齐套", false, true, "gray", text));
    }

    private String materialSkipText(boolean routeReady, boolean equipmentUsable, boolean capabilityReady,
                                    Recipe recipe, boolean shiftReady) {
        if (!routeReady) {
            return "Route未通过，暂不校验物料";
        }
        if (!equipmentUsable) {
            return "设备状态未通过，物料校验未执行";
        }
        if (!capabilityReady) {
            return "设备能力未通过，物料校验未执行";
        }
        if (recipe == null) {
            return "Recipe未通过，物料校验未执行";
        }
        if (!shiftReady) {
            return "班次未通过，物料校验未执行";
        }
        return "上游校验未通过，物料校验未执行";
    }

    private Map<String, Object> activeShiftCheck(Lot lot) {
        String lineCode = lot.getLineCode();
        if (lineCode == null || lineCode.isBlank()) {
            return trackInCheck("shift", "班次窗口", false, true, "red",
                    "Track In班次校验失败: Lot未绑定产线 " + lot.getLotNo());
        }
        List<WorkShift> shifts = workShiftMapper.selectList(new LambdaQueryWrapper<WorkShift>()
                .eq(WorkShift::getLineCode, lineCode)
                .eq(WorkShift::getStatus, "ACTIVE"));
        if (shifts == null || shifts.isEmpty()) {
            return trackInCheck("shift", "班次窗口", false, true, "red",
                    "Track In班次校验失败: 产线无ACTIVE班次 " + lineCode);
        }
        LocalTime now = LocalTime.now();
        boolean matched = shifts.stream().anyMatch(shift -> isWithinShift(now, shift));
        if (!matched) {
            return trackInCheck("shift", "班次窗口", false, true, "red",
                    "Track In班次校验失败: 当前时间不在产线ACTIVE班次窗口 " + lineCode);
        }
        return trackInCheck("shift", "班次窗口", true, true, "green",
                "命中ACTIVE班次 " + shifts.size() + " 个");
    }

    private void validateActiveShift(Lot lot) {
        String lineCode = lot.getLineCode();
        if (lineCode == null || lineCode.isBlank()) {
            throw new BusinessException("Track In班次校验失败: Lot未绑定产线 " + lot.getLotNo());
        }
        List<WorkShift> shifts = workShiftMapper.selectList(new LambdaQueryWrapper<WorkShift>()
                .eq(WorkShift::getLineCode, lineCode)
                .eq(WorkShift::getStatus, "ACTIVE"));
        if (shifts == null || shifts.isEmpty()) {
            throw new BusinessException("Track In班次校验失败: 产线无ACTIVE班次 " + lineCode);
        }
        LocalTime now = LocalTime.now();
        boolean matched = shifts.stream().anyMatch(shift -> isWithinShift(now, shift));
        if (!matched) {
            throw new BusinessException("Track In班次校验失败: 当前时间不在产线ACTIVE班次窗口 " + lineCode);
        }
    }

    private boolean isWithinShift(LocalTime now, WorkShift shift) {
        LocalTime start = shift.getStartTime();
        LocalTime end = shift.getEndTime();
        if (start == null || end == null) {
            return false;
        }
        if (start.equals(end)) {
            return true;
        }
        boolean crossDay = Integer.valueOf(1).equals(shift.getCrossDay()) || start.isAfter(end);
        if (crossDay) {
            return !now.isBefore(start) || now.isBefore(end);
        }
        return !now.isBefore(start) && now.isBefore(end);
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record TrackInEvaluation(Lot lot, Equipment equipment, Recipe recipe, Map<String, Object> result) {
    }
}
