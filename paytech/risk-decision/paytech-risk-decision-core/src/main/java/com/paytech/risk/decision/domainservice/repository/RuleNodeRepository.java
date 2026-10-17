package com.paytech.risk.decision.domainservice.repository;

import java.util.List;
import com.paytech.risk.decision.core.dal.dataobject.RuleNode;

/** RuleNode 单表写入边界。 */
public interface RuleNodeRepository {
    /** 插入已核验的本表事实；空列表不执行 SQL。 */
    void insertAll(List<RuleNode> rows);
}
