package com.paytech.channel.connector.service.processor.request;

import java.util.Map;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/** 一条渠道支付结果通知：路径中的渠道与账号、请求头与原始 Body；原文不得进入日志。 */
@Getter
@Setter
public class ReceiveChannelCallbackRequest {
    /** 渠道编码。 */
    @NotNull
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}")
    private String channelCode;

    /** 渠道技术账号 ID。 */
    @NotNull
    @Positive
    private Long channelAccountId;

    /** 请求头，键为小写名称，多值取第一个。 */
    @NotNull
    private Map<String, String> headers;

    /** 原始 Body 字节。 */
    @NotNull
    private byte[] body;

    @Override
    public String toString() {
        return "ReceiveChannelCallbackRequest[" + channelCode + ", " + channelAccountId + ", values redacted]";
    }
}
