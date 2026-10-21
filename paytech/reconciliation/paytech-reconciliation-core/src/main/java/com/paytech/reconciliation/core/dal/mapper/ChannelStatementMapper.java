package com.paytech.reconciliation.core.dal.mapper;

import com.paytech.reconciliation.domain.view.ReconSummaryView;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.paytech.reconciliation.api.enums.ChannelStatementStatus;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** t_channel_statement：渠道账单的 MyBatis 映射；SQL 位于 mapper/reconciliation/ChannelStatementMapper.xml。 */
@Mapper
public interface ChannelStatementMapper {
    /** 插入一行；主键由应用分配。 */
    int insert(ChannelStatement record);

    /** 按主键读取，不存在返回 null。 */
    ChannelStatement selectById(@Param("id") Long id);

    /** 按主键集合读取，按主键升序。 */
    List<ChannelStatement> selectByIds(@Param("ids") List<Long> ids);

    /** 读取同一批次号的全部导入版本。 */
    List<ChannelStatement> selectByBatch(@Param("clearingAccountId") Long clearingAccountId,
        @Param("channelBatchRef") String channelBatchRef);

    /** 按可选条件分页读取，排序稳定。 */
    List<ChannelStatement> selectPage(@Param("clearingAccountId") Long clearingAccountId,
        @Param("channelBatchRef") String channelBatchRef, @Param("status") ChannelStatementStatus status,
        @Param("settlementDateFrom") LocalDate settlementDateFrom,
        @Param("settlementDateTo") LocalDate settlementDateTo, @Param("offset") long offset, @Param("limit") int limit);

    /** 与 selectPage 相同条件的总数。 */
    long countPage(@Param("clearingAccountId") Long clearingAccountId,
        @Param("channelBatchRef") String channelBatchRef, @Param("status") ChannelStatementStatus status,
        @Param("settlementDateFrom") LocalDate settlementDateFrom,
        @Param("settlementDateTo") LocalDate settlementDateTo);

    /** 接收中按版本更新已接收行数。 */
    int updateImportedLineCount(@Param("record") ChannelStatement record,
        @Param("importedLineCount") Integer importedLineCount, @Param("now") Instant now);

    /** 按版本与原状态推进状态，同时写拒绝原因与对平时间。 */
    int changeStatus(@Param("record") ChannelStatement record, @Param("status") ChannelStatementStatus status,
        @Param("rejectReason") String rejectReason, @Param("reconciledTime") Instant reconciledTime,
        @Param("now") Instant now);

    /** 按版本更新到账确认金额与状态。 */
    int updateCashConfirmed(@Param("record") ChannelStatement record,
        @Param("cashConfirmedAmount") BigDecimal cashConfirmedAmount, @Param("status") ChannelStatementStatus status,
        @Param("now") Instant now);

    /** 按状态汇总在途渠道账单（MATCHING、RECONCILED）。 */
    List<ReconSummaryView.StatementTotal> sumInTransit(@Param("entityNo") String entityNo,
        @Param("currency") String currency);
}
