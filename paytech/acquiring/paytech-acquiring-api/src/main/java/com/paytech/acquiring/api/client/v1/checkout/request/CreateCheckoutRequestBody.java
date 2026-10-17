package com.paytech.commerce.client.api.v1.checkout.request;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.paytech.commerce.client.api.v1.common.ClientApiConstraints;
import com.paytech.foundation.common.json.RejectExplicitNullDeserializer;
import com.paytech.foundation.common.json.StrictOptionalInstantDeserializer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tools.jackson.databind.annotation.JsonDeserialize;

/** 创建 Hosted Checkout 的请求。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateCheckoutRequestBody {
    /**
     * Client 内永久唯一的订单号
     */
    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = ClientApiConstraints.CLIENT_REFERENCE)
    private String clientOrderId;

    /**
     * 订单币种。
     * <li>使用大写 ISO 4217 代码；本期支持 USD。</li>
     */
    @NotBlank
    @Pattern(regexp = "USD", message = "当前创建 Checkout 仅支持 USD")
    private String currency;

    /**
     * 消费者国家。
     * <li>大写 ISO 两位国家码。</li>
     */
    @NotBlank
    @Pattern(regexp = ClientApiConstraints.COUNTRY)
    private String consumerCountryCode;

    /**
     * Checkout 到期时间
     * <li>请求省略时从首次命令时间起默认 30 分钟；显式请求限 5 分钟至 24 小时。</li>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonDeserialize(using = StrictOptionalInstantDeserializer.class)
    private Instant expiresAt;

    /**
     * 原顺序商品明细。
     * <li>1 至 100 项，clientItemId 在订单内唯一。</li>
     */
    @NotNull
    @Size(min = 1, max = 100)
    private List<@NotNull @Valid CheckoutItemInfo> items;

    /**
     * 可选的 Client 命名空间内消费者引用；带上时订单关联该引用的有效 Customer，没有则新建。
     * <li>只能用于 Client 系统中已认证的用户，访客不得传入；不得为 Email、Phone、证件号等个人原值。</li>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonDeserialize(using = RejectExplicitNullDeserializer.class)
    @Size(min = 1, max = 128)
    @Pattern(regexp = ClientApiConstraints.CONSUMER_REFERENCE)
    private String consumerReference;

    /** 商品信息。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CheckoutItemInfo {
        /** Client 商品行标识。 */
        @NotBlank
        @Size(max = 128)
        @Pattern(regexp = ClientApiConstraints.CLIENT_REFERENCE)
        private String clientItemId;

        /** 消费者看到的商品名称。 */
        @NotBlank
        @Size(max = 255)
        private String name;

        /**
         * 商品说明。
         * <li>可省略，不接受显式 null。</li>
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonDeserialize(using = RejectExplicitNullDeserializer.class)
        @Size(max = 1024)
        private String description;

        /** 商品分类代码。 */
        @NotBlank
        @Size(max = 64)
        private String productTypeCode;

        /** 税务分类代码。 */
        @NotBlank
        @Size(max = 64)
        private String taxCategoryCode;

        /**
         * 商品数量。
         * <li>正数字符串，可含小数，例如 "2" 或 "0.5"；不受币种小数位限制。</li>
         */
        @NotBlank
        @Pattern(regexp = ClientApiConstraints.POSITIVE_DECIMAL)
        private String quantity;

        /**
         * 商品单价。
         * <li>币种主单位的非负十进制字符串，例如 USD "10.00" 表示 10 美元；按币种校验精度。</li>
         * <li>请求允许省略无意义的尾随零，不接受 JSON 数值或科学计数法，不隐式舍入。</li>
         */
        @NotBlank
        @Pattern(regexp = ClientApiConstraints.DECIMAL)
        private String unitAmount;

        /**
         * 整行优惠金额。
         * <li>订单币种主单位的非负金额字符串，例如 USD "1.00" 表示该行共优惠 1 美元；按币种校验精度。</li>
         * <li>不再乘数量，不能超过单价乘数量；请求省略时按零计算，不接受显式 null。</li>
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonDeserialize(using = RejectExplicitNullDeserializer.class)
        @Pattern(regexp = ClientApiConstraints.DECIMAL)
        private String discountAmount;

    }
}
