package com.paytech.reconciliation.core.dal.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.paytech.reconciliation.api.enums.ClearingDirection;
import com.paytech.reconciliation.api.enums.PlatformTxnStatus;
import com.paytech.reconciliation.core.dal.dataobject.PlatformTxn;
import com.paytech.reconciliation.domain.ExternalReference;
import com.paytech.reconciliation.domain.PlatformTxnProgressChange;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** t_platform_txn：平台流水的 MyBatis 映射；SQL 位于 mapper/reconciliation/PlatformTxnMapper.xml。 */
@Mapper
public interface PlatformTxnMapper {
    /** 插入一行；主键由应用分配。 */
    int insert(PlatformTxn record);

    /** 按主键读取，不存在返回 null。 */
    PlatformTxn selectById(@Param("id") Long id);

    /** 按主键集合读取，按主键升序。 */
    List<PlatformTxn> selectByIds(@Param("ids") List<Long> ids);

    /** 按来源 billing 事实读取（消费幂等键）。 */
    PlatformTxn selectByBillingFactId(@Param("billingFactId") Long billingFactId);

    /** 按清算账户 + 外部交易号读取（应收与应付各至多一条）。 */
    List<PlatformTxn> selectByReference(@Param("clearingAccountId") Long clearingAccountId,
        @Param("referenceType") String referenceType, @Param("externalTransactionId") String externalTransactionId);

    /** 按可选条件分页读取，排序稳定。 */
    List<PlatformTxn> selectPage(@Param("clearingAccountId") Long clearingAccountId,
        @Param("statementId") Long statementId,
        @Param("status") PlatformTxnStatus status, @Param("direction") ClearingDirection direction,
        @Param("externalTransactionId") String externalTransactionId,
        @Param("businessTimeFrom") Instant businessTimeFrom, @Param("businessTimeTo") Instant businessTimeTo,
        @Param("offset") long offset, @Param("limit") int limit);

    /** 与 selectPage 相同条件的总数。 */
    long countPage(@Param("clearingAccountId") Long clearingAccountId, @Param("statementId") Long statementId,
        @Param("status") PlatformTxnStatus status,
        @Param("direction") ClearingDirection direction, @Param("externalTransactionId") String externalTransactionId,
        @Param("businessTimeFrom") Instant businessTimeFrom, @Param("businessTimeTo") Instant businessTimeTo);

    /** 按版本更新对账进度金额与状态。 */
    int updateProgress(@Param("record") PlatformTxn record, @Param("matchedAmount") BigDecimal matchedAmount,
        @Param("actualFee") BigDecimal actualFee, @Param("settledAmount") BigDecimal settledAmount,
        @Param("status") PlatformTxnStatus status, @Param("now") Instant now);

    /** 按清算账户 + 一段外部交易号（至多 1,000 个）读取。 */
    List<PlatformTxn> selectByReferences(@Param("clearingAccountId") Long clearingAccountId,
        @Param("references") List<ExternalReference> references);

    /** 按版本多行更新对账进度（一段至多 1,000 笔），返回命中行数。 */
    int updateProgressBatch(@Param("changes") List<PlatformTxnProgressChange> changes, @Param("now") Instant now);
}
