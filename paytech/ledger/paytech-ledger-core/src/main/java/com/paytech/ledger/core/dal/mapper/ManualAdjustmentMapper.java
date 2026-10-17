package com.paytech.ledger.core.dal.mapper;

import java.time.Instant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.ledger.core.dal.dataobject.ManualAdjustment;
import com.paytech.ledger.core.enums.AdjustmentStatus;

/** t_manual_adjustment：人工会计调整持久化。 */
@Mapper
public interface ManualAdjustmentMapper {

    /** 插入一行。 */
    int insert(ManualAdjustment record);

    /** 按主键读取。 */
    ManualAdjustment selectById(@Param("id") long id);

    /**
     * 按期望状态和版本迁移调整状态。
     *
     * @return 受影响行数
     */
    int updateStatus(@Param("adjustment") ManualAdjustment adjustment, @Param("status") AdjustmentStatus status,
        @Param("now") Instant now);
}
