package com.paytech.client.contract.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** AgreementParty PartyRole 的数据库闭合值域。 */
public enum AgreementPartyPartyRole implements CodeEnum {
    CLIENT("CLIENT"),

    LOCAL_PARTNER("LOCAL_PARTNER"),

    PLATFORM_SIGNATORY("PLATFORM_SIGNATORY"),

    OPERATING_ENTITY("OPERATING_ENTITY");
    private final String code;
    AgreementPartyPartyRole(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}
