package com.paytech.ledger.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.ledger.core.dal.dataobject.InstructionLine;
import com.paytech.ledger.core.dal.mapper.InstructionLineMapper;
import com.paytech.ledger.domainservice.repository.InstructionLineRepository;

import lombok.AllArgsConstructor;

/** InstructionLineRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class InstructionLineRepositoryImpl implements InstructionLineRepository {
    private final InstructionLineMapper mapper;

    /** 批量插入指令行。 */
    @Override
    public void insertBatch(List<InstructionLine> lines) {
        mapper.insertBatch(lines);
    }

    /** 按行号升序读取指令的全部行。 */
    @Override
    public List<InstructionLine> findByInstruction(long instructionId) {
        return mapper.selectByInstruction(instructionId);
    }
}
