package com.paytech.platform.support.workflow.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.workflow.domain.query.WorkflowHandledTaskView;
import com.paytech.platform.support.workflow.domain.query.WorkflowNodeProgressView;
import com.paytech.platform.support.workflow.domain.query.WorkflowOpenAssignmentView;
import com.paytech.platform.support.workflow.domain.query.WorkflowTaskView;
import com.paytech.platform.support.workflow.domain.query.WorkflowTemplateReferenceCountView;
import com.paytech.platform.support.workflow.domain.query.WorkflowTemplateReferenceView;

/** 我的待办组合查询（动作运行、审批、节点运行与实例），SQL 位于 mapper/workflow/WorkflowTaskMapper.xml。 */
public interface WorkflowTaskMapper {

    /** 按待办开始时间倒序分页。 */
    List<WorkflowTaskView> page(@Param("accountId") long accountId, @Param("taskType") String taskType,
        @Param("keyword") String keyword, @Param("offset") long offset, @Param("size") int size);

    /** 与 page 相同条件的总数。 */
    long count(@Param("accountId") long accountId, @Param("taskType") String taskType,
        @Param("keyword") String keyword);

    /** 我已处理：按处理时间倒序分页。 */
    List<WorkflowHandledTaskView> handledPage(@Param("accountId") long accountId, @Param("taskType") String taskType,
        @Param("keyword") String keyword, @Param("offset") long offset, @Param("size") int size);

    /** 与 handledPage 相同条件的总数。 */
    long handledCount(@Param("accountId") long accountId, @Param("taskType") String taskType,
        @Param("keyword") String keyword);

    /** 一批实例的 TASK 节点运行。 */
    List<WorkflowNodeProgressView> taskRuns(@Param("ids") Collection<Long> instanceIds);

    /** 一批实例进行中节点的当前处理人。 */
    List<WorkflowOpenAssignmentView> openAssignments(@Param("ids") Collection<Long> instanceIds);

    /** 冻结了该模板的已发布版本（文本预筛）。 */
    List<WorkflowTemplateReferenceView> templateReferences(@Param("templateId") String templateId);

    /** 一批模板各自被多少个流程定义的当前发布版本引用；未被引用的模板不返回行。 */
    List<WorkflowTemplateReferenceCountView> templateReferenceCounts(@Param("ids") Collection<String> templateIds);
}
