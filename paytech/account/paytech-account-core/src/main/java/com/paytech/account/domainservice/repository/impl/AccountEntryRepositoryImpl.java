package com.paytech.account.domainservice.repository.impl;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.account.core.dal.dataobject.AccountEntry;
import com.paytech.account.core.dal.mapper.AccountEntryMapper;
import com.paytech.account.domainservice.repository.AccountEntryRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.web.paging.PagingRequestBody;

import lombok.AllArgsConstructor;

/** AccountEntryRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class AccountEntryRepositoryImpl implements AccountEntryRepository {
    private final AccountEntryMapper mapper;

    /** 空集合不写；同一原流水被并发冲回两次时以并发冲突失败。 */
    @Override
    public void insertAll(List<AccountEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        try {
            mapper.insertAll(entries);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("账户流水被并发写入: " + entries.getFirst().getPostingId(), e);
        }
    }

    /** 空集合不查询。 */
    @Override
    public List<AccountEntry> findByPostingIds(List<Long> postingIds) {
        return postingIds.isEmpty() ? List.of() : mapper.selectByPostingIds(postingIds);
    }

    /** 空集合不查询。 */
    @Override
    public List<AccountEntry> findReversalsOf(List<Long> entryIds) {
        return entryIds.isEmpty() ? List.of() : mapper.selectByReversedIds(entryIds);
    }

    /** 数据库 LIMIT / OFFSET 分页。 */
    @Override
    public List<AccountEntry> findPageByAccount(long accountId, PagingRequestBody paging) {
        return mapper.selectPageByAccount(accountId, paging.offset(), paging.size());
    }

    /** 一个账户的流水总数。 */
    @Override
    public long countByAccount(long accountId) {
        return mapper.countByAccount(accountId);
    }

    /** 空集合不查询；SUM 结果按 BigDecimal 读取。 */
    @Override
    public Map<Long, Totals> sumByAccountIds(List<Long> accountIds) {
        Map<Long, Totals> totals = new HashMap<>();
        if (accountIds.isEmpty()) {
            return totals;
        }
        for (Map<String, Object> row : mapper.sumByAccountIds(accountIds)) {
            totals.put(((Number)row.get("accountId")).longValue(), new Totals((BigDecimal)row.get("debitTotal"),
                (BigDecimal)row.get("creditTotal"), ((Number)row.get("entryCount")).longValue()));
        }
        return totals;
    }
}
