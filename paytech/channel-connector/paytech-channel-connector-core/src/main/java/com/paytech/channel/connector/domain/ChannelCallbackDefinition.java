package com.paytech.channel.connector.domain;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

import com.paytech.foundation.common.error.ErrorException;

import lombok.AllArgsConstructor;
import lombok.Getter;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 支付渠道的支付结果通知定义（t_channel.callback_definition_json，3DS 全链路 technique 3.4）
 * <li>signature：algorithm 本期只允许 RSA_SHA256；headerName 为签名请求头；publicKeyParameter 为账号参数中的公钥名（X.509 Base64）。</li>
 * <li>fields：通知 JSON 中商户订单号、渠道交易号、状态、事件时间的点分路径；商户订单号必填，其余可空。</li>
 * <li>acknowledgement：验签通过后的成功应答 contentType 与 body。</li>
 * <li>序列化后最多 4096 UTF-8 字节；不保存凭据或密钥。定义不合法属于配置错误，直接失败。</li>
 */
@Getter
@AllArgsConstructor
public class ChannelCallbackDefinition {
    /** 本期唯一支持的验签算法：SHA256withRSA，签名原文为原始 Body 字节。 */
    public static final String RSA_SHA256 = "RSA_SHA256";

    /** 定义序列化后的最大字节数。 */
    static final int MAX_BYTES = 4096;

    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION).build();

    private static final String HEADER_NAME = "[A-Za-z0-9-]{1,64}";

    private static final String PARAMETER_NAME = "[A-Za-z][A-Za-z0-9_.-]{0,63}";

    private static final String FIELD_PATH = "[A-Za-z_][A-Za-z0-9_]{0,63}(\\.[A-Za-z_][A-Za-z0-9_]{0,63}){0,7}";

    /** 验签算法 */
    private final String algorithm;

    /** 签名请求头名称（大小写不敏感） */
    private final String signatureHeader;

    /** 账号参数中的公钥名 */
    private final String publicKeyParameter;

    /** 商户订单号路径（即 Payment 支付尝试 ID） */
    private final String merchantReferencePath;

    /** 渠道交易号路径；未声明为 null */
    private final String channelTransactionRefPath;

    /** 渠道状态路径；未声明为 null */
    private final String statusPath;

    /** 事件时间路径；未声明为 null */
    private final String eventTimePath;

    /** 成功应答媒体类型 */
    private final String acknowledgementContentType;

    /** 成功应答 Body */
    private final String acknowledgementBody;

    /**
     * 解析并校验通知定义
     *
     * @param json 定义 JSON
     * @return 定义
     * @throws ErrorException 定义缺失必填项、超长或格式不符
     */
    public static ChannelCallbackDefinition parse(String json) {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new ErrorException("渠道通知定义缺失或超过上限");
        }
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (JacksonException invalid) {
            throw new ErrorException("渠道通知定义不是合法 JSON");
        }
        JsonNode signature = root.path("signature");
        JsonNode fields = root.path("fields");
        JsonNode acknowledgement = root.path("acknowledgement");
        var definition = new ChannelCallbackDefinition(required(signature, "algorithm", RSA_SHA256),
            required(signature, "headerName", HEADER_NAME), required(signature, "publicKeyParameter", PARAMETER_NAME),
            required(fields, "merchantReference", FIELD_PATH), optional(fields, "channelTransactionRef"),
            optional(fields, "status"), optional(fields, "eventTime"),
            required(acknowledgement, "contentType", "[a-z]+/[A-Za-z0-9.+-]+(;\\s?charset=[A-Za-z0-9-]+)?"),
            required(acknowledgement, "body", "[\\s\\S]{1,1024}"));
        return definition;
    }

    /**
     * 按定义验签：SHA256withRSA，签名原文为原始 Body 字节
     *
     * @param publicKey 账号参数中的公钥（X.509 SubjectPublicKeyInfo，Base64，允许换行）
     * @param body 原始 Body
     * @param signature 签名请求头的值（Base64）；缺失视为不通过
     * @return 签名是否有效；公钥、签名格式错误按不通过处理
     */
    public boolean verify(String publicKey, byte[] body, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Base64.getMimeDecoder().decode(publicKey))));
            verifier.update(body);
            return verifier.verify(Base64.getDecoder().decode(signature.strip()));
        } catch (GeneralSecurityException | IllegalArgumentException invalid) {
            return false;
        }
    }

    /**
     * 按字段路径读取通知
     * <li>Body 不是 JSON 对象、商户订单号缺失或不是支付尝试 ID（正十进制 long）时返回 null，由调用方记为无法映射。</li>
     * <li>可选字段缺失、类型不符或超长时为 null；事件时间不是 RFC 3339 时为 null。</li>
     *
     * @param body 已验签的原始 Body
     * @return 通知内容；无法映射时为 null
     */
    public ChannelPaymentNotice read(byte[] body) {
        JsonNode root;
        try {
            root = JSON.readTree(body);
        } catch (JacksonException invalid) {
            return null;
        }
        if (root == null || !root.isObject()) {
            return null;
        }
        String reference = text(root, merchantReferencePath, 19);
        if (reference == null || !reference.matches("[1-9][0-9]{0,18}")) {
            return null;
        }
        return new ChannelPaymentNotice(Long.parseLong(reference), reference, text(root, channelTransactionRefPath, 255),
            text(root, statusPath, 64), instant(text(root, eventTimePath, 64)));
    }

    /** 点分路径上的字符串或整数值；缺失、类型不符或超长为 null。 */
    static String text(JsonNode root, String path, int maxLength) {
        if (path == null) {
            return null;
        }
        JsonNode node = root;
        for (String segment : path.split("\\.")) {
            node = node.path(segment);
        }
        String value = node.isString() ? node.asString() : node.isIntegralNumber() ? node.asString() : null;
        return value == null || value.isEmpty() || value.length() > maxLength ? null : value;
    }

    /** RFC 3339 时间；不合法为 null。 */
    static Instant instant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }

    /** 必填字符串，须匹配格式。 */
    static String required(JsonNode node, String field, String pattern) {
        JsonNode value = node.path(field);
        if (!value.isString() || !value.asString().matches(pattern)) {
            throw new ErrorException("渠道通知定义字段无效: " + field);
        }
        return value.asString();
    }

    /** 可选字段路径；声明时须匹配格式。 */
    static String optional(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return required(node, field, FIELD_PATH);
    }
}
