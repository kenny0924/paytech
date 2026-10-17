package com.paytech.acquiring.api.client.v1.refund.response;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.paytech.acquiring.api.client.v1.common.ClientApiConstraints;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 可退款金额的本地快照；查询不预占额度，创建退款时必须重新校验。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GetRefundableAmountResponseBody {
    /** 不透明、大小写敏感的 Commerce 订单号。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank
    @Size(max = 64)
    private String orderNo;

    /** 订单的 ISO 4217 三位大写币种代码。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank
    @Pattern(regexp = ClientApiConstraints.CURRENCY)
    private String currency;

    /**
     * 退款基数扣除预占及成功退款后的可退金额。
     * <li>使用币种主单位字符串并按币种精度输出，例如 USD "10.00"。</li>
     */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank
    @Pattern(regexp = ClientApiConstraints.DECIMAL)
    private String refundableAmount;

    /** 本地退款位置最后持久化的 UTC 时刻，不是本次查询时间。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotNull
    private Instant asOf;

    /** 既有查询合同的非负退款位置版本，独立于 Order 版本。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @PositiveOrZero
    private int version;
}
