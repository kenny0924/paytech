package com.paytech.reconciliation.domainservice.repository.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.SettlementDirection;
import com.paytech.reconciliation.core.dal.dataobject.BankStatementLine;
import com.paytech.reconciliation.domain.view.ReconSummaryView;
import com.paytech.reconciliation.core.dal.mapper.BankStatementLineMapper;
import com.paytech.reconciliation.domainservice.repository.BankStatementLineRepository;

import lombok.AllArgsConstructor;

/** BankStatementLineRepository 的 MyBatis 实现：条件更新先确认行数，成功后才同步传入对象。 */
@Repository
@AllArgsConstructor
public class BankStatementLineRepositoryImpl implements BankStatementLineRepository {
    private final Clock clock;
    private final BankStatementLineMapper mapper;

    /** 批量插入一段流水；调用方先按银行交易号去重，库内唯一键冲突转为并发冲突。 */
    @Override
    public void insertBatch(List<BankStatementLine> records) {
        if (records.isEmpty()) {
            return;
        }
        try {
            mapper.insertBatch(records);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("银行交易号已入库: cashAccountId=" + records.getFirst().getCashAccountId(), e);
        }
    }

    /** 按主键读取。 */
    @Override
    public BankStatementLine findById(Long id) {
        return mapper.selectById(id);
    }

    /** 按主键集合读取；空集合不查询。 */
    @Override
    public List<BankStatementLine> findByIds(List<Long> ids) {
        return ids.isEmpty() ? List.of() : mapper.selectByIds(ids);
    }

    /** 按银行账户 + 银行交易号集合读取（导入去重）；空集合不查询。 */
    @Override
    public List<BankStatementLine> listByTransactionRefs(Long cashAccountId, List<String> refs) {
        return refs.isEmpty() ? List.of() : mapper.selectByTransactionRefs(cashAccountId, refs);
    }

    /** 读取一张对账单的全部流水（完成导入校验、建议任务）。 */
    @Override
    public List<BankStatementLine> listByStatement(Long statementId) {
        return mapper.selectByStatement(statementId);
    }

    /** 读取银行账户下 UNMATCHED / PARTIALLY_MATCHED 的流水（匹配建议）。 */
    @Override
    public List<BankStatementLine> listOpen(Long cashAccountId, SettlementDirection direction) {
        return mapper.selectOpen(cashAccountId, direction);
    }

    /** 按可选条件分页读取（对账单、银行账户、匹配状态、方向、起息日区间）；null 条件不过滤。 */
    @Override
    public List<BankStatementLine> findPage(Long statementId, Long cashAccountId, LineMatchStatus matchStatus,
        SettlementDirection direction, LocalDate valueDateFrom, LocalDate valueDateTo, long offset, int limit) {
        return mapper.selectPage(statementId, cashAccountId, matchStatus, direction, valueDateFrom, valueDateTo,
            offset, limit);
    }

    /** 与 findPage 相同条件的总数。 */
    @Override
    public long countPage(Long statementId, Long cashAccountId, LineMatchStatus matchStatus,
        SettlementDirection direction, LocalDate valueDateFrom, LocalDate valueDateTo) {
        return mapper.countPage(statementId, cashAccountId, matchStatus, direction, valueDateFrom, valueDateTo);
    }

    /** 按版本更新已确认匹配的带符号合计与匹配状态。 */
    @Override
    public void updateMatch(BankStatementLine record, BigDecimal matchedAmount, LineMatchStatus matchStatus) {
        Instant now = now();
        if (mapper.updateMatch(record, matchedAmount, matchStatus, now) != 1) {
            throw new ConcurrentRequestException("银行流水已变更，请刷新后重试: " + record.getId());
        }
        record.setMatchedAmount(matchedAmount);
        record.setMatchStatus(matchStatus);
        record.setVersion(record.getVersion() + 1);
        record.setUpdateTime(now);
    }

    /** 写入时间：UTC 微秒，与 DATETIME(6) 一致。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 按对账单 + 行号集合读取；空集合不查询。 */
    @Override
    public List<BankStatementLine> listByStatementAndLineNos(Long statementId, List<Integer> lineNos) {
        return lineNos.isEmpty() ? List.of() : mapper.selectByStatementAndLineNos(statementId, lineNos);
    }

    /** 删除一张对账单的全部流水。 */
    @Override
    public void deleteByStatement(Long statementId) {
        mapper.deleteByStatement(statementId);
    }

    /** 按方向汇总未完成匹配的流水。 */
    @Override
    public List<ReconSummaryView.UnmatchedLineTotal> sumUnmatched(String entityNo, String currency) {
        return mapper.sumUnmatched(entityNo, currency);
    }
}
