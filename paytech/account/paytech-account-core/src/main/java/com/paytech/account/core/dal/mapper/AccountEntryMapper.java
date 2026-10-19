package com.paytech.account.core.dal.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.account.core.dal.dataobject.AccountEntry;

/** t_account_entry：不可变账户流水。 */
@Mapper
public interface AccountEntryMapper {

    /** 批量插入流水。 */
    int insertAll(@Param("entries") List<AccountEntry> entries);

    /** 读取一组批次的全部流水，按批次、行号排序。 */
    List<AccountEntry> selectByPostingIds(@Param("postingIds") List<Long> postingIds);

    /** 读取冲回给定流水的冲回行。 */
    List<AccountEntry> selectByReversedIds(@Param("entryIds") List<Long> entryIds);

    /** 分页读取一个账户的流水，按所属批次序号、行号升序。 */
    List<AccountEntry> selectPageByAccount(@Param("accountId") long accountId, @Param("offset") int offset,
        @Param("limit") int limit);

    /** 一个账户的流水总数。 */
    long countByAccount(@Param("accountId") long accountId);

    /** 按账户汇总借贷合计与行数；每行含 accountId、debitTotal、creditTotal 与 entryCount。 */
    List<Map<String, Object>> sumByAccountIds(@Param("accountIds") List<Long> accountIds);
}
