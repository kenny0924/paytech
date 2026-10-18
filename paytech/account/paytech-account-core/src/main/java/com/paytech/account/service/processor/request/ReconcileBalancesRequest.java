package com.paytech.account.service.processor.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 日终核对请求。 */
@Getter
@AllArgsConstructor
public class ReconcileBalancesRequest {
    /** 核对日期（UTC）；同日重复核对时更新同一差异记录。 */
    @NotNull
    private final LocalDate reconDate;
}
