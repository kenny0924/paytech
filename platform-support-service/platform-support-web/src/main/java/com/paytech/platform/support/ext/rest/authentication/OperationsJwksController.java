package com.paytech.platform.support.ext.rest.authentication;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.accesscontrol.service.processor.QueryOperationsJwksProcessor;
import com.paytech.platform.support.api.authentication.OperationsJwksQueryApi;
import com.paytech.platform.support.api.authentication.response.JsonWebKeySetResponseBody;

import lombok.AllArgsConstructor;

/**
 * Operations Realm JWKS 内部 HTTP 入口。
 */
@RestController
@AllArgsConstructor
public class OperationsJwksController implements OperationsJwksQueryApi {

    private final QueryOperationsJwksProcessor processor;
    private final JwksConverter converter;

    /** 返回当前 Realm 已校验的完整公钥集合。 */
    @Override
    public JsonWebKeySetResponseBody queryOperationsJwks() {
        return converter.response(processor.process(converter.operationsRequest()));
    }
}
