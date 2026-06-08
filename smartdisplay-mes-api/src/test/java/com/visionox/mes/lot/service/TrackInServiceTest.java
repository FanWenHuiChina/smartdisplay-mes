package com.visionox.mes.lot.service;

import com.visionox.mes.common.BusinessException;
import com.visionox.mes.lot.dto.TrackInRequest;
import com.visionox.mes.lot.dto.TrackOutRequest;
import com.visionox.mes.lot.entity.Equipment;
import com.visionox.mes.lot.entity.Lot;
import com.visionox.mes.lot.entity.LotStepRecord;
import com.visionox.mes.lot.mapper.EquipmentMapper;
import com.visionox.mes.lot.mapper.HoldRecordMapper;
import com.visionox.mes.lot.mapper.LotMapper;
import com.visionox.mes.lot.mapper.LotStepRecordMapper;
import com.visionox.mes.masterdata.entity.WorkShift;
import com.visionox.mes.masterdata.mapper.WorkShiftMapper;
import com.visionox.mes.material.service.MaterialService;
import com.visionox.mes.quality.service.QualityService;
import com.visionox.mes.recipe.entity.Recipe;
import com.visionox.mes.recipe.service.RecipeService;
import com.visionox.mes.route.service.RouteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackInServiceTest {

    @Mock
    private LotMapper lotMapper;

    @Mock
    private EquipmentMapper equipmentMapper;

    @Mock
    private LotStepRecordMapper stepRecordMapper;

    @Mock
    private HoldRecordMapper holdRecordMapper;

    @Mock
    private RecipeService recipeService;

    @Mock
    private RouteService routeService;

    @Mock
    private QualityService qualityService;

    @Mock
    private MaterialService materialService;

    @Mock
    private WorkShiftMapper workShiftMapper;

    @InjectMocks
    private TrackInService trackInService;

    @Test
    void trackInShouldRejectLotThatIsNotReadyBeforeCallingDownstreamRules() {
        Lot lot = lot("LOT001", "PROCESSING", 0);
        when(lotMapper.selectOne(any())).thenReturn(lot);

        assertThatThrownBy(() -> trackInService.trackIn(trackInRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PROCESSING");

        verify(routeService, never()).validateTrackInStep(any(), any(), any());
        verify(lotMapper, never()).updateById(any());
        verify(stepRecordMapper, never()).insert(any());
    }

    @Test
    void trackInShouldRejectUnavailableEquipment() {
        Lot lot = lot("LOT001", "READY", 0);
        Equipment equipment = equipment("COATER_01", "ALARM", "[\"COATING\"]");
        when(lotMapper.selectOne(any())).thenReturn(lot);
        when(equipmentMapper.selectOne(any())).thenReturn(equipment);

        assertThatThrownBy(() -> trackInService.trackIn(trackInRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("COATER_01")
                .hasMessageContaining("ALARM");

        verify(routeService).validateTrackInStep("OLED_PANEL", "COATING", "COATING");
        verify(recipeService, never()).findActiveRecipe(any(), any(), any());
        verify(lotMapper, never()).updateById(any());
        verify(stepRecordMapper, never()).insert(any());
    }

    @Test
    void trackInShouldRejectActiveHoldAfterRecipeValidation() {
        Lot lot = lot("LOT001", "READY", 1);
        when(lotMapper.selectOne(any())).thenReturn(lot);
        when(equipmentMapper.selectOne(any())).thenReturn(equipment("COATER_01", "IDLE", "[\"COATING\"]"));
        when(recipeService.findActiveRecipe("OLED_PANEL", "COATING", "COATER_01")).thenReturn(recipe("RCP_COAT_01"));
        when(holdRecordMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> trackInService.trackIn(trackInRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("LOT001");

        verify(materialService, never()).validateReadiness(any(), any());
        verify(lotMapper, never()).updateById(any());
        verify(stepRecordMapper, never()).insert(any());
    }

    @Test
    void trackInShouldUpdateLotCreateStepRecordAndLockMaterialWhenAllRulesPass() {
        Lot lot = lot("LOT001", "READY", 0);
        when(lotMapper.selectOne(any())).thenReturn(lot);
        when(equipmentMapper.selectOne(any())).thenReturn(equipment("COATER_01", "IDLE", "[\"COATING\"]"));
        when(recipeService.findActiveRecipe("OLED_PANEL", "COATING", "COATER_01")).thenReturn(recipe("RCP_COAT_01"));
        when(workShiftMapper.selectList(any())).thenReturn(List.of(activeShift()));

        trackInService.trackIn(trackInRequest());

        verify(routeService).validateTrackInStep("OLED_PANEL", "COATING", "COATING");
        verify(materialService).validateReadiness(lot, "COATING");

        assertThat(lot.getStatus()).isEqualTo("PROCESSING");
        assertThat(lot.getCurrentStepCode()).isEqualTo("COATING");
        assertThat(lot.getCurrentEquipmentCode()).isEqualTo("COATER_01");
        verify(lotMapper).updateById(lot);

        ArgumentCaptor<LotStepRecord> captor = ArgumentCaptor.forClass(LotStepRecord.class);
        verify(stepRecordMapper).insert(captor.capture());
        LotStepRecord record = captor.getValue();
        assertThat(record.getLotNo()).isEqualTo("LOT001");
        assertThat(record.getStepCode()).isEqualTo("COATING");
        assertThat(record.getEquipmentCode()).isEqualTo("COATER_01");
        assertThat(record.getRecipeCode()).isEqualTo("RCP_COAT_01");
        assertThat(record.getOperator()).isEqualTo("op1001");
        assertThat(record.getTrackInTime()).isNotNull();
        verify(materialService).lockForTrackIn(eq(lot), eq("COATING"), eq("COATER_01"), eq("op1001"));
    }

    @Test
    void trackInShouldAllowReworkLotToRestartAtReworkStep() {
        Lot lot = lot("LOT001", "REWORK", 0);
        when(lotMapper.selectOne(any())).thenReturn(lot);
        when(equipmentMapper.selectOne(any())).thenReturn(equipment("COATER_01", "IDLE", "[\"COATING\"]"));
        when(recipeService.findActiveRecipe("OLED_PANEL", "COATING", "COATER_01")).thenReturn(recipe("RCP_COAT_01"));
        when(workShiftMapper.selectList(any())).thenReturn(List.of(activeShift()));

        trackInService.trackIn(trackInRequest());

        verify(routeService).validateTrackInStep("OLED_PANEL", "COATING", "COATING");
        verify(materialService).validateReadiness(lot, "COATING");
        assertThat(lot.getStatus()).isEqualTo("PROCESSING");
        assertThat(lot.getCurrentEquipmentCode()).isEqualTo("COATER_01");
        verify(lotMapper).updateById(lot);
        verify(stepRecordMapper).insert(any(LotStepRecord.class));
        verify(materialService).lockForTrackIn(eq(lot), eq("COATING"), eq("COATER_01"), eq("op1001"));
    }

    @Test
    void trackInShouldRejectWhenNoActiveShiftWindowMatches() {
        Lot lot = lot("LOT001", "READY", 0);
        when(lotMapper.selectOne(any())).thenReturn(lot);
        when(equipmentMapper.selectOne(any())).thenReturn(equipment("COATER_01", "IDLE", "[\"COATING\"]"));
        when(recipeService.findActiveRecipe("OLED_PANEL", "COATING", "COATER_01")).thenReturn(recipe("RCP_COAT_01"));
        when(workShiftMapper.selectList(any())).thenReturn(List.of(shift("SHIFT_SHORT", "LINE_01",
                LocalTime.now().minusHours(2), LocalTime.now().minusHours(1), 0)));

        assertThatThrownBy(() -> trackInService.trackIn(trackInRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("班次校验失败");

        verify(materialService, never()).validateReadiness(any(), any());
        verify(lotMapper, never()).updateById(any());
        verify(stepRecordMapper, never()).insert(any());
    }

    @Test
    void trackOutShouldRejectLotThatIsNotProcessing() {
        Lot lot = lot("LOT001", "READY", 0);
        when(lotMapper.selectOne(any())).thenReturn(lot);

        assertThatThrownBy(() -> trackInService.trackOut(trackOutRequest("OK")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("READY")
                .hasMessageContaining("PROCESSING");

        verify(stepRecordMapper, never()).selectList(any());
        verify(qualityService, never()).evaluateTrackOut(any(), any(), any());
        verify(materialService, never()).consumeForTrackOut(any(), any());
    }

    @Test
    void trackOutShouldRejectWhenNoOpenTrackInRecordExists() {
        Lot lot = lot("LOT001", "PROCESSING", 0);
        when(lotMapper.selectOne(any())).thenReturn(lot);
        when(stepRecordMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> trackInService.trackOut(trackOutRequest("OK")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Track In")
                .hasMessageContaining("LOT001");

        verify(qualityService, never()).evaluateTrackOut(any(), any(), any());
        verify(stepRecordMapper, never()).updateById(any());
        verify(materialService, never()).consumeForTrackOut(any(), any());
    }

    @Test
    void trackOutShouldReturnLotToReadyAndConsumeMaterialWhenQualityResultIsOk() {
        Lot lot = lot("LOT001", "PROCESSING", 0);
        LotStepRecord record = openStepRecord();
        TrackOutRequest request = trackOutRequest("OK");
        when(lotMapper.selectOne(any())).thenReturn(lot);
        when(stepRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(qualityService.evaluateTrackOut(lot, record, request)).thenReturn("OK");

        String result = trackInService.trackOut(request);

        assertThat(result).isEqualTo("OK");
        assertThat(record.getTrackOutTime()).isNotNull();
        assertThat(record.getProcessParams()).isEqualTo("{\"thickness\":65.2}");
        assertThat(record.getRemark()).isEqualTo("出站正常");
        assertThat(record.getResult()).isEqualTo("OK");
        verify(stepRecordMapper).updateById(record);
        verify(materialService).consumeForTrackOut(lot, record);

        assertThat(lot.getStatus()).isEqualTo("READY");
        verify(lotMapper).updateById(lot);
    }

    @Test
    void trackOutShouldKeepLotDispositionWithQualityServiceWhenQualityResultIsNg() {
        Lot lot = lot("LOT001", "PROCESSING", 0);
        LotStepRecord record = openStepRecord();
        TrackOutRequest request = trackOutRequest("NG");
        when(lotMapper.selectOne(any())).thenReturn(lot);
        when(stepRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(qualityService.evaluateTrackOut(lot, record, request)).thenReturn("NG");

        String result = trackInService.trackOut(request);

        assertThat(result).isEqualTo("NG");
        assertThat(record.getResult()).isEqualTo("NG");
        assertThat(record.getTrackOutTime()).isNotNull();
        verify(stepRecordMapper).updateById(record);
        verify(materialService).consumeForTrackOut(lot, record);

        assertThat(lot.getStatus()).isEqualTo("PROCESSING");
        verify(lotMapper, never()).updateById(lot);
    }

    private TrackInRequest trackInRequest() {
        TrackInRequest request = new TrackInRequest();
        request.setLotNo("LOT001");
        request.setStepCode("COATING");
        request.setEquipmentCode("COATER_01");
        request.setOperator("op1001");
        return request;
    }

    private TrackOutRequest trackOutRequest(String result) {
        TrackOutRequest request = new TrackOutRequest();
        request.setLotNo("LOT001");
        request.setResult(result);
        request.setProcessParams("{\"thickness\":65.2}");
        request.setRemark("OK".equals(result) ? "出站正常" : "涂布厚度不足");
        return request;
    }

    private LotStepRecord openStepRecord() {
        LotStepRecord record = new LotStepRecord();
        record.setLotNo("LOT001");
        record.setStepCode("COATING");
        record.setEquipmentCode("COATER_01");
        record.setRecipeCode("RCP_COAT_01");
        record.setOperator("op1001");
        record.setTrackInTime(LocalDateTime.now().minusMinutes(30));
        return record;
    }

    private Lot lot(String lotNo, String status, int holdFlag) {
        Lot lot = new Lot();
        lot.setLotNo(lotNo);
        lot.setOrderNo("MO001");
        lot.setProductCode("OLED_PANEL");
        lot.setLineCode("LINE_01");
        lot.setCurrentStepCode("COATING");
        lot.setStatus(status);
        lot.setHoldFlag(holdFlag);
        return lot;
    }

    private Equipment equipment(String equipmentCode, String status, String capabilitySteps) {
        Equipment equipment = new Equipment();
        equipment.setEquipmentCode(equipmentCode);
        equipment.setStatus(status);
        equipment.setCapabilitySteps(capabilitySteps);
        return equipment;
    }

    private Recipe recipe(String recipeCode) {
        Recipe recipe = new Recipe();
        recipe.setRecipeCode(recipeCode);
        return recipe;
    }

    private WorkShift activeShift() {
        return shift("SHIFT_ALL_DAY", "LINE_01", LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, 0);
    }

    private WorkShift shift(String shiftCode, String lineCode, LocalTime startTime, LocalTime endTime, int crossDay) {
        WorkShift shift = new WorkShift();
        shift.setShiftCode(shiftCode);
        shift.setLineCode(lineCode);
        shift.setStartTime(startTime);
        shift.setEndTime(endTime);
        shift.setCrossDay(crossDay);
        shift.setStatus("ACTIVE");
        return shift;
    }
}
