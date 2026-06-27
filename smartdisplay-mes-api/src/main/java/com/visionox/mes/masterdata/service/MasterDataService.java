package com.visionox.mes.masterdata.service;

import com.visionox.mes.lot.entity.Equipment;
import com.visionox.mes.lot.entity.ProcessStep;
import com.visionox.mes.lot.mapper.EquipmentMapper;
import com.visionox.mes.lot.mapper.ProcessStepMapper;
import com.visionox.mes.masterdata.entity.ProductionLine;
import com.visionox.mes.masterdata.entity.Site;
import com.visionox.mes.masterdata.entity.WorkShift;
import com.visionox.mes.masterdata.mapper.ProductionLineMapper;
import com.visionox.mes.masterdata.mapper.SiteMapper;
import com.visionox.mes.masterdata.mapper.WorkShiftMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import com.visionox.mes.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 主数据服务
 */
@Service
@RequiredArgsConstructor
public class MasterDataService {

    private final ProcessStepMapper processStepMapper;
    private final EquipmentMapper equipmentMapper;
    private final SiteMapper siteMapper;
    private final ProductionLineMapper productionLineMapper;
    private final WorkShiftMapper workShiftMapper;

    @Cacheable(cacheNames = CacheConfig.MASTER_DATA, key = "'sites'")
    public List<Site> getAllSites() {
        return siteMapper.selectList(new LambdaQueryWrapper<Site>()
                .orderByAsc(Site::getSiteCode));
    }

    @Cacheable(cacheNames = CacheConfig.MASTER_DATA, key = "'lines:' + #siteCode + ':' + #status")
    public List<ProductionLine> getAllProductionLines(String siteCode, String status) {
        LambdaQueryWrapper<ProductionLine> wrapper = new LambdaQueryWrapper<>();
        if (siteCode != null && !siteCode.isBlank()) {
            wrapper.eq(ProductionLine::getSiteCode, siteCode);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(ProductionLine::getStatus, status);
        }
        wrapper.orderByAsc(ProductionLine::getSortOrder)
                .orderByAsc(ProductionLine::getLineCode);
        return productionLineMapper.selectList(wrapper);
    }

    @Cacheable(cacheNames = CacheConfig.MASTER_DATA, key = "'shifts:' + #lineCode + ':' + #status")
    public List<WorkShift> getAllWorkShifts(String lineCode, String status) {
        LambdaQueryWrapper<WorkShift> wrapper = new LambdaQueryWrapper<>();
        if (lineCode != null && !lineCode.isBlank()) {
            wrapper.eq(WorkShift::getLineCode, lineCode);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(WorkShift::getStatus, status);
        }
        wrapper.orderByAsc(WorkShift::getLineCode)
                .orderByAsc(WorkShift::getStartTime);
        return workShiftMapper.selectList(wrapper);
    }

    /**
     * 获取所有工序
     */
    @Cacheable(cacheNames = CacheConfig.MASTER_DATA, key = "'processSteps'")
    public List<ProcessStep> getAllProcessSteps() {
        return processStepMapper.selectList(null);
    }

    /**
     * 获取所有设备
     */
    public List<Equipment> getAllEquipments() {
        return equipmentMapper.selectList(null);
    }
}
