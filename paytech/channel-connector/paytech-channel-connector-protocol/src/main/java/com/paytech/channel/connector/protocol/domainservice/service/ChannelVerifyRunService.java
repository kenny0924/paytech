package com.paytech.channel.connector.protocol.domainservice.service;

import java.time.Instant;
import java.util.List;

import com.paytech.channel.connector.api.enums.VerifyRunStatus;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyRun;
import com.paytech.channel.connector.protocol.core.dal.dataobject.ChannelVerifyRunStep;

/** 验证运行的本地保存；事务由调用方持有，事务内只访问本库。 */
public interface ChannelVerifyRunService {

    /** 插入 RUNNING 运行并在同一事务中登记执行它的补偿任务。 */
    void start(ChannelVerifyRun run);

    /** 一次写入全部步骤并在仍为 RUNNING 时写入结论与证据。 */
    void finish(ChannelVerifyRun run, VerifyRunStatus status, String configVersionsJson, String endpointsJson,
        String failureSummary, Instant finishTime, List<ChannelVerifyRunStep> steps);
}
