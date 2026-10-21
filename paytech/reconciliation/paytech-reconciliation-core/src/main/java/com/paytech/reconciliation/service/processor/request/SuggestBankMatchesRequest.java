package com.paytech.reconciliation.service.processor.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 为一个银行账户出匹配建议（BANK_MATCH_SUGGEST 任务）。 */
@Getter
@AllArgsConstructor
public class SuggestBankMatchesRequest {
    /** 银行账户主键 */
    @NotNull
    private final Long cashAccountId;
}
