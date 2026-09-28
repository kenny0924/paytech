package com.paytech.client.contract.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** BusinessChangeRecord RecordType 的数据库闭合值域。 */
public enum BusinessChangeRecordRecordType implements CodeEnum {
    PARTY("PARTY"),

    CLIENT("CLIENT"),

    LOCAL_PARTNER("LOCAL_PARTNER"),

    OPERATING_ENTITY("OPERATING_ENTITY"),

    PARTY_PROFILE("PARTY_PROFILE"),

    CLIENT_BUSINESS_PROFILE("CLIENT_BUSINESS_PROFILE"),

    COMMERCIAL_TERM_VERSION("COMMERCIAL_TERM_VERSION"),

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
    BusinessChangeRecordRecordType(String code) {
        this.code = code;
    }
    @Override
    public String getCode() {
        return code;
    }
}
