package com.paytech.risk.decision.core.dal.mapper;

import com.paytech.risk.decision.core.dal.dataobject.VelocityEvent;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 可计数计额的不可变风险事件表映射。 */
public interface VelocityEventMapper {
    /** 插入一行。 */
    int insert(@Param("row") VelocityEvent row);

    /** 批量插入；空集合由调用方跳过。 */
    int insertBatch(@Param("rows") List<VelocityEvent> rows);
}
