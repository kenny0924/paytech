package com.paytech.reconciliation.core.dal.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.paytech.reconciliation.api.enums.ChannelLineType;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.MatchMethod;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.domain.ChannelLineMatchChange;
import com.paytech.reconciliation.domain.ChannelLineSearch;
import com.paytech.reconciliation.domain.ExternalReference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** t_channel_statement_line：渠道账单行的 MyBatis 映射；SQL 位于 mapper/reconciliation/ChannelStatementLineMapper.xml。 */
@Mapper
public interface ChannelStatementLineMapper {
    /** 批量插入一段明细行。 */
    int insertBatch(@Param("records") List<ChannelStatementLine> records);

    /** 按主键读取，不存在返回 null。 */
    ChannelStatementLine selectById(@Param("id") Long id);

    /** 按账单与行号集合读取。 */
    List<ChannelStatementLine> selectByStatementAndLineNos(@Param("statementId") Long statementId,
        @Param("lineNos") List<Integer> lineNos);

    /** 读取一张账单的全部行。 */
    List<ChannelStatementLine> selectByStatement(@Param("statementId") Long statementId);

    /** 按清算账户 + 外部交易号读取全部账单的行。 */
    List<ChannelStatementLine> selectByReference(@Param("clearingAccountId") Long clearingAccountId,
        @Param("referenceType") String referenceType, @Param("externalTransactionId") String externalTransactionId);

    /** 按清算账户 + 一段外部交易号（至多 1,000 个）读取全部账单的行。 */
    List<ChannelStatementLine> selectByReferences(@Param("clearingAccountId") Long clearingAccountId,
        @Param("references") List<ExternalReference> references);

    /** 读取匹配到某笔平台流水的行。 */
    List<ChannelStatementLine> selectByPlatformTxn(@Param("platformTxnId") Long platformTxnId);

    /** 读取接收时间早于给定时刻仍未匹配的行。 */
    List<ChannelStatementLine> selectUnmatchedCreatedBefore(@Param("clearingAccountId") Long clearingAccountId,
        @Param("createdBefore") Instant createdBefore, @Param("limit") int limit);

    /** 读取 MATCHING 账单中接收时间早于给定时刻仍未匹配的交易行（PAYMENT、REFUND、CHARGEBACK）。 */
    List<ChannelStatementLine> selectWaitExpired(@Param("clearingAccountId") Long clearingAccountId,
        @Param("createdBefore") Instant createdBefore, @Param("limit") int limit);

    /** 按可选条件分页读取，排序稳定。 */
    List<ChannelStatementLine> selectPage(@Param("statementId") Long statementId,
        @Param("matchStatus") LineMatchStatus matchStatus, @Param("lineType") ChannelLineType lineType,
        @Param("offset") long offset, @Param("limit") int limit);

    /** 与 selectPage 相同条件的总数。 */
    long countPage(@Param("statementId") Long statementId, @Param("matchStatus") LineMatchStatus matchStatus,
        @Param("lineType") ChannelLineType lineType);

    /** 跨账单按条件分页读取（连接所属账单按账单状态过滤），交易日期降序、主键降序。 */
    List<ChannelStatementLine> selectSearchPage(@Param("c") ChannelLineSearch criteria, @Param("offset") long offset,
        @Param("limit") int limit);

    /** 与 selectSearchPage 相同条件的总数。 */
    long countSearch(@Param("c") ChannelLineSearch criteria);

    /** 按版本写入匹配结果（匹配、费用、归类处置与状态）。 */
    int updateMatch(@Param("record") ChannelStatementLine record, @Param("platformTxnId") Long platformTxnId,
        @Param("matchMethod") MatchMethod matchMethod, @Param("matchedTime") Instant matchedTime,
        @Param("actualFee") BigDecimal actualFee, @Param("resolutionId") Long resolutionId,
        @Param("matchStatus") LineMatchStatus matchStatus, @Param("now") Instant now);

    /** 按版本多行写入匹配结果（一段至多 1,000 行），返回命中行数。 */
    int updateMatchBatch(@Param("changes") List<ChannelLineMatchChange> changes,
        @Param("now") Instant now);
}
