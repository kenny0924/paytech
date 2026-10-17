package com.paytech.platform.support.api.apiscopemanagement.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 路由选择器所需编码和名称，不包含授权或引用数量。 */
public class ApiScopeOptionResponseBody {

    /** 选择后保存的精确编码。 */
    private final String scopeCode;

    /** 目录当前展示名称。 */
    private final String displayName;

    /** 按 JSON 属性绑定，保留既有字段及可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ApiScopeOptionResponseBody(
        @JsonProperty("scopeCode") String scopeCode,
        @JsonProperty("displayName") String displayName) {
        this.scopeCode = scopeCode;
        this.displayName = displayName;
    }

    /** 选择后保存的精确编码。 */
    public String getScopeCode() {
        return scopeCode;
    }

    /** 目录当前展示名称。 */
    public String getDisplayName() {
        return displayName;
    }
}
