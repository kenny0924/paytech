package com.paytech.platform.support.localization.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** key 状态（t_i18n_key.status）：导入文件中没有的 key 废弃，重新出现时恢复。 */
@Getter
@RequiredArgsConstructor
public enum KeyStatus implements CodeEnum {

    /** 在用；参与翻译与发布。 */
    ACTIVE("ACTIVE"),

    /** 已废弃；保留翻译，不参与发布。 */
    DEPRECATED("DEPRECATED");

    private final String code;
}
