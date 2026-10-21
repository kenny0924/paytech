package com.paytech.reconciliation.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import com.paytech.reconciliation.api.enums.AccountPurpose;
import com.paytech.reconciliation.api.enums.CashAccountPendingAction;
import com.paytech.reconciliation.api.enums.CashAccountStatus;
import com.paytech.reconciliation.api.enums.ReconEntityType;
import com.paytech.reconciliation.core.dal.dataobject.CashAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** t_cash_account：平台银行账户的 MyBatis 映射；SQL 位于 mapper/reconciliation/CashAccountMapper.xml。 */
@Mapper
public interface CashAccountMapper {
    /** 插入一行；主键由应用分配。 */
    int insert(CashAccount record);

    /** 按主键读取，不存在返回 null。 */
    CashAccount selectById(@Param("id") Long id);

    /** 按主键集合读取，按主键升序。 */
    List<CashAccount> selectByIds(@Param("ids") List<Long> ids);

    /** 按主体 + 币种 + 用途 + 状态读取。 */
    List<CashAccount> selectByScope(@Param("entityType") ReconEntityType entityType,
        @Param("entityId") String entityId, @Param("currency") String currency,
        @Param("accountPurpose") AccountPurpose accountPurpose, @Param("status") CashAccountStatus status);

    /** 按可选条件分页读取，排序稳定。 */
    List<CashAccount> selectPage(@Param("entityType") ReconEntityType entityType, @Param("entityId") String entityId,
        @Param("currency") String currency, @Param("accountPurpose") AccountPurpose accountPurpose,
        @Param("status") CashAccountStatus status, @Param("offset") long offset, @Param("limit") int limit);

    /** 与 selectPage 相同条件的总数。 */
    long countPage(@Param("entityType") ReconEntityType entityType, @Param("entityId") String entityId,
        @Param("currency") String currency, @Param("accountPurpose") AccountPurpose accountPurpose,
        @Param("status") CashAccountStatus status);

    /** 按版本与原状态更新复核相关列。 */
    int updateReview(@Param("record") CashAccount record, @Param("status") CashAccountStatus status,
        @Param("pendingAction") CashAccountPendingAction pendingAction, @Param("contentHash") String contentHash,
        @Param("makerId") Long makerId, @Param("checkerId") Long checkerId, @Param("now") Instant now);
}
