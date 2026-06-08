package com.visionox.mes.pilot.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.visionox.mes.ai.service.AiKbIndexService;
import com.visionox.mes.ai.service.AiKnowledgeService;
import com.visionox.mes.ai.service.AiModelConfigService;
import com.visionox.mes.ai.service.AiRecordService;
import com.visionox.mes.auth.mapper.UserMapper;
import com.visionox.mes.auth.security.RolePermissionService;
import com.visionox.mes.common.BusinessException;
import com.visionox.mes.equipment.adapter.EapAdapter;
import com.visionox.mes.equipment.service.EapGatewayService;
import com.visionox.mes.equipment.service.EquipmentService;
import com.visionox.mes.lot.entity.HoldRecord;
import com.visionox.mes.lot.entity.Lot;
import com.visionox.mes.lot.entity.LotStepRecord;
import com.visionox.mes.lot.mapper.EquipmentMapper;
import com.visionox.mes.lot.mapper.HoldRecordMapper;
import com.visionox.mes.lot.mapper.LotMapper;
import com.visionox.mes.lot.mapper.LotStepRecordMapper;
import com.visionox.mes.lot.mapper.ProcessStepMapper;
import com.visionox.mes.lot.service.HoldService;
import com.visionox.mes.lot.service.TrackInService;
import com.visionox.mes.material.entity.MaterialBatch;
import com.visionox.mes.material.service.MaterialService;
import com.visionox.mes.order.entity.ProductionOrder;
import com.visionox.mes.order.mapper.ProductionOrderMapper;
import com.visionox.mes.order.service.ErpOrderAdapterService;
import com.visionox.mes.quality.service.QualityService;
import com.visionox.mes.recipe.mapper.RecipeMapper;
import com.visionox.mes.recipe.mapper.RecipeParamMapper;
import com.visionox.mes.route.entity.Route;
import com.visionox.mes.route.entity.RouteStep;
import com.visionox.mes.route.service.RouteService;
import com.visionox.mes.system.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PilotMesServiceTest {

    @Mock
    private ProductionOrderMapper orderMapper;

    @Mock
    private LotMapper lotMapper;

    @Mock
    private ProcessStepMapper processStepMapper;

    @Mock
    private EquipmentMapper equipmentMapper;

    @Mock
    private RecipeMapper recipeMapper;

    @Mock
    private RecipeParamMapper recipeParamMapper;

    @Mock
    private LotStepRecordMapper stepRecordMapper;

    @Mock
    private HoldRecordMapper holdRecordMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private TrackInService trackInService;

    @Mock
    private HoldService holdService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private RouteService routeService;

    @Mock
    private QualityService qualityService;

    @Mock
    private MaterialService materialService;

    @Mock
    private ErpOrderAdapterService erpOrderAdapterService;

    @Mock
    private AiKbIndexService aiKbIndexService;

    @Mock
    private AiKnowledgeService aiKnowledgeService;

    @Mock
    private AiModelConfigService aiModelConfigService;

    @Mock
    private AiRecordService aiRecordService;

    @Mock
    private RolePermissionService rolePermissionService;

    @Mock
    private EquipmentService equipmentService;

    @Mock
    private EapAdapter eapAdapter;

    @Mock
    private EapGatewayService eapGatewayService;

    @InjectMocks
    private PilotMesService pilotMesService;

    @Test
    void createOrderShouldPersistCreatedOrderAndWriteAudit() {
        ProductionOrder order = pilotMesService.createOrder(Map.of(
                "orderNo", "MO20260607001",
                "productCode", "OLED_PANEL",
                "productName", "OLED pilot panel",
                "plannedQty", 250,
                "priority", 2,
                "lineCode", "LINE_01"
        ));

        assertThat(order.getOrderNo()).isEqualTo("MO20260607001");
        assertThat(order.getProductCode()).isEqualTo("OLED_PANEL");
        assertThat(order.getPlannedQty()).isEqualTo(250);
        assertThat(order.getCompletedQty()).isZero();
        assertThat(order.getPriority()).isEqualTo(2);
        assertThat(order.getLineCode()).isEqualTo("LINE_01");
        assertThat(order.getStatus()).isEqualTo("CREATED");
        assertThat(order.getCreatedBy()).isEqualTo("system");
        assertThat(order.getCreatedTime()).isNotNull();
        verify(orderMapper).insert(order);
        ArgumentCaptor<String> createSnapshotCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).record(eq("ORDER_CREATE"), eq("MO20260607001"), eq("ORDER"), any(),
                eq("system"), eq("smartdisplay-mes-api"), createSnapshotCaptor.capture());
        assertThat(createSnapshotCaptor.getValue())
                .contains("\"before\":{}")
                .contains("\"after\"")
                .contains("\"orderNo\":\"MO20260607001\"")
                .contains("\"changedFields\"");
    }

    @Test
    void importErpOrdersShouldDelegateToSimulatedErpAdapter() {
        Map<String, Object> request = Map.of("count", 2, "batchNo", "ERP-BATCH-001");
        Map<String, Object> response = Map.of("batchNo", "ERP-BATCH-001", "createdCount", 2);
        when(erpOrderAdapterService.importOrders(request, "system")).thenReturn(response);

        Map<String, Object> result = pilotMesService.importErpOrders(request);

        assertThat(result).isEqualTo(response);
        verify(erpOrderAdapterService).importOrders(request, "system");
    }

    @Test
    @SuppressWarnings("unchecked")
    void ingestQmsInspectionShouldAttachAdapterIdentityAndDelegateToQualityService() {
        Map<String, Object> response = Map.of("messageType", "INSPECTION_RESULT", "lotNo", "LOT001");
        when(qualityService.reportQmsInspection(any())).thenReturn(response);

        Map<String, Object> result = pilotMesService.ingestQmsInspection(Map.of("lotNo", "LOT001", "result", "NG"));

        assertThat(result).isEqualTo(response);
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(qualityService).reportQmsInspection(payloadCaptor.capture());
        assertThat(payloadCaptor.getValue())
                .containsEntry("lotNo", "LOT001")
                .containsEntry("result", "NG")
                .containsEntry("sourceSystem", "qms-adapter")
                .containsEntry("adapterCode", "simulated-qms-adapter");
    }

    @Test
    void checkWmsMaterialReadinessShouldReturnReadinessAndWriteAdapterAudit() {
        when(materialService.materialReadiness()).thenReturn(Map.of("readiness", "READY", "batches", List.of()));

        Map<String, Object> result = pilotMesService.checkWmsMaterialReadiness(Map.of(
                "lotNo", "LOT001",
                "operator", "wms1001"
        ));

        assertThat(result)
                .containsEntry("readiness", "READY")
                .containsEntry("messageType", "MATERIAL_READINESS")
                .containsEntry("sourceSystem", "wms-adapter")
                .containsEntry("adapterCode", "simulated-wms-adapter")
                .containsEntry("lotNo", "LOT001")
                .containsEntry("lineCode", "LINE_01");
        verify(materialService).materialReadiness();
        verify(auditLogService).record(eq("WMS_MATERIAL_READINESS"), eq("LOT001"), eq("WMS_ADAPTER"),
                any(), eq("wms1001"), eq("smartdisplay-mes-api"), any());
    }

    @Test
    void ingestWmsInventoryTransactionShouldNormalizeTypeRouteToMaterialServiceAndAudit() {
        MaterialBatch batch = new MaterialBatch();
        batch.setBatchNo("PI_INK_B001");
        when(materialService.freezeMaterial(eq("PI_INK_B001"), any())).thenReturn(batch);

        Map<String, Object> result = pilotMesService.ingestWmsInventoryTransaction(Map.of(
                "transactionType", "lock",
                "batchNo", "PI_INK_B001",
                "qty", "10",
                "operator", "wms1001"
        ));

        assertThat(result)
                .containsEntry("messageType", "INVENTORY_TRANSACTION")
                .containsEntry("transactionType", "FREEZE")
                .containsEntry("batchNo", "PI_INK_B001")
                .containsEntry("result", "ACCEPTED");
        assertThat(result.get("batch")).isSameAs(batch);
        verify(materialService).freezeMaterial(eq("PI_INK_B001"), any());
        verify(auditLogService).record(eq("WMS_INVENTORY_TRANSACTION"), eq("PI_INK_B001"), eq("WMS_ADAPTER"),
                any(), eq("wms1001"), eq("smartdisplay-mes-api"), any());
    }

    @Test
    void releaseOrderShouldSplitOrderIntoReadyLotsAndWriteAudit() {
        ProductionOrder order = order("MO20260607001", 250);
        when(orderMapper.selectOne(any())).thenReturn(order);
        when(routeService.activeStepCodes("OLED_PANEL")).thenReturn(List.of("CLEAN", "COATING", "EXPOSURE"));
        when(lotMapper.selectCount(any())).thenReturn(0L);

        Map<String, Object> result = pilotMesService.releaseOrder("MO20260607001", Map.of("lotQty", 100));

        assertThat(result.get("order")).isSameAs(order);
        assertThat(result.get("lotCount")).isEqualTo(3);
        assertThat(order.getStatus()).isEqualTo("RELEASED");
        assertThat(order.getStartTime()).isNotNull();
        verify(orderMapper).updateById(order);

        ArgumentCaptor<Lot> lotCaptor = ArgumentCaptor.forClass(Lot.class);
        verify(lotMapper, times(3)).insert(lotCaptor.capture());
        List<Lot> lots = lotCaptor.getAllValues();
        assertThat(lots).extracting(Lot::getLotNo)
                .containsExactly("LOT20260607001-001", "LOT20260607001-002", "LOT20260607001-003");
        assertThat(lots).extracting(Lot::getQty).containsExactly(100, 100, 50);
        assertThat(lots).allSatisfy(lot -> {
            assertThat(lot.getOrderNo()).isEqualTo("MO20260607001");
            assertThat(lot.getProductCode()).isEqualTo("OLED_PANEL");
            assertThat(lot.getLineCode()).isEqualTo("LINE_01");
            assertThat(lot.getCurrentStepCode()).isEqualTo("CLEAN");
            assertThat(lot.getCurrentEquipmentCode()).isNull();
            assertThat(lot.getStatus()).isEqualTo("READY");
            assertThat(lot.getHoldFlag()).isZero();
            assertThat(lot.getPriority()).isEqualTo(2);
            assertThat(lot.getCreatedBy()).isEqualTo("system");
            assertThat(lot.getCreatedTime()).isNotNull();
        });

        @SuppressWarnings("unchecked")
        List<Lot> returnedLots = (List<Lot>) result.get("createdLots");
        assertThat(returnedLots).hasSize(3);
        ArgumentCaptor<String> releaseSnapshotCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).record(eq("ORDER_RELEASE"), eq("MO20260607001"), eq("ORDER"), any(),
                eq("system"), eq("smartdisplay-mes-api"), releaseSnapshotCaptor.capture());
        assertThat(releaseSnapshotCaptor.getValue())
                .contains("\"before\"")
                .contains("\"after\"")
                .contains("\"status\":\"CREATED\"")
                .contains("\"status\":\"RELEASED\"")
                .contains("\"createdLotCount\":3")
                .contains("\"changedFields\"");
    }

    @Test
    void pageLotsShouldApplyLineDataScope() {
        when(rolePermissionService.dataScopeCondition(any(), any(), any(), any(), any(), any()))
                .thenReturn(new RolePermissionService.DataScopeCondition("LINE", "line_code = {0}", List.of("LINE_01")));
        when(lotMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 20));

        pilotMesService.pageLots(1, 20, "LOT", "READY");

        verify(rolePermissionService).dataScopeCondition(any(), any(), eq(""), eq("line_code"), isNull(), isNull());
        verify(lotMapper).selectPage(any(), any());
    }

    @Test
    void traceLotShouldRejectLotOutsideDataScope() {
        when(rolePermissionService.dataScopeCondition(any(), any(), any(), any(), any(), any()))
                .thenReturn(new RolePermissionService.DataScopeCondition("LINE", "1 = 0", List.of()));
        when(lotMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> pilotMesService.traceLot("LOT_OUT_OF_SCOPE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权限");

        verify(stepRecordMapper, never()).selectList(any());
        verify(holdRecordMapper, never()).selectList(any());
    }

    @Test
    void qualityExceptionsShouldNotFallbackWhenFormalQueryReturnsEmpty() {
        when(qualityService.exceptionRows(null)).thenReturn(List.of());

        assertThat(pilotMesService.qualityExceptions(null)).isEmpty();
    }

    @Test
    void materialConsumptionsShouldNotFallbackWhenFormalQueryReturnsEmpty() {
        when(materialService.materialConsumptions(null)).thenReturn(List.of());

        assertThat(pilotMesService.materialConsumptions(null)).isEmpty();
    }

    @Test
    void traceSearchShouldResolveEquipmentToMatchedLotAndTraceEnvelope() {
        Lot lot = lot("LOT001", "PROCESSING");
        LotStepRecord stepRecord = stepRecord("LOT001", "COATER_01");
        when(stepRecordMapper.selectList(any())).thenReturn(List.of(stepRecord));
        when(lotMapper.selectList(any())).thenReturn(List.of(lot));
        when(lotMapper.selectOne(any())).thenReturn(lot);
        when(orderMapper.selectOne(any())).thenReturn(order("MO20260607001", 100));
        when(holdRecordMapper.selectList(any())).thenReturn(List.of());
        when(qualityService.inspectionRows("LOT001")).thenReturn(List.of(Map.of(
                "lotNo", "LOT001",
                "itemCode", "THICKNESS",
                "result", "OK"
        )));
        when(qualityService.exceptionRows("LOT001")).thenReturn(List.of());
        when(materialService.materialConsumptions("LOT001")).thenReturn(List.of(Map.of(
                "lotNo", "LOT001",
                "batchNo", "PI260606-A",
                "materialCode", "MAT-PI-001"
        )));

        Map<String, Object> result = pilotMesService.traceSearch("EQUIPMENT", "COATER_01");

        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) result.get("query");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
        @SuppressWarnings("unchecked")
        Map<String, Object> trace = (Map<String, Object>) result.get("trace");
        @SuppressWarnings("unchecked")
        Map<String, Object> impactSummary = (Map<String, Object>) result.get("impactSummary");

        assertThat(query.get("resolvedType")).isEqualTo("EQUIPMENT");
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0)).containsEntry("lotNo", "LOT001");
        assertThat(trace.get("lot")).isSameAs(lot);
        assertThat(impactSummary.get("matchedLotCount")).isEqualTo(1);
        assertThat(impactSummary.get("equipmentCount")).isEqualTo(1);
    }

    @Test
    void traceSearchShouldResolveSnToLotTrace() {
        Lot lot = lot("LOT001", "READY");
        LotStepRecord stepRecord = stepRecord("LOT001", "COATER_01");
        when(lotMapper.selectOne(any())).thenReturn(lot);
        when(stepRecordMapper.selectList(any())).thenReturn(List.of(stepRecord));
        when(holdRecordMapper.selectList(any())).thenReturn(List.of());
        when(orderMapper.selectOne(any())).thenReturn(order("MO20260607001", 100));
        when(qualityService.inspectionRows("LOT001")).thenReturn(List.of());
        when(qualityService.exceptionRows("LOT001")).thenReturn(List.of());
        when(materialService.materialConsumptions("LOT001")).thenReturn(List.of());

        Map<String, Object> result = pilotMesService.traceSearch("AUTO", "LOT001-SN001");

        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) result.get("query");
        @SuppressWarnings("unchecked")
        Map<String, Object> trace = (Map<String, Object>) result.get("trace");
        @SuppressWarnings("unchecked")
        Map<String, Object> sn = (Map<String, Object>) trace.get("sn");

        assertThat(query.get("resolvedType")).isEqualTo("SN");
        assertThat(query.get("selectedLotNo")).isEqualTo("LOT001");
        assertThat(sn).containsEntry("sn", "LOT001-SN001")
                .containsEntry("lotNo", "LOT001");
    }

    @Test
    void dashboardYieldShouldNotFallbackDefectTopNWhenFormalQueryReturnsEmpty() {
        when(qualityService.defectTopN(5)).thenReturn(List.of());

        Map<String, Object> result = pilotMesService.dashboardYield();

        assertThat(result.get("defectTopN")).isEqualTo(List.of());
    }

    @Test
    void trackOutShouldMoveLotToNextActiveRouteStep() {
        Lot lot = lot("LOT001", "READY");
        lot.setCurrentStepCode("CLEAN");
        when(trackInService.trackOut(any())).thenReturn("OK");
        when(lotMapper.selectOne(any())).thenReturn(lot);
        when(routeService.activeStepCodes("OLED_PANEL")).thenReturn(List.of("CLEAN", "COATING", "EXPOSURE"));

        Map<String, Object> result = pilotMesService.trackOut("LOT001", Map.of("operator", "op1001"));

        assertThat(result.get("result")).isEqualTo("OK");
        assertThat(lot.getStatus()).isEqualTo("READY");
        assertThat(lot.getCurrentStepCode()).isEqualTo("COATING");
        assertThat(lot.getCurrentEquipmentCode()).isNull();
        verify(lotMapper).updateById(lot);
        ArgumentCaptor<String> trackOutSnapshotCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).record(eq("TRACK_OUT"), eq("LOT001"), eq("LOT"), any(),
                eq("op1001"), eq("smartdisplay-mes-api"), trackOutSnapshotCaptor.capture());
        assertThat(trackOutSnapshotCaptor.getValue())
                .contains("\"before\"")
                .contains("\"after\"")
                .contains("\"currentStepCode\":\"CLEAN\"")
                .contains("\"currentStepCode\":\"COATING\"")
                .contains("\"trackOutResult\":\"OK\"")
                .contains("\"changedFields\"");
    }

    @Test
    void trackOutShouldNotMoveRouteWhenQualityResultIsNg() {
        Lot lot = lot("LOT001", "HOLD");
        lot.setCurrentStepCode("COATING");
        when(trackInService.trackOut(any())).thenReturn("NG");
        when(lotMapper.selectOne(any())).thenReturn(lot);

        Map<String, Object> result = pilotMesService.trackOut("LOT001", Map.of("operator", "op1001"));

        assertThat(result.get("result")).isEqualTo("NG");
        assertThat(lot.getStatus()).isEqualTo("HOLD");
        assertThat(lot.getCurrentStepCode()).isEqualTo("COATING");
        verify(routeService, never()).activeStepCodes(any());
        verify(lotMapper, never()).updateById(any());
        verify(auditLogService).record(eq("TRACK_OUT"), eq("LOT001"), eq("LOT"), any(), eq("op1001"), eq("smartdisplay-mes-api"), any());
    }

    @Test
    void trackOutShouldCompleteLotWhenCurrentStepIsLastRouteStep() {
        Lot lot = lot("LOT001", "READY");
        lot.setCurrentStepCode("AGING");
        when(trackInService.trackOut(any())).thenReturn("OK");
        when(lotMapper.selectOne(any())).thenReturn(lot);
        when(routeService.activeStepCodes("OLED_PANEL")).thenReturn(List.of("CLEAN", "COATING", "AGING"));

        pilotMesService.trackOut("LOT001", Map.of("operator", "op1001"));

        assertThat(lot.getStatus()).isEqualTo("COMPLETED");
        assertThat(lot.getCurrentEquipmentCode()).isNull();
        verify(lotMapper).updateById(lot);
    }

    @Test
    void releaseOrderShouldRejectMissingOrder() {
        when(orderMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> pilotMesService.releaseOrder("MO_MISSING", Map.of("lotQty", 100)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MO_MISSING");

        verify(lotMapper, never()).insert(any());
        verify(orderMapper, never()).updateById(any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void reworkShouldMoveLotToReworkStepAndWriteAudit() {
        Lot lot = lot("LOT001", "HOLD");
        HoldRecord holdRecord = holdRecord("LOT001");
        when(lotMapper.selectOne(any())).thenReturn(lot);
        when(holdRecordMapper.selectOne(any())).thenReturn(holdRecord);
        when(routeService.findActiveRoute("OLED_PANEL")).thenReturn(route("RTE_OLED_V1"));
        when(routeService.activeSteps("OLED_PANEL")).thenReturn(List.of(routeStep("ETCH", 1)));

        pilotMesService.rework("LOT001", Map.of(
                "reworkRouteCode", "RTE_OLED_V1",
                "reworkStepCode", "ETCH",
                "operator", "qe1001"
        ));

        assertThat(lot.getStatus()).isEqualTo("REWORK");
        assertThat(lot.getHoldFlag()).isZero();
        assertThat(lot.getCurrentStepCode()).isEqualTo("ETCH");
        assertThat(lot.getCurrentEquipmentCode()).isNull();
        assertThat(holdRecord.getStatus()).isEqualTo("RELEASED");
        assertThat(holdRecord.getReleaseBy()).isEqualTo("qe1001");
        assertThat(holdRecord.getReleaseTime()).isNotNull();
        assertThat(holdRecord.getDisposition()).contains("RTE_OLED_V1", "ETCH");
        verify(holdRecordMapper).updateById(holdRecord);
        verify(lotMapper).updateById(lot);
        ArgumentCaptor<String> reworkSnapshotCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).record(eq("LOT_REWORK"), eq("LOT001"), eq("LOT"), any(), eq("qe1001"),
                eq("smartdisplay-mes-api"), reworkSnapshotCaptor.capture());
        assertThat(reworkSnapshotCaptor.getValue())
                .contains("\"reworkRouteCode\":\"RTE_OLED_V1\"")
                .contains("\"reworkStepCode\":\"ETCH\"")
                .contains("\"currentStepCode\":\"ETCH\"");
    }

    @Test
    void reworkShouldRejectStepThatDoesNotAllowRework() {
        Lot lot = lot("LOT001", "HOLD");
        when(lotMapper.selectOne(any())).thenReturn(lot);
        when(routeService.findActiveRoute("OLED_PANEL")).thenReturn(route("RTE_OLED_V1"));
        when(routeService.activeSteps("OLED_PANEL")).thenReturn(List.of(routeStep("ETCH", 0)));

        assertThatThrownBy(() -> pilotMesService.rework("LOT001", Map.of(
                "reworkRouteCode", "RTE_OLED_V1",
                "reworkStepCode", "ETCH",
                "operator", "qe1001"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not allow rework");

        verify(lotMapper, never()).updateById(any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void scrapShouldMoveLotToScrapAndWriteAudit() {
        Lot lot = lot("LOT001", "HOLD");
        HoldRecord holdRecord = holdRecord("LOT001");
        when(lotMapper.selectOne(any())).thenReturn(lot);
        when(holdRecordMapper.selectOne(any())).thenReturn(holdRecord);

        pilotMesService.scrap("LOT001", Map.of(
                "scrapConfirmed", true,
                "confirmText", "SCRAP:LOT001",
                "reason", "MRB scrap",
                "responsibilityModule", "QUALITY",
                "approver", "mrb_lead",
                "operator", "qe1001"
        ));

        assertThat(lot.getStatus()).isEqualTo("SCRAP");
        assertThat(lot.getHoldFlag()).isZero();
        assertThat(lot.getCurrentEquipmentCode()).isNull();
        assertThat(holdRecord.getStatus()).isEqualTo("RELEASED");
        assertThat(holdRecord.getReleaseBy()).isEqualTo("qe1001");
        assertThat(holdRecord.getReleaseTime()).isNotNull();
        assertThat(holdRecord.getDisposition()).isEqualTo("MRB scrap");
        verify(holdRecordMapper).updateById(holdRecord);
        verify(lotMapper).updateById(lot);
        ArgumentCaptor<String> scrapSnapshotCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).record(eq("LOT_SCRAP"), eq("LOT001"), eq("LOT"), any(), eq("qe1001"),
                eq("smartdisplay-mes-api"), scrapSnapshotCaptor.capture());
        assertThat(scrapSnapshotCaptor.getValue())
                .contains("\"scrapConfirmed\":true")
                .contains("\"confirmText\":\"SCRAP:LOT001\"")
                .contains("\"responsibilityModule\":\"QUALITY\"")
                .contains("\"approver\":\"mrb_lead\"");
    }

    @Test
    void scrapShouldRequireSecondConfirmation() {
        Lot lot = lot("LOT001", "HOLD");
        when(lotMapper.selectOne(any())).thenReturn(lot);

        assertThatThrownBy(() -> pilotMesService.scrap("LOT001", Map.of(
                "reason", "MRB scrap",
                "responsibilityModule", "QUALITY",
                "approver", "mrb_lead",
                "operator", "qe1001"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("second confirmation");

        verify(lotMapper, never()).updateById(any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    private ProductionOrder order(String orderNo, int plannedQty) {
        ProductionOrder order = new ProductionOrder();
        order.setOrderNo(orderNo);
        order.setProductCode("OLED_PANEL");
        order.setProductName("OLED pilot panel");
        order.setPlannedQty(plannedQty);
        order.setPriority(2);
        order.setLineCode("LINE_01");
        order.setStatus("CREATED");
        return order;
    }

    private Lot lot(String lotNo, String status) {
        Lot lot = new Lot();
        lot.setLotNo(lotNo);
        lot.setOrderNo("MO20260607001");
        lot.setProductCode("OLED_PANEL");
        lot.setCurrentStepCode("COATING");
        lot.setCurrentEquipmentCode("COATER_01");
        lot.setStatus(status);
        lot.setHoldFlag("HOLD".equals(status) ? 1 : 0);
        return lot;
    }

    private Route route(String routeCode) {
        Route route = new Route();
        route.setRouteCode(routeCode);
        route.setProductCode("OLED_PANEL");
        route.setStatus("ACTIVE");
        return route;
    }

    private RouteStep routeStep(String stepCode, int allowRework) {
        RouteStep step = new RouteStep();
        step.setStepCode(stepCode);
        step.setAllowRework(allowRework);
        return step;
    }

    private LotStepRecord stepRecord(String lotNo, String equipmentCode) {
        LotStepRecord record = new LotStepRecord();
        record.setLotNo(lotNo);
        record.setStepCode("COATING");
        record.setEquipmentCode(equipmentCode);
        record.setRecipeCode("RCP_COAT_01");
        record.setResult("OK");
        record.setTrackInTime(LocalDateTime.now().minusMinutes(30));
        record.setTrackOutTime(LocalDateTime.now().minusMinutes(5));
        return record;
    }

    private HoldRecord holdRecord(String lotNo) {
        HoldRecord holdRecord = new HoldRecord();
        holdRecord.setLotNo(lotNo);
        holdRecord.setHoldReason("Quality exception");
        holdRecord.setHoldType("QUALITY");
        holdRecord.setHoldBy("qe1001");
        holdRecord.setHoldTime(LocalDateTime.now().minusHours(1));
        holdRecord.setStatus("HOLD");
        return holdRecord;
    }
}
