package com.paytech.channel.connector.protocol.domainservice.service.impl;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.foundation.compensation.api.CompensationEventRequest;
import com.paytech.channel.connector.api.enums.VerifyRunStatus;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyRun;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyRunStep;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelVerifyRunRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.ChannelVerifyRunStepRepository;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelVerifyRunService;

import lombok.AllArgsConstructor;

/**
 * 验证运行的本地保存（沙箱验证技术方案第 3.6 节）：运行与补偿任务原子登记，提交后由本服务执行器异步执行；
 * 补偿模块未启用时拒绝发起。
 */
@Service
@AllArgsConstructor
public class ChannelVerifyRunServiceImpl implements ChannelVerifyRunService {

    /** 补偿任务事件类型。 */
    public static final String EVENT_TYPE = "INTEGRATION_VERIFY_RUN";

    private final ChannelVerifyRunRepository runs;
    private final ChannelVerifyRunStepRepository steps;
    private final ObjectProvider<CompensationEventRegistrar> registrar;

    /** 插入运行并登记补偿任务；事件键为运行标识。 */
    @Override
    public void start(ChannelVerifyRun run) {
        CompensationEventRegistrar events = registrar.getIfAvailable();
        if (events == null) {
            throw new ErrorException(CommonErrorCode.SERVICE_UNAVAILABLE, "沙箱验证运行需要启用本地补偿任务（compensation.enabled）");
        }
        runs.insert(run);
        events.register(new CompensationEventRequest(Long.toString(run.getVerifyRunId()), EVENT_TYPE, "-"));
    }

    /** 先写步骤再写结论；结论条件更新失败时整个事务回滚。 */
    @Override
    public void finish(ChannelVerifyRun run, VerifyRunStatus status, String configVersionsJson,
        String endpointsJson, String failureSummary, Instant finishTime, List<ChannelVerifyRunStep> stepRows) {
        steps.insertAll(stepRows);
        runs.finish(run, status, configVersionsJson, endpointsJson, failureSummary, finishTime);
    }
}
