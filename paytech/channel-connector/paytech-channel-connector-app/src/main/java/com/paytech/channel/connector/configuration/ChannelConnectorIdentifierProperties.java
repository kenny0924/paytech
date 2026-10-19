package com.paytech.channel.connector.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** channel-connector 进程级 Snowflake 标识配置（channel-connector.identifier）；每个 app 独立分配 Worker。 */
@Getter
@AllArgsConstructor
@ConfigurationProperties("channel-connector.identifier")
public class ChannelConnectorIdentifierProperties {
    /**
     * 部署分配的 Region 标识
     * <li>必填，无默认值；同一 Region/Worker 组合在全部运行实例中唯一；修改需重启。</li>
     */
    private final Long regionId;

    /**
     * 部署分配的 Worker 标识
     * <li>必填，无默认值；多实例部署时每个实例不同；修改需重启。</li>
     */
    private final Long workerId;
}
