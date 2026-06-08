package com.visionox.mes.lot.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.visionox.mes.common.Result;
import com.visionox.mes.lot.dto.HoldRequest;
import com.visionox.mes.lot.dto.ReleaseRequest;
import com.visionox.mes.lot.dto.TrackInRequest;
import com.visionox.mes.lot.dto.TrackOutRequest;
import com.visionox.mes.lot.entity.Lot;
import com.visionox.mes.lot.service.HoldService;
import com.visionox.mes.lot.service.TrackInService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Lot流转控制接口
 *
 * 核心功能：
 * - Lot列表查询（分页）
 * - Track In（6层校验）
 * - Track Out（记录加工参数）
 * - Hold（异常控制）
 * - Release（放行）
 */
@Tag(name = "Lot流转控制", description = "Lot Track In/Out、Hold/Release接口")
@RestController
@RequestMapping("/lots")
@RequiredArgsConstructor
public class LotController {

    private final TrackInService trackInService;
    private final HoldService holdService;

    /**
     * Lot分页查询
     */
    @Operation(summary = "Lot分页查询", description = "分页查询Lot列表，支持按lotNo、status筛选")
    @GetMapping
    public Result<IPage<Lot>> pageLots(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Long current,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Long size,
            @Parameter(description = "Lot批次号") @RequestParam(required = false) String lotNo,
            @Parameter(description = "状态") @RequestParam(required = false) String status) {
        Page<Lot> page = new Page<>(current, size);
        IPage<Lot> result = trackInService.pageLots(page, lotNo, status);
        return Result.success(result);
    }

    /**
     * Track In - Lot进站
     */
    @Operation(summary = "Track In", description = "Lot进站，执行6层校验：Lot状态、工序合法性、设备状态、设备能力、Recipe、Hold状态")
    @PostMapping("/{lotNo}/track-in")
    public Result<Void> trackIn(@PathVariable String lotNo,
                                 @Validated @RequestBody TrackInRequest request) {
        request.setLotNo(lotNo);
        trackInService.trackIn(request);
        return Result.success();
    }

    /**
     * Track Out - Lot出站
     */
    @Operation(summary = "Track Out", description = "Lot出站，记录加工参数和结果。NG结果会自动触发Hold")
    @PostMapping("/{lotNo}/track-out")
    public Result<String> trackOut(@PathVariable String lotNo,
                                   @Validated @RequestBody TrackOutRequest request) {
        request.setLotNo(lotNo);
        return Result.success(trackInService.trackOut(request));
    }

    /**
     * Hold - 暂停Lot
     */
    @Operation(summary = "Hold Lot", description = "暂停Lot流转，Hold状态的Lot不能Track In")
    @PostMapping("/{lotNo}/hold")
    public Result<Void> holdLot(@PathVariable String lotNo,
                                 @Validated @RequestBody HoldRequest request) {
        request.setLotNo(lotNo);
        holdService.holdLot(request);
        return Result.success();
    }

    /**
     * Release - 放行Lot
     */
    @Operation(summary = "Release Lot", description = "处理异常后放行Lot，恢复流转")
    @PostMapping("/{lotNo}/release")
    public Result<Void> releaseLot(@PathVariable String lotNo,
                                    @Validated @RequestBody ReleaseRequest request) {
        request.setLotNo(lotNo);
        holdService.releaseLot(request);
        return Result.success();
    }
}
