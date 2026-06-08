package com.visionox.mes.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.visionox.mes.order.entity.ProductionOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 生产工单 Mapper。
 */
@Mapper
public interface ProductionOrderMapper extends BaseMapper<ProductionOrder> {
}
