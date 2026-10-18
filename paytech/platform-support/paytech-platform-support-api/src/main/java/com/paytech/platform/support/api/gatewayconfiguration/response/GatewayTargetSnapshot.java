package com.paytech.platform.support.api.gatewayconfiguration.response;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Gateway 路由引用的当前有效服务目标。 */
public final class GatewayTargetSnapshot {

    /**
     * 稳定目标编码。
     */
    private final String targetServiceCode;

    /**
     * 目标所属公共入口 Channel 编码。
     * <li>取值为已发布的 GatewayRouteChannel Code。</li>
     */
    private final String channel;

    /**
     * 服务发现名称。
     * <li>由 Platform 数据库配置，可与目标编码不同；不承载 URL、IP 或端口。</li>
     */
    private final String discoveryServiceName;

    /** 绑定目标编码、Channel 和服务发现身份。 */
    @JsonCreator
    public GatewayTargetSnapshot(@JsonProperty("targetServiceCode") String targetServiceCode,
        @JsonProperty("channel") String channel,
        @JsonProperty("discoveryServiceName") String discoveryServiceName) {
        this.targetServiceCode = targetServiceCode;
        this.channel = channel;
        this.discoveryServiceName = discoveryServiceName;
    }

    /** 返回稳定目标编码。 */
    public String getTargetServiceCode() {
        return targetServiceCode;
    }

    /** 返回目标所属公共入口 Channel 编码。 */
    public String getChannel() {
        return channel;
    }

    /** 返回服务发现名称。 */
    public String getDiscoveryServiceName() {
        return discoveryServiceName;
    }

    /** 比较完整快照字段，保持原契约的值相等语义。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GatewayTargetSnapshot value)) {
            return false;
        }
        return Objects.equals(targetServiceCode, value.targetServiceCode)
            && Objects.equals(channel, value.channel)
            && Objects.equals(discoveryServiceName, value.discoveryServiceName);
    }

    /** 根据完整快照字段生成与值相等语义一致的哈希值。 */
    @Override
    public int hashCode() {
        return Objects.hash(targetServiceCode, channel, discoveryServiceName);
    }
}
