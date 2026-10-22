package com.paytech.platform.support.workflow.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinition;
import com.paytech.platform.support.workflow.core.enums.WorkflowDefinitionStatus;

/** t_workflow_definition 的 MyBatis 映射，SQL 位于 mapper/workflow/WorkflowDefinitionMapper.xml。 */
public interface WorkflowDefinitionMapper {

    /** 插入一行。 */
    int insert(@Param("row") WorkflowDefinition row);

    /** 按主键读取。 */
    WorkflowDefinition selectById(@Param("id") long id);

    /** 见 XML 同名语句。 */
    WorkflowDefinition selectByCode(@Param("code") String code);

    /** 见 XML 同名语句。 */
    List<WorkflowDefinition> selectByIds(@Param("ids") Collection<Long> ids);

    /** 见 XML 同名语句。 */
    int update(@Param("row") WorkflowDefinition row);

    /** 见 XML 同名语句。 */
    List<WorkflowDefinition> page(@Param("status") WorkflowDefinitionStatus status, @Param("keyword") String keyword, @Param("launchable") boolean launchable, @Param("offset") long offset, @Param("size") int size);

    /** 见 XML 同名语句。 */
    long count(@Param("status") WorkflowDefinitionStatus status, @Param("keyword") String keyword, @Param("launchable") boolean launchable);
}
