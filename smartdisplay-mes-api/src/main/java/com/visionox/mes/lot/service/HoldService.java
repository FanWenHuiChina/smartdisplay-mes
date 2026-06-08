package com.visionox.mes.lot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.visionox.mes.common.BusinessException;
import com.visionox.mes.lot.dto.HoldRequest;
import com.visionox.mes.lot.dto.ReleaseRequest;
import com.visionox.mes.lot.entity.HoldRecord;
import com.visionox.mes.lot.entity.Lot;
import com.visionox.mes.lot.mapper.HoldRecordMapper;
import com.visionox.mes.lot.mapper.LotMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Hold/Release服务
 *
 * 业务说明：
 * - Hold是异常控制手段，Hold状态的Lot不能继续Track In
 * - Release后Lot可继续流转
 * - Hold/Release记录完整追溯链路
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HoldService {

    private final LotMapper lotMapper;
    private final HoldRecordMapper holdRecordMapper;

    /**
     * Hold操作
     */
    @Transactional(rollbackFor = Exception.class)
    public void holdLot(HoldRequest request) {
        log.info("Hold Lot: lotNo={}, reason={}", request.getLotNo(), request.getHoldReason());

        // 1. 查询Lot
        Lot lot = lotMapper.selectOne(
                new LambdaQueryWrapper<Lot>()
                        .eq(Lot::getLotNo, request.getLotNo())
        );
        if (lot == null) {
            throw new BusinessException("Lot不存在: " + request.getLotNo());
        }

        // 2. 检查是否已经Hold
        if (lot.getHoldFlag() != null && lot.getHoldFlag() == 1) {
            Long holdCount = holdRecordMapper.selectCount(
                    new LambdaQueryWrapper<HoldRecord>()
                            .eq(HoldRecord::getLotNo, request.getLotNo())
                            .eq(HoldRecord::getStatus, "HOLD")
            );
            if (holdCount > 0) {
                throw new BusinessException("Lot已经处于Hold状态: " + request.getLotNo());
            }
        }

        // 3. 更新Lot状态
        lot.setHoldFlag(1);
        lot.setStatus("HOLD");
        lotMapper.updateById(lot);

        // 4. 创建Hold记录
        HoldRecord holdRecord = new HoldRecord();
        holdRecord.setLotNo(request.getLotNo());
        holdRecord.setHoldReason(request.getHoldReason());
        holdRecord.setHoldType(request.getHoldType() != null ? request.getHoldType() : "QUALITY");
        holdRecord.setHoldBy(request.getHoldBy() != null ? request.getHoldBy() : "system");
        holdRecord.setHoldTime(LocalDateTime.now());
        holdRecord.setStatus("HOLD");
        holdRecordMapper.insert(holdRecord);

        log.info("Lot Hold成功: lotNo={}, holdRecordId={}", request.getLotNo(), holdRecord.getId());
    }

    /**
     * Release操作
     */
    @Transactional(rollbackFor = Exception.class)
    public void releaseLot(ReleaseRequest request) {
        log.info("Release Lot: lotNo={}, disposition={}", request.getLotNo(), request.getDisposition());

        // 1. 查询Lot
        Lot lot = lotMapper.selectOne(
                new LambdaQueryWrapper<Lot>()
                        .eq(Lot::getLotNo, request.getLotNo())
        );
        if (lot == null) {
            throw new BusinessException("Lot不存在: " + request.getLotNo());
        }

        // 2. 检查是否处于Hold状态
        if (lot.getHoldFlag() == null || lot.getHoldFlag() != 1) {
            throw new BusinessException("Lot不处于Hold状态: " + request.getLotNo());
        }

        // 3. 查找未Release的Hold记录
        HoldRecord holdRecord = holdRecordMapper.selectOne(
                new LambdaQueryWrapper<HoldRecord>()
                        .eq(HoldRecord::getLotNo, request.getLotNo())
                        .eq(HoldRecord::getStatus, "HOLD")
                        .orderByDesc(HoldRecord::getHoldTime)
                        .last("LIMIT 1")
        );

        if (holdRecord == null) {
            throw new BusinessException("未找到Hold记录: " + request.getLotNo());
        }

        // 4. 更新Hold记录
        holdRecord.setReleaseBy(request.getReleaseBy() != null ? request.getReleaseBy() : "system");
        holdRecord.setReleaseTime(LocalDateTime.now());
        holdRecord.setDisposition(request.getDisposition());
        holdRecord.setStatus("RELEASED");
        holdRecordMapper.updateById(holdRecord);

        // 5. 更新Lot状态
        lot.setHoldFlag(0);
        lot.setStatus("READY"); // Release后恢复为READY状态
        lotMapper.updateById(lot);

        log.info("Lot Release成功: lotNo={}", request.getLotNo());
    }
}
