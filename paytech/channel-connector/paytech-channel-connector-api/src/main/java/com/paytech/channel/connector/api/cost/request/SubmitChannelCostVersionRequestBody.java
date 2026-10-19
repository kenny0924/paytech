package com.paytech.channel.connector.api.cost.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.Getter;
import lombok.Setter;

/** 提交渠道成本版本请求。 */
@Getter
@Setter
public class SubmitChannelCostVersionRequestBody {
    /**
     * 提交人平台用户标识
     * <li>必须等于版本编制人；未接统一身份，由内部调用方传入。</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String makerId;
}
