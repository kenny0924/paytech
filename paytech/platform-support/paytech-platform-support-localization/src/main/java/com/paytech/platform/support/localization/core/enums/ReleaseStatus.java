package com.paytech.platform.support.localization.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 发布状态（t_i18n_release.status）：PENDING_CHECK → READY 或 FAILED，之后不再变化。 */
@Getter
@RequiredArgsConstructor
public enum ReleaseStatus implements CodeEnum {

    /** 语言包已登记，等待文件检查。 */
    PENDING_CHECK("PENDING_CHECK"),

    /** 全部语言包检查通过，可作为当前发布。 */
    READY("READY"),

    /** 有语言包检查被拒绝，不能上线。 */
    FAILED("FAILED");

    private final String code;
}
