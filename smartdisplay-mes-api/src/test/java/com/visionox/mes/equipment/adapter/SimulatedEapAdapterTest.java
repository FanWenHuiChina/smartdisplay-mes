package com.visionox.mes.equipment.adapter;

import com.visionox.mes.common.BusinessException;
import com.visionox.mes.equipment.service.EquipmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulatedEapAdapterTest {

    @Mock
    private EquipmentService equipmentService;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void handleMessageShouldDispatchStatusPayloadWithEnvelopeMetadata() {
        SimulatedEapAdapter adapter = new SimulatedEapAdapter(equipmentService);
        when(equipmentService.reportStatus(any())).thenReturn(Map.of("ok", true));

        Map<String, Object> response = adapter.handleMessage(Map.of(
                "messageType", "equipment-status",
                "operator", "ee1001",
                "correlationId", "MSG-001",
                "payload", Map.of(
                        "equipmentCode", "COATER_01",
                        "status", "RUNNING"
                )
        ));

        Map<String, Object> result = (Map<String, Object>) response.get("result");
        assertThat(response.get("adapterCode")).isEqualTo("simulated-eap-adapter");
        assertThat(response.get("protocol")).isEqualTo("SIMULATED_HTTP");
        assertThat(response.get("messageType")).isEqualTo("STATUS");
        assertThat(result.get("ok")).isEqualTo(true);

        ArgumentCaptor<Map<String, Object>> requestCaptor = ArgumentCaptor.forClass(Map.class);
        verify(equipmentService).reportStatus(requestCaptor.capture());
        assertThat(requestCaptor.getValue())
                .containsEntry("equipmentCode", "COATER_01")
                .containsEntry("status", "RUNNING")
                .containsEntry("operator", "ee1001")
                .containsEntry("correlationId", "MSG-001")
                .containsEntry("sourceSystem", "eap-adapter")
                .containsEntry("adapterCode", "simulated-eap-adapter");
    }

    @Test
    void handleMessageShouldDispatchRecipeDownloadAlias() {
        SimulatedEapAdapter adapter = new SimulatedEapAdapter(equipmentService);
        when(equipmentService.downloadRecipe(any())).thenReturn(Map.of("command", Map.of("commandNo", "RDL001")));

        Map<String, Object> response = adapter.handleMessage(Map.of(
                "type", "recipe",
                "payload", Map.of(
                        "equipmentCode", "COATER_01",
                        "recipeCode", "RCP_COAT_001"
                )
        ));

        assertThat(response.get("messageType")).isEqualTo("RECIPE_DOWNLOAD");
        verify(equipmentService).downloadRecipe(any());
    }

    @Test
    void handleMessageShouldRejectUnsupportedMessageType() {
        SimulatedEapAdapter adapter = new SimulatedEapAdapter(equipmentService);

        assertThatThrownBy(() -> adapter.handleMessage(Map.of("messageType", "unknown")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Unsupported EAP messageType");
    }
}
