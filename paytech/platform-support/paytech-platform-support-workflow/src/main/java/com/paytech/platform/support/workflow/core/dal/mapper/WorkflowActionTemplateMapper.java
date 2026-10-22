package com.paytech.platform.support.workflow.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionTemplate;
import com.paytech.platform.support.workflow.core.enums.ActionTemplateStatus;
import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;

/** t_workflow_action_template 的 MyBatis 映射，SQL 位于 mapper/workflow/WorkflowActionTemplateMapper.xml。 */
public interface WorkflowActionTemplateMapper {

    /** 插入一行。 */
    int insert(@Param("row") WorkflowActionTemplate row);

    /** 按主键读取。 */
    WorkflowActionTemplate selectById(@Param("id") long id);

    /** 见 XML 同名语句。 */
    WorkflowActionTemplate selectByCode(@Param("code") String code);

    /** 见 XML 同名语句。 */
    List<WorkflowActionTemplate> selectByIds(@Param("ids") Collection<Long> ids);

    /** 见 XML 同名语句。 */
    int update(@Param("row") WorkflowActionTemplate row);

    /** 见 XML 同名语句。 */
    List<WorkflowActionTemplate> page(@Param("actionType") WorkflowActionType actionType, @Param("status") ActionTemplateStatus status, @Param("keyword") String keyword, @Param("offset") long offset, @Param("size") int size);

    /** 见 XML 同名语句。 */
    long count(@Param("actionType") WorkflowActionType actionType, @Param("status") ActionTemplateStatus status, @Param("keyword") String keyword);
}
