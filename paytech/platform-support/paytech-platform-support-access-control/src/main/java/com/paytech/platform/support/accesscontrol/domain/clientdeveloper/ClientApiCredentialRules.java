package com.paytech.platform.support.accesscontrol.domain.clientdeveloper;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.platform.support.api.apicredential.ApiCredentialConstraints;

/** 工作区自助 API 凭证的输入规则与密钥生成；不访问数据库。 */
public final class ClientApiCredentialRules {

    /** 每个工作区有效凭证上限；轮换不受此限制。 */
    public static final int ACTIVE_LIMIT = 10;

    /** 允许的有效期天数。 */
    public static final Set<Integer> VALIDITY_DAYS = Set.of(30, 90, 180, 365);

    /** 允许的轮换宽限分钟数；0 表示立即撤销旧凭证。 */
    public static final Set<Integer> GRACE_MINUTES = Set.of(0, 60, 1440, 10080);

    private static final String PREFIX_HEAD = "ck-";
    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Argon2PasswordEncoder ENCODER = new Argon2PasswordEncoder(16, 32, 1, 19_456, 2);

    private ClientApiCredentialRules() {}

    /** 去除首尾空白后 1..64 字符，不允许控制字符。 */
    public static String name(String raw) {
        String name = raw == null ? "" : raw.strip();
        if (name.isEmpty() || name.length() > 64 || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new InvalidParameterException("凭证名称需为 1 到 64 个字符，且不能包含控制字符");
        }
        return name;
    }

    /** 1..64 个合法且不重复的 Scope 编码，保持输入顺序。 */
    public static List<String> scopes(List<String> raw) {
        if (raw == null || raw.isEmpty() || raw.size() > ApiCredentialConstraints.MAX_EFFECTIVE_SCOPE_CODES) {
            throw new InvalidParameterException("至少选择一个、最多 64 个 Scope");
        }
        var distinct = new LinkedHashSet<String>(raw);
        if (distinct.size() != raw.size() || !distinct.stream().allMatch(ApiCredentialConstraints::isValidScopeCode)) {
            throw new InvalidParameterException("Scope 编码无效或重复");
        }
        return List.copyOf(distinct);
    }

    /** 有效期天数必须为 30、90、180 或 365。 */
    public static int validityDays(int days) {
        if (!VALIDITY_DAYS.contains(days)) {
            throw new InvalidParameterException("有效期只能是 30、90、180 或 365 天");
        }
        return days;
    }

    /** 宽限分钟数必须为 0、60、1440 或 10080。 */
    public static int graceMinutes(int minutes) {
        if (!GRACE_MINUTES.contains(minutes)) {
            throw new InvalidParameterException("旧凭证宽限只能是立即、1 小时、24 小时或 7 天");
        }
        return minutes;
    }

    /** 轮换时沿用原凭证的有效期天数（创建到到期的整天数），限定在 1..365 天。 */
    public static int inheritedDays(Instant createTime, Instant expiresTime) {
        long days = Duration.between(createTime, expiresTime).toDays();
        return (int) Math.max(1, Math.min(365, days));
    }

    /** 生成一把新密钥：前缀 ck- 加 20 位 base62，密文 256 位 Base64URL，PHC 使用平台批准的 Argon2id 参数。 */
    public static Secret generate() {
        var prefix = new StringBuilder(PREFIX_HEAD);
        for (int index = 0; index < 20; index++) {
            prefix.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String credential = prefix + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new Secret(prefix.toString(), credential, ENCODER.encode(credential));
    }

    /** 新密钥：完整值只在本次响应中出现。 */
    public record Secret(String keyPrefix, String credential, String verifierPhc) {
        /** 不输出密钥与校验值。 */
        @Override
        public String toString() {
            return "Secret[REDACTED]";
        }
    }
}
