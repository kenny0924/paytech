package com.paytech.channel.connector.service.processor.request;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** 一组待判定的支付方式、币种与 MCC；三个维度都精确，不接受 *。 */
@Getter
@Setter
public class ChannelIdentityReadinessItem {
    /** 精确支付方式目录代码。 */
    @NotNull @Pattern(regexp = "[A-Z0-9][A-Z0-9_.-]{0,63}")
    private String paymentMethodCode;

    /** 精确交易币种，ISO 三位大写代码。 */
    @NotNull @Pattern(regexp = "[A-Z]{3}")
    private String currency;

    /** 四位 MCC。 */
    @NotNull @Pattern(regexp = "[0-9]{4}")
    private String mccCode;

    /** 去重键：三个维度。 */
    public List<String> key() {
        return List.of(paymentMethodCode, currency, mccCode);
    }
}
