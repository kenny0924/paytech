package com.paytech.channel.connector.api.candidate.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** MID 能力匹配的完整交易维度；由调用方从已冻结的交易事实取得。 */
@Getter
@Setter
public class MidCriteriaBody {
    /** 精确支付方式目录代码，禁止 *。 */
    @NotBlank
    @Pattern(regexp = "[A-Z0-9][A-Z0-9_.-]{0,63}")
    private String paymentMethodCode;

    /** 支付体系代码；缺失时仅匹配配置 *。 */
    @Pattern(regexp = "[A-Z0-9][A-Z0-9_.-]{0,63}")
    private String schemeCode;

    /** 消费者国家，ISO 3166-1 两位大写代码。 */
    @NotNull
    @Pattern(regexp = "[A-Z]{2}")
    private String countryCode;

    /** 交易币种，ISO 4217 三位大写代码。 */
    @NotNull
    @Pattern(regexp = "[A-Z]{3}")
    private String currency;

    /** 冻结的四位商户类别码。 */
    @NotNull
    @Pattern(regexp = "[0-9]{4}")
    private String mccCode;
}
