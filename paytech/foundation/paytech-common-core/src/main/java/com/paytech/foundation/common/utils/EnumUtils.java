package com.paytech.foundation.common.utils;

import org.apache.commons.lang3.Strings;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * @author lzb
 */
public class EnumUtils {

    /**
     * 根据code获得枚举
     *
     * @param clazz
     *            Enum class
     * @param code
     *            Enum code
     * @return Enum
     */
    public static <T extends CodeEnum> T getEnumByCode(Class<T> clazz, String code) {
        for (T e : clazz.getEnumConstants()) {
            if (Strings.CS.equals(code, e.getCode())) {
                return e;
            }
        }
        return null;
    }
}
