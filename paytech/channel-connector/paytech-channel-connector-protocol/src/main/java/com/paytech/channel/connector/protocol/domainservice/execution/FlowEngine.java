package com.paytech.channel.connector.protocol.domainservice.execution;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.enums.ExecuteStatus;
import com.paytech.channel.connector.protocol.domain.execution.*;
import com.paytech.channel.connector.protocol.domainservice.expression.ExpressionEngine;
import com.paytech.channel.connector.protocol.domainservice.expression.ExpressionProfile;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/**
 * 流程引擎（流程结构第 4、5 节）：从入口节点开始逐个执行，节点结果写入 steps，按优先级判断出边，到出口后映射标准 API 输出。
 *
 * <p>
 * 调用方在第一个节点开始前完成初始化与预算检查；进入本引擎后不再抛出，任何失败都转为 ERROR 结果。 节点内失败只让该节点为 ERROR，流程继续按出边判断；出边条件非布尔或异常、预算耗尽、出口输出不满足结构则整次 ERROR。
 * 日志只记编码、节点 ID、状态与耗时，不记字段值。
 */
@Slf4j
@Component
@AllArgsConstructor
public class FlowEngine {

    private final ValueResolver valueResolver;
    private final ExpressionEngine expressionEngine;

    /**
     * 执行流程。
     *
     * @param snapshot
     *            本次执行快照
     * @param context
     *            已写入 input 与 resources 的上下文
     * @param deadline
     *            流程预算
     * @param runner
     *            原子 API 节点执行
     * @return 出口决定的结果；任何流程级失败为 ERROR
     */
    public ExecutionResult run(ExecutionSnapshot snapshot, ExecutionContext context, Deadline deadline,
        NodeRunner runner) {
        try {
            return walk(snapshot, context, deadline, runner);
        } catch (RuntimeException exception) {
            log.warn("外部 API 流程结果为 ERROR standardApiCode={} integrationCode={} reason={}",
                snapshot.getStandardApiCode(), snapshot.getIntegrationCode(), reason(exception));
            return ExecutionResult.error();
        }
    }

    /** 沿出边前进；流程无环，执行节点数超过节点总数即视为配置损坏。 */
    ExecutionResult walk(ExecutionSnapshot snapshot, ExecutionContext context, Deadline deadline, NodeRunner runner) {
        ExecutionFlow flow = snapshot.getFlow();
        String current = flow.getEntry();
        for (int executed = 0; executed <= flow.getNodes().size(); executed++) {
            ExecutionFlow.Node node = flow.getNodes().get(current);
            if (node == null) {
                return finish(snapshot, current, context);
            }
            if (context.executed(current)) {
                throw new ExecutionErrorException("节点重复执行：" + current);
            }
            if (deadline.expired()) {
                throw new ExecutionErrorException("流程预算已耗尽，未执行节点：" + current);
            }
            context.putStep(current, runNode(snapshot, node, context, deadline, runner));
            current = nextTarget(flow.getEdges().getOrDefault(current, List.of()), context.environment());
        }
        throw new ExecutionErrorException("执行节点数超过流程节点总数");
    }

    /** 到达出口：映射输出并记录结果。 */
    ExecutionResult finish(ExecutionSnapshot snapshot, String returnId, ExecutionContext context) {
        ExecutionFlow.Return target = snapshot.getFlow().getReturns().get(returnId);
        if (target == null) {
            throw new ExecutionErrorException("连线目标不存在：" + returnId);
        }
        ExecutionResult result = mapReturn(target, snapshot.getOutputSchema(), context.environment());
        if (context.trace() != null) {
            context.trace().returned(returnId);
        }
        log.info("外部 API 流程结束 standardApiCode={} integrationCode={} returnId={} executeStatus={}",
            snapshot.getStandardApiCode(), snapshot.getIntegrationCode(), returnId,
            result.getExecuteStatus().getCode());
        return result;
    }

    /** 执行一个节点；执行器抛出的任何异常都记为该节点 ERROR。 */
    StepResult runNode(ExecutionSnapshot snapshot, ExecutionFlow.Node node, ExecutionContext context, Deadline deadline,
        NodeRunner runner) {
        long started = System.nanoTime();
        StepResult result;
        String failure = null;
        try {
            result = runner.run(snapshot, node, context, deadline);
        } catch (RuntimeException exception) {
            failure = reason(exception);
            log.warn("外部 API 节点异常 standardApiCode={} integrationCode={} nodeId={} reason={}",
                snapshot.getStandardApiCode(), snapshot.getIntegrationCode(), node.getId(), failure);
            result = StepResult.error();
        }
        if (context.trace() != null) {
            context.trace().nodeResult(node.getId(), node.getIntegrationApi(), result.getExecuteStatus().getCode(),
                failure, (System.nanoTime() - started) / 1_000_000L);
        }
        log.info("外部 API 节点结束 standardApiCode={} integrationCode={} nodeId={} executeStatus={} elapsedMs={}",
            snapshot.getStandardApiCode(), snapshot.getIntegrationCode(), node.getId(),
            result.getExecuteStatus().getCode(), (System.nanoTime() - started) / 1_000_000L);
        return result;
    }

    /**
     * 按优先级判断出边：第一条为真的条件连线胜出，全部为假走兜底。
     *
     * @param edges
     *            同一起点的出边，条件连线按优先级升序，兜底在最后
     * @param environment
     *            只读环境
     * @return 目标节点或出口 ID
     * @throws ExecutionErrorException
     *             条件非布尔或求值异常，或缺少兜底
     */
    public String nextTarget(List<ExecutionFlow.Edge> edges, Map<String, Object> environment) {
        for (ExecutionFlow.Edge edge : edges) {
            if (edge.getCondition() == null) {
                return edge.getTo();
            }
            Object value;
            try {
                value = expressionEngine.compile(ExpressionProfile.READ_ONLY, edge.getCondition()).execute(environment);
            } catch (RuntimeException exception) {
                throw new ExecutionErrorException("连线条件求值失败：" + edge.getTo(), exception);
            }
            if (!(value instanceof Boolean matched)) {
                throw new ExecutionErrorException("连线条件结果不是布尔值：" + edge.getTo());
            }
            if (matched) {
                return edge.getTo();
            }
        }
        throw new ExecutionErrorException("节点缺少兜底连线");
    }

    /**
     * 出口映射（流程结构第 4.2、5 节）：ERROR 出口输出空对象；SUCCESS/FAIL 按标准 API 输出结构取值， 非必填没有值则省略，任何取值失败或组装结果不满足结构（含必填输出缺失）整次改为 ERROR。
     *
     * @param target
     *            出口
     * @param outputSchema
     *            标准 API 输出结构
     * @param environment
     *            只读环境
     * @return 执行结果
     */
    public ExecutionResult mapReturn(ExecutionFlow.Return target, FieldSchema outputSchema,
        Map<String, Object> environment) {
        if (target.getExecuteStatus() == ExecuteStatus.ERROR) {
            return ExecutionResult.error();
        }
        try {
            var outputs = new LinkedHashMap<String, Object>();
            for (var entry : target.getOutputs().properties()) {
                assign(outputs, entry.getKey(), entry.getValue(), outputSchema, environment, target.getId());
            }
            outputSchema.check(outputs, "");
            return new ExecutionResult(target.getExecuteStatus(), outputs);
        } catch (ExecutionErrorException | FieldSchemaViolation exception) {
            log.warn("外部 API 出口输出无效，改为 ERROR returnId={} reason={}", target.getId(), reason(exception));
            return ExecutionResult.error();
        }
    }

    /** 取一个输出值并挂到点分路径上；路径不在输出结构中或经过数组都视为配置损坏。 */
    void assign(Map<String, Object> outputs, String key, JsonNode binding, FieldSchema outputSchema,
        Map<String, Object> environment, String returnId) {
        String path =
            key.startsWith(FieldSchema.OUTPUTS_ROOT + ".") ? key.substring(FieldSchema.OUTPUTS_ROOT.length() + 1) : key;
        FieldSchema field = path.contains("[") ? null : outputSchema.at(path);
        if (field == null) {
            throw new ExecutionErrorException("出口输出字段不存在：" + returnId + "." + key);
        }
        Object value = valueResolver.resolve(binding, environment, field.getDataType().getCode(), field.isRequired(),
            "returns." + returnId + ".outputs." + key);
        if (value == null) {
            return;
        }
        String[] segments = path.split("\\.");
        Map<String, Object> current = outputs;
        for (int index = 0; index < segments.length - 1; index++) {
            Object next = current.computeIfAbsent(segments[index], name -> new LinkedHashMap<String, Object>());
            if (!(next instanceof LinkedHashMap<?, ?>)) {
                throw new ExecutionErrorException("出口输出路径与已赋值的上级冲突：" + returnId + "." + key);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> child = (Map<String, Object>)next;
            current = child;
        }
        current.put(segments[segments.length - 1], value);
    }

    /** 日志原因只用固定说明与配置位置。 */
    static String reason(RuntimeException exception) {
        return exception instanceof ExecutionErrorException || exception instanceof FieldSchemaViolation
            ? exception.getMessage() : exception.getClass().getSimpleName();
    }
}
