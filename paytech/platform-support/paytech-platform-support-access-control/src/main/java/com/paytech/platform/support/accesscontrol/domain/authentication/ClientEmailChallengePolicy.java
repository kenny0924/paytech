package com.paytech.platform.support.accesscontrol.domain.authentication;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengePurpose;
import com.paytech.platform.support.accesscontrol.domain.enums.ClientChallengeStatus;

/** Client 邮件链接的纯协议规则；HMAC 计算与状态持久化由调用方分阶段完成。 */
public final class ClientEmailChallengePolicy {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final String DIGEST_PATTERN = "[0-9a-f]{64}";

    /** 生成独立查找标识与 256 位秘密；秘密只能经受控邮件适配器发送。 */
    public LinkMaterial newLink() {
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        return new LinkMaterial(UUID.randomUUID().toString(), URL_ENCODER.encodeToString(secret));
    }

    /** 拒绝超长、非规范或带填充的输入，避免同一链接出现多个字符串身份。 */
    public boolean validLink(String challengeNo, String secret) {
        if (challengeNo == null || !challengeNo.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")
            || secret == null || !secret.matches("[A-Za-z0-9_-]{43}")) {
            return false;
        }
        byte[] decoded = Base64.getUrlDecoder().decode(secret);
        return decoded.length == 32 && URL_ENCODER.encodeToString(decoded).equals(secret);
    }

    /** 保持稳定用途/账号/邮箱范围；密钥轮换不能改变活动挑战的唯一身份。 */
    public String scopeHash(long accountId, ClientChallengePurpose purpose, String emailToken) {
        if (accountId <= 0 || purpose == null || emailToken == null || emailToken.isBlank()) {
            throw new IllegalArgumentException("挑战范围缺少账号、用途或邮箱引用");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                tuple("CLIENT-EMAIL-SCOPE", Long.toString(accountId), purpose.getCode(), emailToken)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    /**
     * 邀请接受挑战的范围：只绑定邀请，不绑定账号或邮箱
     * <li>同一邀请任一时刻只有一条活动挑战；重发先替代旧挑战再写入新挑战。</li>
     */
    public String invitationScopeHash(long invitationId) {
        if (invitationId <= 0) {
            throw new IllegalArgumentException("邀请挑战范围缺少邀请标识");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                tuple("CLIENT-INVITATION-SCOPE", Long.toString(invitationId)).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    /**
     * 只判断链接秘密本身是否正确，不判断挑战是否仍可消费
     * <li>用于邀请预览：已接受、已撤销或已到期的邀请仍需凭正确链接显示其状态。</li>
     * <li>尝试次数用尽后一律视为不正确。</li>
     */
    public boolean matchesSecret(ChallengeState state, String computedHmac, int keyVersion) {
        if (state == null || state.maxAttempts() != 5 || state.usedAttempts() < 0
            || state.usedAttempts() >= state.maxAttempts() || keyVersion <= 0 || state.keyVersion() != keyVersion
            || computedHmac == null || !computedHmac.matches(DIGEST_PATTERN) || state.secretHmac() == null
            || !state.secretHmac().matches(DIGEST_PATTERN)) {
            return false;
        }
        return MessageDigest.isEqual(state.secretHmac().getBytes(StandardCharsets.US_ASCII),
            computedHmac.getBytes(StandardCharsets.US_ASCII));
    }

    /** HMAC 同时绑定查找标识、用途、账号范围和秘密，禁止跨接口消费。 */
    public String proofInput(String challengeNo, ClientChallengePurpose purpose, String scopeHash, String secret) {
        if (!validLink(challengeNo, secret) || purpose == null || scopeHash == null
            || !scopeHash.matches(DIGEST_PATTERN)) {
            throw new IllegalArgumentException("邮件链接格式无效");
        }
        return tuple("CUSTOMER-EMAIL-CHALLENGE", challengeNo, purpose.getCode(), scopeHash, secret);
    }

    /** 注册验证为 30 分钟，密码恢复为 20 分钟。 */
    public Instant expiresAt(ClientChallengePurpose purpose, Instant issuedAt) {
        if (purpose == null || issuedAt == null) {
            throw new IllegalArgumentException("挑战用途和签发时间不能为空");
        }
        return issuedAt.plusSeconds(purpose == ClientChallengePurpose.SIGN_UP_EMAIL ? 1800 : 1200);
    }

    /** 按上次签发时间计算 60 秒冷却，终态或邮件发送失败不绕过冷却。 */
    public boolean canResend(Instant lastIssuedAt, Instant now) {
        return lastIssuedAt == null || !lastIssuedAt.plusSeconds(60).isAfter(now);
    }

    /** 比较已在事务外计算的 HMAC；调用方仍需在事务内 CAS 消费原状态。 */
    public boolean accepts(ChallengeState state, ClientChallengePurpose expectedPurpose, String computedHmac,
        int keyVersion, Instant now) {
        if (state == null || expectedPurpose == null || state.purpose() != expectedPurpose
            || state.status() != ClientChallengeStatus.PENDING || state.expiresAt() == null || now == null
            || !state.expiresAt().isAfter(now) || state.maxAttempts() != 5 || state.usedAttempts() < 0
            || state.usedAttempts() >= state.maxAttempts() || keyVersion <= 0 || state.keyVersion() != keyVersion
            || computedHmac == null || !computedHmac.matches(DIGEST_PATTERN) || state.secretHmac() == null
            || !state.secretHmac().matches(DIGEST_PATTERN)) {
            return false;
        }
        return MessageDigest.isEqual(state.secretHmac().getBytes(StandardCharsets.US_ASCII),
            computedHmac.getBytes(StandardCharsets.US_ASCII));
    }

    /** 采用长度前缀区分字段，避免任何分隔字符引起串联歧义。 */
    private String tuple(String... values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            result.append(value.getBytes(StandardCharsets.UTF_8).length).append(':').append(value);
        }
        return result.toString();
    }

    /** 只在当前调用和邮件发送期间持有的链接原值。 */
    public record LinkMaterial(String challengeNo, String secret) {
        /** 日志不得包含链接秘密或可关联查找标识。 */
        @Override
        public String toString() {
            return "LinkMaterial[REDACTED]";
        }
    }

    /** 不带账号资料的挑战校验输入，不是跨模块 DataObject。 */
    public record ChallengeState(ClientChallengePurpose purpose, ClientChallengeStatus status, String secretHmac,
        int keyVersion, Instant expiresAt, int maxAttempts, int usedAttempts) {
        /** 日志不得包含挑战 HMAC。 */
        @Override
        public String toString() {
            return "ChallengeState[REDACTED]";
        }
    }
}
