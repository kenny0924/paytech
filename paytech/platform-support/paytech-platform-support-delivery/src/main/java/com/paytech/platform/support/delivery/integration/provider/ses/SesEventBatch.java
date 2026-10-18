package com.paytech.platform.support.delivery.integration.provider.ses;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次 ReceiveMessage 的结果：成功时为消息列表，失败时为安全错误码。 */
@Getter
@AllArgsConstructor
public class SesEventBatch {

    /** 收到的消息，失败时为空列表。 */
    private final List<SesEventMessage> messages;

    /** 失败的安全错误码，成功时为空。 */
    private final String error;
}
