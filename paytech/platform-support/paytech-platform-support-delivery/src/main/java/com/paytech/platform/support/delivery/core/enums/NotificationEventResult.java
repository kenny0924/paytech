package com.paytech.platform.support.delivery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 单条供应商投递事件的处理结果；决定是否从队列删除消息，并作为指标标签。 */
@Getter
@RequiredArgsConstructor
public enum NotificationEventResult implements CodeEnum {

    /** 已更新尝试或投递，删除消息。 */
    APPLIED("APPLIED", true),

    /** 重复或低优先级事件，事实不变，删除消息。 */
    DUPLICATE("DUPLICATE", true),

    /** 匹配不到发送尝试（即时邮件、测试发送或其他来源），只计数并删除消息。 */
    UNMATCHED("UNMATCHED", true),

    /** 不处理的事件类型，删除消息。 */
    IGNORED("IGNORED", true),

    /** 消息无法解析，保留在队列中由死信队列兜底。 */
    INVALID("INVALID", false),

    /** 尝试仍在发送中或本地并发冲突，保留消息等待重新可见。 */
    DEFERRED("DEFERRED", false);

    private final String code;

    /** 处理后是否从队列删除该消息。 */
    private final boolean consumed;
}
