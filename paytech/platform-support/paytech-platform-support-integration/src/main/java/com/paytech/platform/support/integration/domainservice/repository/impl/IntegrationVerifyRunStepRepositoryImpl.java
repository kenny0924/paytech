package com.paytech.platform.support.integration.domainservice.repository.impl;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.platform.support.integration.core.dal.dataobject.IntegrationVerifyRunStep;
import com.paytech.platform.support.integration.core.dal.mapper.IntegrationVerifyRunStepMapper;
import com.paytech.platform.support.integration.domainservice.repository.IntegrationVerifyRunStepRepository;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;

/** 校验运行步骤写入行数；事务由调用方持有。 */
@Repository
@AllArgsConstructor
public class IntegrationVerifyRunStepRepositoryImpl implements IntegrationVerifyRunStepRepository {

    private final IntegrationVerifyRunStepMapper mapper;

    /** 读取运行的全部步骤，按序号排序。 */
    @Override
    public List<IntegrationVerifyRunStep> byRun(long verifyRunId) {
        return mapper.byRun(verifyRunId);
    }

    /** 一次插入运行的全部步骤；空列表不访问数据库。 */
    @Override
    public void insertAll(List<IntegrationVerifyRunStep> rows) {
        if (!rows.isEmpty() && mapper.insertAll(rows) != rows.size()) {
            throw new ConcurrentRequestException("验证运行步骤写入未完成");
        }
    }
}
