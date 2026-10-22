package com.paytech.channel.connector.service.processor;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import com.paytech.channel.connector.api.enums.ChannelOperation;
import com.paytech.channel.connector.api.enums.ChannelOperationOutcome;
import com.paytech.channel.connector.core.configuration.ChannelProperties;
import com.paytech.channel.connector.domain.ChannelExecutionContext;
import com.paytech.channel.connector.domain.ChannelOperationResult;
import com.paytech.channel.connector.domain.PayOutputs;
import com.paytech.channel.connector.domain.PayQueryOutputs;
import com.paytech.channel.connector.domainservice.repository.*;
import com.paytech.channel.connector.protocol.domainservice.service.ChannelService;
import com.paytech.channel.connector.protocol.domain.execution.ExecutionResult;
import com.paytech.channel.connector.protocol.domain.execution.JsonValues;
import com.paytech.channel.connector.protocol.core.enums.StandardApiNotSentCode;
import com.paytech.channel.connector.protocol.domain.execution.StandardApiNotSentException;
import com.paytech.channel.connector.protocol.service.processor.ExecuteStandardApiProcessor;
import com.paytech.channel.connector.protocol.service.processor.request.ExecuteStandardApiRequest;
import com.paytech.channel.connector.service.processor.request.ExecuteChannelOperationRequest;
import com.paytech.channel.connector.service.processor.request.GetChannelExecutionConfigRequest;
import com.paytech.foundation.common.error.BizCheckFailException;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 执行一次渠道操作（PayerMax 卡直付对接技术方案第 5.1 节；2026-10-20 统一对外出口第一段改为进程内执行）：按用途校验冻结的
 * Channel/Account/MID 后，把账号身份与 Vault 引用作为标准 API 输入，在事务外调用 protocol 的标准 API 执行入口。
 *
 * <p>
 * 渠道编码沿用渠道编码（channel_code，例如 PAYERMAX）；标准 API 编码取渠道操作对应的编码（PAY → PAY，原交易查单 QUERY → PAY_QUERY）。账号配置的 Vault Reveal 与读卡由
 * protocol 初始化函数以 channel-connector-service 身份完成，本类不接触明文。冻结坐标不可用、账号或 MID 的 OE 与请求不一致、账号未配置时
 * 不发出请求，返回 NOT_SENT；protocol 保证未发出的失败（{@link StandardApiNotSentException}）同为 NOT_SENT，其余失败一律结果未知。
 * 输出按操作合同解释为已映射结论（PAY 按 {@link PayOutputs}，QUERY 按 {@link PayQueryOutputs}，D06）。本入口无数据库写入，在调用方事务之外执行，不自动重试。
 *
 * <p>
 * 只接受支付渠道：冻结渠道为服务渠道时不发出请求，按“配置无效”返回 NOT_SENT（failureCode INTEGRATION_CONFIG_INVALID）。
 * 执行在支付渠道执行舱中进行；访问日志的调用方记为 Payment（{@value #PAYMENT_CALLER}）。
 */
@Slf4j
@Component
public class ExecuteChannelOperationProcessor
    extends AbstractChannelExecutionProcessor<ExecuteChannelOperationRequest, ChannelOperationResult> {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 支付入口在对外调用访问日志中的调用方；支付入口只供 Payment 调用。 */
    static final String PAYMENT_CALLER = "payment-service";

    private final ExecuteStandardApiProcessor executeStandardApiProcessor;

    /** 注入冻结坐标校验与 protocol 标准 API 执行入口。 */
    public ExecuteChannelOperationProcessor(ChannelProperties properties, ChannelMidRepository mids,
        MidCapabilityRepository capabilities, ChannelAccountRepository accounts, ChannelService channels,
        PlatformTransactionManager transactionManager, MidCapabilityDetailRepository details,
        ExecuteStandardApiProcessor executeStandardApiProcessor) {
        super(properties, mids, capabilities, accounts, channels, transactionManager, details);
        this.executeStandardApiProcessor = executeStandardApiProcessor;
    }

    /** 冻结坐标在请求的 target 中。 */
    @Override
    protected GetChannelExecutionConfigRequest target(ExecuteChannelOperationRequest request) {
        return request.getTarget();
    }

    /** 校验冻结坐标（本地一致读取）后在事务外执行标准 API；坐标不可用时不发出请求。 */
    @Override
    protected ChannelOperationResult processRequest(ExecuteChannelOperationRequest request) {
        if (serviceChannel(request.getTarget().getChannelId())) {
            return ChannelOperationResult.notSent(StandardApiNotSentCode.INTEGRATION_CONFIG_INVALID.getCode());
        }
        ChannelExecutionContext context;
        try {
            context = readChecked(request.getTarget());
        } catch (BizCheckFailException unavailable) {
            return ChannelOperationResult.notSent("CHANNEL_TARGET_UNAVAILABLE");
        }
        if (context.getAccount().getAccountVaultToken() == null) {
            return ChannelOperationResult.notSent("CHANNEL_ACCOUNT_NOT_CONFIGURED");
        }
        String integrationCode = context.getChannel().getChannelCode();
        ChannelOperation operation = request.getTarget().getOperation();
        return execute(operation, integrationCode, input(context, request));
    }

    /**
     * 进程内执行标准 API
     * <li>未发出类失败（标准 API 或实现不存在、配置无效、初始化失败、预算耗尽、输入不满足请求字段）映射为 NOT_SENT 并带原代码。</li>
     * <li>其余异常可能发生在渠道请求发出之后，一律按结果未知处理并只记录异常类型，不带请求或响应报文。</li>
     *
     * @param operation 渠道操作，决定标准 API 编码与输出合同
     * @param integrationCode 渠道编码（渠道编码）
     * @param input 标准 API 输入，含账号身份与 Vault 引用，不含卡值与账号明文
     * @return 已映射的执行结论
     */
    ChannelOperationResult execute(ChannelOperation operation, String integrationCode, Map<String, Object> input) {
        String standardApiCode = operation.getStandardApiCode();
        ExecutionResult result;
        try {
            result = executeStandardApiProcessor.process(
                new ExecuteStandardApiRequest(standardApiCode, integrationCode, JSON.valueToTree(input), PAYMENT_CALLER));
        } catch (StandardApiNotSentException notSent) {
            return ChannelOperationResult.notSent(notSent.getCode());
        } catch (RuntimeException failure) {
            log.warn("渠道操作执行结果未知 standardApiCode={} integrationCode={} type={}", standardApiCode,
                integrationCode, failure.getClass().getSimpleName());
            return ChannelOperationResult.unknown();
        }
        return result(operation, result);
    }

    /** 执行状态原样映射：ERROR 或无法识别的状态视为结果未知，不猜测成功或失败；查单按 PAY_QUERY 合同解释输出。 */
    static ChannelOperationResult result(ChannelOperation operation, ExecutionResult result) {
        ChannelOperationOutcome outcome = switch (result == null ? "" : result.getExecuteStatus().getCode()) {
            case "SUCCESS" -> ChannelOperationOutcome.SUCCESS;
            case "FAIL" -> ChannelOperationOutcome.FAIL;
            default -> ChannelOperationOutcome.ERROR;
        };
        if (outcome == ChannelOperationOutcome.ERROR) {
            return ChannelOperationResult.unknown();
        }
        Map<String, Object> values = outputs(JsonValues.toJson(result.getOutputs()));
        if (operation == ChannelOperation.QUERY) {
            return ChannelOperationResult.queried(outcome, PayQueryOutputs.of(values));
        }
        return ChannelOperationResult.answered(outcome, PayOutputs.of(values));
    }

    /** 输出按原内部接口的 JSON 口径（十进制写为字符串）转为普通映射；不是对象时视为空输出。 */
    @SuppressWarnings("unchecked")
    static Map<String, Object> outputs(JsonNode outputs) {
        if (outputs == null || !outputs.isObject()) {
            return Map.of();
        }
        return new LinkedHashMap<>(JSON.convertValue(outputs, Map.class));
    }

    /** 业务输入加上冻结账号的身份与 Vault 引用。 */
    static Map<String, Object> input(ChannelExecutionContext context, ExecuteChannelOperationRequest request) {
        var account = new LinkedHashMap<String, Object>();
        account.put("channelCode", context.getChannel().getChannelCode());
        account.put("channelAccountId", context.getAccount().getChannelAccountId().toString());
        account.put("entityNo", context.getAccount().getEntityNo());
        account.put("accountVaultToken", context.getAccount().getAccountVaultToken());
        var input = new LinkedHashMap<>(request.getInput());
        input.put("channelAccount", account);
        return input;
    }
}
