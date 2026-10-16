package com.paytech.finance.accounting.domainservice.repository;

import java.util.List;

import com.paytech.finance.accounting.core.dal.dataobject.InstructionLine;

/** t_InstructionLine：记账指令行写入与读取。 */
public interface InstructionLineRepository {

    /** 批量插入指令行。 */
    void insertBatch(List<InstructionLine> lines);

    /** 按行号升序读取指令的全部行。 */
    List<InstructionLine> findByInstruction(long instructionId);
}
