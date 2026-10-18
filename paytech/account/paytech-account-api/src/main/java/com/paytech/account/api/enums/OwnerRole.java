package com.paytech.account.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 自动记账规则行的核算对象角色，由固定解释器解析为账户 owner_type 与 owner_id。 */
public enum OwnerRole implements CodeEnum {
    /** 目标账簿自身主体 */
    BOOK_ENTITY,

    /** 交易 Client */
    CLIENT,

    /** 交易 Local Partner */
    LOCAL_PARTNER,

    /** 按 MID 与币种命中的 Finance 渠道清算账户 */
    CHANNEL_ACCOUNT,

    /** 平台服务费收取方 Finance Entity */
    PLATFORM_FINANCE_ENTITY,

    /** 交易 Operating Entity */
    TRANSACTION_OE,

    /** Seller 实收税组件的税务机关 */
    TAX_AUTHORITY;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
