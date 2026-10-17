package com.paytech.platform.support.test.builder;

import static com.paytech.platform.support.api.enums.ApiFieldDataType.OBJECT;
import static com.paytech.platform.support.api.enums.ApiFieldDataType.STRING;
import static com.paytech.platform.support.api.enums.ApiFieldLocation.*;

import java.util.List;

import com.paytech.platform.support.api.enums.ApiFieldDataType;
import com.paytech.platform.support.api.enums.ApiFieldLocation;
import com.paytech.platform.support.api.externalapi.request.ApiFieldBody;
import com.paytech.platform.support.api.externalapi.request.IntegrationEndpointBody;
import com.paytech.platform.support.api.externalapi.request.ScriptParameterBody;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 外部 API 配置的合成样例：一个支付标准 API、一个 DEMO 接入方及其下单原子 API、签名脚本与流程。
 *
 * <p>只构造数据，不访问数据库；每次返回新对象，用例可以在副本上制造负例而不影响其他用例。
 * 字段、定义与流程按合同第 8、10 节的形状合成，不代表真实渠道协议。
 */
public final class ExternalApiSamples {

    /** 样例出站地址代码。 */
    public static final String ENDPOINT = "DEMO_GATEWAY";

    /** 样例下单原子 API 编码。 */
    public static final String CREATE_ORDER = "CREATE_ORDER";

    /** 样例签名脚本编码。 */
    public static final String SIGN_SCRIPT = "DEMO_SIGN";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ExternalApiSamples() {
    }

    /** 标准 API 请求字段：支付上下文对象及其流水号、金额与 Vault Token。 */
    public static List<ApiFieldBody> standardRequestFields() {
        return List.of(field(BODY, "paymentContext", OBJECT, true),
            field(BODY, "paymentContext.attemptId", STRING, true),
            field(BODY, "paymentContext.amount", STRING, true),
            field(BODY, "paymentContext.vaultToken", STRING, true));
    }

    /** 标准 API 响应字段：必填支付状态与可选渠道流水号。 */
    public static List<ApiFieldBody> standardResponseFields() {
        return List.of(field(BODY, "paymentStatus", STRING, true),
            field(BODY, "transactionRef", STRING, false));
    }

    /** 接入方唯一出站地址。 */
    public static List<IntegrationEndpointBody> endpoints() {
        return List.of(new IntegrationEndpointBody(ENDPOINT, "https://gateway.example.test/api"));
    }

    /** 下单原子 API 请求字段：路径门店、固定版本头 X-Api-Version、订单号与金额；Content-Type 由请求编码补默认值。 */
    public static List<ApiFieldBody> createOrderRequestFields() {
        return List.of(field(PATH, "outletRef", STRING, true),
            field(HEADER, "X-Api-Version", STRING, true),
            field(BODY, "orderRef", STRING, true),
            field(BODY, "amount", STRING, true));
    }

    /** 下单原子 API 响应字段。 */
    public static List<ApiFieldBody> createOrderResponseFields() {
        return List.of(field(BODY, "reference", STRING, true), field(BODY, "state", STRING, true));
    }

    /** 下单原子 API 定义：受控句柄参数、固定值、HTTP 传输、两条状态规则与分支输出，不引用脚本。 */
    public static ObjectNode createOrderDefinition() {
        return ExternalApiFixtures.definition("create-order");
    }

    /** 在定义中引用签名脚本：REQUEST 阶段，以 params.signingKey 绑定脚本参数 key。 */
    public static ObjectNode createOrderDefinitionWithScript(String scriptCode) {
        ObjectNode definition = createOrderDefinition();
        ((ArrayNode) definition.get("scripts")).add(object("""
            {"id":"signRequest","scriptCode":"%s","stage":"REQUEST","params":{"key":{"kind":"PATH","path":"params.signingKey"}}}
            """.formatted(scriptCode)));
        return definition;
    }

    /** 签名脚本参数：一个必填受控句柄。 */
    public static List<ScriptParameterBody> signParameters() {
        return List.of(new ScriptParameterBody("key", "PROTECTED_HANDLE", true));
    }

    /** 签名脚本源码：用受控句柄对订单号做 HMAC-SHA256，写入 signature Header。 */
    public static String signSource() {
        return ExternalApiFixtures.script("sign");
    }

    /**
     * 支付流程：初始化渠道账号 → 下单节点 → 成功 / 拒绝 / 未知三个出口。
     *
     * <p>下单节点读取初始化结果与标准 API 输入，签名密钥以受控句柄 PATH 绑定；成功出口读取必经节点输出。
     */
    public static ObjectNode payFlow() {
        return ExternalApiFixtures.flow("pay");
    }

    /** 构造字段，显示名称同路径、说明为空。 */
    public static ApiFieldBody field(ApiFieldLocation location, String path, ApiFieldDataType type, boolean required) {
        return new ApiFieldBody(location.getCode(), path, path, type.getCode(), required, null);
    }

    /** 解析 JSON 对象文本。 */
    public static ObjectNode object(String text) {
        return (ObjectNode) JSON.readTree(text);
    }

    /** 序列化 JSON，供本地 Mocker 写入定义列。 */
    public static String text(JsonNode node) {
        return JSON.writeValueAsString(node);
    }
}
