package com.paytech.reconciliation.service.processor.request;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 执行一张渠道账单的匹配与对平（CHANNEL_STATEMENT_MATCH 任务）。 */
@Getter
@AllArgsConstructor
public class MatchChannelStatementRequest {
    /** 渠道账单主键 */
    @NotNull
    private final Long statementId;
}
