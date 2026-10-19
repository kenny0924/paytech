package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 合同开通项状态（t_agreement_capability.status）。 */
public enum AgreementCapabilityStatus implements CodeEnum {
    /** 已开通，可参与解析 */
    ENABLED("ENABLED"),

    /** 风控或合规临时停用，可恢复 */
    SUSPENDED("SUSPENDED"),

    /** 运营关闭 */
    DISABLED("DISABLED");

    private final String code;

    AgreementCapabilityStatus(String code) {
        this.code = code;
    }

    /** 数据库与接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
