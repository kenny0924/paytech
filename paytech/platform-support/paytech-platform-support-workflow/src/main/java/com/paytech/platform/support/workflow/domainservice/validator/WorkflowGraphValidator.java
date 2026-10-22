package com.paytech.platform.support.workflow.domainservice.validator;

import java.util.List;

import com.paytech.platform.support.workflow.core.common.WorkflowLimits;
import com.paytech.platform.support.workflow.core.enums.WorkflowProblemCode;
import com.paytech.platform.support.workflow.domain.validation.WorkflowGraphProblem;
import com.paytech.platform.support.workflow.domain.validation.WorkflowPublicationInput;

/**
 * 流程发布校验（规则 B1，技术方案 3.4）：一次返回全部问题，不在第一个错误处停止。
 *
 * <p>保存草稿时调用方返回问题列表但照常保存；发布时有任何问题即拒绝。校验是纯内存计算：模板状态与人员是否为
 * ACTIVE Operations 账号由调用方事先查询后放入输入。存在回环时跳过依赖拓扑序的并行块与变量校验，避免连带误报。</p>
 */
public final class WorkflowGraphValidator {

    /**
     * 校验一份流程草稿。
     *
     * @param input 流程图、启动表单与调用方查好的模板、人员事实
     * @return 问题列表；没有问题时为空列表
     */
    public List<WorkflowGraphProblem> validate(WorkflowPublicationInput input) {
        ProblemCollector problems = new ProblemCollector();
        GraphTopology topology = new GraphTopology(input.graph());
        if (input.startFormJsonBytes() > WorkflowLimits.MAX_START_FORM_BYTES) {
            problems.graph(WorkflowProblemCode.START_FORM_TOO_LARGE,
                "启动表单大小 " + input.startFormJsonBytes() + " 字节，超过上限 " + WorkflowLimits.MAX_START_FORM_BYTES
                    + " 字节");
        }
        new FormDefinitionRules(problems).check(input.startForm(), "启动表单");
        boolean acyclic = new GraphShapeRules(input.graph(), topology, problems).check(input.graphJsonBytes());
        new ExclusiveNodeRules(input.graph(), topology, problems).check();
        new TaskNodeRules(input, problems).check();
        new NotificationRules(input, problems).check();
        if (acyclic) {
            new ParallelBlockRules(input.graph(), topology, problems).check();
            new VariableFlowRules(input, topology, problems).check();
        }
        return problems.problems();
    }
}
