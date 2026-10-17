package com.paytech.reconciliation.app.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 对账服务（reconciliation-service）进程级 Snowflake 标识配置；每个 app 独立分配 Worker（01 §3）。 */
@Getter
@AllArgsConstructor
@ConfigurationProperties("reconciliation.identifier")
public class ReconciliationIdentifierProperties {
    /**
     * 部署分配的 Region 标识
     * <li>必填；同一 Region/Worker 组合在全部运行实例中唯一。</li>
     */
    private final Long regionId;

    /**
     * 部署分配的 Worker 标识
     * <li>必填；多实例部署时每个实例不同。</li>
     */
    private final Long workerId;
}
