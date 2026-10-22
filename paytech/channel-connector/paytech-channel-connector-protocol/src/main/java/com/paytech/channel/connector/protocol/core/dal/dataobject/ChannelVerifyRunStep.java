package com.paytech.channel.connector.protocol.core.dal.dataobject;

import com.paytech.channel.connector.api.enums.VerifyStepStatus;
import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 验证运行中一个步骤的实际输入、结果、期望核对与脱敏轨迹；运行结束时一次写入。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChannelVerifyRunStep {

    /** 步骤记录内部技术标识。 */
    private Long verifyRunStepId;

    /** 所属运行标识。 */
    private Long verifyRunId;

    /** 步骤序号，从 1 开始。 */
    private int stepNo;

    /** 用例内的步骤标识。 */
    private String stepKey;

    /** 该步骤执行的标准 API 编码。 */
    private String standardApiCode;

    /** 步骤结论。 */
    private VerifyStepStatus stepStatus;

    /** 模板渲染后的实际输入；未执行或渲染失败时为空。 */
    private String resolvedInputJson;

    /** 执行结果；未执行时为空。 */
    private String resultJson;

    /** 期望核对；未执行时为空。 */
    private String assertionsJson;

    /** 脱敏轨迹；未执行时为空。 */
    private String traceJson;

    /** 耗时毫秒；未执行时为空。 */
    private Integer elapsedMs;

    /** UTC 微秒创建时间。 */
    private Instant createTime;
}
