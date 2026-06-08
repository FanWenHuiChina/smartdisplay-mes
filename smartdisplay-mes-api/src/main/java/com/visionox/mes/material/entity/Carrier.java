package com.visionox.mes.material.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 载具与Lot绑定。
 */
@Data
@TableName("material_carrier")
public class Carrier implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String carrierNo;

    private String carrierType;

    private String status;

    private String lotNo;

    private String lineCode;

    private String productCode;

    private String stepCode;

    private String equipmentCode;

    private LocalDateTime bindTime;

    private LocalDateTime unbindTime;

    private String location;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedTime;
}
