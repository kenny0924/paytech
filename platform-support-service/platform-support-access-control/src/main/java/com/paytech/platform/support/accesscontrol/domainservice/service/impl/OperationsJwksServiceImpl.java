package com.paytech.platform.support.accesscontrol.domainservice.service.impl;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.accesscontrol.core.dal.dataobject.JwtSigningKey;
import com.paytech.platform.support.accesscontrol.domain.authentication.PublicVerificationKey;
import com.paytech.platform.support.accesscontrol.domain.enums.JwtSigningKeyStatus;
import com.paytech.platform.support.accesscontrol.domainservice.repository.JwtSigningKeyRepository;
import com.paytech.platform.support.accesscontrol.domainservice.service.OperationsJwksService;

import lombok.AllArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Operations Realm JWKS 查询服务实现。
 */
@Service
@AllArgsConstructor
public class OperationsJwksServiceImpl implements OperationsJwksService {

    private static final int PUBLIC_JWK_SCHEMA_VERSION = 1;
    private static final int MAX_PUBLISHED_KEYS = 16;
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final int MIN_RSA_MODULUS_BITS = 2048;
    private static final Duration MIN_RETIRING_OVERLAP = Duration.ofSeconds(930);
    private static final Pattern KID_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");
    private static final Pattern BASE64URL_UINT_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final Set<String> PUBLIC_RSA_MEMBERS = Set.of("kty", "kid", "use", "key_ops", "alg", "n", "e");

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final JwtSigningKeyRepository repository;

    /**
     * 一次 SELECT 取得完整候选，再按同一时点完成纯内存校验。 无多次数据库读取的一致性需求，不额外打开事务；非法材料整体拒绝发布。
     */
    @Override
    public List<PublicVerificationKey> queryCurrentJwks() {
        Instant now = clock.instant();
        List<JwtSigningKey> candidates = repository.findOperationsVerificationCandidates();
        validateActiveKey(candidates);
        candidates.forEach(key -> validateLifecycle(key, now));
        List<JwtSigningKey> published = candidates.stream().filter(key -> isPublishedAt(key, now)).toList();
        require(published.size() <= MAX_PUBLISHED_KEYS, "可发布密钥超过 16 把");

        List<PublicVerificationKey> keys = published.stream().map(this::toPublicVerificationKey).toList();
        validateUniqueKids(keys);

        require(objectMapper.writeValueAsBytes(Map.of("keys", keys)).length <= MAX_RESPONSE_BYTES, "响应超过 64 KiB");
        return keys;
    }

    /** 筛出仍在验签窗口的公钥，截止时点不再发布。 */
    boolean isPublishedAt(JwtSigningKey key, Instant now) {
        if (key.status() == JwtSigningKeyStatus.ACTIVE) {
            return true;
        }
        return key.verificationEndTime().isAfter(now);
    }

    /** 解析并校验公开字段白名单，只返回不含持久化信息的验签材料。 */
    PublicVerificationKey toPublicVerificationKey(JwtSigningKey key) {
        require(key.publicJwkSchemaVersion() != null && key.publicJwkSchemaVersion() == PUBLIC_JWK_SCHEMA_VERSION,
            "不支持的公钥 JWK 结构版本", key);
        JsonNode node = objectMapper.readTree(key.publicJwkCanonicalJson());
        require(node != null && node.isObject(), "公钥 JWK 必须是 JSON Object", key);
        require(PUBLIC_RSA_MEMBERS.equals(Set.copyOf(node.propertyNames())), "公钥 JWK 字段不完整或包含非公开字段", key);

        String keyType = text(node, "kty", key);
        String kid = text(node, "kid", key);
        String use = text(node, "use", key);
        String algorithm = text(node, "alg", key);
        String modulus = text(node, "n", key);
        String exponent = text(node, "e", key);
        validateJwkContract(key, node, keyType, kid, use, algorithm);
        validateRsaPublicNumbers(key, modulus, exponent);
        return new PublicVerificationKey(keyType, kid, use, List.of("verify"), algorithm, modulus, exponent);
    }

    /** 候选必须恰有一把 ACTIVE 密钥，避免发布空集或歧义签名者。 */
    void validateActiveKey(List<JwtSigningKey> candidates) {
        List<JwtSigningKey> activeKeys =
            candidates.stream().filter(key -> key.status() == JwtSigningKeyStatus.ACTIVE).toList();
        require(activeKeys.size() == 1, "Operations Realm 必须且只能有一把 ACTIVE 密钥");
    }

    /** 校验所有候选的生命周期，包括已到期的 RETIRING 记录。 */
    void validateLifecycle(JwtSigningKey key, Instant now) {
        require(key.notBefore() != null && !key.notBefore().isAfter(now), "密钥尚未生效", key);
        if (key.status() == JwtSigningKeyStatus.ACTIVE) {
            require(Integer.valueOf(1).equals(key.activeSlot()), "ACTIVE 密钥占位无效", key);
            require(key.signingEndTime() == null && key.verificationEndTime() == null, "ACTIVE 密钥不应有截止时间", key);
            return;
        }

        require(key.status() == JwtSigningKeyStatus.RETIRING, "不可发布的密钥状态", key);
        require(key.activeSlot() == null, "RETIRING 密钥不应占用 ACTIVE Slot", key);
        require(key.signingEndTime() != null && key.verificationEndTime() != null, "RETIRING 密钥缺少截止时间", key);
        require(!key.notBefore().isAfter(key.signingEndTime()), "密钥生效时间晚于签名截止时间", key);
        require(!key.signingEndTime().isAfter(now), "RETIRING 密钥仍处于签名窗口", key);
        Duration overlap = Duration.between(key.signingEndTime(), key.verificationEndTime());
        require(overlap.compareTo(MIN_RETIRING_OVERLAP) >= 0, "RETIRING 验签重叠不足 930 秒", key);
    }

    /** 核对 JWK 协议字段与数据库记录，仅允许 RSA/RS256 验签。 */
    void validateJwkContract(JwtSigningKey key, JsonNode node, String keyType, String kid, String use,
        String algorithm) {
        require("RSA".equals(keyType), "kty 必须为 RSA", key);
        require(KID_PATTERN.matcher(kid).matches(), "kid 格式无效", key);
        require(kid.equals(key.kid()), "JWK kid 与数据库记录不一致", key);
        require("sig".equals(use), "use 必须为 sig", key);
        require("RS256".equals(algorithm) && algorithm.equals(key.algorithm().getCode()), "alg 必须为 RS256 且与数据库记录一致",
            key);

        JsonNode operations = node.get("key_ops");
        require(operations != null && operations.isArray() && operations.size() == 1, "key_ops 必须仅包含 verify", key);
        require(operations.get(0).isString() && "verify".equals(operations.get(0).stringValue()),
            "key_ops 必须仅包含 verify", key);
    }

    /** 拒绝不足 2048 bit 的模数及无效公开指数。 */
    void validateRsaPublicNumbers(JwtSigningKey key, String modulus, String exponent) {
        byte[] modulusBytes = decodeBase64UrlUInt(modulus, "n", key);
        byte[] exponentBytes = decodeBase64UrlUInt(exponent, "e", key);
        require(new BigInteger(1, modulusBytes).bitLength() >= MIN_RSA_MODULUS_BITS, "RSA modulus 小于 2048 bit", key);

        BigInteger publicExponent = new BigInteger(1, exponentBytes);
        require(publicExponent.compareTo(BigInteger.ONE) > 0 && publicExponent.testBit(0), "RSA public exponent 无效",
            key);
    }

    /** 只接受无前导零且能原样编码的无填充 Base64URLUInt。 */
    byte[] decodeBase64UrlUInt(String value, String member, JwtSigningKey key) {
        require(BASE64URL_UINT_PATTERN.matcher(value).matches() && value.length() % 4 != 1,
            member + " 不是无填充 Base64URLUInt", key);
        byte[] decoded = Base64.getUrlDecoder().decode(value);
        require(decoded.length > 0 && decoded[0] != 0, member + " 不是规范无符号整数", key);
        require(value.equals(Base64.getUrlEncoder().withoutPadding().encodeToString(decoded)),
            member + " 不是规范无填充 Base64URLUInt", key);
        return decoded;
    }

    /** 读取必需的非空公钥字符串字段。 */
    String text(JsonNode node, String member, JwtSigningKey key) {
        JsonNode value = node.get(member);
        require(value != null && value.isString() && !value.stringValue().isBlank(), member + " 必须是非空字符串", key);
        return value.stringValue();
    }

    /** 同一响应不得包含重复 kid。 */
    void validateUniqueKids(List<PublicVerificationKey> keys) {
        Set<String> kids = new HashSet<>();
        require(keys.stream().allMatch(key -> kids.add(key.kid())), "JWKS kid 必须唯一");
    }

    /** 整个集合不满足发布约束时，通过统一内部错误拒绝响应。 */
    void require(boolean condition, String message) {
        if (!condition) {
            throw new ErrorException("Operations JWKS 无效: " + message);
        }
    }

    /** 记录有限公开定位信息，不在异常中包含原始 JWK 内容。 */
    void require(boolean condition, String message, JwtSigningKey key) {
        if (!condition) {
            throw new ErrorException("Operations JWKS 密钥无效, signingKeyId=" + key.signingKeyId() + ", kid="
                + key.kid() + ", reason=" + message);
        }
    }
}
