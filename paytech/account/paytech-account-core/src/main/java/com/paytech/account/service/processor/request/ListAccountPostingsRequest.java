package com.paytech.account.service.processor.request;

import com.paytech.account.api.enums.AccountEntityType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按入账流与起始序号读取批次（ledger 追平）。 */
@Getter
@AllArgsConstructor
public class ListAccountPostingsRequest {
    /** 每页批次数上限。 */
    public static final int MAX_LIMIT = 200;

    /** 流主体类型。 */
    @NotNull
    private final AccountEntityType entityType;

    /** 流主体标识。 */
    @NotBlank
    private final String entityId;

    /** 大写三字母币种。 */
    @NotBlank
    @Pattern(regexp = "[A-Z]{3}")
    private final String currency;

    /** 流分片号。 */
    @Min(0)
    private final int shardNo;

    /** 起始序号（含）。 */
    @Positive
    private final long fromSequence;

    /** 每页批次数，1–200。 */
    @Min(1)
    @Max(MAX_LIMIT)
    private final int limit;
}
