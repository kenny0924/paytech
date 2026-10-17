package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import com.paytech.platform.support.api.enums.GatewayRouteChannel;
import com.paytech.platform.support.accesscontrol.domain.enums.GatewayConfigurationStatus;

/**
 * Gateway Target 查询投影。
 */
public record GatewayTarget(
    /**
     * Gateway Target 的 Snowflake 主键。
     */
    Long targetId,

    /**
     * 与 Gateway Approved Target Registry 对齐的稳定目标编码。
     */
    String targetServiceCode,

    /**
     * 由运行时服务发现解析的目标服务名称，不是任意网络地址。
     */
    String discoveryServiceName,

    /**
     * 该目标允许承载的外部请求渠道。
     */
    GatewayRouteChannel channel,

    /**
     * 目标的当前启停状态；停用后其 Route 不进入 Snapshot。
     */
    GatewayConfigurationStatus status) {
}
