package com.paytech.ledger.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 自动记账规则行的核算对象角色，由固定解释器解析为账户 owner_type 与 owner_id
 * <li>不再有 CLIENT、LOCAL_PARTNER 角色：Owner 影响由 account 过账批次的流水生成控制科目行，规则只产生非 Owner 行。</li>
 */
public enum RuleOwnerRole implements CodeEnum {
    /** 目标账簿自身主体 */
    BOOK_ENTITY,

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
