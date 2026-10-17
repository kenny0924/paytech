package com.paytech.risk.decision.service.processor.request;

import com.paytech.risk.decision.domain.rule.RuleAstValidator.RuleSetDraft;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 规则管理 Owner 输入，基础约束交由公共 Processor 模板验证。 */
@Getter
@AllArgsConstructor
public class CreateRuleSetVersionRequest {
    /** 可信操作者。 */
    @Positive
    private final long actorAccountId;

    /** HTTP 输入转换后的规则草稿。 */
    @NotNull @Valid
    private final RuleSetDraft draft;

    /** 锁键只使用有界的业务编码，不缓存第二份坐标。 */
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    public String getPointCode() { return draft == null ? null : draft.pointCode(); }
    /** 与决策点共同确定实际规则集资源。 */
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    public String getRuleSetCode() { return draft == null ? null : draft.ruleSetCode(); }
}
