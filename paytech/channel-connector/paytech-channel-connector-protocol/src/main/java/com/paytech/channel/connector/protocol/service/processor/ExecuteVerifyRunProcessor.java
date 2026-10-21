package com.paytech.channel.connector.protocol.service.processor;

import java.util.*;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.channel.connector.api.enums.VerifyRunStatus;
import com.paytech.channel.connector.api.enums.VerifyStepStatus;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationVerifyAccount;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationVerifyRun;
import com.paytech.channel.connector.protocol.core.dal.dataobject.IntegrationVerifyRunStep;
import com.paytech.channel.connector.protocol.domain.execution.*;
import com.paytech.channel.connector.protocol.domain.verify.VerifyAssertion;
import com.paytech.channel.connector.protocol.domain.verify.VerifyStep;
import com.paytech.channel.connector.protocol.domain.verify.VerifyStepResult;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationVerifyAccountRepository;
import com.paytech.channel.connector.protocol.domainservice.repository.IntegrationVerifyRunRepository;
import com.paytech.channel.connector.protocol.domainservice.service.IntegrationVerifyRunService;
import com.paytech.channel.connector.protocol.domainservice.validator.VerifyCaseValidator;
import com.paytech.channel.connector.protocol.domainservice.verify.VerifyAssertionEvaluator;
import com.paytech.channel.connector.protocol.domainservice.verify.VerifyTemplateRenderer;
import com.paytech.channel.connector.protocol.service.processor.request.ExecuteStandardApiRequest;
import com.paytech.channel.connector.protocol.service.processor.request.ExecuteVerifyRunRequest;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 执行一次已登记的沙箱验证运行（沙箱验证技术方案第 2、3.6、4、6 节），由补偿任务在事务外调用。
 *
 * <p>
 * 只执行一次：补偿任务第二次执行说明前一次中断，直接记 INTERRUPTED，不向沙箱重放。账号版本须与发起时一致。
 * 每步依次渲染输入模板、经正式执行入口 {@link ExecuteStandardApiProcessor} 执行（带验证账号与轨迹）并核对期望；
 * 某步未通过则后续步骤记 SKIPPED。全部步骤与结论在一个短本地事务中写入，事务内只访问本库。
 */
@Slf4j
@Component
public class ExecuteVerifyRunProcessor extends AbstractProcessor<ExecuteVerifyRunRequest, VerifyRunStatus> {

    static final int MAX_SUMMARY = 512;
    static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final VerifySupport verify;
    private final ExternalApiProcessorSupport support;
    private final IntegrationVerifyRunRepository runs;
    private final IntegrationVerifyAccountRepository accounts;
    private final IntegrationVerifyRunService runService;
    private final ExecuteStandardApiProcessor executor;
    private final VerifyTemplateRenderer renderer;
    private final VerifyAssertionEvaluator evaluator;
    private final TransactionTemplate write;

    /** 结果写入使用 READ_COMMITTED 短事务，15 秒上限。 */
    public ExecuteVerifyRunProcessor(VerifySupport verify, ExternalApiProcessorSupport support,
        IntegrationVerifyRunRepository runs, IntegrationVerifyAccountRepository accounts,
        IntegrationVerifyRunService runService, ExecuteStandardApiProcessor executor, VerifyTemplateRenderer renderer,
        VerifyAssertionEvaluator evaluator, PlatformTransactionManager manager) {
        this.verify = verify;
        this.support = support;
        this.runs = runs;
        this.accounts = accounts;
        this.runService = runService;
        this.executor = executor;
        this.renderer = renderer;
        this.evaluator = evaluator;
        this.write = new TransactionTemplate(manager);
        this.write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.write.setTimeout(15);
    }

    @Override
    protected VerifyRunStatus processRequest(ExecuteVerifyRunRequest request) {
        IntegrationVerifyRun run = runs.byId(request.getRunId());
        if (run == null || run.getRunStatus() != VerifyRunStatus.RUNNING) {
            return run == null ? null : run.getRunStatus();
        }
        if (request.getExecuteCount() > 1) {
            return finish(run, VerifyRunStatus.INTERRUPTED, Map.of(), List.of(), "执行进程中断，未向沙箱重放", List.of());
        }
        JsonNode snapshot = verify.tree(run.getCaseSnapshotJson());
        IntegrationVerifyAccount account = accounts.byId(snapshot.path("accountId").asLong());
        if (account == null || account.getVersion() != snapshot.path("accountVersion").asInt()) {
            return finish(run, VerifyRunStatus.FAILED, Map.of(), List.of(), "验证账号在运行发起后已被修改或删除", List.of());
        }
        return execute(run, snapshot, account);
    }

    /** 依次执行全部步骤并写入结果。 */
    VerifyRunStatus execute(IntegrationVerifyRun run, JsonNode snapshot, IntegrationVerifyAccount account) {
        String integrationCode = snapshot.path("integrationCode").asString();
        var material = new VerifyAccountMaterial(account.getChannelCode(), account.getChannelAccountId(),
            account.getEntityNo(), verify.map(account.getParameters()), verify.map(account.getCredentials()));
        var scope = new VerifyTemplateRenderer.Scope(Long.toString(run.getVerifyRunId()),
            VerifySupport.time(run.getStartTime()), verify.identity(account), new LinkedHashMap<>());
        var versions = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> endpoints = List.of();
        var rows = new ArrayList<IntegrationVerifyRunStep>();
        String failure = null;
        int stepNo = 0;
        for (VerifyStep step : VerifyCaseValidator.parse(snapshot.path("steps"))) {
            stepNo++;
            if (failure != null) {
                rows.add(row(run, stepNo, step, VerifyStepStatus.SKIPPED, null, null, null, null, null));
                continue;
            }
            long started = System.nanoTime();
            var trace = new ExecutionTrace();
            JsonNode input = null;
            VerifyStepResult result;
            try {
                input = renderer.render(step.getInput(), scope);
                result = run(step, integrationCode, input, new VerificationScope(material, trace));
            } catch (IllegalArgumentException invalidTemplate) {
                result = VerifyStepResult.failed("输入模板渲染失败：" + invalidTemplate.getMessage());
            }
            List<VerifyAssertion> assertions = evaluator.evaluate(step, result, trace.getReturnId());
            boolean passed = VerifyAssertionEvaluator.passed(assertions);
            if (!passed) {
                failure = summary(step, result, assertions);
            }
            if (trace.getConfigVersions() != null) {
                versions.put(step.getId(), trace.getConfigVersions());
                endpoints = trace.getEndpoints();
            }
            scope.steps().put(step.getId(), new VerifyTemplateRenderer.StepValues(input,
                result.getOutputs() == null ? NODES.objectNode() : result.getOutputs()));
            rows.add(row(run, stepNo, step, passed ? VerifyStepStatus.PASSED : VerifyStepStatus.FAILED, input,
                resultNode(result), verify.node(assertions), trace.getConfigVersions() == null ? null : trace.toMap(),
                (int) ((System.nanoTime() - started) / 1_000_000L)));
        }
        return finish(run, failure == null ? VerifyRunStatus.PASSED : VerifyRunStatus.FAILED, versions, endpoints,
            failure, rows);
    }

    /** 经正式执行入口执行一步；执行前的问题按 HTTP 状态与代码记录。 */
    VerifyStepResult run(VerifyStep step, String integrationCode, JsonNode input, VerificationScope scope) {
        try {
            ExecutionResult result = executor.process(
                new ExecuteStandardApiRequest(step.getStandardApiCode(), integrationCode, input, scope));
            return VerifyStepResult.executed(result.getExecuteStatus().getCode(), JsonValues.toJson(result.getOutputs()));
        } catch (SandboxRequiredException notSandbox) {
            return VerifyStepResult.failed(notSandbox.getMessage());
        } catch (StandardApiNotSentException notSent) {
            return VerifyStepResult.problem(notSent.getReason().getProblemStatus(), notSent.getCode(),
                notSent.getFields());
        } catch (FailException problem) {
            return VerifyStepResult.problem(status(problem.getErrorCode().getCode(), 400), problem.getCode(), List.of());
        } catch (ErrorException problem) {
            return VerifyStepResult.problem(status(problem.getErrorCode().getCode(), 500), problem.getCode(), List.of());
        } catch (RuntimeException unexpected) {
            log.warn("沙箱验证步骤执行异常 runStep={} type={}", step.getId(), unexpected.getClass().getSimpleName());
            return VerifyStepResult.failed("执行异常：" + unexpected.getClass().getSimpleName());
        }
    }

    /** 与原内部执行接口一致的 HTTP 状态：通用 NOT_FOUND 为 404，其余按异常类别取默认值（内部接口第 3.2 节）。 */
    static int status(String code, int fallback) {
        if (code.equals(CommonErrorCode.NOT_FOUND.getCode())) {
            return 404;
        }
        return fallback;
    }

    /** 实际结果的 JSON 投影。 */
    static JsonNode resultNode(VerifyStepResult result) {
        ObjectNode node = NODES.objectNode();
        if (result.getError() != null) {
            node.put("kind", "NOT_EXECUTED");
            node.put("error", result.getError());
        } else if (result.getProblemStatus() != null) {
            node.put("kind", "PROBLEM");
            node.put("status", result.getProblemStatus());
            node.put("code", result.getProblemCode());
            var fields = node.putArray("fields");
            result.getProblemFields().forEach(fields::add);
        } else {
            node.put("kind", "EXECUTED");
            node.put("executeStatus", result.getExecuteStatus());
            node.set("outputs", result.getOutputs());
        }
        return node;
    }

    /** 第一个未满足的核对，作为失败摘要。 */
    static String summary(VerifyStep step, VerifyStepResult result, List<VerifyAssertion> assertions) {
        VerifyAssertion first = assertions.stream().filter(item -> !item.passed()).findFirst().orElse(null);
        String text = first == null ? "步骤 " + step.getId() + " 未通过"
            : "步骤 " + step.getId() + "：" + first.target() + " 期望 " + first.match()
                + (first.expected() == null ? "" : " " + first.expected()) + "，实际 " + first.actual();
        return text.length() > MAX_SUMMARY ? text.substring(0, MAX_SUMMARY) : text;
    }

    IntegrationVerifyRunStep row(IntegrationVerifyRun run, int stepNo, VerifyStep step, VerifyStepStatus status,
        JsonNode input, JsonNode result, JsonNode assertions, Map<String, Object> trace, Integer elapsedMs) {
        return new IntegrationVerifyRunStep(support.nextId(), run.getVerifyRunId(), stepNo, step.getId(),
            step.getStandardApiCode(), status, verify.text(input), verify.text(result), verify.text(assertions),
            verify.text(trace), elapsedMs, support.now());
    }

    /** 一个短事务写入步骤与结论。 */
    VerifyRunStatus finish(IntegrationVerifyRun run, VerifyRunStatus status, Map<String, Object> versions,
        List<Map<String, Object>> endpoints, String failure, List<IntegrationVerifyRunStep> rows) {
        var now = support.now();
        write.executeWithoutResult(transaction -> runService.finish(run, status,
            versions.isEmpty() ? null : verify.text(versions), endpoints.isEmpty() ? null : verify.text(endpoints),
            failure, now, rows));
        log.info("沙箱验证运行结束 runId={} status={}", run.getVerifyRunId(), status.getCode());
        return status;
    }
}
