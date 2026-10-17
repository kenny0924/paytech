package com.paytech.ledger.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 创建账簿请求。 */
@Getter
@Setter
public class CreateLedgerBookRequestBody {
    /**
     * 账簿主体类型
     * <li>OPERATING_ENTITY 或 FINANCE_ENTITY</li>
     */
    @NotBlank
    @Pattern(regexp = "OPERATING_ENTITY|FINANCE_ENTITY", message = "只能为 OPERATING_ENTITY 或 FINANCE_ENTITY")
    private String entityType;

    /**
     * 主体标识
     * <li>OPERATING_ENTITY 为 entityNo（如 AE001，本期不向 CCT 核对存在性）；FINANCE_ENTITY 为财务主体主键</li>
     */
    @NotBlank
    @Size(max = 64)
    private String entityId;

    /**
     * 账簿币种
     * <li>必须是已启用的 ISO 4217 代码</li>
     */
    @NotBlank
    @Pattern(regexp = "[A-Z]{3}", message = "必须是大写 ISO 4217 三位代码")
    private String currency;

    /**
     * 采用的科目表代码
     * <li>例如 STANDARD</li>
     */
    @NotBlank
    @Size(max = 64)
    private String chartCode;
}
