package com.paytech.channel.connector.test.builder;

import static com.paytech.channel.connector.api.enums.ApiFieldDataType.OBJECT;
import static com.paytech.channel.connector.api.enums.ApiFieldDataType.STRING;
import static com.paytech.channel.connector.api.enums.ApiFieldLocation.BODY;

import java.util.List;

import com.paytech.channel.connector.api.externalapi.request.ApiFieldBody;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 服务渠道执行测试的协议配置样例：由已验证的下单样例（create-order 定义、call-create-order 流程）派生
 * <li>LOOKUP：无初始化项、无凭据，按 input.paymentContext 下单，出口与样例相同。</li>
 * <li>LOOKUP_SECURE：初始化项 CHANNEL_ACCOUNT_CONFIG 只从本服务注入的 input.channelAccount 读取账号身份与 Vault 引用（不读 entityNo），
 * 与样例同样把账号参数 outletId 放入路径、凭据 apiKey 作为签名参数。</li>
 */
public final class ServiceChannelSamples {
    /** 无凭据的标准 API。 */
    public static final String LOOKUP = "LOOKUP";

    /** 需要账号凭据的标准 API。 */
    public static final String LOOKUP_SECURE = "LOOKUP_SECURE";

    /** 无凭据的原子 API。 */
    public static final String LOOKUP_CALL = "LOOKUP_CALL";

    /** 需要账号凭据的原子 API（即样例下单 API）。 */
    public static final String SECURE_CALL = "SECURE_CALL";

    /** 无凭据原子 API 的渠道路径。 */
    public static final String LOOKUP_PATH = "/api/lookup";

    /** 工具类不实例化。 */
    private ServiceChannelSamples() {
    }

    /** LOOKUP 的请求字段：paymentContext.attemptId、amount。 */
    public static List<ApiFieldBody> lookupRequestFields() {
        return List.of(ExternalApiSamples.field(BODY, "paymentContext", OBJECT, true),
            ExternalApiSamples.field(BODY, "paymentContext.attemptId", STRING, true),
            ExternalApiSamples.field(BODY, "paymentContext.amount", STRING, true));
    }

    /** LOOKUP_SECURE 的请求字段：在 LOOKUP 之上要求本服务注入的 channelAccount。 */
    public static List<ApiFieldBody> secureRequestFields() {
        return List.of(ExternalApiSamples.field(BODY, "paymentContext", OBJECT, true),
            ExternalApiSamples.field(BODY, "paymentContext.attemptId", STRING, true),
            ExternalApiSamples.field(BODY, "paymentContext.amount", STRING, true),
            ExternalApiSamples.field(BODY, "channelAccount", OBJECT, true),
            ExternalApiSamples.field(BODY, "channelAccount.channelCode", STRING, true),
            ExternalApiSamples.field(BODY, "channelAccount.channelAccountId", STRING, true),
            ExternalApiSamples.field(BODY, "channelAccount.accountVaultToken", STRING, true));
    }

    /** 无凭据原子 API 的请求字段：BODY orderRef、amount。 */
    public static List<ApiFieldBody> lookupApiRequestFields() {
        return List.of(ExternalApiSamples.field(BODY, "orderRef", STRING, true),
            ExternalApiSamples.field(BODY, "amount", STRING, true));
    }

    /** 无凭据原子 API 定义：下单样例去掉签名参数与固定 Header，路径为 /lookup。 */
    public static ObjectNode lookupDefinition() {
        ObjectNode definition = ExternalApiSamples.createOrderDefinition();
        definition.set("params", JsonNodeFactory.instance.arrayNode());
        definition.set("fixedValues", JsonNodeFactory.instance.objectNode());
        ((ObjectNode)definition.get("transport")).put("pathTemplate", "/lookup");
        return definition;
    }

    /** 无凭据流程：调用 LOOKUP_CALL，SUCCESS 出口输出 PAID 与交易编号。 */
    public static ObjectNode lookupFlow() {
        ObjectNode flow = ExternalApiFixtures.flow("call-create-order");
        flow.set("initializers", JsonNodeFactory.instance.arrayNode());
        ObjectNode node = (ObjectNode)flow.get("nodes").get(0);
        node.put("integrationApi", LOOKUP_CALL);
        ((ObjectNode)node.get("request")).remove("PATH:outletRef");
        node.set("params", JsonNodeFactory.instance.objectNode());
        return flow;
    }

    /** 需要凭据的流程：初始化项从 input.channelAccount 读取账号身份与 Vault 引用，不传 entityNo。 */
    public static ObjectNode secureFlow() {
        ObjectNode flow = ExternalApiFixtures.flow("call-create-order");
        ObjectNode args = (ObjectNode)flow.get("initializers").get(0).get("args");
        args.set("vaultToken", path("input.channelAccount.accountVaultToken"));
        args.set("channelCode", path("input.channelAccount.channelCode"));
        args.set("channelAccountId", path("input.channelAccount.channelAccountId"));
        args.remove("entityNo");
        ((ObjectNode)flow.get("nodes").get(0)).put("integrationApi", SECURE_CALL);
        return flow;
    }

    /** 下单样例渠道成功响应。 */
    public static String captured(String reference) {
        return """
            {"reference":"%s","state":"CAPTURED"}""".formatted(reference);
    }

    /** 调用方输入：paymentContext 的尝试编号与金额。 */
    public static ObjectNode input(String attemptId) {
        ObjectNode input = JsonNodeFactory.instance.objectNode();
        input.putObject("paymentContext").put("attemptId", attemptId).put("amount", "10.00");
        return input;
    }

    /** PATH 绑定。 */
    static JsonNode path(String path) {
        return JsonNodeFactory.instance.objectNode().put("kind", "PATH").put("path", path);
    }
}
