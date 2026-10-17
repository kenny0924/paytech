package com.paytech.platform.support.configuration;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.paytech.platform.support.accesscontrol.service.processor.QueryGatewaySnapshotProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.request.QueryGatewaySnapshotRequest;
import com.paytech.platform.support.ext.rest.gatewayconfiguration.GatewaySnapshotConverter;

import lombok.AllArgsConstructor;

/**
 * Gateway Snapshot 数据链路就绪探针。
 */
@Component
@AllArgsConstructor
public class GatewaySnapshotHealthIndicator implements HealthIndicator {

    private final GatewaySnapshotConverter converter;
    private final QueryGatewaySnapshotProcessor processor;

    /** 查询、编译或响应转换失败时保持就绪状态为 DOWN。 */
    @Override
    public Health health() {
        try {
            // 就绪状态同时覆盖领域编译和实际 HTTP Snapshot 组装，任一失败均不可接流量。
            converter.convertResponse(processor.process(new QueryGatewaySnapshotRequest()));
            return Health.up().build();
        } catch (RuntimeException exception) {
            return Health.down().build();
        }
    }
}
