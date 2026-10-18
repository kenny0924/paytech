package com.paytech.platform.support.integration.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 当前已确认的注册函数签名目录（研发计划第 4 节）；函数实现在第 3 步执行引擎中完成。
 *
 * <p>CHANNEL_ACCOUNT_CONFIG 签名见材料交接第 4 节；PAYMENT_CARD 凭 card 签发的一次性读卡票据（参数名沿用 paymentInstrumentToken）向 card-service 读取卡数据（design/05 §2.1），
 * 见 PayerMax 卡直付对接技术方案第 3.1 节（requirements/20261014/payment-PayerMax卡直付对接）；MONEY_MINOR_UNITS 按币种精度把主单位金额精确换算为最小单位整数。
 * 脚本函数（研发计划第 3 步第 5 节）只能在公共脚本中调用：认证函数返回受控句柄，由传输层发送时取出明文。
 * 签名字节锁定、RSA 与协议时间见 PayerMax 接入技术方案第 3 节（requirements/20261014/platform-support-PayerMax接入）。
 */
public final class RegisteredFunctionCatalog {

    /** 初始化函数类别。 */
    public static final String INITIALIZER = "INITIALIZER";

    /** 纯函数类别。 */
    public static final String PURE = "PURE";

    /** 公共脚本函数类别。 */
    public static final String SCRIPT = "SCRIPT";

    private static final String HANDLE = "PROTECTED_HANDLE";

    /** 卡值句柄返回类型：只能以 PATH 赋给原子 API cardFields 声明的卡字段。 */
    public static final String CARD = "CARD";

    /** 全部已注册函数。 */
    public static final List<RegisteredFunction> FUNCTIONS = List.of(
        new RegisteredFunction("CHANNEL_ACCOUNT_CONFIG", INITIALIZER,
            List.of(new ParameterDeclaration("vaultToken", "STRING", true),
                new ParameterDeclaration("channelCode", "STRING", true),
                new ParameterDeclaration("channelAccountId", "STRING", true),
                new ParameterDeclaration("entityNo", "STRING", true)),
            "OBJECT{parameters: STRING 映射, credentials: PROTECTED_HANDLE 映射}",
            Map.of("parameters", "STRING", "credentials", "PROTECTED_HANDLE")),
        new RegisteredFunction("PAYMENT_CARD", INITIALIZER,
            List.of(new ParameterDeclaration("paymentInstrumentToken", "STRING", true),
                new ParameterDeclaration("paymentAttemptId", "STRING", true)),
            "OBJECT{pan, expiryMonth, expiryYear, expiryYearShort, cardholderName, cvv: CARD（卡值句柄，只能赋给原子 API 的卡字段）}",
            Map.of("pan", CARD, "expiryMonth", CARD, "expiryYear", CARD, "expiryYearShort", CARD, "cardholderName", CARD,
                "cvv", CARD)),
        new RegisteredFunction("MONEY_MINOR_UNITS", PURE,
            List.of(new ParameterDeclaration("amount", "STRING", true), new ParameterDeclaration("currency", "STRING", true)),
            "INTEGER", Map.of()),
        new RegisteredFunction("MONEY_MAJOR_UNITS", PURE,
            List.of(new ParameterDeclaration("amount", "STRING", true), new ParameterDeclaration("currency", "STRING", true)),
            "DECIMAL（主单位，小数位数等于币种精度）", Map.of()),
        script("bearerAuth", "PROTECTED_HANDLE（Bearer 认证 Header 值）", new ParameterDeclaration("token", HANDLE, true)),
        script("basicAuth", "PROTECTED_HANDLE（Basic 认证 Header 值）", new ParameterDeclaration("username", HANDLE, true),
            new ParameterDeclaration("password", HANDLE, true)),
        script("hmacSha256Hex", "STRING（小写十六进制）", new ParameterDeclaration("key", HANDLE, true),
            new ParameterDeclaration("message", "STRING", true)),
        script("hmacSha256Base64", "STRING（Base64）", new ParameterDeclaration("key", HANDLE, true),
            new ParameterDeclaration("message", "STRING", true)),
        script("sha256Hex", "STRING（小写十六进制）", new ParameterDeclaration("message", "STRING", true)),
        script("base64Encode", "STRING（UTF-8 字节的 Base64）", new ParameterDeclaration("value", "STRING", true)),
        script("json.get", "按点分路径读取的值，不存在为 nil", new ParameterDeclaration("target", "OBJECT", true),
            new ParameterDeclaration("path", "STRING", true)),
        script("json.set", "OBJECT（写入后的 target）", new ParameterDeclaration("target", "OBJECT", true),
            new ParameterDeclaration("path", "STRING", true), new ParameterDeclaration("value", "ANY", true)),
        script("sealJsonBody", "STRING（锁定的请求体文本，即实际发送的字节；仅 JSON 编码的 REQUEST 阶段，锁定后请求体只读；请求体含凭据字段时 ERROR）",
            new ParameterDeclaration("http", "OBJECT", true)),
        script("rsaSha256Sign", "STRING（SHA256withRSA 签名的 Base64）", new ParameterDeclaration("privateKey", HANDLE, true),
            new ParameterDeclaration("message", "STRING", true)),
        script("requireRsaSha256Signature", "BOOLEAN（true；签名缺失或不匹配时节点 ERROR）",
            new ParameterDeclaration("publicKey", "STRING", true), new ParameterDeclaration("message", "STRING", true),
            new ParameterDeclaration("signature", "STRING", true)),
        script("nowRfc3339", "STRING（当前 UTC 时间，yyyy-MM-dd'T'HH:mm:ss.SSS'Z'）"),
        script("urlEncode", "STRING（RFC 3986：A-Za-z0-9-._~ 以外按 UTF-8 百分号编码，大写十六进制）",
            new ParameterDeclaration("value", "STRING", true)),
        script("urlDecode", "STRING（百分号解码为 UTF-8；+ 保持原样；非法序列时节点 ERROR）",
            new ParameterDeclaration("value", "STRING", true)));

    private RegisteredFunctionCatalog() {
    }

    /** 函数可出现的位置：初始化函数只在流程初始化项；纯函数用于流程节点、出口与原子 API 输出取值；脚本函数只在公共脚本。 */
    public static List<String> usages(RegisteredFunction function) {
        return switch (function.getKind()) {
            case INITIALIZER -> List.of("FLOW_INITIALIZER");
            case SCRIPT -> List.of("INTEGRATION_SCRIPT");
            default -> List.of("FLOW_NODE", "FLOW_RETURN", "API_OUTPUT");
        };
    }

    /** 某一类别的函数编码。 */
    public static java.util.Set<String> codes(String kind) {
        return FUNCTIONS.stream().filter(function -> function.getKind().equals(kind)).map(RegisteredFunction::getCode)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** 脚本函数：无返回属性。 */
    private static RegisteredFunction script(String code, String returns, ParameterDeclaration... args) {
        return new RegisteredFunction(code, SCRIPT, List.of(args), returns, Map.of());
    }

    /** 按编码查找注册函数。 */
    public static Optional<RegisteredFunction> find(String code) {
        return FUNCTIONS.stream().filter(function -> function.getCode().equals(code)).findFirst();
    }
}
