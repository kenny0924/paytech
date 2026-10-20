package com.paytech.channel.connector.service.processor.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 创建 MID 能力条件组及其明细。 */
@Getter
@Setter
public class CreateMidCapabilityRequest {
    /** 操作者 Account ID */
    @NotNull
    private Long actorId;

    /** 所属 MID ID */
    @NotNull
    private Long midId;

    /** 精确支付方式 */
    @NotBlank
    private String paymentMethodCode;

    /** 支付体系或 * */
    @NotBlank
    private String schemeCode;

    /** 国家或 * */
    @NotBlank
    private String countryCode;

    /** 币种或 * */
    @NotBlank
    private String currency;

    /** 四位 MCC */
    @NotBlank
    private String mccCode;

    /** 全部明细 */
    @NotEmpty
    private List<@NotNull @Valid MidCapabilityDetailItem> details;
}
