package com.paytech.platform.support.api.authorization.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 当前 Operations 身份对指定路由的授权决策。 */
public final class AuthorizationDecisionResponseBody {
    /** 是否允许访问；false 表示明确拒绝，不代表授权服务不可用。 */
    private final boolean allowed;

    /** 绑定当前授权决策。 */
    @JsonCreator
    public AuthorizationDecisionResponseBody(@JsonProperty("allowed") boolean allowed) {
        this.allowed = allowed;
    }

    /** 返回是否允许访问指定路由。 */
    public boolean isAllowed() {
        return allowed;
    }
}
