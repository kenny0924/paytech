package com.paytech.platform.support.api.authentication;

import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.platform.support.api.authentication.response.JsonWebKeySetResponseBody;

/**
 * Operations Realm 公钥查询契约。
 */
@HttpExchange("/authentication/v1/realms/OPERATIONS")
public interface OperationsJwksQueryApi {

    /**
     * 查询当前可用于 Operations Access JWT 验签的完整 JWKS。
     *
     * @return Operations Realm 完整 JWKS
     */
    @GetExchange("/jwks")
    JsonWebKeySetResponseBody queryOperationsJwks();
}
