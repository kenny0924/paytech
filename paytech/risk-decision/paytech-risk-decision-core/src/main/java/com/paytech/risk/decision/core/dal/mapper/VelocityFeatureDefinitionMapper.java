package com.paytech.risk.decision.core.dal.mapper;

import com.paytech.risk.decision.core.dal.dataobject.VelocityFeatureDefinition;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 实时累计 Feature 的不可变参数表映射。 */
public interface VelocityFeatureDefinitionMapper {
    /** 插入一行。 */
    int insert(@Param("row") VelocityFeatureDefinition row);

    /** 按 Feature Version 批量读取。 */
    List<VelocityFeatureDefinition> selectByIds(@Param("ids") List<Long> ids);
}
