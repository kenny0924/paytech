package com.paytech.ledger.service.processor.request;

import com.paytech.account.api.enums.AccountEntityType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 追平一条 account 过账流。 */
@Getter
@Setter
public class CatchUpPostingStreamRequest {
    /** 流主体类型 */
    @NotNull
    private AccountEntityType entityType;

    /** 流主体标识：OE 为 entityNo，Finance Entity 为十进制主键 */
    @NotBlank
    private String entityId;

    /** 大写 ISO 4217 币种 */
    @NotBlank
    private String currency;

    /** 流分片号 */
    @NotNull
    @Min(0)
    private Integer shardNo;

    /** 本次最多处理的批次数 */
    @NotNull
    @Min(1)
    @Max(10000)
    private Integer maxBatches;
}
