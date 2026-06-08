package com.visionox.mes.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 生产工单实体。
 */
@Data
@TableName("prod_order")
public class ProductionOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private String productCode;
    private String productName;
    private Integer plannedQty;
    private Integer completedQty;
    private Integer priority;
    private String lineCode;
    private Long routeId;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String createdBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
