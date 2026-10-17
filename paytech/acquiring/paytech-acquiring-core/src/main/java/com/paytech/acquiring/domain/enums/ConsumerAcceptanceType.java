package com.paytech.acquiring.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Commerce 法律证据的闭合数据库 Code。 */
public enum ConsumerAcceptanceType implements CodeEnum {
    CONSUMER_TERMS,

    REFUND_POLICY,

    PRIVACY_NOTICE;

    /** 数据库按稳定 Code 映射，未知值失败关闭。 */
    public String getCode() {
        return name();
    }
}
