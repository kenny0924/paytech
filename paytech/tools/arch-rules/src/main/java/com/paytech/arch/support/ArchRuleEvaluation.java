package com.paytech.arch.support;

import com.paytech.arch.rule.RuleViolation;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;

import java.util.List;

/**
 * 执行 ArchUnit 规则并把失败明细转换为 {@link RuleViolation}。
 *
 * @author Claude
 */
public final class ArchRuleEvaluation {

    private ArchRuleEvaluation() {
    }

    /**
     * 执行规则；"that" 部分没有匹配类时视为通过（服务可能尚无该角色模块）。
     *
     * @param ruleId 规则编号
     * @param rule ArchUnit 规则
     * @param classes 导入的类
     * @return 违规列表
     */
    public static List<RuleViolation> violations(String ruleId, ArchRule rule, JavaClasses classes) {
        EvaluationResult result = rule.allowEmptyShould(true).evaluate(classes);
        if (!result.hasViolation()) {
            return List.of();
        }
        return result.getFailureReport().getDetails().stream()
            .map(detail -> new RuleViolation(ruleId, "classes", detail))
            .toList();
    }
}
