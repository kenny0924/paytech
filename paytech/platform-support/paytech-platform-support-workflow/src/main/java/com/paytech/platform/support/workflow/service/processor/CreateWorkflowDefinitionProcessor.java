package com.paytech.platform.support.workflow.service.processor;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.workflow.core.common.WorkflowPermissions;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinition;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinitionVersion;
import com.paytech.platform.support.workflow.core.enums.WorkflowDefinitionStatus;
import com.paytech.platform.support.workflow.core.enums.WorkflowVersionStatus;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowDefinitionRepository;
import com.paytech.platform.support.workflow.domainservice.service.WorkflowDefinitionService;
import com.paytech.platform.support.workflow.service.processor.request.CreateDefinitionRequest;
import com.paytech.platform.support.workflow.service.processor.response.WorkflowMutation;

import lombok.AllArgsConstructor;

/**
 * 新建流程定义（ACTIVE）及草稿 v1：只有开始与结束节点，启动表单为空
 * <li>编码已存在拒绝，唯一索引为最终保护；新建不加锁。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CreateWorkflowDefinitionProcessor extends AbstractProcessor<CreateDefinitionRequest, WorkflowMutation> {

    /** 新草稿的流程图：开始直连结束。 */
    static final String EMPTY_GRAPH = "{\"nodes\":[{\"key\":\"start\",\"type\":\"START\",\"name\":\"开始\"},"
        + "{\"key\":\"end\",\"type\":\"END\",\"name\":\"结束\"}],\"edges\":[{\"key\":\"start_end\",\"from\":\"start\","
        + "\"to\":\"end\"}]}";

    /** 新草稿的启动表单。 */
    static final String EMPTY_FORM = "{\"fields\":[]}";

    private final WorkflowProcessorSupport support;
    private final WorkflowDefinitionService service;
    private final WorkflowDefinitionRepository definitions;

    @Override
    protected WorkflowMutation processRequest(CreateDefinitionRequest request) {
        try {
            return support.write(() -> save(request));
        } catch (DuplicateKeyException duplicate) {
            throw new BizCheckFailException("流程编码已存在：" + request.getDefinitionCode());
        }
    }

    /** 权限重检、编码查重后保存定义与草稿。 */
    WorkflowMutation save(CreateDefinitionRequest request) {
        var session = support.authorize(request.getActor(), request.getSession(), WorkflowPermissions.DEFINITION_MANAGE);
        if (definitions.findByCode(request.getDefinitionCode()) != null) {
            throw new BizCheckFailException("流程编码已存在：" + request.getDefinitionCode());
        }
        long actor = support.actorId(session);
        Instant now = support.now();
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setDefinitionId(support.nextId());
        definition.setDefinitionCode(request.getDefinitionCode());
        definition.setDefinitionName(request.getDefinitionName());
        definition.setDescription(request.getDescription());
        definition.setStatus(WorkflowDefinitionStatus.ACTIVE);
        definition.setDraftVersionNo(1);
        definition.setVersion(1);
        definition.setCreatedBy(actor);
        definition.setUpdatedBy(actor);
        definition.setCreateTime(now);
        definition.setUpdateTime(now);
        service.create(definition, draft(support.nextId(), definition, 1, EMPTY_GRAPH, EMPTY_FORM, actor, now));
        support.succeeded(session, WorkflowMutation.DEFINITION, definition.getDefinitionId(), "CREATED");
        return new WorkflowMutation(WorkflowMutation.DEFINITION, definition.getDefinitionId(),
            definition.getStatus().getCode(), 1);
    }

    /**
     * 构造一份新草稿。
     *
     * @param definitionVersionId 新版本 ID
     * @param definition 定义
     * @param versionNo 版本号
     * @param graphJson 流程图
     * @param startFormJson 启动表单
     * @param actor 操作人
     * @param now 时间
     * @return 草稿
     */
    static WorkflowDefinitionVersion draft(long definitionVersionId, WorkflowDefinition definition, int versionNo, String graphJson,
        String startFormJson, long actor, Instant now) {
        WorkflowDefinitionVersion draft = new WorkflowDefinitionVersion();
        draft.setDefinitionVersionId(definitionVersionId);
        draft.setDefinitionId(definition.getDefinitionId());
        draft.setVersionNo(versionNo);
        draft.setStatus(WorkflowVersionStatus.DRAFT);
        draft.setGraphJson(graphJson);
        draft.setStartFormJson(startFormJson);
        draft.setVersion(1);
        draft.setCreatedBy(actor);
        draft.setUpdatedBy(actor);
        draft.setCreateTime(now);
        draft.setUpdateTime(now);
        return draft;
    }
}
