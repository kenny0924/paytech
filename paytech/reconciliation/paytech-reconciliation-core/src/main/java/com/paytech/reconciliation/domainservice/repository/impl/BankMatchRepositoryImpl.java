package com.paytech.reconciliation.domainservice.repository.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.reconciliation.api.enums.BankMatchStatus;
import com.paytech.reconciliation.core.dal.dataobject.BankMatch;
import com.paytech.reconciliation.core.dal.mapper.BankMatchMapper;
import com.paytech.reconciliation.domainservice.repository.BankMatchRepository;

import lombok.AllArgsConstructor;

/** BankMatchRepository 的 MyBatis 实现：条件更新先确认行数，成功后才同步传入对象。 */
@Repository
@AllArgsConstructor
public class BankMatchRepositoryImpl implements BankMatchRepository {
    private final Clock clock;
    private final BankMatchMapper mapper;

    /** 插入一条建议（PROPOSED）或确认的匹配（CONFIRMED）；流水级合计不超额由调用方在锁内校验。 */
    @Override
    public void insert(BankMatch record) {
        mapper.insert(record);
    }

    /** 按主键读取。 */
    @Override
    public BankMatch findById(Long id) {
        return mapper.selectById(id);
    }

    /** 读取一条银行流水的匹配，可按状态过滤。 */
    @Override
    public List<BankMatch> listByLine(Long bankStatementLineId, BankMatchStatus status) {
        return mapper.selectByLine(bankStatementLineId, status);
    }

    /** 读取一条预期银行流水的匹配，可按状态过滤。 */
    @Override
    public List<BankMatch> listByExpected(Long expectedMovementId, BankMatchStatus status) {
        return mapper.selectByExpected(expectedMovementId, status);
    }

    /** 按可选条件分页读取（银行流水、预期银行流水、状态）；null 条件不过滤。 */
    @Override
    public List<BankMatch> findPage(Long bankStatementLineId, Long expectedMovementId, BankMatchStatus status,
        long offset, int limit) {
        return mapper.selectPage(bankStatementLineId, expectedMovementId, status, offset, limit);
    }

    /** 与 findPage 相同条件的总数。 */
    @Override
    public long countPage(Long bankStatementLineId, Long expectedMovementId, BankMatchStatus status) {
        return mapper.countPage(bankStatementLineId, expectedMovementId, status);
    }

    /** 按版本把 PROPOSED 建议转为 CONFIRMED 或 REJECTED。 */
    @Override
    public void decide(BankMatch record, BankMatchStatus status, Long operatorId, Instant decisionTime) {
        Instant now = now();
        if (mapper.decide(record, status, operatorId, decisionTime, now) != 1) {
            throw new ConcurrentRequestException("银行匹配已变更，请刷新后重试: " + record.getId());
        }
        record.setStatus(status);
        record.setOperatorId(operatorId);
        record.setDecisionTime(decisionTime);
        record.setVersion(record.getVersion() + 1);
        record.setUpdateTime(now);
    }

    /** 写入时间：UTC 微秒，与 DATETIME(6) 一致。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
