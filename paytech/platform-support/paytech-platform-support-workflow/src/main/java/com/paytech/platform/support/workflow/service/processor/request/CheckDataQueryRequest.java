package com.paytech.platform.support.workflow.service.processor.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import com.paytech.platform.support.workflow.core.enums.DataQueryCheckTrigger;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 检查一个 DATA_QUERY 动作的内部请求；首查、定时重查与立即检查共用。 */
@Getter
@AllArgsConstructor
public class CheckDataQueryRequest {

    /** 动作运行 ID。 */
    @Positive
    private final long actionRunId;

    /** 触发来源。 */
    @NotNull
    private final DataQueryCheckTrigger trigger;

    /** 可信操作人账号；只有 MANUAL 必填，其他为 null。 */
    private final String actor;

    /** 可信会话；只有 MANUAL 必填，其他为 null。 */
    private final String session;

    /**
     * 系统触发（首查、定时重查）的请求。
     *
     * @param actionRunId 动作运行 ID
     * @param trigger ACTIVATION 或 SCHEDULE
     * @return 请求
     */
    public static CheckDataQueryRequest system(long actionRunId, DataQueryCheckTrigger trigger) {
        return new CheckDataQueryRequest(actionRunId, trigger, null, null);
    }
}
