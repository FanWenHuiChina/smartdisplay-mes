package com.visionox.mes.masterdata.service;

import com.visionox.mes.lot.mapper.EquipmentMapper;
import com.visionox.mes.lot.mapper.ProcessStepMapper;
import com.visionox.mes.masterdata.entity.ProductionLine;
import com.visionox.mes.masterdata.entity.Site;
import com.visionox.mes.masterdata.entity.WorkShift;
import com.visionox.mes.masterdata.mapper.ProductionLineMapper;
import com.visionox.mes.masterdata.mapper.SiteMapper;
import com.visionox.mes.masterdata.mapper.WorkShiftMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MasterDataServiceTest {

    @Mock
    private ProcessStepMapper processStepMapper;
    @Mock
    private EquipmentMapper equipmentMapper;
    @Mock
    private SiteMapper siteMapper;
    @Mock
    private ProductionLineMapper productionLineMapper;
    @Mock
    private WorkShiftMapper workShiftMapper;

    @InjectMocks
    private MasterDataService masterDataService;

    @Test
    void getAllSitesShouldReturnOrderedSiteRows() {
        Site site = new Site();
        site.setSiteCode("SITE_HF_01");
        when(siteMapper.selectList(any())).thenReturn(List.of(site));

        assertThat(masterDataService.getAllSites()).containsExactly(site);

        verify(siteMapper).selectList(any());
    }

    @Test
    void getAllProductionLinesShouldPassFiltersToMapper() {
        ProductionLine line = new ProductionLine();
        line.setLineCode("LINE_01");
        line.setSiteCode("SITE_HF_01");
        line.setStatus("ACTIVE");
        when(productionLineMapper.selectList(any())).thenReturn(List.of(line));

        assertThat(masterDataService.getAllProductionLines("SITE_HF_01", "ACTIVE")).containsExactly(line);

        verify(productionLineMapper).selectList(any());
    }

    @Test
    void getAllWorkShiftsShouldPassFiltersToMapper() {
        WorkShift shift = new WorkShift();
        shift.setShiftCode("SHIFT_D_LINE_01");
        shift.setLineCode("LINE_01");
        shift.setStatus("ACTIVE");
        when(workShiftMapper.selectList(any())).thenReturn(List.of(shift));

        assertThat(masterDataService.getAllWorkShifts("LINE_01", "ACTIVE")).containsExactly(shift);

        verify(workShiftMapper).selectList(any());
    }
}
