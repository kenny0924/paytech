package com.paytech.channel.connector.api;

/** channel-connector 内部 HTTP 合同共用的请求头名称。 */
public final class ChannelConnectorHeaders {
    /**
     * 操作者 Account ID 请求头
     * <li>由 Gateway 写入、Operations BFF 透传；规范正十进制，所有渠道配置写命令必填，查询不带。</li>
     */
    public static final String ACTOR_HEADER = "X-Gateway-Actor-Subject";

    /** 常量类不实例化。 */
    private ChannelConnectorHeaders() {
    }
}
