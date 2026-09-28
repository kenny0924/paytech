package com.paytech.finance.accounting.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 分类余额查询支持的账户 Owner 类型。 */
public enum OwnerType implements CodeEnum {
    CLIENT,

    LOCAL_PARTNER;

    @Override
    public String getCode() {
        return name();
    }
}
