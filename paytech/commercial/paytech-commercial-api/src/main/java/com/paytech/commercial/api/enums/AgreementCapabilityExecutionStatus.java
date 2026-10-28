package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 合同开通项执行状态（t_agreement_capability.execution_status）
 * <li>成员资格 ACTIVE 时必有值，DISABLED 时为空；Checkout 解析只认 ENABLED。</li>
 */
public enum AgreementCapabilityExecutionStatus implements CodeEnum {
    /** 等待前置：资格、合同与价格、渠道身份尚未全部满足 */
    PENDING_PREREQUISITES("PENDING_PREREQUISITES"),

    /** 已开通，可参与 Checkout 解析 */
    ENABLED("ENABLED"),

    /** 前置明确失效而暂停；前置恢复后由门禁自动恢复 */
    SUSPENDED_BY_PREREQUISITE("SUSPENDED_BY_PREREQUISITE"),

    /** 运营手动暂停；门禁不触碰，只能由运营恢复 */
    SUSPENDED_MANUALLY("SUSPENDED_MANUALLY");

    private final String code;

    AgreementCapabilityExecutionStatus(String code) {
        this.code = code;
    }

    /** 数据库与接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
