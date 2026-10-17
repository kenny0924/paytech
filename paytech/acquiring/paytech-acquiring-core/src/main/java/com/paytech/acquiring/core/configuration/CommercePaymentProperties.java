package com.paytech.acquiring.core.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** Commerce 的 Card 工具申请配置，不包含渠道或实例地址。 */
@Getter
@Setter
@Validated
@ConfigurationProperties("acquiring.payment")
public class CommercePaymentProperties {
    /** 精确的 Card 支付方式代码。
     * <li>必填，来自 Payment 支付方式目录；变更后重启，单次提交保持一致。</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private String cardPaymentMethodCode;
}
