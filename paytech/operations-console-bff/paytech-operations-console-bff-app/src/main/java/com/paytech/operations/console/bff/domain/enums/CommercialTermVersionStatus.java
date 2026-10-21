package com.paytech.operations.console.bff.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** commercial 条款版本状态的消费方定义（commercial-api 未发布状态枚举）；Code 与 commercial 列表筛选值一致，用于工作台计数与队列。 */
public enum CommercialTermVersionStatus implements CodeEnum {
    /** 草稿 */
    DRAFT("DRAFT"),

    /** 待审批 */
    PENDING_APPROVAL("PENDING_APPROVAL"),

    /** 已批准 */
    APPROVED("APPROVED"),

    /** 客户已签署价格协议（合同方案非免签版本排期前） */
    CLIENT_SIGNED("CLIENT_SIGNED"),

    /** 已排期 */
    SCHEDULED("SCHEDULED"),

    /** 生效 */
    ACTIVE("ACTIVE"),

    /** 已替代 */
    SUPERSEDED("SUPERSEDED"),

    /** 已到期 */
    EXPIRED("EXPIRED");

    private final String code;

    CommercialTermVersionStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
