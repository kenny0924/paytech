package com.paytech.compliance.core.dal.dataobject;

import java.time.Instant;

import lombok.Data;

/** 一个案件主体一种筛查类型的一次执行（t_screening_run）。 */
@Data
public class ScreeningRun {
    /** 执行 ID。 */
    private long screeningRunId;
    /** 案件 ID。 */
    private long caseId;
    /** 案件主体 ID。 */
    private long caseSubjectId;
    /** 筛查模板 ID。 */
    private long programScreeningId;
    /** 服务渠道编码。 */
    private String providerCode;
    /** 发给提供方的请求引用。 */
    private String requestRef;
    /** 触发来源：CASE 或 RERUN。 */
    private String triggerSource;
    /** 触发补偿事件子类型。 */
    private String triggerRef;
    /** 提供方执行引用。 */
    private String executionRef;
    /** STARTED、SUCCEEDED、FAILED、TIMED_OUT。 */
    private String status;
    /** 开始时间。 */
    private Instant startedTime;
    /** 完成时间。 */
    private Instant completedTime;
    /** 并发版本。 */
    private long version;
    /** 创建 UTC 时间。 */
    private Instant createTime;
    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
