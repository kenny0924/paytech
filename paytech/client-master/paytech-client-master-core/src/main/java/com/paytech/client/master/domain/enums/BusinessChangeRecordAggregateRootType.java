package com.paytech.client.master.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** BusinessChangeRecord AggregateRootType 的数据库闭合值域。 */
public enum BusinessChangeRecordAggregateRootType implements CodeEnum {
    PARTY("PARTY"),

    CLIENT("CLIENT"),

    LOCAL_PARTNER("LOCAL_PARTNER"),

    OPERATING_ENTITY("OPERATING_ENTITY"),

    CLIENT_APPLICATION("CLIENT_APPLICATION"),

    PARTNER_APPLICATION("PARTNER_APPLICATION"),

    OPERATING_ENTITY_APPLICATION("OPERATING_ENTITY_APPLICATION"),

    PARTY_RELATION("PARTY_RELATION"),

    CLIENT_ENTITY_AFFILIATION("CLIENT_ENTITY_AFFILIATION"),

    ENTITY_SOURCE_RELATION("ENTITY_SOURCE_RELATION"),

    AGREEMENT("AGREEMENT"),

    CONSUMER_LEGAL_DOCUMENT("CONSUMER_LEGAL_DOCUMENT"),

    SELLER_DISCLOSURE("SELLER_DISCLOSURE"),

    COMMERCIAL_TERM_PLAN("COMMERCIAL_TERM_PLAN"),

    PAYMENT_METHOD_PROFILE("PAYMENT_METHOD_PROFILE");
    private final String code;
    BusinessChangeRecordAggregateRootType(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}
