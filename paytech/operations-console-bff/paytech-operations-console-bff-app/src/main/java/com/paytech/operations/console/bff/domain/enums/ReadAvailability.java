package com.paytech.operations.console.bff.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 聚合页面中某一部分的读取结果；Code 是主档详情页 *Status 字段的合同值。 */
public enum ReadAvailability implements CodeEnum {
    /** 已读取，对应字段为 Owner 事实 */
    AVAILABLE("AVAILABLE"),

    /** 读取失败或超时，对应字段为空，页面不得推断为“无数据” */
    UNAVAILABLE("UNAVAILABLE"),

    /** 当前主体没有这一部分（例如 CLIENT 来源的 OE 没有提供方），未读取 */
    NOT_APPLICABLE("NOT_APPLICABLE");

    private final String code;

    ReadAvailability(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
