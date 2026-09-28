package com.paytech.client.contract.domain.enums;
import com.paytech.foundation.common.enums.CodeEnum;
/** Client 主体类型的数据库闭合集合。 */
public enum ClientType implements CodeEnum {
    INDIVIDUAL,

    ORGANIZATION;
    @Override public String getCode() { return name(); }
}
