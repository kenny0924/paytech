package com.paytech.foundation.internalauth.api;

/**
 * 出站内部调用 Token 的来源端口。
 *
 * @author Claude
 */
public interface InternalTokenSupplier {

    /**
     * 取得调用目标服务的 Token；实现可缓存到过期前。
     *
     * @param targetService 目标服务名（服务发现名，即 Token {@code aud}）
     * @param purpose 大写 Purpose Code
     * @return JWS Compact Token
     * @throws InternalTokenUnavailableException 无法取得，调用方失败关闭
     */
    String token(String targetService, String purpose);
}
