package com.paytech.tax.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 谁操作某主体在某地区的税务（t_tax_market.managed_by）；本期只读展示。 */
public enum ManagedBy implements CodeEnum {
    /** 主体持有方（如 PayFac Partner） */
    SUBJECT_HOLDER,

    /** 平台运营代操作 */
    PLATFORM_OPERATIONS;

    /** 数据库、接口与消息使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
