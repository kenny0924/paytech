package com.paytech.platform.support.localization.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 发布文案变化类型（t_i18n_release_change.change_type）：相对同一应用上一发布号的变化。 */
@Getter
@RequiredArgsConstructor
public enum ReleaseChangeType implements CodeEnum {

    /** 上一发布没有该文案。 */
    ADDED("ADDED"),

    /** 文字与上一发布不同。 */
    CHANGED("CHANGED"),

    /** 本发布不再包含该文案。 */
    REMOVED("REMOVED");

    private final String code;
}
