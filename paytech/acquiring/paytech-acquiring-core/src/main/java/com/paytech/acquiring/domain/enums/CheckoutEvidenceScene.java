package com.paytech.acquiring.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Commerce 法律证据的闭合数据库 Code。 */
public enum CheckoutEvidenceScene implements CodeEnum {
    CHECKOUT_OPEN,

    CONSUMER_CONTEXT_SUBMIT,

    LEGAL_ACCEPTANCE,

    PAYMENT_START;

    /** 数据库按稳定 Code 映射，未知值失败关闭。 */
    public String getCode() {
        return name();
    }
}
