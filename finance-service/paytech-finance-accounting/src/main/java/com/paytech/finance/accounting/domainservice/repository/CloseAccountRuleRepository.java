package com.paytech.finance.accounting.domainservice.repository;

import java.time.Instant;

import com.paytech.finance.accounting.core.dal.dataobject.CloseAccountRule;

/** t_close_account_rule：结转规则读取。 */
public interface CloseAccountRuleRepository {

    /** 读取指定时点生效的结转规则，不存在返回空。 */
    CloseAccountRule findEffective(long chartId, long sourceAccountId, Instant at);
}
