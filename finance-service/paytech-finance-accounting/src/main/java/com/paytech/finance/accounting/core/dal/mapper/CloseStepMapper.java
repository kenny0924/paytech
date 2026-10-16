package com.paytech.finance.accounting.core.dal.mapper;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.finance.accounting.core.dal.dataobject.CloseStep;
import com.paytech.finance.accounting.core.enums.CloseStepStatus;

/** t_close_step：关账步骤持久化。 */
@Mapper
public interface CloseStepMapper {

    /** 批量插入步骤。 */
    int insertBatch(@Param("rows") List<CloseStep> rows);

    /** 按步骤号升序读取流程的全部步骤。 */
    List<CloseStep> selectByClose(@Param("periodCloseId") long periodCloseId);

    /**
     * 按期望状态迁移步骤并写入结果摘要与完成时间
     *
     * @return 受影响行数
     */
    int updateStatus(@Param("step") CloseStep step, @Param("status") CloseStepStatus status,
        @Param("resultHash") String resultHash, @Param("now") Instant now);
}
