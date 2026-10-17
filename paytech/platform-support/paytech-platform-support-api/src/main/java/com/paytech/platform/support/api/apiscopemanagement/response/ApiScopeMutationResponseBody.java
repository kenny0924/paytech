package com.paytech.platform.support.api.apiscopemanagement.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 本次目录写入结果，不虚构生命周期状态。 */
public class ApiScopeMutationResponseBody {

    /** Scope 目录主键，正十进制字符串。 */
    private final String apiScopeId;

    /** 不可修改的目录编码。 */
    private final String scopeCode;

    /** 本次保存后的目录版本。 */
    private final int version;

    /** 按 JSON 属性绑定，保留既有字段及可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ApiScopeMutationResponseBody(
        @JsonProperty("apiScopeId") String apiScopeId,
        @JsonProperty("scopeCode") String scopeCode,
        @JsonProperty("version") int version) {
        this.apiScopeId = apiScopeId;
        this.scopeCode = scopeCode;
        this.version = version;
    }

    /** Scope 目录主键，正十进制字符串。 */
    public String getApiScopeId() {
        return apiScopeId;
    }

    /** 不可修改的目录编码。 */
    public String getScopeCode() {
        return scopeCode;
    }

    /** 本次保存后的目录版本。 */
    public int getVersion() {
        return version;
    }
}
