package com.paytech.ledger.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.ledger.core.dal.dataobject.InstructionLine;

/** t_instruction_line：记账指令行持久化。 */
@Mapper
public interface InstructionLineMapper {

    /** 批量插入指令行。 */
    int insertBatch(@Param("lines") List<InstructionLine> lines);

    /** 按行号升序读取指令的全部行。 */
    List<InstructionLine> selectByInstruction(@Param("instructionId") long instructionId);
}
