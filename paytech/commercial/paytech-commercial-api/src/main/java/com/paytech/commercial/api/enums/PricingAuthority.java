package com.paytech.commercial.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 合同定价权归属（t_agreement.pricing_authority）；CLIENT 合同必填，本期应用只接受 PLATFORM。 */
public enum PricingAuthority implements CodeEnum {
    /** 价目由平台制定 */
    PLATFORM("PLATFORM"),

    /** 价目由推荐伙伴提出、平台运营录入审批，Partner 门户只读；要求 referral_partner_id 非空 */
    PARTNER("PARTNER");

    private final String code;

    PricingAuthority(String code) {
        this.code = code;
    }

    /** 数据库与接口使用的稳定编码。 */
    @Override
    public String getCode() {
        return code;
    }
}
