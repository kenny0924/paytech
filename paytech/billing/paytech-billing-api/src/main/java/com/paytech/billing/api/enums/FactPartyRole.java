package com.paytech.billing.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 经济事实明细行的付方 / 收方角色（t_economic_fact_line.party_type、payer_role、payee_role 共用同一组稳定代码）。 */
public enum FactPartyRole implements CodeEnum {
    /** CCT Client */
    CLIENT,

    /** CCT Local Partner */
    LOCAL_PARTNER,

    /** Payment Channel */
    CHANNEL,

    /** CCT Operating Entity，标识为 entityNo */
    OPERATING_ENTITY,

    /** ledger 本地 Finance Entity */
    FINANCE_ENTITY,

    /** 税务机关 */
    TAX_AUTHORITY,

    /** 消费者 */
    CONSUMER,

    /** 平台 */
    PLATFORM;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
