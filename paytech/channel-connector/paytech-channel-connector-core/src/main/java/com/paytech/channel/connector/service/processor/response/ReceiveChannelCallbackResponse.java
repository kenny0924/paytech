package com.paytech.channel.connector.service.processor.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渠道通知的成功应答：取自通知定义的媒体类型与 Body。 */
@Getter
@AllArgsConstructor
public class ReceiveChannelCallbackResponse {
    /** 应答媒体类型。 */
    private final String contentType;

    /** 应答 Body。 */
    private final String body;
}
