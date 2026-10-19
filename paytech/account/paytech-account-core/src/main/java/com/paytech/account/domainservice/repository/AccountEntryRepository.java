package com.paytech.account.domainservice.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.paytech.account.core.dal.dataobject.AccountEntry;
import com.paytech.foundation.web.paging.PagingRequestBody;

/** t_account_entry：不可变账户流水读写。 */
public interface AccountEntryRepository {

    /** 批量插入流水；空集合不写。 */
    void insertAll(List<AccountEntry> entries);

    /** 读取一组批次的全部流水，按批次、行号排序；空集合返回空列表。 */
    List<AccountEntry> findByPostingIds(List<Long> postingIds);

    /** 读取冲回给定流水的冲回行；空集合返回空列表。 */
    List<AccountEntry> findReversalsOf(List<Long> entryIds);

    /** 分页读取一个账户的流水，按所属批次序号、行号升序。 */
    List<AccountEntry> findPageByAccount(long accountId, PagingRequestBody paging);

    /** 一个账户的流水总数。 */
    long countByAccount(long accountId);

    /**
     * 按账户汇总流水
     *
     * @param accountIds 账户主键
     * @return 账户主键 → 借贷合计与行数；没有流水的账户不出现
     */
    Map<Long, Totals> sumByAccountIds(List<Long> accountIds);

    /**
     * 一个账户的流水合计
     *
     * @param debitTotal 借方合计
     * @param creditTotal 贷方合计
     * @param entryCount 行数
     */
    record Totals(BigDecimal debitTotal, BigDecimal creditTotal, long entryCount) {
    }
}
