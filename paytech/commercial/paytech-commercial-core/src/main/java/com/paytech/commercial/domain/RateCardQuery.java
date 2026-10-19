package com.paytech.commercial.domain;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 费率卡查询：Client、时点与可选币种。 */
@Getter
@AllArgsConstructor
public class RateCardQuery {
    /** Client ID */
    @Positive
    private final long clientId;

    /** 费率卡时点 */
    @NotNull
    private final Instant asOf;

    /** 只返回该币种的价格块，可空 */
    private final String currency;
}
