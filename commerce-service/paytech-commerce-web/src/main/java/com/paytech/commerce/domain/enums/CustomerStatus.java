package com.paytech.commerce.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Customer 状态；删除不可撤销。 */
public enum CustomerStatus implements CodeEnum {
    ACTIVE,

    DELETED;

    @Override
    public String getCode() { return name(); }
}
