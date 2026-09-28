package com.paytech.client.contract.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 业务审计操作的数据库闭合值域。 */
public enum BusinessChangeOperation implements CodeEnum {
    CREATE,

    UPDATE,

    DELETE;

    @Override public String getCode() { return name(); }
}
