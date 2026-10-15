package com.paytech.finance.accounting.core.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.finance.accounting.core.dal.dataobject.JournalEntry;

/** t_journal_entry：会计分录头持久化（只追加）。 */
@Mapper
public interface JournalEntryMapper {

    /** 插入分录头。 */
    int insert(JournalEntry record);

    /** 按指令读取其分录头。 */
    JournalEntry selectByInstruction(@Param("instructionId") long instructionId);
}
