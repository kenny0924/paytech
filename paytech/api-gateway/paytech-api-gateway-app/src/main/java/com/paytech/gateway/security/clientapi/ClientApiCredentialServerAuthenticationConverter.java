package com.paytech.gateway.security.clientapi;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;

import com.paytech.gateway.web.GatewayExchangeAttributes;
import com.paytech.platform.support.api.apicredential.ApiCredentialConstraints;

import reactor.core.publisher.Mono;

/** Bearer Scheme 大小写不敏感；Credential 的精确语法与上限由 Platform 拥有。 */
public final class ClientApiCredentialServerAuthenticationConverter implements ServerAuthenticationConverter {
    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        return Mono.fromCallable(() -> {
            List<String> values = exchange.getRequest().getHeaders().getOrEmpty(HttpHeaders.AUTHORIZATION);
            if (values.size() != 1) {
                throw invalid();
            }
            String value = values.getFirst();
            if (value.length() < 8 || !value.regionMatches(true, 0, "Bearer ", 0, 7)
                || !ApiCredentialConstraints.isValidCredential(value.substring(7))) {
                throw invalid();
            }
            return new ClientApiAuthenticationToken(value.substring(7),
                exchange.getRequiredAttribute(GatewayExchangeAttributes.REQUEST_ID));
        });
    }

    private BadCredentialsException invalid() {
        return new BadCredentialsException("未通过身份验证");
    }
}
