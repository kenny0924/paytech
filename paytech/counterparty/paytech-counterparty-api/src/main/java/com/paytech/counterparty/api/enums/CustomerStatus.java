package com.paytech.counterparty.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Customer 状态；删除不可撤销。也是 counterparty 对外合同中的状态值。 */
public enum CustomerStatus implements CodeEnum {
    ACTIVE,

    DELETED;

    @Override
    public String getCode() { return name(); }
}
