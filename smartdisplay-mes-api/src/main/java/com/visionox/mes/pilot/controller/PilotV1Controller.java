package com.visionox.mes.pilot.controller;

import com.visionox.mes.auth.dto.LoginRequest;
import com.visionox.mes.auth.dto.LoginResponse;
import com.visionox.mes.auth.service.AuthService;
import com.visionox.mes.common.Result;
import com.visionox.mes.lot.entity.Equipment;
import com.visionox.mes.lot.entity.Lot;
import com.visionox.mes.lot.entity.ProcessStep;
import com.visionox.mes.order.entity.ProductionOrder;
import com.visionox.mes.masterdata.entity.ProductionLine;
import com.visionox.mes.masterdata.entity.Site;
import com.visionox.mes.masterdata.entity.WorkShift;
import com.visionox.mes.masterdata.service.MasterDataService;
import com.visionox.mes.pilot.service.PilotMesService;
import com.visionox.mes.recipe.dto.RecipeDetailVO;
import com.visionox.mes.recipe.entity.Recipe;
import com.visionox.mes.recipe.service.RecipeService;
import com.visionox.mes.auth.entity.User;
import com.visionox.mes.system.entity.PermissionChangeRequest;
import com.visionox.mes.system.service.PermissionChangeService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 生产级试点版 API v1。
 *
 * <p>该控制器统一承载前端工作台需要的首批闭环接口；已建模的工单、Lot、Recipe、
 * 设备、工序读取真实表，质量、物料、追溯扩展和 AI 数据先走模拟适配器。</p>
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class PilotV1Controller {

    private final AuthService authService;
    private final RecipeService recipeService;
    private final PilotMesService pilotMesService;
    private final PermissionChangeService permissionChangeService;
    private final MasterDataService masterDataService;

    @Operation(summary = "用户登录并签发 JWT", tags = {"认证"})
    @PostMapping("/auth/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success("登录成功", authService.login(request));
    }

    @Operation(tags = {"系统与权限"})
    @GetMapping("/system/users")
    public Result<List<User>> users() {
        return Result.success(pilotMesService.users());
    }

    @Operation(tags = {"系统与权限"})
    @GetMapping("/system/audit-logs")
    public Result<?> auditLogs(@RequestParam(required = false) String bizNo,
                               @RequestParam(required = false) String action,
                               @RequestParam(required = false) String result,
                               @RequestParam(required = false) String source,
                               @RequestParam(required = false) String operator,
                               @RequestParam(required = false) String startTime,
                               @RequestParam(required = false) String endTime,
                               @RequestParam(required = false) Long current,
                               @RequestParam(required = false) Long size) {
        if (current == null && size == null && action == null && result == null
                && source == null && operator == null && startTime == null && endTime == null) {
            return Result.success(pilotMesService.auditLogs(bizNo));
        }
        return Result.success(pilotMesService.pageAuditLogs(current, size, bizNo, action, result, source, operator, startTime, endTime));
    }

    @Operation(tags = {"系统与权限"})
    @GetMapping("/system/summary")
    public Result<Map<String, Object>> systemSummary() {
        return Result.success(pilotMesService.systemSummary());
    }

    @Operation(tags = {"系统与权限"})
    @GetMapping("/system/me/permissions")
    public Result<Map<String, Object>> currentPermissions() {
        return Result.success(pilotMesService.currentPermissions());
    }

    @Operation(tags = {"系统与权限"})
    @GetMapping("/system/permission-change-requests")
    public Result<List<PermissionChangeRequest>> permissionChangeRequests(@RequestParam(required = false) String status) {
        return Result.success(permissionChangeService.listChangeRequests(status));
    }

    @Operation(tags = {"系统与权限"})
    @PostMapping("/system/permission-change-requests")
    public Result<PermissionChangeRequest> createPermissionChangeRequest(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(permissionChangeService.createChangeRequest(request));
    }

    @Operation(tags = {"系统与权限"})
    @PostMapping("/system/permission-change-requests/{changeNo}/review")
    public Result<PermissionChangeRequest> reviewPermissionChangeRequest(@PathVariable String changeNo,
                                                                         @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(permissionChangeService.reviewChangeRequest(changeNo, request));
    }

    @Operation(tags = {"系统与权限"})
    @PostMapping("/system/permissions/reload")
    public Result<Map<String, Object>> reloadPermissions() {
        int appliedRoles = permissionChangeService.reloadApprovedPermissionSnapshots();
        return Result.success(Map.of("appliedRoles", appliedRoles));
    }

    @Operation(tags = {"主数据与工艺"})
    @GetMapping("/master/products")
    public Result<List<Map<String, Object>>> products() {
        return Result.success(pilotMesService.products());
    }

    @Operation(tags = {"主数据与工艺"})
    @GetMapping("/master/sites")
    public Result<List<Site>> sites() {
        return Result.success(masterDataService.getAllSites());
    }

    @Operation(tags = {"主数据与工艺"})
    @GetMapping("/master/production-lines")
    public Result<List<ProductionLine>> productionLines(@RequestParam(required = false) String siteCode,
                                                        @RequestParam(required = false) String status) {
        return Result.success(masterDataService.getAllProductionLines(siteCode, status));
    }

    @Operation(tags = {"主数据与工艺"})
    @GetMapping("/master/shifts")
    public Result<List<WorkShift>> shifts(@RequestParam(required = false) String lineCode,
                                          @RequestParam(required = false) String status) {
        return Result.success(masterDataService.getAllWorkShifts(lineCode, status));
    }

    @Operation(tags = {"主数据与工艺"})
    @GetMapping("/master/process-steps")
    public Result<List<ProcessStep>> processSteps() {
        return Result.success(pilotMesService.processSteps());
    }

    @Operation(tags = {"主数据与工艺"})
    @GetMapping("/master/equipments")
    public Result<List<Equipment>> equipments() {
        return Result.success(pilotMesService.equipments());
    }

    @Operation(tags = {"主数据与工艺"})
    @GetMapping("/master/defect-codes")
    public Result<List<Map<String, Object>>> defectCodes() {
        return Result.success(pilotMesService.defectCodes());
    }

    @Operation(tags = {"主数据与工艺"})
    @GetMapping("/routes")
    public Result<List<Map<String, Object>>> routes() {
        return Result.success(pilotMesService.routes());
    }

    @Operation(tags = {"主数据与工艺"})
    @GetMapping("/boms")
    public Result<List<Map<String, Object>>> boms() {
        return Result.success(pilotMesService.boms());
    }

    @Operation(tags = {"主数据与工艺"})
    @GetMapping("/boms/change-requests")
    public Result<List<Map<String, Object>>> bomChangeRequests(@RequestParam(required = false) String status) {
        return Result.success(pilotMesService.bomChangeRequests(status));
    }

    @Operation(tags = {"主数据与工艺"})
    @GetMapping("/boms/eco-approvals")
    public Result<List<Map<String, Object>>> bomEcoApprovalTasks(@RequestParam(required = false) String changeNo,
                                                                 @RequestParam(required = false) String status) {
        return Result.success(pilotMesService.bomEcoApprovalTasks(changeNo, status));
    }

    @Operation(summary = "提交 BOM 变更申请", tags = {"主数据与工艺"})
    @PostMapping("/boms/change-requests")
    public Result<Map<String, Object>> submitBomChange(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.submitBomChange(request));
    }

    @Operation(summary = "BOM ECO 跨部门会签决策", tags = {"主数据与工艺"})
    @PostMapping("/boms/eco-approvals/{taskNo}/decision")
    public Result<Map<String, Object>> decideBomEcoApproval(@PathVariable String taskNo,
                                                            @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.decideBomEcoApproval(taskNo, request));
    }

    @Operation(tags = {"主数据与工艺"})
    @PostMapping("/boms/change-requests/{changeNo}/review")
    public Result<Map<String, Object>> reviewBomChange(@PathVariable String changeNo,
                                                       @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.reviewBomChange(changeNo, request));
    }

    @Operation(tags = {"主数据与工艺"})
    @PostMapping("/boms/change-requests/{changeNo}/publish")
    public Result<Map<String, Object>> publishBomChange(@PathVariable String changeNo,
                                                        @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.publishBomChange(changeNo, request));
    }

    @Operation(summary = "分页查询 Recipe", tags = {"主数据与工艺"})
    @GetMapping("/recipes")
    public Result<Page<Recipe>> recipes(@RequestParam(defaultValue = "1") long current,
                                        @RequestParam(defaultValue = "20") long size,
                                        @RequestParam(required = false) String productCode,
                                        @RequestParam(required = false) String stepCode,
                                        @RequestParam(required = false) String equipmentCode,
                                        @RequestParam(required = false) String status) {
        return Result.success(pilotMesService.pageRecipes(current, size, productCode, stepCode, equipmentCode, status));
    }

    @Operation(tags = {"主数据与工艺"})
    @GetMapping("/recipes/search")
    public Result<Recipe> searchRecipe(@RequestParam String productCode,
                                       @RequestParam String stepCode,
                                       @RequestParam String equipmentCode) {
        return Result.success(recipeService.findActiveRecipe(productCode, stepCode, equipmentCode));
    }

    @Operation(tags = {"主数据与工艺"})
    @GetMapping("/recipes/{id}")
    public Result<RecipeDetailVO> recipeDetail(@PathVariable Long id) {
        return Result.success(recipeService.getRecipeDetail(id));
    }

    @Operation(summary = "发布 Recipe(强制单一激活)", tags = {"主数据与工艺"})
    @PostMapping("/recipes/{id}/publish")
    public Result<Void> publishRecipe(@PathVariable Long id) {
        recipeService.publishRecipe(id);
        return Result.success();
    }

    @Operation(summary = "分页查询工单", tags = {"工单管理"})
    @GetMapping("/orders")
    public Result<Page<ProductionOrder>> orders(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "20") long size,
                                                @RequestParam(required = false) String status) {
        return Result.success(pilotMesService.pageOrders(current, size, status));
    }

    @Operation(summary = "创建工单", tags = {"工单管理"})
    @PostMapping("/orders")
    public Result<ProductionOrder> createOrder(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.createOrder(request));
    }

    @Operation(summary = "工单释放前置校验", tags = {"工单管理"})
    @GetMapping("/orders/{orderNo}/release-checks")
    public Result<Map<String, Object>> orderReleaseChecks(@PathVariable String orderNo,
                                                          @RequestParam(defaultValue = "100") int lotQty) {
        return Result.success(pilotMesService.orderReleaseChecks(orderNo, lotQty));
    }

    @Operation(summary = "释放工单并生成 Lot", tags = {"工单管理"})
    @PostMapping("/orders/{orderNo}/release")
    public Result<Map<String, Object>> releaseOrder(@PathVariable String orderNo,
                                                    @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.releaseOrder(orderNo, request));
    }

    @Operation(summary = "分页查询 Lot", tags = {"Lot 执行与处置"})
    @GetMapping("/lots")
    public Result<Page<Lot>> lots(@RequestParam(defaultValue = "1") long current,
                                  @RequestParam(defaultValue = "20") long size,
                                  @RequestParam(required = false) String lotNo,
                                  @RequestParam(required = false) String status) {
        return Result.success(pilotMesService.pageLots(current, size, lotNo, status));
    }

    @Operation(summary = "Lot 进站(校验 Route/设备/Recipe/Hold/班次/物料)", tags = {"Lot 执行与处置"})
    @PostMapping("/lots/{lotNo}/track-in")
    public Result<Void> trackIn(@PathVariable String lotNo,
                                @RequestBody(required = false) Map<String, Object> request) {
        pilotMesService.trackIn(lotNo, request);
        return Result.success();
    }

    @Operation(summary = "Lot 进站前置校验", tags = {"Lot 执行与处置"})
    @GetMapping("/lots/{lotNo}/track-in-checks")
    public Result<Map<String, Object>> trackInChecks(@PathVariable String lotNo,
                                                     @RequestParam(required = false) String stepCode,
                                                     @RequestParam(required = false) String equipmentCode,
                                                     @RequestParam(required = false) String operator) {
        return Result.success(pilotMesService.trackInChecks(lotNo, Map.of(
                "stepCode", stepCode == null ? "" : stepCode,
                "equipmentCode", equipmentCode == null ? "" : equipmentCode,
                "operator", operator == null ? "" : operator
        )));
    }

    @Operation(summary = "Lot 出站并记录工艺参数与质量判定", tags = {"Lot 执行与处置"})
    @PostMapping("/lots/{lotNo}/track-out")
    public Result<Map<String, Object>> trackOut(@PathVariable String lotNo,
                                                @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.trackOut(lotNo, request));
    }

    @Operation(summary = "Lot 异常 Hold", tags = {"Lot 执行与处置"})
    @PostMapping("/lots/{lotNo}/hold")
    public Result<Void> hold(@PathVariable String lotNo,
                             @RequestBody(required = false) Map<String, Object> request) {
        pilotMesService.hold(lotNo, request);
        return Result.success();
    }

    @Operation(summary = "批量 Hold", tags = {"Lot 执行与处置"})
    @PostMapping("/lots/batch-hold")
    public Result<Map<String, Object>> batchHold(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.batchHold(request));
    }

    @Operation(summary = "解除 Hold 放行", tags = {"Lot 执行与处置"})
    @PostMapping("/lots/{lotNo}/release")
    public Result<Void> release(@PathVariable String lotNo,
                                @RequestBody(required = false) Map<String, Object> request) {
        pilotMesService.release(lotNo, request);
        return Result.success();
    }

    @Operation(summary = "批量放行", tags = {"Lot 执行与处置"})
    @PostMapping("/lots/batch-release")
    public Result<Map<String, Object>> batchRelease(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.batchRelease(request));
    }

    @Operation(summary = "返工处置", tags = {"Lot 执行与处置"})
    @PostMapping("/lots/{lotNo}/rework")
    public Result<Void> rework(@PathVariable String lotNo,
                               @RequestBody(required = false) Map<String, Object> request) {
        pilotMesService.rework(lotNo, request);
        return Result.success();
    }

    @Operation(summary = "报废处置", tags = {"Lot 执行与处置"})
    @PostMapping("/lots/{lotNo}/scrap")
    public Result<Void> scrap(@PathVariable String lotNo,
                              @RequestBody(required = false) Map<String, Object> request) {
        pilotMesService.scrap(lotNo, request);
        return Result.success();
    }

    @Operation(tags = {"质量与 MRB"})
    @GetMapping("/quality/inspections")
    public Result<List<Map<String, Object>>> qualityInspections(@RequestParam(required = false) String lotNo) {
        return Result.success(pilotMesService.qualityInspections(lotNo));
    }

    @Operation(summary = "查询质量异常", tags = {"质量与 MRB"})
    @GetMapping("/quality/exceptions")
    public Result<List<Map<String, Object>>> qualityExceptions(@RequestParam(required = false) String lotNo,
                                                               @RequestParam(required = false) String sourceModule,
                                                               @RequestParam(required = false) String status) {
        return Result.success(pilotMesService.qualityExceptions(lotNo, sourceModule, status));
    }

    @Operation(tags = {"质量与 MRB"})
    @GetMapping("/quality/exceptions/{eventNo}/mrb-records")
    public Result<List<Map<String, Object>>> qualityMrbRecords(@PathVariable String eventNo) {
        return Result.success(pilotMesService.qualityMrbRecords(eventNo));
    }

    @Operation(tags = {"质量与 MRB"})
    @GetMapping("/quality/mrb-records/{mrbNo}/minutes")
    public Result<List<Map<String, Object>>> qualityMrbMinutes(@PathVariable String mrbNo) {
        return Result.success(pilotMesService.qualityMrbMinutes(mrbNo));
    }

    @Operation(tags = {"质量与 MRB"})
    @PostMapping("/quality/mrb-records/{mrbNo}/minutes")
    public Result<Map<String, Object>> createQualityMrbMinutes(@PathVariable String mrbNo,
                                                               @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.createQualityMrbMinutes(mrbNo, request));
    }

    @Operation(summary = "查询 MRB 会签任务", tags = {"质量与 MRB"})
    @GetMapping("/quality/mrb-approvals")
    public Result<List<Map<String, Object>>> qualityMrbApprovalTasks(@RequestParam(required = false) String eventNo,
                                                                     @RequestParam(required = false) String status) {
        return Result.success(pilotMesService.qualityMrbApprovalTasks(eventNo, status));
    }

    @Operation(summary = "刷新 MRB 会签 SLA 并升级逾期", tags = {"质量与 MRB"})
    @PostMapping("/quality/mrb-approvals/refresh-sla")
    public Result<Map<String, Object>> refreshQualityMrbApprovalSla(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.refreshQualityMrbApprovalSla(request));
    }

    @Operation(summary = "MRB 会签通过", tags = {"质量与 MRB"})
    @PostMapping("/quality/mrb-approvals/{taskNo}/approve")
    public Result<Map<String, Object>> approveMrbTask(@PathVariable String taskNo,
                                                      @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.decideMrbApprovalTask(taskNo, mergeDecision(request, "APPROVE")));
    }

    @Operation(summary = "MRB 会签驳回", tags = {"质量与 MRB"})
    @PostMapping("/quality/mrb-approvals/{taskNo}/reject")
    public Result<Map<String, Object>> rejectMrbTask(@PathVariable String taskNo,
                                                     @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.decideMrbApprovalTask(taskNo, mergeDecision(request, "REJECT")));
    }

    @Operation(summary = "MRB 评审", tags = {"质量与 MRB"})
    @PostMapping("/quality/exceptions/{eventNo}/mrb-review")
    public Result<Map<String, Object>> reviewException(@PathVariable String eventNo,
                                                       @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.reviewException(eventNo, request));
    }

    @Operation(summary = "关闭质量异常", tags = {"质量与 MRB"})
    @PostMapping("/quality/exceptions/{eventNo}/close")
    public Result<Map<String, Object>> closeException(@PathVariable String eventNo,
                                                      @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.closeException(eventNo, request));
    }

    @Operation(summary = "创建质量检验记录", tags = {"质量与 MRB"})
    @PostMapping("/quality/inspections")
    public Result<Map<String, Object>> createInspection(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.createQualityInspection(request));
    }

    @Operation(tags = {"设备与 OEE"})
    @GetMapping("/equipment/events")
    public Result<List<Map<String, Object>>> equipmentEvents(@RequestParam(required = false) String equipmentCode,
                                                            @RequestParam(required = false) String status) {
        return Result.success(pilotMesService.equipmentEvents(equipmentCode, status));
    }

    @Operation(summary = "上报设备异常事件", tags = {"设备与 OEE"})
    @PostMapping("/equipment/events")
    public Result<Map<String, Object>> createEquipmentEvent(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.createEquipmentEvent(request));
    }

    @Operation(tags = {"设备与 OEE"})
    @PostMapping("/equipment/events/{eventNo}/close")
    public Result<Map<String, Object>> closeEquipmentEvent(@PathVariable String eventNo,
                                                          @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.closeEquipmentEvent(eventNo, request));
    }

    @Operation(tags = {"设备与 OEE"})
    @GetMapping("/equipment/oee")
    public Result<Map<String, Object>> equipmentOee(@RequestParam(required = false) String lineCode) {
        return Result.success(pilotMesService.equipmentOeeSummary(lineCode));
    }

    @Operation(tags = {"设备与 OEE"})
    @GetMapping("/equipment/status-history")
    public Result<List<Map<String, Object>>> equipmentStatusHistory(@RequestParam(required = false) String equipmentCode) {
        return Result.success(pilotMesService.equipmentStatusHistories(equipmentCode));
    }

    @Operation(tags = {"设备与 OEE"})
    @GetMapping("/equipment/gateways")
    public Result<List<Map<String, Object>>> equipmentGateways(@RequestParam(required = false) String status) {
        return Result.success(pilotMesService.equipmentGateways(status));
    }

    @Operation(tags = {"设备与 OEE"})
    @PostMapping("/equipment/gateways")
    public Result<Map<String, Object>> registerEquipmentGateway(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.registerEquipmentGateway(request));
    }

    @Operation(tags = {"设备与 OEE"})
    @PostMapping("/equipment/gateways/{gatewayCode}/heartbeat")
    public Result<Map<String, Object>> heartbeatEquipmentGateway(@PathVariable String gatewayCode,
                                                                @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.heartbeatEquipmentGateway(gatewayCode, request));
    }

    @Operation(summary = "设备网关健康检查", tags = {"设备与 OEE"})
    @PostMapping("/equipment/gateways/{gatewayCode}/health-check")
    public Result<Map<String, Object>> checkEquipmentGatewayHealth(@PathVariable String gatewayCode,
                                                                  @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.checkEquipmentGatewayHealth(gatewayCode, request));
    }

    @Operation(tags = {"设备与 OEE"})
    @GetMapping("/equipment/gateway-health-checks")
    public Result<List<Map<String, Object>>> equipmentGatewayHealthChecks(@RequestParam(required = false) String gatewayCode) {
        return Result.success(pilotMesService.equipmentGatewayHealthChecks(gatewayCode));
    }

    @Operation(tags = {"设备与 OEE"})
    @GetMapping("/equipment/gateway-messages")
    public Result<List<Map<String, Object>>> equipmentGatewayMessages(@RequestParam(required = false) String gatewayCode) {
        return Result.success(pilotMesService.equipmentGatewayMessages(gatewayCode));
    }

    @Operation(tags = {"设备与 OEE"})
    @GetMapping("/equipment/gateway-messages/{messageNo}")
    public Result<Map<String, Object>> equipmentGatewayMessageDetail(@PathVariable String messageNo) {
        return Result.success(pilotMesService.equipmentGatewayMessageDetail(messageNo));
    }

    @Operation(tags = {"设备与 OEE"})
    @GetMapping("/equipment/gateway-drivers")
    public Result<List<Map<String, Object>>> equipmentGatewayDrivers() {
        return Result.success(pilotMesService.equipmentGatewayDrivers());
    }

    @Operation(tags = {"设备与 OEE"})
    @PostMapping("/equipment/status/report")
    public Result<Map<String, Object>> reportEquipmentStatus(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.reportEquipmentStatus(request));
    }

    @Operation(tags = {"设备与 OEE"})
    @GetMapping("/equipment/cycle-samples")
    public Result<List<Map<String, Object>>> equipmentCycleSamples(@RequestParam(required = false) String equipmentCode) {
        return Result.success(pilotMesService.equipmentCycleSamples(equipmentCode));
    }

    @Operation(tags = {"设备与 OEE"})
    @GetMapping("/equipment/standard-cycles")
    public Result<List<Map<String, Object>>> equipmentStandardCycles(@RequestParam(required = false) String equipmentCode,
                                                                     @RequestParam(required = false) String status) {
        return Result.success(pilotMesService.equipmentStandardCycles(equipmentCode, status));
    }

    @Operation(tags = {"设备与 OEE"})
    @PostMapping("/equipment/standard-cycles")
    public Result<Map<String, Object>> publishEquipmentStandardCycle(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.publishEquipmentStandardCycle(request));
    }

    @Operation(tags = {"设备与 OEE"})
    @PostMapping("/equipment/cycle-samples/report")
    public Result<Map<String, Object>> reportEquipmentCycleSample(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.reportEquipmentCycleSample(request));
    }

    @Operation(tags = {"设备与 OEE"})
    @GetMapping("/equipment/parameters")
    public Result<List<Map<String, Object>>> equipmentParameterSamples(@RequestParam(required = false) String equipmentCode) {
        return Result.success(pilotMesService.equipmentParameterSamples(equipmentCode));
    }

    @Operation(tags = {"设备与 OEE"})
    @PostMapping("/equipment/parameters/report")
    public Result<Map<String, Object>> reportEquipmentParameters(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.reportEquipmentParameters(request));
    }

    @Operation(tags = {"设备与 OEE"})
    @GetMapping("/equipment/pm-tasks")
    public Result<List<Map<String, Object>>> equipmentPmTasks(@RequestParam(required = false) String status) {
        return Result.success(pilotMesService.equipmentPmTasks(status));
    }

    @Operation(tags = {"设备与 OEE"})
    @PostMapping("/equipment/pm-tasks/{taskNo}/complete")
    public Result<Map<String, Object>> completeEquipmentPmTask(@PathVariable String taskNo,
                                                              @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.completeEquipmentPmTask(taskNo, request));
    }

    @Operation(tags = {"设备与 OEE"})
    @GetMapping("/equipment/recipe-downloads")
    public Result<List<Map<String, Object>>> equipmentRecipeCommands(@RequestParam(required = false) String equipmentCode,
                                                                     @RequestParam(required = false) String status) {
        return Result.success(pilotMesService.equipmentRecipeCommands(equipmentCode, status));
    }

    @Operation(tags = {"设备与 OEE"})
    @PostMapping("/equipment/recipe-downloads")
    public Result<Map<String, Object>> downloadEquipmentRecipe(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.downloadEquipmentRecipe(request));
    }

    @Operation(summary = "EAP 设备消息接入(模拟适配器)", tags = {"外部集成适配器(模拟)"})
    @PostMapping("/adapters/eap/messages")
    public Result<Map<String, Object>> ingestEapMessage(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.ingestEapMessage(request));
    }

    @Operation(summary = "ERP 工单导入(模拟适配器)", tags = {"外部集成适配器(模拟)"})
    @PostMapping("/adapters/erp/orders")
    public Result<Map<String, Object>> importErpOrders(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.importErpOrders(request));
    }

    @Operation(summary = "QMS 检验结果接入(模拟适配器)", tags = {"外部集成适配器(模拟)"})
    @PostMapping("/adapters/qms/inspections")
    public Result<Map<String, Object>> ingestQmsInspection(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.ingestQmsInspection(request));
    }

    @Operation(summary = "WMS 备料齐套校验(模拟适配器)", tags = {"外部集成适配器(模拟)"})
    @PostMapping("/adapters/wms/material-readiness")
    public Result<Map<String, Object>> checkWmsMaterialReadiness(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.checkWmsMaterialReadiness(request));
    }

    @Operation(tags = {"外部集成适配器(模拟)"})
    @PostMapping("/adapters/wms/inventory-transactions")
    public Result<Map<String, Object>> ingestWmsInventoryTransaction(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.ingestWmsInventoryTransaction(request));
    }

    @Operation(tags = {"物料与 WMS"})
    @GetMapping("/material/batches")
    public Result<Map<String, Object>> materialBatches() {
        return Result.success(pilotMesService.materialReadiness());
    }

    @Operation(summary = "物料收料入库", tags = {"物料与 WMS"})
    @PostMapping("/material/receive")
    public Result<Map<String, Object>> receiveMaterial(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.receiveMaterial(request));
    }

    @Operation(tags = {"物料与 WMS"})
    @PostMapping("/material/batches/{batchNo}/freeze")
    public Result<Map<String, Object>> freezeMaterial(@PathVariable String batchNo,
                                                      @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.freezeMaterial(batchNo, request));
    }

    @Operation(tags = {"物料与 WMS"})
    @PostMapping("/material/batches/{batchNo}/unfreeze")
    public Result<Map<String, Object>> unfreezeMaterial(@PathVariable String batchNo,
                                                        @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.unfreezeMaterial(batchNo, request));
    }

    @Operation(tags = {"物料与 WMS"})
    @PostMapping("/material/batches/{batchNo}/return")
    public Result<Map<String, Object>> returnMaterial(@PathVariable String batchNo,
                                                      @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.returnMaterial(batchNo, request));
    }

    @Operation(tags = {"物料与 WMS"})
    @PostMapping("/material/batches/{batchNo}/inventory-count")
    public Result<Map<String, Object>> inventoryCount(@PathVariable String batchNo,
                                                      @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.inventoryCount(batchNo, request));
    }

    @Operation(tags = {"物料与 WMS"})
    @GetMapping("/material/inventory-transactions")
    public Result<List<Map<String, Object>>> materialInventoryTransactions(@RequestParam(required = false) String batchNo) {
        return Result.success(pilotMesService.materialInventoryTransactions(batchNo));
    }

    @Operation(tags = {"物料与 WMS"})
    @GetMapping("/material/incoming-inspections")
    public Result<List<Map<String, Object>>> materialIncomingInspections(@RequestParam(required = false) String batchNo) {
        return Result.success(pilotMesService.materialIncomingInspections(batchNo));
    }

    @Operation(tags = {"物料与 WMS"})
    @GetMapping("/material/suppliers/performance")
    public Result<List<Map<String, Object>>> materialSupplierPerformance() {
        return Result.success(pilotMesService.materialSupplierPerformance());
    }

    @Operation(tags = {"物料与 WMS"})
    @GetMapping("/material/suppliers/trends")
    public Result<List<Map<String, Object>>> materialSupplierTrends(@RequestParam(required = false, defaultValue = "6") Integer months) {
        return Result.success(pilotMesService.materialSupplierTrends(months == null ? 6 : months));
    }

    @Operation(tags = {"物料与 WMS"})
    @GetMapping("/material/suppliers")
    public Result<List<Map<String, Object>>> materialSuppliers() {
        return Result.success(pilotMesService.materialSuppliers());
    }

    @Operation(tags = {"物料与 WMS"})
    @PostMapping("/material/suppliers/{supplierCode}/qualification/evaluate")
    public Result<Map<String, Object>> evaluateMaterialSupplierQualification(@PathVariable String supplierCode,
                                                                            @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.evaluateMaterialSupplierQualification(supplierCode, request));
    }

    @Operation(tags = {"物料与 WMS"})
    @GetMapping("/material/suppliers/corrective-actions")
    public Result<List<Map<String, Object>>> materialSupplierCorrectiveActions(@RequestParam(required = false) String supplierCode,
                                                                               @RequestParam(required = false) String status) {
        return Result.success(pilotMesService.materialSupplierCorrectiveActions(supplierCode, status));
    }

    @Operation(tags = {"物料与 WMS"})
    @GetMapping("/material/suppliers/qualification-reviews")
    public Result<List<Map<String, Object>>> materialSupplierQualificationReviews(@RequestParam(required = false) String supplierCode,
                                                                                 @RequestParam(required = false) String status) {
        return Result.success(pilotMesService.materialSupplierQualificationReviews(supplierCode, status));
    }

    @Operation(tags = {"物料与 WMS"})
    @PostMapping("/material/suppliers/{supplierCode}/qualification-reviews")
    public Result<Map<String, Object>> createMaterialSupplierQualificationReview(@PathVariable String supplierCode,
                                                                                @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.createMaterialSupplierQualificationReview(supplierCode, request));
    }

    @Operation(tags = {"物料与 WMS"})
    @PostMapping("/material/suppliers/qualification-reviews/generate-due")
    public Result<Map<String, Object>> generateDueMaterialSupplierQualificationReviews(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.generateDueMaterialSupplierQualificationReviews(request));
    }

    @Operation(tags = {"物料与 WMS"})
    @PostMapping("/material/suppliers/qualification-reviews/{taskNo}/decision")
    public Result<Map<String, Object>> decideMaterialSupplierQualificationReview(@PathVariable String taskNo,
                                                                                @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.decideMaterialSupplierQualificationReview(taskNo, request));
    }

    @Operation(tags = {"物料与 WMS"})
    @PostMapping("/material/suppliers/corrective-actions")
    public Result<Map<String, Object>> createMaterialSupplierCorrectiveAction(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.createMaterialSupplierCorrectiveAction(request));
    }

    @Operation(tags = {"物料与 WMS"})
    @PostMapping("/material/suppliers/corrective-actions/{actionNo}/close")
    public Result<Map<String, Object>> closeMaterialSupplierCorrectiveAction(@PathVariable String actionNo,
                                                                            @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.closeMaterialSupplierCorrectiveAction(actionNo, request));
    }

    @Operation(tags = {"物料与 WMS"})
    @GetMapping("/material/locations")
    public Result<List<Map<String, Object>>> materialLocations() {
        return Result.success(pilotMesService.materialLocations());
    }

    @Operation(summary = "查询库位任务", tags = {"物料与 WMS"})
    @GetMapping("/material/location-tasks")
    public Result<List<Map<String, Object>>> materialLocationTasks(@RequestParam(required = false) String status,
                                                                   @RequestParam(required = false) String batchNo,
                                                                   @RequestParam(required = false) String reviewResult,
                                                                   @RequestParam(required = false) String dispositionStatus,
                                                                   @RequestParam(required = false) Boolean pendingDispositionOnly) {
        return Result.success(pilotMesService.materialLocationTasks(status, batchNo,
                reviewResult, dispositionStatus, pendingDispositionOnly));
    }

    @Operation(tags = {"物料与 WMS"})
    @PostMapping("/material/location-tasks")
    public Result<Map<String, Object>> createMaterialLocationTask(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.createMaterialLocationTask(request));
    }

    @Operation(summary = "指派库位任务", tags = {"物料与 WMS"})
    @PostMapping("/material/location-tasks/{taskNo}/assign")
    public Result<Map<String, Object>> assignMaterialLocationTask(@PathVariable String taskNo,
                                                                  @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.assignMaterialLocationTask(taskNo, request));
    }

    @Operation(summary = "自助认领库位任务", tags = {"物料与 WMS"})
    @PostMapping("/material/location-tasks/{taskNo}/claim")
    public Result<Map<String, Object>> claimMaterialLocationTask(@PathVariable String taskNo,
                                                                 @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.claimMaterialLocationTask(taskNo, request));
    }

    @Operation(summary = "完成库位任务", tags = {"物料与 WMS"})
    @PostMapping("/material/location-tasks/{taskNo}/complete")
    public Result<Map<String, Object>> completeMaterialLocationTask(@PathVariable String taskNo,
                                                                    @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.completeMaterialLocationTask(taskNo, request));
    }

    @Operation(tags = {"物料与 WMS"})
    @PostMapping("/material/location-tasks/{taskNo}/review")
    public Result<Map<String, Object>> reviewMaterialLocationTask(@PathVariable String taskNo,
                                                                  @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.reviewMaterialLocationTask(taskNo, request));
    }

    @Operation(tags = {"物料与 WMS"})
    @PostMapping("/material/location-tasks/{taskNo}/disposition")
    public Result<Map<String, Object>> dispositionMaterialLocationTask(@PathVariable String taskNo,
                                                                       @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.dispositionMaterialLocationTask(taskNo, request));
    }

    @Operation(tags = {"物料与 WMS"})
    @PostMapping("/material/location-tasks/{taskNo}/cancel")
    public Result<Map<String, Object>> cancelMaterialLocationTask(@PathVariable String taskNo,
                                                                  @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.cancelMaterialLocationTask(taskNo, request));
    }

    @Operation(summary = "来料 IQC 检验(NG 自动开 8D)", tags = {"物料与 WMS"})
    @PostMapping("/material/batches/{batchNo}/incoming-inspection")
    public Result<Map<String, Object>> createMaterialIncomingInspection(@PathVariable String batchNo,
                                                                       @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.createMaterialIncomingInspection(batchNo, request));
    }

    @Operation(tags = {"物料与 WMS"})
    @GetMapping("/material/consumptions")
    public Result<List<Map<String, Object>>> materialConsumptions(@RequestParam(required = false) String lotNo) {
        return Result.success(pilotMesService.materialConsumptions(lotNo));
    }

    @Operation(tags = {"物料与 WMS"})
    @GetMapping("/carriers")
    public Result<List<Map<String, Object>>> carriers() {
        return Result.success(pilotMesService.carriers());
    }

    @Operation(tags = {"物料与 WMS"})
    @PostMapping("/carriers/{carrierNo}/bind")
    public Result<Map<String, Object>> bindCarrier(@PathVariable String carrierNo,
                                                   @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.bindCarrier(carrierNo, request));
    }

    @Operation(tags = {"物料与 WMS"})
    @PostMapping("/carriers/{carrierNo}/unbind")
    public Result<Map<String, Object>> unbindCarrier(@PathVariable String carrierNo,
                                                     @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.unbindCarrier(carrierNo, request));
    }

    @Operation(summary = "追溯统一检索", tags = {"追溯"})
    @GetMapping("/trace/search")
    public Result<Map<String, Object>> traceSearch(@RequestParam(required = false, defaultValue = "AUTO") String type,
                                                   @RequestParam String keyword) {
        return Result.success(pilotMesService.traceSearch(type, keyword));
    }

    @Operation(summary = "Lot 全链路追溯", tags = {"追溯"})
    @GetMapping("/trace/lots/{lotNo}")
    public Result<Map<String, Object>> traceLot(@PathVariable String lotNo) {
        return Result.success(pilotMesService.traceLot(lotNo));
    }

    @Operation(summary = "SN 序列号追溯", tags = {"追溯"})
    @GetMapping("/trace/sn/{sn}")
    public Result<Map<String, Object>> traceSn(@PathVariable String sn) {
        return Result.success(pilotMesService.traceSn(sn));
    }

    @Operation(summary = "生产总览看板", tags = {"看板"})
    @GetMapping("/dashboard/overview")
    public Result<Map<String, Object>> dashboardOverview() {
        return Result.success(pilotMesService.overview());
    }

    @Operation(summary = "良率看板", tags = {"看板"})
    @GetMapping("/dashboard/yield")
    public Result<Map<String, Object>> dashboardYield() {
        return Result.success(pilotMesService.dashboardYield());
    }

    @Operation(summary = "AI 良率日报(RAG + 证据分级)", tags = {"AI 辅助"})
    @PostMapping("/ai/reports/yield")
    public Result<Map<String, Object>> aiYieldReport(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.aiYieldReport(request));
    }

    @Operation(summary = "AI 设备异常分析", tags = {"AI 辅助"})
    @PostMapping("/ai/equipment/analyze")
    public Result<Map<String, Object>> aiEquipmentAnalyze(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.aiEquipmentAnalyze(request));
    }

    @Operation(summary = "知识库 RAG 问答", tags = {"AI 辅助"})
    @PostMapping("/ai/kb/ask")
    public Result<Map<String, Object>> ragAsk(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.ragAsk(request));
    }

    @Operation(tags = {"AI 辅助"})
    @GetMapping("/ai/model-configs")
    public Result<List<Map<String, Object>>> aiModelConfigs() {
        return Result.success(pilotMesService.aiModelConfigs());
    }

    @Operation(tags = {"AI 辅助"})
    @GetMapping("/ai/report-records")
    public Result<List<Map<String, Object>>> aiReportRecords(@RequestParam(required = false) String reportType,
                                                             @RequestParam(required = false) String bizNo,
                                                             @RequestParam(required = false) String evidenceLevel,
                                                             @RequestParam(required = false) Boolean insufficientEvidence) {
        return Result.success(pilotMesService.aiReportRecords(reportType, bizNo, evidenceLevel, insufficientEvidence));
    }

    @Operation(tags = {"AI 辅助"})
    @GetMapping("/ai/report-records/{reportNo}")
    public Result<Map<String, Object>> aiReportRecordDetail(@PathVariable String reportNo) {
        return Result.success(pilotMesService.aiReportRecordDetail(reportNo));
    }

    @Operation(tags = {"AI 辅助"})
    @GetMapping("/ai/kb/documents")
    public Result<List<Map<String, Object>>> knowledgeDocuments() {
        return Result.success(pilotMesService.knowledgeDocuments());
    }

    @Operation(tags = {"AI 辅助"})
    @GetMapping("/ai/kb/index-jobs")
    public Result<List<Map<String, Object>>> knowledgeIndexJobs(@RequestParam(required = false) String documentNo,
                                                                @RequestParam(required = false) String status) {
        return Result.success(pilotMesService.knowledgeIndexJobs(documentNo, status));
    }

    @Operation(tags = {"AI 辅助"})
    @PostMapping("/ai/kb/index-jobs")
    public Result<Map<String, Object>> createKnowledgeIndexJob(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.createKnowledgeIndexJob(request));
    }

    @Operation(summary = "导入知识库文档", tags = {"AI 辅助"})
    @PostMapping("/ai/kb/import")
    public Result<Map<String, Object>> importKnowledgeDocument(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.importKnowledgeDocument(request));
    }

    private Map<String, Object> mergeDecision(Map<String, Object> request, String decision) {
        java.util.LinkedHashMap<String, Object> data = new java.util.LinkedHashMap<>();
        if (request != null) {
            data.putAll(request);
        }
        data.put("decision", decision);
        return data;
    }
}
