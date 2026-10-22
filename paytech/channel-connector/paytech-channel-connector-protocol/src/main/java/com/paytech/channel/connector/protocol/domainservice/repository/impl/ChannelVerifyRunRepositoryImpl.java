package com.paytech.channel.connector.protocol.domainservice.repository.impl;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.channel.connector.api.enums.VerifyRunStatus;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyRun;
import com.paytech.channel.connector.protocol.core.dal.mapper.ChannelVerifyRunMapper;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelVerifyRunRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;

/** 校验验证运行写入行数，成功后同步原对象；事务由调用方持有。 */
@Repository
@AllArgsConstructor
public class ChannelVerifyRunRepositoryImpl implements ChannelVerifyRunRepository {

    private final ChannelVerifyRunMapper mapper;

    /** 按内部标识读取运行。 */
    @Override
    public ChannelVerifyRun byId(long verifyRunId) {
        return mapper.byId(verifyRunId);
    }

    /** 分页查询渠道的运行，可按用例筛选，按开始时间倒序。 */
    @Override
    public List<ChannelVerifyRun> list(long channelId, Long verifyCaseId, long offset, int size) {
        return mapper.list(channelId, verifyCaseId, offset, size);
    }

    /** 使用与分页相同的筛选统计总数。 */
    @Override
    public long count(long channelId, Long verifyCaseId) {
        return mapper.count(channelId, verifyCaseId);
    }

    /** 每个用例最近一次运行（任意状态），按用例标识索引；空集合返回空映射。 */
    @Override
    public Map<Long, ChannelVerifyRun> latestByCases(Collection<Long> verifyCaseIds) {
        return verifyCaseIds.isEmpty() ? Map.of() : byCase(mapper.latestByCases(verifyCaseIds));
    }

    /** 每个用例最近一次已得出结论的运行，按用例标识索引；空集合返回空映射。 */
    @Override
    public Map<Long, ChannelVerifyRun> latestFinishedByCases(Collection<Long> verifyCaseIds) {
        return verifyCaseIds.isEmpty() ? Map.of() : byCase(mapper.latestFinishedByCases(verifyCaseIds));
    }

    /** 插入运行中的记录。 */
    @Override
    public void insert(ChannelVerifyRun row) {
        if (mapper.insert(row) != 1) {
            throw new ConcurrentRequestException("验证运行创建未成功，请刷新后重试");
        }
    }

    /** 只在仍为 RUNNING 时写入结论与证据；已有结论时抛出并发错误。 */
    @Override
    public void finish(ChannelVerifyRun row, VerifyRunStatus runStatus, String configVersionsJson, String endpointsJson, String failureSummary, Instant finishTime) {
        if (mapper.finish(row, runStatus, configVersionsJson, endpointsJson, failureSummary, finishTime) != 1) {
            throw new ConcurrentRequestException("验证运行已有结论，不能重复写入");
        }
        row.setRunStatus(runStatus);
        row.setConfigVersionsJson(configVersionsJson);
        row.setEndpointsJson(endpointsJson);
        row.setFailureSummary(failureSummary);
        row.setFinishTime(finishTime);
        row.setUpdateTime(finishTime);
    }

    static Map<Long, ChannelVerifyRun> byCase(List<ChannelVerifyRun> runs) {
        return runs.stream().collect(Collectors.toMap(ChannelVerifyRun::getVerifyCaseId, Function.identity()));
    }
}
