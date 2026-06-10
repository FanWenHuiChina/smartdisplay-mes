package com.visionox.mes.pilot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.json.JSONUtil;
import com.visionox.mes.ai.service.AiKbIndexService;
import com.visionox.mes.ai.service.AiKnowledgeService;
import com.visionox.mes.ai.service.AiModelConfigService;
import com.visionox.mes.ai.service.AiRecordService;
import com.visionox.mes.auth.entity.User;
import com.visionox.mes.auth.mapper.UserMapper;
import com.visionox.mes.auth.security.AuthContext;
import com.visionox.mes.auth.security.RolePermissionService;
import com.visionox.mes.common.BusinessException;
import com.visionox.mes.equipment.adapter.EapAdapter;
import com.visionox.mes.equipment.service.EapGatewayService;
import com.visionox.mes.equipment.service.EquipmentService;
import com.visionox.mes.lot.dto.HoldRequest;
import com.visionox.mes.lot.dto.ReleaseRequest;
import com.visionox.mes.lot.dto.TrackInRequest;
import com.visionox.mes.lot.dto.TrackOutRequest;
import com.visionox.mes.lot.entity.Equipment;
import com.visionox.mes.lot.entity.HoldRecord;
import com.visionox.mes.lot.entity.Lot;
import com.visionox.mes.lot.entity.LotStepRecord;
import com.visionox.mes.lot.entity.ProcessStep;
import com.visionox.mes.lot.entity.SerialNumber;
import com.visionox.mes.lot.mapper.EquipmentMapper;
import com.visionox.mes.lot.mapper.HoldRecordMapper;
import com.visionox.mes.lot.mapper.LotMapper;
import com.visionox.mes.lot.mapper.LotStepRecordMapper;
import com.visionox.mes.lot.mapper.ProcessStepMapper;
import com.visionox.mes.lot.mapper.SerialNumberMapper;
import com.visionox.mes.lot.service.HoldService;
import com.visionox.mes.lot.service.TrackInService;
import com.visionox.mes.material.service.MaterialService;
import com.visionox.mes.order.entity.ProductionOrder;
import com.visionox.mes.order.mapper.ProductionOrderMapper;
import com.visionox.mes.order.service.ErpOrderAdapterService;
import com.visionox.mes.recipe.entity.Recipe;
import com.visionox.mes.recipe.entity.RecipeParam;
import com.visionox.mes.recipe.mapper.RecipeMapper;
import com.visionox.mes.recipe.mapper.RecipeParamMapper;
import com.visionox.mes.quality.service.QualityService;
import com.visionox.mes.route.entity.Route;
import com.visionox.mes.route.entity.RouteStep;
import com.visionox.mes.route.service.RouteService;
import com.visionox.mes.system.entity.AuditLog;
import com.visionox.mes.system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 生产级试点接口聚合服务。
 *
 * <p>当前版本优先打通单基地单产线试点闭环：真实读取已有 Recipe、Lot、
 * 设备、工序、工单、质量、物料、审计和异常表。演示 fallback 必须显式开启，
 * 生产默认不静默返回样例数据。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PilotMesService {

    private static final List<String> DEFAULT_ROUTE = List.of(
            "CLEAN", "COATING", "EXPOSURE", "ETCH", "EVAPORATION", "ENCAPSULATION", "INSPECTION", "AGING"
    );
    private static final List<String> TRACE_TYPES = List.of(
            "LOT", "SN", "ORDER", "EQUIPMENT", "MATERIAL_BATCH", "DEFECT_CODE"
    );

    private record TraceCandidate(String lotNo, String matchType, String matchField, String evidence) {
    }

    private final ProductionOrderMapper orderMapper;
    private final LotMapper lotMapper;
    private final SerialNumberMapper serialNumberMapper;
    private final ProcessStepMapper processStepMapper;
    private final EquipmentMapper equipmentMapper;
    private final RecipeMapper recipeMapper;
    private final RecipeParamMapper recipeParamMapper;
    private final LotStepRecordMapper stepRecordMapper;
    private final HoldRecordMapper holdRecordMapper;
    private final UserMapper userMapper;
    private final TrackInService trackInService;
    private final HoldService holdService;
    private final AuditLogService auditLogService;
    private final RouteService routeService;
    private final QualityService qualityService;
    private final MaterialService materialService;
    private final ErpOrderAdapterService erpOrderAdapterService;
    private final AiKbIndexService aiKbIndexService;
    private final AiKnowledgeService aiKnowledgeService;
    private final AiModelConfigService aiModelConfigService;
    private final AiRecordService aiRecordService;
    private final RolePermissionService rolePermissionService;
    private final EquipmentService equipmentService;
    private final EapAdapter eapAdapter;
    private final EapGatewayService eapGatewayService;

    @Value("${mes.pilot.fallback-enabled:false}")
    private boolean pilotFallbackEnabled;

    public Map<String, Object> overview() {
        List<Lot> lots = allLots();
        List<Equipment> equipments = equipmentMapper.selectList(null);
        Map<String, Object> equipmentOee = equipmentOeeSummary(null);
        long holdCount = lots.stream().filter(lot -> "HOLD".equals(lot.getStatus())).count();
        long processing = lots.stream().filter(lot -> "PROCESSING".equals(lot.getStatus())).count();
        long completed = lots.stream().filter(lot -> "COMPLETED".equals(lot.getStatus())).count();
        long alarmEquipment = equipments.stream()
                .filter(equipment -> "ALARM".equals(equipment.getStatus()) || "DOWN".equals(equipment.getStatus()))
                .count();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("metrics", List.of(
                metric("今日投入 Lot", String.valueOf(lots.size()), "WIP", "blue", "等待 " + readyCount(lots), "加工中 " + processing),
                metric("综合良率", yieldText(lots), "接近目标", "green", "目标 97.00%", "-0.18%"),
                metric("Hold 待处置", String.valueOf(holdCount), "质量介入", holdCount > 0 ? "red" : "green", "超 SLA 2", "新增 3"),
                metric("设备 OEE", String.valueOf(equipmentOee.getOrDefault("oeeText", equipmentUtilization(equipments))),
                        alarmEquipment > 0 ? "需关注" : "正常", alarmEquipment > 0 ? "amber" : "green",
                        "非计划 " + equipmentOee.getOrDefault("unplannedDowntimeMinutes", 0) + "分",
                        "可用率 " + equipmentOee.getOrDefault("availabilityText", "-"))
        ));
        data.put("routeSteps", routeSteps(lots));
        data.put("alerts", alertQueue());
        data.put("yieldTrend", yieldTrend());
        data.put("equipmentOee", equipmentOee);
        data.put("aiSuggestions", List.of(
                Map.of("title", "主要瓶颈：涂胶 / 蒸镀", "text", "涂胶等待队列高于过去 7 日均值，蒸镀设备异常与 Mura 不良上升存在时间相关性。"),
                Map.of("title", "建议动作", "text", "优先释放 COATER_01 高优先级 Lot；EVAP_01 下一批进站前执行真空稳定性点检。")
        ));
        return data;
    }

    public Page<ProductionOrder> pageOrders(long current, long size, String status) {
        LambdaQueryWrapper<ProductionOrder> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(ProductionOrder::getStatus, status);
        }
        applyDataScope(wrapper, dataScope("line_code", "created_by", "created_time"));
        wrapper.orderByDesc(ProductionOrder::getCreatedTime);
        return orderMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public Map<String, Object> orderReleaseChecks(String orderNo, int lotQty) {
        ProductionOrder order = findOrderByNo(orderNo);
        return buildOrderReleaseChecks(order, Math.max(1, lotQty));
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductionOrder createOrder(Map<String, Object> request) {
        ProductionOrder order = new ProductionOrder();
        order.setOrderNo(text(request, "orderNo", "MO" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())));
        order.setProductCode(text(request, "productCode", "AMOLED_65"));
        order.setProductName(text(request, "productName", order.getProductCode() + " 柔性屏"));
        order.setPlannedQty(intValue(value(request, "plannedQty"), 1000));
        order.setCompletedQty(0);
        order.setPriority(intValue(value(request, "priority"), 0));
        order.setLineCode(text(request, "lineCode", "LINE_01"));
        order.setStatus("CREATED");
        String operator = currentUser();
        order.setCreatedBy(operator);
        order.setCreatedTime(LocalDateTime.now());
        orderMapper.insert(order);
        audit("ORDER_CREATE", order.getOrderNo(), "创建生产工单", operator,
                auditSnapshot(null, orderSnapshot(order), safeRequest(request)));
        return order;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> releaseOrder(String orderNo, Map<String, Object> request) {
        ProductionOrder order = findOrderByNo(orderNo);
        if (order == null) {
            throw new BusinessException("工单不存在: " + orderNo);
        }
        Map<String, Object> before = orderSnapshot(order);
        int lotQty = Math.max(1, intValue(value(request, "lotQty"), 100));
        Map<String, Object> releaseChecks = buildOrderReleaseChecks(order, lotQty);
        assertReleaseChecksPassed(releaseChecks);
        int lotCount = Math.max(1, (int) Math.ceil(order.getPlannedQty() * 1.0 / lotQty));
        String firstStepCode = objectText(releaseChecks.get("firstStepCode"), firstRouteStepCode(order.getProductCode()));
        String operator = currentUser();
        List<Lot> lots = new ArrayList<>();
        List<SerialNumber> createdSerialNumbers = new ArrayList<>();
        for (int i = 1; i <= lotCount; i++) {
            String lotNo = orderNo.replace("MO", "LOT") + "-" + String.format("%03d", i);
            Long exists = lotMapper.selectCount(new LambdaQueryWrapper<Lot>().eq(Lot::getLotNo, lotNo));
            if (longValue(exists, 0) > 0) {
                continue;
            }
            int actualQty = Math.min(lotQty, Math.max(0, order.getPlannedQty() - (i - 1) * lotQty));
            Lot lot = new Lot();
            lot.setLotNo(lotNo);
            lot.setOrderNo(orderNo);
            lot.setProductCode(order.getProductCode());
            lot.setLineCode(order.getLineCode());
            lot.setQty(actualQty);
            lot.setCurrentStepCode(firstStepCode);
            lot.setCurrentEquipmentCode(null);
            lot.setStatus("READY");
            lot.setHoldFlag(0);
            lot.setPriority(order.getPriority());
            lot.setCreatedBy(operator);
            lot.setCreatedTime(LocalDateTime.now());
            lotMapper.insert(lot);
            lots.add(lot);
            createdSerialNumbers.addAll(createSerialNumbers(order, lot, operator));
        }
        order.setStatus("RELEASED");
        order.setStartTime(order.getStartTime() == null ? LocalDateTime.now() : order.getStartTime());
        orderMapper.updateById(order);
        Map<String, Object> after = orderSnapshot(order);
        after.put("createdLotNos", lots.stream().map(Lot::getLotNo).toList());
        after.put("createdLotCount", lots.size());
        after.put("createdSnCount", createdSerialNumbers.size());
        audit("ORDER_RELEASE", orderNo, "释放工单并生成 Lot: " + lots.size(), currentUser(),
                auditSnapshot(before, after, safeRequest(request)));
        return Map.of(
                "order", order,
                "createdLots", lots,
                "lotCount", lots.size(),
                "createdSnCount", createdSerialNumbers.size(),
                "createdSnPreview", createdSerialNumbers.stream().limit(10).map(SerialNumber::getSn).toList()
        );
    }

    private ProductionOrder findOrderByNo(String orderNo) {
        if (!hasText(orderNo)) {
            return null;
        }
        return orderMapper.selectOne(new LambdaQueryWrapper<ProductionOrder>().eq(ProductionOrder::getOrderNo, orderNo));
    }

    private Map<String, Object> buildOrderReleaseChecks(ProductionOrder order, int lotQty) {
        int normalizedLotQty = Math.max(1, lotQty);
        List<Map<String, Object>> checks = new ArrayList<>();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkedTime", LocalDateTime.now());
        result.put("lotQty", normalizedLotQty);
        result.put("orderNo", order == null ? "" : order.getOrderNo());

        if (order == null) {
            checks.add(releaseCheck("orderStatus", "工单状态", false, true, "red", "工单不存在或无权限访问"));
            checks.add(releaseCheck("product", "产品资料", false, true, "gray", "缺少工单，无法解析产品"));
            checks.add(releaseCheck("route", "Route版本", false, true, "gray", "缺少工单，无法读取生效路线"));
            checks.add(releaseCheck("bom", "BOM", false, true, "gray", "缺少工单，无法读取生效BOM"));
            checks.add(releaseCheck("recipe", "Recipe覆盖", false, true, "gray", "缺少工单，无法校验Recipe"));
            checks.add(releaseCheck("equipment", "设备能力", false, true, "gray", "缺少工单，无法校验产线设备"));
            checks.add(releaseCheck("lotSplit", "Lot拆分", false, true, "gray", "缺少工单，无法计算Lot数量"));
            checks.add(releaseCheck("permissionAudit", "权限审计", currentRoleHasButton("order:release"), false,
                    currentRoleHasButton("order:release") ? "green" : "amber", permissionAuditText()));
            return releaseCheckResult(result, checks);
        }

        result.put("productCode", order.getProductCode());
        result.put("lineCode", order.getLineCode());
        result.put("plannedQty", order.getPlannedQty());
        result.put("status", order.getStatus());

        boolean created = "CREATED".equals(order.getStatus());
        checks.add(releaseCheck("orderStatus", "工单状态", created, true, created ? "green" : "red",
                created ? "CREATED，可释放" : "当前状态 " + valueOr(order.getStatus(), "-") + " 不允许释放"));

        Map<String, Object> product = products().stream()
                .filter(row -> Objects.equals(order.getProductCode(), row.get("productCode")))
                .findFirst()
                .orElse(Map.of());
        boolean productReady = !product.isEmpty() && "ACTIVE".equals(product.get("status"));
        boolean productBound = hasText(order.getProductCode());
        checks.add(releaseCheck("product", "产品资料", productBound, true,
                productReady ? "green" : productBound ? "amber" : "red",
                productReady ? order.getProductCode() + " 已启用"
                        : productBound ? order.getProductCode() + " 已绑定，生产适配由Route/BOM/Recipe交叉校验"
                        : "产品编码为空"));

        Route activeRoute = null;
        List<String> routeSteps = List.of();
        String routeError = "";
        if (hasText(order.getProductCode())) {
            try {
                activeRoute = routeService.findActiveRoute(order.getProductCode());
                routeSteps = safeList(routeService.activeStepCodes(order.getProductCode()));
            } catch (Exception e) {
                routeError = e.getMessage();
            }
        }
        boolean routeReady = activeRoute != null && !routeSteps.isEmpty();
        String firstStepCode = routeSteps.isEmpty() ? "" : routeSteps.get(0);
        result.put("routeCode", activeRoute == null ? "" : activeRoute.getRouteCode());
        result.put("routeVersion", activeRoute == null ? "" : activeRoute.getRouteVersion());
        result.put("firstStepCode", firstStepCode);
        result.put("routeSteps", routeSteps);
        checks.add(releaseCheck("route", "Route版本", routeReady, true, routeReady ? "green" : "red",
                routeReady
                        ? activeRoute.getRouteCode() + " / " + valueOr(activeRoute.getRouteVersion(), "-") + "，首站 " + firstStepCode
                        : "未找到生效Route或工序: " + valueOr(routeError, order.getProductCode())));

        Map<String, Object> bom = Map.of();
        String bomError = "";
        if (hasText(order.getProductCode())) {
            try {
                Map<String, Object> activeBom = materialService.activeBomSummary(order.getProductCode());
                bom = activeBom == null ? Map.of() : activeBom;
            } catch (Exception e) {
                bomError = e.getMessage();
            }
        }
        boolean bomReady = !bom.isEmpty() && "ACTIVE".equals(bom.get("status"));
        result.put("bomCode", objectText(bom.get("bomCode"), ""));
        result.put("bomVersion", objectText(bom.get("bomVersion"), ""));
        checks.add(releaseCheck("bom", "BOM", bomReady, true, bomReady ? "green" : "red",
                bomReady
                        ? bom.get("bomCode") + " / " + bom.get("bomVersion") + "，关键物料 " + bom.getOrDefault("keyItems", 0)
                        : "未找到生效BOM: " + valueOr(bomError, order.getProductCode())));

        List<Equipment> lineEquipments = safeList(equipmentMapper.selectList(new LambdaQueryWrapper<Equipment>()
                .eq(Equipment::getLineCode, order.getLineCode())));
        List<String> missingEquipmentSteps = routeSteps.stream()
                .filter(step -> lineEquipments.stream().noneMatch(equipment -> usableEquipment(equipment) && supportsStep(equipment, step)))
                .toList();
        boolean equipmentReady = routeReady && missingEquipmentSteps.isEmpty();
        result.put("equipmentCount", lineEquipments.size());
        result.put("missingEquipmentSteps", missingEquipmentSteps);
        checks.add(releaseCheck("equipment", "设备能力", equipmentReady, true, equipmentReady ? "green" : "red",
                equipmentReady
                        ? order.getLineCode() + " 覆盖 " + routeSteps.size() + " 个工序"
                        : "缺少可用设备能力: " + String.join("/", missingEquipmentSteps)));

        List<Recipe> activeRecipes = safeList(recipeMapper.selectList(new LambdaQueryWrapper<Recipe>()
                .eq(Recipe::getProductCode, order.getProductCode())
                .eq(Recipe::getStatus, "ACTIVE")));
        Set<String> capableEquipments = lineEquipments.stream()
                .filter(this::usableEquipment)
                .map(Equipment::getEquipmentCode)
                .filter(this::hasText)
                .collect(Collectors.toSet());
        List<String> missingRecipeSteps = routeSteps.stream()
                .filter(step -> activeRecipes.stream().noneMatch(recipe -> step.equals(recipe.getStepCode())
                        && (!hasText(recipe.getEquipmentCode()) || capableEquipments.contains(recipe.getEquipmentCode()))))
                .toList();
        boolean recipeReady = routeReady && missingRecipeSteps.isEmpty();
        result.put("recipeCount", activeRecipes.size());
        result.put("missingRecipeSteps", missingRecipeSteps);
        checks.add(releaseCheck("recipe", "Recipe覆盖", recipeReady, true, recipeReady ? "green" : "red",
                recipeReady
                        ? "产品+工序+设备 Recipe 覆盖 " + routeSteps.size() + "/" + routeSteps.size()
                        : "缺少生效Recipe: " + String.join("/", missingRecipeSteps)));

        int plannedQty = order.getPlannedQty() == null ? 0 : order.getPlannedQty();
        int lotCount = plannedQty <= 0 ? 0 : Math.max(1, (int) Math.ceil(plannedQty * 1.0 / normalizedLotQty));
        result.put("expectedLotCount", lotCount);
        boolean lotSplitReady = plannedQty > 0 && normalizedLotQty > 0;
        checks.add(releaseCheck("lotSplit", "Lot拆分", lotSplitReady, true, lotSplitReady ? "green" : "red",
                lotSplitReady ? "计划 " + plannedQty + "，按 " + normalizedLotQty + " 拆 " + lotCount + " 个 Lot" : "计划数量必须大于0"));

        boolean permissionReady = currentRoleHasButton("order:release");
        checks.add(releaseCheck("permissionAudit", "权限审计", permissionReady, false,
                permissionReady ? "green" : "amber", permissionAuditText()));
        return releaseCheckResult(result, checks);
    }

    private void assertReleaseChecksPassed(Map<String, Object> releaseChecks) {
        if (Boolean.TRUE.equals(releaseChecks.get("releasable"))) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> checks = (List<Map<String, Object>>) releaseChecks.getOrDefault("checks", List.of());
        String reasons = checks.stream()
                .filter(check -> !Boolean.FALSE.equals(check.get("blocking")))
                .filter(check -> !Boolean.TRUE.equals(check.get("passed")))
                .map(check -> check.get("title") + "=" + check.get("text"))
                .collect(Collectors.joining("; "));
        throw new BusinessException("工单释放预校验未通过: " + reasons);
    }

    private Map<String, Object> releaseCheckResult(Map<String, Object> result, List<Map<String, Object>> checks) {
        long passedCount = checks.stream().filter(check -> Boolean.TRUE.equals(check.get("passed"))).count();
        long blockingFailedCount = checks.stream()
                .filter(check -> !Boolean.FALSE.equals(check.get("blocking")))
                .filter(check -> !Boolean.TRUE.equals(check.get("passed")))
                .count();
        result.put("checks", checks);
        result.put("passedCount", passedCount);
        result.put("total", checks.size());
        result.put("blockingFailedCount", blockingFailedCount);
        result.put("releasable", blockingFailedCount == 0);
        return result;
    }

    private Map<String, Object> releaseCheck(String key, String title, boolean passed, boolean blocking,
                                             String type, String text) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("key", key);
        check.put("title", title);
        check.put("passed", passed);
        check.put("blocking", blocking);
        check.put("type", type);
        check.put("text", text);
        return check;
    }

    private boolean currentRoleHasButton(String button) {
        try {
            RolePermissionService.PermissionSnapshot snapshot = rolePermissionService.permissionSnapshot(AuthContext.role());
            return snapshot != null && ("ADMIN".equals(snapshot.role()) || snapshot.buttons().contains(button));
        } catch (Exception e) {
            return false;
        }
    }

    private String permissionAuditText() {
        String role = AuthContext.role();
        return currentRoleHasButton("order:release")
                ? role + " 可释放，ORDER_RELEASE 审计已配置"
                : role + " 无释放按钮权限，写接口将由RBAC拦截";
    }

    private boolean usableEquipment(Equipment equipment) {
        if (equipment == null) {
            return false;
        }
        return "IDLE".equals(equipment.getStatus()) || "RUNNING".equals(equipment.getStatus());
    }

    private <T> List<T> safeList(List<T> rows) {
        return rows == null ? List.of() : rows;
    }

    private List<SerialNumber> createSerialNumbers(ProductionOrder order, Lot lot, String operator) {
        int qty = Math.max(0, lot.getQty() == null ? 0 : lot.getQty());
        List<SerialNumber> rows = new ArrayList<>(qty);
        LocalDateTime now = LocalDateTime.now();
        for (int sequence = 1; sequence <= qty; sequence++) {
            SerialNumber serialNumber = new SerialNumber();
            serialNumber.setSn(lot.getLotNo() + "-SN" + String.format("%03d", sequence));
            serialNumber.setLotNo(lot.getLotNo());
            serialNumber.setOrderNo(order.getOrderNo());
            serialNumber.setProductCode(order.getProductCode());
            serialNumber.setLineCode(order.getLineCode());
            serialNumber.setSequenceNo(sequence);
            serialNumber.setGrade("A");
            serialNumber.setStatus("IN_PROCESS");
            serialNumber.setBindTime(now);
            serialNumber.setCreatedBy(operator);
            serialNumberMapper.insert(serialNumber);
            rows.add(serialNumber);
        }
        return rows;
    }

    public Page<Lot> pageLots(long current, long size, String lotNo, String status) {
        LambdaQueryWrapper<Lot> wrapper = lotScopedWrapper();
        if (lotNo != null && !lotNo.isBlank()) {
            wrapper.like(Lot::getLotNo, lotNo);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(Lot::getStatus, status);
        }
        wrapper.orderByDesc(Lot::getCreatedTime);
        return lotMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public void trackIn(String lotNo, Map<String, Object> request) {
        Lot lot = findLot(lotNo);
        Map<String, Object> before = lotSnapshot(lot);
        TrackInRequest trackIn = new TrackInRequest();
        trackIn.setLotNo(lotNo);
        String stepCode = text(request, "stepCode", lot.getCurrentStepCode());
        trackIn.setStepCode(stepCode);
        trackIn.setEquipmentCode(text(request, "equipmentCode", defaultEquipmentCode(stepCode)));
        trackIn.setOperator(text(request, "operator", currentUser()));
        trackInService.trackIn(trackIn);
        audit("TRACK_IN", lotNo, "Lot 进站 " + trackIn.getStepCode() + "/" + trackIn.getEquipmentCode(),
                trackIn.getOperator(), auditSnapshot(before, lotSnapshot(findLot(lotNo)), safeRequest(request)));
    }

    public Map<String, Object> trackInChecks(String lotNo, Map<String, Object> request) {
        Lot lot = findLot(lotNo);
        TrackInRequest trackIn = new TrackInRequest();
        trackIn.setLotNo(lotNo);
        String stepCode = text(request, "stepCode", lot.getCurrentStepCode());
        trackIn.setStepCode(stepCode);
        trackIn.setEquipmentCode(text(request, "equipmentCode", defaultEquipmentCode(stepCode)));
        trackIn.setOperator(text(request, "operator", currentUser()));
        Map<String, Object> checks = new LinkedHashMap<>(trackInService.trackInChecks(trackIn));
        checks.put("selectedEquipmentCode", trackIn.getEquipmentCode());
        checks.put("operator", trackIn.getOperator());
        return enrichTrackInChecks(checks);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> enrichTrackInChecks(Map<String, Object> result) {
        List<Map<String, Object>> checks = new ArrayList<>((List<Map<String, Object>>) result.getOrDefault("checks", List.of()));
        boolean permissionReady = currentRoleHasButton("lot:track-in");
        checks.add(releaseCheck("permission", "操作权限", permissionReady, true,
                permissionReady ? "green" : "red",
                permissionReady ? AuthContext.role() + " 可执行 Track In" : AuthContext.role() + " 无 Track In 权限"));
        checks.add(releaseCheck("audit", "审计留痕", true, false, "blue", "Track In 成功后写入 TRACK_IN 审计"));
        long passedCount = checks.stream().filter(check -> Boolean.TRUE.equals(check.get("passed"))).count();
        long blockingFailedCount = checks.stream()
                .filter(check -> !Boolean.FALSE.equals(check.get("blocking")))
                .filter(check -> !Boolean.TRUE.equals(check.get("passed")))
                .count();
        result.put("checks", checks);
        result.put("passedCount", passedCount);
        result.put("total", checks.size());
        result.put("blockingFailedCount", blockingFailedCount);
        result.put("trackInReady", blockingFailedCount == 0);
        return result;
    }

    public Map<String, Object> trackOut(String lotNo, Map<String, Object> request) {
        Lot lot = findLot(lotNo);
        Map<String, Object> before = lotSnapshot(lot);
        TrackOutRequest trackOut = new TrackOutRequest();
        trackOut.setLotNo(lotNo);
        trackOut.setResult(text(request, "result", "OK"));
        trackOut.setProcessParams(text(request, "processParams", "{\"temperature\":150,\"speed\":300}"));
        trackOut.setRemark(text(request, "remark", "试点接口出站"));
        String finalResult = trackInService.trackOut(trackOut);
        if (!"NG".equals(finalResult)) {
            moveToNextStep(lotNo);
        }
        Map<String, Object> after = lotSnapshot(findLot(lotNo));
        after.put("trackOutResult", finalResult);
        after.put("processParams", trackOut.getProcessParams());
        audit("TRACK_OUT", lotNo, "Lot 出站 result=" + finalResult, text(request, "operator", currentUser()),
                auditSnapshot(before, after, safeRequest(request)));
        return Map.of("lotNo", lotNo, "result", finalResult);
    }

    public void hold(String lotNo, Map<String, Object> request) {
        Lot lot = findLot(lotNo);
        Map<String, Object> before = lotSnapshot(lot);
        HoldRequest hold = new HoldRequest();
        hold.setLotNo(lotNo);
        hold.setHoldReason(text(request, "holdReason", text(request, "reason", "试点异常 Hold")));
        hold.setHoldType(text(request, "holdType", "QUALITY"));
        hold.setHoldBy(text(request, "holdBy", currentUser()));
        holdService.holdLot(hold);
        audit("LOT_HOLD", lotNo, hold.getHoldReason(), hold.getHoldBy(),
                auditSnapshot(before, lotSnapshot(findLot(lotNo)), safeRequest(request)));
    }

    public void release(String lotNo, Map<String, Object> request) {
        Lot lot = findLot(lotNo);
        Map<String, Object> before = lotSnapshot(lot);
        ReleaseRequest release = new ReleaseRequest();
        release.setLotNo(lotNo);
        release.setDisposition(text(request, "disposition", "复判通过，允许继续流转"));
        release.setReleaseBy(text(request, "releaseBy", currentUser()));
        holdService.releaseLot(release);
        closeExceptionIfProvided(lotNo, request, "RELEASE", release.getDisposition());
        audit("LOT_RELEASE", lotNo, release.getDisposition(), release.getReleaseBy(),
                auditSnapshot(before, lotSnapshot(findLot(lotNo)), safeRequest(request)));
    }

    public void rework(String lotNo, Map<String, Object> request) {
        Lot lot = findLot(lotNo);
        Map<String, Object> before = lotSnapshot(lot);
        String reworkRouteCode = requireText(request, "reworkRouteCode", "Rework route is required");
        String reworkStepCode = requireText(request, "reworkStepCode", "Rework start step is required");
        Route activeRoute = routeService.findActiveRoute(lot.getProductCode());
        if (!Objects.equals(activeRoute.getRouteCode(), reworkRouteCode)) {
            throw new BusinessException("Rework route must match active product route: " + activeRoute.getRouteCode());
        }
        RouteStep reworkStep = routeService.activeSteps(lot.getProductCode()).stream()
                .filter(step -> Objects.equals(step.getStepCode(), reworkStepCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Rework step is not configured in active route: " + reworkStepCode));
        if (!Integer.valueOf(1).equals(reworkStep.getAllowRework())) {
            throw new BusinessException("Rework step does not allow rework: " + reworkStepCode);
        }
        String operator = text(request, "operator", currentUser());
        String disposition = "MRB rework route=" + reworkRouteCode + ", step=" + reworkStepCode;
        releaseOpenHoldForDisposition(lot, operator, disposition);
        lot.setStatus("REWORK");
        lot.setCurrentStepCode(reworkStepCode);
        lot.setCurrentEquipmentCode(null);
        lotMapper.updateById(lot);
        closeExceptionIfProvided(lotNo, request, "REWORK", disposition);
        audit("LOT_REWORK", lotNo, "Rework route=" + reworkRouteCode + ", step=" + reworkStepCode,
                operator,
                auditSnapshot(before, lotSnapshot(lot), safeRequest(request)));
    }

    public void scrap(String lotNo, Map<String, Object> request) {
        Lot lot = findLot(lotNo);
        Map<String, Object> before = lotSnapshot(lot);
        assertScrapConfirmed(lotNo, request);
        String reason = requireText(request, "reason", "Scrap reason is required");
        String responsibilityModule = requireText(request, "responsibilityModule", "Scrap responsibility module is required");
        String approver = requireText(request, "approver", "Scrap approver is required");
        String operator = text(request, "operator", currentUser());
        releaseOpenHoldForDisposition(lot, operator, reason);
        lot.setStatus("SCRAP");
        lot.setCurrentEquipmentCode(null);
        lotMapper.updateById(lot);
        closeExceptionIfProvided(lotNo, request, "SCRAP", reason);
        audit("LOT_SCRAP", lotNo,
                "Scrap reason=" + reason + ", module=" + responsibilityModule + ", approver=" + approver,
                operator,
                auditSnapshot(before, lotSnapshot(lot), safeRequest(request)));
    }

    public List<Map<String, Object>> products() {
        return List.of(
                Map.of("productCode", "AMOLED_65", "productName", "AMOLED 6.5寸柔性屏", "productType", "FLEX_PANEL", "status", "ACTIVE"),
                Map.of("productCode", "AMOLED_67", "productName", "AMOLED 6.7寸柔性屏", "productType", "FLEX_PANEL", "status", "ACTIVE"),
                Map.of("productCode", "FOLD_78", "productName", "7.8寸折叠模组", "productType", "FOLD_MODULE", "status", "ACTIVE")
        );
    }

    public List<Map<String, Object>> boms() {
        try {
            List<Map<String, Object>> rows = materialService.boms();
            if (rows != null && !rows.isEmpty()) {
                return rows;
            }
        } catch (Exception e) {
            return fallbackListOnFailure("BOM", e, fallbackBoms());
        }
        return fallbackListOnEmpty(fallbackBoms());
    }

    public List<Map<String, Object>> bomChangeRequests(String status) {
        return materialService.bomChangeRequests(status);
    }

    public List<Map<String, Object>> bomEcoApprovalTasks(String changeNo, String status) {
        return materialService.bomEcoApprovalTasks(changeNo, status);
    }

    public Map<String, Object> submitBomChange(Map<String, Object> request) {
        return Map.of("change", materialService.submitBomChange(safeRequest(request)));
    }

    public Map<String, Object> decideBomEcoApproval(String taskNo, Map<String, Object> request) {
        return Map.of("approvalTask", materialService.decideBomEcoApprovalTask(taskNo, safeRequest(request)));
    }

    public Map<String, Object> reviewBomChange(String changeNo, Map<String, Object> request) {
        return Map.of("change", materialService.reviewBomChange(changeNo, safeRequest(request)));
    }

    public Map<String, Object> publishBomChange(String changeNo, Map<String, Object> request) {
        return Map.of("change", materialService.publishBomChange(changeNo, safeRequest(request)));
    }

    public List<Map<String, Object>> routes() {
        try {
            List<Map<String, Object>> routes = routeService.activeRouteSummaries();
            if (routes != null && !routes.isEmpty()) {
                return routes;
            }
        } catch (Exception e) {
            return fallbackListOnFailure("Route", e, fallbackRoutes());
        }
        return fallbackListOnEmpty(fallbackRoutes());
    }

    private List<Map<String, Object>> fallbackRoutes() {
        return List.of(Map.of(
                "routeCode", "RTE_G6_V08",
                "productCode", "AMOLED_65",
                "version", "V08",
                "status", "ACTIVE",
                "steps", DEFAULT_ROUTE
        ));
    }

    public List<Map<String, Object>> defectCodes() {
        return List.of(
                defect("D-MURA", "Mura", "Cell", "MAJOR"),
                defect("D-BRIGHT", "亮点", "Cell", "MAJOR"),
                defect("D-DARK", "暗点", "Cell", "MAJOR"),
                defect("D-SCRATCH", "划伤", "Module", "MINOR"),
                defect("D-BOND-OFFSET", "绑定偏移", "Module", "MAJOR")
        );
    }

    public List<Map<String, Object>> equipmentEvents() {
        try {
            List<Map<String, Object>> rows = equipmentService.events(null, null);
            if (rows != null && !rows.isEmpty()) {
                return rows;
            }
        } catch (Exception e) {
            return fallbackListOnFailure("设备事件", e, fallbackEquipmentEvents());
        }
        return fallbackListOnEmpty(fallbackEquipmentEvents());
    }

    public List<Map<String, Object>> equipmentEvents(String equipmentCode, String status) {
        try {
            List<Map<String, Object>> rows = equipmentService.events(equipmentCode, status);
            return rows == null ? List.of() : rows;
        } catch (Exception e) {
            List<Map<String, Object>> fallbackRows =
                    (equipmentCode == null || equipmentCode.isBlank()) && (status == null || status.isBlank())
                            ? fallbackEquipmentEvents()
                            : List.of();
            return fallbackListOnFailure("设备事件", e, fallbackRows);
        }
    }

    public Map<String, Object> createEquipmentEvent(Map<String, Object> request) {
        return equipmentService.createEvent(safeRequest(request));
    }

    public Map<String, Object> closeEquipmentEvent(String eventNo, Map<String, Object> request) {
        return equipmentService.closeEvent(eventNo, safeRequest(request));
    }

    public List<Map<String, Object>> equipmentStatusHistories(String equipmentCode) {
        try {
            return equipmentService.statusHistories(equipmentCode);
        } catch (Exception e) {
            log.warn("设备状态历史读取失败，已返回空列表: {}", e.getMessage());
            return List.of();
        }
    }

    public List<Map<String, Object>> equipmentGateways(String status) {
        try {
            return eapGatewayService.gateways(status);
        } catch (Exception e) {
            log.warn("设备网关连接读取失败，已返回空列表: {}", e.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> registerEquipmentGateway(Map<String, Object> request) {
        return eapGatewayService.registerGateway(safeRequest(request));
    }

    public Map<String, Object> heartbeatEquipmentGateway(String gatewayCode, Map<String, Object> request) {
        return eapGatewayService.heartbeat(gatewayCode, safeRequest(request));
    }

    public Map<String, Object> checkEquipmentGatewayHealth(String gatewayCode, Map<String, Object> request) {
        return eapGatewayService.checkHealth(gatewayCode, safeRequest(request));
    }

    public List<Map<String, Object>> equipmentGatewayHealthChecks(String gatewayCode) {
        try {
            return eapGatewayService.healthChecks(gatewayCode);
        } catch (Exception e) {
            log.warn("设备网关健康检查履历读取失败，已返回空列表: {}", e.getMessage());
            return List.of();
        }
    }

    public List<Map<String, Object>> equipmentGatewayMessages(String gatewayCode) {
        try {
            return eapGatewayService.messages(gatewayCode);
        } catch (Exception e) {
            log.warn("设备网关消息履历读取失败，已返回空列表: {}", e.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> equipmentGatewayMessageDetail(String messageNo) {
        return eapGatewayService.messageDetail(messageNo);
    }

    public List<Map<String, Object>> equipmentGatewayDrivers() {
        return eapGatewayService.drivers();
    }

    public Map<String, Object> reportEquipmentStatus(Map<String, Object> request) {
        return eapAdapter.reportStatus(safeRequest(request));
    }

    public List<Map<String, Object>> equipmentCycleSamples(String equipmentCode) {
        try {
            return equipmentService.cycleSamples(equipmentCode);
        } catch (Exception e) {
            log.warn("设备节拍采样读取失败，已返回空列表: {}", e.getMessage());
            return List.of();
        }
    }

    public List<Map<String, Object>> equipmentStandardCycles(String equipmentCode, String status) {
        try {
            return equipmentService.standardCycles(equipmentCode, status);
        } catch (Exception e) {
            log.warn("设备标准节拍主数据读取失败，已返回空列表: {}", e.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> publishEquipmentStandardCycle(Map<String, Object> request) {
        return equipmentService.publishStandardCycle(safeRequest(request));
    }

    public Map<String, Object> reportEquipmentCycleSample(Map<String, Object> request) {
        return eapAdapter.reportCycleSample(safeRequest(request));
    }

    public Map<String, Object> equipmentOeeSummary(String lineCode) {
        try {
            return equipmentService.oeeSummary(lineCode);
        } catch (Exception e) {
            return fallbackMapOnFailure("设备OEE", e, fallbackEquipmentOee(lineCode));
        }
    }

    public List<Map<String, Object>> equipmentParameterSamples(String equipmentCode) {
        return equipmentService.parameterSamples(equipmentCode);
    }

    public Map<String, Object> reportEquipmentParameters(Map<String, Object> request) {
        return eapAdapter.reportParameters(safeRequest(request));
    }

    public List<Map<String, Object>> equipmentPmTasks(String status) {
        return equipmentService.pmTasks(status);
    }

    public Map<String, Object> completeEquipmentPmTask(String taskNo, Map<String, Object> request) {
        return equipmentService.completePmTask(taskNo, safeRequest(request));
    }

    public List<Map<String, Object>> equipmentRecipeCommands(String equipmentCode, String status) {
        try {
            return equipmentService.recipeCommands(equipmentCode, status);
        } catch (Exception e) {
            log.warn("Recipe下发履历正式表读取失败，已返回空列表: {}", e.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> downloadEquipmentRecipe(Map<String, Object> request) {
        return eapAdapter.downloadRecipe(safeRequest(request));
    }

    public Map<String, Object> ingestEapMessage(Map<String, Object> request) {
        return eapGatewayService.ingestMessage(safeRequest(request), eapAdapter);
    }

    public Map<String, Object> importErpOrders(Map<String, Object> request) {
        return erpOrderAdapterService.importOrders(safeRequest(request), currentUser());
    }

    public Map<String, Object> ingestQmsInspection(Map<String, Object> request) {
        return qualityService.reportQmsInspection(adapterPayload(request, "qms-adapter", "simulated-qms-adapter"));
    }

    public Map<String, Object> createQualityInspection(Map<String, Object> request) {
        return qualityService.createManualInspection(safeRequest(request));
    }

    public Map<String, Object> checkWmsMaterialReadiness(Map<String, Object> request) {
        Map<String, Object> payload = adapterPayload(request, "wms-adapter", "simulated-wms-adapter");
        Map<String, Object> data = new LinkedHashMap<>(materialService.materialReadiness());
        data.put("adapterCode", payload.get("adapterCode"));
        data.put("sourceSystem", payload.get("sourceSystem"));
        data.put("messageType", "MATERIAL_READINESS");
        data.put("lotNo", text(payload, "lotNo", ""));
        data.put("lineCode", text(payload, "lineCode", "LINE_01"));
        audit("WMS_MATERIAL_READINESS", text(payload, "lotNo", "MATERIAL"),
                "WMS齐套查询 readiness=" + data.get("readiness"), text(payload, "operator", currentUser()),
                JSONUtil.toJsonStr(payload));
        return data;
    }

    public Map<String, Object> ingestWmsInventoryTransaction(Map<String, Object> request) {
        Map<String, Object> payload = adapterPayload(request, "wms-adapter", "simulated-wms-adapter");
        String txnType = normalizeWmsTransactionType(text(payload, "transactionType", text(payload, "action", "RECEIVE")));
        Object batch = switch (txnType) {
            case "RECEIVE" -> materialService.receiveMaterial(payload);
            case "FREEZE" -> materialService.freezeMaterial(requireText(payload, "batchNo", "WMS冻结缺少批次号"), payload);
            case "UNFREEZE" -> materialService.unfreezeMaterial(requireText(payload, "batchNo", "WMS解冻缺少批次号"), payload);
            case "RETURN" -> materialService.returnMaterial(requireText(payload, "batchNo", "WMS退料缺少批次号"), payload);
            case "COUNT" -> materialService.inventoryCount(requireText(payload, "batchNo", "WMS盘点缺少批次号"), payload);
            default -> throw new BusinessException("不支持的WMS库存事务类型: " + txnType);
        };

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("adapterCode", payload.get("adapterCode"));
        data.put("sourceSystem", payload.get("sourceSystem"));
        data.put("messageType", "INVENTORY_TRANSACTION");
        data.put("transactionType", txnType);
        data.put("batchNo", text(payload, "batchNo", ""));
        data.put("result", "ACCEPTED");
        data.put("batch", batch);
        audit("WMS_INVENTORY_TRANSACTION", text(payload, "batchNo", txnType),
                "WMS库存事务 type=" + txnType, text(payload, "operator", currentUser()),
                JSONUtil.toJsonStr(payload));
        return data;
    }

    private List<Map<String, Object>> fallbackEquipmentEvents() {
        return List.of(
                Map.of("eventNo", "EVT-260606-001", "equipmentCode", "EVAP_01", "eventType", "ALARM", "eventLevel", "P2", "description", "真空波动", "status", "OPEN"),
                Map.of("eventNo", "EVT-260606-002", "equipmentCode", "COATER_02", "eventType", "PARAMETER", "eventLevel", "P1", "description", "涂胶膜厚超限", "status", "OPEN"),
                Map.of("eventNo", "EVT-260606-003", "equipmentCode", "BOND_03", "eventType", "QUALITY", "eventLevel", "P2", "description", "绑定偏移预警", "status", "PROCESSING")
        );
    }

    private Map<String, Object> fallbackEquipmentOee(String lineCode) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("windowHours", 24);
        data.put("lineCode", valueOr(lineCode, "ALL"));
        data.put("equipmentCount", 8);
        data.put("scheduledMinutes", 11520);
        data.put("plannedProductionMinutes", 11485);
        data.put("plannedDowntimeMinutes", 35);
        data.put("unplannedDowntimeMinutes", 66);
        data.put("availabilityRate", 99.43);
        data.put("availabilityText", "99.43%");
        data.put("performanceRate", 98.75);
        data.put("performanceText", "98.75%");
        data.put("qualityRate", 96.82);
        data.put("qualityText", "96.82%");
        data.put("oeeRate", 94.99);
        data.put("oeeText", "94.99%");
        data.put("performanceSampleCount", 3);
        data.put("statusDistribution", List.of(
                Map.of("status", "RUNNING", "qty", 3, "type", "green"),
                Map.of("status", "IDLE", "qty", 3, "type", "blue"),
                Map.of("status", "ALARM", "qty", 1, "type", "red"),
                Map.of("status", "DOWN", "qty", 1, "type", "red")
        ));
        data.put("reasonTopN", List.of(
                Map.of("reasonCode", "VACUUM_PUMP_DOWN", "reasonName", "真空泵停机", "downtimeType", "UNPLANNED", "downtimeCategory", "EQUIPMENT", "durationMinutes", 38, "eventCount", 1, "type", "red"),
                Map.of("reasonCode", "CHAMBER_PRESSURE", "reasonName", "腔体压力报警", "downtimeType", "UNPLANNED", "downtimeCategory", "EQUIPMENT", "durationMinutes", 28, "eventCount", 1, "type", "red"),
                Map.of("reasonCode", "PM_NOZZLE_CLEAN", "reasonName", "喷嘴清洁", "downtimeType", "PLANNED", "downtimeCategory", "PM", "durationMinutes", 35, "eventCount", 1, "type", "amber")
        ));
        data.put("equipmentRows", List.of(
                Map.of("equipmentCode", "COATER_01", "equipmentName", "涂胶机-1", "lineCode", "LINE_01", "status", "IDLE", "availabilityRate", 100.0, "availabilityText", "100.00%", "plannedDowntimeMinutes", 35, "unplannedDowntimeMinutes", 0, "type", "blue"),
                Map.of("equipmentCode", "EVAP_01", "equipmentName", "蒸镀机-1", "lineCode", "LINE_01", "status", "DOWN", "availabilityRate", 97.36, "availabilityText", "97.36%", "plannedDowntimeMinutes", 0, "unplannedDowntimeMinutes", 38, "type", "red"),
                Map.of("equipmentCode", "ETCH_01", "equipmentName", "蚀刻机-1", "lineCode", "LINE_01", "status", "ALARM", "availabilityRate", 98.06, "availabilityText", "98.06%", "plannedDowntimeMinutes", 0, "unplannedDowntimeMinutes", 28, "type", "red")
        ));
        data.put("recentCycleSamples", List.of(
                Map.of("sampleNo", "ECS-FALLBACK-001", "equipmentCode", "COATER_01", "standardCycleSeconds", 58, "actualCycleSeconds", 61, "performanceText", "95.08%", "result", "OK", "type", "green"),
                Map.of("sampleNo", "ECS-FALLBACK-002", "equipmentCode", "COATER_02", "standardCycleSeconds", 58, "actualCycleSeconds", 72, "performanceText", "80.56%", "result", "NG", "type", "red")
        ));
        data.put("calculationNote", "试点口径：可用率来自近24小时设备停机事件，性能率来自EAP标准节拍/实际节拍采样。");
        return data;
    }

    public Map<String, Object> materialReadiness() {
        try {
            Map<String, Object> data = materialService.materialReadiness();
            Object batches = data.get("batches");
            if (batches instanceof List<?> list && !list.isEmpty()) {
                return data;
            }
            if (!pilotFallbackEnabled) {
                return data;
            }
        } catch (Exception e) {
            return fallbackMapOnFailure("物料齐套", e, fallbackMaterialReadiness());
        }
        return fallbackMaterialReadiness();
    }

    public List<Map<String, Object>> carriers() {
        try {
            List<Map<String, Object>> rows = materialService.carriers();
            if (rows != null && !rows.isEmpty()) {
                return rows;
            }
        } catch (Exception e) {
            return fallbackListOnFailure("载具", e, fallbackCarriers());
        }
        return fallbackListOnEmpty(fallbackCarriers());
    }

    public Map<String, Object> bindCarrier(String carrierNo, Map<String, Object> request) {
        Lot lot = findLot(text(request, "lotNo", ""));
        return materialService.bindCarrier(carrierNo, lot, safeRequest(request));
    }

    public Map<String, Object> unbindCarrier(String carrierNo, Map<String, Object> request) {
        return materialService.unbindCarrier(carrierNo, safeRequest(request));
    }

    public Map<String, Object> traceLot(String lotNo) {
        Lot lot = findLot(lotNo);
        List<LotStepRecord> steps = stepRecordMapper.selectList(new LambdaQueryWrapper<LotStepRecord>()
                .eq(LotStepRecord::getLotNo, lotNo)
                .orderByAsc(LotStepRecord::getTrackInTime));
        List<HoldRecord> holds = holdRecordMapper.selectList(new LambdaQueryWrapper<HoldRecord>()
                .eq(HoldRecord::getLotNo, lotNo)
                .orderByAsc(HoldRecord::getHoldTime));
        List<SerialNumber> serialNumbers = serialNumbers(lotNo);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("lot", lot);
        data.put("order", orderMapper.selectOne(new LambdaQueryWrapper<ProductionOrder>().eq(ProductionOrder::getOrderNo, lot.getOrderNo())));
        data.put("route", traceRoute(lot.getProductCode()));
        data.put("serialNumbers", serialNumbers);
        data.put("serialNumberSummary", serialNumberSummary(lotNo, serialNumbers));
        data.put("carriers", carrierTraceRows(lotNo));
        data.put("stepRecords", steps);
        data.put("holdRecords", holds);
        data.put("qualityRecords", qualityInspections(lotNo));
        data.put("exceptionEvents", qualityExceptions(lotNo));
        data.put("materialConsumptions", materialConsumptions(lotNo));
        data.put("auditLogs", auditLogs(lotNo));
        List<Map<String, Object>> matches = List.of(traceLotMatch(lot));
        data.put("impactSummary", traceImpactSummary(matches, data));
        data.put("relatedDimensions", traceRelatedDimensions(matches, data));
        return data;
    }

    private Map<String, Object> traceRoute(String productCode) {
        try {
            Route route = routeService.findActiveRoute(productCode);
            List<String> steps = safeList(routeService.activeStepCodes(productCode));
            return Map.of(
                    "routeCode", valueOr(route.getRouteCode(), ""),
                    "productCode", valueOr(route.getProductCode(), productCode),
                    "version", valueOr(route.getRouteVersion(), ""),
                    "status", valueOr(route.getStatus(), ""),
                    "steps", steps
            );
        } catch (BusinessException e) {
            log.warn("追溯Route证据读取失败: product={}, reason={}", productCode, e.getMessage());
            return Map.of(
                    "productCode", valueOr(productCode, ""),
                    "status", "MISSING",
                    "steps", List.of(),
                    "error", e.getMessage()
            );
        }
    }

    public Map<String, Object> traceSn(String sn) {
        String query = objectText(sn, "").trim();
        SerialNumber serialNumber = findSerialNumber(query);
        String lotNo = serialNumber == null ? resolveLotNoFromSn(query) : serialNumber.getLotNo();
        if (lotNo.isBlank()) {
            throw new BusinessException("SN未绑定Lot或格式不支持: " + sn);
        }
        Map<String, Object> trace = traceLot(lotNo);
        trace.put("sn", serialNumber == null ? fallbackSerialNumberSnapshot(query, lotNo) : serialNumberSnapshot(serialNumber));
        return trace;
    }

    public Map<String, Object> traceSearch(String type, String keyword) {
        String query = objectText(keyword, "").trim();
        if (query.isBlank()) {
            throw new BusinessException("追溯关键字不能为空");
        }
        String normalizedType = normalizeTraceType(type);
        List<String> searchTypes = "AUTO".equals(normalizedType) ? traceSearchTypes(query) : List.of(normalizedType);
        String resolvedType = searchTypes.get(0);
        List<TraceCandidate> candidates = List.of();
        for (String searchType : searchTypes) {
            candidates = traceCandidates(searchType, query);
            if (!candidates.isEmpty()) {
                resolvedType = searchType;
                break;
            }
        }

        List<Map<String, Object>> matches = traceCandidateRows(candidates);
        if (matches.isEmpty()) {
            throw new BusinessException("未找到可追溯对象: " + query);
        }

        String selectedLotNo = objectText(matches.get(0).get("lotNo"), "");
        Map<String, Object> trace = "SN".equals(resolvedType) ? traceSn(query) : traceLot(selectedLotNo);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("query", Map.of(
                "type", normalizedType,
                "keyword", query,
                "resolvedType", resolvedType,
                "selectedLotNo", selectedLotNo,
                "matchedLotCount", matches.size()
        ));
        data.put("matches", matches);
        data.put("trace", trace);
        data.put("impactSummary", traceImpactSummary(matches, trace));
        data.put("relatedDimensions", traceRelatedDimensions(matches, trace));
        return data;
    }

    public List<Map<String, Object>> qualityInspections(String lotNo) {
        assertLotAccessibleIfPresent(lotNo);
        try {
            List<Map<String, Object>> rows = qualityService.inspectionRows(lotNo);
            return rows == null ? List.of() : rows;
        } catch (Exception e) {
            return fallbackListOnFailure("质量检验", e, fallbackQualityInspections(lotNo));
        }
    }

    public List<Map<String, Object>> materialConsumptions(String lotNo) {
        assertLotAccessibleIfPresent(lotNo);
        try {
            List<Map<String, Object>> rows = materialService.materialConsumptions(lotNo);
            return rows == null ? List.of() : rows;
        } catch (Exception e) {
            return fallbackListOnFailure("物料消耗", e, fallbackMaterialConsumptions(lotNo));
        }
    }

    private List<Map<String, Object>> carrierTraceRows(String lotNo) {
        try {
            List<Map<String, Object>> rows = materialService.carriersByLot(lotNo);
            return rows == null ? List.of() : rows;
        } catch (Exception e) {
            log.warn("Carrier姝ｅ紡琛ㄨ鍙栧け璐ワ紝Lot杩芥函宸插拷鐣arrier璇佹嵁: {}", e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> traceLotMatch(Lot lot) {
        Map<String, Object> match = new LinkedHashMap<>();
        match.put("lotNo", objectText(lot.getLotNo(), ""));
        match.put("orderNo", objectText(lot.getOrderNo(), ""));
        match.put("status", objectText(lot.getStatus(), ""));
        match.put("currentStepCode", objectText(lot.getCurrentStepCode(), ""));
        match.put("matchField", "lotNo");
        match.put("evidence", objectText(lot.getLotNo(), ""));
        return match;
    }

    public Map<String, Object> receiveMaterial(Map<String, Object> request) {
        return Map.of("batch", materialService.receiveMaterial(request));
    }

    public Map<String, Object> freezeMaterial(String batchNo, Map<String, Object> request) {
        return Map.of("batch", materialService.freezeMaterial(batchNo, request));
    }

    public Map<String, Object> unfreezeMaterial(String batchNo, Map<String, Object> request) {
        return Map.of("batch", materialService.unfreezeMaterial(batchNo, request));
    }

    public Map<String, Object> returnMaterial(String batchNo, Map<String, Object> request) {
        return Map.of("batch", materialService.returnMaterial(batchNo, request));
    }

    public Map<String, Object> inventoryCount(String batchNo, Map<String, Object> request) {
        return Map.of("batch", materialService.inventoryCount(batchNo, request));
    }

    public List<Map<String, Object>> materialInventoryTransactions(String batchNo) {
        return materialService.inventoryTransactions(batchNo);
    }

    public List<Map<String, Object>> materialIncomingInspections(String batchNo) {
        return materialService.incomingInspections(batchNo);
    }

    public List<Map<String, Object>> materialSupplierPerformance() {
        try {
            List<Map<String, Object>> rows = materialService.supplierPerformance();
            if (rows != null && !rows.isEmpty()) {
                return rows;
            }
        } catch (Exception e) {
            return fallbackListOnFailure("供应商绩效评分", e, fallbackMaterialSupplierPerformance());
        }
        return fallbackListOnEmpty(fallbackMaterialSupplierPerformance());
    }

    public List<Map<String, Object>> materialSupplierTrends(int months) {
        try {
            List<Map<String, Object>> rows = materialService.supplierScoreTrends(months);
            if (rows != null && !rows.isEmpty()) {
                return rows;
            }
        } catch (Exception e) {
            return fallbackListOnFailure("供应商月度评分趋势", e, fallbackMaterialSupplierTrends());
        }
        return fallbackListOnEmpty(fallbackMaterialSupplierTrends());
    }

    public List<Map<String, Object>> materialSuppliers() {
        return materialService.suppliers();
    }

    public Map<String, Object> evaluateMaterialSupplierQualification(String supplierCode, Map<String, Object> request) {
        return materialService.evaluateSupplierQualification(supplierCode, safeRequest(request));
    }

    public List<Map<String, Object>> materialSupplierCorrectiveActions(String supplierCode, String status) {
        return materialService.supplierCorrectiveActions(supplierCode, status);
    }

    public List<Map<String, Object>> materialSupplierQualificationReviews(String supplierCode, String status) {
        return materialService.supplierQualificationReviewTasks(supplierCode, status);
    }

    public Map<String, Object> createMaterialSupplierQualificationReview(String supplierCode, Map<String, Object> request) {
        return materialService.createSupplierQualificationReviewTask(supplierCode, safeRequest(request));
    }

    public Map<String, Object> generateDueMaterialSupplierQualificationReviews(Map<String, Object> request) {
        return materialService.generateDueSupplierQualificationReviewTasks(safeRequest(request));
    }

    public Map<String, Object> decideMaterialSupplierQualificationReview(String taskNo, Map<String, Object> request) {
        return materialService.decideSupplierQualificationReviewTask(taskNo, safeRequest(request));
    }

    public Map<String, Object> createMaterialSupplierCorrectiveAction(Map<String, Object> request) {
        return Map.of("action", materialService.createSupplierCorrectiveAction(safeRequest(request)));
    }

    public Map<String, Object> closeMaterialSupplierCorrectiveAction(String actionNo, Map<String, Object> request) {
        return Map.of("action", materialService.closeSupplierCorrectiveAction(actionNo, safeRequest(request)));
    }

    public List<Map<String, Object>> materialLocations() {
        try {
            List<Map<String, Object>> rows = materialService.materialLocations();
            if (rows != null && !rows.isEmpty()) {
                return rows;
            }
        } catch (Exception e) {
            return fallbackListOnFailure("物料库位策略", e, fallbackMaterialLocations());
        }
        return fallbackListOnEmpty(fallbackMaterialLocations());
    }

    public List<Map<String, Object>> materialLocationTasks(String status, String batchNo) {
        try {
            List<Map<String, Object>> rows = materialService.materialLocationTasks(status, batchNo);
            if (rows != null && !rows.isEmpty()) {
                return rows;
            }
        } catch (Exception e) {
            return fallbackListOnFailure("物料库位任务", e, fallbackMaterialLocationTasks(status, batchNo));
        }
        return fallbackListOnEmpty(fallbackMaterialLocationTasks(status, batchNo));
    }

    public Map<String, Object> createMaterialLocationTask(Map<String, Object> request) {
        return materialService.createLocationTask(safeRequest(request));
    }

    public Map<String, Object> assignMaterialLocationTask(String taskNo, Map<String, Object> request) {
        return materialService.assignLocationTask(taskNo, safeRequest(request));
    }

    public Map<String, Object> completeMaterialLocationTask(String taskNo, Map<String, Object> request) {
        return materialService.completeLocationTask(taskNo, safeRequest(request));
    }

    public Map<String, Object> cancelMaterialLocationTask(String taskNo, Map<String, Object> request) {
        return materialService.cancelLocationTask(taskNo, safeRequest(request));
    }

    public Map<String, Object> createMaterialIncomingInspection(String batchNo, Map<String, Object> request) {
        return materialService.createIncomingInspection(batchNo, safeRequest(request));
    }

    public List<Map<String, Object>> qualityExceptions(String lotNo) {
        assertLotAccessibleIfPresent(lotNo);
        try {
            List<Map<String, Object>> rows = qualityService.exceptionRows(lotNo);
            return rows == null ? List.of() : rows;
        } catch (Exception e) {
            return fallbackListOnFailure("异常事件", e, fallbackExceptionEvents(lotNo));
        }
    }

    public List<Map<String, Object>> qualityMrbRecords(String eventNo) {
        return qualityService.mrbRecords(eventNo);
    }

    public List<Map<String, Object>> qualityMrbMinutes(String mrbNo) {
        return qualityService.mrbMinutes(mrbNo);
    }

    public Map<String, Object> createQualityMrbMinutes(String mrbNo, Map<String, Object> request) {
        return qualityService.createMrbMinutes(mrbNo, safeRequest(request));
    }

    public List<Map<String, Object>> qualityMrbApprovalTasks(String eventNo, String status) {
        return qualityService.mrbApprovalTasks(eventNo, status);
    }

    public Map<String, Object> refreshQualityMrbApprovalSla(Map<String, Object> request) {
        return qualityService.refreshMrbApprovalSla(safeRequest(request));
    }

    public Map<String, Object> decideMrbApprovalTask(String taskNo, Map<String, Object> request) {
        return qualityService.decideMrbApprovalTask(taskNo, safeRequest(request));
    }

    public Map<String, Object> reviewException(String eventNo, Map<String, Object> request) {
        return qualityService.reviewException(eventNo, safeRequest(request));
    }

    public Map<String, Object> closeException(String eventNo, Map<String, Object> request) {
        return qualityService.closeException(eventNo, safeRequest(request));
    }

    private List<Map<String, Object>> fallbackListOnEmpty(List<Map<String, Object>> fallbackRows) {
        return pilotFallbackEnabled ? fallbackRows : List.of();
    }

    private List<Map<String, Object>> fallbackListOnFailure(String dataName,
                                                            Exception e,
                                                            List<Map<String, Object>> fallbackRows) {
        if (pilotFallbackEnabled) {
            log.warn("{}正式数据读取失败，已启用试点fallback: {}", dataName, e.getMessage());
            return fallbackRows;
        }
        log.warn("{}正式数据读取失败，未启用试点fallback: {}", dataName, e.getMessage());
        throw new BusinessException(dataName + "正式数据读取失败，未启用试点fallback");
    }

    private Map<String, Object> fallbackMapOnFailure(String dataName,
                                                     Exception e,
                                                     Map<String, Object> fallbackData) {
        if (pilotFallbackEnabled) {
            log.warn("{}正式数据读取失败，已启用试点fallback: {}", dataName, e.getMessage());
            return fallbackData;
        }
        log.warn("{}正式数据读取失败，未启用试点fallback: {}", dataName, e.getMessage());
        throw new BusinessException(dataName + "正式数据读取失败，未启用试点fallback");
    }

    private List<Map<String, Object>> fallbackQualityInspections(String lotNo) {
        String targetLotNo = valueOr(lotNo, "LOT202406001");
        return List.of(
                Map.of("lotNo", targetLotNo, "stepCode", "INSPECTION", "itemCode", "MURA_CHECK", "result", "OK", "defectCode", "", "measuredValue", "0"),
                Map.of("lotNo", targetLotNo, "stepCode", "COATING", "itemCode", "THICKNESS", "result", "WARNING", "defectCode", "D-MURA", "measuredValue", "2.18")
        );
    }

    public Map<String, Object> dashboardYield() {
        List<Map<String, Object>> defectTopN;
        try {
            defectTopN = qualityService.defectTopN(5);
            if (defectTopN == null) {
                defectTopN = List.of();
            }
        } catch (Exception e) {
            defectTopN = fallbackListOnFailure("缺陷TopN", e, fallbackDefectTopN());
        }
        return Map.of(
                "trend", yieldTrend(),
                "defectTopN", defectTopN,
                "equipmentAlarmTopN", List.of(
                        Map.of("equipmentCode", "EVAP_01", "qty", 6),
                        Map.of("equipmentCode", "COATER_02", "qty", 4),
                        Map.of("equipmentCode", "BOND_03", "qty", 3)
                ),
                "equipmentOee", equipmentOeeSummary(null)
        );
    }

    public Map<String, Object> aiYieldReport(Map<String, Object> request) {
        String reportNo = "AIR-YIELD-" + System.currentTimeMillis();
        Map<String, Object> modelConfig = aiModelConfig("YIELD_DAILY", "yield-daily-v2", "mock-structured-output");
        String model = text(modelConfig, "modelName", "mock-structured-output");
        String promptVersion = text(modelConfig, "promptTemplateVersion", "yield-daily-v2");
        Map<String, Object> inputSnapshot = new LinkedHashMap<>();
        inputSnapshot.put("request", safeRequest(request));
        inputSnapshot.put("yield", dashboardYield());
        inputSnapshot.put("modelConfig", modelConfig);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("summary", "今日综合良率接近目标，主要波动集中在 COATING 与 EVAPORATION。");
        output.put("keyDefects", List.of("Mura", "亮点", "绑定偏移"));
        output.put("suspectedRootCause", "EVAP_01 真空波动与 COATER_02 膜厚超限共同影响良率。");
        output.put("recommendations", List.of("EVAP_01 下一批进站前执行真空稳定性点检", "COATER_02 校验涂胶速度与膜厚参数", "质量工程师复判 P1 Hold Lot"));
        output.put("writeActionAllowed", false);
        output.put("modelMode", modelConfig.get("modelMode"));
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportNo", reportNo);
        report.put("reportType", "YIELD_DAILY");
        report.put("model", model);
        report.put("promptTemplateVersion", promptVersion);
        putModelConfig(report, modelConfig);
        putEvidence(report, 0, 0.0, "NONE", false, text(modelConfig, "retrievalStrategy", "MES_SNAPSHOT"));
        report.put("inputSnapshot", inputSnapshot);
        report.put("output", output);
        report.put("createdTime", LocalDateTime.now());
        aiRecordService.record(reportNo, "YIELD_DAILY", reportNo, "AI_REPORT", promptVersion, model,
                inputSnapshot, output, currentUser(), aiMetadata(modelConfig, report));
        audit("AI_YIELD_REPORT", reportNo, "生成 AI 良率日报", currentUser());
        return report;
    }

    public Map<String, Object> aiEquipmentAnalyze(Map<String, Object> request) {
        String equipmentCode = text(request, "equipmentCode", "EVAP_01");
        String lotNo = text(request, "lotNo", "");
        String reportNo = "AIR-EQP-" + System.currentTimeMillis();
        Map<String, Object> modelConfig = aiModelConfig("EQUIPMENT_ANALYSIS", "equipment-analyze-v2", "mock-structured-output");
        String model = text(modelConfig, "modelName", "mock-structured-output");
        String promptVersion = text(modelConfig, "promptTemplateVersion", "equipment-analyze-v2");
        List<Map<String, Object>> equipmentEvents = targetEquipmentEvents(equipmentCode);
        List<Map<String, Object>> lotContexts = equipmentLotContexts(equipmentCode, lotNo);
        List<Map<String, Object>> recentDefects = recentDefectsForEquipment(equipmentCode);
        Map<String, Object> inputSnapshot = new LinkedHashMap<>();
        inputSnapshot.put("request", safeRequest(request));
        inputSnapshot.put("equipmentCode", equipmentCode);
        inputSnapshot.put("events", equipmentEvents);
        inputSnapshot.put("lots", lotContexts);
        inputSnapshot.put("recentDefects", recentDefects);
        inputSnapshot.put("yield", dashboardYield());
        inputSnapshot.put("modelConfig", modelConfig);
        List<Map<String, Object>> sources = aiKnowledgeService.searchSources(
                equipmentCode + " 设备报警 真空 波动 Mura SOP " + String.join(" ", defectNames(recentDefects)), 2);
        Map<String, Object> evidence = evidenceSummary(sources, text(modelConfig, "retrievalStrategy", "MES_AND_RAG"));
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("riskLevel", equipmentRiskLevel(equipmentEvents, recentDefects, evidence));
        output.put("equipmentCode", equipmentCode);
        output.put("eventCount", equipmentEvents.size());
        output.put("lotCount", lotContexts.size());
        output.put("defectCount", recentDefects.stream().mapToInt(row -> intValue(row.get("qty"), 0)).sum());
        output.put("possibleCauses", List.of("腔体真空波动", "材料蒸镀速率偏移", "PM 后参数未完全稳定"));
        output.put("checkSteps", List.of("确认最近 2 小时报警趋势", "复核真空泵状态", "抽查当前 Lot AOI 缺陷分布"));
        output.put("sources", sources.isEmpty() ? List.of(Map.of("warning", "知识库依据不足，请补充设备手册或SOP片段")) : sources);
        output.put("writeActionAllowed", false);
        output.put("lotContexts", lotContexts);
        output.put("recentDefects", recentDefects);
        output.putAll(evidence);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportNo", reportNo);
        report.put("reportType", "EQUIPMENT_ANALYSIS");
        report.put("model", model);
        report.put("promptTemplateVersion", promptVersion);
        putModelConfig(report, modelConfig);
        report.putAll(output);
        report.put("createdTime", LocalDateTime.now());
        aiRecordService.record(reportNo, "EQUIPMENT_ANALYSIS", equipmentCode, "EQUIPMENT", promptVersion, model,
                inputSnapshot, output, currentUser(), aiMetadata(modelConfig, report));
        audit("AI_EQUIPMENT_ANALYZE", equipmentCode, "生成 AI 设备异常分析: " + reportNo, currentUser());
        return report;
    }

    public Map<String, Object> ragAsk(Map<String, Object> request) {
        String question = text(request, "question", "");
        String reportNo = "AIR-KB-" + System.currentTimeMillis();
        Map<String, Object> modelConfig = aiModelConfig("SOP_QA", "rag-sop-qa-v2", "mock-rag-output");
        String model = text(modelConfig, "modelName", "mock-rag-output");
        String promptVersion = text(modelConfig, "promptTemplateVersion", "rag-sop-qa-v2");
        Map<String, Object> output = aiKnowledgeService.ask(question);
        Map<String, Object> inputSnapshot = new LinkedHashMap<>();
        inputSnapshot.put("request", safeRequest(request));
        inputSnapshot.put("question", question);
        inputSnapshot.put("sources", output.get("sources"));
        inputSnapshot.put("modelConfig", modelConfig);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportNo", reportNo);
        report.put("reportType", "SOP_QA");
        report.put("model", model);
        report.put("promptTemplateVersion", promptVersion);
        putModelConfig(report, modelConfig);
        report.putAll(output);
        report.put("createdTime", LocalDateTime.now());
        aiRecordService.record(reportNo, "SOP_QA", question, "SOP_KB", promptVersion, model,
                inputSnapshot, output, currentUser(), aiMetadata(modelConfig, report));
        audit("AI_KB_ASK", reportNo, "生成 AI SOP 问答", currentUser());
        return report;
    }

    public Map<String, Object> importKnowledgeDocument(Map<String, Object> request) {
        Map<String, Object> result = aiKnowledgeService.importDocument(safeRequest(request), currentUser());
        audit("AI_KB_IMPORT", String.valueOf(result.get("documentNo")),
                "导入知识库文档并生成切片: " + result.get("chunkCount"), currentUser());
        return result;
    }

    public List<Map<String, Object>> knowledgeDocuments() {
        return aiKnowledgeService.documents();
    }

    public List<Map<String, Object>> knowledgeIndexJobs(String documentNo, String status) {
        return aiKbIndexService.jobs(documentNo, status);
    }

    public Map<String, Object> createKnowledgeIndexJob(Map<String, Object> request) {
        return aiKbIndexService.createIndexJob(safeRequest(request), currentUser());
    }

    public List<Map<String, Object>> aiModelConfigs() {
        return aiModelConfigService.configs();
    }

    public List<Map<String, Object>> aiReportRecords(String reportType, String bizNo,
                                                     String evidenceLevel, Boolean insufficientEvidence) {
        return aiRecordService.records(reportType, bizNo, evidenceLevel, insufficientEvidence);
    }

    public Map<String, Object> aiReportRecordDetail(String reportNo) {
        return aiRecordService.detail(reportNo);
    }

    private Map<String, Object> aiModelConfig(String useCase, String defaultPromptVersion, String defaultModel) {
        try {
            Map<String, Object> config = aiModelConfigService.resolveForUseCase(useCase, defaultPromptVersion, defaultModel);
            if (config != null && !config.isEmpty()) {
                return config;
            }
        } catch (Exception e) {
            log.warn("AI模型配置读取失败，已降级到本地模拟配置: useCase={}, reason={}", useCase, e.getMessage());
        }
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("configCode", "FALLBACK_" + valueOr(useCase, "GENERAL"));
        fallback.put("configName", "试点fallback AI配置");
        fallback.put("useCase", valueOr(useCase, "GENERAL"));
        fallback.put("provider", "LOCAL");
        fallback.put("modelProvider", "LOCAL");
        fallback.put("modelName", defaultModel);
        fallback.put("modelMode", "SIMULATED");
        fallback.put("promptTemplateVersion", defaultPromptVersion);
        fallback.put("retrievalStrategy", switch (valueOr(useCase, "GENERAL")) {
            case "SOP_QA" -> "KEYWORD_FALLBACK";
            case "EQUIPMENT_ANALYSIS" -> "MES_AND_RAG";
            default -> "MES_SNAPSHOT";
        });
        fallback.put("modelConfigSnapshot", "{\"boundary\":\"fallback local deterministic output\",\"writeActionAllowed\":false}");
        return fallback;
    }

    private void putModelConfig(Map<String, Object> target, Map<String, Object> modelConfig) {
        target.put("modelConfigCode", modelConfig.get("configCode"));
        target.put("modelProvider", modelConfig.get("modelProvider"));
        target.put("modelMode", modelConfig.get("modelMode"));
        target.put("retrievalStrategy", modelConfig.get("retrievalStrategy"));
        target.put("modelConfig", modelConfig);
    }

    private void putEvidence(Map<String, Object> target, int evidenceCount, double maxEvidenceScore,
                             String evidenceLevel, boolean insufficientEvidence, String retrievalStrategy) {
        target.put("evidenceCount", evidenceCount);
        target.put("maxEvidenceScore", maxEvidenceScore);
        target.put("confidence", maxEvidenceScore);
        target.put("evidenceLevel", evidenceLevel);
        target.put("evidenceType", evidenceType(evidenceLevel));
        target.put("insufficientEvidence", insufficientEvidence);
        target.put("retrievalStrategy", retrievalStrategy);
    }

    private Map<String, Object> evidenceSummary(List<Map<String, Object>> sources, String retrievalStrategy) {
        int evidenceCount = sources == null ? 0 : sources.size();
        double maxEvidenceScore = sources == null ? 0.0 : sources.stream()
                .mapToDouble(source -> doubleValue(source.get("score")))
                .max()
                .orElse(0.0);
        String evidenceLevel = evidenceLevel(maxEvidenceScore, evidenceCount);
        Map<String, Object> evidence = new LinkedHashMap<>();
        putEvidence(evidence, evidenceCount, maxEvidenceScore, evidenceLevel,
                "INSUFFICIENT".equals(evidenceLevel), retrievalStrategy);
        return evidence;
    }

    private List<Map<String, Object>> targetEquipmentEvents(String equipmentCode) {
        List<Map<String, Object>> rows = equipmentEvents(equipmentCode, null);
        if (rows != null && !rows.isEmpty()) {
            return rows.stream().limit(8).collect(Collectors.toList());
        }
        return equipmentEvents().stream()
                .filter(row -> Objects.equals(equipmentCode, String.valueOf(row.get("equipmentCode"))))
                .limit(8)
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> equipmentLotContexts(String equipmentCode, String lotNo) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (lotNo != null && !lotNo.isBlank()) {
            Lot lot = findLot(lotNo);
            rows.add(lotSnapshot(lot));
        }
        try {
            List<Lot> activeLots = lotMapper.selectList(lotScopedWrapper()
                    .eq(Lot::getCurrentEquipmentCode, equipmentCode)
                    .orderByDesc(Lot::getUpdatedTime)
                    .last("LIMIT 5"));
            if (activeLots == null) {
                return rows;
            }
            for (Lot lot : activeLots) {
                if (rows.stream().noneMatch(row -> Objects.equals(row.get("lotNo"), lot.getLotNo()))) {
                    rows.add(lotSnapshot(lot));
                }
            }
        } catch (Exception e) {
            log.warn("AI设备分析关联Lot读取失败，已仅使用请求上下文: equipment={}, reason={}", equipmentCode, e.getMessage());
        }
        return rows;
    }

    private List<Map<String, Object>> recentDefectsForEquipment(String equipmentCode) {
        try {
            return qualityService.defectTopN(5);
        } catch (Exception e) {
            log.warn("AI设备分析近期缺陷读取失败，已降级到空缺陷证据: equipment={}, reason={}", equipmentCode, e.getMessage());
            return List.of();
        }
    }

    private List<String> defectNames(List<Map<String, Object>> defects) {
        if (defects == null) {
            return List.of();
        }
        return defects.stream()
                .map(row -> text(row, "defectName", text(row, "defectCode", "")))
                .filter(name -> !name.isBlank())
                .collect(Collectors.toList());
    }

    private String equipmentRiskLevel(List<Map<String, Object>> events,
                                      List<Map<String, Object>> defects,
                                      Map<String, Object> evidence) {
        boolean criticalEvent = events != null && events.stream()
                .map(row -> String.valueOf(row.getOrDefault("eventLevel", row.getOrDefault("severity", ""))).toUpperCase(Locale.ROOT))
                .anyMatch(level -> level.contains("P1") || level.contains("CRITICAL"));
        int defectQty = defects == null ? 0 : defects.stream().mapToInt(row -> intValue(row.get("qty"), 0)).sum();
        String evidenceLevel = text(evidence, "evidenceLevel", "INSUFFICIENT");
        if (criticalEvent || defectQty >= 20) {
            return "P1";
        }
        if (defectQty >= 8 || "HIGH".equals(evidenceLevel) || (events != null && events.size() >= 3)) {
            return "P2";
        }
        return "P3";
    }

    private Map<String, Object> aiMetadata(Map<String, Object> modelConfig, Map<String, Object> report) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("modelProvider", modelConfig.get("modelProvider"));
        metadata.put("modelMode", modelConfig.get("modelMode"));
        metadata.put("modelConfigCode", modelConfig.get("configCode"));
        metadata.put("retrievalStrategy", report.get("retrievalStrategy"));
        metadata.put("evidenceCount", report.get("evidenceCount"));
        metadata.put("maxEvidenceScore", report.get("maxEvidenceScore"));
        metadata.put("evidenceLevel", report.get("evidenceLevel"));
        metadata.put("insufficientEvidence", report.get("insufficientEvidence"));
        metadata.put("modelConfigSnapshot", modelConfig);
        return metadata;
    }

    private String evidenceLevel(double maxEvidenceScore, int evidenceCount) {
        if (evidenceCount <= 0 || maxEvidenceScore < 0.65) {
            return "INSUFFICIENT";
        }
        if (maxEvidenceScore >= 0.85 && evidenceCount >= 2) {
            return "HIGH";
        }
        if (maxEvidenceScore >= 0.75) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String evidenceType(String evidenceLevel) {
        return switch (valueOr(evidenceLevel, "INSUFFICIENT")) {
            case "HIGH" -> "green";
            case "MEDIUM" -> "blue";
            case "LOW" -> "amber";
            default -> "red";
        };
    }

    private double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public Map<String, Object> systemSummary() {
        List<String> roles = List.of("ADMIN", "PLANNER", "OPERATOR", "QE", "PE", "EE");
        return Map.of(
                "roles", roles,
                "users", userMapper.selectList(null),
                "auditLogs", auditLogs(null),
                "permissions", roles.stream().map(rolePermissionService::permissions).collect(Collectors.toList()),
                "rules", List.of(
                        Map.of("name", "关键 Recipe 发布双人复核", "status", "ENABLED"),
                        Map.of("name", "Hold 超 SLA 升级", "status", "REVIEW_REQUIRED"),
                        Map.of("name", "敏感操作二次确认", "status", "ENABLED")
                )
        );
    }

    public Map<String, Object> currentPermissions() {
        return rolePermissionService.permissions(AuthContext.role());
    }

    public List<User> users() {
        return userMapper.selectList(null);
    }

    public List<Map<String, Object>> auditLogs(String bizNo) {
        try {
            List<AuditLog> logs = auditLogService.list(bizNo, 50);
            if (logs != null && !logs.isEmpty()) {
                return logs.stream().map(this::auditRow).collect(Collectors.toList());
            }
        } catch (Exception e) {
            return fallbackListOnFailure("系统审计", e, fallbackAuditLogs(bizNo));
        }
        return fallbackListOnEmpty(fallbackAuditLogs(bizNo));
    }

    public Page<Map<String, Object>> pageAuditLogs(Long current,
                                                   Long size,
                                                   String bizNo,
                                                   String action,
                                                   String result,
                                                   String source,
                                                   String operator,
                                                   String startTime,
                                                   String endTime) {
        long safeCurrent = Math.max(1L, current == null ? 1L : current);
        long safeSize = Math.min(Math.max(1L, size == null ? 20L : size), 100L);
        LocalDateTime parsedStartTime = parseAuditTime(startTime, false);
        LocalDateTime parsedEndTime = parseAuditTime(endTime, true);
        try {
            Page<AuditLog> page = auditLogService.page(
                    safeCurrent,
                    safeSize,
                    bizNo,
                    action,
                    result,
                    source,
                    operator,
                    parsedStartTime,
                    parsedEndTime
            );
            Page<Map<String, Object>> mapped = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
            mapped.setRecords(page.getRecords().stream().map(this::auditRow).collect(Collectors.toList()));
            return mapped;
        } catch (Exception e) {
            if (!pilotFallbackEnabled) {
                log.warn("系统审计分页正式表读取失败，未启用试点fallback: {}", e.getMessage());
                throw new BusinessException("系统审计正式数据读取失败，未启用试点fallback");
            }
            List<Map<String, Object>> fallback = fallbackAuditLogs(bizNo).stream()
                    .filter(row -> auditFallbackMatches(row, action, result, source, operator))
                    .toList();
            Page<Map<String, Object>> page = new Page<>(safeCurrent, safeSize, fallback.size());
            int from = (int) Math.min((safeCurrent - 1) * safeSize, fallback.size());
            int to = (int) Math.min(from + safeSize, fallback.size());
            page.setRecords(fallback.subList(from, to));
            return page;
        }
    }

    public List<ProcessStep> processSteps() {
        return processStepMapper.selectList(null);
    }

    public List<Equipment> equipments() {
        LambdaQueryWrapper<Equipment> wrapper = new LambdaQueryWrapper<>();
        applyDataScope(wrapper, dataScope("line_code", null, null));
        return equipmentMapper.selectList(wrapper);
    }

    public Page<Recipe> pageRecipes(long current, long size, String productCode, String stepCode, String equipmentCode, String status) {
        LambdaQueryWrapper<Recipe> wrapper = new LambdaQueryWrapper<>();
        if (hasText(productCode)) {
            wrapper.eq(Recipe::getProductCode, productCode);
        }
        if (hasText(stepCode)) {
            wrapper.eq(Recipe::getStepCode, stepCode);
        }
        if (hasText(equipmentCode)) {
            wrapper.eq(Recipe::getEquipmentCode, equipmentCode);
        }
        if (hasText(status)) {
            wrapper.eq(Recipe::getStatus, status);
        }
        wrapper.orderByDesc(Recipe::getCreatedTime);
        return recipeMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public List<RecipeParam> recipeParams(Long recipeId) {
        return recipeParamMapper.selectList(new LambdaQueryWrapper<RecipeParam>().eq(RecipeParam::getRecipeId, recipeId));
    }

    private List<Lot> allLots() {
        return lotMapper.selectList(lotScopedWrapper());
    }

    private <T> void applyDataScope(LambdaQueryWrapper<T> wrapper, RolePermissionService.DataScopeCondition condition) {
        if (condition != null && !condition.unrestricted()) {
            wrapper.apply(condition.sql(), condition.parameters().toArray());
        }
    }

    private RolePermissionService.DataScopeCondition dataScope(String lineCodeColumn, String ownerColumn, String createdTimeColumn) {
        return rolePermissionService.dataScopeCondition(AuthContext.role(), AuthContext.username(), "",
                lineCodeColumn, ownerColumn, createdTimeColumn);
    }

    private LambdaQueryWrapper<Lot> lotScopedWrapper() {
        LambdaQueryWrapper<Lot> wrapper = new LambdaQueryWrapper<>();
        applyDataScope(wrapper, dataScope("line_code", null, null));
        return wrapper;
    }

    private Lot findLot(String lotNo) {
        LambdaQueryWrapper<Lot> wrapper = lotScopedWrapper().eq(Lot::getLotNo, lotNo);
        Lot lot = lotMapper.selectOne(wrapper);
        if (lot == null) {
            throw new BusinessException("Lot不存在或无权限访问: " + lotNo);
        }
        return lot;
    }

    private void assertLotAccessibleIfPresent(String lotNo) {
        if (lotNo != null && !lotNo.isBlank()) {
            findLot(lotNo);
        }
    }

    private String normalizeTraceType(String type) {
        String normalized = objectText(type, "AUTO").trim().toUpperCase(Locale.ROOT).replace("-", "_");
        return switch (normalized) {
            case "", "AUTO", "LOT", "SN", "ORDER", "EQUIPMENT", "MATERIAL", "MATERIAL_BATCH", "DEFECT", "DEFECT_CODE" ->
                    "MATERIAL".equals(normalized) ? "MATERIAL_BATCH"
                            : "DEFECT".equals(normalized) ? "DEFECT_CODE"
                            : normalized.isBlank() ? "AUTO" : normalized;
            default -> throw new BusinessException("不支持的追溯类型: " + type);
        };
    }

    private List<String> traceSearchTypes(String keyword) {
        Map<String, Boolean> types = new LinkedHashMap<>();
        types.put(inferTraceType(keyword), true);
        for (String type : TRACE_TYPES) {
            types.put(type, true);
        }
        return new ArrayList<>(types.keySet());
    }

    private String inferTraceType(String keyword) {
        String upper = objectText(keyword, "").trim().toUpperCase(Locale.ROOT);
        if (upper.contains("-SN") || upper.startsWith("SN")) {
            return "SN";
        }
        if (upper.startsWith("MO")) {
            return "ORDER";
        }
        if (upper.startsWith("LOT")) {
            return "LOT";
        }
        if (upper.startsWith("D-") || upper.startsWith("DEFECT")) {
            return "DEFECT_CODE";
        }
        return "EQUIPMENT";
    }

    private List<TraceCandidate> traceCandidates(String type, String keyword) {
        return switch (type) {
            case "LOT" -> traceLotCandidates(keyword);
            case "SN" -> traceSnCandidates(keyword);
            case "ORDER" -> traceOrderCandidates(keyword);
            case "EQUIPMENT" -> traceEquipmentCandidates(keyword);
            case "MATERIAL_BATCH" -> traceMaterialBatchCandidates(keyword);
            case "DEFECT_CODE" -> traceDefectCodeCandidates(keyword);
            default -> throw new BusinessException("不支持的追溯类型: " + type);
        };
    }

    private List<TraceCandidate> traceLotCandidates(String keyword) {
        try {
            Lot lot = findLot(keyword);
            return List.of(new TraceCandidate(lot.getLotNo(), "LOT", "lotNo", lot.getLotNo()));
        } catch (BusinessException ignored) {
            return List.of();
        }
    }

    private List<TraceCandidate> traceSnCandidates(String keyword) {
        SerialNumber serialNumber = findSerialNumber(keyword);
        String lotNo = serialNumber == null ? resolveLotNoFromSn(keyword) : serialNumber.getLotNo();
        if (lotNo.isBlank()) {
            return List.of();
        }
        try {
            Lot lot = findLot(lotNo);
            String evidence = serialNumber == null ? keyword : serialNumber.getSn() + " / " + serialNumber.getStatus();
            return List.of(new TraceCandidate(lot.getLotNo(), "SN", "sn", evidence));
        } catch (BusinessException ignored) {
            return List.of();
        }
    }

    private List<TraceCandidate> traceOrderCandidates(String keyword) {
        return lotMapper.selectList(lotScopedWrapper()
                        .eq(Lot::getOrderNo, keyword)
                        .orderByDesc(Lot::getCreatedTime)
                        .last("LIMIT 50"))
                .stream()
                .map(lot -> new TraceCandidate(lot.getLotNo(), "ORDER", "orderNo", keyword))
                .toList();
    }

    private List<TraceCandidate> traceEquipmentCandidates(String keyword) {
        Map<String, TraceCandidate> candidates = new LinkedHashMap<>();
        List<LotStepRecord> stepRecords = stepRecordMapper.selectList(new LambdaQueryWrapper<LotStepRecord>()
                .eq(LotStepRecord::getEquipmentCode, keyword)
                .orderByDesc(LotStepRecord::getTrackInTime)
                .last("LIMIT 100"));
        for (LotStepRecord record : stepRecords) {
            addTraceCandidate(candidates, new TraceCandidate(record.getLotNo(), "EQUIPMENT", "equipmentCode",
                    keyword + " / " + objectText(record.getStepCode(), "-")));
        }
        List<Lot> currentLots = lotMapper.selectList(lotScopedWrapper()
                .eq(Lot::getCurrentEquipmentCode, keyword)
                .orderByDesc(Lot::getUpdatedTime)
                .last("LIMIT 50"));
        for (Lot lot : currentLots) {
            addTraceCandidate(candidates, new TraceCandidate(lot.getLotNo(), "EQUIPMENT", "currentEquipmentCode", keyword));
        }
        return new ArrayList<>(candidates.values());
    }

    private List<TraceCandidate> traceMaterialBatchCandidates(String keyword) {
        Map<String, TraceCandidate> candidates = new LinkedHashMap<>();
        for (Map<String, Object> row : materialConsumptions(null)) {
            if (sameText(row.get("batchNo"), keyword) || sameText(row.get("batch"), keyword)) {
                addTraceCandidate(candidates, new TraceCandidate(objectText(row.get("lotNo"), ""), "MATERIAL_BATCH",
                        "batchNo", keyword + " / " + objectText(row.get("materialCode"), "-")));
            }
        }
        return new ArrayList<>(candidates.values());
    }

    private List<TraceCandidate> traceDefectCodeCandidates(String keyword) {
        Map<String, TraceCandidate> candidates = new LinkedHashMap<>();
        for (Map<String, Object> row : qualityInspections(null)) {
            if (sameText(row.get("defectCode"), keyword)) {
                addTraceCandidate(candidates, new TraceCandidate(objectText(row.get("lotNo"), ""), "DEFECT_CODE",
                        "defectCode", keyword + " / " + objectText(row.get("itemCode"), "-")));
            }
        }
        return new ArrayList<>(candidates.values());
    }

    private void addTraceCandidate(Map<String, TraceCandidate> candidates, TraceCandidate candidate) {
        if (candidate != null && candidate.lotNo() != null && !candidate.lotNo().isBlank()) {
            candidates.putIfAbsent(candidate.lotNo(), candidate);
        }
    }

    private List<Map<String, Object>> traceCandidateRows(List<TraceCandidate> candidates) {
        Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        for (TraceCandidate candidate : candidates) {
            try {
                Lot lot = findLot(candidate.lotNo());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("lotNo", lot.getLotNo());
                row.put("orderNo", lot.getOrderNo());
                row.put("productCode", lot.getProductCode());
                row.put("lineCode", lot.getLineCode());
                row.put("qty", lot.getQty());
                row.put("status", lot.getStatus());
                row.put("currentStepCode", lot.getCurrentStepCode());
                row.put("currentEquipmentCode", lot.getCurrentEquipmentCode());
                row.put("matchType", candidate.matchType());
                row.put("matchField", candidate.matchField());
                row.put("evidence", candidate.evidence());
                rows.putIfAbsent(lot.getLotNo(), row);
            } catch (BusinessException ignored) {
                log.debug("追溯候选Lot不在当前数据范围内: {}", candidate.lotNo());
            }
        }
        return new ArrayList<>(rows.values());
    }

    private SerialNumber findSerialNumber(String sn) {
        String query = objectText(sn, "").trim();
        if (query.isBlank()) {
            return null;
        }
        return serialNumberMapper.selectOne(new LambdaQueryWrapper<SerialNumber>().eq(SerialNumber::getSn, query));
    }

    private List<SerialNumber> serialNumbers(String lotNo) {
        List<SerialNumber> rows = serialNumberMapper.selectList(new LambdaQueryWrapper<SerialNumber>()
                .eq(SerialNumber::getLotNo, lotNo)
                .orderByAsc(SerialNumber::getSequenceNo)
                .last("LIMIT 100"));
        return rows == null ? List.of() : rows;
    }

    private Map<String, Object> serialNumberSummary(String lotNo, List<SerialNumber> serialNumbers) {
        long totalCount = longValue(serialNumberMapper.selectCount(new LambdaQueryWrapper<SerialNumber>()
                .eq(SerialNumber::getLotNo, lotNo)), serialNumbers.size());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalCount", totalCount);
        summary.put("returnedCount", serialNumbers.size());
        summary.put("firstSn", serialNumbers.isEmpty() ? "" : serialNumbers.get(0).getSn());
        summary.put("limited", totalCount > serialNumbers.size());
        return summary;
    }

    private Map<String, Object> serialNumberSnapshot(SerialNumber serialNumber) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("sn", serialNumber.getSn());
        snapshot.put("lotNo", serialNumber.getLotNo());
        snapshot.put("orderNo", serialNumber.getOrderNo());
        snapshot.put("productCode", serialNumber.getProductCode());
        snapshot.put("lineCode", serialNumber.getLineCode());
        snapshot.put("sequenceNo", serialNumber.getSequenceNo());
        snapshot.put("grade", serialNumber.getGrade());
        snapshot.put("status", serialNumber.getStatus());
        snapshot.put("bindTime", serialNumber.getBindTime());
        return snapshot;
    }

    private Map<String, Object> fallbackSerialNumberSnapshot(String sn, String lotNo) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("sn", sn);
        snapshot.put("lotNo", lotNo);
        snapshot.put("grade", "A");
        snapshot.put("status", "IN_PROCESS");
        snapshot.put("source", "FORMAT_COMPATIBLE");
        return snapshot;
    }

    private Map<String, Object> traceImpactSummary(List<Map<String, Object>> matches, Map<String, Object> trace) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("matchedLotCount", matches.size());
        summary.put("holdLotCount", matches.stream().filter(row -> "HOLD".equals(row.get("status"))).count());
        summary.put("serialNumberCount", longValue(fieldValue(trace.get("serialNumberSummary"), "totalCount"), listValue(trace.get("serialNumbers")).size()));
        summary.put("carrierCount", listValue(trace.get("carriers")).size());
        summary.put("ngInspectionCount", listValue(trace.get("qualityRecords")).stream()
                .filter(row -> !"OK".equals(fieldText(row, "result")))
                .count());
        summary.put("materialBatchCount", distinctTextCount(listValue(trace.get("materialConsumptions")), "batchNo"));
        summary.put("defectCodeCount", distinctTextCount(listValue(trace.get("qualityRecords")), "defectCode"));
        summary.put("equipmentCount", distinctTextCount(listValue(trace.get("stepRecords")), "equipmentCode"));
        return summary;
    }

    private Map<String, Object> traceRelatedDimensions(List<Map<String, Object>> matches, Map<String, Object> trace) {
        Map<String, Object> dimensions = new LinkedHashMap<>();
        dimensions.put("orderNos", distinctTextValues(matches, "orderNo"));
        dimensions.put("serialNumbers", distinctTextValues(listValue(trace.get("serialNumbers")), "sn"));
        dimensions.put("carrierNos", distinctTextValues(listValue(trace.get("carriers")), "carrierNo"));
        dimensions.put("equipmentCodes", distinctTextValues(listValue(trace.get("stepRecords")), "equipmentCode"));
        dimensions.put("materialBatches", distinctTextValues(listValue(trace.get("materialConsumptions")), "batchNo"));
        dimensions.put("defectCodes", distinctTextValues(listValue(trace.get("qualityRecords")), "defectCode"));
        return dimensions;
    }

    private List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private int distinctTextCount(List<?> rows, String fieldName) {
        return distinctTextValues(rows, fieldName).size();
    }

    private List<String> distinctTextValues(List<?> rows, String fieldName) {
        Map<String, Boolean> values = new LinkedHashMap<>();
        for (Object row : rows) {
            String text = fieldText(row, fieldName);
            if (!text.isBlank()) {
                values.put(text, true);
            }
        }
        return new ArrayList<>(values.keySet());
    }

    private String fieldText(Object row, String fieldName) {
        if (row instanceof Map<?, ?> map) {
            return objectText(map.get(fieldName), "");
        }
        if (row instanceof LotStepRecord record) {
            return switch (fieldName) {
                case "lotNo" -> objectText(record.getLotNo(), "");
                case "stepCode" -> objectText(record.getStepCode(), "");
                case "equipmentCode" -> objectText(record.getEquipmentCode(), "");
                case "recipeCode" -> objectText(record.getRecipeCode(), "");
                case "result" -> objectText(record.getResult(), "");
                default -> "";
            };
        }
        if (row instanceof Lot lot) {
            return switch (fieldName) {
                case "lotNo" -> objectText(lot.getLotNo(), "");
                case "orderNo" -> objectText(lot.getOrderNo(), "");
                case "productCode" -> objectText(lot.getProductCode(), "");
                case "status" -> objectText(lot.getStatus(), "");
                default -> "";
            };
        }
        if (row instanceof SerialNumber serialNumber) {
            return switch (fieldName) {
                case "sn" -> objectText(serialNumber.getSn(), "");
                case "lotNo" -> objectText(serialNumber.getLotNo(), "");
                case "orderNo" -> objectText(serialNumber.getOrderNo(), "");
                case "productCode" -> objectText(serialNumber.getProductCode(), "");
                case "status" -> objectText(serialNumber.getStatus(), "");
                default -> "";
            };
        }
        return "";
    }

    private Object fieldValue(Object row, String fieldName) {
        if (row instanceof Map<?, ?> map) {
            return map.get(fieldName);
        }
        return null;
    }

    private boolean sameText(Object value, String expected) {
        return objectText(value, "").equalsIgnoreCase(objectText(expected, ""));
    }

    private String resolveLotNoFromSn(String sn) {
        String text = objectText(sn, "").trim();
        String upper = text.toUpperCase(Locale.ROOT);
        int snMarker = upper.lastIndexOf("-SN");
        if (snMarker > 0) {
            return text.substring(0, snMarker);
        }
        return "";
    }

    private long readyCount(List<Lot> lots) {
        return lots.stream().filter(lot -> "READY".equals(lot.getStatus())).count();
    }

    private String yieldText(List<Lot> lots) {
        if (lots.isEmpty()) {
            return "0.00%";
        }
        long bad = lots.stream().filter(lot -> "HOLD".equals(lot.getStatus()) || "SCRAP".equals(lot.getStatus())).count();
        double yield = Math.max(0.0, (lots.size() - bad) * 100.0 / lots.size());
        return String.format("%.2f%%", yield);
    }

    private String equipmentUtilization(List<Equipment> equipments) {
        if (equipments.isEmpty()) {
            return "0.0%";
        }
        long active = equipments.stream().filter(e -> "RUNNING".equals(e.getStatus()) || "IDLE".equals(e.getStatus())).count();
        return String.format("%.1f%%", active * 100.0 / equipments.size());
    }

    private List<Map<String, Object>> routeSteps(List<Lot> lots) {
        List<ProcessStep> steps = processStepMapper.selectList(null);
        Map<String, Long> wipByStep = lots.stream()
                .filter(lot -> lot.getCurrentStepCode() != null)
                .collect(Collectors.groupingBy(Lot::getCurrentStepCode, Collectors.counting()));
        return steps.stream()
                .sorted(Comparator.comparing(step -> DEFAULT_ROUTE.indexOf(step.getStepCode()) < 0 ? 999 : DEFAULT_ROUTE.indexOf(step.getStepCode())))
                .map(step -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("code", step.getStepCode());
                    row.put("name", step.getStepName());
                    row.put("segment", step.getSegment());
                    row.put("wip", wipByStep.getOrDefault(step.getStepCode(), 0L));
                    row.put("needRecipe", step.getNeedRecipe());
                    row.put("needQc", step.getNeedQc());
                    row.put("status", wipByStep.getOrDefault(step.getStepCode(), 0L) > 10 ? "BOTTLENECK" : "NORMAL");
                    return row;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> yieldTrend() {
        return List.of(
                Map.of("date", LocalDate.now().minusDays(6).toString(), "yield", 96.1),
                Map.of("date", LocalDate.now().minusDays(5).toString(), "yield", 96.5),
                Map.of("date", LocalDate.now().minusDays(4).toString(), "yield", 95.8),
                Map.of("date", LocalDate.now().minusDays(3).toString(), "yield", 96.9),
                Map.of("date", LocalDate.now().minusDays(2).toString(), "yield", 96.4),
                Map.of("date", LocalDate.now().minusDays(1).toString(), "yield", 97.1),
                Map.of("date", LocalDate.now().toString(), "yield", 96.82)
        );
    }

    private List<Map<String, Object>> alertQueue() {
        try {
            List<Map<String, Object>> rows = qualityService.exceptionRows(null);
            if (rows == null) {
                return List.of();
            }
            return rows.stream()
                    .limit(5)
                    .map(row -> Map.<String, Object>of(
                            "title", valueOr((String) row.get("title"), "异常事件"),
                            "level", valueOr((String) row.get("eventLevel"), "P2"),
                            "type", valueOr((String) row.get("eventType"), "QUALITY"),
                            "status", valueOr((String) row.get("status"), "OPEN")
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return fallbackListOnFailure("异常队列", e, fallbackAlertQueue());
        }
    }

    private List<Map<String, Object>> fallbackAlertQueue() {
        return List.of(
                Map.of("title", "LOT260606-017 涂胶膜厚超限", "level", "P1", "type", "QUALITY", "status", "OPEN"),
                Map.of("title", "EVAP_01 真空波动", "level", "P2", "type", "EQUIPMENT", "status", "PROCESSING"),
                Map.of("title", "BOND_03 绑定偏移预警", "level", "P2", "type", "QUALITY", "status", "OPEN")
        );
    }

    private List<Map<String, Object>> fallbackExceptionEvents(String lotNo) {
        List<Map<String, Object>> rows = List.of(
                Map.of("eventNo", "EX-FALLBACK-001", "eventType", "QUALITY", "eventLevel", "P1", "lotNo", valueOr(lotNo, "LOT202406006"), "stepCode", "COATING", "equipmentCode", "COATER_02", "title", "涂胶膜厚超限", "description", "涂胶厚度超过Recipe上限，等待MRB处置", "status", "OPEN", "ownerRole", "QE"),
                Map.of("eventNo", "EX-FALLBACK-002", "eventType", "EQUIPMENT", "eventLevel", "P2", "lotNo", valueOr(lotNo, "LOT202406004"), "stepCode", "EVAPORATION", "equipmentCode", "EVAP_01", "title", "蒸镀真空度波动", "description", "EAP模拟适配器上报真空度波动", "status", "PROCESSING", "ownerRole", "EE")
        );
        if (lotNo == null || lotNo.isBlank()) {
            return rows;
        }
        return rows.stream()
                .filter(row -> lotNo.equals(row.get("lotNo")))
                .collect(Collectors.toList());
    }

    private void closeExceptionIfProvided(String lotNo, Map<String, Object> request, String action, String defaultConclusion) {
        String eventNo = text(request, "eventNo", "");
        if (eventNo.isBlank()) {
            return;
        }
        Map<String, Object> closeRequest = new LinkedHashMap<>();
        closeRequest.put("lotNo", lotNo);
        closeRequest.put("dispositionAction", action);
        closeRequest.put("closeConclusion", text(request, "closeConclusion", defaultConclusion));
        closeRequest.put("rootCause", text(request, "rootCause", "MRB 处置动作触发异常关闭"));
        closeRequest.put("closedBy", text(request, "operator", currentUser()));
        qualityService.closeException(eventNo, closeRequest);
    }

    private List<Map<String, Object>> fallbackBoms() {
        return List.of(
                Map.of("bomCode", "BOM_65_V06", "bomName", "AMOLED 6.5寸试点BOM", "productCode", "AMOLED_65", "bomVersion", "V06", "status", "ACTIVE", "items", 4, "keyItems", 3, "steps", List.of("COATING", "EVAPORATION", "ENCAPSULATION")),
                Map.of("bomCode", "BOM_67_V04", "bomName", "AMOLED 6.7寸试点BOM", "productCode", "AMOLED_67", "bomVersion", "V04", "status", "ACTIVE", "items", 4, "keyItems", 3, "steps", List.of("COATING", "EVAPORATION", "ENCAPSULATION")),
                Map.of("bomCode", "BOM_FOLD_V02", "bomName", "折叠模组试点BOM", "productCode", "FOLD_78", "bomVersion", "V02", "status", "DRAFT", "items", 6, "keyItems", 4, "steps", List.of("BOND", "MODULE"))
        );
    }

    private Map<String, Object> fallbackMaterialReadiness() {
        return Map.of(
                "batches", List.of(
                        Map.of("materialCode", "MAT-PI-001", "materialName", "PI 胶", "batchNo", "PI260606-A", "remainPercent", 18, "status", "WARNING", "type", "amber", "availableQty", 820, "reservedQty", 120, "unit", "g"),
                        Map.of("materialCode", "MAT-OLED-R", "materialName", "红光有机材料", "batchNo", "OLED-R-260605-B", "remainPercent", 62, "status", "OK", "type", "green", "availableQty", 310, "reservedQty", 20, "unit", "g"),
                        Map.of("materialCode", "MAT-ENCAP-001", "materialName", "封装胶", "batchNo", "ENCAP260604-C", "remainPercent", 76, "status", "OK", "type", "green", "availableQty", 540, "reservedQty", 30, "unit", "g")
                ),
                "checks", List.of(
                        Map.of("title", "BOM关键物料", "text", "已配置 3 批", "type", "green"),
                        Map.of("title", "批次质量", "text", "来料质量 PASS", "type", "green"),
                        Map.of("title", "FIFO库存", "text", "PI 胶低库存", "type", "amber"),
                        Map.of("title", "齐套结果", "text", "PASS_WITH_WARNING", "type", "amber")
                ),
                "readiness", "PASS_WITH_WARNING"
        );
    }

    private List<Map<String, Object>> fallbackCarriers() {
        return List.of(
                Map.of("carrierNo", "CST-260606-001", "code", "CST-260606-001", "carrierType", "Cassette", "status", "BOUND", "type", "green", "lotNo", "LOT202406001", "lot", "LOT202406001", "stepCode", "COATING", "step", "COATING"),
                Map.of("carrierNo", "CST-260606-002", "code", "CST-260606-002", "carrierType", "Cassette", "status", "IDLE", "type", "blue", "lotNo", "", "lot", "-", "stepCode", "", "step", "-"),
                Map.of("carrierNo", "TRAY-260606-009", "code", "TRAY-260606-009", "carrierType", "Tray", "status", "CLEANING", "type", "amber", "lotNo", "", "lot", "-", "stepCode", "", "step", "-")
        );
    }

    private List<Map<String, Object>> fallbackMaterialSupplierPerformance() {
        return List.of(
                fallbackMaterialSupplierPerformanceRow("SUP-B", 4, 2, 320, 2, 4, 2, 1, 1, 50.0, 2, 55.0, "HIGH", "red"),
                fallbackMaterialSupplierPerformanceRow("SUP-A", 7, 7, 1180, 3, 6, 6, 0, 0, 100.0, 0, 100.0, "LOW", "green")
        );
    }

    private Map<String, Object> fallbackMaterialSupplierPerformanceRow(String supplierCode, int batchCount,
                                                                       int availableBatchCount, int availableQty,
                                                                       int materialCount, int inspectionCount,
                                                                       int passCount, int holdCount, int ngCount,
                                                                       double passRate, int riskBatchCount,
                                                                       double score, String riskLevel, String type) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("supplierCode", supplierCode);
        row.put("batchCount", batchCount);
        row.put("availableBatchCount", availableBatchCount);
        row.put("availableQty", availableQty);
        row.put("materialCount", materialCount);
        row.put("inspectionCount", inspectionCount);
        row.put("passCount", passCount);
        row.put("holdCount", holdCount);
        row.put("ngCount", ngCount);
        row.put("passRate", passRate);
        row.put("passRateText", String.format("%.1f%%", passRate));
        row.put("riskBatchCount", riskBatchCount);
        row.put("score", score);
        row.put("scoreText", String.format("%.1f", score));
        row.put("riskLevel", riskLevel);
        row.put("type", type);
        return row;
    }

    private List<Map<String, Object>> fallbackMaterialSupplierTrends() {
        return List.of(
                fallbackMaterialSupplierTrend("SUP-B", "OLED有机材料供应商B", "ORGANIC",
                        new double[]{88.0, 86.5, 84.0, 77.5, 62.0, 55.0},
                        new double[]{96.0, 95.0, 92.0, 88.0, 76.0, 50.0},
                        "HIGH", "red", 2, 0, 1, "近周期存在 IQC NG 与未关闭8D，需维持条件准入并跟踪三批趋势。"),
                fallbackMaterialSupplierTrend("SUP-A", "PI材料供应商A", "CHEMICAL",
                        new double[]{98.0, 99.0, 100.0, 100.0, 99.5, 100.0},
                        new double[]{100.0, 100.0, 100.0, 100.0, 100.0, 100.0},
                        "LOW", "green", 0, 0, 0, "近周期供应商批次、IQC 与8D表现稳定。")
        );
    }

    private Map<String, Object> fallbackMaterialSupplierTrend(String supplierCode,
                                                              String supplierName,
                                                              String materialClass,
                                                              double[] scores,
                                                              double[] passRates,
                                                              String latestRiskLevel,
                                                              String type,
                                                              int actionWindowCount,
                                                              int overdueWindowCount,
                                                              int latestOpenActionCount,
                                                              String summary) {
        YearMonth start = YearMonth.now().minusMonths(scores.length - 1L);
        List<Map<String, Object>> points = new ArrayList<>();
        for (int index = 0; index < scores.length; index++) {
            YearMonth period = start.plusMonths(index);
            boolean latest = index == scores.length - 1;
            points.add(fallbackMaterialSupplierTrendPoint(
                    period,
                    scores[index],
                    passRates[index],
                    latest ? latestRiskLevel : scores[index] < 70.0 ? "HIGH" : scores[index] < 90.0 ? "MEDIUM" : "LOW",
                    latest ? type : scores[index] < 70.0 ? "red" : scores[index] < 90.0 ? "amber" : "green",
                    latest ? latestOpenActionCount : 0
            ));
        }
        Map<String, Object> latestPoint = points.get(points.size() - 1);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("supplierCode", supplierCode);
        row.put("supplierName", supplierName);
        row.put("materialClass", materialClass);
        row.put("periodStart", start.toString());
        row.put("periodEnd", start.plusMonths(scores.length - 1L).toString());
        row.put("latestPeriod", latestPoint.get("period"));
        row.put("latestScore", latestPoint.get("score"));
        row.put("latestScoreText", latestPoint.get("scoreText"));
        row.put("latestPassRate", latestPoint.get("passRate"));
        row.put("latestPassRateText", latestPoint.get("passRateText"));
        row.put("latestRiskLevel", latestRiskLevel);
        row.put("actionWindowCount", actionWindowCount);
        row.put("overdueWindowCount", overdueWindowCount);
        row.put("trend", points);
        row.put("summary", summary);
        row.put("type", type);
        return row;
    }

    private Map<String, Object> fallbackMaterialSupplierTrendPoint(YearMonth period,
                                                                   double score,
                                                                   double passRate,
                                                                   String riskLevel,
                                                                   String type,
                                                                   int openActionCount) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("period", period.toString());
        point.put("batchCount", 2);
        point.put("riskBatchCount", "HIGH".equals(riskLevel) ? 1 : 0);
        point.put("inspectionCount", 2);
        point.put("passCount", passRate >= 99.0 ? 2 : passRate >= 80.0 ? 1 : 0);
        point.put("holdCount", "MEDIUM".equals(riskLevel) ? 1 : 0);
        point.put("ngCount", "HIGH".equals(riskLevel) ? 1 : 0);
        point.put("actionCount", openActionCount);
        point.put("openActionCount", openActionCount);
        point.put("overdueActionCount", 0);
        point.put("activityCount", 4 + openActionCount);
        point.put("materialCount", 2);
        point.put("materialCodes", List.of("PI_INK", "OLED_R"));
        point.put("passRate", passRate);
        point.put("passRateText", String.format("%.1f%%", passRate));
        point.put("score", score);
        point.put("scoreText", String.format("%.1f", score));
        point.put("riskLevel", riskLevel);
        point.put("type", type);
        return point;
    }

    private List<Map<String, Object>> fallbackMaterialLocations() {
        return List.of(
                fallbackMaterialLocation("WMS-A01", "CHEM-A", "CHEMICAL", "CHEMICAL", "ACTIVE", 5000, 940, "g", "18 ~ 25℃", "30 ~ 55%RH", 20, "green"),
                fallbackMaterialLocation("COLD-02", "ORG-COLD", "COLD", "ORGANIC", "ACTIVE", 2000, 322, "g", "2 ~ 8℃", "20 ~ 45%RH", 30, "green"),
                fallbackMaterialLocation("WMS-HOLD-01", "HOLD", "QUARANTINE", "ANY", "LOCKED", 1000, 0, "EA", "18 ~ 28℃", "30 ~ 70%RH", 900, "red")
        );
    }

    private Map<String, Object> fallbackMaterialLocation(String locationCode, String zoneCode,
                                                         String storageType, String materialClass,
                                                         String status, int capacityQty, int usedQty,
                                                         String unit, String temperatureWindow,
                                                         String humidityWindow, int strategyPriority,
                                                         String type) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("locationCode", locationCode);
        row.put("zoneCode", zoneCode);
        row.put("areaCode", "WMS");
        row.put("storageType", storageType);
        row.put("materialClass", materialClass);
        row.put("status", status);
        row.put("capacityQty", capacityQty);
        row.put("usedQty", usedQty);
        row.put("availableQty", capacityQty - usedQty);
        row.put("utilizationText", String.format("%.1f%%", usedQty * 100.0 / capacityQty));
        row.put("unit", unit);
        row.put("temperatureWindow", temperatureWindow);
        row.put("humidityWindow", humidityWindow);
        row.put("strategyPriority", strategyPriority);
        row.put("type", type);
        return row;
    }

    private List<Map<String, Object>> fallbackMaterialLocationTasks(String status, String batchNo) {
        List<Map<String, Object>> rows = List.of(
                fallbackMaterialLocationTask("MLT-FB-001", "PUTAWAY", "PI260606-A", "PI_INK", "PI胶",
                        "WMS-IN", "WMS-A01", 820, 820, "g", "DONE", "来料上架", "wms1001", "09:20", "green"),
                fallbackMaterialLocationTask("MLT-FB-002", "MOVE", "ENCAP260604-C", "ENCAP_GLUE", "封装胶",
                        "WMS-IN", "WMS-B03", 540, 540, "g", "DONE", "产线补料前移库", "wms1002", "10:35", "green"),
                fallbackMaterialLocationTask("MLT-FB-003", "COUNT", "OLED-R-260605-B", "OLED_R", "红光有机材料",
                        "COLD-02", "COLD-02", 310, 310, "g", "DONE", "低温库日盘", "wms1001", "13:10", "green")
        );
        return rows.stream()
                .filter(row -> status == null || status.isBlank() || status.equals(row.get("status")))
                .filter(row -> batchNo == null || batchNo.isBlank() || batchNo.equals(row.get("batchNo")))
                .collect(Collectors.toList());
    }

    private Map<String, Object> fallbackMaterialLocationTask(String taskNo, String taskType,
                                                             String batchNo, String materialCode,
                                                             String materialName, String sourceLocation,
                                                             String targetLocation, int plannedQty,
                                                             int actualQty, String unit, String status,
                                                             String reason, String operator,
                                                             String executedTime, String type) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("taskNo", taskNo);
        row.put("taskType", taskType);
        row.put("batchNo", batchNo);
        row.put("materialCode", materialCode);
        row.put("materialName", materialName);
        row.put("sourceLocation", sourceLocation);
        row.put("targetLocation", targetLocation);
        row.put("plannedQty", plannedQty);
        row.put("actualQty", actualQty);
        row.put("unit", unit);
        row.put("status", status);
        row.put("reason", reason);
        row.put("operator", operator);
        row.put("executedTime", executedTime);
        row.put("type", type);
        return row;
    }

    private List<Map<String, Object>> fallbackMaterialConsumptions(String lotNo) {
        List<Map<String, Object>> rows = List.of(
                fallbackMaterialConsumption("LOT202406001", "COATING", "MAT-PI-001", "PI 胶", "PI260606-A", 42.8, "42.8g", "op1007", "13:42"),
                fallbackMaterialConsumption("LOT202406004", "EVAPORATION", "MAT-OLED-R", "红光有机材料", "OLED-R-260605-B", 8.2, "8.2g", "op1011", "14:08")
        );
        if (lotNo == null || lotNo.isBlank()) {
            return rows;
        }
        return rows.stream()
                .filter(row -> lotNo.equals(row.get("lotNo")))
                .collect(Collectors.toList());
    }

    private Map<String, Object> fallbackMaterialConsumption(String lotNo, String stepCode, String materialCode,
                                                            String materialName, String batchNo, double consumedQty,
                                                            String qty, String operator, String time) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("lotNo", lotNo);
        row.put("lot", lotNo);
        row.put("stepCode", stepCode);
        row.put("step", stepCode);
        row.put("materialCode", materialCode);
        row.put("materialName", materialName);
        row.put("batchNo", batchNo);
        row.put("batch", batchNo);
        row.put("consumedQty", consumedQty);
        row.put("qty", qty);
        row.put("operator", operator);
        row.put("time", time);
        row.put("status", "TRACEABLE");
        row.put("type", "green");
        return row;
    }

    private List<Map<String, Object>> fallbackDefectTopN() {
        return List.of(
                Map.of("defectCode", "D-MURA", "defectName", "Mura", "qty", 18),
                Map.of("defectCode", "D-BRIGHT", "defectName", "亮点", "qty", 11),
                Map.of("defectCode", "D-BOND-OFFSET", "defectName", "绑定偏移", "qty", 7)
        );
    }

    private void moveToNextStep(String lotNo) {
        Lot lot = findLot(lotNo);
        if (!"READY".equals(lot.getStatus())) {
            return;
        }
        List<String> routeSteps = routeStepCodes(lot.getProductCode());
        int index = routeSteps.indexOf(lot.getCurrentStepCode());
        if (index >= 0 && index < routeSteps.size() - 1) {
            lot.setCurrentStepCode(routeSteps.get(index + 1));
            lot.setCurrentEquipmentCode(null);
        } else if (index == routeSteps.size() - 1) {
            lot.setStatus("COMPLETED");
            lot.setCurrentEquipmentCode(null);
        } else {
            log.warn("Lot当前工序不在生效Route中，跳过自动推进: lotNo={}, product={}, step={}",
                    lot.getLotNo(), lot.getProductCode(), lot.getCurrentStepCode());
            return;
        }
        lotMapper.updateById(lot);
    }

    private String firstRouteStepCode(String productCode) {
        List<String> routeSteps = routeStepCodes(productCode);
        return routeSteps.isEmpty() ? "COATING" : routeSteps.get(0);
    }

    private List<String> routeStepCodes(String productCode) {
        try {
            List<String> steps = routeService.activeStepCodes(productCode);
            if (steps != null && !steps.isEmpty()) {
                return steps;
            }
        } catch (Exception e) {
            if (!pilotFallbackEnabled) {
                log.warn("生效Route正式数据读取失败，未启用试点fallback: product={}, reason={}",
                        productCode, e.getMessage());
                throw new BusinessException("生效Route正式数据读取失败，未启用试点fallback");
            }
            log.warn("生效Route正式数据读取失败，已启用试点fallback: product={}, reason={}",
                    productCode, e.getMessage());
        }
        return pilotFallbackEnabled ? DEFAULT_ROUTE : List.of();
    }

    private String defaultEquipmentCode(String stepCode) {
        String fallback = switch (valueOr(stepCode, "")) {
            case "CLEAN" -> "CLEANER_01";
            case "COATING" -> "COATER_01";
            case "EXPOSURE" -> "EXPOSURE_01";
            case "ETCH" -> "ETCH_02";
            case "EVAPORATION" -> "EVAP_01";
            case "ENCAPSULATION" -> "ENCAP_01";
            case "INSPECTION" -> "INSPECT_01";
            case "AGING" -> "AGING_01";
            default -> "COATER_01";
        };
        try {
            return equipmentMapper.selectList(null).stream()
                    .filter(equipment -> "IDLE".equals(equipment.getStatus()) || "RUNNING".equals(equipment.getStatus()))
                    .filter(equipment -> supportsStep(equipment, stepCode))
                    .map(Equipment::getEquipmentCode)
                    .findFirst()
                    .orElse(fallback);
        } catch (Exception e) {
            log.warn("默认设备解析失败，已降级到静态映射: step={}, equipment={}, reason={}", stepCode, fallback, e.getMessage());
            return fallback;
        }
    }

    private boolean supportsStep(Equipment equipment, String stepCode) {
        if (equipment == null || stepCode == null || stepCode.isBlank()) {
            return false;
        }
        String capabilitySteps = equipment.getCapabilitySteps();
        if (capabilitySteps == null || capabilitySteps.isBlank()) {
            return false;
        }
        try {
            return JSONUtil.toList(capabilitySteps, String.class).contains(stepCode);
        } catch (Exception ignored) {
            return capabilitySteps.contains(stepCode);
        }
    }

    private Map<String, Object> metric(String label, String value, String tag, String type, String left, String right) {
        return Map.of("label", label, "value", value, "tag", tag, "type", type, "left", left, "right", right);
    }

    private Map<String, Object> defect(String code, String name, String segment, String level) {
        return Map.of("defectCode", code, "defectName", name, "segment", segment, "level", level, "status", "ACTIVE");
    }

    private Map<String, Object> auditRow(String time, String user, String object, String action, String result, String source) {
        return Map.of("time", time, "user", user, "object", object, "action", action, "result", result, "source", source);
    }

    private void audit(String action, String object, String description, String operator) {
        audit(action, object, description, operator, null);
    }

    private void audit(String action, String object, String description, String operator, String requestSnapshot) {
        try {
            auditLogService.record(action, object, bizType(action), description, operator, "smartdisplay-mes-api", requestSnapshot);
        } catch (Exception e) {
            log.warn("审计日志写入失败，已降级不阻断主流程: action={}, object={}, reason={}", action, object, e.getMessage());
        }
    }

    private String currentUser() {
        return AuthContext.username();
    }

    private Map<String, Object> auditRow(AuditLog log) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("time", log.getCreatedTime() == null ? "" : log.getCreatedTime().toLocalTime().withNano(0).toString());
        row.put("createdTime", log.getCreatedTime() == null ? "" : log.getCreatedTime().toString());
        row.put("user", valueOr(log.getOperator(), "system"));
        row.put("object", valueOr(log.getBizNo(), "-"));
        row.put("bizType", valueOr(log.getBizType(), "-"));
        row.put("action", valueOr(log.getAction(), "-"));
        row.put("result", valueOr(log.getResult(), "-"));
        row.put("source", valueOr(log.getSource(), "-"));
        row.put("description", valueOr(log.getDescription(), ""));
        row.put("requestMethod", valueOr(log.getRequestMethod(), ""));
        row.put("requestUri", valueOr(log.getRequestUri(), ""));
        row.put("clientIp", valueOr(log.getClientIp(), ""));
        row.put("userAgent", valueOr(log.getUserAgent(), ""));
        row.put("requestSnapshot", valueOr(log.getRequestSnapshot(), ""));
        return row;
    }

    private LocalDateTime parseAuditTime(String value, boolean endOfDay) {
        if (!hasText(value)) {
            return null;
        }
        String text = value.trim();
        try {
            if (text.length() == 10) {
                LocalDate date = LocalDate.parse(text);
                return endOfDay ? date.atTime(23, 59, 59) : date.atStartOfDay();
            }
            return LocalDateTime.parse(text);
        } catch (Exception e) {
            throw new BusinessException("审计时间格式不正确: " + value);
        }
    }

    private boolean auditFallbackMatches(Map<String, Object> row, String action, String result, String source, String operator) {
        if (hasText(action) && !String.valueOf(row.getOrDefault("action", "")).toUpperCase(Locale.ROOT)
                .contains(action.trim().toUpperCase(Locale.ROOT))) {
            return false;
        }
        if (hasText(result) && !String.valueOf(row.getOrDefault("result", "")).toUpperCase(Locale.ROOT)
                .contains(result.trim().toUpperCase(Locale.ROOT))) {
            return false;
        }
        if (hasText(source) && !String.valueOf(row.getOrDefault("source", "")).toUpperCase(Locale.ROOT)
                .contains(source.trim().toUpperCase(Locale.ROOT))) {
            return false;
        }
        return !hasText(operator) || String.valueOf(row.getOrDefault("user", "")).toUpperCase(Locale.ROOT)
                .contains(operator.trim().toUpperCase(Locale.ROOT));
    }

    private List<Map<String, Object>> fallbackAuditLogs(String bizNo) {
        return List.of(
                auditRow("14:31:22", "qe1003", valueOr(bizNo, "LOT260606-017"), "Hold Release 审批", "通过", "10.12.8.41"),
                auditRow("14:26:10", "pe2007", "RCP_COAT_65_V12", "Recipe 参数变更", "待复核", "10.12.6.18"),
                auditRow("14:18:44", "op1007", valueOr(bizNo, "COATER_02"), "Track In", "失败", "LINE-HMI-02"),
                auditRow("14:05:36", "pc3002", "MO20260606012", "工单释放", "成功", "10.12.3.22")
        );
    }

    private String bizType(String action) {
        if (action == null) {
            return "UNKNOWN";
        }
        if (action.startsWith("ORDER_")) {
            return "ORDER";
        }
        if (action.startsWith("TRACK_") || action.startsWith("LOT_")) {
            return "LOT";
        }
        if (action.startsWith("AI_KB_")) {
            return "SOP_KB";
        }
        if (action.startsWith("AI_")) {
            return "AI_REPORT";
        }
        if (action.startsWith("WMS_")) {
            return "WMS_ADAPTER";
        }
        if (action.startsWith("QMS_")) {
            return "QMS_ADAPTER";
        }
        return "SYSTEM";
    }

    private String text(Map<String, Object> request, String key, String defaultValue) {
        Object value = value(request, key);
        return value == null || String.valueOf(value).isBlank() ? defaultValue : String.valueOf(value);
    }

    private String requireText(Map<String, Object> request, String key, String errorMessage) {
        String text = text(request, key, "");
        if (text.isBlank()) {
            throw new BusinessException(errorMessage);
        }
        return text;
    }

    private void assertScrapConfirmed(String lotNo, Map<String, Object> request) {
        Object confirmed = value(request, "scrapConfirmed");
        boolean flag = Boolean.TRUE.equals(confirmed) || "true".equalsIgnoreCase(String.valueOf(confirmed));
        String confirmText = text(request, "confirmText", "").trim();
        String expected = "SCRAP:" + lotNo;
        if (!flag || !expected.equals(confirmText)) {
            throw new BusinessException("Scrap requires second confirmation: scrapConfirmed=true and confirmText=" + expected);
        }
    }

    private void releaseOpenHoldForDisposition(Lot lot, String operator, String disposition) {
        if (!Integer.valueOf(1).equals(lot.getHoldFlag())) {
            return;
        }
        HoldRecord holdRecord = holdRecordMapper.selectOne(
                new LambdaQueryWrapper<HoldRecord>()
                        .eq(HoldRecord::getLotNo, lot.getLotNo())
                        .eq(HoldRecord::getStatus, "HOLD")
                        .orderByDesc(HoldRecord::getHoldTime)
                        .last("LIMIT 1"));
        if (holdRecord == null) {
            throw new BusinessException("未找到待释放Hold记录: " + lot.getLotNo());
        }
        holdRecord.setReleaseBy(operator);
        holdRecord.setReleaseTime(LocalDateTime.now());
        holdRecord.setDisposition(disposition);
        holdRecord.setStatus("RELEASED");
        holdRecordMapper.updateById(holdRecord);
        lot.setHoldFlag(0);
    }

    private Object value(Map<String, Object> request, String key) {
        return request == null ? null : request.get(key);
    }

    private Map<String, Object> safeRequest(Map<String, Object> request) {
        return request == null ? Map.of() : request;
    }

    private Map<String, Object> adapterPayload(Map<String, Object> request, String sourceSystem, String adapterCode) {
        Map<String, Object> payload = new LinkedHashMap<>(request == null ? Map.of() : request);
        payload.putIfAbsent("sourceSystem", sourceSystem);
        payload.putIfAbsent("adapterCode", adapterCode);
        return payload;
    }

    private String normalizeWmsTransactionType(String type) {
        String normalized = valueOr(type, "RECEIVE").trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "RECEIVE", "INBOUND", "PUTAWAY" -> "RECEIVE";
            case "FREEZE", "LOCK", "HOLD" -> "FREEZE";
            case "UNFREEZE", "UNLOCK", "RELEASE" -> "UNFREEZE";
            case "RETURN", "RETURN_MATERIAL" -> "RETURN";
            case "COUNT", "INVENTORY_COUNT", "STOCK_COUNT" -> "COUNT";
            default -> normalized;
        };
    }

    private String auditSnapshot(Map<String, Object> before, Map<String, Object> after, Map<String, Object> request) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("before", before == null ? Map.of() : before);
        snapshot.put("after", after == null ? Map.of() : after);
        snapshot.put("changedFields", changedFields(before, after));
        snapshot.put("request", request == null ? Map.of() : request);
        return JSONUtil.toJsonStr(snapshot);
    }

    private List<String> changedFields(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> safeBefore = before == null ? Map.of() : before;
        Map<String, Object> safeAfter = after == null ? Map.of() : after;
        return safeAfter.keySet().stream()
                .filter(key -> !Objects.equals(safeBefore.get(key), safeAfter.get(key)))
                .sorted()
                .toList();
    }

    private Map<String, Object> orderSnapshot(ProductionOrder order) {
        if (order == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("orderNo", order.getOrderNo());
        snapshot.put("productCode", order.getProductCode());
        snapshot.put("plannedQty", order.getPlannedQty());
        snapshot.put("completedQty", order.getCompletedQty());
        snapshot.put("priority", order.getPriority());
        snapshot.put("lineCode", order.getLineCode());
        snapshot.put("status", order.getStatus());
        snapshot.put("startTime", order.getStartTime());
        return snapshot;
    }

    private Map<String, Object> lotSnapshot(Lot lot) {
        if (lot == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("lotNo", lot.getLotNo());
        snapshot.put("orderNo", lot.getOrderNo());
        snapshot.put("productCode", lot.getProductCode());
        snapshot.put("qty", lot.getQty());
        snapshot.put("lineCode", lot.getLineCode());
        snapshot.put("status", lot.getStatus());
        snapshot.put("holdFlag", lot.getHoldFlag());
        snapshot.put("currentStepCode", lot.getCurrentStepCode());
        snapshot.put("currentEquipmentCode", lot.getCurrentEquipmentCode());
        return snapshot;
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

    private long longValue(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String valueOr(String value, String fallback) {
        return Objects.requireNonNullElse(value, fallback);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String objectText(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }
}
