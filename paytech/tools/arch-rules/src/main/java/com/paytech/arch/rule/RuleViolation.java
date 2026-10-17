package com.paytech.arch.rule;

/**
 * 一条规则违规。
 *
 * @param ruleId 规则编号（R1–R12）
 * @param subject 违规对象：模块坐标、类名或文件路径（含行号）
 * @param message 中文说明
 * @author Claude
 */
public record RuleViolation(String ruleId, String subject, String message) {

    @Override
    public String toString() {
        return "[" + ruleId + "] " + subject + ": " + message;
    }
}
