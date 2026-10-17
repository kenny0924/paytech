package com.paytech.vault.client.internal;

/**
 * 出站内部调用 Token 的 Vault 侧端口（D26）。
 * <li>宿主存在 Foundation {@code InternalTokenSupplier} 时由自动配置适配；不存在时没有本端口，请求不带 {@code Authorization}。</li>
 * <li>本接口不引用 internal-auth 类型，避免宿主未引入该模块时加载失败。</li>
 *
 * @author Claude
 */
@FunctionalInterface
public interface VaultInternalToken {

    /**
     * 取得调用目标服务的 Token。
     *
     * @param targetService Vault 服务发现名（Token {@code aud}）
     * @return JWS Compact Token；返回 null 表示当前没有可用的 Token 来源，请求按原样只带 {@code X-Caller-Service}
     * @throws RuntimeException 来源存在但取 Token 失败；调用方失败关闭，不发送请求
     */
    String token(String targetService);
}
