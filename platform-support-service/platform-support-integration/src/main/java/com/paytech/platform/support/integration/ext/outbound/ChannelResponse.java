package com.paytech.platform.support.integration.ext.outbound;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渠道响应：状态码、Header（同名多值以逗号连接，名称大小写不敏感）与原始响应体字节。 */
@Getter
@AllArgsConstructor
public class ChannelResponse {

    /** HTTP 状态码。 */
    private final int status;

    /** 响应 Header。 */
    private final Map<String, String> headers;

    /** 原始响应体，空响应为空数组。 */
    private final byte[] body;

    @Override
    public String toString() {
        return "ChannelResponse[status=" + status + "]";
    }
}
