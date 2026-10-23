package com.paytech.platform.support.workflow.domainservice.repository.impl;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.paytech.platform.support.workflow.core.dal.mapper.WorkflowTaskMapper;
import com.paytech.platform.support.workflow.domain.query.WorkflowHandledTaskView;
import com.paytech.platform.support.workflow.domain.query.WorkflowNodeProgressView;
import com.paytech.platform.support.workflow.domain.query.WorkflowOpenAssignmentView;
import com.paytech.platform.support.workflow.domain.query.WorkflowTaskView;
import com.paytech.platform.support.workflow.domain.query.WorkflowTemplateReferenceCountView;
import com.paytech.platform.support.workflow.domain.query.WorkflowTemplateReferenceView;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowTaskQueryRepository;

import lombok.AllArgsConstructor;

/** 我的待办组合查询实现。 */
@Repository
@AllArgsConstructor
public class WorkflowTaskQueryRepositoryImpl implements WorkflowTaskQueryRepository {

    private final WorkflowTaskMapper mapper;

    /** 按待办开始时间倒序分页。 */
    @Override
    public List<WorkflowTaskView> page(long accountId, String taskType, String keyword, long offset, int size) {
        return mapper.page(accountId, taskType, like(keyword), offset, size);
    }

    /** 与分页相同条件的总数。 */
    @Override
    public long count(long accountId, String taskType, String keyword) {
        return mapper.count(accountId, taskType, like(keyword));
    }

    /** 我已处理分页。 */
    @Override
    public List<WorkflowHandledTaskView> handledPage(long accountId, String taskType, String keyword, long offset,
        int size) {
        return mapper.handledPage(accountId, taskType, like(keyword), offset, size);
    }

    /** 我已处理总数。 */
    @Override
    public long handledCount(long accountId, String taskType, String keyword) {
        return mapper.handledCount(accountId, taskType, like(keyword));
    }

    /** 一批实例的 TASK 节点运行。 */
    @Override
    public List<WorkflowNodeProgressView> taskRuns(Collection<Long> instanceIds) {
        return instanceIds.isEmpty() ? List.of() : mapper.taskRuns(instanceIds);
    }

    /** 一批实例的当前处理人。 */
    @Override
    public List<WorkflowOpenAssignmentView> openAssignments(Collection<Long> instanceIds) {
        return instanceIds.isEmpty() ? List.of() : mapper.openAssignments(instanceIds);
    }

    /** 模板引用预筛。 */
    @Override
    public List<WorkflowTemplateReferenceView> templateReferences(long templateId) {
        return mapper.templateReferences(Long.toString(templateId));
    }

    /** 一批模板被引用的流程数；快照中的模板 ID 为文本，按文本匹配。 */
    @Override
    public Map<Long, Integer> templateReferenceCounts(Collection<Long> templateIds) {
        if (templateIds.isEmpty()) {
            return Map.of();
        }
        List<String> ids = templateIds.stream().map(String::valueOf).toList();
        return mapper.templateReferenceCounts(ids).stream().collect(Collectors.toMap(
            WorkflowTemplateReferenceCountView::getTemplateId, WorkflowTemplateReferenceCountView::getDefinitionCount));
    }

    /**
     * 关键字按字面匹配：转义 LIKE 通配符（转义符 !）；空白视为不限。
     *
     * @param keyword 关键字
     * @return 转义后的关键字或 null
     */
    public static String like(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
