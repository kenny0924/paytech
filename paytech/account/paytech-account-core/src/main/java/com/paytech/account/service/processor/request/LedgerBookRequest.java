package com.paytech.account.service.processor.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按 ledger 账簿主键的读取请求。 */
@Getter
@AllArgsConstructor
public class LedgerBookRequest {
    /** ledger 账簿主键。 */
    @Positive
    private final long ledgerBookId;
}
