package com.visionox.mes.material.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.visionox.mes.material.entity.MaterialBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface MaterialBatchMapper extends BaseMapper<MaterialBatch> {

    @Select("""
            SELECT *
            FROM material_batch
            WHERE batch_no = #{batchNo}
            LIMIT 1
            FOR UPDATE
            """)
    MaterialBatch selectByBatchNoForUpdate(@Param("batchNo") String batchNo);

    @Select("""
            SELECT *
            FROM material_batch
            WHERE material_code = #{materialCode}
              AND status = 'AVAILABLE'
              AND quality_status = 'PASS'
              AND available_qty >= #{requiredQty}
              AND (expire_time IS NULL OR expire_time > #{now})
            ORDER BY fifo_seq ASC, received_time ASC, id ASC
            LIMIT 1
            FOR UPDATE
            """)
    MaterialBatch selectAvailableBatchForUpdate(@Param("materialCode") String materialCode,
                                                @Param("requiredQty") BigDecimal requiredQty,
                                                @Param("now") LocalDateTime now);
}
