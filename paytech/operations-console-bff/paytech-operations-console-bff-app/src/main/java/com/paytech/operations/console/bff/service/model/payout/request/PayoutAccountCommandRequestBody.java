package com.paytech.operations.console.bff.service.model.payout.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 停用、恢复、关闭收款账户的浏览器请求
 * <li>操作人取当前 Operations 会话；运营不传 Owner，归属由 payout 按账户判断。</li>
 * <li>停用取 BeneficiarySuspendReason、关闭取 BeneficiaryCloseReason，必填；恢复不传。</li>
 */
@Getter
@Setter
public class PayoutAccountCommandRequestBody {
    /** 原因代码 */
    @Pattern(regexp = "[A-Z][A-Z_]{0,63}", message = "必须是大写原因代码")
    private String reasonCode;

    /** 期望的账户版本，取自最近一次读取的 version */
    @NotBlank
    @Pattern(regexp = "0|[1-9][0-9]{0,18}", message = "必须为非负整数字符串")
    private String expectedVersion;
}
