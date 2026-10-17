package com.paytech.platform.support.integration.ext.outbound;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 发往渠道的最终请求：地址、方法、Header 与已编码的请求体字节；Header 可能含凭据，不得记录。 */
@Getter
@AllArgsConstructor
public class ChannelRequest {

    /** 完整地址（基础地址 + 路径 + 查询串）。 */
    private final URI uri;

    /** HTTP 方法。 */
    private final String method;

    /** Header，名称大小写不敏感。 */
    private final Map<String, String> headers;

    /** 请求体字节；无请求体为 null。 */
    private final byte[] body;

    /** 连接超时。 */
    private final Duration connectTimeout;

    /** 等待响应超时。 */
    private final Duration responseTimeout;

    @Override
    public String toString() {
        return "ChannelRequest[redacted]";
    }
}
