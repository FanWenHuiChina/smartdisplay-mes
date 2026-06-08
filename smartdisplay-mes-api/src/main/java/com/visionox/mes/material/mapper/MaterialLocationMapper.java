package com.visionox.mes.material.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.visionox.mes.material.entity.MaterialLocation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface MaterialLocationMapper extends BaseMapper<MaterialLocation> {

    @Select("""
            SELECT *
            FROM md_material_location
            WHERE location_code = #{locationCode}
            LIMIT 1
            FOR UPDATE
            """)
    MaterialLocation selectByLocationCodeForUpdate(@Param("locationCode") String locationCode);

    @Select("""
            SELECT *
            FROM md_material_location
            WHERE status = 'ACTIVE'
              AND (unit IS NULL OR unit = '' OR unit = #{unit})
              AND (material_class IS NULL OR material_class = '' OR material_class = 'ANY' OR material_class = #{materialClass})
              AND (capacity_qty IS NULL OR capacity_qty - COALESCE(used_qty, 0) >= #{qty})
            ORDER BY strategy_priority ASC, location_code ASC
            LIMIT 1
            FOR UPDATE
            """)
    MaterialLocation selectAvailableLocationForUpdate(@Param("materialClass") String materialClass,
                                                      @Param("unit") String unit,
                                                      @Param("qty") BigDecimal qty);
}
