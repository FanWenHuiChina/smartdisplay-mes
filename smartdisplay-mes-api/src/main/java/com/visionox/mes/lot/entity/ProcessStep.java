package com.visionox.mes.lot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工序定义实体
 */
@Data
@TableName("md_process_step")
public class ProcessStep {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 工序编码
     */
    private String stepCode;

    /**
     * 工序名称
     */
    private String stepName;

    /**
     * 工序类型
     */
    private String stepType;

    /**
     * 所属段（Array/Cell/Module）
     */
    private String segment;

    /**
     * 是否需要Recipe（1是0否）
     */
    private Integer needRecipe;

    /**
     * 是否需要质检（1是0否）
     */
    private Integer needQc;

    /**
     * 是否允许返工（1是0否）
     */
    private Integer allowRework;

    /**
     * 说明
     */
    private String description;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;
}
