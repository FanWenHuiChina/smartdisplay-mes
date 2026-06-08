package com.visionox.mes.quality.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 缺陷记录。
 */
@Data
@TableName("quality_defect_record")
public class QualityDefectRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String defectNo;

    private String inspectionNo;

    private String lotNo;

    private String stepCode;

    private String equipmentCode;

    private String defectCode;

    private String defectName;

    private String defectLevel;

    private String defectPosition;

    private Integer qty;

    private String status;

    private String disposition;

    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
