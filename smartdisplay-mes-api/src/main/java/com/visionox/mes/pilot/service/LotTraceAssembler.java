package com.visionox.mes.pilot.service;

import com.visionox.mes.lot.entity.Lot;
import com.visionox.mes.lot.entity.LotStepRecord;
import com.visionox.mes.lot.entity.SerialNumber;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lot 追溯结果的只读"塑形"逻辑：把已取数的追溯上下文聚合成影响面汇总与关联维度。
 *
 * <p>从 {@link PilotMesService} 抽出的纯函数读模型组装器——不依赖任何 Mapper / 数据范围 / 外部服务，
 * 只对入参做转换。因此可独立单测、无循环依赖：取数仍由 PilotMesService 负责，结果塑形交给本类。
 * 这是上帝类拆分的第一步样例（见《PilotMesService拆分设计说明》）。</p>
 */
@Component
public class LotTraceAssembler {

    /**
     * 影响面汇总：命中 Lot 数、Hold 数、SN 数、载具数、NG 检验数、物料批次数、缺陷码数、设备数。
     */
    public Map<String, Object> impactSummary(List<Map<String, Object>> matches, Map<String, Object> trace) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("matchedLotCount", matches.size());
        summary.put("holdLotCount", matches.stream().filter(row -> "HOLD".equals(row.get("status"))).count());
        summary.put("serialNumberCount", longValue(fieldValue(trace.get("serialNumberSummary"), "totalCount"), listValue(trace.get("serialNumbers")).size()));
        summary.put("carrierCount", listValue(trace.get("carriers")).size());
        summary.put("ngInspectionCount", listValue(trace.get("qualityRecords")).stream()
                .filter(row -> !"OK".equals(fieldText(row, "result")))
                .count());
        summary.put("materialBatchCount", distinctTextCount(listValue(trace.get("materialConsumptions")), "batchNo"));
        summary.put("defectCodeCount", distinctTextCount(listValue(trace.get("qualityRecords")), "defectCode"));
        summary.put("equipmentCount", distinctTextCount(listValue(trace.get("stepRecords")), "equipmentCode"));
        return summary;
    }

    /**
     * 关联维度：从命中 Lot 与追溯上下文抽取去重后的订单 / SN / 载具 / 设备 / 物料批次 / 缺陷码集合。
     */
    public Map<String, Object> relatedDimensions(List<Map<String, Object>> matches, Map<String, Object> trace) {
        Map<String, Object> dimensions = new LinkedHashMap<>();
        dimensions.put("orderNos", distinctTextValues(matches, "orderNo"));
        dimensions.put("serialNumbers", distinctTextValues(listValue(trace.get("serialNumbers")), "sn"));
        dimensions.put("carrierNos", distinctTextValues(listValue(trace.get("carriers")), "carrierNo"));
        dimensions.put("equipmentCodes", distinctTextValues(listValue(trace.get("stepRecords")), "equipmentCode"));
        dimensions.put("materialBatches", distinctTextValues(listValue(trace.get("materialConsumptions")), "batchNo"));
        dimensions.put("defectCodes", distinctTextValues(listValue(trace.get("qualityRecords")), "defectCode"));
        return dimensions;
    }

    private List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private int distinctTextCount(List<?> rows, String fieldName) {
        return distinctTextValues(rows, fieldName).size();
    }

    private List<String> distinctTextValues(List<?> rows, String fieldName) {
        Map<String, Boolean> values = new LinkedHashMap<>();
        for (Object row : rows) {
            String text = fieldText(row, fieldName);
            if (!text.isBlank()) {
                values.put(text, true);
            }
        }
        return new ArrayList<>(values.keySet());
    }

    private String fieldText(Object row, String fieldName) {
        if (row instanceof Map<?, ?> map) {
            return objectText(map.get(fieldName), "");
        }
        if (row instanceof LotStepRecord record) {
            return switch (fieldName) {
                case "lotNo" -> objectText(record.getLotNo(), "");
                case "stepCode" -> objectText(record.getStepCode(), "");
                case "equipmentCode" -> objectText(record.getEquipmentCode(), "");
                case "recipeCode" -> objectText(record.getRecipeCode(), "");
                case "result" -> objectText(record.getResult(), "");
                default -> "";
            };
        }
        if (row instanceof Lot lot) {
            return switch (fieldName) {
                case "lotNo" -> objectText(lot.getLotNo(), "");
                case "orderNo" -> objectText(lot.getOrderNo(), "");
                case "productCode" -> objectText(lot.getProductCode(), "");
                case "status" -> objectText(lot.getStatus(), "");
                default -> "";
            };
        }
        if (row instanceof SerialNumber serialNumber) {
            return switch (fieldName) {
                case "sn" -> objectText(serialNumber.getSn(), "");
                case "lotNo" -> objectText(serialNumber.getLotNo(), "");
                case "orderNo" -> objectText(serialNumber.getOrderNo(), "");
                case "productCode" -> objectText(serialNumber.getProductCode(), "");
                case "status" -> objectText(serialNumber.getStatus(), "");
                default -> "";
            };
        }
        return "";
    }

    private Object fieldValue(Object row, String fieldName) {
        if (row instanceof Map<?, ?> map) {
            return map.get(fieldName);
        }
        return null;
    }

    // 以下两个为 PilotMesService 同名纯工具方法的副本，复制以保持本组装器零依赖（纯函数，无发散风险）。
    private long longValue(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String objectText(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }
}
