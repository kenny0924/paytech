package com.paytech.billing.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 计费事实明细行的付方 / 收方角色（t_billing_fact_line.payer_role、payee_role 共用同一组稳定代码）。 */
public enum FactPartyRole implements CodeEnum {
    /** CCT Client */
    CLIENT,

    /** CCT Local Partner */
    LOCAL_PARTNER,

    /** Payment Channel */
    CHANNEL,

    /** CCT Operating Entity，标识为 entityNo */
    OPERATING_ENTITY,

    /** ledger 本地 Finance Entity；平台方用它表达 */
    FINANCE_ENTITY,

    /** 税务机关 */
    TAX_AUTHORITY,

    /** 平台银行账户 */
    CASH_ACCOUNT,

    /** 平台外汇账户 */
    FX_ACCOUNT,

    /** 消费者；仅重复扣款两类事实的应退消费者款 */
    CONSUMER;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
