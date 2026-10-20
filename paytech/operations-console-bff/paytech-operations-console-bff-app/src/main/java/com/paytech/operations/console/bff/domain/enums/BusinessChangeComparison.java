package com.paytech.operations.console.bff.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 变更历史行与上一快照的比较结果；Code 是变更历史响应 comparison 的合同值。 */
public enum BusinessChangeComparison implements CodeEnum {
    /** 该受审计行最早的快照，全部字段视为变化 */
    FIRST_VERSION("FIRST_VERSION"),

    /** 已与同一受审计行的上一快照比较 */
    COMPARED("COMPARED"),

    /** 上一快照超出 BFF 回溯上限，未比较 */
    NOT_COMPARED("NOT_COMPARED");

    private final String code;

    BusinessChangeComparison(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
