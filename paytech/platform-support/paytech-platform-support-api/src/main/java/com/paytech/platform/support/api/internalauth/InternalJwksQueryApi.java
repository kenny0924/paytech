package com.paytech.platform.support.api.internalauth;

import org.springframework.http.MediaType;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.platform.support.api.internalauth.response.InternalJwksResponseBody;

/**
 * 内部调用 Token 公钥查询契约（D26）：各服务的 {@code internal-auth.jwks-uri} 指向本接口。
 * <li>只发布 P-256 / ES256 公钥：当前签名密钥及仍在验签窗口内的轮换中密钥；不含任何私钥成员。</li>
 * <li>无需内部 Token 即可读取（{@code internal-auth.exempt-path-prefixes} 豁免本路径）。</li>
 */
@HttpExchange(url = "/platform/v1/internal-auth", accept = MediaType.APPLICATION_JSON_VALUE)
public interface InternalJwksQueryApi {

    /**
     * 查询当前可用于内部调用 Token 验签的完整 JWKS
     *
     * @return 标准 JSON Web Key Set
     *         <li>503 表示签名密钥配置无效，调用方保留已缓存公钥并失败关闭。</li>
     */
    @GetExchange("/jwks")
    InternalJwksResponseBody queryInternalJwks();
}
