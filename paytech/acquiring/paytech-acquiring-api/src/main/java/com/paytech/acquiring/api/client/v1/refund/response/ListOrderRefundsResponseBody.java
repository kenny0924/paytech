package com.paytech.commerce.client.api.v1.refund.response;

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

/** 同一订单下按创建时间、退款号倒序排列的一页退款。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ListOrderRefundsResponseBody {
    /** 当前页退款，包含 Client 和 Operations 来源；无记录时返回空列表。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotNull
    @Valid
    private List<@NotNull @Valid RefundInfo> items;

    /** 大小写敏感的下一页游标；没有更多数据时明确返回 null。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String nextCursor;

    /** 当前退款信息，不返回内部坐标或原因原文。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RefundInfo {
        /** 不透明、大小写敏感的 Commerce 退款号。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank
        @Size(max = 64)
        private String refundRequestNo;

        /** Client 业务退款号；Operations 来源明确返回 null。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @JsonInclude(JsonInclude.Include.ALWAYS)
        @Size(max = 128)
        @Pattern(regexp = ClientApiConstraints.CLIENT_REFERENCE)
        private String clientRefundId;

        /** 创建来源 CLIENT 或 OPERATIONS。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank
        private String requestOrigin;

        /** 归属当前 Client 的公开订单号。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank
        @Size(max = 64)
        private String orderNo;

        /** 订单币种主单位的金额字符串，例如 USD "10.00"。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank
        @Pattern(regexp = ClientApiConstraints.POSITIVE_DECIMAL)
        private String amount;

        /** ISO 4217 三位大写币种代码。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank
        @Pattern(regexp = ClientApiConstraints.CURRENCY)
        private String currency;

        /** 当前退款生命周期；Payment 未决结果保持 PROCESSING。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank
        private String refundStatus;

        /** 创建时提交的稳定退款原因代码。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotBlank
        private String reasonCode;

        /** 失败或取消原因；不适用时明确返回 null。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @JsonInclude(JsonInclude.Include.ALWAYS)
        private String statusReasonCode;

        /** 退款创建的 UTC 时刻。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotNull
        private Instant createdAt;

        /** 权威终态完成的 UTC 时刻；未决时明确返回 null。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @JsonInclude(JsonInclude.Include.ALWAYS)
        private Instant completedAt;

        /** 当前退款表示最后改变的 UTC 时刻。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @NotNull
        private Instant updatedAt;

        /** 既有退款表示版本，独立于 Order 版本。 */
        @JsonProperty(isRequired = OptBoolean.TRUE)
        @PositiveOrZero
        private int version;
    }
}
