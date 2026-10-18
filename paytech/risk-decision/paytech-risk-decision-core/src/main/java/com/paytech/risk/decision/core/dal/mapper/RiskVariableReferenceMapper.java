package com.paytech.risk.decision.core.dal.mapper;

import com.paytech.risk.decision.core.dal.dataobject.*;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 精确关系和条件整树候选的固定 SQL 入口。 */
public interface RiskVariableReferenceMapper {
    /** 初始读取全部阶段的行号上界。 */
    List<VariableReferenceBound> bounds();
    /** 依据固定阶段读取有界候选，不使用 JSON 路径或文本模糊筛选。 */
    List<VariableReferenceRow> scan(@Param("phase") String phase, @Param("code") String code,
        @Param("scope") String scope, @Param("lastId") long lastId, @Param("upperBound") long upperBound, @Param("limit") int limit);
}
