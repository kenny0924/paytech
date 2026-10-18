package com.paytech.platform.support.accesscontrol.domain.authentication;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Pattern;

/** Client 会话的固定生命周期与不透明 Refresh 格式，不访问外部状态。 */
public final class ClientSessionRules {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{22}\\.[A-Za-z0-9_-]{43}");
    /** 固定规则不实例化。 */
    private ClientSessionRules() {}
    /** 会话绝对期限固定七天。 */
    public static Instant absoluteExpiry(Instant issuedAt) { return issuedAt.plusSeconds(604800); }
    /** 成功刷新延长一天空闲时间，但不能越过原绝对期限。 */
    public static Instant idleExpiry(Instant now, Instant absolute) {
        Instant idle = now.plusSeconds(86400);
        return idle.isBefore(absolute) ? idle : absolute;
    }
    /** 凭据或安全纪元变更立即使旧会话不可继续使用。 */
    public static boolean valid(SessionState state, int epoch, int credentialVersion, Instant now) {
        return state != null && "ACTIVE".equals(state.status()) && state.accountEpoch() == epoch
            && state.credentialVersion() == credentialVersion && now.isBefore(state.idleExpireTime())
            && now.isBefore(state.absoluteExpireTime());
    }
    /** 只接纳规范、有限长度的公开查找段与 256 bit 秘密。 */
    public static String lookup(String raw) {
        if (raw == null || !TOKEN.matcher(raw).matches()) { return null; }
        String[] parts = raw.split("\\.");
        for (String part : parts) {
            if (!Base64.getUrlEncoder().withoutPadding().encodeToString(Base64.getUrlDecoder().decode(part)).equals(part)) {
                return null;
            }
        }
        return parts[0];
    }
    /** 创建一次性随机令牌，仅在当前调用内返回原值。 */
    public static String newRefreshToken() { return random(16) + "." + random(32); }
    /** 生成规范无填充随机字节文本。 */
    private static String random(int size) {
        byte[] bytes = new byte[size]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    /** 纯规则只接收已经解析的会话事实。 */
    public record SessionState(String status, int accountEpoch, int credentialVersion,
        Instant idleExpireTime, Instant absoluteExpireTime) {}
}
