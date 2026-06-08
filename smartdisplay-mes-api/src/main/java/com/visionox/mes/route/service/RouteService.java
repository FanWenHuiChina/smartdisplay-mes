package com.visionox.mes.route.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.visionox.mes.common.BusinessException;
import com.visionox.mes.route.entity.Route;
import com.visionox.mes.route.entity.RouteStep;
import com.visionox.mes.route.mapper.RouteMapper;
import com.visionox.mes.route.mapper.RouteStepMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工艺路线服务。
 */
@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteMapper routeMapper;
    private final RouteStepMapper routeStepMapper;

    public Route findActiveRoute(String productCode) {
        List<Route> routes = routeMapper.selectList(new LambdaQueryWrapper<Route>()
                .eq(Route::getProductCode, productCode)
                .eq(Route::getStatus, "ACTIVE")
                .orderByDesc(Route::getRouteVersion)
                .orderByDesc(Route::getEffectiveTime));
        if (routes.isEmpty()) {
            throw new BusinessException("未找到产品生效Route: product=" + productCode);
        }
        return routes.get(0);
    }

    public List<RouteStep> activeSteps(String productCode) {
        Route route = findActiveRoute(productCode);
        List<RouteStep> steps = routeStepMapper.selectList(new LambdaQueryWrapper<RouteStep>()
                .eq(RouteStep::getRouteId, route.getId())
                .orderByAsc(RouteStep::getStepSeq));
        if (steps.isEmpty()) {
            throw new BusinessException("生效Route未配置工序: route=" + route.getRouteCode());
        }
        return steps;
    }

    public List<String> activeStepCodes(String productCode) {
        return activeSteps(productCode).stream()
                .sorted(Comparator.comparing(RouteStep::getStepSeq))
                .map(RouteStep::getStepCode)
                .toList();
    }

    public List<Map<String, Object>> activeRouteSummaries() {
        List<Route> routes = routeMapper.selectList(new LambdaQueryWrapper<Route>()
                .eq(Route::getStatus, "ACTIVE")
                .orderByAsc(Route::getProductCode)
                .orderByDesc(Route::getRouteVersion));
        return routes.stream().map(route -> Map.<String, Object>of(
                "routeCode", route.getRouteCode(),
                "productCode", route.getProductCode(),
                "version", route.getRouteVersion(),
                "status", route.getStatus(),
                "steps", routeStepMapper.selectList(new LambdaQueryWrapper<RouteStep>()
                                .eq(RouteStep::getRouteId, route.getId())
                                .orderByAsc(RouteStep::getStepSeq))
                        .stream()
                        .map(RouteStep::getStepCode)
                        .collect(Collectors.toList())
        )).collect(Collectors.toList());
    }

    public void validateTrackInStep(String productCode, String expectedStepCode, String requestedStepCode) {
        if (requestedStepCode == null || requestedStepCode.isBlank()) {
            throw new BusinessException("Track In工序不能为空");
        }
        if (expectedStepCode == null || expectedStepCode.isBlank()) {
            throw new BusinessException("Lot当前工序为空，无法执行Route校验");
        }
        if (!expectedStepCode.equals(requestedStepCode)) {
            throw new BusinessException(String.format("Route防跳站校验失败: 当前待执行工序=%s, 请求工序=%s",
                    expectedStepCode, requestedStepCode));
        }
        List<String> stepCodes = activeStepCodes(productCode);
        if (!stepCodes.contains(requestedStepCode)) {
            throw new BusinessException(String.format("Route未包含请求工序: product=%s, step=%s",
                    productCode, requestedStepCode));
        }
    }
}
