package com.paytech.arch.test;

import com.paytech.arch.rule.RuleViolation;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;

/**
 * 规则违规断言。
 *
 * @author Claude
 */
public final class ViolationChecker {

    private final List<RuleViolation> violations;

    private ViolationChecker(List<RuleViolation> violations) {
        this.violations = violations;
    }

    /**
     * 包装违规列表。
     *
     * @param violations 违规
     * @return 断言器
     */
    public static ViolationChecker of(List<RuleViolation> violations) {
        return new ViolationChecker(violations);
    }

    /**
     * 断言没有违规。
     *
     * @return 断言器
     */
    public ViolationChecker none() {
        Assertions.assertTrue(violations.isEmpty(), () -> "预期无违规，实际:\n" + describe());
        return this;
    }

    /**
     * 断言违规条数。
     *
     * @param expected 条数
     * @return 断言器
     */
    public ViolationChecker count(int expected) {
        Assertions.assertEquals(expected, violations.size(), () -> "违规条数不符:\n" + describe());
        return this;
    }

    /**
     * 断言全部违规属于指定规则。
     *
     * @param ruleId 规则编号
     * @return 断言器
     */
    public ViolationChecker allFrom(String ruleId) {
        Assertions.assertFalse(violations.isEmpty(), "预期存在违规");
        Assertions.assertTrue(violations.stream().allMatch(violation -> violation.ruleId().equals(ruleId)),
            () -> "存在其他规则的违规:\n" + describe());
        return this;
    }

    /**
     * 断言存在一条对象与说明都包含给定片段的违规。
     *
     * @param subjectFragment 对象片段
     * @param messageFragment 说明片段
     * @return 断言器
     */
    public ViolationChecker contains(String subjectFragment, String messageFragment) {
        boolean found = violations.stream().anyMatch(violation -> violation.subject().contains(subjectFragment)
            && violation.message().contains(messageFragment));
        Assertions.assertTrue(found,
            () -> "未找到违规 [" + subjectFragment + "] " + messageFragment + "，实际:\n" + describe());
        return this;
    }

    /**
     * 断言存在一条说明包含全部片段的违规（用于 ArchUnit 明细）。
     *
     * @param fragments 片段
     * @return 断言器
     */
    public ViolationChecker containsMessage(String... fragments) {
        boolean found = violations.stream().anyMatch(violation -> {
            for (String fragment : fragments) {
                if (!violation.message().contains(fragment)) {
                    return false;
                }
            }
            return true;
        });
        Assertions.assertTrue(found, () -> "未找到包含 " + List.of(fragments) + " 的违规，实际:\n" + describe());
        return this;
    }

    private String describe() {
        return violations.stream().map(RuleViolation::toString).collect(Collectors.joining("\n"));
    }
}
