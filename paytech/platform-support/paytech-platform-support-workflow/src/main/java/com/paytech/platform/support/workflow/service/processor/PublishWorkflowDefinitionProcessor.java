package com.paytech.platform.support.workflow.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.workflow.core.common.WorkflowPermissions;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinition;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinitionVersion;
import com.paytech.platform.support.workflow.domain.validation.WorkflowGraphProblem;
import com.paytech.platform.support.workflow.domainservice.convert.WorkflowRuntimeJson;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowDefinitionRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowDefinitionVersionRepository;
import com.paytech.platform.support.workflow.domainservice.service.WorkflowDefinitionService;
import com.paytech.platform.support.workflow.service.processor.request.PublishDefinitionRequest;
import com.paytech.platform.support.workflow.service.processor.response.WorkflowMutation;

import lombok.AllArgsConstructor;

/**
 * 发布草稿（技术方案第 7 节 10）：重新执行 B1 校验，有任何问题即拒绝；通过后冻结引用模板的 ID、版本、类型与配置，
 * 草稿变为不可变版本，定义的当前版本切到本版本、草稿指针清空
 * <li>锁 workflow-definition:{id}（TRY_LOCK），定义与版本都做 CAS。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class PublishWorkflowDefinitionProcessor extends AbstractLockProcessor<PublishDefinitionRequest, WorkflowMutation> {

    private final WorkflowDraftChecker checker;
    private final WorkflowProcessorSupport support;
    private final WorkflowDefinitionService service;
    private final WorkflowDefinitionRepository definitions;
    private final WorkflowDefinitionVersionRepository versions;
    private final WorkflowRuntimeJson json = new WorkflowRuntimeJson();

    /** 与保存草稿、启停共用定义资源锁。 */
    @Override
    protected LockCommand lockCommand(PublishDefinitionRequest request) {
        return LockCommand.tryLock(WorkflowDefinitionLocks.lock(request.getDefinitionId()), support.lockWaitMillis(),
            support.lockLeaseMillis());
    }

    @Override
    protected WorkflowMutation processWithinLock(PublishDefinitionRequest request) {
        return support.write(() -> {
            var session = support.authorize(request.getActor(), request.getSession(),
                WorkflowPermissions.DEFINITION_MANAGE);
            WorkflowDefinition definition = definitions.find(request.getDefinitionId());
            if (definition == null) {
                throw support.notFound();
            }
            WorkflowDefinitionLocks.requireVersion(definition, request.getExpectedVersion());
            WorkflowDefinitionVersion draft = draft(definition);
            var checked = checker.checkSubmitted(draft.getGraphJson(), draft.getStartFormJson());
            requireNoProblems(checked.problems());
            long actor = support.actorId(session);
            var now = support.now();
            draft.setActionSnapshotJson(json.writeSnapshot(checked.templates().values().stream()
                .map(row -> new WorkflowRuntimeJson.FrozenTemplate(row.getActionTemplateId(), row.getVersion(),
                    row.getTemplateCode(), row.getTemplateName(), row.getActionType(), row.getConfigJson())).toList()));
            draft.setPublishedBy(actor);
            draft.setPublishedTime(now);
            draft.setUpdatedBy(actor);
            draft.setUpdateTime(now);
            definition.setCurrentVersionNo(draft.getVersionNo());
            definition.setDraftVersionNo(null);
            definition.setUpdatedBy(actor);
            definition.setUpdateTime(now);
            service.publish(definition, draft);
            support.succeeded(session, WorkflowMutation.DEFINITION, definition.getDefinitionId(), "PUBLISHED");
            return new WorkflowMutation(WorkflowMutation.DEFINITION, definition.getDefinitionId(),
                definition.getStatus().getCode(), definition.getVersion());
        });
    }

    /** 当前草稿；没有草稿时拒绝。 */
    WorkflowDefinitionVersion draft(WorkflowDefinition definition) {
        if (definition.getDraftVersionNo() == null) {
            throw new BizCheckFailException("没有待发布的草稿：" + definition.getDefinitionCode());
        }
        WorkflowDefinitionVersion draft = versions.findByNo(definition.getDefinitionId(), definition.getDraftVersionNo());
        if (draft == null) {
            throw new ErrorException("流程定义的草稿指针指向不存在的版本：" + definition.getDefinitionId());
        }
        return draft;
    }

    /** 有问题即拒绝发布；明细由详情接口返回。 */
    void requireNoProblems(List<WorkflowGraphProblem> problems) {
        if (!problems.isEmpty()) {
            throw new BizCheckFailException("发布校验未通过，共 " + problems.size() + " 个问题，首个：" + problems.get(0).message());
        }
    }
}
