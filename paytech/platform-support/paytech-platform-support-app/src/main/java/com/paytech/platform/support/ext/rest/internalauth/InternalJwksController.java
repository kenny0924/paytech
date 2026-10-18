package com.paytech.platform.support.ext.rest.internalauth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.api.internalauth.InternalJwksQueryApi;
import com.paytech.platform.support.api.internalauth.response.InternalJwksResponseBody;
import com.paytech.platform.support.core.internalauth.InternalTokenKeyRing;

import lombok.AllArgsConstructor;

/** 内部调用 Token 公钥发布入口；只读内存中的已校验密钥集合，不访问数据库。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "internal-token.issuer.enabled", havingValue = "true")
public class InternalJwksController implements InternalJwksQueryApi {

    private final InternalTokenKeyRing keyRing;
    private final InternalTokenConverter converter;

    /** 返回当前签名公钥与轮换中公钥。 */
    @Override
    public InternalJwksResponseBody queryInternalJwks() {
        return converter.convertJwks(keyRing.publishedKeys());
    }
}
