package com.paytech.ledger.api.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 追平一条 account 过账流请求；流身份即 AccountPosted 聚合 ID 的四个组成部分。 */
@Getter
@Setter
public class CatchUpPostingStreamRequestBody {
    /** 流主体类型：OPERATING_ENTITY 或 FINANCE_ENTITY。 */
    @NotBlank
    @Pattern(regexp = "OPERATING_ENTITY|FINANCE_ENTITY", message = "主体类型非法")
    private String entityType;

    /** 流主体标识：OE 为 entityNo，Finance Entity 为十进制主键。 */
    @NotBlank
    @Pattern(regexp = "[A-Z0-9]{1,19}", message = "主体标识非法")
    private String entityId;

    /** 大写 ISO 4217 币种。 */
    @NotBlank
    @Pattern(regexp = "[A-Z]{3}", message = "币种非法")
    private String currency;

    /** 流分片号，从 0 开始。 */
    @NotNull
    @Min(0)
    private Integer shardNo;

    /**
     * 本次最多处理的批次数
     * <li>可空，默认 200；1–10000。</li>
     */
    @Min(1)
    @Max(10000)
    private Integer maxBatches;
}
