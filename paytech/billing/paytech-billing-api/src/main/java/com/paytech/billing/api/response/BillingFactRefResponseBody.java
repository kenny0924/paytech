package com.paytech.billing.api.response;

import lombok.Getter;
import lombok.Setter;

/** 计费事实引用的外部业务对象。 */
@Getter
@Setter
public class BillingFactRefResponseBody {
    /** 引用类型代码（FactRefType） */
    private String refType;

    /** 按 refType 解释的稳定标识 */
    private String refId;
}
