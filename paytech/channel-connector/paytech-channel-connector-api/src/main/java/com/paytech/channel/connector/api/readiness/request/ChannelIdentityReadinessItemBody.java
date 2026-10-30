package com.paytech.channel.connector.api.readiness.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 一组待判定的支付方式、币种与 MCC。 */
@Getter
@Setter
public class ChannelIdentityReadinessItemBody {
    /** 精确支付方式目录代码（与 MID 能力行同一目录），禁止 *。 */
    @NotNull
    @Pattern(regexp = "[A-Z0-9][A-Z0-9_.-]{0,63}")
    private String paymentMethodCode;

    /** 精确交易币种，ISO 4217 三位大写代码，禁止 *。 */
    @NotNull
    @Pattern(regexp = "[A-Z]{3}")
    private String currency;

    /** 四位商户类别码（Client 主经营分类 MCC）。 */
    @NotNull
    @Pattern(regexp = "[0-9]{4}")
    private String mccCode;
}
