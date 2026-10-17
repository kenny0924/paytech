package com.paytech.client.master.domain.enums;
import com.paytech.foundation.common.enums.CodeEnum;
/** 关系表的闭合集合。 */
public enum PartyRelationType implements CodeEnum {
    OWNERSHIP,

    CONTROL,

    DIRECTOR,

    SIGNATORY,

    UBO;
    @Override public String getCode() { return name(); }
}
