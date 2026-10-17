package com.paytech.platform.support.integration.domain.execution;

import java.util.Map;

import com.paytech.platform.support.api.enums.ExecuteStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一次标准 API 执行的结果：出口决定的执行状态与标准 API 输出；ERROR 时输出为空对象。 */
@Getter
@AllArgsConstructor
public class ExecutionResult {

    /** 执行状态。 */
    private final ExecuteStatus executeStatus;

    /** 标准 API 输出（outputs 之下的结构）。 */
    private final Map<String, Object> outputs;

    /** 结果无法确认。 */
    public static ExecutionResult error() {
        return new ExecutionResult(ExecuteStatus.ERROR, Map.of());
    }
}
