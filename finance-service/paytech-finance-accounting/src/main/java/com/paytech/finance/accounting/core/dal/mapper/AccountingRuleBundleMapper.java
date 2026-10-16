package com.paytech.finance.accounting.core.dal.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.finance.accounting.core.dal.dataobject.AccountingRuleBundle;

/** t_accounting_rule_bundle：规则集持久化。 */
@Mapper
public interface AccountingRuleBundleMapper {

    /** 按代码读取规则集。 */
    AccountingRuleBundle selectByCode(@Param("bundleCode") String bundleCode);

    /** 按代码读取规则集并加排他锁。 */
    AccountingRuleBundle selectByCodeForUpdate(@Param("bundleCode") String bundleCode);

    /**
     * 以期望版本切换当前版本指针，版本号加一
     *
     * @return 受影响行数
     */
    int updateCurrentVersion(@Param("bundle") AccountingRuleBundle bundle, @Param("currentVerId") long currentVerId,
        @Param("now") Instant now);
}
