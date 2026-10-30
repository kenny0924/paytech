package com.paytech.tax.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 计算阶段（t_tax_calculation.calculation_stage）：证据来源与是否可支付的区别。 */
public enum TaxCalculationStage implements CodeEnum {
    /** 建单暂估：按 Client 声明国家（DECLARED_COUNTRY）计算，不可支付，expiresAt 为空 */
    PROVISIONAL,

    /** 最终报价：按消费者账单国家（BILLING_COUNTRY）计算；READY 且未过期才可支付 */
    FINAL;

    /** 数据库、接口与消息使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
