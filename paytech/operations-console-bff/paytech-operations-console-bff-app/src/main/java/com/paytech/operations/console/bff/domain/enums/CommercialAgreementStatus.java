package com.paytech.operations.console.bff.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** commercial 合同状态的消费方定义（commercial-api 未发布状态枚举）；Code 与 commercial 列表筛选值一致，用于工作台计数。 */
public enum CommercialAgreementStatus implements CodeEnum {
    /** 草稿 */
    DRAFT("DRAFT"),

    /** 待签署 */
    PENDING_SIGNATURE("PENDING_SIGNATURE"),

    /** 已签署 */
    SIGNED("SIGNED"),

    /** 生效 */
    ACTIVE("ACTIVE"),

    /** 已到期 */
    EXPIRED("EXPIRED"),

    /** 已终止 */
    TERMINATED("TERMINATED"),

    /** 已取消 */
    CANCELLED("CANCELLED");

    private final String code;

    CommercialAgreementStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
