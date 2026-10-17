package com.paytech.risk.decision.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.risk.decision.core.dal.dataobject.FeatureVersion;

/** Feature 执行版本表映射。 */
public interface FeatureVersionMapper {
    /** 插入不可变 Feature Version。 */
    int insert(@Param("row") FeatureVersion row);

    /** 按 ID 批量读取。 */
    List<FeatureVersion> selectByIds(@Param("ids") List<Long> ids);
}
