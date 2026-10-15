package com.paytech.finance.accounting.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.finance.accounting.core.dal.dataobject.InstructionLine;
import com.paytech.finance.accounting.core.dal.mapper.InstructionLineMapper;
import com.paytech.finance.accounting.domainservice.repository.InstructionLineRepository;

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
}
