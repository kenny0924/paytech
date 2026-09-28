package com.paytech.platform.support.ext.rest.authentication;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.accesscontrol.service.processor.QueryCustomerJwksProcessor;
import com.paytech.platform.support.api.authentication.CustomerJwksQueryApi;
import com.paytech.platform.support.api.authentication.response.JsonWebKeySetResponseBody;

import lombok.AllArgsConstructor;

/**
 * Customer Realm JWKS 内部 HTTP 入口。
 */
@RestController
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
@AllArgsConstructor
public class CustomerJwksController implements CustomerJwksQueryApi {

    private final JwksConverter converter;
    private final QueryCustomerJwksProcessor processor;

    /** 返回当前 Realm 已校验的完整公钥集合。 */
    @Override
    public JsonWebKeySetResponseBody queryCustomerJwks() {
        return converter.response(processor.process(converter.customerRequest()));
    }
}
