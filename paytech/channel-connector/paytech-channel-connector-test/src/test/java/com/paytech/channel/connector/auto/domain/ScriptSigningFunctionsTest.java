package com.paytech.channel.connector.auto.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.channel.connector.protocol.domain.RegisteredFunctionCatalog;
import com.paytech.channel.connector.protocol.domain.execution.ExecutionErrorException;
import com.paytech.channel.connector.protocol.domain.execution.JsonValues;
import com.paytech.channel.connector.protocol.domain.execution.ProtectedHandle;
import com.paytech.channel.connector.protocol.domainservice.execution.HttpWork;
import com.paytech.channel.connector.protocol.domainservice.expression.ExpressionEngine;
import com.paytech.channel.connector.protocol.domainservice.expression.ExpressionProfile;

import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * 公共脚本的签名字节锁定、RSA 签名与验签、协议时间（PayerMax 接入技术方案第 3 节）：直接以脚本剖面执行源码，
 * 覆盖锁定后只读、重复与不可锁定阶段、篡改与错误密钥、异常信息不含密钥。完整 HTTP 链路见 StandardApiSignedChannelTest。
 *
 * <p>
 * Cybersource HTTP Signature 所需的 sha256Base64、hmacSha256Base64DecodedKey 与 nowRfc1123（CYBS_NI 接入技术方案第 4 节）
 * 以对接包 test-vectors.json 的样本逐字节断言；样本密钥是本机随机生成的测试专用值（testOnly），与任何真实商户无关。
 *
 * @see ExpressionEngine#compile(ExpressionProfile, String)
 */
class ScriptSigningFunctionsTest {

    private static final KeyPair KEYS = keyPair();
    private static final KeyPair OTHER = keyPair();
    /** 测试专用 HMAC 密钥（Base64）。 */
    private static final String CYBS_SECRET_KEY = "v9EJBDh1IuYowEzGJ4Wr74fy/eFcxHs+tzby2ypVChc=";
    /** 样本请求体 {"a":1} 的 SHA-256 Base64。 */
    private static final String CYBS_DIGEST = "AVq9f1zFei3ZS3WQ8ErYCEJzkF7jPsXOvq5iJ2qX+GI=";
    /** 样本五行签名串。 */
    private static final String CYBS_SIGNING_STRING = "host: api.cybersource.com\ndate: Wed, 21 Oct 2026 08:30:00 GMT\n"
        + "request-target: post /pts/v2/payments\ndigest: SHA-256=" + CYBS_DIGEST + "\nv-c-merchant-id: testonly_merchant";
    private static final JsonMapper JSON =
        JsonMapper.builder().enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN).build();

    private final ExpressionEngine engine =
        new ExpressionEngine(Clock.fixed(Instant.parse("2026-10-14T07:29:00.123456Z"), ZoneOffset.UTC));

    /** 新函数登记在注册函数目录并对脚本剖面开放，只读剖面不可调用。 */
    @Test
    void test_catalog() {
        for (String name : List.of("sealJsonBody", "rsaSha256Sign", "requireRsaSha256Signature", "nowRfc3339", "urlEncode",
            "urlDecode", "sha256Base64", "hmacSha256Base64DecodedKey", "nowRfc1123")) {
            assertTrue(RegisteredFunctionCatalog.codes(RegisteredFunctionCatalog.SCRIPT).contains(name), name);
            assertTrue(ExpressionProfile.SCRIPT.getFunctions().contains(name), name);
            assertFalse(ExpressionProfile.READ_ONLY.getFunctions().contains(name), name);
        }
    }

    /** 锁定后签名覆盖的就是锁定字节；请求体随后只读，赋值与嵌套修改都失败，锁定字节不变。 */
    @Test
    void test_sealThenSign() {
        HttpWork http = request();
        run("http.requestHeaders.sign = rsaSha256Sign(params.key, sealJsonBody(http))", http);

        String sent = new String(http.sealedJsonBody(), StandardCharsets.UTF_8);
        assertAll(() -> assertEquals("{\"data\":{\"amount\":10.00}}", sent),
            () -> assertTrue(verify(sent, (String)headers(http).get("sign"))));
        for (String source : List.of("json.set(http.requestBody, 'x', 1)", "http.requestBody = 'x'",
            "sealJsonBody(http)")) {
            assertThrows(RuntimeException.class, () -> run(source, http), source);
        }
        assertEquals(sent, new String(http.sealedJsonBody(), StandardCharsets.UTF_8));
    }

    /** 非 JSON 编码与 RESPONSE 阶段不能锁定。 */
    @Test
    void test_sealUnavailable() {
        HttpWork form = HttpWork.request(Map.of(), new LinkedHashMap<>(Map.of("a", "1")), null);
        HttpWork response = HttpWork.response(request(), 200, Map.of(), Map.of(), "{}");

        assertThrows(RuntimeException.class, () -> run("sealJsonBody(http)", form));
        assertThrows(RuntimeException.class, () -> run("sealJsonBody(http)", response));
        assertNull(form.sealedJsonBody());
    }

    /** 验签：正确签名返回 true；篡改、其他私钥、缺失、格式错误的签名或公钥都失败，异常不含公钥或签名。 */
    @Test
    void test_requireSignature() {
        String signature = sign("{\"ok\":true}");
        String publicKey = encode(KEYS.getPublic().getEncoded());
        assertEquals(true, verifyScript(publicKey, "{\"ok\":true}", signature));

        var failures = List.<Object[]>of(new Object[] {publicKey, "{\"ok\":false}", signature},
            new Object[] {encode(OTHER.getPublic().getEncoded()), "{\"ok\":true}", signature},
            new Object[] {publicKey, "{\"ok\":true}", null}, new Object[] {publicKey, "{\"ok\":true}", ""},
            new Object[] {publicKey, "{\"ok\":true}", "not base64!"}, new Object[] {"bad-key", "{\"ok\":true}", signature});
        for (Object[] failure : failures) {
            RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> verifyScript((String)failure[0], (String)failure[1], (String)failure[2]));
            assertFalse(String.valueOf(thrown.getMessage()).contains(publicKey.substring(40, 80)));
            assertFalse(signature.length() > 40 && String.valueOf(thrown.getMessage()).contains(signature.substring(0, 40)));
        }
    }

    /** 私钥格式错误：签名失败，异常不含私钥文本；私钥必须是受控句柄。 */
    @Test
    void test_signRejectsBadKey() {
        HttpWork http = request();
        var env = env(http, Map.of("key", new ProtectedHandle("secret-not-a-key")));
        RuntimeException thrown = assertThrows(RuntimeException.class,
            () -> engine.compile(ExpressionProfile.SCRIPT, "rsaSha256Sign(params.key, 'x')").execute(env));
        assertFalse(String.valueOf(thrown.getMessage()).contains("secret-not-a-key"));

        var plain = env(http, Map.of("key", encode(KEYS.getPrivate().getEncoded())));
        assertThrows(RuntimeException.class,
            () -> engine.compile(ExpressionProfile.SCRIPT, "rsaSha256Sign(params.key, 'x')").execute(plain));
    }

    /** 协议时间：引擎时钟的 UTC 毫秒精度，固定以 Z 结尾。 */
    @Test
    void test_nowRfc3339() {
        HttpWork http = request();
        run("http.requestHeaders.time = nowRfc3339()", http);
        assertEquals("2026-10-14T07:29:00.123Z", headers(http).get("time"));
        var midnight = new ExpressionEngine(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        assertEquals("2026-01-01T00:00:00.000Z",
            midnight.compile(ExpressionProfile.SCRIPT, "nowRfc3339()").execute(new HashMap<>()));
    }

    /** urlEncode：RFC 3986 unreserved 之外按 UTF-8 百分号编码（大写十六进制），空格不变成 +；与 urlDecode 往返一致。 */
    @Test
    void test_urlEncode() {
        assertEquals("a%2Bb%2Fc%3D%3D", script("urlEncode('a+b/c==')"));
        assertEquals("AZaz09-._~%20%25%E4%B8%AD%F0%9F%98%80", script("urlEncode('AZaz09-._~ %中😀')"));
        assertEquals("", script("urlEncode('')"));
        String signature = sign("{\"ok\":true}");
        assertEquals(signature, script("urlDecode(urlEncode('" + signature + "'))"));
        assertThrows(RuntimeException.class, () -> script("urlEncode(1)"));
    }

    /** urlDecode：大小写十六进制都可解码，+ 保持原样；截断、非十六进制与非法 UTF-8 序列一律失败。 */
    @Test
    void test_urlDecode() {
        assertEquals("a+b/c==", script("urlDecode('a+b%2fc%3D%3d')"));
        assertEquals("中 😀", script("urlDecode('%E4%B8%AD%20😀')"));
        for (String invalid : List.of("%", "%2", "abc%G1", "%FF", "%E4%B8")) {
            assertThrows(RuntimeException.class, () -> script("urlDecode('" + invalid + "')"), invalid);
        }
    }

    /** sha256Base64：UTF-8 字节的 SHA-256 标准 Base64（带填充），与样本一致；非字符串参数按固定文字失败。 */
    @Test
    void test_sha256Base64() {
        assertEquals(CYBS_DIGEST, script("sha256Base64('{\"a\":1}')"));
        assertEquals("47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=", script("sha256Base64('')"));
        ExecutionErrorException thrown = assertThrows(ExecutionErrorException.class, () -> script("sha256Base64(1)"));
        assertEquals("sha256Base64 的参数必须是字符串", thrown.getMessage());
    }

    /** hmacSha256Base64DecodedKey：密钥按 Base64 解码后计算 HmacSHA256，结果与样本逐字节一致。 */
    @Test
    void test_hmacSha256Base64DecodedKey() {
        var params = new HashMap<String, Object>();
        params.put("key", new ProtectedHandle(CYBS_SECRET_KEY));
        params.put("message", CYBS_SIGNING_STRING);
        assertEquals("dEKh5lsiIfAITWPFPZu9sAl8grzT2UrM1BdCQVEhh2U=", engine.compile(ExpressionProfile.SCRIPT,
            "hmacSha256Base64DecodedKey(params.key, params.message)").execute(env(request(), params)));
    }

    /** 密钥不是合法 Base64：固定文字的异常，不含密钥；密钥是普通字符串而非受控句柄时拒绝。 */
    @Test
    void test_hmacSha256Base64DecodedKeyRejectsKey() {
        String invalid = "not-base64-secret!";
        var params = new HashMap<String, Object>();
        params.put("key", new ProtectedHandle(invalid));
        ExecutionErrorException thrown = assertThrows(ExecutionErrorException.class, () -> engine
            .compile(ExpressionProfile.SCRIPT, "hmacSha256Base64DecodedKey(params.key, 'x')").execute(env(request(), params)));
        assertAll(() -> assertEquals("hmacSha256Base64DecodedKey 的密钥不是合法 Base64", thrown.getMessage()),
            () -> assertFalse(thrown.getMessage().contains(invalid)));

        params.put("key", CYBS_SECRET_KEY);
        ExecutionErrorException plain = assertThrows(ExecutionErrorException.class, () -> engine
            .compile(ExpressionProfile.SCRIPT, "hmacSha256Base64DecodedKey(params.key, 'x')").execute(env(request(), params)));
        assertAll(() -> assertEquals("hmacSha256Base64DecodedKey 的凭据参数必须是受控句柄", plain.getMessage()),
            () -> assertFalse(plain.getMessage().contains(CYBS_SECRET_KEY)));
    }

    /** nowRfc1123：引擎时钟的 UTC 秒精度，英文星期与月份，个位日期补零。 */
    @Test
    void test_nowRfc1123() {
        var sample = new ExpressionEngine(Clock.fixed(Instant.parse("2026-10-21T08:30:00Z"), ZoneOffset.UTC));
        assertEquals("Wed, 21 Oct 2026 08:30:00 GMT",
            sample.compile(ExpressionProfile.SCRIPT, "nowRfc1123()").execute(new HashMap<>()));
        var singleDigitDay = new ExpressionEngine(Clock.fixed(Instant.parse("2026-03-05T07:04:09.987Z"), ZoneOffset.UTC));
        assertEquals("Thu, 05 Mar 2026 07:04:09 GMT",
            singleDigitDay.compile(ExpressionProfile.SCRIPT, "nowRfc1123()").execute(new HashMap<>()));
        HttpWork http = request();
        run("http.requestHeaders.Date = nowRfc1123()", http);
        assertEquals("Wed, 14 Oct 2026 07:29:00 GMT", headers(http).get("Date"));
    }

    /** 金额为 10.00 的 JSON 请求体，锁定时按 JSON 数字编码。 */
    private static HttpWork request() {
        var body = new LinkedHashMap<String, Object>();
        body.put("data", new LinkedHashMap<>(Map.of("amount", new BigDecimal("10.00"))));
        return HttpWork.request(Map.of(), body, value -> JSON.writeValueAsBytes(JsonValues.toJson(value, true)));
    }

    /** 执行无 HTTP 上下文的脚本并返回结果。 */
    private Object script(String source) {
        return engine.compile(ExpressionProfile.SCRIPT, source).execute(env(request(), Map.of()));
    }

    /** 对 HTTP 工作对象执行脚本。 */
    private void run(String source, HttpWork http) {
        engine.compile(ExpressionProfile.SCRIPT, source)
            .execute(env(http, Map.of("key", new ProtectedHandle(encode(KEYS.getPrivate().getEncoded())))));
    }

    /** 以验签函数校验签名。 */
    private Object verifyScript(String publicKey, String message, String signature) {
        var params = new HashMap<String, Object>();
        params.put("publicKey", publicKey);
        params.put("message", message);
        params.put("signature", signature);
        return engine.compile(ExpressionProfile.SCRIPT,
            "requireRsaSha256Signature(params.publicKey, params.message, params.signature)")
            .execute(env(request(), params));
    }

    /** 脚本运行环境。 */
    private static Map<String, Object> env(HttpWork http, Map<String, Object> params) {
        var env = new HashMap<String, Object>();
        env.put("http", http);
        env.put("params", params);
        return env;
    }

    /** 请求头工作副本。 */
    private static Map<?, ?> headers(HttpWork http) {
        return (Map<?, ?>)http.get(HttpWork.REQUEST_HEADERS);
    }

    /** 随机生成的合成 RSA 密钥对。 */
    private static KeyPair keyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** SHA256withRSA 签名，Base64 编码。 */
    private static String sign(String text) {
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(KEYS.getPrivate());
            signer.update(text.getBytes(StandardCharsets.UTF_8));
            return encode(signer.sign());
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** SHA256withRSA 验签。 */
    private static boolean verify(String text, String signature) {
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(KEYS.getPublic());
            verifier.update(text.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signature));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** 标准 Base64 编码。 */
    private static String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
