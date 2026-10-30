package com.paytech.acquiring.api.client.v1.checkout.response;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.paytech.acquiring.api.client.v1.common.ClientApiConstraints;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 创建 Hosted Checkout 的响应。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateCheckoutResponseBody {
    /** Commerce 不透明订单号，区分大小写。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank
    @Size(max = 64)
    private String orderNo;

    /** Client 内永久唯一的订单号；不等于幂等键。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = ClientApiConstraints.CLIENT_REFERENCE)
    private String clientOrderId;

    /** 消费者跳转的 Hosted Checkout 地址。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotNull
    private String checkoutUrl;

    /**
     * 订单币种。
     * <li>使用大写 ISO 4217 代码；本期支持 USD。</li>
     */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank
    @Pattern(regexp = ClientApiConstraints.CURRENCY)
    private String currency;

    /** 订单创建时冻结的预估金额。<li>币种主单位字符串；本期 USD 固定两位小数，例如 "10.00"。</li> */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotNull
    @Valid
    private AmountsInfo amounts;

    /**
     * 首次创建冻结的 UTC 到期时间。
     * <li>重放不会延长。</li>
     */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotNull
    private Instant expiresAt;

    /**
     * 创建回执的准备状态。
     * <li>固定为 PENDING。</li>
     */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank
    private String preparationStatus;

    /**
     * 创建回执的金额状态（OrderAmountStatus Code）。
     * <li>ESTIMATED：按 Client 声明的消费者国家暂估的税额与总额，消费者提交账单地址后重算，暂不可支付。</li>
     * <li>BLOCKED：无法正确计税，税额、舍入额与总额为 null，原因见 taxBlockReason；订单仍创建，消费者资料提交后可能重算为可支付。</li>
     * <li>FINAL：税务不由平台计算（NOT_MANAGED），税额为 Client 自报值（Client API 尚无该字段，为 0）。</li>
     */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank
    private String amountStatus;

    /**
     * 创建回执的会话状态。
     * <li>固定为 PREPARING。</li>
     */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank
    private String checkoutStatus;

    /**
     * 税务阻断原因代码。
     * <li>仅 amountStatus=BLOCKED 时有值，如 MARKET_NOT_ENABLED、NO_RULE、UNKNOWN_TAX_CATEGORY；其他状态为 null。</li>
     */
    @Size(max = 64)
    private String taxBlockReason;

    /** 创建时的预估金额信息；均使用订单币种主单位字符串，响应按币种补齐小数位。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AmountsInfo {
        /**
         * 优惠前商品总额，等于各行单价乘数量之和。
         * <li>币种主单位金额字符串，当前 USD 按两位小数输出，例如 "10.00"。</li>
         */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank
        @Pattern(regexp = ClientApiConstraints.DECIMAL)
        private String merchandiseAmount;

        /**
         * 订单优惠总额，等于各行优惠金额之和。
         * <li>币种主单位金额字符串，当前 USD 按两位小数输出，例如 "1.00"。</li>
         */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank
        @Pattern(regexp = ClientApiConstraints.DECIMAL)
        private String discountAmount;

        /**
         * 优惠后税前金额，等于商品总额减优惠总额。
         * <li>币种主单位金额字符串，当前 USD 按两位小数输出，例如 "9.00"。</li>
         */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank
        @Pattern(regexp = ClientApiConstraints.DECIMAL)
        private String netAmount;

        /**
         * 税额。
         * <li>币种主单位金额字符串，非空时按币种精度输出。</li>
         * <li>支付准备前为 null。</li>
         */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @JsonInclude(JsonInclude.Include.ALWAYS)
        @Pattern(regexp = ClientApiConstraints.DECIMAL)
        private String taxAmount;

        /**
         * 舍入金额。
         * <li>币种主单位金额字符串，非空时按币种精度输出。</li>
         * <li>支付准备前为 null。</li>
         */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @JsonInclude(JsonInclude.Include.ALWAYS)
        @Pattern(regexp = ClientApiConstraints.DECIMAL)
        private String roundingAmount;

        /**
         * 最终总额。
         * <li>币种主单位金额字符串，非空时按币种精度输出。</li>
         * <li>支付准备前为 null，不以零代替未知。</li>
         */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @JsonInclude(JsonInclude.Include.ALWAYS)
        @Pattern(regexp = ClientApiConstraints.DECIMAL)
        private String totalAmount;

        /**
         * 已支付金额。
         * <li>币种主单位金额字符串，非空时按币种精度输出。</li>
         * <li>创建回执省略本字段。</li>
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Pattern(regexp = ClientApiConstraints.DECIMAL)
        private String paidAmount;

    }
}
