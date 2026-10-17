package com.paytech.platform.support.delivery.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 供应商投递事件给出的结果；同一尝试上优先级只升不降，硬退信与拒发同级且为最终结果。 */
@Getter
@RequiredArgsConstructor
public enum NotificationEventOutcome implements CodeEnum {

    /** 软退信：供应商仍可能重试，投递状态不变。 */
    SOFT_BOUNCE("SOFT_BOUNCE", 1),

    /** 已送达收件服务器。 */
    DELIVERED("DELIVERED", 2),

    /** 硬退信：地址不可投递，含命中供应商抑制列表。 */
    HARD_BOUNCE("HARD_BOUNCE", 3),

    /** 供应商受理后拒发，如内容含病毒。 */
    REJECTED("REJECTED", 3);

    private final String code;

    /** 覆盖优先级，数值大者覆盖小者。 */
    private final int priority;

    /** 是否应覆盖尝试上已记录的结果；同级保留先到的结果。 */
    public boolean overrides(NotificationEventOutcome current) {
        return current == null || priority > current.priority;
    }

    /** 是否表示收件地址最终不可投递。 */
    public boolean undeliverable() {
        return this == HARD_BOUNCE || this == REJECTED;
    }
}
