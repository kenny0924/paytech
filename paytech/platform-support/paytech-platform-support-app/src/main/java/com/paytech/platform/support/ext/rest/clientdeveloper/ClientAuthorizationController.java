package com.paytech.platform.support.ext.rest.clientdeveloper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.platform.support.accesscontrol.service.processor.AuthorizeClientProcessor;
import com.paytech.platform.support.accesscontrol.service.processor.request.ClientAuthorizeRequest;
import com.paytech.platform.support.api.authorization.ClientAuthorizationApi;
import com.paytech.platform.support.api.authorization.request.ClientAuthorizationRequestBody;
import com.paytech.platform.support.api.authorization.response.ClientAuthorizationResponseBody;

import lombok.AllArgsConstructor;

/** 私网 CLIENT 权限判断入口；只做协议转换。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "client.authentication.enabled", havingValue = "true")
public class ClientAuthorizationController implements ClientAuthorizationApi {
    private final ClientDeveloperConverter converter;
    private final AuthorizeClientProcessor authorizeClientProcessor;

    /** 返回已授予的权限码。 */
    @Override
    public ClientAuthorizationResponseBody authorize(String accountId, String sessionId,
        ClientAuthorizationRequestBody request) {
        return new ClientAuthorizationResponseBody(authorizeClientProcessor.process(new ClientAuthorizeRequest(
            converter.session(accountId), converter.session(sessionId), request.getPermissionCodes())));
    }
}
