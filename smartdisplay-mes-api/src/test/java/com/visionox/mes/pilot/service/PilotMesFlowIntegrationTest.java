package com.visionox.mes.pilot.service;

import com.visionox.mes.ai.service.AiKbIndexService;
import com.visionox.mes.ai.service.AiKnowledgeService;
import com.visionox.mes.ai.service.AiModelConfigService;
import com.visionox.mes.ai.service.AiRecordService;
import com.visionox.mes.auth.mapper.UserMapper;
import com.visionox.mes.auth.security.RolePermissionService;
import com.visionox.mes.equipment.adapter.SimulatedEapAdapter;
import com.visionox.mes.equipment.service.EapGatewayService;
import com.visionox.mes.equipment.service.EquipmentService;
import com.visionox.mes.lot.entity.Equipment;
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
import com.visionox.mes.material.service.MaterialService;
import com.visionox.mes.order.entity.ProductionOrder;
import com.visionox.mes.order.mapper.ProductionOrderMapper;
import com.visionox.mes.order.service.ErpOrderAdapterService;
import com.visionox.mes.quality.entity.ExceptionEvent;
import com.visionox.mes.quality.entity.QualityDefectRecord;
import com.visionox.mes.quality.entity.QualityInspection;
import com.visionox.mes.quality.mapper.ExceptionEventMapper;
import com.visionox.mes.quality.mapper.QualityDefectRecordMapper;
import com.visionox.mes.quality.mapper.QualityInspectionMapper;
import com.visionox.mes.quality.mapper.QualityMrbApprovalTaskMapper;
import com.visionox.mes.quality.mapper.QualityMrbAttachmentMapper;
import com.visionox.mes.quality.mapper.QualityMrbMinutesMapper;
import com.visionox.mes.quality.mapper.QualityMrbRecordMapper;
import com.visionox.mes.quality.service.QualityService;
import com.visionox.mes.recipe.entity.Recipe;
import com.visionox.mes.recipe.entity.RecipeParam;
import com.visionox.mes.recipe.mapper.RecipeMapper;
import com.visionox.mes.recipe.mapper.RecipeParamMapper;
import com.visionox.mes.recipe.service.RecipeService;
import com.visionox.mes.route.service.RouteService;
import com.visionox.mes.system.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PilotMesFlowIntegrationTest {

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
    private RecipeService recipeService;
    @Mock
    private RouteService routeService;
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
    private AuditLogService auditLogService;
    @Mock
    private QualityInspectionMapper inspectionMapper;
    @Mock
    private QualityDefectRecordMapper defectRecordMapper;
    @Mock
    private ExceptionEventMapper exceptionEventMapper;
    @Mock
    private QualityMrbRecordMapper mrbRecordMapper;
    @Mock
    private QualityMrbAttachmentMapper mrbAttachmentMapper;
    @Mock
    private QualityMrbMinutesMapper mrbMinutesMapper;
    @Mock
    private QualityMrbApprovalTaskMapper mrbApprovalTaskMapper;
    @Mock
    private EquipmentService equipmentService;
    @Mock
    private EapGatewayService eapGatewayService;

    private PilotMesService pilotMesService;
    private final AtomicReference<Lot> lotRef = new AtomicReference<>();
    private final List<LotStepRecord> stepRecords = new ArrayList<>();
    private final List<QualityInspection> inspections = new ArrayList<>();
    private final List<QualityDefectRecord> defects = new ArrayList<>();
    private final List<ExceptionEvent> exceptions = new ArrayList<>();
    private final List<HoldRecord> holds = new ArrayList<>();
    private final AtomicLong stepRecordId = new AtomicLong(1000);

    @BeforeEach
    void setUp() {
        QualityService qualityService = new QualityService(
                inspectionMapper,
                defectRecordMapper,
                exceptionEventMapper,
                mrbRecordMapper,
                mrbAttachmentMapper,
                mrbMinutesMapper,
                mrbApprovalTaskMapper,
                recipeMapper,
                recipeParamMapper,
                lotMapper,
                holdRecordMapper,
                auditLogService,
                rolePermissionService
        );
        TrackInService trackInService = new TrackInService(
                lotMapper,
                equipmentMapper,
                stepRecordMapper,
                holdRecordMapper,
                recipeService,
                routeService,
                qualityService,
                materialService
        );
        HoldService holdService = new HoldService(lotMapper, holdRecordMapper);
        pilotMesService = new PilotMesService(
                orderMapper,
                lotMapper,
                processStepMapper,
                equipmentMapper,
                recipeMapper,
                recipeParamMapper,
                stepRecordMapper,
                holdRecordMapper,
                userMapper,
                trackInService,
                holdService,
                auditLogService,
                routeService,
                qualityService,
                materialService,
                erpOrderAdapterService,
                aiKbIndexService,
                aiKnowledgeService,
                aiModelConfigService,
                aiRecordService,
                rolePermissionService,
                equipmentService,
                new SimulatedEapAdapter(equipmentService),
                eapGatewayService
        );

        wireStatefulMappers();
        wireMasterRules();
    }

    @Test
    void orderReleaseTrackInNgHoldReleaseOkTrackOutShouldProduceTraceableClosedLoop() {
        ProductionOrder order = order();
        when(orderMapper.selectOne(any())).thenReturn(order);

        Map<String, Object> releaseResult = pilotMesService.releaseOrder(order.getOrderNo(), Map.of("lotQty", 100));

        @SuppressWarnings("unchecked")
        List<Lot> releasedLots = (List<Lot>) releaseResult.get("createdLots");
        Lot lot = releasedLots.get(0);
        assertThat(lot.getLotNo()).isEqualTo("LOT20260607001-001");
        assertThat(lot.getStatus()).isEqualTo("READY");
        assertThat(lot.getCurrentStepCode()).isEqualTo("COATING");
        assertThat(lot.getLineCode()).isEqualTo("LINE_01");

        pilotMesService.trackIn(lot.getLotNo(), Map.of(
                "stepCode", "COATING",
                "equipmentCode", "COATER_01",
                "operator", "op1001"
        ));
        assertThat(lot.getStatus()).isEqualTo("PROCESSING");
        assertThat(stepRecords).hasSize(1);
        assertThat(stepRecords.get(0).getRecipeCode()).isEqualTo("RCP_COAT_01");

        Map<String, Object> ngTrackOut = pilotMesService.trackOut(lot.getLotNo(), Map.of(
                "operator", "op1001",
                "result", "OK",
                "processParams", "{\"THICKNESS\":2.40}",
                "remark", "膜厚超上限"
        ));
        assertThat(ngTrackOut.get("result")).isEqualTo("NG");
        assertThat(lot.getStatus()).isEqualTo("HOLD");
        assertThat(lot.getHoldFlag()).isEqualTo(1);
        assertThat(lot.getCurrentStepCode()).isEqualTo("COATING");
        assertThat(holds).hasSize(1);
        assertThat(holds.get(0).getStatus()).isEqualTo("HOLD");
        assertThat(exceptions).hasSize(1);
        assertThat(exceptions.get(0).getStatus()).isEqualTo("OPEN");
        assertThat(defects).hasSize(1);

        pilotMesService.release(lot.getLotNo(), Map.of(
                "releaseBy", "qe1001",
                "disposition", "MRB复判允许重新执行 COATING"
        ));
        assertThat(lot.getStatus()).isEqualTo("READY");
        assertThat(lot.getHoldFlag()).isZero();
        assertThat(holds.get(0).getStatus()).isEqualTo("RELEASED");
        assertThat(holds.get(0).getReleaseBy()).isEqualTo("qe1001");

        pilotMesService.trackIn(lot.getLotNo(), Map.of(
                "stepCode", "COATING",
                "equipmentCode", "COATER_01",
                "operator", "op1002"
        ));
        Map<String, Object> okTrackOut = pilotMesService.trackOut(lot.getLotNo(), Map.of(
                "operator", "op1002",
                "result", "OK",
                "processParams", "{\"THICKNESS\":2.00}",
                "remark", "复测通过"
        ));
        assertThat(okTrackOut.get("result")).isEqualTo("OK");
        assertThat(lot.getStatus()).isEqualTo("READY");
        assertThat(lot.getCurrentStepCode()).isEqualTo("EXPOSURE");
        assertThat(lot.getCurrentEquipmentCode()).isNull();
        assertThat(stepRecords).hasSize(2);
        assertThat(inspections).hasSize(2);

        when(materialService.materialConsumptions(lot.getLotNo())).thenReturn(List.of(Map.of(
                "lotNo", lot.getLotNo(),
                "materialCode", "MAT-PI-001",
                "batchNo", "PI260606-A",
                "traceStatus", "TRACEABLE"
        )));
        when(auditLogService.list(eq(lot.getLotNo()), eq(50))).thenReturn(List.of());

        Map<String, Object> trace = pilotMesService.traceLot(lot.getLotNo());

        assertThat(trace.get("lot")).isSameAs(lot);
        assertThat(trace.get("order")).isSameAs(order);
        assertThat((List<?>) trace.get("stepRecords")).hasSize(2);
        assertThat((List<?>) trace.get("holdRecords")).hasSize(1);
        assertThat((List<?>) trace.get("qualityRecords")).hasSize(2);
        assertThat((List<?>) trace.get("exceptionEvents")).hasSize(1);
        assertThat((List<?>) trace.get("materialConsumptions")).hasSize(1);
        verify(materialService, times(2)).validateReadiness(lot, "COATING");
        verify(materialService).lockForTrackIn(eq(lot), eq("COATING"), eq("COATER_01"), eq("op1001"));
        verify(materialService, times(2)).consumeForTrackOut(eq(lot), any());
    }

    private void wireStatefulMappers() {
        when(lotMapper.selectCount(any())).thenReturn(0L);
        when(lotMapper.selectOne(any())).thenAnswer(invocation -> lotRef.get());
        doAnswer(invocation -> {
            Lot lot = invocation.getArgument(0);
            lotRef.set(lot);
            return 1;
        }).when(lotMapper).insert(any(Lot.class));

        doAnswer(invocation -> {
            LotStepRecord record = invocation.getArgument(0);
            record.setId(stepRecordId.incrementAndGet());
            stepRecords.add(record);
            return 1;
        }).when(stepRecordMapper).insert(any(LotStepRecord.class));
        when(stepRecordMapper.selectList(any())).thenAnswer(invocation -> {
            List<LotStepRecord> openRecords = stepRecords.stream()
                    .filter(record -> record.getTrackOutTime() == null)
                    .toList();
            return openRecords.isEmpty() ? new ArrayList<>(stepRecords) : openRecords;
        });

        doAnswer(invocation -> {
            QualityInspection inspection = invocation.getArgument(0);
            inspections.add(inspection);
            return 1;
        }).when(inspectionMapper).insert(any(QualityInspection.class));
        when(inspectionMapper.selectList(any())).thenAnswer(invocation -> new ArrayList<>(inspections));

        doAnswer(invocation -> {
            QualityDefectRecord defect = invocation.getArgument(0);
            defects.add(defect);
            return 1;
        }).when(defectRecordMapper).insert(any(QualityDefectRecord.class));

        doAnswer(invocation -> {
            ExceptionEvent event = invocation.getArgument(0);
            exceptions.add(event);
            return 1;
        }).when(exceptionEventMapper).insert(any(ExceptionEvent.class));
        when(exceptionEventMapper.selectList(any())).thenAnswer(invocation -> new ArrayList<>(exceptions));

        when(holdRecordMapper.selectCount(any())).thenAnswer(invocation -> holds.stream()
                .filter(hold -> "HOLD".equals(hold.getStatus()))
                .count());
        doAnswer(invocation -> {
            HoldRecord hold = invocation.getArgument(0);
            holds.add(hold);
            return 1;
        }).when(holdRecordMapper).insert(any(HoldRecord.class));
        when(holdRecordMapper.selectOne(any())).thenAnswer(invocation -> holds.stream()
                .filter(hold -> "HOLD".equals(hold.getStatus()))
                .findFirst()
                .orElse(null));
        when(holdRecordMapper.selectList(any())).thenAnswer(invocation -> new ArrayList<>(holds));
    }

    private void wireMasterRules() {
        Recipe recipe = recipe();
        RecipeParam thicknessParam = thicknessParam();
        Equipment coater = equipment();

        when(routeService.activeStepCodes("OLED_PANEL")).thenReturn(List.of("COATING", "EXPOSURE"));
        when(equipmentMapper.selectList(any())).thenReturn(List.of(coater));
        when(equipmentMapper.selectOne(any())).thenReturn(coater);
        when(recipeService.findActiveRecipe("OLED_PANEL", "COATING", "COATER_01")).thenReturn(recipe);
        when(recipeMapper.selectOne(any())).thenReturn(recipe);
        when(recipeParamMapper.selectList(any())).thenReturn(List.of(thicknessParam));
        when(routeService.activeRouteSummaries()).thenReturn(List.of(Map.of(
                "routeCode", "RTE-OLED-PILOT",
                "productCode", "OLED_PANEL",
                "steps", List.of("COATING", "EXPOSURE")
        )));
    }

    private ProductionOrder order() {
        ProductionOrder order = new ProductionOrder();
        order.setOrderNo("MO20260607001");
        order.setProductCode("OLED_PANEL");
        order.setProductName("OLED pilot panel");
        order.setPlannedQty(100);
        order.setPriority(1);
        order.setLineCode("LINE_01");
        order.setStatus("CREATED");
        return order;
    }

    private Equipment equipment() {
        Equipment equipment = new Equipment();
        equipment.setEquipmentCode("COATER_01");
        equipment.setStatus("IDLE");
        equipment.setCapabilitySteps("[\"COATING\"]");
        return equipment;
    }

    private Recipe recipe() {
        Recipe recipe = new Recipe();
        recipe.setId(101L);
        recipe.setRecipeCode("RCP_COAT_01");
        recipe.setProductCode("OLED_PANEL");
        recipe.setStepCode("COATING");
        recipe.setEquipmentCode("COATER_01");
        recipe.setStatus("ACTIVE");
        return recipe;
    }

    private RecipeParam thicknessParam() {
        RecipeParam param = new RecipeParam();
        param.setRecipeId(101L);
        param.setParamCode("THICKNESS");
        param.setParamName("涂胶厚度");
        param.setUpperLimit(new BigDecimal("2.20"));
        param.setLowerLimit(new BigDecimal("1.80"));
        param.setUnit("um");
        param.setParamType("DIMENSION");
        param.setIsKeyParam(1);
        param.setDisplayOrder(1);
        return param;
    }
}
