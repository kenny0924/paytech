package com.paytech.client.contract.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Agreement AgreementFormat 的数据库闭合值域。 */
public enum AgreementAgreementFormat implements CodeEnum {
    MERGED_PARTY("MERGED_PARTY"),

    LEGACY_ROLE_SIGNATURE("LEGACY_ROLE_SIGNATURE");
    private final String code;
    AgreementAgreementFormat(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}
