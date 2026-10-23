package com.paytech.platform.support.workflow.service.processor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.platform.support.api.files.FileAddress;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.common.utils.EnumUtils;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsSessionService.SessionContext;
import com.paytech.platform.support.api.workflow.response.WorkflowActionTemplateResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowActivityBody;
import com.paytech.platform.support.api.workflow.response.WorkflowActivityListResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowDefinitionDetailResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowDefinitionSummaryBody;
import com.paytech.platform.support.api.workflow.response.WorkflowHandledTaskBody;
import com.paytech.platform.support.api.workflow.response.WorkflowInstanceDetailResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowTemplateReferenceListResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowInstanceSummaryBody;
import com.paytech.platform.support.api.workflow.response.WorkflowLaunchResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowTaskBody;
import com.paytech.platform.support.api.workflow.response.WorkflowVersionBody;
import com.paytech.platform.support.workflow.domainservice.validator.AttachmentRules;
import com.paytech.platform.support.workflow.core.common.WorkflowPermissions;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionRun;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionTemplate;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowApproval;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinition;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinitionVersion;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowInstance;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowNodeRun;
import com.paytech.platform.support.workflow.core.enums.ActionTemplateStatus;
import com.paytech.platform.support.workflow.core.enums.ApprovalStatus;
import com.paytech.platform.support.workflow.core.enums.NodeRunStatus;
import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;
import com.paytech.platform.support.workflow.core.enums.WorkflowDefinitionStatus;
import com.paytech.platform.support.workflow.core.enums.WorkflowInstanceStatus;
import com.paytech.platform.support.workflow.core.enums.WorkflowNodeType;
import com.paytech.platform.support.workflow.domain.WorkflowInstanceContext;
import com.paytech.platform.support.workflow.domain.graph.WorkflowNode;
import com.paytech.platform.support.workflow.domain.query.WorkflowInstanceFilter;
import com.paytech.platform.support.workflow.domain.runtime.NodeRunState;
import com.paytech.platform.support.workflow.domain.query.WorkflowTaskView;
import com.paytech.platform.support.workflow.domain.query.WorkflowTemplateReferenceView;
import com.paytech.platform.support.workflow.domainservice.convert.WorkflowGraphJsonReader;
import com.paytech.platform.support.workflow.domainservice.convert.WorkflowRuntimeJson;
import com.paytech.platform.support.workflow.domainservice.process.WorkflowNavigator;
import com.paytech.platform.support.workflow.domainservice.repository.impl.WorkflowTaskQueryRepositoryImpl;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowActionTemplateRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowDefinitionRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowDefinitionVersionRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowInstanceRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowTaskQueryRepository;
import com.paytech.platform.support.workflow.domainservice.service.WorkflowInstanceQueryService;

import lombok.RequiredArgsConstructor;

/**
 * 工作流查询入口（技术方案第 7 节的 GET 接口）：重检路由权限，按“参与人或 WORKFLOW_INSTANCE_READ”收窄实例可见范围，
 * 分页在数据库执行，结果在本模块内转为响应体。读已提交、无显式事务：允许观察相邻的已提交状态。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class WorkflowQueryProcessor {

    private final WorkflowDraftChecker checker;
    private final WorkflowProcessorSupport support;
    private final WorkflowAttachments attachments;
    private final WorkflowTaskQueryRepository tasks;
    private final WorkflowInstanceRepository instances;
    private final WorkflowInstanceQueryService queries;
    private final WorkflowDefinitionRepository definitions;
    private final WorkflowActionTemplateRepository templates;
    private final WorkflowDefinitionVersionRepository versions;
    private final WorkflowListEnricher enricher;
    private final WorkflowNavigator navigator = new WorkflowNavigator();
    private final WorkflowResponseAssembler assembler = new WorkflowResponseAssembler();
    private final WorkflowRuntimeJson runtimeJson = new WorkflowRuntimeJson();
    private final WorkflowGraphJsonReader graphReader = new WorkflowGraphJsonReader();

    /** 动作模板分页；每行带被引用的流程定义数（一次分组查询）。 */
    public PagingResponseBody<WorkflowActionTemplateResponseBody> listTemplates(String actor, String session,
        String actionType, String status, String keyword, PagingRequestBody paging) {
        support.authorize(actor, session, WorkflowPermissions.DEFINITION_READ);
        WorkflowActionType type = code(WorkflowActionType.class, actionType, "actionType");
        ActionTemplateStatus state = code(ActionTemplateStatus.class, status, "status");
        String text = blankToNull(keyword);
        List<WorkflowActionTemplate> rows = templates.page(type, state, text, paging.offset(), paging.size());
        Map<String, String> people = support.displayNames(rows.stream().map(WorkflowActionTemplate::getUpdatedBy)
            .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new)));
        Map<Long, Integer> referenceCounts = tasks.templateReferenceCounts(
            rows.stream().map(WorkflowActionTemplate::getActionTemplateId).toList());
        List<WorkflowActionTemplateResponseBody> items = rows.stream().map(row -> {
            WorkflowActionTemplateResponseBody body = assembler.template(row, false, people);
            body.setReferencedDefinitionCount(referenceCounts.getOrDefault(row.getActionTemplateId(), 0));
            return body;
        }).toList();
        return new PagingResponseBody<>(items, paging.page(), paging.size(), templates.count(type, state, text));
    }

    /** 动作模板详情。 */
    public WorkflowActionTemplateResponseBody getTemplate(String actor, String session, long templateId) {
        support.authorize(actor, session, WorkflowPermissions.DEFINITION_READ);
        WorkflowActionTemplate row = templates.find(templateId);
        if (row == null) {
            throw support.notFound();
        }
        Map<String, String> people = row.getUpdatedBy() == null ? Map.of()
            : support.displayNames(List.of(row.getUpdatedBy()));
        return assembler.template(row, true, people);
    }

    /** 流程定义分页；launchable 为 true 时只返回 ACTIVE 且已发布的定义（发起入口）。 */
    public PagingResponseBody<WorkflowDefinitionSummaryBody> listDefinitions(String actor, String session, String status,
        String keyword, boolean launchable, PagingRequestBody paging) {
        support.authorize(actor, session,
            launchable ? WorkflowPermissions.INSTANCE_START : WorkflowPermissions.DEFINITION_READ);
        WorkflowDefinitionStatus state = launchable ? WorkflowDefinitionStatus.ACTIVE
            : code(WorkflowDefinitionStatus.class, status, "status");
        String text = blankToNull(keyword);
        List<WorkflowDefinitionSummaryBody> items = definitions.page(state, text, launchable, paging.offset(),
            paging.size()).stream().map(assembler::definition).toList();
        return new PagingResponseBody<>(items, paging.page(), paging.size(), definitions.count(state, text, launchable));
    }

    /** 流程定义详情：草稿与校验问题、版本摘要。 */
    public WorkflowDefinitionDetailResponseBody getDefinition(String actor, String session, long definitionId) {
        support.authorize(actor, session, WorkflowPermissions.DEFINITION_READ);
        WorkflowDefinition definition = requireDefinition(definitionId);
        WorkflowDefinitionVersion draft = definition.getDraftVersionNo() == null ? null
            : versions.findByNo(definitionId, definition.getDraftVersionNo());
        var problems = draft == null ? List.<com.paytech.platform.support.api.workflow.response.WorkflowProblemBody>of()
            : assembler.problems(checker.check(draft.getGraphJson(), draft.getStartFormJson()).problems());
        List<WorkflowDefinitionVersion> summaries = versions.summaries(definitionId);
        return new WorkflowDefinitionDetailResponseBody(assembler.definition(definition),
            draft == null ? null : assembler.version(draft), problems,
            summaries.stream().map(assembler::version).toList(), definitionPeople(definitionId, summaries));
    }

    /** 草稿与各版本流程图引用到的账号显示名；无法解析的图跳过（草稿可保存不完整内容）。 */
    Map<String, String> definitionPeople(long definitionId, List<WorkflowDefinitionVersion> summaries) {
        Set<Long> ids = new LinkedHashSet<>();
        for (WorkflowDefinitionVersion summary : summaries) {
            WorkflowDefinitionVersion version = versions.findByNo(definitionId, summary.getVersionNo());
            try {
                ids.addAll(graphReader.read(version.getGraphJson()).referencedAccountIds());
            } catch (RuntimeException unreadable) {
                // 草稿可保存结构正确但语义不完整的图；结构错误的图没有可显示的人员。
            }
        }
        return support.displayNames(ids);
    }

    /** 引用某动作模板的已发布版本：冻结版本、是否等于当前版本、运行中实例数。 */
    public WorkflowTemplateReferenceListResponseBody listTemplateReferences(String actor, String session,
        long templateId) {
        support.authorize(actor, session, WorkflowPermissions.DEFINITION_READ);
        WorkflowActionTemplate template = templates.find(templateId);
        if (template == null) {
            throw support.notFound();
        }
        List<WorkflowTemplateReferenceListResponseBody.Item> items = new ArrayList<>();
        for (WorkflowTemplateReferenceView row : tasks.templateReferences(templateId)) {
            Integer frozen = runtimeJson.snapshotVersions(row.getActionSnapshotJson()).get(templateId);
            if (frozen == null) {
                continue;
            }
            items.add(new WorkflowTemplateReferenceListResponseBody.Item(WorkflowResponseAssembler.id(row.getDefinitionId()),
                row.getDefinitionCode(), row.getDefinitionName(), row.getVersionNo(),
                Boolean.TRUE.equals(row.getCurrentVersion()), frozen, frozen.equals(template.getVersion()),
                row.getRunningInstanceCount()));
        }
        return new WorkflowTemplateReferenceListResponseBody(Long.toString(templateId), template.getVersion(), items);
    }

    /** 查看某个版本。 */
    public WorkflowVersionBody getVersion(String actor, String session, long definitionId, int versionNo) {
        support.authorize(actor, session, WorkflowPermissions.DEFINITION_READ);
        WorkflowDefinitionVersion version = versions.findByNo(definitionId, versionNo);
        if (version == null) {
            throw support.notFound();
        }
        return assembler.version(version);
    }

    /** 可发起流程的当前发布版本与默认人员显示名。 */
    public WorkflowLaunchResponseBody getLaunchable(String actor, String session, long definitionId) {
        support.authorize(actor, session, WorkflowPermissions.INSTANCE_START);
        WorkflowDefinition definition = requireDefinition(definitionId);
        if (definition.getStatus() != WorkflowDefinitionStatus.ACTIVE || definition.getCurrentVersionNo() == null) {
            throw support.notFound();
        }
        WorkflowDefinitionVersion version = versions.findByNo(definitionId, definition.getCurrentVersionNo());
        var body = assembler.version(version);
        var graph = checker.check(version.getGraphJson(), version.getStartFormJson()).graph();
        return new WorkflowLaunchResponseBody(assembler.definition(definition), version.getVersionNo(), body.getGraph(),
            body.getStartForm(), body.getActionSnapshot(), support.displayNames(graph.referencedAccountIds()));
    }

    /** 实例分页：默认只看参与的实例；scope=ALL 需 WORKFLOW_INSTANCE_READ。 */
    public PagingResponseBody<WorkflowInstanceSummaryBody> listInstances(String actor, String session, String scope,
        String definitionId, String status, String startedBy, String businessKey, String keyword,
        PagingRequestBody paging) {
        SessionContext current = support.authorize(actor, session, WorkflowPermissions.TASK_HANDLE);
        boolean all = "ALL".equals(scope);
        if (scope != null && !all && !"MINE".equals(scope)) {
            throw new InvalidParameterException("scope 只能为 MINE 或 ALL");
        }
        if (all && !support.has(current, WorkflowPermissions.INSTANCE_READ)) {
            throw new com.paytech.foundation.common.error.FailException(
                com.paytech.platform.support.api.enums.OperationsErrorCode.OPERATIONS_PERMISSION_DENIED);
        }
        var filter = new WorkflowInstanceFilter(definitionId == null ? null : support.parseId(definitionId, "definitionId"),
            code(WorkflowInstanceStatus.class, status, "status"),
            startedBy == null ? null : support.parseId(startedBy, "startedBy"), blankToNull(businessKey),
            all ? null : support.actorId(current), WorkflowTaskQueryRepositoryImpl.like(keyword));
        List<WorkflowInstance> rows = instances.page(filter, paging.offset(), paging.size());
        Map<String, String> people = support.displayNames(rows.stream().map(WorkflowInstance::getStartedBy).toList());
        List<WorkflowInstanceSummaryBody> items = rows.stream().map(row -> assembler.instance(row, people)).toList();
        enricher.instances(rows, items);
        return new PagingResponseBody<>(items, paging.page(), paging.size(), instances.count(filter));
    }

    /** 实例详情：参与人或 WORKFLOW_INSTANCE_READ；待审批节点附驳回方式与目标。 */
    public WorkflowInstanceDetailResponseBody getInstance(String actor, String session, long instanceId) {
        WorkflowInstanceContext context = visible(actor, session, instanceId);
        List<NodeRunState> states = context.getNodeRuns().stream().map(run -> new NodeRunState(run.getNodeRunId(),
            run.getNodeKey(), run.getNodeType(), run.getRoundNo(), run.getBranchKey(), run.getPreviousNodeRunId(),
            run.getStatus(), run.getSelectedEdgeKey())).toList();
        WorkflowInstance instance = context.getInstance();
        Map<String, String> people = support.displayNames(people(context));
        var body = new WorkflowInstanceDetailResponseBody();
        body.setInstance(assembler.instance(instance, people));
        body.setDefinitionCode(context.getDefinition().getDefinitionCode());
        body.setDefinitionName(context.getDefinition().getDefinitionName());
        body.setVersionNo(context.getVersion().getVersionNo());
        body.setVersion(instance.getVersion());
        body.setTerminatedBy(WorkflowResponseAssembler.id(instance.getTerminatedBy()));
        body.setTerminationReason(instance.getTerminationReason());
        body.setGraph(assembler.tree(context.getVersion().getGraphJson()));
        body.setStartForm(assembler.tree(context.getVersion().getStartFormJson()));
        body.setStartValues(assembler.tree(instance.getStartValuesJson()));
        body.setActionSnapshot(assembler.tree(context.getVersion().getActionSnapshotJson()));
        body.setNodeRuns(context.getNodeRuns().stream().map(run -> assembler.nodeRun(run, rejectOption(context, states,
            run))).toList());
        Map<Long, String> templateNames = runtimeJson.snapshotNames(context.getVersion().getActionSnapshotJson());
        body.setActionRuns(context.getActionRuns().stream()
            .map(run -> assembler.actionRun(run, templateNames.get(run.getActionTemplateId()))).toList());
        body.setVerifications(context.getVerifications().stream().map(assembler::verification).toList());
        body.setApprovals(context.getApprovals().stream().map(assembler::approval).toList());
        body.setTransfers(context.getTransfers().stream().map(assembler::transfer).toList());
        body.setNodeApprovers(nodeApprovers(context));
        body.setPeople(people);
        body.setFiles(attachments.facts(attachments.referenced(context)).values().stream().map(assembler::attachment)
            .toList());
        return body;
    }

    /**
     * 附件下载前的读侧检查（技术方案第 5 节、第 7 节 #28）：请求人是实例参与人或有 WORKFLOW_INSTANCE_READ，
     * 且该文件的地址（FileAddress.of(fileId)）出现在本实例的启动值或动作输出中；否则按不存在返回 404。只读本库、不开事务，下载授权由调用方在其后签发。
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param instanceId 实例 ID
     * @param fileId 文件 ID
     */
    public void requireAttachmentVisible(String actor, String session, long instanceId, long fileId) {
        WorkflowInstanceContext context = visible(actor, session, instanceId);
        String address = FileAddress.of(fileId).value();
        if (attachments.referenced(context).stream().map(AttachmentRules::address).noneMatch(address::equals)) {
            throw support.notFound();
        }
    }

    /** 有审批的任务节点当前生效的审批人；尚未开启审批的节点同样给出，用于待办页判断有无审批。 */
    Map<String, List<String>> nodeApprovers(WorkflowInstanceContext context) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        context.getGraph().nodes().stream().filter(WorkflowNode::hasApprovers).forEach(node -> result.put(node.key(),
            context.approversFor(node.key()).stream().map(WorkflowResponseAssembler::id).toList()));
        return result;
    }

    /**
     * 活动记录：节点状态、动作完成、审批决定、转交、通知回调，按时间正序
     * <li>动作完成取各动作运行最近一次完成；2026-10-22 前的历史验证记录照常列出，与其同时刻的那次提交不重复列出。</li>
     */
    public WorkflowActivityListResponseBody listActivities(String actor, String session, long instanceId) {
        WorkflowInstanceContext context = visible(actor, session, instanceId);
        Map<String, String> people = support.displayNames(people(context));
        List<WorkflowActivityBody> items = new ArrayList<>();
        context.getNodeRuns().stream().filter(run -> run.getNodeType() == WorkflowNodeType.TASK)
            .forEach(run -> items.add(assembler.nodeActivity(run)));
        context.getVerifications().forEach(row -> items.add(assembler.verificationActivity(row,
            nodeOfAction(context, row.getActionRunId()), people)));
        context.getActionRuns().stream().filter(run -> run.getSubmittedTime() != null && !recordedByVerification(context, run))
            .forEach(run -> items.add(assembler.completionActivity(run, context.nodeRun(run.getNodeRunId()).orElse(null),
                people)));
        context.getApprovals().stream().filter(row -> row.getDecidedTime() != null
            && row.getStatus() != ApprovalStatus.PENDING).forEach(row -> items.add(assembler.approvalActivity(row,
                context.nodeRun(row.getNodeRunId()).orElse(null), people)));
        context.getTransfers().forEach(row -> items.add(assembler.transferActivity(row, people)));
        context.getCallbacks().forEach(row -> items.add(assembler.callbackActivity(row,
            row.getNodeRunId() == null ? null : context.nodeRun(row.getNodeRunId()).orElse(null))));
        items.sort(WorkflowResponseAssembler.byTime());
        return new WorkflowActivityListResponseBody(items);
    }

    /** 我的待办分页。 */
    public PagingResponseBody<WorkflowTaskBody> myTasks(String actor, String session, String taskType, String keyword,
        PagingRequestBody paging) {
        SessionContext current = support.authorize(actor, session, WorkflowPermissions.TASK_HANDLE);
        requireTaskType(taskType);
        long me = support.actorId(current);
        List<WorkflowTaskView> rows = tasks.page(me, taskType, keyword, paging.offset(), paging.size());
        List<WorkflowTaskBody> items = rows.stream().map(assembler::task).toList();
        enricher.tasks(rows, items);
        return new PagingResponseBody<>(items, paging.page(), paging.size(), tasks.count(me, taskType, keyword));
    }

    /** 我已处理分页：我提交过的动作与我已决定的审批。 */
    public PagingResponseBody<WorkflowHandledTaskBody> handledTasks(String actor, String session, String taskType,
        String keyword, PagingRequestBody paging) {
        SessionContext current = support.authorize(actor, session, WorkflowPermissions.TASK_HANDLE);
        requireTaskType(taskType);
        long me = support.actorId(current);
        return new PagingResponseBody<>(enricher.handled(tasks.handledPage(me, taskType, keyword, paging.offset(),
            paging.size())), paging.page(), paging.size(), tasks.handledCount(me, taskType, keyword));
    }

    /** taskType 只能为空、ACTION 或 APPROVAL。 */
    static void requireTaskType(String taskType) {
        if (taskType != null && !Set.of("ACTION", "APPROVAL").contains(taskType)) {
            throw new InvalidParameterException("taskType 只能为 ACTION 或 APPROVAL");
        }
    }

    /** 加载实例并要求可见；不可见与不存在一样返回 404。 */
    WorkflowInstanceContext visible(String actor, String session, long instanceId) {
        SessionContext current = support.authorize(actor, session, WorkflowPermissions.TASK_HANDLE);
        WorkflowInstanceContext context = queries.load(instanceId, support.now());
        if (context == null || !context.isParticipant(support.actorId(current))
            && !support.has(current, WorkflowPermissions.INSTANCE_READ)) {
            throw support.notFound();
        }
        return context;
    }

    /** 待审批任务节点的驳回选项；其他节点为 null。 */
    com.paytech.platform.support.workflow.domain.runtime.RejectOption rejectOption(WorkflowInstanceContext context,
        List<NodeRunState> states, WorkflowNodeRun run) {
        if (run.getNodeType() != WorkflowNodeType.TASK || run.getStatus() != NodeRunStatus.APPROVING) {
            return null;
        }
        return navigator.rejectOption(context.getGraph(), states, run.getNodeRunId());
    }

    /** 实例中出现的全部人员。 */
    Set<Long> people(WorkflowInstanceContext context) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(context.getInstance().getStartedBy());
        context.getActionRuns().forEach(run -> {
            ids.add(run.getAssigneeId());
            ids.add(run.getSubmittedBy());
        });
        context.getApprovals().stream().map(WorkflowApproval::getApproverId).forEach(ids::add);
        context.getTransfers().forEach(row -> {
            ids.add(row.getFromUserId());
            ids.add(row.getToUserId());
            ids.add(row.getTransferredBy());
        });
        context.getVerifications().forEach(row -> ids.add(row.getVerifiedBy()));
        ids.add(context.getInstance().getTerminatedBy());
        context.getGraph().nodes().stream().filter(WorkflowNode::hasApprovers)
            .forEach(node -> ids.addAll(context.approversFor(node.key())));
        return ids.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 动作运行所在的节点运行。 */
    WorkflowNodeRun nodeOfAction(WorkflowInstanceContext context, Long actionRunId) {
        return context.getActionRuns().stream().filter(run -> run.getActionRunId().equals(actionRunId)).findFirst()
            .flatMap(run -> context.nodeRun(run.getNodeRunId())).orElse(null);
    }

    /** 动作运行最近一次提交是否已由历史验证记录表达（旧版提交与验证同一时刻写入）。 */
    boolean recordedByVerification(WorkflowInstanceContext context, WorkflowActionRun run) {
        return context.getVerifications().stream().anyMatch(row -> row.getActionRunId().equals(run.getActionRunId())
            && row.getVerifiedTime().equals(run.getSubmittedTime()));
    }

    /** 读取定义；不存在为 404。 */
    WorkflowDefinition requireDefinition(long definitionId) {
        WorkflowDefinition definition = definitions.find(definitionId);
        if (definition == null) {
            throw support.notFound();
        }
        return definition;
    }

    /**
     * 可空编码转枚举；未知编码为参数错误。
     *
     * @param type 枚举类型
     * @param value 编码
     * @param name 参数名
     * @param <E> 枚举类型
     * @return 枚举或 null
     */
    static <E extends Enum<E> & com.paytech.foundation.common.enums.CodeEnum> E code(Class<E> type, String value,
        String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        E result = EnumUtils.getEnumByCode(type, value);
        if (result == null) {
            throw new InvalidParameterException(name + " 取值不合法：" + value);
        }
        return result;
    }

    /** 空白转 null。 */
    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
