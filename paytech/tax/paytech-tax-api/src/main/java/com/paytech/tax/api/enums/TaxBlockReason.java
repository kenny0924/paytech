package com.paytech.tax.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** BLOCKED 原因码（blockReasons[].code）；同一计算可有多条，path 指出所缺字段或所在行。 */
public enum TaxBlockReason implements CodeEnum {
    /** 供应地对该主体没有有效 market，或 collection_status 不是 ENABLED */
    MARKET_NOT_ENABLED,

    /** 地区包没有命中该行的处理规则 */
    NO_RULE,

    /** 地区包命中该行的规则多于一条 */
    AMBIGUOUS_RULE,

    /** 行的税类不在 t_tax_category 或已停用 */
    UNKNOWN_TAX_CATEGORY,

    /** 缺少本阶段所需的国家证据（PROVISIONAL 需 DECLARED_COUNTRY，FINAL 需 BILLING_COUNTRY） */
    MISSING_EVIDENCE,

    /** market 指向的地区包不存在、未批准、不在有效期或内容不符合 Schema */
    POLICY_INVALID;

    /** 数据库、接口与消息使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
