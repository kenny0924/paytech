package com.paytech.platform.support.workflow.domainservice.validator;

import java.util.ArrayList;
import java.util.List;

import com.paytech.platform.support.workflow.core.enums.WorkflowProblemCode;
import com.paytech.platform.support.workflow.domain.validation.WorkflowGraphProblem;

/**
 * 按发现顺序收集校验问题。
 */
final class ProblemCollector {

    private final List<WorkflowGraphProblem> problems = new ArrayList<>();

    /**
     * 记录节点问题。
     *
     * @param code 问题码
     * @param nodeKey 节点 key
     * @param message 中文说明
     */
    void node(WorkflowProblemCode code, String nodeKey, String message) {
        problems.add(new WorkflowGraphProblem(code, nodeKey, null, null, message));
    }

    /**
     * 记录节点内条目的问题。
     *
     * @param code 问题码
     * @param nodeKey 节点 key；流程设置或启动表单时为 null
     * @param itemKey 条目 key
     * @param message 中文说明
     */
    void item(WorkflowProblemCode code, String nodeKey, String itemKey, String message) {
        problems.add(new WorkflowGraphProblem(code, nodeKey, null, itemKey, message));
    }

    /**
     * 记录连线问题。
     *
     * @param code 问题码
     * @param nodeKey 相关节点 key；可为 null
     * @param edgeKey 连线 key
     * @param message 中文说明
     */
    void edge(WorkflowProblemCode code, String nodeKey, String edgeKey, String message) {
        problems.add(new WorkflowGraphProblem(code, nodeKey, edgeKey, null, message));
    }

    /**
     * 记录整张图的问题。
     *
     * @param code 问题码
     * @param message 中文说明
     */
    void graph(WorkflowProblemCode code, String message) {
        problems.add(new WorkflowGraphProblem(code, null, null, null, message));
    }

    /**
     * 追加已构造的问题。
     *
     * @param found 问题
     */
    void addAll(List<WorkflowGraphProblem> found) {
        problems.addAll(found);
    }

    /**
     * 是否已记录某类问题。
     *
     * @param code 问题码
     * @return 已记录返回 true
     */
    boolean has(WorkflowProblemCode code) {
        return problems.stream().anyMatch(problem -> problem.code() == code);
    }

    /**
     * 返回收集到的问题。
     *
     * @return 不可变列表
     */
    List<WorkflowGraphProblem> problems() {
        return List.copyOf(problems);
    }
}
