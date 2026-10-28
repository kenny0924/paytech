package com.paytech.client.master.domain.onboarding.requirement;

/**
 * 某类型声明的最新一条
 *
 * @param declaredValue 声明取值 TRUE 或 FALSE
 * @param current 是否针对申请当前资料版本与所配置的文档版本
 */
public record DeclarationFact(String declaredValue, boolean current) {
}
