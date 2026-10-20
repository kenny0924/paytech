package com.paytech.channel.connector.api.cost.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 作废未到生效时间的已批准渠道成本版本请求。 */
@Getter
@Setter
public class CancelChannelCostVersionRequestBody {
    /**
     * 作废人平台用户标识
     * <li>与 makerId 同形态；未接统一身份，由内部调用方传入。</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String operatorId;

    /**
     * 作废原因
     * <li>1–512 个字符，写入变更记录；禁止填写凭据。</li>
     */
    @NotBlank
    @Size(min = 1, max = 512)
    private String reason;
}
