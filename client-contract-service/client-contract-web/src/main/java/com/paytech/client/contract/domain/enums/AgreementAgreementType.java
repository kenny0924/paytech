package com.paytech.client.contract.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Agreement AgreementType 的数据库闭合值域。 */
public enum AgreementAgreementType implements CodeEnum {
    CLIENT("CLIENT"),

    PARTNER("PARTNER");
    private final String code;
    AgreementAgreementType(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}
