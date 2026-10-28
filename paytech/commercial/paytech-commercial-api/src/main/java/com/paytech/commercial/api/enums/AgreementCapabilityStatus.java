package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 合同开通项成员资格（t_agreement_capability.status）
 * <li>只表示开通项是否纳入合同；能否执行由 {@link AgreementCapabilityExecutionStatus} 表达。</li>
 */
public enum AgreementCapabilityStatus implements CodeEnum {
    /** 纳入合同，价格须覆盖 */
    ACTIVE("ACTIVE"),

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
