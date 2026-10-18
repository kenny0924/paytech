package com.paytech.ledger.service.processor.request;

import com.paytech.ledger.core.enums.LedgerEntityType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 创建账簿。 */
@Getter
@Setter
public class CreateLedgerBookRequest {
    /** 主体类型 */
    @NotNull
    private LedgerEntityType entityType;

    /** 主体标识：OE 为 entityNo，Finance Entity 为本地主键 */
    @NotBlank
    private String entityId;

    /** ISO 4217 币种 */
    @NotBlank
    private String currency;

    /** 采用的科目表代码 */
    @NotBlank
    private String chartCode;
}
