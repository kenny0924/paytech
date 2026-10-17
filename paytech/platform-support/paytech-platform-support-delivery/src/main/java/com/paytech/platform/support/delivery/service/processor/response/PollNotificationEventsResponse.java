package com.paytech.platform.support.delivery.service.processor.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/** 一个账号一轮拉取的结果摘要。 */
@Getter
@ToString
@AllArgsConstructor
public class PollNotificationEventsResponse {

    /** 本轮收到的消息数。 */
    private final int received;

    /** 本轮失败的安全错误码；成功或账号无需拉取时为空。 */
    private final String error;
}
