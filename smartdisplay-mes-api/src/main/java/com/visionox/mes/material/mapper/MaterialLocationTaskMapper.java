package com.visionox.mes.material.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.visionox.mes.material.entity.MaterialLocationTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MaterialLocationTaskMapper extends BaseMapper<MaterialLocationTask> {

    @Select("""
            SELECT *
            FROM material_location_task
            WHERE task_no = #{taskNo}
            LIMIT 1
            FOR UPDATE
            """)
    MaterialLocationTask selectByTaskNoForUpdate(@Param("taskNo") String taskNo);
}
