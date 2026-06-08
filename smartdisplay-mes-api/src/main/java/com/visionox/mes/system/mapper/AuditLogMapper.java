package com.visionox.mes.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.visionox.mes.system.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审计日志 Mapper。
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
