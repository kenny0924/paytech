package com.paytech.channel.connector.protocol.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渠道在本环境的一个出站地址；序列化后保存在 t_channel.endpoints。 */
@Getter
@AllArgsConstructor
public class EndpointDefinition {

    /** 渠道内唯一的地址代码。 */
    private final String endpointCode;

    /** https 基础地址，可带路径前缀。 */
    private final String baseUrl;

    /** 是否沙箱地址；只有沙箱地址允许沙箱验证运行。 */
    private final boolean sandbox;

    /** 非沙箱地址。 */
    public EndpointDefinition(String endpointCode, String baseUrl) {
        this(endpointCode, baseUrl, false);
    }
}
