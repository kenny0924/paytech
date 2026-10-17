package com.paytech.channel.connector.service.processor.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/** Channel 内部业务合同。 */
@Getter
@Setter
public class MidCriteria {
    /** 精确支付方式目录代码，禁止 *。 */
    @NotBlank @Pattern(regexp = "[A-Z0-9][A-Z0-9_.-]{0,63}")
    private String paymentMethodCode;

    /** 支付体系代码；缺失时仅匹配配置 *。 */
    @Pattern(regexp = "[A-Z0-9][A-Z0-9_.-]{0,63}")
    private String schemeCode;

    /** Commerce 冻结的消费者国家，ISO 两位大写代码。 */
    @NotNull @Pattern(regexp = "[A-Z]{2}")
    private String countryCode;

    /** 交易币种，ISO 三位大写代码。 */
    @NotNull @Pattern(regexp = "[A-Z]{3}")
    private String currency;

    /** 上游冻结的精确四位业务 MCC。 */
    @NotNull @Pattern(regexp = "[0-9]{4}")
    private String mccCode;
}
