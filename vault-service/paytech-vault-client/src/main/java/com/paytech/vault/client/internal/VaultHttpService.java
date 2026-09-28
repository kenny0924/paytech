package com.paytech.vault.client.internal;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.vault.client.internal.wire.ProtectRequest;
import com.paytech.vault.client.internal.wire.ProtectResponse;
import com.paytech.vault.client.internal.wire.RevealRequest;
import com.paytech.vault.client.internal.wire.RevealResponse;

/** 实现内部使用的 HTTP 契约；宿主只依赖 VaultClient。 */
@HttpExchange(url = "/vault", accept = MediaType.APPLICATION_JSON_VALUE, contentType = MediaType.APPLICATION_JSON_VALUE)
public interface VaultHttpService {
    @PostExchange("/v1/records/protect")
    ProtectResponse protect(@RequestHeader("X-Caller-Service") String caller,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Request-Id") String requestId, @RequestBody ProtectRequest request);

    @PostExchange("/v1/records/reveal")
    RevealResponse reveal(@RequestHeader("X-Caller-Service") String caller,
            @RequestHeader("X-Request-Id") String requestId, @RequestBody RevealRequest request);
}
