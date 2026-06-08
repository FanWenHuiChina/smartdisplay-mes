package com.visionox.mes.route.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 工艺路线工序明细。
 */
@Data
@TableName("md_route_step")
public class RouteStep implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long routeId;
    private String stepCode;
    private Integer stepSeq;
    private String segment;
    private Integer allowRework;
    private LocalDateTime createdTime;
}
