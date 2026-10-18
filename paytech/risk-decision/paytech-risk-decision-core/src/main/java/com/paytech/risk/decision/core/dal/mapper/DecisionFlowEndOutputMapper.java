package com.paytech.risk.decision.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.risk.decision.core.dal.dataobject.DecisionFlowEndOutput;

/** 结束节点附加输出表映射。 */
public interface DecisionFlowEndOutputMapper {
    /** 插入一行。 */
    int insert(@Param("row") DecisionFlowEndOutput row);

    /** 读取一个流程版本的全部输出，按节点与编码排序。 */
    List<DecisionFlowEndOutput> selectByVersion(@Param("flowVersionId") long flowVersionId);
}
