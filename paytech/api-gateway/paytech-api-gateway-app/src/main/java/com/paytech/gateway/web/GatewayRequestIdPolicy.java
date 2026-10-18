package com.paytech.gateway.web;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;

/** 仅 Client API 可保留合法的外部关联标识；其他入口一律重新生成。 */
public final class GatewayRequestIdPolicy {
    private final Supplier<String> generator;

    public GatewayRequestIdPolicy(Supplier<String> generator) {
        this.generator = Objects.requireNonNull(generator, "Request ID 生成器不可为空");
    }

    public String resolve(GatewayRouteChannel channel, List<String> values) {
        if (channel == GatewayRouteChannel.CLIENT_API && values != null && values.size() == 1) {
            String value = values.getFirst();
            if (ApiProblemProfile.isValidRequestId(value)) {
                return value;
            }
        }
        String generatedRequestId = generator.get();
        if (!ApiProblemProfile.isValidRequestId(generatedRequestId)) {
            throw new IllegalStateException("Generated request ID does not match the Problem Profile");
        }
        return generatedRequestId;
    }
}
