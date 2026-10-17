package com.paytech.platform.support.core.configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.paytech.foundation.internalauth.api.InternalTokenClaims;
import com.paytech.platform.support.core.enums.InternalTokenKeyUsage;

import lombok.Getter;
import lombok.Setter;

/**
 * 内部调用 Token 签发端配置（D26，{@code internal-token.issuer.*}）；只在 enabled=true 时绑定，变更需重启。
 * <li>前缀不放在 Foundation 的 {@code internal-auth.*} 下：该前缀按 ignoreUnknownFields=false 严格绑定。</li>
 */
@Getter
@Setter
@ConfigurationProperties("internal-token.issuer")
public class InternalTokenIssuerProperties {

    /**
     * 是否启用签发端与内部 JWKS
     * <li>默认 false：签发与 JWKS 接口不存在，Platform 出站不能附加内部 Token。</li>
     */
    private boolean enabled;

    /**
     * 签名密钥来源
     * <li>默认 STATIC；表驱动来源待 Schema 变更。</li>
     */
    private InternalTokenKeyUsage keyUsage = InternalTokenKeyUsage.STATIC;

    /**
     * 签发 Token 的实例 ID（{@code inst}）
     * <li>默认取 {@code paytech.instance-id}；目标服务按自身实例 ID 校验，启用时必填。</li>
     */
    private String instanceId;

    /**
     * Token 有效期
     * <li>默认且最长 300 秒，必须为正。</li>
     */
    private Duration tokenLifetime = InternalTokenClaims.MAX_LIFETIME;

    /**
     * 当前签名私钥：含 kid 的 P-256 私钥 JWK JSON
     * <li>只能经安全配置注入，不得写入版本库；JWKS 只发布其公钥部分。</li>
     */
    private String privateJwk;

    /**
     * 轮换中的旧公钥：P-256 公钥 JWK JSON 列表
     * <li>轮换时把旧公钥移入此处并至少保留 Token 最长有效期加调用方 JWKS 缓存期（默认 300s + 5min），之后删除。</li>
     */
    private List<String> retiringPublicJwks = new ArrayList<>();

    /**
     * 调用方白名单：服务名 → 允许的目标服务与 Purpose
     * <li>阶段 0 以 X-Caller-Service 识别调用方，过渡信任；默认为空，全部拒绝。</li>
     */
    private Map<String, Caller> callers = new LinkedHashMap<>();

    /**
     * 调用方是否获准为该目标服务与 Purpose 申请 Token
     * <li>未登记调用方、越权目标与越权 Purpose 结果相同，不暴露白名单内容。</li>
     *
     * @param callerService 调用方服务名
     * @param targetService 目标服务名
     * @param purpose Purpose Code
     * @return 获准返回 true
     */
    public boolean permits(String callerService, String targetService, String purpose) {
        Caller caller = callerService == null ? null : callers.get(callerService);
        return caller != null && caller.getTargets().contains(targetService) && caller.getPurposes().contains(purpose);
    }

    /**
     * 启动时校验：实例 ID、有效期与白名单格式；任一不合法拒绝启动。
     *
     * @throws IllegalStateException 配置不合法
     */
    public void validate() {
        if (instanceId == null || !InternalTokenClaims.INSTANCE_ID.matcher(instanceId).matches()) {
            throw new IllegalStateException("internal-token.issuer.instance-id（默认取 paytech.instance-id）必须是合法实例 ID");
        }
        if (tokenLifetime == null || tokenLifetime.isNegative() || tokenLifetime.isZero()
            || tokenLifetime.compareTo(InternalTokenClaims.MAX_LIFETIME) > 0) {
            throw new IllegalStateException("internal-token.issuer.token-lifetime 必须在 (0, 300s] 之间");
        }
        callers.forEach(InternalTokenIssuerProperties::validateCaller);
    }

    /** 服务名、目标服务与 Purpose 的格式；列表非空。 */
    private static void validateCaller(String caller, Caller grant) {
        String prefix = "internal-token.issuer.callers." + caller;
        if (!InternalTokenClaims.SERVICE_NAME.matcher(caller).matches()) {
            throw new IllegalStateException("internal-token.issuer.callers 服务名不合法：" + caller);
        }
        if (grant == null || grant.getTargets().isEmpty() || grant.getPurposes().isEmpty()) {
            throw new IllegalStateException(prefix + " 必须至少登记一个目标服务与一个 Purpose");
        }
        if (!grant.getTargets().stream().allMatch(target -> InternalTokenClaims.SERVICE_NAME.matcher(target).matches())) {
            throw new IllegalStateException(prefix + ".targets 含不合法的服务名");
        }
        if (!grant.getPurposes().stream().allMatch(purpose -> InternalTokenClaims.PURPOSE.matcher(purpose).matches())) {
            throw new IllegalStateException(prefix + ".purposes 含不合法的 Purpose Code");
        }
    }

    /** 一个调用方获准的目标服务与 Purpose。 */
    @Getter
    @Setter
    public static class Caller {

        /**
         * 允许的目标服务名（Token {@code aud}）
         * <li>至少一个；格式同服务发现名。</li>
         */
        private Set<String> targets = Set.of();

        /**
         * 允许的 Purpose Code
         * <li>至少一个；普通业务调用为 GENERAL。</li>
         */
        private Set<String> purposes = Set.of();
    }
}
