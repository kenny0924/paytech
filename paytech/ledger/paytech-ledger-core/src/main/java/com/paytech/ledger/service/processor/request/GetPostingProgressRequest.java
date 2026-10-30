package com.paytech.ledger.service.processor.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 查询一本账簿的两段入账进度。 */
@Getter
@AllArgsConstructor
public class GetPostingProgressRequest {
    /** 账簿主键 */
    @NotNull
    private final Long ledgerBookId;
}
