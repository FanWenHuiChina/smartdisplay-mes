package com.visionox.mes.pilot.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the extracted pure trace-shaping logic. Previously this lived inline in
 * PilotMesService and was only exercised end-to-end; pulling it into a dependency-free
 * component makes it directly unit-testable.
 */
class LotTraceAssemblerTest {

    private final LotTraceAssembler assembler = new LotTraceAssembler();

    @Test
    void impactSummaryCountsAcrossDimensions() {
        List<Map<String, Object>> matches = List.of(
                Map.of("lotNo", "L1", "orderNo", "MO1", "status", "HOLD"),
                Map.of("lotNo", "L2", "orderNo", "MO1", "status", "PROCESSING"));
        Map<String, Object> trace = Map.of(
                "serialNumberSummary", Map.of("totalCount", 120L),
                "serialNumbers", List.of(Map.of("sn", "S1")),
                "carriers", List.of(Map.of("carrierNo", "C1"), Map.of("carrierNo", "C2")),
                "qualityRecords", List.of(
                        Map.of("result", "OK", "defectCode", ""),
                        Map.of("result", "NG", "defectCode", "D-MURA")),
                "materialConsumptions", List.of(
                        Map.of("batchNo", "B1"), Map.of("batchNo", "B1"), Map.of("batchNo", "B2")),
                "stepRecords", List.of(Map.of("equipmentCode", "E1"), Map.of("equipmentCode", "E2")));

        Map<String, Object> summary = assembler.impactSummary(matches, trace);

        assertThat(summary.get("matchedLotCount")).isEqualTo(2);
        assertThat(summary.get("holdLotCount")).isEqualTo(1L);
        assertThat(summary.get("serialNumberCount")).isEqualTo(120L);
        assertThat(summary.get("carrierCount")).isEqualTo(2);
        assertThat(summary.get("ngInspectionCount")).isEqualTo(1L);
        assertThat(summary.get("materialBatchCount")).isEqualTo(2);
        assertThat(summary.get("defectCodeCount")).isEqualTo(1);
        assertThat(summary.get("equipmentCount")).isEqualTo(2);
    }

    @Test
    void serialNumberCountFallsBackToReturnedListWhenSummaryMissing() {
        Map<String, Object> trace = Map.of(
                "serialNumbers", List.of(Map.of("sn", "S1"), Map.of("sn", "S2")),
                "carriers", List.of(),
                "qualityRecords", List.of(),
                "materialConsumptions", List.of(),
                "stepRecords", List.of());

        Map<String, Object> summary = assembler.impactSummary(List.of(), trace);

        assertThat(summary.get("serialNumberCount")).isEqualTo(2L);
        assertThat(summary.get("matchedLotCount")).isEqualTo(0);
    }

    @Test
    void relatedDimensionsDeduplicatesAndSkipsBlanks() {
        List<Map<String, Object>> matches = List.of(
                Map.of("orderNo", "MO1"), Map.of("orderNo", "MO1"), Map.of("orderNo", "MO2"));
        Map<String, Object> trace = Map.of(
                "serialNumbers", List.of(Map.of("sn", "S1"), Map.of("sn", "S1")),
                "carriers", List.of(Map.of("carrierNo", "C1")),
                "stepRecords", List.of(Map.of("equipmentCode", "E1")),
                "materialConsumptions", List.of(Map.of("batchNo", "B1")),
                "qualityRecords", List.of(Map.of("defectCode", "D1"), Map.of("defectCode", "")));

        Map<String, Object> dimensions = assembler.relatedDimensions(matches, trace);

        assertThat((List<String>) dimensions.get("orderNos")).containsExactly("MO1", "MO2");
        assertThat((List<String>) dimensions.get("serialNumbers")).containsExactly("S1");
        assertThat((List<String>) dimensions.get("defectCodes")).containsExactly("D1");
    }
}
