package com.paytech.ledger.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.account.api.enums.OwnerType;
import com.paytech.ledger.core.dal.dataobject.LedgerAccount;
import com.paytech.ledger.domain.AccountQuery;
import com.paytech.ledger.domain.view.AccountJournalTotalsView;
import com.paytech.ledger.domain.view.AccountView;

/** t_ledger_account：会计科目账户身份持久化；余额值只由本库 Journal 聚合得出。 */
@Mapper
public interface LedgerAccountMapper {

    /** 插入一行。 */
    int insert(LedgerAccount record);

    /** 按主键读取。 */
    LedgerAccount selectById(@Param("id") long id);

    /** 按主键升序读取一组账户，不加锁。 */
    List<LedgerAccount> selectByIds(@Param("ids") List<Long> ids);

    /** 按账户身份唯一键读取，不存在返回空。 */
    LedgerAccount selectByIdentity(@Param("ledgerBookId") long ledgerBookId, @Param("chartAccountId") long chartAccountId,
        @Param("ownerType") OwnerType ownerType, @Param("ownerId") String ownerId,
        @Param("balanceBucket") BalanceBucket balanceBucket);

    /** 按过滤条件分页读取账簿内账户、科目代码与分录聚合余额，按科目、核算对象、余额桶与主键排序。 */
    List<AccountView> selectPage(@Param("query") AccountQuery query, @Param("size") int size,
        @Param("offset") int offset);

    /** 统计符合过滤条件的账户数量。 */
    long countPage(@Param("query") AccountQuery query);

    /** 读取账簿内指定核算对象类型的全部账户与分录聚合余额，按科目、核算对象、余额桶与主键排序。 */
    List<AccountView> selectOwnerTotals(@Param("ledgerBookId") long ledgerBookId,
        @Param("ownerTypes") Collection<OwnerType> ownerTypes);

    /** 统计一组账簿内指定核算对象类型、分录聚合净额非零的账户数。 */
    long countNonZeroByOwnerTypes(@Param("bookIds") List<Long> bookIds,
        @Param("ownerTypes") Collection<OwnerType> ownerTypes);

    /** 从账户全部分录行聚合借贷累计与最后分录序号。 */
    AccountJournalTotalsView selectJournalTotals(@Param("accountId") long accountId);
}
