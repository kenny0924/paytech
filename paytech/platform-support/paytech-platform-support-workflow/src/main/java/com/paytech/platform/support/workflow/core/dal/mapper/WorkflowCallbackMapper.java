package com.paytech.platform.support.workflow.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowCallback;
import com.paytech.platform.support.workflow.core.enums.CallbackStatus;

/** t_workflow_callback 的 MyBatis 映射，SQL 位于 mapper/workflow/WorkflowCallbackMapper.xml。 */
public interface WorkflowCallbackMapper {

    /** 插入一行。 */
    int insert(@Param("row") WorkflowCallback row);

    /** 按主键读取。 */
    WorkflowCallback selectById(@Param("id") long id);

    /** 见 XML 同名语句。 */
    List<WorkflowCallback> selectByInstance(@Param("instanceId") long instanceId);

    /** 见 XML 同名语句。 */
    int update(@Param("row") WorkflowCallback row, @Param("expected") CallbackStatus expected);
}
