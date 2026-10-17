package com.paytech.vault.client.internal;

import java.util.UUID;

import org.slf4j.MDC;

import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.vault.client.*;
import com.paytech.vault.client.internal.wire.ProtectRequest;
import com.paytech.vault.client.internal.wire.RevealRequest;

import lombok.AllArgsConstructor;

/** 将不可变公共值转换到私有 HTTP 契约。 */
@AllArgsConstructor
public final class DefaultVaultClient implements VaultClient {
    private final String caller;
    private final VaultHttpService service;
    @Override public ProtectResult protect(ProtectCommand command) {
        VaultValidation.required(command);
        var response = service.protect(caller, command.idempotencyKey(), requestId(),
                new ProtectRequest(command.dataType(), command.tokenMode().getCode(), command.value(), command.purpose()));
        return new ProtectResult(response.token());
    }
    @Override public RevealResult reveal(RevealQuery query) {
        VaultValidation.required(query);
        var response = service.reveal(caller, requestId(), new RevealRequest(query.token(), query.purpose()));
        return new RevealResult(response.value());
    }
    private String requestId() {
        String current = MDC.get("requestId");
        return ApiProblemProfile.isValidRequestId(current) ? current : UUID.randomUUID().toString();
    }
}
