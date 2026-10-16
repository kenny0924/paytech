package com.paytech.finance.accounting.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.finance.accounting.core.dal.dataobject.PeriodCloseBook;

/** t_period_close_book：关账流程内逐账簿证据持久化。 */
@Mapper
public interface PeriodCloseBookMapper {

    /** 批量插入账簿证据。 */
    int insertBatch(@Param("rows") List<PeriodCloseBook> rows);

    /** 按账簿主键升序读取流程的全部账簿证据。 */
    List<PeriodCloseBook> selectByClose(@Param("periodCloseId") long periodCloseId);

    /**
     * 结转分录过账后推进水位并记录结转指令；只允许 LOCKED 状态
     *
     * @return 受影响行数
     */
    int recordPosting(@Param("book") PeriodCloseBook book, @Param("asOfSequence") long asOfSequence,
        @Param("instructionId") long instructionId, @Param("now") Instant now);

    /**
     * 冻结最终试算并置为 FINALIZED；只允许 LOCKED 状态
     *
     * @return 受影响行数
     */
    int finalizeBook(@Param("book") PeriodCloseBook book, @Param("finalTrialRunId") long finalTrialRunId,
        @Param("now") Instant now);

    /**
     * 重开冲回过账后记录冲回指令（无冲回行时为空）并置为 FINALIZED；水位保持发起时冻结值，只允许 LOCKED 状态
     *
     * @return 受影响行数
     */
    int finalizeReopenBook(@Param("book") PeriodCloseBook book, @Param("instructionId") Long instructionId,
        @Param("now") Instant now);

    /** 统计流程中未 FINALIZED、最终试算缺失、未成功或水位不一致的账簿数。 */
    long countInvalidFinalBooks(@Param("periodCloseId") long periodCloseId);
}
