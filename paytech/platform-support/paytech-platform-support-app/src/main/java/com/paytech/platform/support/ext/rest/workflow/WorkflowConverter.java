package com.paytech.platform.support.ext.rest.workflow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.common.utils.EnumUtils;
import com.paytech.platform.support.api.workflow.request.ChangeWorkflowStatusRequestBody;
import com.paytech.platform.support.api.workflow.request.CreateWorkflowActionTemplateRequestBody;
import com.paytech.platform.support.api.workflow.request.CreateWorkflowDefinitionRequestBody;
import com.paytech.platform.support.api.workflow.request.DecideWorkflowApprovalRequestBody;
import com.paytech.platform.support.api.workflow.request.SaveWorkflowDraftRequestBody;
import com.paytech.platform.support.api.workflow.request.StartWorkflowInstanceRequestBody;
import com.paytech.platform.support.api.workflow.request.TransferWorkflowTaskRequestBody;
import com.paytech.platform.support.api.workflow.request.UpdateWorkflowActionTemplateRequestBody;
import com.paytech.platform.support.api.workflow.response.WorkflowDraftResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowMutationResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowProblemBody;
import com.paytech.platform.support.api.workflow.response.WorkflowSubmissionResponseBody;
import com.paytech.platform.support.workflow.core.enums.ApprovalDecision;
import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;
import com.paytech.platform.support.workflow.service.processor.request.ChangeWorkflowStatusRequest;
import com.paytech.platform.support.workflow.service.processor.request.CreateActionTemplateRequest;
import com.paytech.platform.support.workflow.service.processor.request.CreateDefinitionRequest;
import com.paytech.platform.support.workflow.service.processor.request.DecideApprovalRequest;
import com.paytech.platform.support.workflow.service.processor.request.SaveDraftRequest;
import com.paytech.platform.support.workflow.service.processor.request.StartInstanceRequest;
import com.paytech.platform.support.workflow.service.processor.request.TransferTaskRequest;
import com.paytech.platform.support.workflow.service.processor.request.UpdateActionTemplateRequest;
import com.paytech.platform.support.workflow.service.processor.response.WorkflowDraftResult;
import com.paytech.platform.support.workflow.service.processor.response.WorkflowMutation;
import com.paytech.platform.support.workflow.service.processor.response.WorkflowSubmissionResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 工作流 HTTP 协议转换：十进制字符串 ID、稳定 Code 与 JSON 对象文本；字段约束由 Body 注解与公共 Processor 负责。 */
@Component
public class WorkflowConverter {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Path 或 Body 中的十进制字符串 ID。 */
    long id(String value, String name) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw new InvalidParameterException(name + " 必须是十进制字符串 ID");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new InvalidParameterException(name + " 必须是十进制字符串 ID", exception);
        }
    }

    /** JSON 对象转文本；不是对象为参数错误。 */
    String object(JsonNode node, String name) {
        if (node == null || !node.isObject()) {
            throw new InvalidParameterException(name + " 必须是 JSON 对象");
        }
        return JSON.writeValueAsString(node);
    }

    /** 新建动作模板。 */
    CreateActionTemplateRequest createTemplate(String actor, String session, CreateWorkflowActionTemplateRequestBody body) {
        return new CreateActionTemplateRequest(actor, session, body.getTemplateCode(), body.getTemplateName().strip(),
            EnumUtils.getEnumByCode(WorkflowActionType.class, body.getActionType()), body.getDescription(),
            object(body.getConfig(), "config"));
    }

    /** 修改动作模板。 */
    UpdateActionTemplateRequest updateTemplate(String actor, String session, String templateId,
        UpdateWorkflowActionTemplateRequestBody body) {
        return new UpdateActionTemplateRequest(actor, session, id(templateId, "templateId"), body.getTemplateName().strip(),
            body.getDescription(), object(body.getConfig(), "config"), body.getExpectedVersion());
    }

    /** 启停。 */
    ChangeWorkflowStatusRequest status(String actor, String session, String targetId, ChangeWorkflowStatusRequestBody body) {
        return new ChangeWorkflowStatusRequest(actor, session, id(targetId, "id"), "ACTIVE".equals(body.getStatus()),
            body.getExpectedVersion());
    }

    /** 新建流程定义。 */
    CreateDefinitionRequest createDefinition(String actor, String session, CreateWorkflowDefinitionRequestBody body) {
        return new CreateDefinitionRequest(actor, session, body.getDefinitionCode(), body.getDefinitionName().strip(),
            body.getDescription());
    }

    /** 保存草稿。 */
    SaveDraftRequest draft(String actor, String session, String definitionId, SaveWorkflowDraftRequestBody body) {
        return new SaveDraftRequest(actor, session, id(definitionId, "definitionId"), body.getDefinitionName().strip(),
            body.getDescription(), object(body.getGraph(), "graph"), object(body.getStartForm(), "startForm"),
            body.getExpectedVersion());
    }

    /** 发起实例：人员修改转为节点 / 动作 key 到账号 ID。 */
    StartInstanceRequest start(String actor, String session, StartWorkflowInstanceRequestBody body) {
        Map<String, List<Long>> approvers = new LinkedHashMap<>();
        if (body.getApprovers() != null) {
            body.getApprovers().forEach(item -> approvers.put(item.getNodeKey(),
                item.getApproverIds().stream().map(value -> id(value, "approverIds")).toList()));
        }
        Map<String, Long> executors = new LinkedHashMap<>();
        if (body.getExecutors() != null) {
            body.getExecutors().forEach(item -> executors.put(item.getActionKey(), id(item.getExecutorId(), "executorId")));
        }
        object(body.getStartValues(), "startValues");
        return new StartInstanceRequest(actor, session, id(body.getDefinitionId(), "definitionId"), body.getVersionNo(),
            body.getTitle().strip(), blankToNull(body.getBusinessKey()), body.getStartValues(), approvers, executors);
    }

    /** 转交。 */
    TransferTaskRequest transfer(String actor, String session, String targetId, TransferWorkflowTaskRequestBody body) {
        return new TransferTaskRequest(actor, session, id(targetId, "id"), id(body.getToUserId(), "toUserId"),
            blankToNull(body.getReason()));
    }

    /** 审批决定。 */
    DecideApprovalRequest decide(String actor, String session, String approvalId, DecideWorkflowApprovalRequestBody body) {
        return new DecideApprovalRequest(actor, session, id(approvalId, "approvalId"),
            EnumUtils.getEnumByCode(ApprovalDecision.class, body.getDecision()), blankToNull(body.getComment()),
            blankToNull(body.getRejectTargetNodeKey()));
    }

    /** 写入口最小结果。 */
    WorkflowMutationResponseBody mutation(WorkflowMutation result) {
        return new WorkflowMutationResponseBody(result.getObjectType(), Long.toString(result.getObjectId()),
            result.getStatus(), result.getVersion());
    }

    /** 保存草稿结果。 */
    WorkflowDraftResponseBody draft(WorkflowDraftResult result) {
        return new WorkflowDraftResponseBody(Long.toString(result.getDefinitionId()), result.getVersion(),
            result.getDraftVersionNo(), result.getProblems().stream().map(problem -> new WorkflowProblemBody(
                problem.code().getCode(), problem.nodeKey(), problem.edgeKey(), problem.itemKey(), problem.message()))
                .toList());
    }

    /** 提交结果。 */
    WorkflowSubmissionResponseBody submission(WorkflowSubmissionResult result) {
        return new WorkflowSubmissionResponseBody(Long.toString(result.getActionRunId()), result.getStatus(),
            Long.toString(result.getVerificationId()), result.getFailureReason(), result.getInstanceStatus());
    }

    /** 空白转 null，其余去除首尾空白。 */
    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
