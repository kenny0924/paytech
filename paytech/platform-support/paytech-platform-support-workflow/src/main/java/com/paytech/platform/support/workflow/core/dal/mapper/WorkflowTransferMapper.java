package com.paytech.platform.support.workflow.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowTransfer;

/** t_workflow_transfer 的 MyBatis 映射，SQL 位于 mapper/workflow/WorkflowTransferMapper.xml。 */
public interface WorkflowTransferMapper {

    /** 插入一行。 */
    int insert(@Param("row") WorkflowTransfer row);

    /** 按主键读取。 */
    WorkflowTransfer selectById(@Param("id") long id);

    /** 见 XML 同名语句。 */
    List<WorkflowTransfer> selectByInstance(@Param("instanceId") long instanceId);
}
