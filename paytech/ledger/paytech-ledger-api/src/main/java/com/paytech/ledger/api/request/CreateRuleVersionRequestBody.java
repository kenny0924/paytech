package com.paytech.ledger.api.request;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** 创建自动记账规则版本请求；编制人取自 X-Gateway-Actor-Subject。 */
@Getter
@Setter
public class CreateRuleVersionRequestBody {
    /**
     * 版本开始适用的时间（UTC）
     * <li>批准时已到该时间则直接生效，生效时间记为批准时间；否则保持 APPROVED，到期生效。</li>
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
