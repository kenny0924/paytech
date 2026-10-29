package com.paytech.risk.control.core.dal.mapper;

import java.util.List;

import com.paytech.risk.control.core.dal.dataobject.UnderwritingRuleVersion;

/** 自动承保规则版本表映射（只读；版本以 DML 发布）。 */
public interface UnderwritingRuleVersionMapper {
    /** 读取 ACTIVE 版本。 */
    UnderwritingRuleVersion selectActive();

    /** 读取全部版本，版本号升序。 */
    List<UnderwritingRuleVersion> selectAll();
}
