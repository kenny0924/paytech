package com.paytech.platform.support.delivery.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 瞬时渲染结果，不持久化、不生成默认 toString。 */
@Getter
@RequiredArgsConstructor
public class NotificationMessage {

    /** 纯文本主题。 */
    private final String subject;

    /** 纯文本正文。 */
    private final String body;
}
