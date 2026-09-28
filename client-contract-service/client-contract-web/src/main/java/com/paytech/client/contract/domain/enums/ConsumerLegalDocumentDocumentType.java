package com.paytech.client.contract.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** ConsumerLegalDocument DocumentType 的数据库闭合值域。 */
public enum ConsumerLegalDocumentDocumentType implements CodeEnum {
    CONSUMER_TERMS("CONSUMER_TERMS"),

    REFUND_POLICY("REFUND_POLICY"),

    PRIVACY_NOTICE("PRIVACY_NOTICE");
    private final String code;
    ConsumerLegalDocumentDocumentType(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}
