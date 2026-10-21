package com.paytech.reconciliation.domainservice.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.domain.view.ReconSummaryView;

/** t_bank_statement_line（银行流水）仓储：一张表一个仓储，只做本表读写与行数检查，不开事务。 */
public interface BankStatementLineRepository {
    /**
     * 批量插入一段流水；调用方先按银行交易号去重，库内唯一键冲突转为并发冲突。
     * 
     * @param records 同一对账单的新流水
     */
    void insertBatch(List<BankStatementLine> records);

    /**
     * 按主键读取。
     * 
     * @param id 主键
     * @return 记录；不存在为 null
     */
    BankStatementLine findById(Long id);

    /**
     * 按主键集合读取；空集合不查询。
     * 
     * @param ids 主键集合
     * @return 存在的记录，按主键升序
     */
    List<BankStatementLine> findByIds(List<Long> ids);

    /**
     * 按银行账户 + 银行交易号集合读取（导入去重）；空集合不查询。
     * 
     * @param cashAccountId 银行账户主键
     * @param refs 银行交易号
     * @return 已入库的流水
     */
    List<BankStatementLine> listByTransactionRefs(Long cashAccountId, List<String> refs);

    /**
     * 读取一张对账单的全部流水（完成导入校验、建议任务）。
     * 
     * @param statementId 对账单主键
     * @return 按行号升序
     */
    List<BankStatementLine> listByStatement(Long statementId);

    /**
     * 读取银行账户下 UNMATCHED / PARTIALLY_MATCHED 的流水（匹配建议）。
     * 
     * @param cashAccountId 银行账户主键
     * @param direction 可选方向
     * @return 按起息日、主键升序
     */
    List<BankStatementLine> listOpen(Long cashAccountId, SettlementDirection direction);

    /**
     * 按可选条件分页读取（对账单、银行账户、匹配状态、方向、起息日区间）；null 条件不过滤。
     * 
     * @param statementId 可选条件
     * @param cashAccountId 可选条件
     * @param matchStatus 可选条件
     * @param direction 可选条件
     * @param valueDateFrom 可选条件
     * @param valueDateTo 可选条件
     * @param offset 已校验分页请求的偏移量
     * @param limit 每页条数
     * @return 当前页记录
     */
    List<BankStatementLine> findPage(Long statementId, Long cashAccountId, LineMatchStatus matchStatus,
        SettlementDirection direction, LocalDate valueDateFrom, LocalDate valueDateTo, long offset, int limit);

    /**
     * 与 findPage 相同条件的总数。
     * 
     * @param statementId 可选条件
     * @param cashAccountId 可选条件
     * @param matchStatus 可选条件
     * @param direction 可选条件
     * @param valueDateFrom 可选条件
     * @param valueDateTo 可选条件
     * @return 总数
     */
    long countPage(Long statementId, Long cashAccountId, LineMatchStatus matchStatus, SettlementDirection direction,
        LocalDate valueDateFrom, LocalDate valueDateTo);

    /**
     * 按版本更新已确认匹配的带符号合计与匹配状态。
     * 
     * @param record 已读取的流水
     * @param matchedAmount 新的已匹配金额
     * @param matchStatus 新的匹配状态
     */
    void updateMatch(BankStatementLine record, BigDecimal matchedAmount, LineMatchStatus matchStatus);

    /**
     * 按对账单 + 行号集合读取（分段追加的行号幂等）；空集合不查询。
     *
     * @param statementId 对账单主键
     * @param lineNos 行号
     * @return 已接收的行，按行号升序
     */
    List<BankStatementLine> listByStatementAndLineNos(Long statementId, List<Integer> lineNos);

    /**
     * 删除一张对账单的全部流水：完成导入被拒时释放这些银行交易号，避免挡住更正后的重新导入。
     *
     * @param statementId 对账单主键
     */
    void deleteByStatement(Long statementId);

    /**
     * 按方向汇总已导入对账单中 UNMATCHED / PARTIALLY_MATCHED / EXCEPTION 的流水（在途汇总）。
     *
     * @param entityNo 可选经营主体编号；为空汇总全部银行账户
     * @param currency 币种
     * @return 每个方向一行
     */
    List<ReconSummaryView.UnmatchedLineTotal> sumUnmatched(String entityNo, String currency);
}
