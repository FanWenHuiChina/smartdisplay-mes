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
 * 异常事件。
 */
@Data
@TableName("exception_event")
public class ExceptionEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventNo;

    private String eventType;

    private String eventLevel;

    private String lotNo;

    private String orderNo;

    private String stepCode;

    private String equipmentCode;

    private String sourceModule;

    private String title;

    private String description;

    private String status;

    private String ownerRole;

    private String ownerUser;

    private LocalDateTime occurredTime;

    private String mrbResult;

    private String mrbOpinion;

    private String mrbReviewer;

    private LocalDateTime mrbTime;

    private String dispositionAction;

    private String rootCause;

    private LocalDateTime closedTime;

    private String closeConclusion;

    private String createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdTime;
}
