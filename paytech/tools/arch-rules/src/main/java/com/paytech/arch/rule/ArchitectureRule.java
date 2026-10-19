package com.paytech.arch.rule;

import com.paytech.arch.scope.ServiceArchitectureScope;

import java.util.List;

/**
 * 针对单个服务的边界规则。
 *
 * @author Claude
 */
public interface ArchitectureRule {

    /**
     * 规则编号。
     *
     * @return R1–R14
     */
    String id();

    /**
     * 规则说明。
     *
     * @return 中文说明
     */
    String description();

    /**
     * 检查服务的目标模块。
     *
     * @param scope 检查对象
     * @return 违规列表；无违规时为空
     */
    List<RuleViolation> check(ServiceArchitectureScope scope);
}
