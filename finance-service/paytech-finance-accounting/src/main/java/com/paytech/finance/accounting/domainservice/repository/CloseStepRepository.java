package com.paytech.finance.accounting.domainservice.repository;

import java.util.List;

import com.paytech.finance.accounting.core.dal.dataobject.CloseStep;
import com.paytech.finance.accounting.core.enums.CloseStepStatus;

/** t_close_step：关账步骤读写。 */
public interface CloseStepRepository {

    /** 批量插入步骤。 */
    void insertBatch(List<CloseStep> steps);

    /** 按步骤号升序读取流程步骤。 */
    List<CloseStep> findByClose(long periodCloseId);

    /** 把步骤迁移到终态并写入结果摘要，成功后同步原对象。 */
    void complete(CloseStep step, CloseStepStatus status, String resultHash);
}
