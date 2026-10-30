package com.paytech.payout.app.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** payout-service 进程级 Snowflake 标识配置；每个 app 独立分配 Worker。 */
@Getter
@AllArgsConstructor
@ConfigurationProperties("payout.identifier")
public class PayoutIdentifierProperties {
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
