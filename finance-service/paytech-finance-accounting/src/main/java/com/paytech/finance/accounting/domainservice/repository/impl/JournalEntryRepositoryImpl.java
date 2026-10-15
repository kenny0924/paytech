package com.paytech.finance.accounting.domainservice.repository.impl;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.paytech.finance.accounting.core.dal.dataobject.JournalEntry;
import com.paytech.finance.accounting.core.dal.mapper.JournalEntryMapper;
import com.paytech.finance.accounting.domainservice.repository.JournalEntryRepository;
import com.paytech.foundation.common.error.ConcurrentRequestException;

import lombok.AllArgsConstructor;

/** JournalEntryRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class JournalEntryRepositoryImpl implements JournalEntryRepository {
    private final JournalEntryMapper mapper;

    /** 插入分录头；同一指令或序号已有分录时按并发冲突拒绝。 */
    @Override
    public void insert(JournalEntry entry) {
        insertConcurrent(() -> mapper.insert(entry), "分录已存在: 指令 " + entry.getInstructionId());
    }

    /** 按指令读取分录头，不存在返回空。 */
    @Override
    public JournalEntry findByInstruction(long instructionId) {
        return mapper.selectByInstruction(instructionId);
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
