package com.paytech.billing.core.dal.dataobject;

import java.time.Instant;

import com.paytech.billing.api.enums.FactRefType;

import lombok.Getter;
import lombok.Setter;

/** t_billing_fact_ref：计费事实引用的外部业务对象，每种类型至多一条；不可变。 */
@Getter
@Setter
public class BillingFactRef {
    /** Snowflake 主键 */
    private Long id;

    /** 所属计费事实 */
    private Long factId;

    /** 引用的外部业务对象类型 */
    private FactRefType refType;

    /**
     * 按 refType 解释的稳定标识
     * <li>数字 ID 为规范十进制字符串；来源 Owner 各异，不建立跨库外键。</li>
     */
    private String refId;

    /**
     * 渠道外部交易号类型
     * <li>仅 refType = CHANNEL_TRANSACTION_REF 时非空；取值来自 Payment 渠道交易引用类型目录，非固定枚举。</li>
     */
    private String externalReferenceType;

    /**
     * 渠道外部交易号
     * <li>仅 refType = CHANNEL_TRANSACTION_REF 时非空；reconciliation 按它匹配渠道账单行。</li>
     */
    private String externalTransactionId;

    /** 记录创建的 UTC 微秒时间 */
    private Instant createTime;
}
