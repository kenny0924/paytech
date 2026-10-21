package com.paytech.reconciliation.domainservice.repository.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.reconciliation.api.enums.ChannelLineType;
import com.paytech.reconciliation.api.enums.LineMatchStatus;
import com.paytech.reconciliation.api.enums.MatchMethod;
import com.paytech.reconciliation.core.dal.dataobject.ChannelStatementLine;
import com.paytech.reconciliation.core.dal.mapper.ChannelStatementLineMapper;
import com.paytech.reconciliation.domain.ChannelLineMatchChange;
import com.paytech.reconciliation.domain.ExternalReference;
import com.paytech.reconciliation.domainservice.repository.ChannelStatementLineRepository;

import lombok.AllArgsConstructor;

/** ChannelStatementLineRepository 的 MyBatis 实现：条件更新先确认行数，成功后才同步传入对象。 */
@Repository
@AllArgsConstructor
public class ChannelStatementLineRepositoryImpl implements ChannelStatementLineRepository {
    private final Clock clock;
    private final ChannelStatementLineMapper mapper;

    /** 批量插入一段明细行（每段 ≤ 100 行）；行号冲突转为并发冲突，调用方先按行号查询实现同号同内容幂等。 */
    @Override
    public void insertBatch(List<ChannelStatementLine> records) {
        if (records.isEmpty()) {
            return;
        }
        try {
            mapper.insertBatch(records);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("渠道账单行号已存在: statementId=" + records.getFirst().getStatementId(), e);
        }
    }

    /** 按主键读取。 */
    @Override
    public ChannelStatementLine findById(Long id) {
        return mapper.selectById(id);
    }

    /** 按账单与行号集合读取（分段追加的同号幂等比较）；空集合不查询。 */
    @Override
    public List<ChannelStatementLine> listByStatementAndLineNos(Long statementId, List<Integer> lineNos) {
        return lineNos.isEmpty() ? List.of() : mapper.selectByStatementAndLineNos(statementId, lineNos);
    }

    /** 读取一张账单的全部行（完成导入校验、逐行匹配、对平门禁）；单张账单至多 20,000 行。 */
    @Override
    public List<ChannelStatementLine> listByStatement(Long statementId) {
        return mapper.selectByStatement(statementId);
    }

    /** 按清算账户 + 外部交易号读取全部账单的行（跨账单查重与同交易号 FEE 行）。 */
    @Override
    public List<ChannelStatementLine> listByReference(Long clearingAccountId, String referenceType,
        String externalTransactionId) {
        return mapper.selectByReference(clearingAccountId, referenceType, externalTransactionId);
    }

    /** 按清算账户 + 一组外部交易号读取全部账单的行；每 1,000 个交易号一次查询，合并后按主键升序。 */
    @Override
    public List<ChannelStatementLine> listByReferences(Long clearingAccountId, Collection<ExternalReference> references) {
        return RepositoryBatches.query(references, part -> mapper.selectByReferences(clearingAccountId, part)).stream()
            .sorted(Comparator.comparing(ChannelStatementLine::getId)).toList();
    }

    /** 读取匹配到某笔平台流水的全部行。 */
    @Override
    public List<ChannelStatementLine> listByPlatformTxn(Long platformTxnId) {
        return mapper.selectByPlatformTxn(platformTxnId);
    }

    /** 读取一个清算账户下接收时间早于给定时刻仍为 UNMATCHED 的行（等待期满任务）。 */
    @Override
    public List<ChannelStatementLine> listUnmatchedCreatedBefore(Long clearingAccountId, Instant createdBefore,
        int limit) {
        return mapper.selectUnmatchedCreatedBefore(clearingAccountId, createdBefore, limit);
    }

    /** 读取 MATCHING 账单中接收时间早于给定时刻仍未匹配的交易行（等待期满任务）。 */
    @Override
    public List<ChannelStatementLine> listWaitExpired(Long clearingAccountId, Instant createdBefore, int limit) {
        return mapper.selectWaitExpired(clearingAccountId, createdBefore, limit);
    }

    /** 按可选条件分页读取（账单（必填）、匹配状态、行类型）；null 条件不过滤。 */
    @Override
    public List<ChannelStatementLine> findPage(Long statementId, LineMatchStatus matchStatus,
        ChannelLineType lineType, long offset, int limit) {
        return mapper.selectPage(statementId, matchStatus, lineType, offset, limit);
    }

    /** 与 findPage 相同条件的总数。 */
    @Override
    public long countPage(Long statementId, LineMatchStatus matchStatus, ChannelLineType lineType) {
        return mapper.countPage(statementId, matchStatus, lineType);
    }

    /** 按版本写入一行的匹配结果；释放匹配时把引用列传 null、状态传 UNMATCHED。 */
    @Override
    public void updateMatch(ChannelStatementLine record, Long platformTxnId, MatchMethod matchMethod,
        Instant matchedTime, BigDecimal actualFee, Long resolutionId, LineMatchStatus matchStatus) {
        Instant now = now();
        if (mapper.updateMatch(record, platformTxnId, matchMethod, matchedTime, actualFee, resolutionId, matchStatus,
            now) != 1) {
            throw new ConcurrentRequestException("渠道账单行已变更，请刷新后重试: " + record.getId());
        }
        record.setPlatformTxnId(platformTxnId);
        record.setMatchMethod(matchMethod);
        record.setMatchedTime(matchedTime);
        record.setActualFee(actualFee);
        record.setResolutionId(resolutionId);
        record.setMatchStatus(matchStatus);
        record.setVersion(record.getVersion() + 1);
        record.setUpdateTime(now);
    }

    /** 按版本批量写入匹配结果：每 1,000 行一条多行条件更新，命中行数不符即并发冲突；全部成功后同步传入对象。 */
    @Override
    public void updateMatches(List<ChannelLineMatchChange> changes) {
        if (changes.isEmpty()) {
            return;
        }
        Instant now = now();
        List<ChannelLineMatchChange> ordered = changes.stream().sorted(Comparator.comparing(change -> change.record().getId())).toList();
        for (List<ChannelLineMatchChange> part : RepositoryBatches.partition(ordered)) {
            if (mapper.updateMatchBatch(part, now) != part.size()) {
                throw new ConcurrentRequestException("渠道账单行已变更，请刷新后重试: " + part.getFirst().record().getId()
                    + " 起 " + part.size() + " 行");
            }
        }
        changes.forEach(change -> sync(change, now));
    }

    /** 条件更新成功后同步对象。 */
    static void sync(ChannelLineMatchChange change, Instant now) {
        ChannelStatementLine record = change.record();
        record.setPlatformTxnId(change.platformTxnId());
        record.setMatchMethod(change.matchMethod());
        record.setMatchedTime(change.matchedTime());
        record.setActualFee(change.actualFee());
        record.setResolutionId(change.resolutionId());
        record.setMatchStatus(change.matchStatus());
        record.setVersion(record.getVersion() + 1);
        record.setUpdateTime(now);
    }

    /** 写入时间：UTC 微秒，与 DATETIME(6) 一致。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
