package com.paytech.billing.api.request;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 创建渠道成本版本请求。 */
@Getter
@Setter
public class CreateChannelCostVersionRequestBody {
    /**
     * 编制人平台用户标识
     * <li>未接统一身份，由内部调用方传入。</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String makerId;

    /**
     * 版本开始参与匹配的时间（UTC）
     * <li>批准时已到该时间则直接生效，否则保持 APPROVED。</li>
     */
    @NotNull
    private Instant effectiveFrom;

    /**
     * 完整适用范围集
     * <li>非空，随版本一次写入；范围序号与六个匹配维度组合在版本内唯一。</li>
     */
    @NotNull
    @Size(max = 200)
    private List<@Valid ChannelCostScopeRequestBody> scopes;
}
