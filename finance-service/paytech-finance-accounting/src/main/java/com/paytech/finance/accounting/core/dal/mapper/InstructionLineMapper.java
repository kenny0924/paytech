package com.paytech.finance.accounting.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.finance.accounting.core.dal.dataobject.InstructionLine;

/** t_instruction_line：记账指令行持久化。 */
@Mapper
public interface InstructionLineMapper {

    /** 批量插入指令行。 */
    int insertBatch(@Param("lines") List<InstructionLine> lines);
}
