package com.paytech.commercial.api.checkout.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Commerce 创建 Checkout 时的只读解析输入；不得指定 OE、行业、MCC 或支付方式。 */
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
