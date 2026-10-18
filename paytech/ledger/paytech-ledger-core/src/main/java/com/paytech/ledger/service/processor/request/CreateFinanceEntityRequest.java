package com.paytech.ledger.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/** 创建财务主体。 */
@Getter
@Setter
public class CreateFinanceEntityRequest {
    /** 财务主体代码 */
    @NotBlank
    private String entityCode;

    /** CCT 法律 Party 标识 */
    @NotNull
    private Long legalPartyId;

    /** 显示名称 */
    @NotBlank
    private String displayName;
}
