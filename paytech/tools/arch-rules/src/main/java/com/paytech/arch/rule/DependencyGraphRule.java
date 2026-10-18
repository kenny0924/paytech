package com.paytech.arch.rule;

import com.paytech.arch.model.MavenModule;

import java.util.Collection;
import java.util.List;

/**
 * 基于 Maven 依赖图（读取 pom.xml，不调用 Maven）的规则部分，可对整个 Reactor 一次执行。
 *
 * @author Claude
 */
public interface DependencyGraphRule {

    /**
     * 规则编号。
     *
     * @return 编号
     */
    String id();

    /**
     * 检查每个模块声明的非 test 依赖。
     *
     * @param modules 模块集合
     * @return 违规列表
     */
    List<RuleViolation> checkDependencyGraph(Collection<MavenModule> modules);
}
