package com.paytech.channel.connector.protocol.service.processor;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.channel.connector.protocol.core.enums.StandardApiNotSentCode;
import com.paytech.channel.connector.protocol.domain.execution.*;
import com.paytech.channel.connector.protocol.domainservice.execution.AtomicApiExecutor;
import com.paytech.channel.connector.protocol.domainservice.execution.FlowEngine;
import com.paytech.channel.connector.protocol.domainservice.execution.InitializerRunner;
import com.paytech.channel.connector.protocol.service.processor.request.ExecuteStandardApiRequest;

import lombok.AllArgsConstructor;

/**
 * 标准 API 执行（protocol 公开的进程内入口，原 Platform 内部接口第 1～3 节）：不加锁、不写库，只有加载快照的一次短只读事务。
 *
 * <p>
 * 第一个节点开始前的问题抛出 {@link StandardApiNotSentException} 并保证没有发出渠道请求：标准 API 或实现不存在、配置未通过运行前校验、
 * input 不满足标准 API 请求字段（字段位置以 input. 开头）、必需初始化项失败或预算已耗尽。第一个节点开始后由流程引擎兜底，任何失败都是
 * ERROR 结果。初始化（Vault 读取、读卡）与渠道请求都在事务外执行，不重试；调用方必须在数据库事务外调用。
 *
 * <p>
 * 沙箱验证运行以同一入口执行（沙箱验证技术方案第 2 节）：请求带验证附加项时，记录配置版本与出站地址， 流程用到的出站地址必须都标记为沙箱，否则在任何外部调用前拒绝；账号改读验证账号，其余步骤不变。
 */
@Component
@AllArgsConstructor
public class ExecuteStandardApiProcessor extends AbstractProcessor<ExecuteStandardApiRequest, ExecutionResult> {

    /** 输入在请求体中的属性名，用作字段错误位置的前缀。 */
    static final String INPUT = "input";

    private final FlowEngine flowEngine;
    private final AtomicApiExecutor atomicApiExecutor;
    private final InitializerRunner initializerRunner;
    private final ExecutionSnapshotLoader snapshotLoader;

    @Override
    protected ExecutionResult processRequest(ExecuteStandardApiRequest request) {
        ExecutionSnapshot snapshot = snapshotLoader.load(request.getStandardApiCode(), request.getIntegrationCode());
        if (request.getVerification() != null) {
            requireSandbox(snapshot, request.getVerification().getTrace());
        }
        var context = new ExecutionContext(input(snapshot, request), request.getVerification());
        Deadline deadline = Deadline.start(snapshot.getFlow().getTimeoutMs());
        initializerRunner.run(snapshot, context, deadline);
        if (deadline.expired()) {
            throw new StandardApiNotSentException(StandardApiNotSentCode.INTEGRATION_BUDGET_EXHAUSTED,
                "第一个节点开始前流程预算已耗尽: " + snapshot.getIntegrationCode() + ", 标准 API: " + snapshot.getStandardApiCode());
        }
        return flowEngine.run(snapshot, context, deadline, atomicApiExecutor);
    }

    /** 验证运行：记录加载的配置，流程用到的出站地址必须全部标记为沙箱。 */
    void requireSandbox(ExecutionSnapshot snapshot, ExecutionTrace trace) {
        trace.loaded(snapshot.getConfigVersions(), snapshot.getEndpoints(), snapshot.getSandboxEndpoints());
        var notSandbox = snapshot.usedEndpoints().stream()
            .filter(code -> !snapshot.getSandboxEndpoints().contains(code)).toList();
        if (!notSandbox.isEmpty()) {
            throw new SandboxRequiredException("出站地址未标记沙箱，验证运行未发出请求: " + String.join("、", notSandbox)
                + ", 接入方: " + snapshot.getIntegrationCode());
        }
    }

    /** 按标准 API 请求字段校验并转换 input；不满足时以未发出的 VALIDATION_ERROR 返回出错位置。 */
    @SuppressWarnings("unchecked")
    Map<String, Object> input(ExecutionSnapshot snapshot, ExecuteStandardApiRequest request) {
        try {
            return (Map<String, Object>)snapshot.getInputSchema().fromJson(request.getInput(), "");
        } catch (FieldSchemaViolation violation) {
            String field = violation.getPath().isEmpty() ? INPUT : INPUT + "." + violation.getPath();
            throw new StandardApiNotSentException(StandardApiNotSentCode.VALIDATION_ERROR,
                "标准 API 输入不满足请求字段: " + field + ", " + violation.getMessage(), List.of(field));
        }
    }
}
