package com.paytech.channel.connector.api.channel.response;

import lombok.Getter;
import lombok.Setter;

/** 渠道在本环境的一个出站地址。 */
@Getter
@Setter
public class ChannelEndpointDetail {
    /** 地址代码，渠道内唯一 */
    private String endpointCode;

    /** 基础地址 */
    private String baseUrl;

    /** 是否沙箱地址；只有沙箱地址允许沙箱验证运行 */
    private boolean sandbox;
}
