package com.paytech.channel.connector.protocol.domainservice.repository;

import com.paytech.channel.connector.api.enums.VerifyRunStatus;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationVerifyRun;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** 验证运行的单表仓储；事务由调用方持有，不向其他模块发布 DataObject。 */
public interface IntegrationVerifyRunRepository {

    /** 按内部标识读取运行。 */
    IntegrationVerifyRun byId(long verifyRunId);

    /** 分页查询接入方的运行，可按用例筛选，按开始时间倒序。 */
    List<IntegrationVerifyRun> list(long integrationId, Long verifyCaseId, long offset, int size);

    /** 使用与分页相同的筛选统计总数。 */
    long count(long integrationId, Long verifyCaseId);

    /** 每个用例最近一次运行（任意状态），按用例标识索引；空集合返回空映射。 */
    Map<Long, IntegrationVerifyRun> latestByCases(Collection<Long> verifyCaseIds);

    /** 每个用例最近一次已得出结论的运行，按用例标识索引；空集合返回空映射。 */
    Map<Long, IntegrationVerifyRun> latestFinishedByCases(Collection<Long> verifyCaseIds);

    /** 插入运行中的记录。 */
    void insert(IntegrationVerifyRun row);

    /** 只在仍为 RUNNING 时写入结论与证据；已有结论时抛出并发错误。 */
    void finish(IntegrationVerifyRun row, VerifyRunStatus runStatus, String configVersionsJson, String endpointsJson, String failureSummary, Instant finishTime);
}
