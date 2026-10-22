package com.paytech.platform.support.workflow.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinitionVersion;

/** t_workflow_definition_version 的 MyBatis 映射，SQL 位于 mapper/workflow/WorkflowDefinitionVersionMapper.xml。 */
public interface WorkflowDefinitionVersionMapper {

    /** 插入一行。 */
    int insert(@Param("row") WorkflowDefinitionVersion row);

    /** 按主键读取。 */
    WorkflowDefinitionVersion selectById(@Param("id") long id);

    /** 见 XML 同名语句。 */
    WorkflowDefinitionVersion selectByNo(@Param("definitionId") long definitionId, @Param("versionNo") int versionNo);

    /** 见 XML 同名语句。 */
    List<WorkflowDefinitionVersion> selectSummaries(@Param("definitionId") long definitionId);

    /** 见 XML 同名语句。 */
    int updateDraft(@Param("row") WorkflowDefinitionVersion row);

    /** 见 XML 同名语句。 */
    int publish(@Param("row") WorkflowDefinitionVersion row);
}
