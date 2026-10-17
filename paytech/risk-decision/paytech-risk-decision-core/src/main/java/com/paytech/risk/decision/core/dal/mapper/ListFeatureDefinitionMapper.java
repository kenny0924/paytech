package com.paytech.risk.decision.core.dal.mapper;

import com.paytech.risk.decision.core.dal.dataobject.ListFeatureDefinition;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 名单命中 Feature 的不可变参数表映射。 */
public interface ListFeatureDefinitionMapper {
    /** 插入一行。 */
    int insert(@Param("row") ListFeatureDefinition row);

    /** 按 Feature Version 批量读取。 */
    List<ListFeatureDefinition> selectByIds(@Param("ids") List<Long> ids);
}
