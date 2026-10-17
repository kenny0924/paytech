package com.paytech.commerce.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Hosted Checkout 公开站点配置。 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "commerce.checkout")
public class CommerceCheckoutProperties {

    /**
     * 收银台公开 HTTPS Origin。
     * <li>必填，不包含凭据、路径、查询、片段或尾斜线；例如 https://checkout.example.com。</li>
     * <li>修改后需重启，仅影响新建 Session，已有 Session 保留创建时冻结的值。</li>
     */
    @NotBlank
    private String publicOrigin;
}
