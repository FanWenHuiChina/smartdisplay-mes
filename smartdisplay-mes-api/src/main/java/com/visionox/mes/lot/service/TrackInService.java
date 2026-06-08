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
import java.util.List;

/**
 * Track In/Out服务
 *
 * 核心业务逻辑：
 * Track In 8层校验（参考显示行业通用 MES 执行控制模型）：
 * 1. Lot状态校验 - 必须是READY状态
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

        // ====== 第1层：Lot状态校验 ======
        Lot lot = lotMapper.selectOne(
                new LambdaQueryWrapper<Lot>()
                        .eq(Lot::getLotNo, request.getLotNo())
        );
        if (lot == null) {
            throw new BusinessException("Lot不存在: " + request.getLotNo());
        }

        if (!"READY".equals(lot.getStatus())) {
            throw new BusinessException(
                    String.format("Lot状态不允许进站: 当前状态=%s, 期望状态=READY", lot.getStatus())
            );
        }

        // ====== 第2层：工序合法性校验 ======
        routeService.validateTrackInStep(lot.getProductCode(), lot.getCurrentStepCode(), request.getStepCode());

        // ====== 第3层：设备状态校验 ======
        Equipment equipment = equipmentMapper.selectOne(
                new LambdaQueryWrapper<Equipment>()
                        .eq(Equipment::getEquipmentCode, request.getEquipmentCode())
        );
        if (equipment == null) {
            throw new BusinessException("设备不存在: " + request.getEquipmentCode());
        }

        if (!"IDLE".equals(equipment.getStatus()) && !"RUNNING".equals(equipment.getStatus())) {
            throw new BusinessException(
                    String.format("设备状态不可用: equipment=%s, status=%s",
                            request.getEquipmentCode(), equipment.getStatus())
            );
        }

        // ====== 第4层：设备能力校验 ======
        if (!checkEquipmentCapability(equipment, request.getStepCode())) {
            throw new BusinessException(
                    String.format("设备不支持该工序: equipment=%s, step=%s",
                            request.getEquipmentCode(), request.getStepCode())
            );
        }

        // ====== 第5层：Recipe校验 ======
        Recipe recipe;
        try {
            recipe = recipeService.findActiveRecipe(
                    lot.getProductCode(),
                    request.getStepCode(),
                    request.getEquipmentCode()
            );
            log.debug("找到有效Recipe: {}", recipe.getRecipeCode());
        } catch (BusinessException e) {
            throw new BusinessException(
                    String.format("Recipe校验失败: product=%s, step=%s, equipment=%s - %s",
                            lot.getProductCode(), request.getStepCode(),
                            request.getEquipmentCode(), e.getMessage())
            );
        }

        // ====== 第6层：Hold状态校验 ======
        if (lot.getHoldFlag() != null && lot.getHoldFlag() == 1) {
            // 查询未Release的Hold记录
            Long holdCount = holdRecordMapper.selectCount(
                    new LambdaQueryWrapper<HoldRecord>()
                            .eq(HoldRecord::getLotNo, request.getLotNo())
                            .eq(HoldRecord::getStatus, "HOLD")
            );
            if (holdCount > 0) {
                throw new BusinessException("Lot处于Hold状态，不能进站: " + request.getLotNo());
            }
        }

        // ====== 第7层：班次校验 ======
        validateActiveShift(lot);

        // ====== 第8层：关键物料齐套校验 ======
        materialService.validateReadiness(lot, request.getStepCode());

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
            log.error("解析设备能力失败: equipment={}, capabilitySteps={}",
                    equipment.getEquipmentCode(), capabilitySteps, e);
            return false;
        }
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
}
