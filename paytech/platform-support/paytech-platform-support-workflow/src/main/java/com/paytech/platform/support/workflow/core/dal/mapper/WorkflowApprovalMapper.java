package com.paytech.platform.support.workflow.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowApproval;

/** t_workflow_approval 的 MyBatis 映射，SQL 位于 mapper/workflow/WorkflowApprovalMapper.xml。 */
public interface WorkflowApprovalMapper {

    /** 插入一行。 */
    int insert(@Param("row") WorkflowApproval row);

    /** 按主键读取。 */
    WorkflowApproval selectById(@Param("id") long id);

    /** 见 XML 同名语句。 */
    List<WorkflowApproval> selectByInstance(@Param("instanceId") long instanceId);

    /** 见 XML 同名语句。 */
    int update(@Param("row") WorkflowApproval row);
}
