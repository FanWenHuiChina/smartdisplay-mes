package com.visionox.mes.order.service;

import com.visionox.mes.common.BusinessException;
import com.visionox.mes.order.entity.ProductionOrder;
import com.visionox.mes.order.mapper.ProductionOrderMapper;
import com.visionox.mes.system.service.AuditLogService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ErpOrderAdapterServiceTest {

    @Test
    void importOrdersShouldCreateOneThousandSimulatedOrders() {
        AtomicInteger insertCount = new AtomicInteger();
        List<ProductionOrder> samples = new ArrayList<>();
        RecordingAuditLogService auditLogService = new RecordingAuditLogService();
        ErpOrderAdapterService service = new ErpOrderAdapterService(mapper(List.of(), insertCount, samples), auditLogService);

        Map<String, Object> result = service.importOrders(Map.of(
                "batchNo", "ERP-BATCH-1000",
                "count", 1000,
                "orderPrefix", "MOERP-PERF",
                "plannedQty", 80,
                "lineCode", "LINE_01"
        ), "planner");

        assertThat(result.get("batchNo")).isEqualTo("ERP-BATCH-1000");
        assertThat(result.get("receivedCount")).isEqualTo(1000);
        assertThat(result.get("createdCount")).isEqualTo(1000);
        assertThat(result.get("skippedCount")).isEqualTo(0);
        assertThat(result.get("failedCount")).isEqualTo(0);
        assertThat(result.get("truncated")).isEqualTo(true);
        assertThat((List<?>) result.get("sampleOrderNos")).hasSize(20);

        assertThat(insertCount.get()).isEqualTo(1000);
        assertThat(samples).extracting(ProductionOrder::getOrderNo)
                .startsWith("MOERP-PERF-0001", "MOERP-PERF-0002", "MOERP-PERF-0003");
        assertThat(samples).allSatisfy(order -> {
            assertThat(order.getStatus()).isEqualTo("CREATED");
            assertThat(order.getCompletedQty()).isZero();
            assertThat(order.getPlannedQty()).isEqualTo(80);
            assertThat(order.getLineCode()).isEqualTo("LINE_01");
            assertThat(order.getCreatedBy()).isEqualTo("planner");
        });
        assertThat(auditLogService.action).isEqualTo("ERP_ORDER_IMPORT");
        assertThat(auditLogService.bizNo).isEqualTo("ERP-BATCH-1000");
        assertThat(auditLogService.bizType).isEqualTo("ERP_ADAPTER");
        assertThat(auditLogService.description).contains("created=1000");
        assertThat(auditLogService.operator).isEqualTo("planner");
        assertThat(auditLogService.source).isEqualTo("erp-adapter");
        assertThat(auditLogService.requestSnapshot).contains("createdCount");
    }

    @Test
    void importOrdersShouldSkipExistingOrderNos() {
        ProductionOrder existing = new ProductionOrder();
        existing.setOrderNo("MO-EXISTS");
        AtomicInteger insertCount = new AtomicInteger();
        ErpOrderAdapterService service = new ErpOrderAdapterService(
                mapper(List.of(existing), insertCount, new ArrayList<>()),
                new RecordingAuditLogService()
        );

        Map<String, Object> result = service.importOrders(Map.of(
                "batchNo", "ERP-BATCH-SKIP",
                "orders", List.of(Map.of("orderNo", "MO-EXISTS", "productCode", "AMOLED_65", "plannedQty", 100))
        ), "planner");

        assertThat(result.get("createdCount")).isEqualTo(0);
        assertThat(result.get("skippedCount")).isEqualTo(1);
        assertThat(insertCount.get()).isZero();
    }

    @Test
    void importOrdersShouldRejectMoreThanOneThousandOrders() {
        ErpOrderAdapterService service = new ErpOrderAdapterService(
                mapper(List.of(), new AtomicInteger(), new ArrayList<>()),
                new RecordingAuditLogService()
        );

        assertThatThrownBy(() -> service.importOrders(Map.of("count", 1001), "planner"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最多支持 1000 条工单");
    }

    private ProductionOrderMapper mapper(List<ProductionOrder> existingOrders,
                                         AtomicInteger insertCount,
                                         List<ProductionOrder> samples) {
        return (ProductionOrderMapper) Proxy.newProxyInstance(
                ProductionOrderMapper.class.getClassLoader(),
                new Class<?>[]{ProductionOrderMapper.class},
                (proxy, method, args) -> {
                    if ("selectList".equals(method.getName())) {
                        return existingOrders;
                    }
                    if ("insert".equals(method.getName())) {
                        ProductionOrder order = (ProductionOrder) args[0];
                        insertCount.incrementAndGet();
                        if (samples.size() < 3) {
                            samples.add(order);
                        }
                        return 1;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private Object defaultValue(Class<?> returnType) {
        if (returnType == Void.TYPE) {
            return null;
        }
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Integer.TYPE || returnType == Long.TYPE || returnType == Short.TYPE || returnType == Byte.TYPE) {
            return 0;
        }
        return null;
    }

    private static class RecordingAuditLogService extends AuditLogService {
        private String action;
        private String bizNo;
        private String bizType;
        private String description;
        private String operator;
        private String source;
        private String requestSnapshot;

        RecordingAuditLogService() {
            super(null);
        }

        @Override
        public void record(String action, String bizNo, String bizType, String description,
                           String operator, String source, String requestSnapshot) {
            this.action = action;
            this.bizNo = bizNo;
            this.bizType = bizType;
            this.description = description;
            this.operator = operator;
            this.source = source;
            this.requestSnapshot = requestSnapshot;
        }
    }
}
