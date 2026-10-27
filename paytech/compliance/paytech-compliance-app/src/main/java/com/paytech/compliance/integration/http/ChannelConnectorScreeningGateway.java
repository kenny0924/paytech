package com.paytech.compliance.integration.http;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.paytech.channel.connector.api.serviceexecution.ServiceChannelExecutionApi;
import com.paytech.channel.connector.api.serviceexecution.request.ExecuteServiceChannelRequestBody;
import com.paytech.channel.connector.api.serviceexecution.response.ServiceChannelExecutionResponseBody;
import com.paytech.compliance.domain.ScreeningSearchCommand;
import com.paytech.compliance.domain.ScreeningSearchResult;
import com.paytech.compliance.integration.ScreeningSearchGateway;
import com.paytech.foundation.common.error.FailException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 经 channel-connector 通用执行入口调用 SCREENING_SEARCH（服务渠道）
 * <li>调用方固定为 compliance-service；不自动重试，重试由补偿任务按策略执行。</li>
 * <li>4xx Problem（参数、授权、渠道或标准 API 不存在）按 connector 合同保证未发出外部请求，归为 FAIL；其他异常、5xx 与 ERROR 一律结果未知。</li>
 * <li>输出不合法（缺少命中引用等）按结果未知处理，不虚构成功；命中名称、生日等原文不读取、不记录。</li>
 */
public class ChannelConnectorScreeningGateway implements ScreeningSearchGateway {
    /** 标准 API 编码。 */
    static final String STANDARD_API = "SCREENING_SEARCH";

    /** 调用方服务名。 */
    static final String CALLER = "compliance-service";

    private static final Set<String> ENTITY_TYPES = Set.of("PERSON", "ORGANIZATION", "VESSEL", "OTHER");

    private static final Set<String> RISK_LEVELS = Set.of("LOW", "MEDIUM", "HIGH", "UNKNOWN");

    private final ServiceChannelExecutionApi api;
    private final JsonMapper jsonMapper;

    /** 注入由 HttpServiceClientFactory 创建的 connector 客户端与 JSON。 */
    public ChannelConnectorScreeningGateway(ServiceChannelExecutionApi api, JsonMapper jsonMapper) {
        this.api = api;
        this.jsonMapper = jsonMapper;
    }

    /** 执行并规范化结果；不抛出。 */
    @Override
    public ScreeningSearchResult search(ScreeningSearchCommand command) {
        ServiceChannelExecutionResponseBody response;
        try {
            response = api.execute(CALLER, request(command));
        } catch (FailException notExecuted) {
            return new ScreeningSearchResult(ScreeningSearchResult.FAIL, null, List.of());
        } catch (RuntimeException unknown) {
            return new ScreeningSearchResult(ScreeningSearchResult.UNKNOWN, null, List.of());
        }
        if (response == null || !"SUCCESS".equals(response.getExecuteStatus())) {
            boolean failed = response != null && "FAIL".equals(response.getExecuteStatus());
            return new ScreeningSearchResult(failed ? ScreeningSearchResult.FAIL : ScreeningSearchResult.UNKNOWN, null, List.of());
        }
        return outputs(response.getOutputs());
    }

    /** 标准 API 输入。 */
    ExecuteServiceChannelRequestBody request(ScreeningSearchCommand command) {
        ObjectNode input = jsonMapper.createObjectNode();
        input.put("subjectType", command.getSubject().getSubjectType());
        input.put("fullName", command.getSubject().getFullName());
        if (command.getSubject().getBirthYear() != null) {
            input.put("birthYear", command.getSubject().getBirthYear());
        }
        ArrayNode countries = input.putArray("countries");
        command.getSubject().getCountries().forEach(countries::add);
        input.putArray("screeningTypes").add(command.getScreeningType());
        input.put("clientRef", command.getClientRef());
        ExecuteServiceChannelRequestBody body = new ExecuteServiceChannelRequestBody();
        body.setChannelCode(command.getChannelCode());
        body.setStandardApiCode(STANDARD_API);
        body.setInput(input);
        return body;
    }

    /** 规范化成功输出；结构不合法时结果未知。 */
    static ScreeningSearchResult outputs(JsonNode outputs) {
        if (outputs == null || !outputs.isObject()) {
            return new ScreeningSearchResult(ScreeningSearchResult.UNKNOWN, null, List.of());
        }
        JsonNode hits = outputs.get("hits");
        if (hits != null && !hits.isNull() && !hits.isArray()) {
            return new ScreeningSearchResult(ScreeningSearchResult.UNKNOWN, null, List.of());
        }
        List<ScreeningSearchResult.Match> matches = new ArrayList<>();
        if (hits != null && hits.isArray()) {
            for (JsonNode hit : hits) {
                ScreeningSearchResult.Match match = match(hit);
                if (match == null) {
                    return new ScreeningSearchResult(ScreeningSearchResult.UNKNOWN, null, List.of());
                }
                matches.add(match);
            }
        }
        return new ScreeningSearchResult(ScreeningSearchResult.SUCCESS, text(outputs, "runRef", 128), matches);
    }

    /** 一条命中；缺少或超长的外部引用、非法分数返回 null。 */
    static ScreeningSearchResult.Match match(JsonNode hit) {
        String ref = text(hit, "externalRef", 128);
        BigDecimal score = score(hit.get("score"));
        if (ref == null || ref.isBlank() || score == null) {
            return null;
        }
        String entityType = text(hit, "entityType", 16);
        String riskLevel = text(hit, "riskLevel", 16);
        List<String> listTypes = new ArrayList<>();
        JsonNode lists = hit.get("listTypes");
        if (lists != null && lists.isArray()) {
            lists.forEach(item -> listTypes.add(item.asString().length() > 32 ? item.asString().substring(0, 32) : item.asString()));
        }
        JsonNode matchTypes = hit.get("matchTypes");
        String matchType = matchTypes != null && matchTypes.isArray() && !matchTypes.isEmpty()
            ? truncate(matchTypes.get(0).asString(), 64) : "UNKNOWN";
        return new ScreeningSearchResult.Match(ref, ENTITY_TYPES.contains(entityType) ? entityType : "OTHER", listTypes, score,
            matchType, RISK_LEVELS.contains(riskLevel) ? riskLevel : "UNKNOWN");
    }

    /** 0—100 的匹配分数；接受数字或十进制字符串。 */
    static BigDecimal score(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(node.asString());
            return value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0 ? null : value;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    /** 可空文本字段；超长截断。 */
    static String text(JsonNode node, String field, int max) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : truncate(value.asString(), max);
    }

    /** 截断到上限。 */
    static String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }
}
