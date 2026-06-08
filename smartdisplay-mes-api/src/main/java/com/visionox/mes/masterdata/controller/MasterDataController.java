package com.visionox.mes.masterdata.controller;

import com.visionox.mes.common.Result;
import com.visionox.mes.lot.entity.Equipment;
import com.visionox.mes.lot.entity.ProcessStep;
import com.visionox.mes.masterdata.entity.ProductionLine;
import com.visionox.mes.masterdata.entity.Site;
import com.visionox.mes.masterdata.entity.WorkShift;
import com.visionox.mes.masterdata.service.MasterDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 主数据管理接口
 *
 * 提供工序、设备等基础数据查询
 */
@Tag(name = "主数据管理", description = "工序、设备等基础数据接口")
@RestController
@RequestMapping("/master-data")
@RequiredArgsConstructor
public class MasterDataController {

    private final MasterDataService masterDataService;

    @Operation(summary = "获取基地列表", description = "查询基地/厂区主数据")
    @GetMapping("/sites")
    public Result<List<Site>> getSites() {
        return Result.success(masterDataService.getAllSites());
    }

    @Operation(summary = "获取产线列表", description = "查询产线主数据")
    @GetMapping("/production-lines")
    public Result<List<ProductionLine>> getProductionLines(@RequestParam(required = false) String siteCode,
                                                           @RequestParam(required = false) String status) {
        return Result.success(masterDataService.getAllProductionLines(siteCode, status));
    }

    @Operation(summary = "获取班次列表", description = "查询班次主数据")
    @GetMapping("/shifts")
    public Result<List<WorkShift>> getWorkShifts(@RequestParam(required = false) String lineCode,
                                                 @RequestParam(required = false) String status) {
        return Result.success(masterDataService.getAllWorkShifts(lineCode, status));
    }

    /**
     * 获取所有工序列表
     */
    @Operation(summary = "获取工序列表", description = "查询所有工序定义")
    @GetMapping("/process-steps")
    public Result<List<ProcessStep>> getProcessSteps() {
        List<ProcessStep> steps = masterDataService.getAllProcessSteps();
        return Result.success(steps);
    }

    /**
     * 获取所有设备列表
     */
    @Operation(summary = "获取设备列表", description = "查询所有设备信息")
    @GetMapping("/equipments")
    public Result<List<Equipment>> getEquipments() {
        List<Equipment> equipments = masterDataService.getAllEquipments();
        return Result.success(equipments);
    }
}
