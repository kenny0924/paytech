package com.paytech.platform.support.integration.domainservice.function;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorFunction;
import com.googlecode.aviator.runtime.type.AviatorNil;
import com.googlecode.aviator.runtime.type.AviatorObject;
import com.googlecode.aviator.runtime.type.AviatorRuntimeJavaType;
import com.paytech.platform.support.integration.domain.execution.ExecutionErrorException;
import com.paytech.platform.support.integration.domain.execution.ProtectedHandle;
import com.paytech.platform.support.integration.domainservice.execution.HttpWork;

/**
 * 公共脚本可调用的注册函数（研发计划第 3 步第 5 节）；签名登记在
 * {@link com.paytech.platform.support.integration.domain.RegisteredFunctionCatalog}。
 *
 * <p>认证函数返回受控句柄，赋给请求 Header 后由传输层在发送时取出明文，脚本读不到凭据；
 * 摘要与编码函数返回普通字符串。参数类型不符时抛出固定文字的异常，不带参数值。
 *
 * <p>对最终字节签名（原子 API 结构第 6 节）：sealJsonBody 锁定请求体并返回实际发送的文本，再交给签名函数；
 * 验签函数失败即抛出异常（节点 ERROR），脚本不能吞掉验签失败。
 */
public final class ScriptFunctions {

    /** Bearer 认证 Header 值。 */
    public static final String BEARER_AUTH = "bearerAuth";

    /** Basic 认证 Header 值。 */
    public static final String BASIC_AUTH = "basicAuth";

    /** HMAC-SHA256，十六进制小写。 */
    public static final String HMAC_SHA256_HEX = "hmacSha256Hex";

    /** HMAC-SHA256，Base64。 */
    public static final String HMAC_SHA256_BASE64 = "hmacSha256Base64";

    /** SHA-256，十六进制小写。 */
    public static final String SHA256_HEX = "sha256Hex";

    /** UTF-8 字符串的 Base64。 */
    public static final String BASE64_ENCODE = "base64Encode";

    /** 按点分路径读取对象中的值。 */
    public static final String JSON_GET = "json.get";

    /** 按点分路径写入对象，缺少的中间对象自动创建。 */
    public static final String JSON_SET = "json.set";

    /** 序列化并锁定 JSON 请求体，返回实际发送的文本。 */
    public static final String SEAL_JSON_BODY = "sealJsonBody";

    /** SHA256withRSA 签名，Base64。 */
    public static final String RSA_SHA256_SIGN = "rsaSha256Sign";

    /** SHA256withRSA 验签，失败即异常。 */
    public static final String REQUIRE_RSA_SHA256_SIGNATURE = "requireRsaSha256Signature";

    /** 当前 UTC 时间，RFC 3339 毫秒精度。 */
    public static final String NOW_RFC3339 = "nowRfc3339";

    private static final DateTimeFormatter RFC3339_MILLIS =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private ScriptFunctions() {
    }

    /**
     * 全部脚本函数的 Aviator 实现。
     *
     * @param clock nowRfc3339 使用的时钟
     * @return 函数实现
     */
    public static List<AviatorFunction> all(Clock clock) {
        return List.of(new BearerAuth(), new BasicAuth(), new HmacSha256(HMAC_SHA256_HEX, false),
            new HmacSha256(HMAC_SHA256_BASE64, true), new Sha256Hex(), new Base64Encode(), new JsonGet(), new JsonSet(),
            new SealJsonBody(), new RsaSha256Sign(), new RequireRsaSha256Signature(), new NowRfc3339(clock));
    }

    static ProtectedHandle handle(AviatorObject argument, Map<String, Object> env, String function) {
        if (argument.getValue(env) instanceof ProtectedHandle handle) {
            return handle;
        }
        throw new ExecutionErrorException(function + " 的凭据参数必须是受控句柄");
    }

    static String text(AviatorObject argument, Map<String, Object> env, String function) {
        if (argument.getValue(env) instanceof String text) {
            return text;
        }
        throw new ExecutionErrorException(function + " 的参数必须是字符串");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(AviatorObject argument, Map<String, Object> env, String function) {
        if (argument.getValue(env) instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new ExecutionErrorException(function + " 的对象参数必须是对象");
    }

    static String[] segments(String path, String function) {
        if (path.isEmpty() || !path.matches("[A-Za-z_$][A-Za-z0-9_$-]*(\\.[A-Za-z_$][A-Za-z0-9_$-]*)*")) {
            throw new ExecutionErrorException(function + " 的路径必须是点分属性名");
        }
        return path.split("\\.");
    }

    static final class BearerAuth extends AbstractFunction {
        @Override
        public String getName() {
            return BEARER_AUTH;
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject token) {
            String value = handle(token, env, BEARER_AUTH).apply(secret -> "Bearer " + secret);
            return AviatorRuntimeJavaType.valueOf(new ProtectedHandle(value));
        }
    }

    static final class BasicAuth extends AbstractFunction {
        @Override
        public String getName() {
            return BASIC_AUTH;
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject user, AviatorObject password) {
            String credentials = handle(user, env, BASIC_AUTH).apply(name ->
                handle(password, env, BASIC_AUTH).apply(secret -> name + ":" + secret));
            String value = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            return AviatorRuntimeJavaType.valueOf(new ProtectedHandle(value));
        }
    }

    static final class HmacSha256 extends AbstractFunction {
        private final String name;
        private final boolean base64;

        HmacSha256(String name, boolean base64) {
            this.name = name;
            this.base64 = base64;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject key, AviatorObject message) {
            byte[] data = text(message, env, name).getBytes(StandardCharsets.UTF_8);
            byte[] digest = handle(key, env, name).apply(secret -> {
                try {
                    Mac mac = Mac.getInstance("HmacSHA256");
                    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                    return mac.doFinal(data);
                } catch (GeneralSecurityException failure) {
                    throw new ExecutionErrorException(name + " 计算失败");
                }
            });
            return AviatorRuntimeJavaType.valueOf(base64 ? Base64.getEncoder().encodeToString(digest)
                : HexFormat.of().formatHex(digest));
        }
    }

    static final class Sha256Hex extends AbstractFunction {
        @Override
        public String getName() {
            return SHA256_HEX;
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject message) {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text(message, env, SHA256_HEX).getBytes(StandardCharsets.UTF_8));
                return AviatorRuntimeJavaType.valueOf(HexFormat.of().formatHex(digest));
            } catch (GeneralSecurityException failure) {
                throw new ExecutionErrorException(SHA256_HEX + " 计算失败");
            }
        }
    }

    static final class Base64Encode extends AbstractFunction {
        @Override
        public String getName() {
            return BASE64_ENCODE;
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject value) {
            return AviatorRuntimeJavaType.valueOf(Base64.getEncoder()
                .encodeToString(text(value, env, BASE64_ENCODE).getBytes(StandardCharsets.UTF_8)));
        }
    }

    static final class JsonGet extends AbstractFunction {
        @Override
        public String getName() {
            return JSON_GET;
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject target, AviatorObject path) {
            Object current = object(target, env, JSON_GET);
            for (String segment : segments(text(path, env, JSON_GET), JSON_GET)) {
                if (!(current instanceof Map<?, ?> map)) {
                    return AviatorNil.NIL;
                }
                current = map.get(segment);
            }
            return current == null ? AviatorNil.NIL : AviatorRuntimeJavaType.valueOf(current);
        }
    }

    static final class JsonSet extends AbstractFunction {
        @Override
        public String getName() {
            return JSON_SET;
        }

        @Override
        @SuppressWarnings("unchecked")
        public AviatorObject call(Map<String, Object> env, AviatorObject target, AviatorObject path,
            AviatorObject value) {
            Map<String, Object> root = object(target, env, JSON_SET);
            String[] segments = segments(text(path, env, JSON_SET), JSON_SET);
            Map<String, Object> current = root;
            for (int index = 0; index < segments.length - 1; index++) {
                Object next = current.computeIfAbsent(segments[index], key -> new LinkedHashMap<String, Object>());
                if (!(next instanceof Map<?, ?>)) {
                    throw new ExecutionErrorException(JSON_SET + " 的路径经过了非对象值");
                }
                current = (Map<String, Object>) next;
            }
            current.put(segments[segments.length - 1], value.getValue(env));
            return AviatorRuntimeJavaType.valueOf(root);
        }
    }

    static final class SealJsonBody extends AbstractFunction {
        @Override
        public String getName() {
            return SEAL_JSON_BODY;
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject http) {
            if (!(http.getValue(env) instanceof HttpWork work)) {
                throw new ExecutionErrorException(SEAL_JSON_BODY + " 的参数必须是 http 工作对象");
            }
            return AviatorRuntimeJavaType.valueOf(work.sealJsonBody());
        }
    }

    static final class RsaSha256Sign extends AbstractFunction {
        @Override
        public String getName() {
            return RSA_SHA256_SIGN;
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject privateKey, AviatorObject message) {
            byte[] data = text(message, env, RSA_SHA256_SIGN).getBytes(StandardCharsets.UTF_8);
            byte[] signature = handle(privateKey, env, RSA_SHA256_SIGN).apply(key -> {
                try {
                    Signature signer = Signature.getInstance("SHA256withRSA");
                    signer.initSign(KeyFactory.getInstance("RSA")
                        .generatePrivate(new PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(key))));
                    signer.update(data);
                    return signer.sign();
                } catch (GeneralSecurityException | IllegalArgumentException failure) {
                    throw new ExecutionErrorException(RSA_SHA256_SIGN + " 签名失败");
                }
            });
            return AviatorRuntimeJavaType.valueOf(Base64.getEncoder().encodeToString(signature));
        }
    }

    static final class RequireRsaSha256Signature extends AbstractFunction {
        @Override
        public String getName() {
            return REQUIRE_RSA_SHA256_SIGNATURE;
        }

        @Override
        public AviatorObject call(Map<String, Object> env, AviatorObject publicKey, AviatorObject message,
            AviatorObject signature) {
            if (!(signature.getValue(env) instanceof String encoded) || encoded.isEmpty()) {
                throw new ExecutionErrorException(REQUIRE_RSA_SHA256_SIGNATURE + " 缺少签名");
            }
            boolean valid;
            try {
                Signature verifier = Signature.getInstance("SHA256withRSA");
                verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(
                    Base64.getMimeDecoder().decode(text(publicKey, env, REQUIRE_RSA_SHA256_SIGNATURE)))));
                verifier.update(text(message, env, REQUIRE_RSA_SHA256_SIGNATURE).getBytes(StandardCharsets.UTF_8));
                valid = verifier.verify(Base64.getDecoder().decode(encoded.strip()));
            } catch (GeneralSecurityException | IllegalArgumentException failure) {
                throw new ExecutionErrorException(REQUIRE_RSA_SHA256_SIGNATURE + " 验签失败");
            }
            if (!valid) {
                throw new ExecutionErrorException(REQUIRE_RSA_SHA256_SIGNATURE + " 验签失败");
            }
            return AviatorRuntimeJavaType.valueOf(Boolean.TRUE);
        }
    }

    static final class NowRfc3339 extends AbstractFunction {
        private final Clock clock;

        NowRfc3339(Clock clock) {
            this.clock = clock;
        }

        @Override
        public String getName() {
            return NOW_RFC3339;
        }

        @Override
        public AviatorObject call(Map<String, Object> env) {
            return AviatorRuntimeJavaType.valueOf(RFC3339_MILLIS.format(clock.instant()));
        }
    }
}
