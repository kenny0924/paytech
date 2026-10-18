package com.paytech.gateway.security;

import java.util.Objects;

import org.springframework.web.reactive.function.client.WebClient;

import com.paytech.foundation.internalauth.api.InternalAuthMode;
import com.paytech.foundation.internalauth.api.InternalTokenClaims;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
import com.paytech.foundation.internalauth.webflux.InternalTokenExchangeFilterFunction;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Gateway 出站内部调用 Token（D26）：{@code internal-auth.mode} 非 OFF 时，为调用 Platform 与转发到内部服务的请求附加
 * {@code Authorization: Bearer <Token>}。
 * <li>默认 OFF：不附加，出站请求与接入前一致。</li>
 * <li>Gateway 是公网入口，只附加出站 Token，不校验入站内部 Token。</li>
 * <li>非 OFF 却没有 Token 来源（本地静态密钥对或 Platform 签发客户端）时启动失败，不静默降级为不附加。</li>
 *
 * @author Claude
 */
public final class GatewayInternalTokenPolicy {

    private final InternalTokenSupplier supplier;

    /**
     * 按模式创建出站 Token 策略。
     *
     * @param mode {@code internal-auth.mode}
     * @param supplier Foundation 提供的 Token 来源；OFF 时忽略，可为 null
     */
    public GatewayInternalTokenPolicy(InternalAuthMode mode, InternalTokenSupplier supplier) {
        Objects.requireNonNull(mode, "internal-auth.mode 不可为空");
        if (mode != InternalAuthMode.OFF && supplier == null) {
            throw new IllegalStateException("internal-auth.mode=" + mode
                + " 时 Gateway 需要内部 Token 来源: 配置 internal-auth.static-key（仅本地）或提供 Platform 签发客户端");
        }
        this.supplier = mode == InternalAuthMode.OFF ? null : supplier;
    }

    /**
     * 是否为出站请求附加内部 Token。
     *
     * @return 非 OFF 返回 true
     */
    public boolean enabled() {
        return supplier != null;
    }

    /**
     * 为调用一个内部服务的 WebClient 挂上 Token 过滤器；OFF 时不改动 Builder。
     *
     * @param builder WebClient 构建器
     * @param targetService 目标服务名（Token {@code aud}）
     */
    public void apply(WebClient.Builder builder, String targetService) {
        if (supplier == null) {
            return;
        }
        builder.filter(new InternalTokenExchangeFilterFunction(supplier, targetService));
    }

    /**
     * 取转发目标服务的 {@code GENERAL} Token。
     * <li>Token 获取可能同步访问 Platform，因此在 boundedElastic 上执行；失败原样传播，由调用方失败关闭。</li>
     * <li>OFF 时返回空。</li>
     *
     * @param targetService 目标服务名（Token {@code aud}）
     * @return Token
     */
    public Mono<String> token(String targetService) {
        if (supplier == null) {
            return Mono.empty();
        }
        return Mono.fromCallable(() -> supplier.token(targetService, InternalTokenClaims.GENERAL_PURPOSE))
            .subscribeOn(Schedulers.boundedElastic());
    }
}
