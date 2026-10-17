package com.paytech.billing.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 自动记账金额来源；除 GROSS_AMOUNT 取交易经济 Header 外，其余按同名组件类型汇总 posted_amount。 */
public enum AmountSource implements CodeEnum {
    /** 交易全额 */
    GROSS_AMOUNT,

    /** Client 本金 */
    CLIENT_PRINCIPAL,

    /** Client 手续费 */
    CLIENT_FEE,

    /** Seller 实收税 */
    SELLER_TAX,

    /** 预计渠道成本 */
    CHANNEL_COST,

    /** Local Partner 分润 */
    PARTNER_SHARE,

    /** 平台服务费 */
    PLATFORM_SERVICE_CHARGE;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
