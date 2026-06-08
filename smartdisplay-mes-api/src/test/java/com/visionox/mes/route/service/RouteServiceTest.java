package com.visionox.mes.route.service;

import com.visionox.mes.common.BusinessException;
import com.visionox.mes.route.entity.Route;
import com.visionox.mes.route.entity.RouteStep;
import com.visionox.mes.route.mapper.RouteMapper;
import com.visionox.mes.route.mapper.RouteStepMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

    @Mock
    private RouteMapper routeMapper;

    @Mock
    private RouteStepMapper routeStepMapper;

    @InjectMocks
    private RouteService routeService;

    @Test
    void findActiveRouteShouldRejectMissingActiveRoute() {
        when(routeMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> routeService.findActiveRoute("OLED_PANEL"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OLED_PANEL");

        verify(routeStepMapper, never()).selectList(any());
    }

    @Test
    void activeStepsShouldRejectRouteWithoutSteps() {
        when(routeMapper.selectList(any())).thenReturn(List.of(route(10L, "RTE-OLED-V2", "V2")));
        when(routeStepMapper.selectList(any())).thenReturn(List.of());

        assertThatThrownBy(() -> routeService.activeSteps("OLED_PANEL"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("RTE-OLED-V2");
    }

    @Test
    void activeStepCodesShouldReturnConfiguredRouteStepOrder() {
        when(routeMapper.selectList(any())).thenReturn(List.of(route(10L, "RTE-OLED-V2", "V2")));
        when(routeStepMapper.selectList(any())).thenReturn(List.of(
                step(10L, "COATING", 20),
                step(10L, "CLEAN", 10),
                step(10L, "EXPOSURE", 30)
        ));

        List<String> steps = routeService.activeStepCodes("OLED_PANEL");

        assertThat(steps).containsExactly("CLEAN", "COATING", "EXPOSURE");
    }

    @Test
    void validateTrackInStepShouldRejectBlankRequestedStep() {
        assertThatThrownBy(() -> routeService.validateTrackInStep("OLED_PANEL", "COATING", " "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Track In");

        verify(routeMapper, never()).selectList(any());
    }

    @Test
    void validateTrackInStepShouldRejectMissingLotCurrentStep() {
        assertThatThrownBy(() -> routeService.validateTrackInStep("OLED_PANEL", null, "COATING"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Lot");

        verify(routeMapper, never()).selectList(any());
    }

    @Test
    void validateTrackInStepShouldRejectSkipStepBeforeRouteLookup() {
        assertThatThrownBy(() -> routeService.validateTrackInStep("OLED_PANEL", "COATING", "EXPOSURE"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("COATING")
                .hasMessageContaining("EXPOSURE");

        verify(routeMapper, never()).selectList(any());
    }

    @Test
    void validateTrackInStepShouldRejectStepNotConfiguredInActiveRoute() {
        when(routeMapper.selectList(any())).thenReturn(List.of(route(10L, "RTE-OLED-V2", "V2")));
        when(routeStepMapper.selectList(any())).thenReturn(List.of(step(10L, "CLEAN", 10)));

        assertThatThrownBy(() -> routeService.validateTrackInStep("OLED_PANEL", "COATING", "COATING"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OLED_PANEL")
                .hasMessageContaining("COATING");
    }

    @Test
    void validateTrackInStepShouldPassWhenLotStepMatchesActiveRoute() {
        when(routeMapper.selectList(any())).thenReturn(List.of(route(10L, "RTE-OLED-V2", "V2")));
        when(routeStepMapper.selectList(any())).thenReturn(List.of(
                step(10L, "CLEAN", 10),
                step(10L, "COATING", 20)
        ));

        assertThatCode(() -> routeService.validateTrackInStep("OLED_PANEL", "COATING", "COATING"))
                .doesNotThrowAnyException();
    }

    private Route route(Long id, String routeCode, String version) {
        Route route = new Route();
        route.setId(id);
        route.setRouteCode(routeCode);
        route.setProductCode("OLED_PANEL");
        route.setRouteVersion(version);
        route.setStatus("ACTIVE");
        return route;
    }

    private RouteStep step(Long routeId, String stepCode, int seq) {
        RouteStep step = new RouteStep();
        step.setRouteId(routeId);
        step.setStepCode(stepCode);
        step.setStepSeq(seq);
        return step;
    }
}
