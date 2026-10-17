package com.paytech.client.master.api.checkout.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** 候选查询输入；不得指定 OE、行业或 MCC，经营分类由客户主档自行读取。 */
@Getter
@Setter
public class CheckoutCandidatesRequestBody {
    /** 已授权 Client；规范正十进制 BIGINT 字符串。 */
    @NotBlank
    private String clientId;

    /** 消费者国家；两位大写代码，必须与批准的 OE Scope 准确匹配。 */
    @NotBlank
    private String consumerCountryCode;

    /** 交易币种；三位大写代码，必须与批准的 OE Scope 准确匹配。 */
    @NotBlank
    private String currency;

    /** 法律内容 Locale；用于选出候选自带的法律文件与卖方披露，不作隐式回退。 */
    @NotBlank
    private String locale;

    /**
     * Commerce 冻结的商业时点。
     * <li>UTC RFC 3339 字符串，最多六位小数；是否晚于当前时间由 commercial 校验，客户主档不比较本机时钟。</li>
     */
    @NotBlank
    private String contextAsOfTime;
}
