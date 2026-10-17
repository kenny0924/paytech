package com.paytech.platform.support.integration.domain.execution;

import java.util.LinkedHashMap;
import java.util.Map;

import com.paytech.platform.support.api.enums.ExecuteStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一个原子 API 节点的执行结果，写入 steps.&lt;id&gt;；ERROR 时输出为空对象。 */
@Getter
@AllArgsConstructor
public class StepResult {

    /** 环境中执行状态的属性名。 */
    public static final String EXECUTE_STATUS = "executeStatus";

    /** 环境中输出的属性名。 */
    public static final String OUTPUTS = "outputs";

    /** 执行状态。 */
    private final ExecuteStatus executeStatus;

    /** 按状态分支映射的输出。 */
    private final Map<String, Object> outputs;

    /** 结果无法确认的节点。 */
    public static StepResult error() {
        return new StepResult(ExecuteStatus.ERROR, Map.of());
    }

    /** 表达式读取的形状：{executeStatus, outputs}。 */
    public Map<String, Object> toEnvironment() {
        var result = new LinkedHashMap<String, Object>();
        result.put(EXECUTE_STATUS, executeStatus.getCode());
        result.put(OUTPUTS, outputs);
        return result;
    }
}
