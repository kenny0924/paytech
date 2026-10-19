package com.paytech.commercial.domain;

import java.time.Instant;
import java.util.Map;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 价格命中的内部请求：冻结版本父链、商业时点、币种与原始维度事实（由入口转换，字段格式已在 HTTP 边界校验）。 */
@Getter
@AllArgsConstructor
public class CommercialPriceQuery {
    /** 版本 ID */
    private final long termVersionId;

    /** 准确 OE */
    @NotNull
    private final String entityNo;

    /** 合同 ID */
    private final long agreementId;

    /** 商业时点 */
    @NotNull
    private final Instant contextAsOfTime;

    /** 条款类型 */
    @NotNull
    private final String termType;

    /** 交易币种 */
    @NotNull
    private final String currency;

    /** Client Owner，可空 */
    private final Long clientId;

    /** Local Partner Owner，可空 */
    private final Long localPartnerId;

    /** 支付方式，可空 */
    private final String paymentMethodCode;

    /** 原始维度事实（键为维度代码，值可空） */
    @NotNull
    private final Map<String, String> dimensions;
}
