package com.paytech.billing.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 人工调整明细行收方 Owner 账户的目标余额桶（t_billing_fact_line.target_bucket）
 * <li>仅 MANUAL_ADJUSTMENT 且收方为 CLIENT / LOCAL_PARTNER 时可非空，为空按 AVAILABLE；Wire 字段以 String 承载同名代码。</li>
 */
public enum FactTargetBucket implements CodeEnum {
    /** 待结算余额 */
    PENDING,

    /** 可用余额 */
    AVAILABLE;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
