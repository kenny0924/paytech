package com.paytech.ledger.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.ledger.core.dal.dataobject.JournalEntry;
import com.paytech.ledger.domain.view.JournalEntryDetailView;

/** t_journal_entry：会计分录头持久化（只追加）。 */
@Mapper
public interface JournalEntryMapper {

    /** 插入分录头。 */
    int insert(JournalEntry record);

    /** 按指令读取其分录头。 */
    JournalEntry selectByInstruction(@Param("instructionId") long instructionId);

    /** 读取分录头及其来源指令与人工调整单号，不存在返回空。 */
    JournalEntryDetailView selectDetail(@Param("journalEntryId") long journalEntryId);

    /** 关账步骤 2：统计期间内借贷不平或没有分录行的分录。 */
    long countUnbalancedInPeriod(@Param("periodId") long periodId, @Param("bookIds") List<Long> bookIds);
}
