package com.paytech.client.contract.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** AgreementDocumentRef DocumentType 的数据库闭合值域。 */
public enum AgreementDocumentRefDocumentType implements CodeEnum {
    SIGNING_DOCUMENT("SIGNING_DOCUMENT"),

    ANNEX("ANNEX"),

    EXECUTED_DOCUMENT("EXECUTED_DOCUMENT"),

    TERMINATION_NOTICE("TERMINATION_NOTICE");
    private final String code;
    AgreementDocumentRefDocumentType(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}
