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
}
