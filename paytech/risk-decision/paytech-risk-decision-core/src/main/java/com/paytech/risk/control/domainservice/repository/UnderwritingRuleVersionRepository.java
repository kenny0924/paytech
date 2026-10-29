package com.paytech.risk.control.domainservice.repository;

import java.util.List;
import java.util.Optional;

import com.paytech.risk.control.core.dal.dataobject.UnderwritingRuleVersion;

/** 自动承保规则版本的单表只读边界。 */
public interface UnderwritingRuleVersionRepository {
    /** 读取 ACTIVE 版本。 */
    Optional<UnderwritingRuleVersion> findActive();

    /** 读取全部版本，版本号升序（数量很少，用于把版本 ID 换成版本号）。 */
    List<UnderwritingRuleVersion> findAll();
}
