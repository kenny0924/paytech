package com.paytech.channel.connector.protocol.domain.execution;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流程运行上下文的三区：input（标准 API 输入）、resources（初始化结果）、steps（节点结果）。
 *
 * <p>
 * 只由执行器写入，只在本次调用内存中存在，不持久化。表达式只读取 {@link #environment()} 的投影。
 * 沙箱验证运行另带 {@link VerificationScope}（验证账号与轨迹），不进入表达式环境；正式执行为 null。
 */
public class ExecutionContext {

    /** 上下文根：标准 API 输入。 */
    public static final String INPUT = "input";

    /** 上下文根：初始化结果。 */
    public static final String RESOURCES = "resources";

    /** 上下文根：节点结果。 */
    public static final String STEPS = "steps";

    private final Map<String, Object> input;
    private final Map<String, Object> resources = new LinkedHashMap<>();
    private final Map<String, Object> steps = new LinkedHashMap<>();
    private final VerificationScope verification;

    public ExecutionContext(Map<String, Object> input) {
        this(input, null);
    }

    public ExecutionContext(Map<String, Object> input, VerificationScope verification) {
        this.input = input;
        this.verification = verification;
    }

    /** 沙箱验证附加项；正式执行为 null。 */
    public VerificationScope getVerification() {
        return verification;
    }

    /** 沙箱验证轨迹；正式执行为 null。 */
    public ExecutionTrace trace() {
        return verification == null ? null : verification.getTrace();
    }

    /** 记录初始化项结果。 */
    public void putResource(String id, Map<String, Object> value) {
        resources.put(id, value);
    }

    /** 记录节点结果。 */
    public void putStep(String id, StepResult result) {
        steps.put(id, result.toEnvironment());
    }

    /** 节点是否已执行。 */
    public boolean executed(String id) {
        return steps.containsKey(id);
    }

    /** 表达式与路径读取的环境：三区的只读视图。 */
    public Map<String, Object> environment() {
        var environment = new HashMap<String, Object>();
        environment.put(INPUT, Collections.unmodifiableMap(input));
        environment.put(RESOURCES, Collections.unmodifiableMap(resources));
        environment.put(STEPS, Collections.unmodifiableMap(steps));
        return environment;
    }
}
