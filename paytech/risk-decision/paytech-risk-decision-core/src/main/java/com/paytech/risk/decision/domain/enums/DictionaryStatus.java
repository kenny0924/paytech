package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 字典选择资格状态的固定代码。 */
public enum DictionaryStatus implements CodeEnum {
    ACTIVE,

    DISABLED;

    /** 返回稳定代码，供持久化及协议边界显式转换。 */
    @Override public String getCode() { return switch (this) {
        case ACTIVE -> "ACTIVE";
        case DISABLED -> "DISABLED";
    }; }
}
