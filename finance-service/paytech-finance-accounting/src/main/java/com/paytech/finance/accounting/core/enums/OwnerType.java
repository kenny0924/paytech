package com.paytech.finance.accounting.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 统一记账账户的核算对象类型。 */
public enum OwnerType implements CodeEnum {
    /** CCT 经营主体，ownerId 为 entityNo */
    OPERATING_ENTITY,

    /** Finance 内部财务主体 */
    FINANCE_ENTITY,

    /** Client */
    CLIENT,

    /** Local Partner */
    LOCAL_PARTNER,

    /** 渠道技术账户 */
    CHANNEL_ACCOUNT,

    /** 平台或经营主体银行账户 */
    CASH_ACCOUNT,

    /** 税务机关 */
    TAX_AUTHORITY;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
