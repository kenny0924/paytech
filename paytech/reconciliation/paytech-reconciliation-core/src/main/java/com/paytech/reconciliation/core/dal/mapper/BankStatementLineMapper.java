package com.paytech.reconciliation.core.dal.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.domain.view.ReconSummaryView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** t_bank_statement_line：银行流水的 MyBatis 映射；SQL 位于 mapper/reconciliation/BankStatementLineMapper.xml。 */
@Mapper
public interface BankStatementLineMapper {
    /** 批量插入一段流水。 */
    int insertBatch(@Param("records") List<BankStatementLine> records);

    /** 按主键读取，不存在返回 null。 */
    BankStatementLine selectById(@Param("id") Long id);

    /** 按主键集合读取，按主键升序。 */
    List<BankStatementLine> selectByIds(@Param("ids") List<Long> ids);

    /** 按银行账户 + 银行交易号集合读取。 */
    List<BankStatementLine> selectByTransactionRefs(@Param("cashAccountId") Long cashAccountId,
        @Param("refs") List<String> refs);

    /** 读取一张对账单的全部流水。 */
    List<BankStatementLine> selectByStatement(@Param("statementId") Long statementId);

    /** 读取银行账户下未完成匹配的流水。 */
    List<BankStatementLine> selectOpen(@Param("cashAccountId") Long cashAccountId,
        @Param("direction") SettlementDirection direction);

    /** 按可选条件分页读取，排序稳定。 */
    List<BankStatementLine> selectPage(@Param("statementId") Long statementId,
        @Param("cashAccountId") Long cashAccountId, @Param("matchStatus") LineMatchStatus matchStatus,
        @Param("direction") SettlementDirection direction, @Param("valueDateFrom") LocalDate valueDateFrom,
        @Param("valueDateTo") LocalDate valueDateTo, @Param("offset") long offset, @Param("limit") int limit);

    /** 与 selectPage 相同条件的总数。 */
    long countPage(@Param("statementId") Long statementId, @Param("cashAccountId") Long cashAccountId,
        @Param("matchStatus") LineMatchStatus matchStatus, @Param("direction") SettlementDirection direction,
        @Param("valueDateFrom") LocalDate valueDateFrom, @Param("valueDateTo") LocalDate valueDateTo);

    /** 按版本更新已确认匹配金额与匹配状态。 */
    int updateMatch(@Param("record") BankStatementLine record, @Param("matchedAmount") BigDecimal matchedAmount,
        @Param("matchStatus") LineMatchStatus matchStatus, @Param("now") Instant now);

    /** 按对账单 + 行号集合读取（分段追加的行号幂等）。 */
    List<BankStatementLine> selectByStatementAndLineNos(@Param("statementId") Long statementId,
        @Param("lineNos") List<Integer> lineNos);

    /** 删除一张对账单的全部流水（完成导入被拒时释放银行交易号）。 */
    int deleteByStatement(@Param("statementId") Long statementId);

    /** 按方向汇总已导入对账单中未完成匹配的流水（在途汇总）。 */
    List<ReconSummaryView.UnmatchedLineTotal> sumUnmatched(@Param("entityNo") String entityNo,
        @Param("currency") String currency);
}
