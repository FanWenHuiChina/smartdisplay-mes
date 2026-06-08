package com.visionox.mes.route.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工艺路线主表。
 */
@Data
@TableName("md_route")
public class Route implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String routeCode;
    private String routeName;
    private String productCode;
    private String routeVersion;
    private String status;
    private LocalDateTime effectiveTime;
    private String description;
    private String createdBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    @TableLogic
    private Integer deleted;
}
