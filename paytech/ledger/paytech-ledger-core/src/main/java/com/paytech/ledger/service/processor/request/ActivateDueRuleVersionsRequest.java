package com.paytech.ledger.service.processor.request;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一轮到期规则版本生效（XXL-Job accountingRuleVersionActivation）。 */
@Getter
@AllArgsConstructor
public class ActivateDueRuleVersionsRequest {
    /** 判断是否到期的时点（UTC，微秒精度） */
    @NotNull
    private final Instant asOf;
}
