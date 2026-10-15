package com.paytech.finance.ext.rest.accounting.converter;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.InvalidParameterException;

/** 把 Wire 字符串 Code 显式解析为内部枚举；未知 Code 以参数错误拒绝，不回退默认值。 */
final class CodeEnumConverter {

    private CodeEnumConverter() {
    }

    /** 按稳定 Code 查找枚举常量。 */
    static <E extends Enum<E> & CodeEnum> E parse(Class<E> type, String code) {
        for (E constant : type.getEnumConstants()) {
            if (constant.getCode().equals(code)) {
                return constant;
            }
        }
        throw new InvalidParameterException("未知的 " + type.getSimpleName() + " 编码: " + code);
    }
}
