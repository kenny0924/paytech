package com.paytech.commercial.domain;

import com.paytech.commercial.api.enums.PriceRelevanceReason;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一条价格与合同开通项不相关的原因：保存时作为提示返回，排期 / 激活时拒绝。 */
@Getter
@AllArgsConstructor
public class PriceRelevanceIssue {
    /** 价格 OE */
    private final String entityNo;

    /** 价格币种 */
    private final String currency;

    /** 定价画像代码 */
    private final String pricingProfileCode;

    /** 原因 */
    private final PriceRelevanceReason reason;

    /** 中文说明 */
    private final String message;

    /** 拒绝信息中的价格定位。 */
    public String label() {
        return entityNo + "/" + currency + "/" + pricingProfileCode;
    }
}
