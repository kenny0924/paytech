package com.paytech.commercial.api.term.response;

import lombok.Getter;
import lombok.Setter;

/** 一条与合同开通项不相关的价格；保存时提示，排期 / 激活时拦截。 */
@Getter
@Setter
public class PriceRelevanceWarning {
    /** 价格 OE */
    private String entityNo;

    /** 价格币种 */
    private String currency;

    /** 定价画像代码 */
    private String pricingProfileCode;

    /**
     * 原因代码
     * <li>PAYMENT_METHOD_NOT_ENABLED、CURRENCY_NOT_ENABLED、CONDITION_OUT_OF_SCOPE、FEATURE_NOT_ENABLED、NO_CAPABILITY_ON_ENTITY。</li>
     */
    private String reasonCode;

    /** 中文说明 */
    private String message;
}
