package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * Checkout 解析派生的责任主体类型（derived.taxResponsibleParty、derived.fundsBookOwner）。
 * <li>具体主体由同一响应的 sellerPartyId / acquiringEntityNo 指明，这里只给出角色。</li>
 */
public enum ResponsiblePartyType implements CodeEnum {
    /** 收单 Operating Entity */
    OPERATING_ENTITY("OPERATING_ENTITY"),

    /** 平台 Client */
    CLIENT("CLIENT");

    private final String code;

    ResponsiblePartyType(String code) {
        this.code = code;
    }

    /** 接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
