package com.paytech.finance.accounting.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.finance.accounting.core.dal.dataobject.JournalLine;

/** t_journal_line：会计分录行持久化（只追加）。 */
@Mapper
public interface JournalLineMapper {

    /** 批量插入分录行。 */
    int insertBatch(@Param("lines") List<JournalLine> lines);
}
