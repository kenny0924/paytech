package com.paytech.billing.api.fact;

import lombok.Getter;
import lombok.Setter;

/** 计费事实引用的外部业务对象（t_billing_fact_ref）。 */
@Getter
@Setter
public class BillingFactRefPayload {
    /** 引用类型代码（FactRefType）。 */
    private String refType;

    /** 按 refType 解释的稳定标识；数字 ID 为规范十进制字符串。 */
    private String refId;

    /**
     * 渠道外部交易号类型
     * <li>仅 refType = CHANNEL_TRANSACTION_REF 时非空，取值来自 Payment 渠道交易引用类型目录（非固定枚举）；其余为 JSON null。</li>
     */
    private String externalReferenceType;

    /**
     * 渠道外部交易号
     * <li>仅 refType = CHANNEL_TRANSACTION_REF 时非空；reconciliation 按它匹配渠道账单行。其余为 JSON null。</li>
     */
    private String externalTransactionId;
}
