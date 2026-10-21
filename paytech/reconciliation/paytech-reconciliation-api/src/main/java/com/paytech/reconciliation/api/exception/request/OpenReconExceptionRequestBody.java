package com.paytech.reconciliation.api.exception.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 人工开立对账异常请求：只指定来源对象，类型与差异金额由系统确定。 */
@Getter
@Setter
public class OpenReconExceptionRequestBody {
    /**
     * 来源对象类型
     * <li>只允许 PLATFORM_TXN、EXPECTED_BANK_MOVEMENT、BANK_STATEMENT_LINE。</li>
     */
    @NotBlank
    @Pattern(regexp = "PLATFORM_TXN|EXPECTED_BANK_MOVEMENT|BANK_STATEMENT_LINE",
        message = "只能为 PLATFORM_TXN、EXPECTED_BANK_MOVEMENT 或 BANK_STATEMENT_LINE")
    private String sourceType;

    /**
     * 来源对象主键
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String sourceId;
}
