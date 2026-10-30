package com.paytech.tax.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 不由平台计税的原因（响应 notManagedReason，由计算记录推导，不单独持久化）；NOT_MANAGED 不阻断支付，与 BLOCKED 原因分开。 */
public enum TaxNotManagedReason implements CodeEnum {
    /** 责任方不是平台登记的纳税主体（在 t_tax_market 没有任何记录），例如 PSP Client */
    SUBJECT_NOT_REGISTERED,

    /** 调用方未传责任方：CLIENT 来源 OE 没有商业关系快照，责任方在 Client 一侧，从不在 tax 登记 */
    TAX_RESPONSIBLE_UNRESOLVED;

    /** 接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
