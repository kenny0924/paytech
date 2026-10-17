package com.paytech.channel.connector.integration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.channel.connector.api.enums.ChannelOperationOutcome;
import com.paytech.channel.connector.domain.ChannelOperationResult;
import com.paytech.channel.connector.domain.PlatformPayOutputs;
import com.paytech.platform.support.api.enums.StandardApiExecutionErrorCode;
import com.paytech.platform.support.api.standardapi.StandardApiCommandApi;
import com.paytech.platform.support.api.standardapi.request.ExecuteStandardApiRequestBody;
import com.paytech.platform.support.api.standardapi.response.ExecuteStandardApiResponseBody;

import lombok.AllArgsConstructor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Platform 标准 API 内部执行客户端（2026-10-13 标准 API 与流程决定）：在数据库事务外调用，不自动重试。
 *
 * <p>
 * 200 的 executeStatus 原样映射，输出按 PAY 合同解释为业务字段（{@link PlatformPayOutputs}，D06）；只有 Platform 合同保证
 * “未发出渠道请求”的 Problem（标准 API 或实现不存在、配置无效、初始化失败、预算耗尽、输入 422）视为 NOT_SENT，其余失败（超时、网络、
 * 未知 5xx、无效响应）一律结果未知。日志与异常不带请求或响应报文。
 */
@Component
@AllArgsConstructor
public class PlatformStandardApiClient {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> NOT_SENT_CODES = Set.of(StandardApiExecutionErrorCode.STANDARD_API_NOT_FOUND.getCode(),
        StandardApiExecutionErrorCode.INTEGRATION_IMPLEMENTATION_NOT_FOUND.getCode(),
        StandardApiExecutionErrorCode.INTEGRATION_CONFIG_INVALID.getCode(),
        StandardApiExecutionErrorCode.INTEGRATION_INITIALIZATION_FAILED.getCode(),
        StandardApiExecutionErrorCode.INTEGRATION_BUDGET_EXHAUSTED.getCode(), CommonErrorCode.VALIDATION_ERROR.getCode());

    private final StandardApiCommandApi standardApiCommandApi;

    /**
     * 执行标准 API。
     *
     * @param standardApiCode 标准 API 编码
     * @param integrationCode Platform 接入方编码
     * @param input 标准 API 输入，不含卡值与账号材料
     * @return 已映射的执行结论
     */
    public ChannelOperationResult execute(String standardApiCode, String integrationCode, Map<String, Object> input) {
        ExecuteStandardApiResponseBody body;
        try {
            body = standardApiCommandApi.executeStandardApi(standardApiCode,
                new ExecuteStandardApiRequestBody(integrationCode, JSON.valueToTree(input)));
        } catch (RuntimeException failure) {
            String code = problemCode(failure);
            return NOT_SENT_CODES.contains(code) ? ChannelOperationResult.notSent(code) : ChannelOperationResult.unknown();
        }
        return result(body);
    }

    /** 200 响应：未知的 executeStatus 视为结果未知，不猜测成功或失败。 */
    static ChannelOperationResult result(ExecuteStandardApiResponseBody body) {
        ChannelOperationOutcome outcome = switch (body == null ? "" : String.valueOf(body.getExecuteStatus())) {
            case "SUCCESS" -> ChannelOperationOutcome.SUCCESS;
            case "FAIL" -> ChannelOperationOutcome.FAIL;
            default -> ChannelOperationOutcome.ERROR;
        };
        if (outcome == ChannelOperationOutcome.ERROR) {
            return ChannelOperationResult.unknown();
        }
        return ChannelOperationResult.answered(outcome, PlatformPayOutputs.of(outputs(body.getOutputs())));
    }

    /** 输出对象转为普通映射；不是对象时视为空输出。 */
    @SuppressWarnings("unchecked")
    static Map<String, Object> outputs(JsonNode outputs) {
        if (outputs == null || !outputs.isObject()) {
            return Map.of();
        }
        return new LinkedHashMap<>(JSON.convertValue(outputs, Map.class));
    }

    /** 沿异常链读取 Platform Problem 的 code；没有 HTTP 响应或无法解析时为 null。 */
    static String problemCode(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof RestClientResponseException response) {
                try {
                    JsonNode problem = JSON.readTree(response.getResponseBodyAsString());
                    return problem.path("code").isString() ? problem.path("code").asString() : null;
                } catch (JacksonException invalid) {
                    return null;
                }
            }
            if (current.getCause() == current) {
                return null;
            }
        }
        return null;
    }
}
