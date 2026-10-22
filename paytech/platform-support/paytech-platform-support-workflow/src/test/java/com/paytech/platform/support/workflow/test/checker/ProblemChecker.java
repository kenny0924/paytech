package com.paytech.platform.support.workflow.test.checker;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;

import com.paytech.platform.support.workflow.core.enums.WorkflowProblemCode;
import com.paytech.platform.support.workflow.domain.validation.WorkflowGraphProblem;

/**
 * 校验问题列表断言。
 */
public final class ProblemChecker {

    private final List<WorkflowGraphProblem> problems;

    /**
     * 包装问题列表。
     *
     * @param problems 问题列表
     */
    private ProblemChecker(List<WorkflowGraphProblem> problems) {
        this.problems = problems;
    }

    /**
     * 包装问题列表。
     *
     * @param problems 问题列表
     * @return 断言器
     */
    public static ProblemChecker of(List<WorkflowGraphProblem> problems) {
        return new ProblemChecker(problems);
    }

    /**
     * 断言没有任何问题。
     *
     * @return 断言器
     */
    public ProblemChecker none() {
        Assertions.assertEquals(List.of(), problems);
        return this;
    }

    /**
     * 断言存在指向该节点的问题。
     *
     * @param code 问题码
     * @param nodeKey 节点 key；整图问题传 null
     * @return 断言器
     */
    public ProblemChecker node(WorkflowProblemCode code, String nodeKey) {
        Assertions.assertTrue(problems.stream().anyMatch(p -> p.code() == code && Objects.equals(p.nodeKey(), nodeKey)),
            () -> "缺少问题 " + code + " @node " + nodeKey + "，实际：" + problems);
        return this;
    }

    /**
     * 断言存在指向该连线的问题。
     *
     * @param code 问题码
     * @param edgeKey 连线 key
     * @return 断言器
     */
    public ProblemChecker edge(WorkflowProblemCode code, String edgeKey) {
        Assertions.assertTrue(problems.stream().anyMatch(p -> p.code() == code && Objects.equals(p.edgeKey(), edgeKey)),
            () -> "缺少问题 " + code + " @edge " + edgeKey + "，实际：" + problems);
        return this;
    }

    /**
     * 断言存在指向该条目的问题。
     *
     * @param code 问题码
     * @param itemKey 条目 key
     * @return 断言器
     */
    public ProblemChecker item(WorkflowProblemCode code, String itemKey) {
        Assertions.assertTrue(problems.stream().anyMatch(p -> p.code() == code && Objects.equals(p.itemKey(), itemKey)),
            () -> "缺少问题 " + code + " @item " + itemKey + "，实际：" + problems);
        return this;
    }

    /**
     * 断言问题码集合恰好为给定集合（不计次数），且每条问题都有中文说明。
     *
     * @param codes 期望的问题码
     * @return 断言器
     */
    public ProblemChecker exactly(WorkflowProblemCode... codes) {
        Assertions.assertEquals(Set.of(codes),
            problems.stream().map(WorkflowGraphProblem::code).collect(Collectors.toSet()),
            () -> "实际：" + problems);
        problems.forEach(p -> Assertions.assertFalse(p.message().isBlank()));
        return this;
    }

    /**
     * 断言不含某个问题码。
     *
     * @param code 问题码
     * @return 断言器
     */
    public ProblemChecker without(WorkflowProblemCode code) {
        Assertions.assertTrue(problems.stream().noneMatch(p -> p.code() == code), () -> "不应出现 " + code + "：" + problems);
        return this;
    }
}
