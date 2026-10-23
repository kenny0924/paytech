package com.paytech.platform.support.workflow.domainservice.repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.paytech.platform.support.workflow.domain.query.WorkflowHandledTaskView;
import com.paytech.platform.support.workflow.domain.query.WorkflowNodeProgressView;
import com.paytech.platform.support.workflow.domain.query.WorkflowOpenAssignmentView;
import com.paytech.platform.support.workflow.domain.query.WorkflowTaskView;
import com.paytech.platform.support.workflow.domain.query.WorkflowTemplateReferenceView;

/** 我的待办组合查询（本模块四张运行表）。 */
public interface WorkflowTaskQueryRepository {

    /** 按待办开始时间倒序分页；taskType 为空不限。 */
    List<WorkflowTaskView> page(long accountId, String taskType, String keyword, long offset, int size);

    /** 与分页相同条件的总数。 */
    long count(long accountId, String taskType, String keyword);

    /** 我已处理：按处理时间倒序分页；keyword 为实例标题片段，可空。 */
    List<WorkflowHandledTaskView> handledPage(long accountId, String taskType, String keyword, long offset, int size);

    /** 与我已处理分页相同条件的总数。 */
    long handledCount(long accountId, String taskType, String keyword);

    /** 一批实例的 TASK 节点运行；空集合返回空列表。 */
    List<WorkflowNodeProgressView> taskRuns(Collection<Long> instanceIds);

    /** 一批实例进行中节点的当前处理人；空集合返回空列表。 */
    List<WorkflowOpenAssignmentView> openAssignments(Collection<Long> instanceIds);

    /** 冻结了该模板的已发布版本（文本预筛，调用方按快照精确核对）。 */
    List<WorkflowTemplateReferenceView> templateReferences(long templateId);

    /**
     * 一批模板各自被多少个流程定义引用，口径与引用查询的当前发布版本一致（按定义去重）。
     *
     * @param templateIds 模板 ID；空集合返回空 Map
     * @return 模板 ID 到流程定义数；未被引用的模板不在结果中
     */
    Map<Long, Integer> templateReferenceCounts(Collection<Long> templateIds);
}
