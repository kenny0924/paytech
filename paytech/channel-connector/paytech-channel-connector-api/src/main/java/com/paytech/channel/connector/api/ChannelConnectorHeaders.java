package com.paytech.channel.connector.api;

/** channel-connector 内部 HTTP 合同共用的请求头名称。 */
public final class ChannelConnectorHeaders {
    /**
     * 操作者 Account ID 请求头
     * <li>由 Gateway 写入、Operations BFF 透传；规范正十进制，所有渠道配置写命令必填，查询不带。</li>
     */
    public static final String ACTOR_HEADER = "X-Gateway-Actor-Subject";

    /**
     * 调用方服务名请求头
     * <li>通用执行入口识别调用方：携带已验证内部 Token 时以 Token 调用方为准，两者不一致 400；internal-auth 关闭时信任本头（过渡信任，部署须限制为私网）。</li>
     */
    public static final String CALLER_HEADER = "X-Caller-Service";

    /** 常量类不实例化。 */
    private ChannelConnectorHeaders() {
    }
}
