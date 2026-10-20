package com.paytech.ledger.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.ledger.core.dal.dataobject.AccountingRuleLine;
import com.paytech.ledger.core.dal.mapper.AccountingRuleLineMapper;
import com.paytech.ledger.domainservice.repository.AccountingRuleLineRepository;

import lombok.AllArgsConstructor;

/** AccountingRuleLineRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class AccountingRuleLineRepositoryImpl implements AccountingRuleLineRepository {
    private final AccountingRuleLineMapper mapper;

    /** 版本全部规则行，按指令类型、行号升序。 */
    @Override
    public List<AccountingRuleLine> findByVersion(long bundleVerId) {
        return mapper.selectByVersion(bundleVerId);
    }

    /** 是否有规则集当前生效版本的规则行引用该科目代码。 */
    @Override
    public boolean existsCurrentByAccountCode(String accountCode) {
        return mapper.countCurrentByAccountCode(accountCode) > 0;
    }

    /** 批量写入一个版本的规则行，写入行数必须与输入一致。 */
    @Override
    public void insertAll(List<AccountingRuleLine> lines) {
        if (mapper.insertBatch(lines) != lines.size()) {
            throw new ErrorException("规则行写入数量不一致: " + lines.size());
        }
    }
}
