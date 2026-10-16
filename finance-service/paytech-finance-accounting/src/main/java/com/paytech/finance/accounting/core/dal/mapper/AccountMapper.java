package com.paytech.finance.accounting.core.dal.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.finance.accounting.core.dal.dataobject.Account;
import com.paytech.finance.accounting.core.enums.BalanceBucket;
import com.paytech.finance.accounting.core.enums.OwnerType;
import com.paytech.finance.accounting.domain.AccountQuery;
import com.paytech.finance.accounting.domain.view.AccountJournalTotalsView;
import com.paytech.finance.accounting.domain.view.AccountProjectionCheckView;
import com.paytech.finance.accounting.domain.view.AccountView;
import com.paytech.finance.accounting.domain.view.OwnerAccountBalanceView;

/** t_account：统一记账账户持久化。 */
@Mapper
public interface AccountMapper {

    /** 插入一行。 */
    int insert(Account record);

    /** 按主键读取。 */
    Account selectById(@Param("id") long id);

    /** 按主键升序以排他锁读取一组账户，作为过账的账户锁顺序。 */
    List<Account> selectByIdsForUpdate(@Param("ids") List<Long> ids);

    /** 读取一组账户，用于提交前校验。 */
    List<Account> selectByIds(@Param("ids") List<Long> ids);

    /**
     * 以余额版本 CAS 应用一条分录后的余额投影。
     *
     * @return 受影响行数
     */
    int applyPosting(@Param("account") Account account, @Param("debitTotal") BigDecimal debitTotal,
        @Param("creditTotal") BigDecimal creditTotal, @Param("netBalance") BigDecimal netBalance,
        @Param("journalSequence") long journalSequence, @Param("now") Instant now);

    /** 读取 Owner 在一个账簿内的全部账户及其科目，用于分类余额查询。 */
    List<OwnerAccountBalanceView> selectOwnerBalances(@Param("ledgerBookId") long ledgerBookId,
        @Param("ownerType") OwnerType ownerType, @Param("ownerId") String ownerId);

    /** 按过滤条件分页读取账簿内账户及其科目代码，按科目、核算对象、余额桶与主键排序。 */
    List<AccountView> selectPage(@Param("query") AccountQuery query, @Param("size") int size,
        @Param("offset") int offset);

    /** 统计符合过滤条件的账户数量。 */
    long countPage(@Param("query") AccountQuery query);

    /** 读取账簿内全部账户的余额投影与水位内全量分录借贷合计。 */
    List<AccountProjectionCheckView> selectProjectionChecks(@Param("ledgerBookId") long ledgerBookId,
        @Param("asOfSequence") long asOfSequence);

    /** 按账户身份唯一键读取，不存在返回空。 */
    Account selectByIdentity(@Param("ledgerBookId") long ledgerBookId, @Param("chartAccountId") long chartAccountId,
        @Param("ownerType") OwnerType ownerType, @Param("ownerId") String ownerId,
        @Param("balanceBucket") BalanceBucket balanceBucket);

    /** 以排他锁读取一个账户；必须在事务内调用。 */
    Account selectByIdForUpdate(@Param("id") long id);

    /** 从账户全部分录行重算借贷累计与最后分录序号。 */
    AccountJournalTotalsView selectJournalTotals(@Param("accountId") long accountId);

    /**
     * 以余额版本 CAS 写入重建后的余额投影，不要求序号单调。
     *
     * @return 受影响行数
     */
    int rebuildProjection(@Param("account") Account account, @Param("debitTotal") BigDecimal debitTotal,
        @Param("creditTotal") BigDecimal creditTotal, @Param("netBalance") BigDecimal netBalance,
        @Param("lastJournalSequence") long lastJournalSequence, @Param("now") Instant now);
}
