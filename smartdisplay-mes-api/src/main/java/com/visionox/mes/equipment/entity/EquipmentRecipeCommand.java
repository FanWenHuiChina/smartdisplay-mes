package com.visionox.mes.equipment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("equipment_recipe_command")
public class EquipmentRecipeCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String commandNo;
    private String equipmentCode;
    private String lineCode;
    private String lotNo;
    private String stepCode;
    private String productCode;
    private Long recipeId;
    private String recipeCode;
    private String recipeVersion;
    private String commandType;
    private String commandStatus;
    private String downloadBy;
    private LocalDateTime downloadTime;
    private String eapAckStatus;
    private String readbackStatus;
    private String expectedParamSnapshot;
    private String readbackParamSnapshot;
    private String mismatchDetail;
    private String sourceSystem;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private String requestSnapshot;
}
