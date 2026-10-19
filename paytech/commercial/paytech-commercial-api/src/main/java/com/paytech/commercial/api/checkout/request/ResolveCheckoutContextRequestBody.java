package com.paytech.commercial.api.checkout.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 收单创建 Checkout 时的只读解析输入；不得指定 OE、行业或 MCC，支付方式只用于开通项匹配。 */
@Getter
@Setter
public class ResolveCheckoutContextRequestBody {
    /** 可信 Commerce 已授权的 Client；规范正十进制 BIGINT 字符串。 */
    @NotBlank
    private String clientId;

    /** Commerce 原订单编号；由字母或数字开头，后续可含点、下划线及短横线。 */
    @NotBlank
    @Size(max = 64)
    private String orderNo;

    /** 消费者国家；两位大写代码，支持范围以批准的 OE Scope 为准。 */
    @NotBlank
    private String consumerCountryCode;

    /** 交易币种；三位大写代码，必须与批准的 OE Scope 准确匹配。 */
    @NotBlank
    private String currency;

    /**
     * 本次支付方式 Code，可选
     * <li>Payment Method 稳定代码，大写字母开头，仅含大写字母、数字与下划线，最长 64 个字符；格式不符返回 INVALID_CHECKOUT_QUERY。</li>
     * <li>给出时，候选 OE 下 Client 合同至少一个 ENABLED、商业时点在区间内且币种与消费者国家约束满足的开通项，否则该候选落空；命中项全部在
     * clientTerms.capabilities 返回。省略时不做开通项匹配，该数组为空。</li>
     */
    @Size(max = 64)
    private String paymentMethodCode;

    /** 法律内容 Locale；精确匹配批准的法律文件与卖方披露，不作隐式回退。 */
    @NotBlank
    private String locale;

    /**
     * Commerce 创建流程冻结的商业时点。
     * <li>UTC RFC 3339 字符串，最多六位小数，不晚于 CCT 当前时间。</li>
     * <li>同次尚未完成创建的解析重试保留原值；订单提交后不得重新选择 OE。</li>
     */
    @NotBlank
    private String contextAsOfTime;
}
