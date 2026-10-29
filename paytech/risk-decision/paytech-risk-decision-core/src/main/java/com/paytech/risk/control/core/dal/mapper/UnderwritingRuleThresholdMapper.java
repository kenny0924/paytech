package com.paytech.risk.control.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.paytech.risk.control.core.dal.dataobject.UnderwritingRuleThreshold;

/** 自动承保币种阈值表映射（只读）。 */
public interface UnderwritingRuleThresholdMapper {
    /** 读取某规则版本的全部阈值，币种升序。 */
    List<UnderwritingRuleThreshold> selectByRuleVersionId(@Param("ruleVersionId") long ruleVersionId);
}
