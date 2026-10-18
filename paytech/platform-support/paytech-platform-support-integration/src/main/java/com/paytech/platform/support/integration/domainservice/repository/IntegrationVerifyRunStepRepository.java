package com.paytech.platform.support.integration.domainservice.repository;

import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationVerifyRunStep;
import java.util.List;

/** 验证运行步骤的单表仓储；事务由调用方持有，不向其他模块发布 DataObject。 */
public interface IntegrationVerifyRunStepRepository {

    /** 读取运行的全部步骤，按序号排序。 */
    List<IntegrationVerifyRunStep> byRun(long verifyRunId);

    /** 一次插入运行的全部步骤；空列表不访问数据库。 */
    void insertAll(List<IntegrationVerifyRunStep> rows);
}
