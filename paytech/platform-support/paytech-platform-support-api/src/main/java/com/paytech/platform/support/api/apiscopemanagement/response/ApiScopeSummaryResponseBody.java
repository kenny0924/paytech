package com.paytech.platform.support.api.apiscopemanagement.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 全局 Scope 目录摘要，没有启停状态。 */
public class ApiScopeSummaryResponseBody {

    /** Scope 目录主键，正十进制字符串。 */
    private final String apiScopeId;

    /** 不可修改的小写 ASCII 能力编码。 */
    private final String scopeCode;

    /** 可编辑展示名称，不参与认证。 */
    private final String displayName;

    /** 可空业务说明。 */
    private final String description;

    /** 目录行并发版本。 */
    private final int version;

    /** 按 JSON 属性绑定，保留既有字段及可空语义。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ApiScopeSummaryResponseBody(
        @JsonProperty("apiScopeId") String apiScopeId,
        @JsonProperty("scopeCode") String scopeCode,
        @JsonProperty("displayName") String displayName,
        @JsonProperty("description") String description,
        @JsonProperty("version") int version) {
        this.apiScopeId = apiScopeId;
        this.scopeCode = scopeCode;
        this.displayName = displayName;
        this.description = description;
        this.version = version;
    }

    /** Scope 目录主键，正十进制字符串。 */
    public String getApiScopeId() {
        return apiScopeId;
    }

    /** 不可修改的小写 ASCII 能力编码。 */
    public String getScopeCode() {
        return scopeCode;
    }

    /** 可编辑展示名称，不参与认证。 */
    public String getDisplayName() {
        return displayName;
    }

    /** 可空业务说明。 */
    public String getDescription() {
        return description;
    }

    /** 目录行并发版本。 */
    public int getVersion() {
        return version;
    }
}
