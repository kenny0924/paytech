package com.paytech.risk.decision.domainservice.service;

import com.paytech.risk.decision.core.dal.dataobject.ReleaseShadowRun;
import com.paytech.risk.decision.domain.runtime.ShadowSampleContext;

/** 影子回放结果的本地保存；不登记补偿任务，供回放 Handler 使用。 */
public interface ShadowRunService {
    /** 原子保存一个影子样本评估及其 Feature 结果与轨迹；同一批次同一基线评估重复时抛出并发冲突。 */
    void saveSample(ShadowSampleContext context);

    /** 写入影子批次完成汇总；批次已结束或版本变化时抛出并发冲突。 */
    void complete(ReleaseShadowRun run, long expectedVersion);
}
