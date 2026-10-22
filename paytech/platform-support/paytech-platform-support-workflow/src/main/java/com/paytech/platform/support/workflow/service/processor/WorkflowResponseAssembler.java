package com.paytech.platform.support.workflow.service.processor;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.platform.support.api.workflow.response.WorkflowActionTemplateResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowActivityBody;
import com.paytech.platform.support.api.workflow.response.WorkflowAttachmentBody;
import com.paytech.platform.support.api.workflow.response.WorkflowDefinitionSummaryBody;
import com.paytech.platform.support.api.workflow.response.WorkflowInstanceDetailResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowInstanceSummaryBody;
import com.paytech.platform.support.api.workflow.response.WorkflowProblemBody;
import com.paytech.platform.support.api.workflow.response.WorkflowTaskBody;
import com.paytech.platform.support.api.workflow.response.WorkflowVersionBody;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionRun;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionTemplate;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowApproval;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowCallback;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinition;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinitionVersion;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowInstance;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowNodeRun;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowTransfer;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowVerification;
import com.paytech.platform.support.workflow.domain.attachment.AttachmentFact;
import com.paytech.platform.support.workflow.domain.query.WorkflowTaskView;
import com.paytech.platform.support.workflow.domain.runtime.CallbackTarget;
import com.paytech.platform.support.workflow.domain.runtime.RejectOption;
import com.paytech.platform.support.workflow.domain.validation.WorkflowGraphProblem;
import com.paytech.platform.support.workflow.domainservice.convert.WorkflowRuntimeJson;

/**
 * 工作流查询结果到 HTTP 响应体的转换：DataObject 只在本模块内使用，出模块前转成 -api 的响应体；ID 一律输出十进制字符串。
 */
final class WorkflowResponseAssembler {

    private final WorkflowRuntimeJson json = new WorkflowRuntimeJson();

    /**
     * 可空 ID 转字符串。
     *
     * @param id ID
     * @return 字符串或 null
     */
    static String id(Long id) {
        return id == null ? null : Long.toString(id);
    }

    /** 动作模板；withConfig 为 false 时不含配置；people 为账号 ID 到显示名。 */
    WorkflowActionTemplateResponseBody template(WorkflowActionTemplate row, boolean withConfig, Map<String, String> people) {
        return new WorkflowActionTemplateResponseBody(id(row.getActionTemplateId()), row.getTemplateCode(),
            row.getTemplateName(), row.getActionType().getCode(), row.getDescription(),
            withConfig ? json.tree(row.getConfigJson()) : null, row.getStatus().getCode(), row.getVersion(),
            id(row.getUpdatedBy()), people.get(id(row.getUpdatedBy())), row.getUpdateTime());
    }

    /** 流程定义摘要。 */
    WorkflowDefinitionSummaryBody definition(WorkflowDefinition row) {
        return new WorkflowDefinitionSummaryBody(id(row.getDefinitionId()), row.getDefinitionCode(),
            row.getDefinitionName(), row.getDescription(), row.getStatus().getCode(), row.getCurrentVersionNo(),
            row.getDraftVersionNo(), row.getVersion(), row.getUpdateTime());
    }

    /** 版本内容；摘要行（不含 JSON 列）对应字段为空。 */
    WorkflowVersionBody version(WorkflowDefinitionVersion row) {
        return new WorkflowVersionBody(row.getVersionNo(), row.getStatus().getCode(), tree(row.getGraphJson()),
            tree(row.getStartFormJson()), tree(row.getActionSnapshotJson()), id(row.getPublishedBy()),
            row.getPublishedTime(), row.getUpdateTime());
    }

    /** 发布校验问题。 */
    List<WorkflowProblemBody> problems(List<WorkflowGraphProblem> problems) {
        return problems.stream().map(problem -> new WorkflowProblemBody(problem.code().getCode(), problem.nodeKey(),
            problem.edgeKey(), problem.itemKey(), problem.message())).toList();
    }

    /** 实例摘要。 */
    WorkflowInstanceSummaryBody instance(WorkflowInstance row, Map<String, String> people) {
        return new WorkflowInstanceSummaryBody(id(row.getInstanceId()), id(row.getDefinitionId()), row.getTitle(),
            row.getBusinessKey(), row.getStatus().getCode(), id(row.getStartedBy()), people.get(id(row.getStartedBy())),
            row.getStartTime(), row.getEndTime());
    }

    /** 待办。 */
    WorkflowTaskBody task(WorkflowTaskView row) {
        return new WorkflowTaskBody(row.getTaskType(), id(row.getTaskId()), id(row.getInstanceId()),
            row.getInstanceTitle(), row.getNodeKey(), row.getRoundNo(), row.getActionKey(), row.getWaitingSince(), null,
            null);
    }

    /** 节点运行；rejectOption 仅待审批 TASK 给出。 */
    WorkflowInstanceDetailResponseBody.NodeRunBody nodeRun(WorkflowNodeRun row, RejectOption option) {
        return new WorkflowInstanceDetailResponseBody.NodeRunBody(id(row.getNodeRunId()), row.getNodeKey(),
            row.getNodeType().getCode(), row.getRoundNo(), row.getBranchKey(), id(row.getPreviousNodeRunId()),
            row.getStatus().getCode(), row.getSelectedEdgeKey(), row.getActivatedTime(), row.getCompletedTime(),
            option == null ? null : option.kind().getCode(), option == null ? List.of() : option.targetNodeKeys());
    }

    /** 动作运行；templateName 取版本快照中冻结的名称。 */
    WorkflowInstanceDetailResponseBody.ActionRunBody actionRun(WorkflowActionRun row, String templateName) {
        return new WorkflowInstanceDetailResponseBody.ActionRunBody(id(row.getActionRunId()), id(row.getNodeRunId()),
            row.getActionKey(), row.getActionType().getCode(), row.getRequired(), id(row.getAssigneeId()),
            row.getStatus().getCode(), tree(row.getOutputJson()), id(row.getSubmittedBy()), row.getSubmittedTime(),
            templateName);
    }

    /** 附件元数据。 */
    WorkflowAttachmentBody attachment(AttachmentFact fact) {
        return new WorkflowAttachmentBody(id(fact.fileId()), FileAddress.of(fact.fileId()).value(), fact.displayName(), fact.mimeType(), id(fact.sizeBytes()),
            fact.scanStatus() == null ? null : fact.scanStatus().getCode(), id(fact.uploaderId()));
    }

    /** 验证记录。 */
    WorkflowInstanceDetailResponseBody.VerificationBody verification(WorkflowVerification row) {
        return new WorkflowInstanceDetailResponseBody.VerificationBody(id(row.getVerificationId()),
            id(row.getActionRunId()), row.getResult().getCode(), row.getFailureReason(), tree(row.getEvidenceJson()),
            id(row.getVerifiedBy()), row.getVerifiedTime());
    }

    /** 审批行。 */
    WorkflowInstanceDetailResponseBody.ApprovalBody approval(WorkflowApproval row) {
        return new WorkflowInstanceDetailResponseBody.ApprovalBody(id(row.getApprovalId()), id(row.getNodeRunId()),
            id(row.getApproverId()), row.getStatus().getCode(), row.getDecisionComment(), row.getRejectTargetNodeKey(),
            row.getDecidedTime());
    }

    /** 转交记录。 */
    WorkflowInstanceDetailResponseBody.TransferBody transfer(WorkflowTransfer row) {
        return new WorkflowInstanceDetailResponseBody.TransferBody(id(row.getTransferId()), row.getTargetType().getCode(),
            id(row.getTargetId()), id(row.getFromUserId()), id(row.getToUserId()), row.getReason(),
            id(row.getTransferredBy()), row.getCreateTime());
    }

    /** 节点运行的活动。 */
    WorkflowActivityBody nodeActivity(WorkflowNodeRun row) {
        return new WorkflowActivityBody(row.getCompletedTime() != null ? row.getCompletedTime() : row.getActivatedTime(),
            "NODE", row.getNodeKey(), row.getRoundNo(), row.getStatus().getCode(), null, null, null,
            id(row.getNodeRunId()));
    }

    /** 提交与验证的活动。 */
    WorkflowActivityBody verificationActivity(WorkflowVerification row, WorkflowNodeRun node, Map<String, String> people) {
        return new WorkflowActivityBody(row.getVerifiedTime(), "SUBMISSION", node == null ? null : node.getNodeKey(),
            node == null ? null : node.getRoundNo(), row.getResult().getCode(), id(row.getVerifiedBy()),
            people.get(id(row.getVerifiedBy())), row.getFailureReason(), id(row.getActionRunId()));
    }

    /** 审批决定的活动；待决定的行不出现。 */
    WorkflowActivityBody approvalActivity(WorkflowApproval row, WorkflowNodeRun node, Map<String, String> people) {
        return new WorkflowActivityBody(row.getDecidedTime(), "APPROVAL", node == null ? null : node.getNodeKey(),
            node == null ? null : node.getRoundNo(), row.getStatus().getCode(), id(row.getApproverId()),
            people.get(id(row.getApproverId())), row.getDecisionComment(), id(row.getApprovalId()));
    }

    /** 转交的活动。 */
    WorkflowActivityBody transferActivity(WorkflowTransfer row, Map<String, String> people) {
        return new WorkflowActivityBody(row.getCreateTime(), "TRANSFER", null, null, row.getTargetType().getCode(),
            id(row.getTransferredBy()), people.get(id(row.getTransferredBy())),
            people.getOrDefault(id(row.getFromUserId()), id(row.getFromUserId())) + " → "
                + people.getOrDefault(id(row.getToUserId()), id(row.getToUserId())), id(row.getTargetId()));
    }

    /** 通知回调的活动：类型为 NOTIFY（站内与邮件通知）或 CALLBACK（业务回调）；明细为触发事件、编码或收件人数、最后错误。 */
    WorkflowActivityBody callbackActivity(WorkflowCallback row, WorkflowNodeRun node) {
        CallbackTarget target = json.readTarget(row.getTarget());
        String detail = row.getTriggerCode().getCode() + " "
            + (target.callbackCode() != null ? target.callbackCode() : target.recipientIds().size() + " 位收件人")
            + (row.getLastError() == null ? "" : "；" + row.getLastError());
        return new WorkflowActivityBody(row.getUpdateTime(), row.getCallbackType().getCode(),
            node == null ? null : node.getNodeKey(),
            node == null ? null : node.getRoundNo(), row.getStatus().getCode(), null, null, detail,
            id(row.getCallbackId()));
    }

    /** 活动按时间正序。 */
    static Comparator<WorkflowActivityBody> byTime() {
        return Comparator.comparing(WorkflowActivityBody::getTime);
    }

    /**
     * 可空 JSON 列转树。
     *
     * @param text JSON 文本
     * @return 树或 null
     */
    tools.jackson.databind.JsonNode tree(String text) {
        return text == null ? null : json.tree(text);
    }
}
