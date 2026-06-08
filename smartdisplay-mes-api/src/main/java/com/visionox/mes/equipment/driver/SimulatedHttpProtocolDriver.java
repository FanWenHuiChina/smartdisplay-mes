package com.visionox.mes.equipment.driver;

import org.springframework.stereotype.Component;

@Component
public class SimulatedHttpProtocolDriver extends AbstractEapProtocolDriver {

    @Override
    public String protocolType() {
        return "SIMULATED_HTTP";
    }

    @Override
    public String driverCode() {
        return "simulated-http-driver";
    }

    @Override
    protected String description() {
        return "试点模拟 HTTP 驱动，用于开发环境和演示环境标准消息入站。";
    }
}
