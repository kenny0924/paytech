package com.paytech.commerce.client.api.v1.order.response;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.paytech.commerce.client.api.v1.common.ClientApiConstraints;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Client 订单查询响应；只包含允许公开的本地事实。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetOrderResponseBody {
    /** 不透明、大小写敏感的 Commerce 订单号。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank
    @Size(max = 64)
    private String orderNo;

    /** Client 提供、在该 Client 内永久唯一的业务订单号。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = ClientApiConstraints.CLIENT_REFERENCE)
    private String clientOrderId;

    /**
     * 订单创建时选用的接入方式。
     * <li>HOSTED_CHECKOUT：平台托管收银台；DIRECT_PAY：Client 直接支付集成。</li>
     * <li>由服务端固定，后续支付尝试或入口变化不修改本字段。</li>
     */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank
    private String creationMode;

    /** 当前订单生命周期代码。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank
    private String orderStatus;

    /** 支付准备状态 PENDING 或 READY。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank
    private String preparationStatus;

    /** 金额状态 ESTIMATED 或 FINAL；准备前税额、舍入额与总额为 null。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank
    private String amountStatus;

    /** 本地聚合支付状态；未决的 UNKNOWN 对外保持 PROCESSING。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank
    private String paymentStatus;

    /** ISO 4217 三位大写币种代码。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank
    @Pattern(regexp = ClientApiConstraints.CURRENCY)
    private String currency;

    /** 创建时冻结的两位大写消费者国家代码。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank
    @Pattern(regexp = ClientApiConstraints.COUNTRY)
    private String consumerCountryCode;

    /** 订单的不可变商品快照。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotNull
    @Valid
    private List<OrderItemInfo> items;

    /**
     * 订单汇总金额。
     * <li>币种主单位字符串，按币种精度输出，例如 USD "10.00"。</li>
     * <li>准备前税额、舍入额及总额为 null。</li>
     */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotNull
    @Valid
    private AmountsInfo amounts;

    /**
     * 实际关联的收银台会话。
     * <li>没有关联会话时为 null；不能仅由 creationMode 推断是否存在。</li>
     */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Valid
    private CheckoutInfo checkout;

    /**
     * 最近支付尝试。
     * <li>尚无 Attempt 时为空且 JSON 省略此字段。</li>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Valid
    private LastPaymentAttemptInfo lastPaymentAttempt;

    /** 当前退款预占、成功退款和剩余可退金额。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotNull
    @Valid
    private RefundSummaryInfo refundSummary;

    /** 本地 Finance 投影中的资金可用性。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotNull
    @Valid
    private FundsAvailabilityInfo fundsAvailability;

    /** 订单创建的 UTC 时刻。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotNull
    private Instant createdAt;

    /** 订单到期的 UTC 时刻。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotNull
    private Instant expiresAt;

    /**
     * 支付成功的 UTC 时刻。
     * <li>未成功时为空且 JSON 省略此字段。</li>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Instant paidAt;

    /** 订单、Session 及相关本地投影更新时间的最大值。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotNull
    private Instant updatedAt;

    /** 订单关联的收银台会话信息。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CheckoutInfo {
        /** 收银台会话生命周期代码，不代替订单或支付状态。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank
        private String status;

        /**
         * 原会话的公开链接。
         * <li>到期后为 null；完成支付后到期前可展示结果，不代表还能支付。</li>
         */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @JsonInclude(JsonInclude.Include.ALWAYS)
        private String url;

        /**
         * 收银台会话到期的 UTC 时刻。
         * <li>当前与订单期限一致，重复查询不延长有效期。</li>
         */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotNull
        private Instant expiresAt;
    }

    /** 订单创建时冻结的商品信息。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderItemInfo {
        /** Client 提供、订单内唯一的商品行标识。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank
        @Size(max = 128)
        @Pattern(regexp = ClientApiConstraints.CLIENT_REFERENCE)
        private String clientItemId;

        /** 面向 Consumer 的商品名称。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank
        @Size(max = 255)
        private String name;

        /** 面向 Consumer 的商品描述；未提供时返回空字符串。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotNull
        @Size(max = 1024)
        private String description;

        /** Client 提供的商品分类代码。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank
        @Size(max = 64)
        private String productTypeCode;

        /** Client 提供的税务分类代码。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank
        @Size(max = 64)
        private String taxCategoryCode;

        /** 正数字符串，可含小数，例如 "2" 或 "0.5"；不受币种小数位限制。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank
        @Pattern(regexp = ClientApiConstraints.POSITIVE_DECIMAL)
        private String quantity;

        /** 订单币种主单位单价字符串，按币种精度输出，例如 USD "10.00" 表示 10 美元。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank
        @Pattern(regexp = ClientApiConstraints.DECIMAL)
        private String unitAmount;

        /** 订单币种主单位的整行优惠字符串，按币种精度输出；不再乘数量。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank
        @Pattern(regexp = ClientApiConstraints.DECIMAL)
        private String discountAmount;
    }

    /** 订单金额信息；以订单币种主单位字符串表示，按币种精度输出，例如 USD "10.00"。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AmountsInfo {
        /** 各行单价乘数量之和，未扣优惠且不含税额或舍入额。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank
        @Pattern(regexp = ClientApiConstraints.DECIMAL)
        private String merchandiseAmount;

        /** 各行优惠金额之和，每行优惠不再乘数量。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank
        @Pattern(regexp = ClientApiConstraints.DECIMAL)
        private String discountAmount;

        /** 商品总额减优惠总额，未包含税额或舍入额。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank
        @Pattern(regexp = ClientApiConstraints.DECIMAL)
        private String netAmount;

        /**
         * 税额。
         * <li>币种主单位字符串；准备前为 null，完成计税后为明确金额。</li>
         */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @JsonInclude(JsonInclude.Include.ALWAYS)
        @Pattern(regexp = ClientApiConstraints.DECIMAL)
        private String taxAmount;

        /**
         * 舍入金额。
         * <li>币种主单位字符串；准备前为 null，准备后为明确金额。</li>
         */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @JsonInclude(JsonInclude.Include.ALWAYS)
        @Pattern(regexp = ClientApiConstraints.DECIMAL)
        private String roundingAmount;

        /**
         * 最终订单金额。
         * <li>币种主单位字符串；准备前为 null，不以零值代替未知。</li>
         */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @JsonInclude(JsonInclude.Include.ALWAYS)
        @Pattern(regexp = ClientApiConstraints.DECIMAL)
        private String totalAmount;

        /**
         * 已成功支付金额。
         * <li>币种主单位字符串；订单查询未支付时返回零额。</li>
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Pattern(regexp = ClientApiConstraints.DECIMAL)
        private String paidAmount;
    }

    /** 最近一次支付尝试的信息，不含 Channel 或敏感支付数据。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LastPaymentAttemptInfo {
        /** 最近 Attempt 的状态代码，不包含 Channel 或敏感支付信息。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank
        private String status;
    }

    /** 当前退款金额信息；均为订单币种主单位字符串，按币种精度输出。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RefundSummaryInfo {
        /** 未到终态退款请求预占的金额。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank
        @Pattern(regexp = ClientApiConstraints.DECIMAL)
        private String processingAmount;

        /** 已经成功退款的累计金额。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank
        @Pattern(regexp = ClientApiConstraints.DECIMAL)
        private String succeededAmount;

        /** 退款基数减去预占和已退款金额；查询不预占额度。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank
        @Pattern(regexp = ClientApiConstraints.DECIMAL)
        private String refundableAmount;
    }

    /** 本地资金可用性信息。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FundsAvailabilityInfo {
        /** 当前资金可用状态代码。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank
        private String status;

        /** 订单币种主单位的已成熟可用金额字符串，按币种精度输出；NOT_APPLICABLE 或 PENDING 时为 null。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @JsonInclude(JsonInclude.Include.ALWAYS)
        @Pattern(regexp = ClientApiConstraints.DECIMAL)
        private String availableAmount;

        /** 预计可用 UTC 时刻；尚无计划时为 null。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @JsonInclude(JsonInclude.Include.ALWAYS)
        private Instant scheduledAt;

        /** 最终成熟的 UTC 时刻；未确认成熟时为 null。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @JsonInclude(JsonInclude.Include.ALWAYS)
        private Instant availableAt;

        /** 本地已应用事实的最新业务 UTC 时刻。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotNull
        private Instant asOf;

        /** 本地资金投影的持久化更新时间。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotNull
        private Instant updatedAt;

    }
}
