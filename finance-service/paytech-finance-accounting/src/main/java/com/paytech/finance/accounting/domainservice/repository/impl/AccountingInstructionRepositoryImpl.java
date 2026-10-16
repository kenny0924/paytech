package com.paytech.finance.accounting.domainservice.repository.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.finance.accounting.core.dal.dataobject.AccountingInstruction;
import com.paytech.finance.accounting.core.dal.mapper.AccountingInstructionMapper;
import com.paytech.finance.accounting.core.enums.InstructionStatus;
import com.paytech.finance.accounting.domainservice.repository.AccountingInstructionRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;

import lombok.AllArgsConstructor;

/** AccountingInstructionRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class AccountingInstructionRepositoryImpl implements AccountingInstructionRepository {
    private final Clock clock;
    private final AccountingInstructionMapper mapper;

    /** 插入记账指令；同一来源已有指令时按并发冲突拒绝。 */
    @Override
    public void insert(AccountingInstruction instruction) {
        insertConcurrent(() -> mapper.insert(instruction), "记账指令已存在: " + instruction.getId());
    }

    /** 按人工调整读取其唯一指令，不存在返回空。 */
    @Override
    public AccountingInstruction findByManualAdjustment(long manualAdjustmentId) {
        return mapper.selectByManualAdjustment(manualAdjustmentId);
    }

    /** 按原状态与版本迁移指令状态，成功后同步原对象。 */
    @Override
    public void updateStatus(AccountingInstruction instruction, InstructionStatus status) {
        Instant now = now();
        if (mapper.updateStatus(instruction, status, now) != 1) {
            throw new ConcurrentRequestException("记账指令状态已变化: " + instruction.getId());
        }
        instruction.setStatus(status);
        instruction.setVersion(instruction.getVersion() + 1);
        instruction.setUpdateTime(now);
    }

    /** 统计账簿中尚未过账的指令。 */
    @Override
    public long countPendingByBooks(List<Long> bookIds) {
        return mapper.countPendingByBooks(bookIds);
    }

    /** 当前 UTC 时间，截断到数据库支持的微秒精度。 */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 执行插入，唯一键冲突说明同一命令已被并发处理。 */
    private void insertConcurrent(Runnable insert, String message) {
        try {
            insert.run();
        } catch (DuplicateKeyException e) {
            throw new ConcurrentRequestException(message, e);
        }
    }
}
