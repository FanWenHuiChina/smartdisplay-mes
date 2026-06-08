package com.visionox.mes.material.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("material_inventory_txn")
public class MaterialInventoryTxn implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String txnNo;
    private String txnType;
    private String materialCode;
    private String materialName;
    private String batchNo;
    private String supplierCode;
    private BigDecimal qtyDelta;
    private BigDecimal availableBefore;
    private BigDecimal availableAfter;
    private BigDecimal frozenBefore;
    private BigDecimal frozenAfter;
    private BigDecimal reservedBefore;
    private BigDecimal reservedAfter;
    private BigDecimal countedQty;
    private String unit;
    private String reason;
    private String sourceSystem;
    private String operator;
    private LocalDateTime txnTime;
    private String requestSnapshot;
    private LocalDateTime createdTime;
}
