package com.paytech.platform.support.workflow.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionRun;

/** t_workflow_action_run 的 MyBatis 映射，SQL 位于 mapper/workflow/WorkflowActionRunMapper.xml。 */
public interface WorkflowActionRunMapper {

    /** 插入一行。 */
    int insert(@Param("row") WorkflowActionRun row);

    /** 按主键读取。 */
    WorkflowActionRun selectById(@Param("id") long id);

    /** 见 XML 同名语句。 */
    List<WorkflowActionRun> selectByInstance(@Param("instanceId") long instanceId);

    /** 见 XML 同名语句。 */
    int update(@Param("row") WorkflowActionRun row);

    /**
     * 动作输出引用了该文件地址的其他实例，最多一个。
     *
     * @param fileAddress 已按转义符 ! 转义 LIKE 通配符的文件地址
     * @param excludeInstanceId 不计入的实例；可空
     * @return 实例 ID
     */
    List<Long> selectInstanceIdsReferencingFile(@Param("fileAddress") String fileAddress,
        @Param("excludeInstanceId") Long excludeInstanceId);
}
