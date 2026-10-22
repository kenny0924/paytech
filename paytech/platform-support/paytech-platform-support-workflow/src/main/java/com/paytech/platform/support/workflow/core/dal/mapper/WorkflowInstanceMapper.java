package com.paytech.platform.support.workflow.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowInstance;
import com.paytech.platform.support.workflow.domain.query.WorkflowInstanceFilter;

/** t_workflow_instance 的 MyBatis 映射，SQL 位于 mapper/workflow/WorkflowInstanceMapper.xml。 */
public interface WorkflowInstanceMapper {

    /** 插入一行。 */
    int insert(@Param("row") WorkflowInstance row);

    /** 按主键读取。 */
    WorkflowInstance selectById(@Param("id") long id);

    /** 见 XML 同名语句。 */
    int update(@Param("row") WorkflowInstance row);

    /** 见 XML 同名语句。 */
    List<WorkflowInstance> page(@Param("filter") WorkflowInstanceFilter filter, @Param("offset") long offset, @Param("size") int size);

    /** 见 XML 同名语句。 */
    long count(@Param("filter") WorkflowInstanceFilter filter);

    /**
     * 启动值的 FILE 字段引用了该文件地址的其他实例，最多一个。
     *
     * @param fileAddress 已按转义符 ! 转义 LIKE 通配符的文件地址
     * @param excludeInstanceId 不计入的实例；可空
     * @return 实例 ID
     */
    List<Long> selectIdsReferencingFile(@Param("fileAddress") String fileAddress, @Param("excludeInstanceId") Long excludeInstanceId);
}
