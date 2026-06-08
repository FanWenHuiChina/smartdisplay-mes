package com.visionox.mes.equipment.adapter;

import java.util.Map;

/**
 * EAP 接入边界，后续可替换为 SECS/GEM、OPC UA 或厂商 HTTP 网关实现。
 */
public interface EapAdapter {

    String adapterCode();

    Map<String, Object> handleMessage(Map<String, Object> request);

    Map<String, Object> reportStatus(Map<String, Object> request);

    Map<String, Object> reportCycleSample(Map<String, Object> request);

    Map<String, Object> reportParameters(Map<String, Object> request);

    Map<String, Object> downloadRecipe(Map<String, Object> request);
}
