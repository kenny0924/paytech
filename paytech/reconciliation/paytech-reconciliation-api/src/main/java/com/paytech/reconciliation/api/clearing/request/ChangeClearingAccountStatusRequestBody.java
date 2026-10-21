package com.paytech.reconciliation.api.clearing.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 启用或停用清算账户请求。 */
@Getter
@Setter
public class ChangeClearingAccountStatusRequestBody {
    /**
     * 目标状态
     * <li>ACTIVE 或 INACTIVE；停用后不再接收新的平台流水和渠道账单，也不参与解析。</li>
     */
    @NotBlank
    @Pattern(regexp = "ACTIVE|INACTIVE", message = "只能为 ACTIVE 或 INACTIVE")
    private String status;
}
