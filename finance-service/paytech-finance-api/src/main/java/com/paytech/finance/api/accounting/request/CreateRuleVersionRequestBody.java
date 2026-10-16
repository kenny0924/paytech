package com.paytech.finance.api.accounting.request;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 创建自动记账规则版本请求。 */
@Getter
@Setter
public class CreateRuleVersionRequestBody {
    /**
     * 编制人平台用户标识
     * <li>未接统一身份，由内部调用方传入。</li>
     */
    @NotBlank
    @Pattern(regexp = "[1-9][0-9]{0,18}", message = "必须是正十进制整数字符串")
    private String makerId;

    /**
     * 版本开始适用的时间（UTC）
     * <li>批准时已到该时间则直接生效，否则保持 APPROVED。</li>
     */
    @NotNull
    private Instant effectiveFrom;

    /**
     * 完整有序规则行集
     * <li>非空，随版本一次写入；同一指令类型内行号、行代码唯一。</li>
     */
    @NotNull
    @Size(max = 200)
    private List<@Valid RuleLineRequestBody> lines;
}
