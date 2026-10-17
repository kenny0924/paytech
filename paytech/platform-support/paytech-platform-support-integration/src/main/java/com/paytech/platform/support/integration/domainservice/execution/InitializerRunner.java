package com.paytech.platform.support.integration.domainservice.execution;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.api.enums.StandardApiExecutionErrorCode;
import com.paytech.platform.support.integration.domain.ParameterDeclaration;
import com.paytech.platform.support.integration.domain.RegisteredFunctionCatalog;
import com.paytech.platform.support.integration.domain.execution.Deadline;
import com.paytech.platform.support.integration.domain.execution.ExecutionContext;
import com.paytech.platform.support.integration.domain.execution.ExecutionFlow;
import com.paytech.platform.support.integration.domain.execution.ExecutionSnapshot;
import com.paytech.platform.support.integration.domainservice.function.ChannelAccountConfigFunction;
import com.paytech.platform.support.integration.domainservice.function.InitializerFunction;
import com.paytech.platform.support.integration.domainservice.function.PaymentCardFunction;
import com.paytech.platform.support.integration.domainservice.function.VerifyAccountConfigFunction;
import com.paytech.platform.support.integration.domainservice.function.VerifyPaymentCardFunction;
import com.paytech.platform.support.integration.domainservice.validator.ValueBindingValidator;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/**
 * 初始化项（流程结构第 3 节）：按 args 对 resources.* 的引用推导依赖，拓扑顺序串行执行，结果写入 resources.&lt;id&gt;。
 *
 * <p>
 * 发生在第一个节点之前：required=true 的初始化项失败返回 503 INTEGRATION_INITIALIZATION_FAILED， 预算在执行前已耗尽返回 503
 * INTEGRATION_BUDGET_EXHAUSTED，均保证没有发出渠道请求； required=false 失败时 resources.&lt;id&gt; 不存在。日志只记初始化项 ID 与固定原因。
 * 沙箱验证运行中 CHANNEL_ACCOUNT_CONFIG 改读验证账号、PAYMENT_CARD 改读验证账号测试卡，并在轨迹中记录每个初始化项的成败（不记内容）。
 */
@Slf4j
@Component
public class InitializerRunner {

    private static final String RESOURCES_PREFIX = ExecutionContext.RESOURCES + ".";

    private final ValueResolver valueResolver;
    private final ValueBindingValidator bindings;
    private final Map<String, InitializerFunction> functions;
    private final VerifyAccountConfigFunction verifyAccount;
    private final VerifyPaymentCardFunction verifyCard;

    public InitializerRunner(ValueResolver valueResolver, ValueBindingValidator bindings,
        List<InitializerFunction> functions, VerifyAccountConfigFunction verifyAccount,
        VerifyPaymentCardFunction verifyCard) {
        this.valueResolver = valueResolver;
        this.bindings = bindings;
        this.verifyAccount = verifyAccount;
        this.verifyCard = verifyCard;
        this.functions =
            functions.stream().collect(Collectors.toUnmodifiableMap(InitializerFunction::code, Function.identity()));
    }

    /**
     * 执行全部初始化项。
     *
     * @param snapshot
     *            本次执行快照
     * @param context
     *            已写入 input 的上下文，结果写入 resources
     * @param deadline
     *            流程预算
     */
    public void run(ExecutionSnapshot snapshot, ExecutionContext context, Deadline deadline) {
        for (ExecutionFlow.Initializer initializer : order(snapshot.getFlow().getInitializers())) {
            if (deadline.expired()) {
                throw new ErrorException(StandardApiExecutionErrorCode.INTEGRATION_BUDGET_EXHAUSTED,
                    "初始化前流程预算已耗尽: " + snapshot.getIntegrationCode() + ", 初始化项: " + initializer.getId());
            }
            try {
                context.putResource(initializer.getId(), execute(initializer, context));
                if (context.trace() != null) {
                    context.trace().initializer(initializer.getId(), initializer.getFunction(), true, null);
                }
            } catch (RuntimeException failure) {
                if (context.trace() != null) {
                    context.trace().initializer(initializer.getId(), initializer.getFunction(), false,
                        FlowEngine.reason(failure));
                }
                log.warn("外部 API 初始化项失败 standardApiCode={} integrationCode={} initializerId={} required={} reason={}",
                    snapshot.getStandardApiCode(), snapshot.getIntegrationCode(), initializer.getId(),
                    initializer.isRequired(), FlowEngine.reason(failure));
                if (initializer.isRequired()) {
                    throw new ErrorException(StandardApiExecutionErrorCode.INTEGRATION_INITIALIZATION_FAILED,
                        "必需初始化项失败: " + snapshot.getIntegrationCode() + ", 初始化项: " + initializer.getId());
                }
            }
        }
    }

    /** 解析参数并调用初始化函数。 */
    Map<String, Object> execute(ExecutionFlow.Initializer initializer, ExecutionContext context) {
        var signature = RegisteredFunctionCatalog.find(initializer.getFunction()).orElseThrow();
        InitializerFunction function = functions.get(initializer.getFunction());
        if (function == null) {
            throw new IllegalStateException("初始化函数未实现");
        }
        var args = args(signature.getArgs(), initializer, context);
        if (context.getVerification() != null && ChannelAccountConfigFunction.CODE.equals(initializer.getFunction())) {
            return verifyAccount.apply(args, context.getVerification().getAccount());
        }
        if (context.getVerification() != null && PaymentCardFunction.CODE.equals(initializer.getFunction())) {
            return verifyCard.apply(context.getVerification().getAccount());
        }
        return function.apply(args);
    }

    /** 按签名解析初始化参数；必填缺失即失败。 */
    Map<String, Object> args(List<ParameterDeclaration> declarations, ExecutionFlow.Initializer initializer,
        ExecutionContext context) {
        var environment = context.environment();
        var args = new LinkedHashMap<String, Object>();
        for (ParameterDeclaration parameter : declarations) {
            JsonNode binding = initializer.getArgs().get(parameter.getName());
            String location = "initializers." + initializer.getId() + ".args." + parameter.getName();
            Object value = binding == null ? null : valueResolver.resolve(binding, environment, parameter.getDataType(),
                parameter.isRequired(), location);
            if (binding == null && parameter.isRequired()) {
                throw new IllegalStateException("缺少必填初始化参数：" + location);
            }
            if (value != null) {
                args.put(parameter.getName(), value);
            }
        }
        return args;
    }

    /** 依赖先于引用方，其余保持配置顺序；流程已通过无环校验。 */
    List<ExecutionFlow.Initializer> order(List<ExecutionFlow.Initializer> initializers) {
        Map<String, ExecutionFlow.Initializer> pending = new LinkedHashMap<>();
        initializers.forEach(item -> pending.put(item.getId(), item));
        var ordered = new ArrayList<ExecutionFlow.Initializer>();
        Set<String> done = new LinkedHashSet<>();
        while (!pending.isEmpty()) {
            var ready =
                pending.values().stream().filter(item -> done.containsAll(dependencies(item, pending.keySet(), done)))
                    .findFirst().orElseThrow(() -> new IllegalStateException("初始化项依赖成环"));
            ordered.add(ready);
            done.add(ready.getId());
            pending.remove(ready.getId());
        }
        return ordered;
    }

    /** args 中引用的其他初始化项。 */
    Set<String> dependencies(ExecutionFlow.Initializer initializer, Set<String> pending, Set<String> done) {
        var result = new LinkedHashSet<String>();
        for (JsonNode binding : initializer.getArgs()) {
            references(binding).stream().filter(path -> path.startsWith(RESOURCES_PREFIX))
                .map(path -> path.substring(RESOURCES_PREFIX.length()).split("[.\\[]")[0])
                .filter(id -> pending.contains(id) || done.contains(id)).forEach(result::add);
        }
        return result;
    }

    /** 取值绑定中出现的上下文引用（含 FUNCTION 参数）。 */
    List<String> references(JsonNode binding) {
        var result = new ArrayList<String>();
        switch (binding.path("kind").asString("")) {
            case "PATH" -> result.add(binding.path("path").asString(""));
            case "EXPRESSION" -> result.addAll(bindings.references(binding.path("expression").asString("")));
            case "FUNCTION" -> binding.path("args").forEach(argument -> result.addAll(references(argument)));
            default -> {
            }
        }
        return result;
    }
}
