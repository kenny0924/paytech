package com.paytech.platform.support.workflow.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowVerification;

/** t_workflow_verification 的 MyBatis 映射，SQL 位于 mapper/workflow/WorkflowVerificationMapper.xml。 */
public interface WorkflowVerificationMapper {

    /** 按主键读取。 */
    WorkflowVerification selectById(@Param("id") long id);

    /** 见 XML 同名语句。 */
    List<WorkflowVerification> selectByInstance(@Param("instanceId") long instanceId);
}
