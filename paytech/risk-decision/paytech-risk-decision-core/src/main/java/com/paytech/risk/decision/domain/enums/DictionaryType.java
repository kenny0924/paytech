package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 字典类型的固定代码。 */
public enum DictionaryType implements CodeEnum {
    REJECTION_REASON,

    SUBJECT_TAG;

    /** 返回稳定代码，供持久化及协议边界显式转换。 */
    @Override public String getCode() { return switch (this) {
        case REJECTION_REASON -> "REJECTION_REASON";
        case SUBJECT_TAG -> "SUBJECT_TAG";
    }; }
}
