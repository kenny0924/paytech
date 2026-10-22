package com.paytech.platform.support.workflow.test.builder;

import static com.paytech.platform.support.workflow.test.builder.WorkflowGraphBuilder.clause;
import static com.paytech.platform.support.workflow.test.builder.WorkflowGraphBuilder.when;

import com.paytech.platform.support.workflow.core.enums.ApprovalMode;
import com.paytech.platform.support.workflow.core.enums.ConditionOperator;

/**
 * 测试共用的流程图样例。
 */
public final class SampleGraphs {

    private SampleGraphs() {
    }

    /**
     * “新渠道上线”完整样例，覆盖全部节点类型且能通过发布校验：
     *
     * <pre>
     * start → approve（审批人甲）→ commerce（表单动作 commerceProduct，执行人甲；审批人乙）→ applePayGate
     * applePayGate：e_apple（start.paymentMethod = APPLE_PAY）→ applePay（人工确认，执行人乙）→ fork；默认 e_skip → fork
     * fork：b_risk → risk1（上传文件，执行人乙）→ risk2（审批人甲）→ join
     *       b_channel → channel1（页面任务，channelCode = start.channelCode，执行人甲）→ join
     * join → final（审批人甲、乙，ALL）→ end
     * </pre>
     *
     * @return 构造器，可继续追加负例改动
     */
    public static WorkflowGraphBuilder newChannelLaunch() {
        return WorkflowGraphBuilder.graph()
            .start("start")
            .task("approve", task -> task.approvers(ApprovalMode.ANY, WorkflowFixtures.APPROVER_A))
            .task("commerce", task -> task.action("commerceProduct", WorkflowFixtures.TPL_FORM, WorkflowFixtures.EXECUTOR_A)
                .approvers(ApprovalMode.ANY, WorkflowFixtures.APPROVER_B))
            .exclusive("applePayGate", "e_skip", when("e_apple", clause("start.paymentMethod", ConditionOperator.EQ,
                "APPLE_PAY")))
            .task("applePay", task -> task.action("applePayConfirm", WorkflowFixtures.TPL_CONFIRM,
                WorkflowFixtures.EXECUTOR_B))
            .split("fork")
            .task("risk1", task -> task.action("riskFiles", WorkflowFixtures.TPL_UPLOAD, WorkflowFixtures.EXECUTOR_B))
            .task("risk2", task -> task.approvers(ApprovalMode.ANY, WorkflowFixtures.APPROVER_A))
            .task("channel1", task -> task.actionWithVariableInput("channelSetup", WorkflowFixtures.TPL_PAGE,
                "channelCode", "start.channelCode"))
            .join("join")
            .task("final", task -> task.approvers(ApprovalMode.ALL, WorkflowFixtures.APPROVER_A,
                WorkflowFixtures.APPROVER_B))
            .end("end")
            .chain("start", "approve", "commerce", "applePayGate")
            .edge("e_apple", "applePayGate", "applePay")
            .edge("e_skip", "applePayGate", "fork")
            .edge("applePay_fork", "applePay", "fork")
            .edge("b_risk", "fork", "risk1")
            .chain("risk1", "risk2", "join")
            .edge("b_channel", "fork", "channel1")
            .chain("channel1", "join", "final", "end");
    }
}
