package com.paytech.platform.support.api.authentication;

import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.platform.support.api.authentication.response.JsonWebKeySetResponseBody;

/**
 * Customer Realm 公钥查询契约。
 */
@HttpExchange("/authentication/v1/realms/CUSTOMER")
public interface CustomerJwksQueryApi {

    /**
     * 查询当前可用于 Customer Access JWT 验签的完整 JWKS。
     *
     * @return Customer Realm 完整 JWKS
     */
    @GetExchange("/jwks")
    JsonWebKeySetResponseBody queryCustomerJwks();
}
