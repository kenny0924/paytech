package com.paytech.commerce.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Commerce 命令表的当前与历史操作类型；保留常量不代表仍开放写入。 */
public enum CommandOperationType implements CodeEnum {
    CREATE_CHECKOUT("CREATE_CHECKOUT"),

    CANCEL_ORDER("CANCEL_ORDER"),

    CREATE_REFUND("CREATE_REFUND"),

    SAVE_CHECKOUT_CONSUMER("SAVE_CHECKOUT_CONSUMER"),

    PREPARE_CHECKOUT("PREPARE_CHECKOUT"),

    SELECT_CHECKOUT_ENTITY("SELECT_CHECKOUT_ENTITY"),

    START_CHECKOUT_INTENT("START_CHECKOUT_INTENT"),

    CONFIRM_CHECKOUT_PAYMENT("CONFIRM_CHECKOUT_PAYMENT"),

    /** 历史支付请求命令；当前提交与查询不再读写。 */
    SUBMIT_CHECKOUT_PAYMENT("SUBMIT_CHECKOUT_PAYMENT");

    private final String code;

    CommandOperationType(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
