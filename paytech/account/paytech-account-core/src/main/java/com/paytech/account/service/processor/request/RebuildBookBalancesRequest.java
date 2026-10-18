package com.paytech.account.service.processor.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 账簿余额重建请求。 */
@Getter
@AllArgsConstructor
public class RebuildBookBalancesRequest {
    /** ledger 账簿主键。 */
    @Positive
    private final long ledgerBookId;

    /** 发起人；只用于日志审计。 */
    @Positive
    private final long operatorId;
}
