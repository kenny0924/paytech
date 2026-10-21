package com.paytech.reconciliation.domainservice.repository.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.reconciliation.api.enums.BankStatementStatus;
import com.paytech.reconciliation.core.dal.dataobject.BankStatement;
import com.paytech.reconciliation.core.dal.mapper.BankStatementMapper;
import com.paytech.reconciliation.domainservice.repository.BankStatementRepository;

import lombok.AllArgsConstructor;

/** BankStatementRepository 的 MyBatis 实现：条件更新先确认行数，成功后才同步传入对象。 */
@Repository
@AllArgsConstructor
public class BankStatementRepositoryImpl implements BankStatementRepository {
    private final Clock clock;
    private final BankStatementMapper mapper;

    /** 插入新记录；业务唯一键冲突转为并发冲突（409），调用方应先按业务键查询。 */
    @Override
    public void insert(BankStatement record) {
        try {
            mapper.insert(record);
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException("银行对账单编号已存在: " + record.getStatementRef(), e);
        }
    }

    /** 按主键读取。 */
    @Override
    public BankStatement findById(Long id) {
        return mapper.selectById(id);
    }

    /** 按银行账户 + 对账单编号读取。 */
    @Override
    public BankStatement findByRef(Long cashAccountId, String statementRef) {
        return mapper.selectByRef(cashAccountId, statementRef);
    }

    /** 读取同一银行账户期间结束日最晚的已导入对账单（连续性检查）。 */
    @Override
    public BankStatement findLatestImported(Long cashAccountId) {
        return mapper.selectLatestImported(cashAccountId);
    }

    /** 按可选条件分页读取（银行账户、状态）；null 条件不过滤。 */
    @Override
    public List<BankStatement> findPage(Long cashAccountId, BankStatementStatus status, long offset, int limit) {
        return mapper.selectPage(cashAccountId, status, offset, limit);
    }

    /** 与 findPage 相同条件的总数。 */
    @Override
    public long countPage(Long cashAccountId, BankStatementStatus status) {
        return mapper.countPage(cashAccountId, status);
    }

    /** 分段追加后在 RECEIVING 状态下按版本更新已接收行数、重复行数与待拒绝原因。 */
    @Override
    public void updateImportProgress(BankStatement record, Integer importedLineCount, Integer duplicateLineCount,
        String rejectReason) {
        Instant now = now();
        if (mapper.updateImportProgress(record, importedLineCount, duplicateLineCount, rejectReason, now) != 1) {
            throw new ConcurrentRequestException("银行对账单已变更，请刷新后重试: " + record.getId());
        }
        record.setImportedLineCount(importedLineCount);
        record.setDuplicateLineCount(duplicateLineCount);
        record.setRejectReason(rejectReason);
        record.setVersion(record.getVersion() + 1);
        record.setUpdateTime(now);
    }

    /** 已拒绝的对账单重置为 RECEIVING，成功后同步原对象。 */
    @Override
    public void restart(BankStatement record, BankStatement replacement) {
        Instant now = now();
        if (mapper.restart(record, replacement, now) != 1) {
            throw new ConcurrentRequestException("银行对账单已变更，请刷新后重试: " + record.getId());
        }
        record.setPeriodStart(replacement.getPeriodStart());
        record.setPeriodEnd(replacement.getPeriodEnd());
        record.setOpeningBalance(replacement.getOpeningBalance());
        record.setClosingBalance(replacement.getClosingBalance());
        record.setDeclaredLineCount(replacement.getDeclaredLineCount());
        record.setImportedLineCount(0);
        record.setDuplicateLineCount(0);
        record.setFileName(replacement.getFileName());
        record.setFileContentHash(replacement.getFileContentHash());
        record.setRejectReason(null);
        record.setStatus(BankStatementStatus.RECEIVING);
        record.setMakerId(replacement.getMakerId());
        record.setVersion(record.getVersion() + 1);
        record.setUpdateTime(now);
    }

    /** 完成导入时按版本与原状态转 IMPORTED 或 REJECTED。 */
    @Override
    public void changeStatus(BankStatement record, BankStatementStatus status, String rejectReason) {
        Instant now = now();
        if (mapper.changeStatus(record, status, rejectReason, now) != 1) {
            throw new ConcurrentRequestException("银行对账单已变更，请刷新后重试: " + record.getId());
        }
        record.setStatus(status);
        record.setRejectReason(rejectReason);
        record.setVersion(record.getVersion() + 1);
        record.setUpdateTime(now);
    }

    /** 写入时间：UTC 微秒，与 DATETIME(6) 一致。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
