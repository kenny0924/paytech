package com.paytech.platform.support.delivery.service.processor.request;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/** 拉取一个发送账号事件队列中的投递事件。 */
@Getter
@ToString
@AllArgsConstructor
public class PollNotificationEventsRequest {

    /** 发送账号身份。 */
    @NotNull
    private final Long providerId;
}
