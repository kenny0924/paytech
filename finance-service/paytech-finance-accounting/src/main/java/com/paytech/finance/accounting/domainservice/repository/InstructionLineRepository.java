package com.paytech.finance.accounting.domainservice.repository;

import java.util.List;

import com.paytech.finance.accounting.core.dal.dataobject.InstructionLine;

/** t_InstructionLine：记账指令行写入。 */
public interface InstructionLineRepository {

    /** 批量插入指令行。 */
    void insertBatch(List<InstructionLine> lines);
}
