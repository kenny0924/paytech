package com.paytech.platform.support.test.base;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.platform.support.api.workflow.request.CreateWorkflowActionTemplateRequestBody;
import com.paytech.platform.support.api.workflow.request.CreateWorkflowDefinitionRequestBody;
import com.paytech.platform.support.api.workflow.request.DecideWorkflowApprovalRequestBody;
import com.paytech.platform.support.api.workflow.request.PublishWorkflowDefinitionRequestBody;
import com.paytech.platform.support.api.workflow.request.SaveWorkflowDraftRequestBody;
import com.paytech.platform.support.api.workflow.request.StartWorkflowInstanceRequestBody;
import com.paytech.platform.support.api.workflow.request.SubmitWorkflowActionRequestBody;
import com.paytech.platform.support.api.workflow.response.WorkflowDraftResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowInstanceDetailResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowInstanceDetailResponseBody.ActionRunBody;
import com.paytech.platform.support.api.workflow.response.WorkflowInstanceDetailResponseBody.ApprovalBody;
import com.paytech.platform.support.api.workflow.response.WorkflowMutationResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowSubmissionResponseBody;
import com.paytech.platform.support.test.builder.OperatorBuilder;
import com.paytech.platform.support.test.builder.OperatorBuilder.Operator;
import com.paytech.platform.support.test.builder.WorkflowGraphJson;
import com.paytech.platform.support.test.checker.PlatformResponseChecker;
import com.paytech.platform.support.test.dal.WorkflowTestMapper;
import com.paytech.platform.support.test.workflow.WorkflowFixture;
import com.paytech.platform.support.workflow.core.enums.ActionRunStatus;

import tools.jackson.databind.JsonNode;

/**
 * 工作流入口测试的公共调用：模板、定义与实例全部经正式 HTTP 入口创建，不直接写工作流表。
 *
 * <p>本类准备先确认没有残留的工作流补偿任务，再插入一名持有全部目录权限的流程管理员。</p>
 */
public abstract class WorkflowApiTestBase extends OperationsApiTestBase {

    protected static final String TEMPLATES = "/workflow-admin/v1/action-templates";
    protected static final String DEFINITIONS = "/workflow-admin/v1/definitions";
    protected static final String RUNTIME = "/workflow/v1";

    /** 实例标题序号，使同一流程的实例标题可区分。 */
    private static final java.util.concurrent.atomic.AtomicInteger TITLES = new java.util.concurrent.atomic.AtomicInteger();

    @Autowired
    protected WorkflowTestMapper workflowData;

    @Autowired
    protected WorkflowFixture workflowFixture;

    /** 流程管理员。 */
    protected Operator admin;

    /** 工作流公共准备。 */
    @BeforeEach
    void setUpWorkflowMocks() {
        workflowFixture.assertEmpty();
        admin = operationsMocker.operator(OperatorBuilder.administrator());
    }

    /** 再插入一名持有全部权限的人员。 */
    protected Operator person() {
        return operationsMocker.operator(OperatorBuilder.administrator());
    }

    /** 新建动作模板并返回 ID。 */
    protected long template(String code, String type, JsonNode config) {
        var mutation = post(TEMPLATES, admin, new CreateWorkflowActionTemplateRequestBody(code, code + " 模板", type,
            null, config)).assertOk().as(WorkflowMutationResponseBody.class);
        return Long.parseLong(mutation.getObjectId());
    }

    /** 不要求说明的人工确认模板。 */
    protected long confirmTemplate(String code) {
        return template(code, "MANUAL_CONFIRM", WorkflowGraphJson.confirmConfig(false));
    }

    /** 新建流程定义并返回 ID。 */
    protected long createDefinition(String code) {
        var mutation = post(DEFINITIONS, admin, new CreateWorkflowDefinitionRequestBody(code, code + " 流程", null))
            .assertOk().as(WorkflowMutationResponseBody.class);
        return Long.parseLong(mutation.getObjectId());
    }

    /** 以定义当前版本保存草稿。 */
    protected WorkflowDraftResponseBody saveDraft(long definitionId, JsonNode graph, JsonNode startForm) {
        int version = ((Number) workflowData.definition(definitionId).get("version")).intValue();
        return put(DEFINITIONS + "/" + definitionId + "/draft", admin,
            new SaveWorkflowDraftRequestBody("流程 " + definitionId, null, graph, startForm, version)).assertOk()
            .as(WorkflowDraftResponseBody.class);
    }

    /** 以定义当前版本发布。 */
    protected PlatformResponseChecker publish(long definitionId) {
        int version = ((Number) workflowData.definition(definitionId).get("version")).intValue();
        return post(DEFINITIONS + "/" + definitionId + "/publication", admin,
            new PublishWorkflowDefinitionRequestBody(version));
    }

    /** 新建、保存并发布，返回定义 ID。 */
    protected long published(String code, JsonNode graph, JsonNode startForm) {
        long definitionId = createDefinition(code);
        saveDraft(definitionId, graph, startForm);
        publish(definitionId).assertOk();
        return definitionId;
    }

    /** 以当前发布版本发起实例。 */
    protected PlatformResponseChecker start(long definitionId, Operator starter, JsonNode startValues,
        List<StartWorkflowInstanceRequestBody.NodeApprovers> approvers,
        List<StartWorkflowInstanceRequestBody.ActionExecutor> executors) {
        int versionNo = ((Number) workflowData.definition(definitionId).get("current_version_no")).intValue();
        int sequence = TITLES.incrementAndGet();
        // 业务关联键逐次不同：同键 RUNNING 实例会被防重返回（REQ-05），防重场景由专门用例构造。
        return post(RUNTIME + "/instances", starter, new StartWorkflowInstanceRequestBody(Long.toString(definitionId),
            versionNo, "实例 " + definitionId + "-" + sequence, "BK-" + definitionId + "-" + sequence, startValues,
            approvers, executors, null));
    }

    /** 以默认人员发起实例并返回 ID。 */
    protected long started(long definitionId, Operator starter, JsonNode startValues) {
        var mutation = start(definitionId, starter, startValues, null, null).assertOk()
            .as(WorkflowMutationResponseBody.class);
        return Long.parseLong(mutation.getObjectId());
    }

    /** 实例详情。 */
    protected WorkflowInstanceDetailResponseBody detail(long instanceId, Operator viewer) {
        return get(RUNTIME + "/instances/" + instanceId, viewer).assertOk()
            .as(WorkflowInstanceDetailResponseBody.class);
    }

    /** 某动作最新一轮待处理（PENDING）的动作运行 ID。 */
    protected String openAction(long instanceId, String actionKey) {
        return detail(instanceId, admin).getActionRuns().stream()
            .filter(run -> run.getActionKey().equals(actionKey))
            .filter(run -> ActionRunStatus.PENDING.getCode().equals(run.getStatus()))
            .map(ActionRunBody::getActionRunId).reduce((first, last) -> last)
            .orElseThrow(() -> new AssertionError("没有可提交的动作：" + actionKey));
    }

    /** 某审批人待决定的审批 ID。 */
    protected String openApproval(long instanceId, Operator approver) {
        return detail(instanceId, admin).getApprovals().stream()
            .filter(row -> row.getApproverId().equals(approver.actor()) && "PENDING".equals(row.getStatus()))
            .map(ApprovalBody::getApprovalId).findFirst()
            .orElseThrow(() -> new AssertionError("没有待决定的审批：" + approver.actor()));
    }

    /** 完成动作（#19）。 */
    protected PlatformResponseChecker submit(String actionRunId, Operator executor, JsonNode output) {
        return post(RUNTIME + "/action-runs/" + actionRunId + "/submission", executor,
            new SubmitWorkflowActionRequestBody(output));
    }

    /** 完成动作并要求状态为 COMPLETED。 */
    protected void confirm(long instanceId, String actionKey, Operator executor) {
        var result = submit(openAction(instanceId, actionKey), executor, WorkflowGraphJson.empty()).assertOk()
            .as(WorkflowSubmissionResponseBody.class);
        assertEquals(ActionRunStatus.COMPLETED.getCode(), result.getStatus(), actionKey + " 完成结果");
    }

    /** 审批决定。 */
    protected PlatformResponseChecker decide(long instanceId, Operator approver, String decision, String comment,
        String rejectTarget) {
        return post(RUNTIME + "/approvals/" + openApproval(instanceId, approver) + "/decision", approver,
            new DecideWorkflowApprovalRequestBody(decision, comment, rejectTarget));
    }

    /** 同意。 */
    protected void approve(long instanceId, Operator approver) {
        decide(instanceId, approver, "APPROVE", null, null).assertOk();
    }

    /** 实例状态列。 */
    protected String instanceStatus(long instanceId) {
        return (String) workflowData.instance(instanceId).get("status");
    }

    /** 节点各轮运行状态，按激活顺序：nodeKey#round=STATUS。 */
    protected List<String> nodeTrail(long instanceId) {
        return workflowData.nodeRuns(instanceId).stream().filter(row -> !"start".equals(row.get("node_key")))
            .map(WorkflowApiTestBase::trail).toList();
    }

    private static String trail(Map<String, Object> row) {
        return row.get("node_key") + "#" + ((Number) row.get("round_no")).intValue() + "=" + row.get("status");
    }
}
