package com.paytech.client.contract.domain.enums;
import com.paytech.foundation.common.enums.CodeEnum;
/** 关系表的闭合集合。 */
public enum PartyRelationStatus implements CodeEnum {
    DRAFT,

    ACTIVE,

    ENDED;
    @Override public String getCode() { return name(); }
}
