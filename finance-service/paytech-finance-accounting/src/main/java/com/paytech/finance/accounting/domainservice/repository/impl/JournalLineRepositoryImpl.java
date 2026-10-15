package com.paytech.finance.accounting.domainservice.repository.impl;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.paytech.finance.accounting.core.dal.dataobject.JournalLine;
import com.paytech.finance.accounting.core.dal.mapper.JournalLineMapper;
import com.paytech.finance.accounting.domainservice.repository.JournalLineRepository;

import lombok.AllArgsConstructor;

/** JournalLineRepository 的 MyBatis 实现。 */
@Repository
@AllArgsConstructor
public class JournalLineRepositoryImpl implements JournalLineRepository {
    private final JournalLineMapper mapper;

    /** 批量插入分录行。 */
    @Override
    public void insertBatch(List<JournalLine> lines) {
        mapper.insertBatch(lines);
    }
}
