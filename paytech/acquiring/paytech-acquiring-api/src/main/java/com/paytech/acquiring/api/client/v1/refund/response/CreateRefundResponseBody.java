package com.paytech.commerce.client.api.v1.refund.response;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.paytech.commerce.client.api.v1.common.ClientApiConstraints;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 退款已在本地受理的创建回执，不表示渠道退款成功。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateRefundResponseBody {
    /** 永久唯一、大小写敏感的公开退款号。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank
    @Size(max = 64)
    private String refundRequestNo;

    /** Client 命名空间内的原业务退款号。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = ClientApiConstraints.CLIENT_REFERENCE)
    private String clientRefundId;

    /** 原退款所属的公开订单号。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank
    @Size(max = 64)
    private String orderNo;

    /** 按订单币种精度输出的主单位金额字符串，例如 USD "10.00"。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank
    @Pattern(regexp = ClientApiConstraints.POSITIVE_DECIMAL)
    private String amount;

    /** 继承订单的 ISO 4217 三位币种代码。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotBlank
    @Pattern(regexp = ClientApiConstraints.CURRENCY)
    private String currency;

    /** 原退款创建的 UTC 时刻；重复提交不刷新。 */
    @JsonProperty(isRequired = OptBoolean.TRUE)
    @NotNull
    private Instant createdAt;
}
