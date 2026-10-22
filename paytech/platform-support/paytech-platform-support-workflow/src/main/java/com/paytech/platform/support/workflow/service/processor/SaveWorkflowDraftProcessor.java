package com.paytech.platform.support.workflow.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.workflow.core.common.WorkflowPermissions;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinition;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinitionVersion;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowDefinitionRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowDefinitionVersionRepository;
import com.paytech.platform.support.workflow.domainservice.service.WorkflowDefinitionService;
import com.paytech.platform.support.workflow.service.processor.request.SaveDraftRequest;
import com.paytech.platform.support.workflow.service.processor.response.WorkflowDraftResult;
import com.paytech.platform.support.workflow.service.processor.response.WorkflowMutation;

import lombok.AllArgsConstructor;

/**
 * 保存草稿（技术方案第 7 节 9）：返回当前发布校验问题但不阻止保存
 * <li>已有草稿按原版本覆盖；没有草稿时新建版本号为当前发布版本加一的草稿（内容取本次请求）。</li>
 * <li>锁 workflow-definition:{id}（TRY_LOCK），定义与草稿都做版本 CAS；结构不合法返回 400。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class SaveWorkflowDraftProcessor extends AbstractLockProcessor<SaveDraftRequest, WorkflowDraftResult> {

    private final WorkflowDraftChecker checker;
    private final WorkflowProcessorSupport support;
    private final WorkflowDefinitionService service;
    private final WorkflowDefinitionRepository definitions;
    private final WorkflowDefinitionVersionRepository versions;

    /** 与发布、启停共用定义资源锁。 */
    @Override
    protected LockCommand lockCommand(SaveDraftRequest request) {
        return LockCommand.tryLock(WorkflowDefinitionLocks.lock(request.getDefinitionId()), support.lockWaitMillis(),
            support.lockLeaseMillis());
    }

    @Override
    protected WorkflowDraftResult processWithinLock(SaveDraftRequest request) {
        return support.write(() -> {
            var session = support.authorize(request.getActor(), request.getSession(),
                WorkflowPermissions.DEFINITION_MANAGE);
            WorkflowDefinition definition = definitions.find(request.getDefinitionId());
            if (definition == null) {
                throw support.notFound();
            }
            WorkflowDefinitionLocks.requireVersion(definition, request.getExpectedVersion());
            var checked = checker.checkSubmitted(request.getGraphJson(), request.getStartFormJson());
            long actor = support.actorId(session);
            boolean newDraft = definition.getDraftVersionNo() == null;
            WorkflowDefinitionVersion draft = newDraft ? newDraft(definition, request, actor) : existingDraft(definition,
                request, actor);
            definition.setDefinitionName(request.getDefinitionName());
            definition.setDescription(request.getDescription());
            definition.setDraftVersionNo(draft.getVersionNo());
            definition.setUpdatedBy(actor);
            definition.setUpdateTime(support.now());
            service.saveDraft(definition, draft, newDraft);
            support.succeeded(session, WorkflowMutation.DEFINITION, definition.getDefinitionId(), "DRAFT_SAVED");
            return new WorkflowDraftResult(definition.getDefinitionId(), definition.getVersion(), draft.getVersionNo(),
                checked.problems());
        });
    }

    /** 没有草稿时新建：版本号为当前发布版本加一。 */
    WorkflowDefinitionVersion newDraft(WorkflowDefinition definition, SaveDraftRequest request, long actor) {
        int next = (definition.getCurrentVersionNo() == null ? 0 : definition.getCurrentVersionNo()) + 1;
        return CreateWorkflowDefinitionProcessor.draft(support.nextId(), definition, next, request.getGraphJson(),
            request.getStartFormJson(), actor, support.now());
    }

    /** 覆盖已有草稿的内容。 */
    WorkflowDefinitionVersion existingDraft(WorkflowDefinition definition, SaveDraftRequest request, long actor) {
        WorkflowDefinitionVersion draft = versions.findByNo(definition.getDefinitionId(), definition.getDraftVersionNo());
        if (draft == null) {
            throw new ErrorException("流程定义的草稿指针指向不存在的版本："
                + definition.getDefinitionId());
        }
        draft.setGraphJson(request.getGraphJson());
        draft.setStartFormJson(request.getStartFormJson());
        draft.setUpdatedBy(actor);
        draft.setUpdateTime(support.now());
        return draft;
    }
}
