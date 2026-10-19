package com.paytech.channel.connector.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;
import com.paytech.foundation.common.error.InvalidParameterException;

/** 渠道成本入口按稳定 Code 解析目录枚举；未知代码以参数错误拒绝，不映射为空。 */
public final class ChannelCostCodes {

    private ChannelCostCodes() {
    }

    /**
     * 按稳定 Code 解析目录枚举
     *
     * @param type 枚举类型
     * @param field 错误消息中的字段名
     * @param code 待解析代码
     * @return 对应枚举常量
     * @throws InvalidParameterException 代码不在目录内
     */
    public static <E extends Enum<E> & CodeEnum> E parse(Class<E> type, String field, String code) {
        for (E constant : type.getEnumConstants()) {
            if (constant.getCode().equals(code)) {
                return constant;
            }
        }
        throw new InvalidParameterException(field + "代码不在目录内: " + code);
    }
}
