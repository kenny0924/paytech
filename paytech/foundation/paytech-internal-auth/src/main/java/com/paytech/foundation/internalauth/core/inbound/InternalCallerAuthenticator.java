package com.paytech.foundation.internalauth.core.inbound;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.internalauth.api.CallerIdentity;
import com.paytech.foundation.internalauth.api.InternalAuthMode;
import com.paytech.foundation.internalauth.api.InternalTokenClaims;
import com.paytech.foundation.internalauth.api.InternalTokenInvalidException;
import com.paytech.foundation.internalauth.api.InternalTokenUnavailableException;
import com.paytech.foundation.internalauth.api.InternalTokenVerifier;

/**
 * 与 Web 栈无关的入站判定，Servlet 过滤器与 WebFlux 过滤器共用。
 * <li>{@code REQUIRED}：缺少或不合法的 Token → 401；校验公钥不可用 → 503。</li>
 * <li>{@code DUAL}：合法 Token 优先；否则回退唯一且合法的 {@code X-Caller-Service}（每个调用方首次记 WARN）；两者都没有时放行且不带身份。</li>
 * <li>豁免路径前缀（默认 actuator 与 {@code /public/}）不校验。</li>
 *
 * @author Claude
 */
public final class InternalCallerAuthenticator {

    /** 承载 Token 的请求头。 */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /** 迁移期明文调用方头。 */
    public static final String LEGACY_CALLER_HEADER = "X-Caller-Service";

    private static final Logger LOGGER = LoggerFactory.getLogger(InternalCallerAuthenticator.class);

    private static final String BEARER_PREFIX = "bearer ";

    private static final int MAX_WARNED_CALLERS = 256;

    private final InternalAuthMode mode;
    private final InternalTokenVerifier verifier;
    private final List<String> exemptPathPrefixes;
    private final Set<String> warnedLegacyCallers = ConcurrentHashMap.newKeySet();

    /**
     * 创建判定器。
     *
     * @param mode 校验模式
     * @param verifier Token 校验器
     * @param exemptPathPrefixes 豁免路径前缀
     */
    public InternalCallerAuthenticator(InternalAuthMode mode, InternalTokenVerifier verifier,
        List<String> exemptPathPrefixes) {
        this.mode = Objects.requireNonNull(mode, "mode must not be null");
        this.verifier = Objects.requireNonNull(verifier, "verifier must not be null");
        this.exemptPathPrefixes = List.copyOf(exemptPathPrefixes);
    }

    /**
     * 判定一个入站请求。
     *
     * @param path 不含 Context Path 的请求路径
     * @param authorizationValues 全部 {@code Authorization} 头值
     * @param legacyCallerValues 全部 {@code X-Caller-Service} 头值
     * @return 判定结果
     */
    public Decision authenticate(String path, List<String> authorizationValues, List<String> legacyCallerValues) {
        if (mode == InternalAuthMode.OFF || isExempt(path)) {
            return Decision.pass();
        }
        String token = bearerToken(authorizationValues);
        Decision tokenDecision = token == null ? Decision.reject(CommonErrorCode.INTERNAL_TOKEN_INVALID, 401)
            : verify(token);
        if (tokenDecision.identity() != null || mode == InternalAuthMode.REQUIRED) {
            return tokenDecision;
        }
        return legacy(legacyCallerValues);
    }

    /**
     * 当前模式。
     *
     * @return 模式
     */
    public InternalAuthMode mode() {
        return mode;
    }

    private Decision verify(String token) {
        try {
            return Decision.authenticated(verifier.verify(token));
        } catch (InternalTokenInvalidException exception) {
            LOGGER.debug("内部调用 Token 被拒绝: {}", exception.getMessage());
            return Decision.reject(CommonErrorCode.INTERNAL_TOKEN_INVALID, 401);
        } catch (InternalTokenUnavailableException exception) {
            LOGGER.warn("内部调用 Token 校验公钥不可用，失败关闭", exception);
            return Decision.reject(CommonErrorCode.SERVICE_UNAVAILABLE, 503);
        }
    }

    private Decision legacy(List<String> legacyCallerValues) {
        if (legacyCallerValues == null || legacyCallerValues.size() != 1) {
            return Decision.pass();
        }
        String caller = legacyCallerValues.getFirst();
        if (caller == null || !InternalTokenClaims.SERVICE_NAME.matcher(caller).matches()) {
            return Decision.pass();
        }
        if (warnedLegacyCallers.size() < MAX_WARNED_CALLERS && warnedLegacyCallers.add(caller)) {
            LOGGER.warn("内部调用方仍使用 X-Caller-Service 明文头（DUAL 模式回退）: {}", caller);
        }
        return Decision.authenticated(CallerIdentity.legacy(caller));
    }

    private boolean isExempt(String path) {
        String safePath = path == null ? "" : path;
        return exemptPathPrefixes.stream().anyMatch(safePath::startsWith);
    }

    private static String bearerToken(List<String> authorizationValues) {
        if (authorizationValues == null || authorizationValues.size() != 1) {
            return null;
        }
        String value = authorizationValues.getFirst();
        if (value == null || value.length() <= BEARER_PREFIX.length()
            || !value.substring(0, BEARER_PREFIX.length()).toLowerCase(Locale.ROOT).equals(BEARER_PREFIX)) {
            return null;
        }
        return value.substring(BEARER_PREFIX.length()).trim();
    }

    /**
     * 入站判定结果：放行（可能无身份）、已识别或拒绝。
     *
     * @param identity 已识别的调用方；放行无身份或拒绝时为 null
     * @param rejection 拒绝错误码；非拒绝时为 null
     * @param status 拒绝时的 HTTP 状态；非拒绝时为 0
     */
    public record Decision(CallerIdentity identity, CodeMessageEnum rejection, int status) {

        static Decision pass() {
            return new Decision(null, null, 0);
        }

        static Decision authenticated(CallerIdentity identity) {
            return new Decision(identity, null, 0);
        }

        static Decision reject(CodeMessageEnum code, int status) {
            return new Decision(null, code, status);
        }

        /**
         * 是否拒绝请求。
         *
         * @return 拒绝返回 true
         */
        public boolean rejected() {
            return rejection != null;
        }
    }
}
