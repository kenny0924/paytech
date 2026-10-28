package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 开通项前置检查项。 */
public enum CapabilityPrerequisiteCode implements CodeEnum {
    /** ① Client 与开通项所在 OE 的角色资格（compliance） */
    QUALIFICATION("QUALIFICATION"),

    /** ② 合同生效且 ACTIVE 价格版本覆盖开通项（本库） */
    AGREEMENT_TERM("AGREEMENT_TERM"),

    /** ④ 渠道身份（channel-connector）；本期未接入，固定 SKIPPED */
    CHANNEL_IDENTITY("CHANNEL_IDENTITY");

    private final String code;

    CapabilityPrerequisiteCode(String code) {
        this.code = code;
    }

    /** 接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
