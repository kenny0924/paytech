package com.paytech.risk.decision.domainservice.repository;

import java.util.List;
import com.paytech.risk.decision.core.dal.dataobject.RuleActionParameter;

/** RuleActionParameter 单表写入边界。 */
public interface RuleActionParameterRepository {
    /** 插入已核验的本表事实；空列表不执行 SQL。 */
    void insertAll(List<RuleActionParameter> rows);
}
