package com.paytech.ledger.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.ledger.core.dal.dataobject.PeriodClose;
import com.paytech.ledger.core.enums.PeriodCloseStatus;
import com.paytech.ledger.core.enums.PeriodCloseType;
import com.paytech.ledger.domain.view.PeriodCloseView;

/** t_period_close：关账/重开流程根持久化与关账前置检查查询。 */
@Mapper
public interface PeriodCloseMapper {

    /** 插入流程根。 */
    int insert(PeriodClose record);

    /** 同一期间的下一个关账序号。 */
    int selectNextCloseNo(@Param("periodId") long periodId);

    /** 按期间与序号读取流程根。 */
    PeriodClose selectByPeriodAndNo(@Param("periodId") long periodId, @Param("closeNo") int closeNo);

    /** 按主键读取流程根。 */
    PeriodClose selectById(@Param("id") long id);

    /** 读取期间内指定类型序号最大的流程根。 */
    PeriodClose selectLatestByType(@Param("periodId") long periodId, @Param("closeType") PeriodCloseType closeType);

    /**
     * 按期望状态与版本迁移流程状态，同时写入活动槽、关账摘要与复核人
     *
     * @return 受影响行数
     */
    int updateStatus(@Param("close") PeriodClose close, @Param("status") PeriodCloseStatus status,
        @Param("activeSlot") Integer activeSlot, @Param("closeHash") String closeHash,
        @Param("checkerId") Long checkerId, @Param("now") Instant now);

    /** 读取流程摘要（含首个失败步骤类型）。 */
    PeriodCloseView selectView(@Param("periodId") long periodId, @Param("closeNo") int closeNo);

    /** 按序号降序分页读取流程摘要。 */
    List<PeriodCloseView> selectViewPage(@Param("periodId") long periodId, @Param("size") int size,
        @Param("offset") int offset);

    /** 统计期间的流程数。 */
    long countByPeriod(@Param("periodId") long periodId);

    /** 关账步骤 2：统计服务级补偿任务表中仍在待执行或可重试状态的任务（只读）。 */
    long countPendingCompensationEvents();

    /** 关账步骤 3：统计账簿内余额非零的渠道与银行账户（Treasury 未上线，无法核对）。 */
    long countNonZeroTreasuryAccounts(@Param("bookIds") List<Long> bookIds);
}
