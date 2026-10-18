package com.paytech.ledger.core.dal.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.ledger.core.dal.dataobject.ManualAdjustmentLine;

/** t_manual_adjustment_line：人工调整明细持久化。 */
@Mapper
public interface ManualAdjustmentLineMapper {

    /** 批量插入调整明细。 */
    int insertBatch(@Param("lines") List<ManualAdjustmentLine> lines);

    /** 按行号升序读取调整明细。 */
    List<ManualAdjustmentLine> selectByAdjustment(@Param("adjustmentId") long adjustmentId);
}
