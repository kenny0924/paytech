package com.paytech.client.console.bff.service.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * Client 门户设默认、关闭收款账户的浏览器请求
 * <li>Owner 与操作人由 BFF 按当前工作区与成员填入。</li>
 * <li>关闭必须给 BeneficiaryCloseReason；设默认不传原因。</li>
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
