package com.visionox.mes.order.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.visionox.mes.common.BusinessException;
import com.visionox.mes.order.entity.ProductionOrder;
import com.visionox.mes.order.mapper.ProductionOrderMapper;
import com.visionox.mes.system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * ERP模拟适配器：接收外部工单下发并落MES工单。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ErpOrderAdapterService {

    private static final DateTimeFormatter NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final AtomicLong NO_COUNTER = new AtomicLong();
    private static final int MAX_IMPORT_COUNT = 1000;
    private static final int SAMPLE_LIMIT = 20;

    private final ProductionOrderMapper orderMapper;
    private final AuditLogService auditLogService;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importOrders(Map<String, Object> request, String operator) {
        Map<String, Object> safeRequest = request == null ? Map.of() : request;
        String batchNo = text(safeRequest, "batchNo", nextNo("ERP-ORDER-BATCH"));
        String currentOperator = valueOr(operator, "erp-adapter");
        List<Map<String, Object>> rows = normalizeRows(safeRequest);
        List<String> orderNos = resolveOrderNos(rows, safeRequest);
        Set<String> existingOrderNos = existingOrderNos(orderNos);
        Set<String> importedOrderNos = new HashSet<>();

        int createdCount = 0;
        int skippedCount = 0;
        List<String> sampleOrderNos = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            String orderNo = orderNos.get(i);
            if (existingOrderNos.contains(orderNo) || !importedOrderNos.add(orderNo)) {
                skippedCount++;
                continue;
            }
            ProductionOrder order = new ProductionOrder();
            order.setOrderNo(orderNo);
            order.setProductCode(text(row, "productCode", text(safeRequest, "productCode", "AMOLED_65")));
            order.setProductName(text(row, "productName", defaultProductName(order.getProductCode())));
            order.setPlannedQty(positiveInt(value(row, "plannedQty"), positiveInt(value(safeRequest, "plannedQty"), 100)));
            order.setCompletedQty(0);
            order.setPriority(nonNegativeInt(value(row, "priority"), nonNegativeInt(value(safeRequest, "priority"), 0)));
            order.setLineCode(text(row, "lineCode", text(safeRequest, "lineCode", "LINE_01")));
            order.setStatus("CREATED");
            order.setCreatedBy(currentOperator);
            order.setCreatedTime(now);
            order.setUpdatedTime(now);
            orderMapper.insert(order);
            createdCount++;
            if (sampleOrderNos.size() < SAMPLE_LIMIT) {
                sampleOrderNos.add(orderNo);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchNo", batchNo);
        result.put("sourceSystem", text(safeRequest, "sourceSystem", "SIMULATED_ERP"));
        result.put("receivedCount", rows.size());
        result.put("createdCount", createdCount);
        result.put("skippedCount", skippedCount);
        result.put("failedCount", 0);
        result.put("sampleLimit", SAMPLE_LIMIT);
        result.put("sampleOrderNos", sampleOrderNos);
        result.put("truncated", createdCount > SAMPLE_LIMIT);
        result.put("status", "COMPLETED");
        result.put("createdTime", now);

        auditLogService.record("ERP_ORDER_IMPORT", batchNo, "ERP_ADAPTER",
                "ERP模拟工单导入: received=" + rows.size() + ", created=" + createdCount + ", skipped=" + skippedCount,
                currentOperator, "erp-adapter", JSONUtil.toJsonStr(result));
        return result;
    }

    private List<Map<String, Object>> normalizeRows(Map<String, Object> request) {
        Object orders = value(request, "orders");
        if (orders instanceof Collection<?> collection) {
            if (collection.isEmpty()) {
                throw new BusinessException("ERP工单列表不能为空");
            }
            if (collection.size() > MAX_IMPORT_COUNT) {
                throw new BusinessException("ERP单次导入最多支持 " + MAX_IMPORT_COUNT + " 条工单");
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object item : collection) {
                if (item instanceof Map<?, ?> map) {
                    rows.add(toStringKeyMap(map));
                } else {
                    throw new BusinessException("ERP工单明细必须是对象结构");
                }
            }
            return rows;
        }

        int count = positiveInt(value(request, "count"), 1);
        if (count > MAX_IMPORT_COUNT) {
            throw new BusinessException("ERP单次导入最多支持 " + MAX_IMPORT_COUNT + " 条工单");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            rows.add(Map.of(
                    "orderNo", generatedOrderNo(request, i),
                    "productCode", text(request, "productCode", productCodeBySeq(i)),
                    "productName", defaultProductName(text(request, "productCode", productCodeBySeq(i))),
                    "plannedQty", positiveInt(value(request, "plannedQty"), 100),
                    "priority", nonNegativeInt(value(request, "priority"), i % 5),
                    "lineCode", text(request, "lineCode", "LINE_01")
            ));
        }
        return rows;
    }

    private Map<String, Object> toStringKeyMap(Map<?, ?> source) {
        Map<String, Object> row = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null) {
                row.put(String.valueOf(key), value);
            }
        });
        return row;
    }

    private List<String> resolveOrderNos(List<Map<String, Object>> rows, Map<String, Object> request) {
        List<String> orderNos = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            orderNos.add(text(rows.get(i), "orderNo", generatedOrderNo(request, i + 1)));
        }
        return orderNos;
    }

    private Set<String> existingOrderNos(List<String> orderNos) {
        Set<String> distinctOrderNos = orderNos.stream()
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (distinctOrderNos.isEmpty()) {
            return Set.of();
        }
        return orderMapper.selectList(new QueryWrapper<ProductionOrder>()
                        .select("order_no")
                        .in("order_no", distinctOrderNos))
                .stream()
                .map(ProductionOrder::getOrderNo)
                .collect(Collectors.toSet());
    }

    private String generatedOrderNo(Map<String, Object> request, int seq) {
        String prefix = text(request, "orderPrefix", "MOERP" + NO_TIME.format(LocalDateTime.now()));
        return prefix.replaceAll("[^A-Za-z0-9_-]+", "").toUpperCase(Locale.ROOT) + "-" + String.format("%04d", seq);
    }

    private String nextNo(String prefix) {
        long seq = NO_COUNTER.updateAndGet(value -> value >= 9999 ? 1 : value + 1);
        return prefix + "-" + NO_TIME.format(LocalDateTime.now()) + "-" + String.format("%04d", seq);
    }

    private String productCodeBySeq(int seq) {
        return switch (Math.floorMod(seq - 1, 3)) {
            case 0 -> "AMOLED_65";
            case 1 -> "AMOLED_67";
            default -> "FOLD_78";
        };
    }

    private String defaultProductName(String productCode) {
        return switch (valueOr(productCode, "AMOLED_65")) {
            case "AMOLED_67" -> "AMOLED 6.7寸柔性屏";
            case "FOLD_78" -> "7.8寸折叠模组";
            default -> "AMOLED 6.5寸柔性屏";
        };
    }

    private Object value(Map<String, Object> request, String key) {
        return request == null ? null : request.get(key);
    }

    private String text(Map<String, Object> request, String key, String defaultValue) {
        Object value = value(request, key);
        return value == null || String.valueOf(value).isBlank() ? defaultValue : String.valueOf(value);
    }

    private int positiveInt(Object value, int defaultValue) {
        int parsed = intValue(value, defaultValue);
        return parsed <= 0 ? defaultValue : parsed;
    }

    private int nonNegativeInt(Object value, int defaultValue) {
        return Math.max(0, intValue(value, defaultValue));
    }

    private int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
