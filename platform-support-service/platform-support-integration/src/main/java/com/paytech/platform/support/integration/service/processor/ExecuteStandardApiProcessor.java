package com.paytech.platform.support.integration.service.processor;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.api.enums.StandardApiExecutionErrorCode;
import com.paytech.platform.support.integration.domain.execution.*;
import com.paytech.platform.support.integration.domainservice.execution.AtomicApiExecutor;
import com.paytech.platform.support.integration.domainservice.execution.FlowEngine;
import com.paytech.platform.support.integration.domainservice.execution.InitializerRunner;
import com.paytech.platform.support.integration.service.processor.request.ExecuteStandardApiRequest;

import lombok.AllArgsConstructor;

/**
 * 标准 API 内部执行（内部接口第 1～3 节）：不加锁、不写库，只有加载快照的一次短只读事务。
 *
 * <p>
 * 第一个节点开始前的问题返回 Problem 并保证没有发出渠道请求：标准 API 或实现不存在 404， 配置未通过运行前校验 500，input 不满足标准 API 请求字段 422（errors[] 位置以 input. 开头），
 * 必需初始化项失败或预算已耗尽 503。第一个节点开始后由流程引擎兜底，任何失败都是 200 + ERROR。 初始化（Vault 读取）与渠道请求都在事务外执行，不重试。
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
        var context = new ExecutionContext(input(snapshot, request));
        Deadline deadline = Deadline.start(snapshot.getFlow().getTimeoutMs());
        initializerRunner.run(snapshot, context, deadline);
        if (deadline.expired()) {
            throw new ErrorException(StandardApiExecutionErrorCode.INTEGRATION_BUDGET_EXHAUSTED,
                "第一个节点开始前流程预算已耗尽: " + snapshot.getIntegrationCode() + ", 标准 API: " + snapshot.getStandardApiCode());
        }
        return flowEngine.run(snapshot, context, deadline, atomicApiExecutor);
    }

    /** 按标准 API 请求字段校验并转换 input；不满足时以 422 返回出错位置。 */
    @SuppressWarnings("unchecked")
    Map<String, Object> input(ExecutionSnapshot snapshot, ExecuteStandardApiRequest request) {
        try {
            return (Map<String, Object>)snapshot.getInputSchema().fromJson(request.getInput(), "");
        } catch (FieldSchemaViolation violation) {
            String field = violation.getPath().isEmpty() ? INPUT : INPUT + "." + violation.getPath();
            throw new RequestBodyValidationException(List.of(new ProblemFieldError(field, violation.getMessage())),
                null);
        }
    }
}
