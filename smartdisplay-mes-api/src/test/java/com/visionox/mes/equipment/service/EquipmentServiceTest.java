package com.visionox.mes.equipment.service;

import com.visionox.mes.equipment.entity.EquipmentCycleSample;
import com.visionox.mes.equipment.entity.EquipmentEvent;
import com.visionox.mes.equipment.entity.EquipmentParameterSample;
import com.visionox.mes.equipment.entity.EquipmentPmTask;
import com.visionox.mes.equipment.entity.EquipmentRecipeCommand;
import com.visionox.mes.equipment.entity.EquipmentStandardCycle;
import com.visionox.mes.equipment.entity.EquipmentStatusHistory;
import com.visionox.mes.equipment.mapper.EquipmentCycleSampleMapper;
import com.visionox.mes.equipment.mapper.EquipmentEventMapper;
import com.visionox.mes.equipment.mapper.EquipmentParameterSampleMapper;
import com.visionox.mes.equipment.mapper.EquipmentPmTaskMapper;
import com.visionox.mes.equipment.mapper.EquipmentRecipeCommandMapper;
import com.visionox.mes.equipment.mapper.EquipmentStandardCycleMapper;
import com.visionox.mes.equipment.mapper.EquipmentStatusHistoryMapper;
import com.visionox.mes.lot.entity.Equipment;
import com.visionox.mes.lot.mapper.EquipmentMapper;
import com.visionox.mes.recipe.entity.Recipe;
import com.visionox.mes.recipe.entity.RecipeParam;
import com.visionox.mes.recipe.mapper.RecipeMapper;
import com.visionox.mes.recipe.mapper.RecipeParamMapper;
import com.visionox.mes.system.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentServiceTest {

    @Mock
    private EquipmentMapper equipmentMapper;

    @Mock
    private EquipmentEventMapper eventMapper;

    @Mock
    private EquipmentPmTaskMapper pmTaskMapper;

    @Mock
    private EquipmentParameterSampleMapper parameterSampleMapper;

    @Mock
    private EquipmentRecipeCommandMapper recipeCommandMapper;

    @Mock
    private EquipmentStatusHistoryMapper statusHistoryMapper;

    @Mock
    private EquipmentCycleSampleMapper cycleSampleMapper;

    @Mock
    private EquipmentStandardCycleMapper standardCycleMapper;

    @Mock
    private RecipeMapper recipeMapper;

    @Mock
    private RecipeParamMapper recipeParamMapper;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private EquipmentService equipmentService;

    @Test
    void createEventShouldPersistEventUpdateEquipmentStatusAndAudit() {
        Equipment equipment = equipment("COATER_02", "RUNNING");
        when(equipmentMapper.selectOne(any())).thenReturn(equipment);

        equipmentService.createEvent(Map.of(
                "equipmentCode", "COATER_02",
                "eventType", "ALARM",
                "eventLevel", "P1",
                "title", "Pressure fluctuation",
                "operator", "ee1001"
        ));

        ArgumentCaptor<EquipmentEvent> eventCaptor = ArgumentCaptor.forClass(EquipmentEvent.class);
        verify(eventMapper).insert(eventCaptor.capture());
        EquipmentEvent event = eventCaptor.getValue();
        assertThat(event.getEquipmentCode()).isEqualTo("COATER_02");
        assertThat(event.getEventType()).isEqualTo("ALARM");
        assertThat(event.getEventLevel()).isEqualTo("P1");
        assertThat(event.getStatus()).isEqualTo("OPEN");

        assertThat(equipment.getStatus()).isEqualTo("ALARM");
        verify(equipmentMapper).updateById(equipment);
        verify(auditLogService).record(eq("EQUIPMENT_EVENT"), eq(event.getEventNo()), eq("EQUIPMENT"), any(), eq("ee1001"), eq("equipment-service"), any());
    }

    @Test
    void reportParametersShouldCreateSampleAndAlarmEventWhenValueIsOutOfLimit() {
        Equipment equipment = equipment("COATER_02", "RUNNING");
        when(equipmentMapper.selectOne(any())).thenReturn(equipment);

        equipmentService.reportParameters(Map.of(
                "equipmentCode", "COATER_02",
                "lotNo", "LOT001",
                "stepCode", "COATING",
                "recipeCode", "RCP_COAT_002",
                "paramCode", "THICKNESS",
                "paramValue", "2.26",
                "lowerLimit", "1.8",
                "upperLimit", "2.2",
                "unit", "um",
                "operator", "ee1001"
        ));

        ArgumentCaptor<EquipmentParameterSample> sampleCaptor = ArgumentCaptor.forClass(EquipmentParameterSample.class);
        verify(parameterSampleMapper).insert(sampleCaptor.capture());
        EquipmentParameterSample sample = sampleCaptor.getValue();
        assertThat(sample.getEquipmentCode()).isEqualTo("COATER_02");
        assertThat(sample.getParamCode()).isEqualTo("THICKNESS");
        assertThat(sample.getParamValue()).isEqualByComparingTo("2.26");
        assertThat(sample.getResult()).isEqualTo("NG");

        ArgumentCaptor<EquipmentEvent> eventCaptor = ArgumentCaptor.forClass(EquipmentEvent.class);
        verify(eventMapper).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("PARAMETER");

        assertThat(equipment.getStatus()).isEqualTo("ALARM");
        verify(equipmentMapper).updateById(equipment);
        verify(auditLogService).record(eq("EAP_PARAMETER_REPORT"), eq(sample.getSampleNo()), eq("EQUIPMENT"), any(), eq("ee1001"), eq("equipment-service"), any());
    }

    @Test
    void completePmTaskShouldCloseTaskRestoreEquipmentAndAudit() {
        EquipmentPmTask task = new EquipmentPmTask();
        task.setTaskNo("PM001");
        task.setEquipmentCode("EVAP_01");
        task.setStatus("OPEN");
        task.setPlanEndTime(LocalDateTime.now().plusHours(1));
        Equipment equipment = equipment("EVAP_01", "PM");

        when(pmTaskMapper.selectOne(any())).thenReturn(task);
        when(equipmentMapper.selectOne(any())).thenReturn(equipment);

        equipmentService.completePmTask("PM001", Map.of(
                "result", "PASS",
                "operator", "ee1002",
                "equipmentStatus", "IDLE"
        ));

        assertThat(task.getStatus()).isEqualTo("COMPLETED");
        assertThat(task.getResult()).isEqualTo("PASS");
        assertThat(task.getOperator()).isEqualTo("ee1002");
        assertThat(task.getCompletedTime()).isNotNull();
        verify(pmTaskMapper).updateById(task);

        assertThat(equipment.getStatus()).isEqualTo("IDLE");
        verify(equipmentMapper).updateById(equipment);
        verify(auditLogService).record(eq("EQUIPMENT_PM_COMPLETE"), eq("PM001"), eq("EQUIPMENT"), any(), eq("ee1002"), eq("equipment-service"), any());
    }

    @Test
    void downloadRecipeShouldPersistCommandWhenReadbackMatches() {
        Equipment equipment = equipment("COATER_01", "IDLE");
        Recipe recipe = activeRecipe("RCP_COAT_001", "COATER_01");
        when(equipmentMapper.selectOne(any())).thenReturn(equipment);
        when(recipeMapper.selectOne(any())).thenReturn(recipe);
        when(recipeParamMapper.selectList(any())).thenReturn(List.of(recipeParam("TEMP_COATING", "150", "145", "155")));

        equipmentService.downloadRecipe(Map.of(
                "equipmentCode", "COATER_01",
                "recipeCode", "RCP_COAT_001",
                "readbackStatus", "MATCH",
                "operator", "ee1001"
        ));

        ArgumentCaptor<EquipmentRecipeCommand> commandCaptor = ArgumentCaptor.forClass(EquipmentRecipeCommand.class);
        verify(recipeCommandMapper).insert(commandCaptor.capture());
        EquipmentRecipeCommand command = commandCaptor.getValue();
        assertThat(command.getEquipmentCode()).isEqualTo("COATER_01");
        assertThat(command.getRecipeCode()).isEqualTo("RCP_COAT_001");
        assertThat(command.getCommandStatus()).isEqualTo("SUCCESS");
        assertThat(command.getReadbackStatus()).isEqualTo("MATCH");
        assertThat(command.getExpectedParamSnapshot()).contains("TEMP_COATING");
        verify(auditLogService).record(eq("EQUIPMENT_RECIPE_DOWNLOAD"), eq(command.getCommandNo()), eq("EQUIPMENT"), any(), eq("ee1001"), eq("equipment-service"), any());
    }

    @Test
    void downloadRecipeShouldCreateEventWhenReadbackMismatches() {
        Equipment equipment = equipment("COATER_02", "RUNNING");
        Recipe recipe = activeRecipe("RCP_COAT_002", "COATER_02");
        when(equipmentMapper.selectOne(any())).thenReturn(equipment);
        when(recipeMapper.selectOne(any())).thenReturn(recipe);
        when(recipeParamMapper.selectList(any())).thenReturn(List.of(recipeParam("THICKNESS", "2.0", "1.8", "2.2")));

        equipmentService.downloadRecipe(Map.of(
                "equipmentCode", "COATER_02",
                "recipeCode", "RCP_COAT_002",
                "readbackStatus", "MISMATCH",
                "operator", "ee1001"
        ));

        ArgumentCaptor<EquipmentRecipeCommand> commandCaptor = ArgumentCaptor.forClass(EquipmentRecipeCommand.class);
        verify(recipeCommandMapper).insert(commandCaptor.capture());
        assertThat(commandCaptor.getValue().getCommandStatus()).isEqualTo("FAILED");
        assertThat(commandCaptor.getValue().getReadbackStatus()).isEqualTo("MISMATCH");

        ArgumentCaptor<EquipmentEvent> eventCaptor = ArgumentCaptor.forClass(EquipmentEvent.class);
        verify(eventMapper).insert(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("RECIPE");
        assertThat(eventCaptor.getValue().getEventLevel()).isEqualTo("P1");
        assertThat(equipment.getStatus()).isEqualTo("ALARM");
        verify(equipmentMapper).updateById(equipment);
    }

    @Test
    void oeeSummaryShouldAggregatePlannedAndUnplannedDowntime() {
        LocalDateTime now = LocalDateTime.now();
        Equipment coater = equipment("COATER_01", "RUNNING");
        Equipment evap = equipment("EVAP_01", "IDLE");
        when(equipmentMapper.selectList(isNull())).thenReturn(List.of(coater, evap));
        when(eventMapper.selectList(any())).thenReturn(List.of(
                event("PM001", "COATER_01", "PM", "PLANNED", "PM_NOZZLE_CLEAN", now.minusMinutes(120), now.minusMinutes(90)),
                event("ALM001", "EVAP_01", "ALARM", "UNPLANNED", "VACUUM_PUMP_DOWN", now.minusMinutes(80), now.minusMinutes(20))
        ));
        when(parameterSampleMapper.selectList(any())).thenReturn(List.of(
                sample("EPS001", "COATER_01", "OK"),
                sample("EPS002", "EVAP_01", "NG")
        ));

        Map<String, Object> data = equipmentService.oeeSummary("LINE_01");

        assertThat(data.get("plannedDowntimeMinutes")).isEqualTo(30L);
        assertThat(data.get("unplannedDowntimeMinutes")).isEqualTo(60L);
        assertThat(data.get("qualityText")).isEqualTo("50.00%");
        assertThat(data.get("oeeText")).asString().endsWith("%");
        assertThat((List<?>) data.get("reasonTopN")).hasSize(2);
    }

    @Test
    void closeEventShouldFillDurationRestoreEquipmentAndAudit() {
        EquipmentEvent event = event("EVT001", "EVAP_01", "ALARM", "UNPLANNED", "VACUUM_PUMP_DOWN",
                LocalDateTime.now().minusMinutes(42), null);
        Equipment equipment = equipment("EVAP_01", "ALARM");
        when(eventMapper.selectOne(any())).thenReturn(event);
        when(equipmentMapper.selectOne(any())).thenReturn(equipment);

        equipmentService.closeEvent("EVT001", Map.of(
                "operator", "ee1001",
                "closeConclusion", "真空泵复位后点检通过",
                "equipmentStatus", "IDLE"
        ));

        assertThat(event.getStatus()).isEqualTo("CLOSED");
        assertThat(event.getClosedBy()).isEqualTo("ee1001");
        assertThat(event.getDurationMinutes()).isGreaterThanOrEqualTo(41);
        verify(eventMapper).updateById(event);
        assertThat(equipment.getStatus()).isEqualTo("IDLE");
        verify(equipmentMapper).updateById(equipment);
        verify(auditLogService).record(eq("EQUIPMENT_EVENT_CLOSE"), eq("EVT001"), eq("EQUIPMENT"), any(), eq("ee1001"), eq("equipment-service"), any());
    }

    @Test
    void reportStatusShouldUpdateEquipmentWriteHistoryAndAudit() {
        Equipment equipment = equipment("COATER_01", "IDLE");
        when(equipmentMapper.selectOne(any())).thenReturn(equipment);

        equipmentService.reportStatus(Map.of(
                "equipmentCode", "COATER_01",
                "status", "RUNNING",
                "changeReason", "EAP heartbeat RUNNING",
                "operator", "ee1001"
        ));

        assertThat(equipment.getStatus()).isEqualTo("RUNNING");
        verify(equipmentMapper).updateById(equipment);
        ArgumentCaptor<EquipmentStatusHistory> historyCaptor = ArgumentCaptor.forClass(EquipmentStatusHistory.class);
        verify(statusHistoryMapper).insert(historyCaptor.capture());
        EquipmentStatusHistory history = historyCaptor.getValue();
        assertThat(history.getEquipmentCode()).isEqualTo("COATER_01");
        assertThat(history.getFromStatus()).isEqualTo("IDLE");
        assertThat(history.getToStatus()).isEqualTo("RUNNING");
        assertThat(history.getChangeReason()).isEqualTo("EAP heartbeat RUNNING");
        verify(auditLogService).record(eq("EAP_STATUS_REPORT"), eq("COATER_01"), eq("EQUIPMENT"), any(), eq("ee1001"), eq("equipment-service"), any());
    }

    @Test
    void reportCycleSampleShouldPersistSampleAndAudit() {
        Equipment equipment = equipment("COATER_02", "RUNNING");
        when(equipmentMapper.selectOne(any())).thenReturn(equipment);

        equipmentService.reportCycleSample(Map.of(
                "equipmentCode", "COATER_02",
                "lotNo", "LOT001",
                "stepCode", "COATING",
                "recipeCode", "RCP_COAT_002",
                "standardCycleSeconds", "58",
                "actualCycleSeconds", "72",
                "outputQty", "1",
                "goodQty", "0",
                "operator", "ee1001"
        ));

        ArgumentCaptor<EquipmentCycleSample> sampleCaptor = ArgumentCaptor.forClass(EquipmentCycleSample.class);
        verify(cycleSampleMapper).insert(sampleCaptor.capture());
        EquipmentCycleSample sample = sampleCaptor.getValue();
        assertThat(sample.getEquipmentCode()).isEqualTo("COATER_02");
        assertThat(sample.getStandardCycleSeconds()).isEqualByComparingTo("58");
        assertThat(sample.getActualCycleSeconds()).isEqualByComparingTo("72");
        assertThat(sample.getGoodQty()).isZero();
        assertThat(sample.getResult()).isEqualTo("NG");
        verify(auditLogService).record(eq("EAP_CYCLE_REPORT"), eq(sample.getSampleNo()), eq("EQUIPMENT"), any(), eq("ee1001"), eq("equipment-service"), any());
    }

    @Test
    void reportCycleSampleShouldUseActiveStandardCycleWhenStandardIsNotReported() {
        Equipment equipment = equipment("COATER_01", "RUNNING");
        EquipmentStandardCycle standardCycle = standardCycle("ESC001", "AMOLED_65", "COATING", "COATER_01", "RCP_COAT_001", "58", "52", "70");
        when(equipmentMapper.selectOne(any())).thenReturn(equipment);
        when(standardCycleMapper.selectList(any())).thenReturn(List.of(standardCycle));

        equipmentService.reportCycleSample(Map.of(
                "equipmentCode", "COATER_01",
                "productCode", "AMOLED_65",
                "lotNo", "LOT001",
                "stepCode", "COATING",
                "recipeCode", "RCP_COAT_001",
                "actualCycleSeconds", "62",
                "outputQty", "1",
                "goodQty", "1",
                "operator", "ee1001"
        ));

        ArgumentCaptor<EquipmentCycleSample> sampleCaptor = ArgumentCaptor.forClass(EquipmentCycleSample.class);
        verify(cycleSampleMapper).insert(sampleCaptor.capture());
        EquipmentCycleSample sample = sampleCaptor.getValue();
        assertThat(sample.getStandardCycleSeconds()).isEqualByComparingTo("58");
        assertThat(sample.getResult()).isEqualTo("OK");
        assertThat(sample.getRawPayload()).contains("ESC001");
    }

    @Test
    void publishStandardCycleShouldExpirePreviousActiveCycleAndAudit() {
        Equipment equipment = equipment("COATER_01", "IDLE");
        EquipmentStandardCycle oldCycle = standardCycle("ESC000", "AMOLED_65", "COATING", "COATER_01", "RCP_COAT_001", "60", "54", "72");
        when(equipmentMapper.selectOne(any())).thenReturn(equipment);
        when(standardCycleMapper.selectList(any())).thenReturn(List.of(oldCycle));

        equipmentService.publishStandardCycle(Map.of(
                "equipmentCode", "COATER_01",
                "productCode", "AMOLED_65",
                "stepCode", "COATING",
                "recipeCode", "RCP_COAT_001",
                "cycleVersion", "V1.1",
                "standardCycleSeconds", "58",
                "lowerCycleSeconds", "52",
                "upperCycleSeconds", "70",
                "operator", "ee1001"
        ));

        assertThat(oldCycle.getStatus()).isEqualTo("INACTIVE");
        assertThat(oldCycle.getExpireTime()).isNotNull();
        verify(standardCycleMapper).updateById(oldCycle);

        ArgumentCaptor<EquipmentStandardCycle> cycleCaptor = ArgumentCaptor.forClass(EquipmentStandardCycle.class);
        verify(standardCycleMapper).insert(cycleCaptor.capture());
        EquipmentStandardCycle newCycle = cycleCaptor.getValue();
        assertThat(newCycle.getProductCode()).isEqualTo("AMOLED_65");
        assertThat(newCycle.getStepCode()).isEqualTo("COATING");
        assertThat(newCycle.getEquipmentCode()).isEqualTo("COATER_01");
        assertThat(newCycle.getRecipeCode()).isEqualTo("RCP_COAT_001");
        assertThat(newCycle.getCycleVersion()).isEqualTo("V1.1");
        assertThat(newCycle.getStandardCycleSeconds()).isEqualByComparingTo("58");
        verify(auditLogService).record(eq("EQUIPMENT_STANDARD_CYCLE_PUBLISH"), eq(newCycle.getCycleNo()), eq("EQUIPMENT"), any(), eq("ee1001"), eq("equipment-service"), any());
    }

    @Test
    void oeeSummaryShouldPreferCycleSamplesForPerformanceAndQuality() {
        LocalDateTime now = LocalDateTime.now();
        Equipment coater = equipment("COATER_01", "RUNNING");
        when(equipmentMapper.selectList(isNull())).thenReturn(List.of(coater));
        when(eventMapper.selectList(any())).thenReturn(List.of());
        when(cycleSampleMapper.selectList(any())).thenReturn(List.of(
                cycleSample("ECS001", "COATER_01", "50", "100", 2, 1)
        ));

        Map<String, Object> data = equipmentService.oeeSummary("LINE_01");

        assertThat(data.get("performanceText")).isEqualTo("50.00%");
        assertThat(data.get("qualityText")).isEqualTo("50.00%");
        assertThat(data.get("performanceSampleCount")).isEqualTo(1);
        assertThat(String.valueOf(data.get("calculationNote"))).contains("EAP标准节拍");
    }

    private Equipment equipment(String code, String status) {
        Equipment equipment = new Equipment();
        equipment.setId(100L);
        equipment.setEquipmentCode(code);
        equipment.setEquipmentName(code);
        equipment.setLineCode("LINE_01");
        equipment.setStatus(status);
        equipment.setCapabilitySteps("[\"COATING\"]");
        equipment.setUpdatedTime(LocalDateTime.now().minusHours(1));
        return equipment;
    }

    private EquipmentEvent event(String eventNo, String equipmentCode, String eventType, String downtimeType,
                                 String reasonCode, LocalDateTime startedTime, LocalDateTime endedTime) {
        EquipmentEvent event = new EquipmentEvent();
        event.setEventNo(eventNo);
        event.setEquipmentCode(equipmentCode);
        event.setLineCode("LINE_01");
        event.setEventType(eventType);
        event.setEventLevel("P2");
        event.setTitle(reasonCode);
        event.setDescription(reasonCode);
        event.setStatus(endedTime == null ? "OPEN" : "CLOSED");
        event.setSourceSystem("eap-adapter");
        event.setOccurredTime(startedTime);
        event.setStartedTime(startedTime);
        event.setEndedTime(endedTime);
        event.setReasonCode(reasonCode);
        event.setReasonName(reasonCode);
        event.setDowntimeType(downtimeType);
        event.setDowntimeCategory("PM".equals(eventType) ? "PM" : "EQUIPMENT");
        event.setCreatedBy("system");
        event.setCreatedTime(startedTime);
        event.setUpdatedTime(endedTime == null ? startedTime : endedTime);
        return event;
    }

    private EquipmentParameterSample sample(String sampleNo, String equipmentCode, String result) {
        EquipmentParameterSample sample = new EquipmentParameterSample();
        sample.setSampleNo(sampleNo);
        sample.setEquipmentCode(equipmentCode);
        sample.setLineCode("LINE_01");
        sample.setParamCode("TEMP");
        sample.setParamName("TEMP");
        sample.setParamValue(BigDecimal.ONE);
        sample.setResult(result);
        sample.setSampleTime(LocalDateTime.now().minusMinutes(10));
        sample.setSourceSystem("eap-adapter");
        return sample;
    }

    private EquipmentCycleSample cycleSample(String sampleNo, String equipmentCode, String standard, String actual,
                                             int outputQty, int goodQty) {
        EquipmentCycleSample sample = new EquipmentCycleSample();
        sample.setSampleNo(sampleNo);
        sample.setEquipmentCode(equipmentCode);
        sample.setLineCode("LINE_01");
        sample.setLotNo("LOT001");
        sample.setStepCode("COATING");
        sample.setRecipeCode("RCP_COAT_001");
        sample.setStandardCycleSeconds(new BigDecimal(standard));
        sample.setActualCycleSeconds(new BigDecimal(actual));
        sample.setOutputQty(outputQty);
        sample.setGoodQty(goodQty);
        sample.setResult(goodQty == outputQty ? "OK" : "NG");
        sample.setSampleTime(LocalDateTime.now().minusMinutes(5));
        sample.setSourceSystem("eap-adapter");
        return sample;
    }

    private EquipmentStandardCycle standardCycle(String cycleNo, String productCode, String stepCode,
                                                 String equipmentCode, String recipeCode, String standard,
                                                 String lower, String upper) {
        EquipmentStandardCycle cycle = new EquipmentStandardCycle();
        cycle.setCycleNo(cycleNo);
        cycle.setProductCode(productCode);
        cycle.setStepCode(stepCode);
        cycle.setEquipmentCode(equipmentCode);
        cycle.setRecipeCode(recipeCode);
        cycle.setCycleVersion("V1.0");
        cycle.setStandardCycleSeconds(new BigDecimal(standard));
        cycle.setLowerCycleSeconds(new BigDecimal(lower));
        cycle.setUpperCycleSeconds(new BigDecimal(upper));
        cycle.setStatus("ACTIVE");
        cycle.setEffectiveTime(LocalDateTime.now().minusDays(1));
        cycle.setUpdatedTime(LocalDateTime.now().minusHours(1));
        return cycle;
    }

    private Recipe activeRecipe(String recipeCode, String equipmentCode) {
        Recipe recipe = new Recipe();
        recipe.setId(200L);
        recipe.setRecipeCode(recipeCode);
        recipe.setRecipeName(recipeCode);
        recipe.setProductCode(recipeCode.endsWith("002") ? "AMOLED_67" : "AMOLED_65");
        recipe.setStepCode("COATING");
        recipe.setEquipmentCode(equipmentCode);
        recipe.setRecipeVersion("V1.0");
        recipe.setStatus("ACTIVE");
        return recipe;
    }

    private RecipeParam recipeParam(String code, String target, String lower, String upper) {
        RecipeParam param = new RecipeParam();
        param.setId(300L);
        param.setRecipeId(200L);
        param.setParamCode(code);
        param.setParamName(code);
        param.setTargetValue(new BigDecimal(target));
        param.setLowerLimit(new BigDecimal(lower));
        param.setUpperLimit(new BigDecimal(upper));
        param.setUnit("unit");
        param.setIsKeyParam(1);
        param.setDisplayOrder(1);
        return param;
    }
}
