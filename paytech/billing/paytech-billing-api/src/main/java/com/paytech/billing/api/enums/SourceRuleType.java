package com.paytech.billing.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 明细行计算参数来源的规则族（t_billing_fact_line.source_rule_type）；资金类与人工行为空。 */
public enum SourceRuleType implements CodeEnum {
    /** commercial 命中的 Client 价格 */
    COMMERCIAL_PRICE,

    /** Local Partner 分润规则 */
    PARTNER_SHARE,

    /** 平台服务费规则 */
    PLATFORM_SERVICE_CHARGE,

    /** 税务规则 */
    TAX_RULE,

    /** channel-connector 渠道成本方案版本 */
    CHANNEL_COST,

    /** 外汇汇率 */
    FX_RATE;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
