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
import com.visionox.mes.recipe.entity.Recipe;
import com.visionox.mes.recipe.service.RecipeService;
import com.visionox.mes.auth.entity.User;
import com.visionox.mes.system.entity.PermissionChangeRequest;
import com.visionox.mes.system.service.PermissionChangeService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
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

    @PostMapping("/auth/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success("登录成功", authService.login(request));
    }

    @GetMapping("/system/users")
    public Result<List<User>> users() {
        return Result.success(pilotMesService.users());
    }

    @GetMapping("/system/audit-logs")
    public Result<List<Map<String, Object>>> auditLogs(@RequestParam(required = false) String bizNo) {
        return Result.success(pilotMesService.auditLogs(bizNo));
    }

    @GetMapping("/system/summary")
    public Result<Map<String, Object>> systemSummary() {
        return Result.success(pilotMesService.systemSummary());
    }

    @GetMapping("/system/me/permissions")
    public Result<Map<String, Object>> currentPermissions() {
        return Result.success(pilotMesService.currentPermissions());
    }

    @GetMapping("/system/permission-change-requests")
    public Result<List<PermissionChangeRequest>> permissionChangeRequests(@RequestParam(required = false) String status) {
        return Result.success(permissionChangeService.listChangeRequests(status));
    }

    @PostMapping("/system/permission-change-requests")
    public Result<PermissionChangeRequest> createPermissionChangeRequest(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(permissionChangeService.createChangeRequest(request));
    }

    @PostMapping("/system/permission-change-requests/{changeNo}/review")
    public Result<PermissionChangeRequest> reviewPermissionChangeRequest(@PathVariable String changeNo,
                                                                         @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(permissionChangeService.reviewChangeRequest(changeNo, request));
    }

    @PostMapping("/system/permissions/reload")
    public Result<Map<String, Object>> reloadPermissions() {
        int appliedRoles = permissionChangeService.reloadApprovedPermissionSnapshots();
        return Result.success(Map.of("appliedRoles", appliedRoles));
    }

    @GetMapping("/master/products")
    public Result<List<Map<String, Object>>> products() {
        return Result.success(pilotMesService.products());
    }

    @GetMapping("/master/sites")
    public Result<List<Site>> sites() {
        return Result.success(masterDataService.getAllSites());
    }

    @GetMapping("/master/production-lines")
    public Result<List<ProductionLine>> productionLines(@RequestParam(required = false) String siteCode,
                                                        @RequestParam(required = false) String status) {
        return Result.success(masterDataService.getAllProductionLines(siteCode, status));
    }

    @GetMapping("/master/shifts")
    public Result<List<WorkShift>> shifts(@RequestParam(required = false) String lineCode,
                                          @RequestParam(required = false) String status) {
        return Result.success(masterDataService.getAllWorkShifts(lineCode, status));
    }

    @GetMapping("/master/process-steps")
    public Result<List<ProcessStep>> processSteps() {
        return Result.success(pilotMesService.processSteps());
    }

    @GetMapping("/master/equipments")
    public Result<List<Equipment>> equipments() {
        return Result.success(pilotMesService.equipments());
    }

    @GetMapping("/master/defect-codes")
    public Result<List<Map<String, Object>>> defectCodes() {
        return Result.success(pilotMesService.defectCodes());
    }

    @GetMapping("/routes")
    public Result<List<Map<String, Object>>> routes() {
        return Result.success(pilotMesService.routes());
    }

    @GetMapping("/boms")
    public Result<List<Map<String, Object>>> boms() {
        return Result.success(pilotMesService.boms());
    }

    @GetMapping("/boms/change-requests")
    public Result<List<Map<String, Object>>> bomChangeRequests(@RequestParam(required = false) String status) {
        return Result.success(pilotMesService.bomChangeRequests(status));
    }

    @GetMapping("/boms/eco-approvals")
    public Result<List<Map<String, Object>>> bomEcoApprovalTasks(@RequestParam(required = false) String changeNo,
                                                                 @RequestParam(required = false) String status) {
        return Result.success(pilotMesService.bomEcoApprovalTasks(changeNo, status));
    }

    @PostMapping("/boms/change-requests")
    public Result<Map<String, Object>> submitBomChange(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.submitBomChange(request));
    }

    @PostMapping("/boms/eco-approvals/{taskNo}/decision")
    public Result<Map<String, Object>> decideBomEcoApproval(@PathVariable String taskNo,
                                                            @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.decideBomEcoApproval(taskNo, request));
    }

    @PostMapping("/boms/change-requests/{changeNo}/review")
    public Result<Map<String, Object>> reviewBomChange(@PathVariable String changeNo,
                                                       @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.reviewBomChange(changeNo, request));
    }

    @PostMapping("/boms/change-requests/{changeNo}/publish")
    public Result<Map<String, Object>> publishBomChange(@PathVariable String changeNo,
                                                        @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.publishBomChange(changeNo, request));
    }

    @GetMapping("/recipes")
    public Result<Page<Recipe>> recipes(@RequestParam(defaultValue = "1") long current,
                                        @RequestParam(defaultValue = "20") long size) {
        return Result.success(pilotMesService.pageRecipes(current, size));
    }

    @PostMapping("/recipes/{id}/publish")
    public Result<Void> publishRecipe(@PathVariable Long id) {
        recipeService.activateRecipe(id);
        return Result.success();
    }

    @GetMapping("/orders")
    public Result<Page<ProductionOrder>> orders(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "20") long size,
                                                @RequestParam(required = false) String status) {
        return Result.success(pilotMesService.pageOrders(current, size, status));
    }

    @PostMapping("/orders")
    public Result<ProductionOrder> createOrder(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.createOrder(request));
    }

    @PostMapping("/orders/{orderNo}/release")
    public Result<Map<String, Object>> releaseOrder(@PathVariable String orderNo,
                                                    @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.releaseOrder(orderNo, request));
    }

    @GetMapping("/lots")
    public Result<Page<Lot>> lots(@RequestParam(defaultValue = "1") long current,
                                  @RequestParam(defaultValue = "20") long size,
                                  @RequestParam(required = false) String lotNo,
                                  @RequestParam(required = false) String status) {
        return Result.success(pilotMesService.pageLots(current, size, lotNo, status));
    }

    @PostMapping("/lots/{lotNo}/track-in")
    public Result<Void> trackIn(@PathVariable String lotNo,
                                @RequestBody(required = false) Map<String, Object> request) {
        pilotMesService.trackIn(lotNo, request);
        return Result.success();
    }

    @PostMapping("/lots/{lotNo}/track-out")
    public Result<Map<String, Object>> trackOut(@PathVariable String lotNo,
                                                @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.trackOut(lotNo, request));
    }

    @PostMapping("/lots/{lotNo}/hold")
    public Result<Void> hold(@PathVariable String lotNo,
                             @RequestBody(required = false) Map<String, Object> request) {
        pilotMesService.hold(lotNo, request);
        return Result.success();
    }

    @PostMapping("/lots/{lotNo}/release")
    public Result<Void> release(@PathVariable String lotNo,
                                @RequestBody(required = false) Map<String, Object> request) {
        pilotMesService.release(lotNo, request);
        return Result.success();
    }

    @PostMapping("/lots/{lotNo}/rework")
    public Result<Void> rework(@PathVariable String lotNo,
                               @RequestBody(required = false) Map<String, Object> request) {
        pilotMesService.rework(lotNo, request);
        return Result.success();
    }

    @PostMapping("/lots/{lotNo}/scrap")
    public Result<Void> scrap(@PathVariable String lotNo,
                              @RequestBody(required = false) Map<String, Object> request) {
        pilotMesService.scrap(lotNo, request);
        return Result.success();
    }

    @GetMapping("/quality/inspections")
    public Result<List<Map<String, Object>>> qualityInspections(@RequestParam(required = false) String lotNo) {
        return Result.success(pilotMesService.qualityInspections(lotNo));
    }

    @GetMapping("/quality/exceptions")
    public Result<List<Map<String, Object>>> qualityExceptions(@RequestParam(required = false) String lotNo) {
        return Result.success(pilotMesService.qualityExceptions(lotNo));
    }

    @GetMapping("/quality/exceptions/{eventNo}/mrb-records")
    public Result<List<Map<String, Object>>> qualityMrbRecords(@PathVariable String eventNo) {
        return Result.success(pilotMesService.qualityMrbRecords(eventNo));
    }

    @GetMapping("/quality/mrb-records/{mrbNo}/minutes")
    public Result<List<Map<String, Object>>> qualityMrbMinutes(@PathVariable String mrbNo) {
        return Result.success(pilotMesService.qualityMrbMinutes(mrbNo));
    }

    @PostMapping("/quality/mrb-records/{mrbNo}/minutes")
    public Result<Map<String, Object>> createQualityMrbMinutes(@PathVariable String mrbNo,
                                                               @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.createQualityMrbMinutes(mrbNo, request));
    }

    @GetMapping("/quality/mrb-approvals")
    public Result<List<Map<String, Object>>> qualityMrbApprovalTasks(@RequestParam(required = false) String eventNo,
                                                                     @RequestParam(required = false) String status) {
        return Result.success(pilotMesService.qualityMrbApprovalTasks(eventNo, status));
    }

    @PostMapping("/quality/mrb-approvals/refresh-sla")
    public Result<Map<String, Object>> refreshQualityMrbApprovalSla(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.refreshQualityMrbApprovalSla(request));
    }

    @PostMapping("/quality/mrb-approvals/{taskNo}/approve")
    public Result<Map<String, Object>> approveMrbTask(@PathVariable String taskNo,
                                                      @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.decideMrbApprovalTask(taskNo, mergeDecision(request, "APPROVE")));
    }

    @PostMapping("/quality/mrb-approvals/{taskNo}/reject")
    public Result<Map<String, Object>> rejectMrbTask(@PathVariable String taskNo,
                                                     @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.decideMrbApprovalTask(taskNo, mergeDecision(request, "REJECT")));
    }

    @PostMapping("/quality/exceptions/{eventNo}/mrb-review")
    public Result<Map<String, Object>> reviewException(@PathVariable String eventNo,
                                                       @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.reviewException(eventNo, request));
    }

    @PostMapping("/quality/exceptions/{eventNo}/close")
    public Result<Map<String, Object>> closeException(@PathVariable String eventNo,
                                                      @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.closeException(eventNo, request));
    }

    @PostMapping("/quality/inspections")
    public Result<List<Map<String, Object>>> createInspection(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.qualityInspections(String.valueOf(request == null ? "" : request.getOrDefault("lotNo", ""))));
    }

    @GetMapping("/equipment/events")
    public Result<List<Map<String, Object>>> equipmentEvents(@RequestParam(required = false) String equipmentCode,
                                                            @RequestParam(required = false) String status) {
        return Result.success(pilotMesService.equipmentEvents(equipmentCode, status));
    }

    @PostMapping("/equipment/events")
    public Result<Map<String, Object>> createEquipmentEvent(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.createEquipmentEvent(request));
    }

    @PostMapping("/equipment/events/{eventNo}/close")
    public Result<Map<String, Object>> closeEquipmentEvent(@PathVariable String eventNo,
                                                          @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.closeEquipmentEvent(eventNo, request));
    }

    @GetMapping("/equipment/oee")
    public Result<Map<String, Object>> equipmentOee(@RequestParam(required = false) String lineCode) {
        return Result.success(pilotMesService.equipmentOeeSummary(lineCode));
    }

    @GetMapping("/equipment/status-history")
    public Result<List<Map<String, Object>>> equipmentStatusHistory(@RequestParam(required = false) String equipmentCode) {
        return Result.success(pilotMesService.equipmentStatusHistories(equipmentCode));
    }

    @GetMapping("/equipment/gateways")
    public Result<List<Map<String, Object>>> equipmentGateways(@RequestParam(required = false) String status) {
        return Result.success(pilotMesService.equipmentGateways(status));
    }

    @PostMapping("/equipment/gateways")
    public Result<Map<String, Object>> registerEquipmentGateway(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.registerEquipmentGateway(request));
    }

    @PostMapping("/equipment/gateways/{gatewayCode}/heartbeat")
    public Result<Map<String, Object>> heartbeatEquipmentGateway(@PathVariable String gatewayCode,
                                                                @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.heartbeatEquipmentGateway(gatewayCode, request));
    }

    @PostMapping("/equipment/gateways/{gatewayCode}/health-check")
    public Result<Map<String, Object>> checkEquipmentGatewayHealth(@PathVariable String gatewayCode,
                                                                  @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.checkEquipmentGatewayHealth(gatewayCode, request));
    }

    @GetMapping("/equipment/gateway-health-checks")
    public Result<List<Map<String, Object>>> equipmentGatewayHealthChecks(@RequestParam(required = false) String gatewayCode) {
        return Result.success(pilotMesService.equipmentGatewayHealthChecks(gatewayCode));
    }

    @GetMapping("/equipment/gateway-messages")
    public Result<List<Map<String, Object>>> equipmentGatewayMessages(@RequestParam(required = false) String gatewayCode) {
        return Result.success(pilotMesService.equipmentGatewayMessages(gatewayCode));
    }

    @GetMapping("/equipment/gateway-drivers")
    public Result<List<Map<String, Object>>> equipmentGatewayDrivers() {
        return Result.success(pilotMesService.equipmentGatewayDrivers());
    }

    @PostMapping("/equipment/status/report")
    public Result<Map<String, Object>> reportEquipmentStatus(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.reportEquipmentStatus(request));
    }

    @GetMapping("/equipment/cycle-samples")
    public Result<List<Map<String, Object>>> equipmentCycleSamples(@RequestParam(required = false) String equipmentCode) {
        return Result.success(pilotMesService.equipmentCycleSamples(equipmentCode));
    }

    @GetMapping("/equipment/standard-cycles")
    public Result<List<Map<String, Object>>> equipmentStandardCycles(@RequestParam(required = false) String equipmentCode,
                                                                     @RequestParam(required = false) String status) {
        return Result.success(pilotMesService.equipmentStandardCycles(equipmentCode, status));
    }

    @PostMapping("/equipment/standard-cycles")
    public Result<Map<String, Object>> publishEquipmentStandardCycle(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.publishEquipmentStandardCycle(request));
    }

    @PostMapping("/equipment/cycle-samples/report")
    public Result<Map<String, Object>> reportEquipmentCycleSample(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.reportEquipmentCycleSample(request));
    }

    @GetMapping("/equipment/parameters")
    public Result<List<Map<String, Object>>> equipmentParameterSamples(@RequestParam(required = false) String equipmentCode) {
        return Result.success(pilotMesService.equipmentParameterSamples(equipmentCode));
    }

    @PostMapping("/equipment/parameters/report")
    public Result<Map<String, Object>> reportEquipmentParameters(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.reportEquipmentParameters(request));
    }

    @GetMapping("/equipment/pm-tasks")
    public Result<List<Map<String, Object>>> equipmentPmTasks(@RequestParam(required = false) String status) {
        return Result.success(pilotMesService.equipmentPmTasks(status));
    }

    @PostMapping("/equipment/pm-tasks/{taskNo}/complete")
    public Result<Map<String, Object>> completeEquipmentPmTask(@PathVariable String taskNo,
                                                              @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.completeEquipmentPmTask(taskNo, request));
    }

    @GetMapping("/equipment/recipe-downloads")
    public Result<List<Map<String, Object>>> equipmentRecipeCommands(@RequestParam(required = false) String equipmentCode,
                                                                     @RequestParam(required = false) String status) {
        return Result.success(pilotMesService.equipmentRecipeCommands(equipmentCode, status));
    }

    @PostMapping("/equipment/recipe-downloads")
    public Result<Map<String, Object>> downloadEquipmentRecipe(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.downloadEquipmentRecipe(request));
    }

    @PostMapping("/adapters/eap/messages")
    public Result<Map<String, Object>> ingestEapMessage(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.ingestEapMessage(request));
    }

    @PostMapping("/adapters/erp/orders")
    public Result<Map<String, Object>> importErpOrders(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.importErpOrders(request));
    }

    @PostMapping("/adapters/qms/inspections")
    public Result<Map<String, Object>> ingestQmsInspection(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.ingestQmsInspection(request));
    }

    @PostMapping("/adapters/wms/material-readiness")
    public Result<Map<String, Object>> checkWmsMaterialReadiness(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.checkWmsMaterialReadiness(request));
    }

    @PostMapping("/adapters/wms/inventory-transactions")
    public Result<Map<String, Object>> ingestWmsInventoryTransaction(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.ingestWmsInventoryTransaction(request));
    }

    @GetMapping("/material/batches")
    public Result<Map<String, Object>> materialBatches() {
        return Result.success(pilotMesService.materialReadiness());
    }

    @PostMapping("/material/receive")
    public Result<Map<String, Object>> receiveMaterial(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.receiveMaterial(request));
    }

    @PostMapping("/material/batches/{batchNo}/freeze")
    public Result<Map<String, Object>> freezeMaterial(@PathVariable String batchNo,
                                                      @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.freezeMaterial(batchNo, request));
    }

    @PostMapping("/material/batches/{batchNo}/unfreeze")
    public Result<Map<String, Object>> unfreezeMaterial(@PathVariable String batchNo,
                                                        @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.unfreezeMaterial(batchNo, request));
    }

    @PostMapping("/material/batches/{batchNo}/return")
    public Result<Map<String, Object>> returnMaterial(@PathVariable String batchNo,
                                                      @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.returnMaterial(batchNo, request));
    }

    @PostMapping("/material/batches/{batchNo}/inventory-count")
    public Result<Map<String, Object>> inventoryCount(@PathVariable String batchNo,
                                                      @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.inventoryCount(batchNo, request));
    }

    @GetMapping("/material/inventory-transactions")
    public Result<List<Map<String, Object>>> materialInventoryTransactions(@RequestParam(required = false) String batchNo) {
        return Result.success(pilotMesService.materialInventoryTransactions(batchNo));
    }

    @GetMapping("/material/incoming-inspections")
    public Result<List<Map<String, Object>>> materialIncomingInspections(@RequestParam(required = false) String batchNo) {
        return Result.success(pilotMesService.materialIncomingInspections(batchNo));
    }

    @GetMapping("/material/suppliers/performance")
    public Result<List<Map<String, Object>>> materialSupplierPerformance() {
        return Result.success(pilotMesService.materialSupplierPerformance());
    }

    @GetMapping("/material/suppliers/trends")
    public Result<List<Map<String, Object>>> materialSupplierTrends(@RequestParam(required = false, defaultValue = "6") Integer months) {
        return Result.success(pilotMesService.materialSupplierTrends(months == null ? 6 : months));
    }

    @GetMapping("/material/suppliers")
    public Result<List<Map<String, Object>>> materialSuppliers() {
        return Result.success(pilotMesService.materialSuppliers());
    }

    @PostMapping("/material/suppliers/{supplierCode}/qualification/evaluate")
    public Result<Map<String, Object>> evaluateMaterialSupplierQualification(@PathVariable String supplierCode,
                                                                            @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.evaluateMaterialSupplierQualification(supplierCode, request));
    }

    @GetMapping("/material/suppliers/corrective-actions")
    public Result<List<Map<String, Object>>> materialSupplierCorrectiveActions(@RequestParam(required = false) String supplierCode,
                                                                               @RequestParam(required = false) String status) {
        return Result.success(pilotMesService.materialSupplierCorrectiveActions(supplierCode, status));
    }

    @GetMapping("/material/suppliers/qualification-reviews")
    public Result<List<Map<String, Object>>> materialSupplierQualificationReviews(@RequestParam(required = false) String supplierCode,
                                                                                 @RequestParam(required = false) String status) {
        return Result.success(pilotMesService.materialSupplierQualificationReviews(supplierCode, status));
    }

    @PostMapping("/material/suppliers/{supplierCode}/qualification-reviews")
    public Result<Map<String, Object>> createMaterialSupplierQualificationReview(@PathVariable String supplierCode,
                                                                                @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.createMaterialSupplierQualificationReview(supplierCode, request));
    }

    @PostMapping("/material/suppliers/qualification-reviews/{taskNo}/decision")
    public Result<Map<String, Object>> decideMaterialSupplierQualificationReview(@PathVariable String taskNo,
                                                                                @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.decideMaterialSupplierQualificationReview(taskNo, request));
    }

    @PostMapping("/material/suppliers/corrective-actions")
    public Result<Map<String, Object>> createMaterialSupplierCorrectiveAction(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.createMaterialSupplierCorrectiveAction(request));
    }

    @PostMapping("/material/suppliers/corrective-actions/{actionNo}/close")
    public Result<Map<String, Object>> closeMaterialSupplierCorrectiveAction(@PathVariable String actionNo,
                                                                            @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.closeMaterialSupplierCorrectiveAction(actionNo, request));
    }

    @GetMapping("/material/locations")
    public Result<List<Map<String, Object>>> materialLocations() {
        return Result.success(pilotMesService.materialLocations());
    }

    @GetMapping("/material/location-tasks")
    public Result<List<Map<String, Object>>> materialLocationTasks(@RequestParam(required = false) String status,
                                                                   @RequestParam(required = false) String batchNo) {
        return Result.success(pilotMesService.materialLocationTasks(status, batchNo));
    }

    @PostMapping("/material/location-tasks")
    public Result<Map<String, Object>> createMaterialLocationTask(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.createMaterialLocationTask(request));
    }

    @PostMapping("/material/location-tasks/{taskNo}/assign")
    public Result<Map<String, Object>> assignMaterialLocationTask(@PathVariable String taskNo,
                                                                  @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.assignMaterialLocationTask(taskNo, request));
    }

    @PostMapping("/material/location-tasks/{taskNo}/complete")
    public Result<Map<String, Object>> completeMaterialLocationTask(@PathVariable String taskNo,
                                                                    @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.completeMaterialLocationTask(taskNo, request));
    }

    @PostMapping("/material/location-tasks/{taskNo}/cancel")
    public Result<Map<String, Object>> cancelMaterialLocationTask(@PathVariable String taskNo,
                                                                  @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.cancelMaterialLocationTask(taskNo, request));
    }

    @PostMapping("/material/batches/{batchNo}/incoming-inspection")
    public Result<Map<String, Object>> createMaterialIncomingInspection(@PathVariable String batchNo,
                                                                       @RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.createMaterialIncomingInspection(batchNo, request));
    }

    @GetMapping("/material/consumptions")
    public Result<List<Map<String, Object>>> materialConsumptions(@RequestParam(required = false) String lotNo) {
        return Result.success(pilotMesService.materialConsumptions(lotNo));
    }

    @GetMapping("/carriers")
    public Result<List<Map<String, Object>>> carriers() {
        return Result.success(pilotMesService.carriers());
    }

    @GetMapping("/trace/lots/{lotNo}")
    public Result<Map<String, Object>> traceLot(@PathVariable String lotNo) {
        return Result.success(pilotMesService.traceLot(lotNo));
    }

    @GetMapping("/trace/sn/{sn}")
    public Result<Map<String, Object>> traceSn(@PathVariable String sn) {
        return Result.success(pilotMesService.traceSn(sn));
    }

    @GetMapping("/dashboard/overview")
    public Result<Map<String, Object>> dashboardOverview() {
        return Result.success(pilotMesService.overview());
    }

    @GetMapping("/dashboard/yield")
    public Result<Map<String, Object>> dashboardYield() {
        return Result.success(pilotMesService.dashboardYield());
    }

    @PostMapping("/ai/reports/yield")
    public Result<Map<String, Object>> aiYieldReport(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.aiYieldReport(request));
    }

    @PostMapping("/ai/equipment/analyze")
    public Result<Map<String, Object>> aiEquipmentAnalyze(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.aiEquipmentAnalyze(request));
    }

    @PostMapping("/ai/kb/ask")
    public Result<Map<String, Object>> ragAsk(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.ragAsk(request));
    }

    @GetMapping("/ai/model-configs")
    public Result<List<Map<String, Object>>> aiModelConfigs() {
        return Result.success(pilotMesService.aiModelConfigs());
    }

    @GetMapping("/ai/report-records")
    public Result<List<Map<String, Object>>> aiReportRecords(@RequestParam(required = false) String reportType,
                                                             @RequestParam(required = false) String bizNo,
                                                             @RequestParam(required = false) String evidenceLevel,
                                                             @RequestParam(required = false) Boolean insufficientEvidence) {
        return Result.success(pilotMesService.aiReportRecords(reportType, bizNo, evidenceLevel, insufficientEvidence));
    }

    @GetMapping("/ai/report-records/{reportNo}")
    public Result<Map<String, Object>> aiReportRecordDetail(@PathVariable String reportNo) {
        return Result.success(pilotMesService.aiReportRecordDetail(reportNo));
    }

    @GetMapping("/ai/kb/documents")
    public Result<List<Map<String, Object>>> knowledgeDocuments() {
        return Result.success(pilotMesService.knowledgeDocuments());
    }

    @GetMapping("/ai/kb/index-jobs")
    public Result<List<Map<String, Object>>> knowledgeIndexJobs(@RequestParam(required = false) String documentNo,
                                                                @RequestParam(required = false) String status) {
        return Result.success(pilotMesService.knowledgeIndexJobs(documentNo, status));
    }

    @PostMapping("/ai/kb/index-jobs")
    public Result<Map<String, Object>> createKnowledgeIndexJob(@RequestBody(required = false) Map<String, Object> request) {
        return Result.success(pilotMesService.createKnowledgeIndexJob(request));
    }

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
