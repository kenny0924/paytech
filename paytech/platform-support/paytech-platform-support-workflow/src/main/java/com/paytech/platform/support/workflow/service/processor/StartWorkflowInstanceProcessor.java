package com.paytech.platform.support.workflow.service.processor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService.SessionContext;
import com.paytech.platform.support.workflow.core.common.WorkflowPermissions;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinition;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinitionVersion;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowInstance;
import com.paytech.platform.support.workflow.core.enums.WorkflowDefinitionStatus;
import com.paytech.platform.support.workflow.core.enums.WorkflowInstanceStatus;
import com.paytech.platform.support.workflow.core.enums.WorkflowNodeType;
import com.paytech.platform.support.workflow.domain.WorkflowInstanceContext;
import com.paytech.platform.support.workflow.domain.attachment.AttachmentFact;
import com.paytech.platform.support.workflow.domain.form.FormDefinition;
import com.paytech.platform.support.workflow.domain.graph.WorkflowGraph;
import com.paytech.platform.support.workflow.domain.graph.WorkflowNode;
import com.paytech.platform.support.workflow.domain.runtime.WorkflowAssignment;
import com.paytech.platform.support.workflow.domainservice.convert.FormDefinitionJsonReader;
import com.paytech.platform.support.workflow.domainservice.convert.WorkflowGraphJsonReader;
import com.paytech.platform.support.workflow.domainservice.convert.WorkflowRuntimeJson;
import com.paytech.platform.support.workflow.domainservice.process.WorkflowRuntimeEngine;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowDefinitionRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowDefinitionVersionRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowInstanceRepository;
import com.paytech.platform.support.workflow.domainservice.service.WorkflowRuntimeService;
import com.paytech.platform.support.workflow.domainservice.validator.AttachmentRules;
import com.paytech.platform.support.workflow.domainservice.validator.FormValueValidator;
import com.paytech.platform.support.workflow.service.processor.request.StartInstanceRequest;
import com.paytech.platform.support.workflow.service.processor.response.WorkflowMutation;

import lombok.RequiredArgsConstructor;

/**
 * 发起实例（R5，技术方案第 4 节“发起”；REQ-05 按编码发起与业务关联键防重）：校验启动表单 → 冻结人员分配（版本默认值加发起时的修改，
 * 均须为 ACTIVE 的 Operations 账号）→ 写实例与 START 运行 → 推进到第一批任务节点，并按规则登记通知回调与 DATA_QUERY 首查
 * <li>按 definitionCode 发起取当前发布版本；按 definitionId 发起要求当前发布版本等于请求的版本号（防止发布中途换版本）。
 * 流程未发布或已停用返回 400，定义不存在返回 404。</li>
 * <li>给出 businessKey 时同一定义下只允许一个 RUNNING 实例（uk_workflow_inst_active）：事务内先查到同键实例即返回它
 * （created=false，不比对标题、启动值与人员）；并发发起撞唯一索引时整个事务回滚，在事务外回读并返回既有实例，
 * 回读时对方已结束返回 409。新建记录没有可竞争的资源锁，并发由唯一索引保证。</li>
 * <li>启动表单的文件字段存文件地址（/files/v1/{fileId}），格式不合法返回 422；文件须由发起人以工作流附件上传、未被其他实例引用；扫描中不挡发起，已确认未通过扫描的文件在该文件路径上返回 422（“文件未通过安全检查，请删除后重新上传”）。</li>
 * <li>启动值只检查必填、值类型与平台长度上限（2026-10-22 起字段规则中的长度、范围、文件数量与类型不再校验），问题按字段返回 422（路径 startValues.&lt;key&gt;，原因与完成动作一致）。</li>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class StartWorkflowInstanceProcessor extends AbstractProcessor<StartInstanceRequest, WorkflowMutation> {

    /** 启动值在请求体中的属性名。 */
    static final String START_VALUES = "startValues";

    /** 业务关联键防重的唯一索引。 */
    static final String ACTIVE_KEY_INDEX = "uk_workflow_inst_active";

    private final WorkflowProcessorSupport support;
    private final WorkflowAttachments attachments;
    private final WorkflowRuntimeService runtime;
    private final WorkflowInstanceRepository instances;
    private final WorkflowDefinitionRepository definitions;
    private final WorkflowDefinitionVersionRepository versions;
    private final WorkflowRuntimeJson json = new WorkflowRuntimeJson();
    private final FormValueValidator forms = new FormValueValidator();
    private final WorkflowRuntimeEngine engine = new WorkflowRuntimeEngine();
    private final WorkflowGraphJsonReader graphReader = new WorkflowGraphJsonReader();
    private final FormDefinitionJsonReader formReader = new FormDefinitionJsonReader();

    @Override
    protected WorkflowMutation processRequest(StartInstanceRequest request) {
        try {
            return support.write(() -> start(request));
        } catch (DuplicateKeyException failure) {
            if (failure.getMessage() == null || !failure.getMessage().contains(ACTIVE_KEY_INDEX)) {
                throw failure;
            }
            // 并发发起：事务已回滚，回读同键实例
            return support.write(() -> concurrentlyStarted(request));
        }
    }

    /**
     * 本地事务内：权限与可发起版本 → 同键 RUNNING 实例 → 新建并推进。
     *
     * @param request 请求
     * @return 新实例（created=true）或同键已有实例（created=false）
     */
    WorkflowMutation start(StartInstanceRequest request) {
        // 权限与可发起版本
        var session = support.authorize(request.getActor(), request.getSession(), WorkflowPermissions.INSTANCE_START);
        WorkflowDefinition definition = launchable(request);
        // 同键 RUNNING 实例
        WorkflowInstance running = running(definition.getDefinitionId(), request.getBusinessKey());
        if (running != null) {
            return reused(session, running);
        }
        WorkflowDefinitionVersion version = versions.findByNo(definition.getDefinitionId(),
            definition.getCurrentVersionNo());
        if (version == null) {
            throw new ErrorException("流程定义的当前版本指针指向不存在的版本：" + definition.getDefinitionId());
        }
        // 构建上下文
        WorkflowInstanceContext context = context(definition, version, request, support.actorId(session));
        // 推进并保存
        engine.start(context, support::nextId);
        support.registerCallbackEvents(context.getChange());
        runtime.apply(context.getChange());
        support.succeeded(session, WorkflowMutation.INSTANCE, context.getInstance().getInstanceId(), "STARTED");
        return new WorkflowMutation(WorkflowMutation.INSTANCE, context.getInstance().getInstanceId(),
            context.getInstance().getStatus().getCode(), context.getInstance().getVersion(), true);
    }

    /**
     * 并发发起撞唯一索引后的回读：重检权限，返回胜出的同键实例；它已结束则请调用方重试。
     *
     * @param request 请求
     * @return 同键已有实例（created=false）
     */
    WorkflowMutation concurrentlyStarted(StartInstanceRequest request) {
        var session = support.authorize(request.getActor(), request.getSession(), WorkflowPermissions.INSTANCE_START);
        WorkflowInstance running = running(resolve(request).getDefinitionId(), request.getBusinessKey());
        if (running == null) {
            throw new ConcurrentRequestException("流程实例正在发起，请稍后重试");
        }
        return reused(session, running);
    }

    /**
     * 同一定义下业务关联键的 RUNNING 实例；未给业务关联键时不防重。
     *
     * @param definitionId 定义 ID
     * @param businessKey 业务关联键，可空
     * @return 实例；没有时为 null
     */
    WorkflowInstance running(long definitionId, String businessKey) {
        return businessKey == null ? null : instances.findRunningByBusinessKey(definitionId, businessKey);
    }

    /**
     * 返回同键已有实例并登记审计（REUSED）。
     *
     * @param session 当前会话
     * @param running 已有实例
     * @return created=false 的结果
     */
    WorkflowMutation reused(SessionContext session, WorkflowInstance running) {
        support.succeeded(session, WorkflowMutation.INSTANCE, running.getInstanceId(), "REUSED");
        return new WorkflowMutation(WorkflowMutation.INSTANCE, running.getInstanceId(), running.getStatus().getCode(),
            running.getVersion(), false);
    }

    /** 流程存在、ACTIVE、已发布；按 ID 发起时当前版本还须等于请求的版本号。 */
    WorkflowDefinition launchable(StartInstanceRequest request) {
        WorkflowDefinition definition = resolve(request);
        if (definition.getStatus() != WorkflowDefinitionStatus.ACTIVE || definition.getCurrentVersionNo() == null) {
            throw new BizCheckFailException("流程未发布或已停用，不能发起：" + definition.getDefinitionCode());
        }
        if (request.getDefinitionCode() == null && !definition.getCurrentVersionNo().equals(request.getVersionNo())) {
            throw new BizCheckFailException("流程已发布新版本 v" + definition.getCurrentVersionNo() + "，请刷新后重新发起");
        }
        return definition;
    }

    /** 按编码或 ID 读取流程定义；不存在返回 404。 */
    WorkflowDefinition resolve(StartInstanceRequest request) {
        WorkflowDefinition definition = request.getDefinitionCode() != null
            ? definitions.findByCode(request.getDefinitionCode()) : definitions.find(request.getDefinitionId());
        if (definition == null) {
            throw support.notFound();
        }
        return definition;
    }

    /** 校验启动值与人员后构造新实例的上下文。 */
    WorkflowInstanceContext context(WorkflowDefinition definition, WorkflowDefinitionVersion version,
        StartInstanceRequest request, long actor) {
        WorkflowGraph graph = graphReader.read(version.getGraphJson());
        var startForm = formReader.read(version.getStartFormJson());
        attachments.requireNoFieldErrors(forms.requestErrors(startForm, request.getStartValues(),
            startFiles(startForm, request, actor), START_VALUES));
        WorkflowAssignment assignment = assignment(graph, request);
        support.requireActive(assignment.accountIds(), "执行人或审批人");
        WorkflowInstance instance = instance(definition, version, request, assignment, actor);
        var snapshot = new WorkflowInstanceContext.Snapshot(definition, version, instance, graph, startForm,
            json.readSnapshot(version.getActionSnapshotJson()), assignment);
        var runs = new WorkflowInstanceContext.Runs(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        return new WorkflowInstanceContext(snapshot, runs, instance.getStartTime(), true);
    }

    /** 启动值引用的文件：检查归属与扫描未通过后返回文件事实；扫描中不挡发起。 */
    Map<Long, AttachmentFact> startFiles(FormDefinition startForm, StartInstanceRequest request, long actor) {
        attachments.requireNoFieldErrors(
            AttachmentRules.addressErrors(startForm, request.getStartValues(), START_VALUES));
        Set<Long> fileIds = AttachmentRules.referenced(startForm, request.getStartValues());
        Map<Long, AttachmentFact> facts = attachments.facts(fileIds);
        attachments.requireAttachable(fileIds, facts, actor, null);
        attachments.requireNoFieldErrors(
            AttachmentRules.scanFailedErrors(startForm, request.getStartValues(), facts, START_VALUES));
        return facts;
    }

    /** 版本默认人员加发起时的修改；修改只能针对有审批的任务节点与已有动作。 */
    WorkflowAssignment assignment(WorkflowGraph graph, StartInstanceRequest request) {
        Map<String, List<Long>> approvers = new LinkedHashMap<>();
        Map<String, Long> executors = new LinkedHashMap<>();
        for (WorkflowNode node : graph.nodes()) {
            if (node.type() == WorkflowNodeType.TASK && node.hasApprovers()) {
                approvers.put(node.key(), node.approval().approverIds());
            }
            node.actions().forEach(action -> executors.put(action.key(), action.executorId()));
        }
        request.getApprovers().forEach((nodeKey, ids) -> {
            if (!approvers.containsKey(nodeKey)) {
                throw new BizCheckFailException("节点没有审批配置，不能修改审批人：" + nodeKey);
            }
            if (ids.isEmpty() || new HashSet<>(ids).size() != ids.size()) {
                throw new BizCheckFailException("审批人不能为空或重复：" + nodeKey);
            }
            approvers.put(nodeKey, ids);
        });
        request.getExecutors().forEach((actionKey, id) -> {
            if (!executors.containsKey(actionKey)) {
                throw new BizCheckFailException("流程中没有该动作，不能修改执行人：" + actionKey);
            }
            executors.put(actionKey, id);
        });
        return new WorkflowAssignment(approvers, executors);
    }

    /** 新实例：RUNNING、版本 1，启动值与人员分配冻结。 */
    WorkflowInstance instance(WorkflowDefinition definition, WorkflowDefinitionVersion version,
        StartInstanceRequest request, WorkflowAssignment assignment, long actor) {
        Instant now = support.now();
        WorkflowInstance instance = new WorkflowInstance();
        instance.setInstanceId(support.nextId());
        instance.setDefinitionId(definition.getDefinitionId());
        instance.setDefinitionVersionId(version.getDefinitionVersionId());
        instance.setTitle(request.getTitle());
        instance.setBusinessKey(request.getBusinessKey());
        instance.setStatus(WorkflowInstanceStatus.RUNNING);
        instance.setActiveSlot(request.getBusinessKey() == null ? null : 1);
        instance.setStartValuesJson(json.write(request.getStartValues()));
        instance.setAssignmentJson(json.writeAssignment(assignment));
        instance.setStartedBy(actor);
        instance.setStartTime(now);
        instance.setVersion(1);
        instance.setCreateTime(now);
        instance.setUpdateTime(now);
        return instance;
    }
}
