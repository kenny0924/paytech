package com.paytech.platform.support.workflow.service.processor;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import com.paytech.platform.support.api.enums.OnboardingFileErrorCode;
import com.paytech.platform.support.filedocument.domain.file.StoredFileView;
import com.paytech.platform.support.filedocument.domainservice.service.FileLifecycleService;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionRun;
import com.paytech.platform.support.workflow.domain.WorkflowInstanceContext;
import com.paytech.platform.support.workflow.domain.action.ActionTemplateReference;
import com.paytech.platform.support.workflow.domain.attachment.AttachmentFact;
import com.paytech.platform.support.workflow.domainservice.convert.WorkflowRuntimeJson;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowActionRunRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowInstanceRepository;
import com.paytech.platform.support.workflow.domainservice.validator.AttachmentRules;

import lombok.RequiredArgsConstructor;

import tools.jackson.databind.JsonNode;

/**
 * 工作流入口共用的附件查询与归属检查（技术方案第 5 节）：业务数据只存文件地址；先按 {@code FileAddress} 校验地址格式
 * （不合法返回 422 字段错误），再按解析出的文件 ID 读取 file-document 元数据，确认附件由提交人本人以工作流原因上传、
 * 未被其他实例引用。只读本库，可在入口的写事务内执行。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class WorkflowAttachments {

    private final FileLifecycleService files;
    /** 动作提交内容在请求体中的属性名。 */
    static final String OUTPUT_ROOT = "output";

    private final WorkflowInstanceRepository instances;
    private final WorkflowActionRunRepository actionRuns;
    private final WorkflowRuntimeJson json = new WorkflowRuntimeJson();

    /**
     * 读取文件事实；不存在的文件不出现在结果中。
     *
     * @param fileIds 文件 ID
     * @return 文件 ID 到事实
     */
    public Map<Long, AttachmentFact> facts(Collection<Long> fileIds) {
        Map<Long, AttachmentFact> result = new LinkedHashMap<>();
        for (Long fileId : Set.copyOf(fileIds)) {
            StoredFileView view = describe(fileId);
            if (view != null) {
                result.put(fileId, new AttachmentFact(fileId, view.getSourceSystem(), view.getReasonCode(),
                    view.getUploaderId(), view.getDisplayName(), view.getMimeType(), view.getSizeBytes(),
                    view.getScanStatus(), view.getObjectStatus()));
            }
        }
        return result;
    }

    /**
     * 要求文件可以挂到本实例：存在、是工作流附件、由提交人上传，且没有被其他实例的启动值或动作输出引用。
     *
     * @param fileIds 引用的文件
     * @param facts 文件事实
     * @param submitter 提交人
     * @param instanceId 当前实例；发起时为空
     */
    public void requireAttachable(Collection<Long> fileIds, Map<Long, AttachmentFact> facts, long submitter,
        Long instanceId) {
        List<String> problems = AttachmentRules.ownershipProblems(fileIds, facts, submitter);
        if (!problems.isEmpty()) {
            throw new BizCheckFailException("附件不可用：" + String.join("；", problems));
        }
        for (Long fileId : fileIds) {
            String address = AttachmentRules.address(fileId);
            if (instances.referencedByOtherInstance(address, instanceId)
                || actionRuns.referencedByOtherInstance(address, instanceId)) {
                throw new BizCheckFailException("附件已用于其他流程实例，请重新上传：" + address);
            }
        }
    }

    /**
     * 提交动作时的文件事实：提交人是当前执行人时按模板取出引用的文件、检查归属并读取事实；
     * 否则返回空，由引擎按指派规则拒绝。
     *
     * @param context 实例上下文
     * @param actionRunId 动作运行
     * @param output 提交内容
     * @param submitter 提交人
     * @return 文件事实
     */
    public Map<Long, AttachmentFact> forSubmission(WorkflowInstanceContext context, long actionRunId, JsonNode output,
        long submitter) {
        WorkflowActionRun row = context.getActionRuns().stream().filter(run -> run.getActionRunId() == actionRunId)
            .findFirst().orElse(null);
        if (row == null || row.getAssigneeId() != submitter) {
            return Map.of();
        }
        ActionTemplateReference template = context.getTemplates().get(row.getActionTemplateId());
        if (template == null) {
            return Map.of();
        }
        requireAddresses(AttachmentRules.addressErrors(template.config(), output, OUTPUT_ROOT));
        Set<Long> fileIds = AttachmentRules.referenced(template.config(), output);
        Map<Long, AttachmentFact> facts = facts(fileIds);
        requireAttachable(fileIds, facts, submitter, context.getInstance().getInstanceId());
        return facts;
    }

    /**
     * 要求文件地址格式合法；否则按请求体字段返回 422 VALIDATION_ERROR。
     *
     * @param errors 字段路径到原因
     */
    public void requireAddresses(Map<String, String> errors) {
        if (!errors.isEmpty()) {
            throw new RequestBodyValidationException(errors.entrySet().stream()
                .map(error -> new ProblemFieldError(error.getKey(), error.getValue())).toList(), null);
        }
    }

    /**
     * 实例引用的全部文件：启动值的 FILE 字段，以及各轮动作运行输出（含未通过验证的提交）中的文件。
     * 归属在写入时已确认，下载授权以此为准。
     *
     * @param context 实例上下文
     * @return 文件 ID，按出现顺序
     */
    public Set<Long> referenced(WorkflowInstanceContext context) {
        Set<Long> fileIds = new LinkedHashSet<>(AttachmentRules.referenced(context.getStartForm(),
            json.tree(context.getInstance().getStartValuesJson())));
        for (WorkflowActionRun run : context.getActionRuns()) {
            ActionTemplateReference template = context.getTemplates().get(run.getActionTemplateId());
            if (template != null && run.getOutputJson() != null) {
                fileIds.addAll(AttachmentRules.referenced(template.config(), json.tree(run.getOutputJson())));
            }
        }
        return fileIds;
    }

    /**
     * 按文件 ID 读取元数据；文件不存在返回 null。
     *
     * @param fileId 文件 ID
     * @return 元数据或 null
     */
    StoredFileView describe(long fileId) {
        try {
            return files.describe(fileId);
        } catch (FailException failure) {
            if (OnboardingFileErrorCode.FILE_NOT_FOUND.getCode().equals(failure.getCode())) {
                return null;
            }
            throw failure;
        }
    }
}
