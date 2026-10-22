package com.paytech.platform.support.workflow.service.processor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.platform.support.api.workflow.response.WorkflowCurrentNodeBody;
import com.paytech.platform.support.api.workflow.response.WorkflowHandledTaskBody;
import com.paytech.platform.support.api.workflow.response.WorkflowInstanceSummaryBody;
import com.paytech.platform.support.api.workflow.response.WorkflowPersonBody;
import com.paytech.platform.support.api.workflow.response.WorkflowTaskBody;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinition;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinitionVersion;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowInstance;
import com.paytech.platform.support.workflow.core.enums.NodeRunStatus;
import com.paytech.platform.support.workflow.core.enums.WorkflowNodeType;
import com.paytech.platform.support.workflow.domain.graph.WorkflowGraph;
import com.paytech.platform.support.workflow.domain.graph.WorkflowNode;
import com.paytech.platform.support.workflow.domain.query.WorkflowHandledTaskView;
import com.paytech.platform.support.workflow.domain.query.WorkflowNodeProgressView;
import com.paytech.platform.support.workflow.domain.query.WorkflowOpenAssignmentView;
import com.paytech.platform.support.workflow.domain.query.WorkflowTaskView;
import com.paytech.platform.support.workflow.domainservice.convert.WorkflowGraphJsonReader;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowDefinitionRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowDefinitionVersionRepository;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowTaskQueryRepository;

import lombok.RequiredArgsConstructor;

/**
 * 列表页的展示附加信息：流程名称、版本号、节点名称、进度与当前处理人。
 * <li>一页内按批读取：定义按 ID 批量、版本按 ID 逐个（同页去重）、TASK 节点运行与当前处理人各一次批量查询。</li>
 * <li>节点名称取实例绑定版本的流程图；版本缺失时名称为空，不影响主列表。</li>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class WorkflowListEnricher {

    private final WorkflowProcessorSupport support;
    private final WorkflowTaskQueryRepository tasks;
    private final WorkflowDefinitionRepository definitions;
    private final WorkflowDefinitionVersionRepository versions;
    private final WorkflowGraphJsonReader graphs = new WorkflowGraphJsonReader();

    /**
     * 实例摘要附加流程名称、版本号、进度与当前节点处理人。
     *
     * @param rows 本页实例
     * @param summaries 基本摘要，与 rows 同序
     */
    public void instances(List<WorkflowInstance> rows, List<WorkflowInstanceSummaryBody> summaries) {
        Map<Long, String> names = definitionNames(rows.stream().map(WorkflowInstance::getDefinitionId).toList());
        Map<Long, Version> versionCache = new HashMap<>();
        List<Long> ids = rows.stream().map(WorkflowInstance::getInstanceId).toList();
        Map<Long, List<WorkflowNodeProgressView>> runs = tasks.taskRuns(ids).stream()
            .collect(Collectors.groupingBy(WorkflowNodeProgressView::getInstanceId));
        List<WorkflowOpenAssignmentView> open = tasks.openAssignments(ids);
        Map<String, String> people = support.displayNames(open.stream().map(WorkflowOpenAssignmentView::getUserId)
            .collect(Collectors.toCollection(LinkedHashSet::new)));
        Map<Long, List<WorkflowOpenAssignmentView>> openByInstance = open.stream()
            .collect(Collectors.groupingBy(WorkflowOpenAssignmentView::getInstanceId));
        for (int index = 0; index < rows.size(); index++) {
            WorkflowInstance row = rows.get(index);
            WorkflowInstanceSummaryBody body = summaries.get(index);
            Version version = versionCache.computeIfAbsent(row.getDefinitionVersionId(), this::version);
            body.setDefinitionName(names.get(row.getDefinitionId()));
            body.setVersionNo(version.versionNo());
            body.setTotalTaskCount(version.graph().map(WorkflowListEnricher::taskCount).orElse(null));
            body.setCompletedTaskCount(completed(runs.getOrDefault(row.getInstanceId(), List.of())));
            body.setCurrentNodes(currentNodes(openByInstance.getOrDefault(row.getInstanceId(), List.of()), version,
                people));
        }
    }

    /**
     * 待办附加流程名称与节点名称。
     *
     * @param rows 本页待办
     * @param bodies 基本响应，与 rows 同序
     */
    public void tasks(List<WorkflowTaskView> rows, List<WorkflowTaskBody> bodies) {
        Map<Long, String> names = definitionNames(rows.stream().map(WorkflowTaskView::getDefinitionId).toList());
        Map<Long, Version> cache = new HashMap<>();
        for (int index = 0; index < rows.size(); index++) {
            WorkflowTaskView row = rows.get(index);
            Version version = cache.computeIfAbsent(row.getDefinitionVersionId(), this::version);
            bodies.get(index).setDefinitionName(names.get(row.getDefinitionId()));
            bodies.get(index).setNodeName(version.nodeName(row.getNodeKey()));
        }
    }

    /**
     * 我已处理的响应。
     *
     * @param rows 本页已处理项
     * @return 响应
     */
    public List<WorkflowHandledTaskBody> handled(List<WorkflowHandledTaskView> rows) {
        Map<Long, String> names = definitionNames(rows.stream().map(WorkflowHandledTaskView::getDefinitionId).toList());
        Map<Long, Version> cache = new HashMap<>();
        List<WorkflowHandledTaskBody> result = new ArrayList<>();
        for (WorkflowHandledTaskView row : rows) {
            Version version = cache.computeIfAbsent(row.getDefinitionVersionId(), this::version);
            result.add(new WorkflowHandledTaskBody(row.getTaskType(), WorkflowResponseAssembler.id(row.getTaskId()),
                WorkflowResponseAssembler.id(row.getInstanceId()), row.getInstanceTitle(), row.getInstanceStatus(),
                names.get(row.getDefinitionId()), row.getNodeKey(), version.nodeName(row.getNodeKey()),
                row.getRoundNo(), row.getActionKey(), row.getResult(), row.getHandledTime()));
        }
        return result;
    }

    /** 定义 ID 到名称。 */
    Map<Long, String> definitionNames(Collection<Long> ids) {
        Set<Long> distinct = new LinkedHashSet<>(ids);
        return distinct.isEmpty() ? Map.of() : definitions.findByIds(distinct).stream()
            .collect(Collectors.toMap(WorkflowDefinition::getDefinitionId, WorkflowDefinition::getDefinitionName));
    }

    /** 读取版本并解析流程图；版本缺失或图无法解析时只保留可得信息。 */
    Version version(Long definitionVersionId) {
        WorkflowDefinitionVersion row = definitionVersionId == null ? null : versions.find(definitionVersionId);
        if (row == null) {
            return new Version(null, Optional.empty());
        }
        try {
            return new Version(row.getVersionNo(), Optional.of(graphs.read(row.getGraphJson())));
        } catch (RuntimeException unreadable) {
            return new Version(row.getVersionNo(), Optional.empty());
        }
    }

    /** 流程图中的 TASK 节点数。 */
    static int taskCount(WorkflowGraph graph) {
        return (int) graph.nodes().stream().filter(node -> node.type() == WorkflowNodeType.TASK).count();
    }

    /** 最新一轮已放行的不同 TASK 节点数。 */
    static int completed(List<WorkflowNodeProgressView> runs) {
        Map<String, WorkflowNodeProgressView> latest = new HashMap<>();
        for (WorkflowNodeProgressView run : runs) {
            latest.merge(run.getNodeKey(), run, (left, right) -> left.getRoundNo() >= right.getRoundNo() ? left : right);
        }
        return (int) latest.values().stream().filter(run -> NodeRunStatus.PASSED.getCode().equals(run.getStatus()))
            .count();
    }

    /** 按节点归并当前处理人，保持查询顺序。 */
    static List<WorkflowCurrentNodeBody> currentNodes(List<WorkflowOpenAssignmentView> open, Version version,
        Map<String, String> people) {
        Map<String, WorkflowCurrentNodeBody> byNode = new LinkedHashMap<>();
        Map<String, Set<Long>> seen = new HashMap<>();
        for (WorkflowOpenAssignmentView row : open) {
            WorkflowCurrentNodeBody node = byNode.computeIfAbsent(row.getNodeKey(), key -> new WorkflowCurrentNodeBody(
                key, version.nodeName(key), row.getNodeStatus(), new ArrayList<>()));
            if (seen.computeIfAbsent(row.getNodeKey(), key -> new LinkedHashSet<>()).add(row.getUserId())) {
                String id = Long.toString(row.getUserId());
                node.getHandlers().add(new WorkflowPersonBody(id, people.get(id)));
            }
        }
        return new ArrayList<>(byNode.values());
    }

    /**
     * 版本号与流程图。
     *
     * @param versionNo 版本号
     * @param graph 流程图
     */
    record Version(Integer versionNo, Optional<WorkflowGraph> graph) {

        /** 节点名称；找不到为 null。 */
        String nodeName(String nodeKey) {
            return graph.flatMap(value -> value.node(nodeKey)).map(WorkflowNode::name).orElse(null);
        }
    }
}
