package com.paytech.ledger.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 以投影重建方式发起试算异常处置请求。 */
@Getter
@Setter
public class StartProjectionRebuildRequestBody {
    /**
     * 编制人平台用户标识
     * <li>未接统一身份，由内部调用方传入。</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String makerId;
}
