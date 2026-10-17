package com.paytech.platform.support.ext.rest.apicredential;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.accesscontrol.service.processor.ApiCredentialValidationProcessor;
import com.paytech.platform.support.api.apicredential.ApiCredentialValidationApi;
import com.paytech.platform.support.api.apicredential.response.ValidatedCredentialResponseBody;

import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

/** PLT-I03 私网协议适配器，查询和认证交给 Access Control 的用例入口。 */
@RestController
@AllArgsConstructor
public class ApiCredentialValidationController implements ApiCredentialValidationApi {

    private final ApiCredentialValidationProcessor validationProcessor;

    /** 适配既有 reactive HTTP 合同，不在 Controller 执行查询或事务编排。 */
    @Override
    public Mono<ValidatedCredentialResponseBody> validate(String credential) {
        return Mono.fromSupplier(() -> ApiCredentialValidationConverter.toResponseBody(
            validationProcessor.process(credential)));
    }
}
