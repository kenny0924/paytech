package com.paytech.client.contract.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** AgreementParty AcceptanceMethod 的数据库闭合值域。 */
public enum AgreementPartyAcceptanceMethod implements CodeEnum {
    E_SIGNATURE("E_SIGNATURE"),

    CLICK_ACCEPTANCE("CLICK_ACCEPTANCE"),

    MANUAL_SIGNATURE("MANUAL_SIGNATURE");
    private final String code;
    AgreementPartyAcceptanceMethod(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}
